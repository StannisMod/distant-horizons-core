/*
 *    This file is part of the Distant Horizons mod (formerly the LOD Mod),
 *    licensed under the GNU GPL v3 License.
 *
 *    Copyright (C) 2021  James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.lod.core.render;

import java.awt.Color;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.seibel.lod.core.api.ApiShared;
import com.seibel.lod.core.logging.ConfigBasedLogger;
import com.seibel.lod.core.logging.ConfigBasedSpamLogger;
import com.seibel.lod.core.objects.BoolType;
import com.seibel.lod.core.objects.Pos2D;
import com.seibel.lod.core.render.objects.QuadElementBuffer;
import com.seibel.lod.core.render.objects.GLState;
import com.seibel.lod.core.util.*;
import com.seibel.lod.core.util.gridList.*;
import com.seibel.lod.core.wrapperInterfaces.misc.ILightMapWrapper;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL32;

import com.seibel.lod.core.builders.lodBuilding.bufferBuilding.LodBufferBuilderFactory;
import com.seibel.lod.core.enums.rendering.DebugMode;
import com.seibel.lod.core.enums.rendering.FogColorMode;
import com.seibel.lod.core.enums.rendering.FogDistance;
import com.seibel.lod.core.handlers.dependencyInjection.SingletonHandler;
import com.seibel.lod.core.objects.lod.LodDimension;
import com.seibel.lod.core.objects.math.Mat4f;
import com.seibel.lod.core.objects.math.Vec3d;
import com.seibel.lod.core.objects.math.Vec3f;
import com.seibel.lod.core.objects.opengl.RenderRegion;
import com.seibel.lod.core.wrapperInterfaces.block.AbstractBlockPosWrapper;
import com.seibel.lod.core.wrapperInterfaces.config.ILodConfigWrapperSingleton;
import com.seibel.lod.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.lod.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.lod.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.lod.core.wrapperInterfaces.world.IWorldWrapper;

/**
 * This is where all the magic happens. <br>
 * This is where LODs are draw to the world.
 * 
 * @author James Seibel
 * @version 12-12-2021
 */
public class LodRenderer
{
	private static final ILodConfigWrapperSingleton CONFIG = SingletonHandler.get(ILodConfigWrapperSingleton.class);
	public static final ConfigBasedLogger EVENT_LOGGER = new ConfigBasedLogger(LogManager.getLogger(LodRenderer.class),
			() -> CONFIG.client().advanced().debugging().debugSwitch().getLogRendererBufferEvent());

	public static ConfigBasedSpamLogger tickLogger = new ConfigBasedSpamLogger(LogManager.getLogger(LodRenderer.class),
			() -> CONFIG.client().advanced().debugging().debugSwitch().getLogRendererBufferEvent(),1);
	public static final boolean ENABLE_DRAW_LAG_SPIKE_LOGGING = false;
	public static final boolean ENABLE_DUMP_GL_STATE = true;
	public static final long DRAW_LAG_SPIKE_THRESHOLD_NS = TimeUnit.NANOSECONDS.convert(20, TimeUnit.MILLISECONDS);

	public static final boolean ENABLE_IBO = true;
	
	public static class LagSpikeCatcher {

		long timer = System.nanoTime();
		public LagSpikeCatcher() {}
		public void end(String source) {
			if (!ENABLE_DRAW_LAG_SPIKE_LOGGING) return;
			timer = System.nanoTime() - timer;
			if (timer> DRAW_LAG_SPIKE_THRESHOLD_NS) { //4 ms
				EVENT_LOGGER.debug("NOTE: "+source+" took "+Duration.ofNanos(timer)+"!");
			}
			
		}
	}
	
	private static final IMinecraftClientWrapper MC = SingletonHandler.get(IMinecraftClientWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonHandler.get(IMinecraftRenderWrapper.class);

	/**
	 * If true the LODs colors will be replaced with
	 * a checkerboard, this can be used for debugging.
	 */
	public DebugMode previousDebugMode = DebugMode.OFF;
	
	// This tells us if the renderer is enabled or not. If in a world, it should be enabled.
	private boolean isSetupComplete = false;
	
	/** This is used to generate the buildable buffers */
	private final LodBufferBuilderFactory lodBufferBuilderFactory;
	
	// The shader program
	LodRenderProgram shaderProgram = null;
	
	/** This is used to determine if the LODs should be regenerated */
	private AbstractBlockPosWrapper lastUpdatedPos = null;
	public QuadElementBuffer quadIBO = null;
	
	// these variables are used to determine if the buffers should be rebuilt
	private int prevRenderDistance = 0;
	private long prevPlayerPosTime = 0;
	private long prevVanillaChunkTime = 0;
	private long prevChunkTime = 0;
	
	
	/** This is used to determine if the LODs should be regenerated */
	private FogDistance prevFogDistance = FogDistance.NEAR_AND_FAR;
	
	/**
	 * if this is true the LOD buffers should be regenerated,
	 * provided they aren't already being regenerated.
	 */
	private volatile boolean partialRegen = false;
	private volatile boolean fullRegen = true;
	private volatile boolean markToCleanup = false;
	
	/**
	 * This HashSet contains every chunk that Vanilla Minecraft
	 * is going to render
	 */
	public PosArrayGridList<BoolType> vanillaChunks = null;
	
	private boolean canVanillaFogBeDisabled = true;

	public void requestCleanup() {markToCleanup = true;}
	
	public LodRenderer(LodBufferBuilderFactory newLodNodeBufferBuilder)
	{
		lodBufferBuilderFactory = newLodNodeBufferBuilder;
	}

	/**
	 * Besides drawing the LODs this method also starts
	 * the async process of generating the Buffers that hold those LODs.
	 * @param lodDim The dimension to draw, if null doesn't replace the current dimension.
	 * @param baseModelViewMatrix This matrix stack should come straight from MC's renderChunkLayer (or future equivalent) method
	 * @param baseProjectionMatrix
	 * @param partialTicks how far into the current tick this method was called.
	 */
	public void drawLODs(LodDimension lodDim, Mat4f baseModelViewMatrix, Mat4f baseProjectionMatrix, float partialTicks, IProfilerWrapper profiler)
	{
		//=================================//
		// determine if LODs should render //
		//=================================//
		
		if (lodDim == null)
		{
			// if there aren't any loaded LodChunks
			// don't try drawing anything
			return;
		}
		
		if (MC_RENDER.playerHasBlindnessEffect())
		{
			// if the player is blind, don't render LODs,
			// and don't change minecraft's fog
			// which blindness relies on.
			return;
		}
		if (MC_RENDER.getLightmapWrapper() == null)
			return;

		// get MC's shader program
		// Save all MC render state
		LagSpikeCatcher drawSaveGLState = new LagSpikeCatcher();
		GLState currentState = new GLState();
		if (ENABLE_DUMP_GL_STATE) {
			tickLogger.debug("Saving GL state: {}", currentState);
		}
		drawSaveGLState.end("drawSaveGLState");

		GLProxy glProxy = GLProxy.getInstance();
		if (canVanillaFogBeDisabled && CONFIG.client().graphics().fogQuality().getDisableVanillaFog())
			if (!MC_RENDER.tryDisableVanillaFog())
				canVanillaFogBeDisabled = false;
		
		// TODO move the buffer regeneration logic into its own class (probably called in the client api instead)
		// starting here...
		LagSpikeCatcher updateStatus = new LagSpikeCatcher();
		updateRegenStatus(lodDim, partialTicks);
		updateStatus.end("LodDrawSetup:UpdateStatus");
		

		// FIXME: Currently, we check for last Lod Dimension so that we can trigger a cleanup() if dimension has changed
		// The better thing to do is to call cleanup() on leaving dimensions in the EventApi, but only for client-side.
		if (markToCleanup) {
			LagSpikeCatcher drawObjectClenup = new LagSpikeCatcher();
			markToCleanup = false;
			cleanup(); // This will unset the isSetupComplete, causing a setup() call.
			drawObjectClenup.end("drawObjectClenup");
		}
		
		//=================//
		// create the LODs //
		//=================//
		
		// only regenerate the LODs if:
		// 1. we want to regenerate LODs
		// 2. we aren't already regenerating the LODs
		// 3. we aren't waiting for the build and draw buffers to swap
		//		(this is to prevent thread conflicts)
		LagSpikeCatcher swapBuffer = new LagSpikeCatcher();
		if (partialRegen || fullRegen) {
			if (lodBufferBuilderFactory.updateAndSwapLodBuffersAsync(this, lodDim, MC.getPlayerBlockPos().getX(),
					MC.getPlayerBlockPos().getY(), MC.getPlayerBlockPos().getZ(), fullRegen)) {
				// the regen process has been started,
				// it will be done when lodBufferBuilder.newBuffersAvailable() is true
				fullRegen = false;
				partialRegen = false;
			}
		}
		swapBuffer.end("SwapBuffer");
		// Get the front buffers to draw
		MovableGridRingList<RenderRegion> regions = lodBufferBuilderFactory.getRenderRegions();
		
		if (regions == null) {
			// There is no vbos, which means nothing needs to be drawn. So skip rendering
			return;
		}
		
		//===================//
		// draw params setup //
		//===================//
		
		profiler.push("LOD draw setup");
		LagSpikeCatcher drawSetup = new LagSpikeCatcher();
		/*---------Set GL State--------*/
		// Make sure to unbind current VBO so we don't mess up vanilla settings
		LagSpikeCatcher drawGLSetup = new LagSpikeCatcher();
		LagSpikeCatcher drawBindBuff = new LagSpikeCatcher();
		//GL32.glBindFramebuffer(GL32.GL_FRAMEBUFFER, MC_RENDER.getTargetFrameBuffer());
		GL32.glViewport(0,0, MC_RENDER.getTargetFrameBufferViewportWidth(), MC_RENDER.getTargetFrameBufferViewportHeight());
		GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, 0);
		drawBindBuff.end("drawBindBuff");
		// set the required open GL settings
		LagSpikeCatcher drawSetPolygon = new LagSpikeCatcher();
		if (CONFIG.client().advanced().debugging().getDebugMode() == DebugMode.SHOW_DETAIL_WIREFRAME
			|| CONFIG.client().advanced().debugging().getDebugMode() == DebugMode.SHOW_GENMODE_WIREFRAME
			|| CONFIG.client().advanced().debugging().getDebugMode() == DebugMode.SHOW_WIREFRAME
			|| CONFIG.client().advanced().debugging().getDebugMode() == DebugMode.SHOW_OVERLAPPING_QUADS_WIREFRAME) {
			GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_LINE);
			//GL32.glDisable(GL32.GL_CULL_FACE);
		}
		else {
			GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_FILL);
			GL32.glEnable(GL32.GL_CULL_FACE);
		}
		drawSetPolygon.end("drawSetPolygon");
		LagSpikeCatcher drawEnableDepth = new LagSpikeCatcher();
		GL32.glEnable(GL32.GL_DEPTH_TEST);
		// GL32.glDisable(GL32.GL_DEPTH_TEST);
		GL32.glDepthFunc(GL32.GL_LESS);
		drawEnableDepth.end("drawEnableDepth");
		drawGLSetup.end("drawGLSetup");
		// enable transparent rendering
		GL32.glBlendFunc(GL32.GL_SRC_ALPHA, GL32.GL_ONE_MINUS_SRC_ALPHA);
		GL32.glEnable(GL32.GL_BLEND);

		GL32.glDisable(GL32.GL_BLEND);
		GL32.glClear(GL32.GL_DEPTH_BUFFER_BIT);

		/*---------Bind required objects--------*/
		// Setup LodRenderProgram and the LightmapTexture if it has not yet been done
		// also binds LightmapTexture, VAO, and ShaderProgram
		if (!isSetupComplete) {
			LagSpikeCatcher drawObjectSetup = new LagSpikeCatcher();
			setup();
			drawObjectSetup.end("drawObjectSetup");
		} else {
			LagSpikeCatcher drawShaderBind = new LagSpikeCatcher();
			LodFogConfig newConfig = shaderProgram.isShaderUsable();
			if (newConfig != null) {
				shaderProgram.free();
				shaderProgram = new LodRenderProgram(newConfig);
			}
			shaderProgram.bind();
			drawShaderBind.end("drawShaderBind");
		}
		LagSpikeCatcher drawSetActiveTexture = new LagSpikeCatcher();
		GL32.glActiveTexture(GL32.GL_TEXTURE0);
		drawSetActiveTexture.end("drawSetActiveTexture");
		LagSpikeCatcher drawCalculateParams = new LagSpikeCatcher();
		//LightmapTexture lightmapTexture = new LightmapTexture();
		
		/*---------Get required data--------*/
		// Get the matrixs for rendering
		int vanillaBlockRenderedDistance = MC_RENDER.getRenderDistance() * LodUtil.CHUNK_WIDTH;
		int farPlaneBlockDistance;
		// required for setupFog and setupProjectionMatrix
		if (MC.getWrappedClientWorld().getDimensionType().hasCeiling())
			farPlaneBlockDistance = Math.min(CONFIG.client().graphics().quality().getLodChunkRenderDistance(), LodUtil.CEILED_DIMENSION_MAX_RENDER_DISTANCE) * LodUtil.CHUNK_WIDTH;
		else
			farPlaneBlockDistance = CONFIG.client().graphics().quality().getLodChunkRenderDistance() * LodUtil.CHUNK_WIDTH;
		drawCalculateParams.end("drawCalculateParams");

		Mat4f combinedMatrix = createCombinedMatrix(baseProjectionMatrix, baseModelViewMatrix,
				vanillaBlockRenderedDistance, farPlaneBlockDistance, partialTicks);
		
		/*---------Fill uniform data--------*/
		LagSpikeCatcher drawFillData = new LagSpikeCatcher();
		// Fill the uniform data. Note: GL33.GL_TEXTURE0 == texture bindpoint 0
		shaderProgram.fillUniformData(combinedMatrix,
				MC_RENDER.isFogStateSpecial() ? getSpecialFogColor(partialTicks) : getFogColor(partialTicks),
				0, MC.getWrappedClientWorld().getHeight(), MC.getWrappedClientWorld().getMinHeight(), farPlaneBlockDistance,
				vanillaBlockRenderedDistance, MC_RENDER.isFogStateSpecial());

		// Note: Since lightmapTexture is changing every frame, it's faster to recreate it than to reuse the old one.
		LagSpikeCatcher drawFillLightmap = new LagSpikeCatcher();
		ILightMapWrapper lightmap = MC_RENDER.getLightmapWrapper();
		lightmap.bind();

		if (ENABLE_IBO) quadIBO.bind();

		//lightmapTexture.fillData(MC_RENDER.getLightmapTextureWidth(), MC_RENDER.getLightmapTextureHeight(), MC_RENDER.getLightmapPixels());
		drawFillLightmap.end("drawFillLightmap");
		drawFillData.end("DrawFillData");
		//GL32.glEnable( GL32.GL_POLYGON_OFFSET_FILL );
		//GL32.glPolygonOffset( 1f, 1f );

		//===========//
		// rendering //
		//===========//
		drawSetup.end("LodDrawSetup");
		profiler.popPush("LOD draw");
		LagSpikeCatcher draw = new LagSpikeCatcher();
		
		boolean cullingDisabled = CONFIG.client().graphics().advancedGraphics().getDisableDirectionalCulling();
		Vec3d cameraPos = MC_RENDER.getCameraExactPosition();
		AbstractBlockPosWrapper cameraBlockPos = MC_RENDER.getCameraBlockPosition();
		Vec3f cameraDir = MC_RENDER.getLookAtVector();
		int drawCount = 0;

		{
			int ox,oy,dx,dy;
		    ox = oy = dx = 0;
		    dy = -1;
		    int len = regions.getSize();
		    int maxI = len*len;
		    int halfLen = len/2;
		    for(int i =0; i < maxI; i++){
		        if ((-halfLen <= ox) && (ox <= halfLen) && (-halfLen <= oy) && (oy <= halfLen)){
		        	Pos2D pos = regions.getCenter();
		        	int regionX = ox+pos.x;
		        	int regionZ = oy+pos.y;
		        	{
						RenderRegion region = regions.get(regionX, regionZ);
						if (region == null) continue;
						if (region.render(lodDim, cameraPos, cameraBlockPos, cameraDir,
								!cullingDisabled, shaderProgram)) drawCount++;
		        	}
		        }
		        if( (ox == oy) || ((ox < 0) && (ox == -oy)) || ((ox > 0) && (ox == 1-oy))){
		            int temp = dx;
		            dx = -dy;
		            dy = temp;
		        }
		        ox += dx;
		        oy += dy;
		    }
		}
		//if (drawCall==0)
		//	tickLogger.info("DrawCall Count: {}", drawCount);
		
		//================//
		// render cleanup //
		//================//
		draw.end("LodDraw");
		profiler.popPush("LOD cleanup");
		LagSpikeCatcher drawCleanup = new LagSpikeCatcher();
		lightmap.unbind();
		if (ENABLE_IBO) quadIBO.unbind();
		
		GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, 0);

		shaderProgram.unbind();
		//lightmapTexture.free();
		GL32.glClear(GL32.GL_DEPTH_BUFFER_BIT);

		currentState.restore();
		drawCleanup.end("LodDrawCleanup");

		// end of internal LOD profiling
		profiler.pop();
		tickLogger.incLogTries();
	}
	
	//=================//
	// Setup Functions //
	//=================//
	
	/** Setup all render objects - REQUIRES to be in render thread */
	private void setup() {
		if (isSetupComplete) {
			EVENT_LOGGER.warn("Renderer setup called but it has already completed setup!");
			return;
		}
		if (!GLProxy.hasInstance()) {
			EVENT_LOGGER.warn("Renderer setup called but GLProxy has not yet been setup!");
			return;
		}

		EVENT_LOGGER.info("Setting up renderer");
		isSetupComplete = true;
		shaderProgram = new LodRenderProgram(LodFogConfig.generateFogConfig());
		if (ENABLE_IBO) {
			quadIBO = new QuadElementBuffer();
			quadIBO.reserve(LodBufferBuilderFactory.MAX_QUADS_PER_BUFFER);
		}
		EVENT_LOGGER.info("Renderer setup complete");
	}
	
	/** Create all buffers that will be used. */
	public void setupBuffers()
	{
		lodBufferBuilderFactory.triggerReset();
	}

	private Color getFogColor(float partialTicks)
	{
		Color fogColor;
		
		if (CONFIG.client().graphics().fogQuality().getFogColorMode() == FogColorMode.USE_SKY_COLOR)
			fogColor = MC_RENDER.getSkyColor();
		else
			fogColor = MC_RENDER.getFogColor(partialTicks);
		
		return fogColor;
	}
	private Color getSpecialFogColor(float partialTicks)
	{
		return MC_RENDER.getSpecialFogColor(partialTicks);
	}

	private static float calculateNearClipPlane(float distance, float partialTicks) {
		double fov = MC_RENDER.getFov(partialTicks);
		double aspectRatio = (double)MC_RENDER.getScreenWidth()/MC_RENDER.getScreenHeight();
		return (float) (distance
						/ Math.sqrt(1d + LodUtil.pow2(Math.tan(fov/180d*Math.PI/2d))
						* (LodUtil.pow2(aspectRatio) + 1d)));
	}

	/**
	 * create and return a new projection matrix based on MC's projection matrix
	 * @param projMat this is Minecraft's current projection matrix
	 * @param modelMat this is Minecraft's current model matrix
	 * @param vanillaBlockRenderedDistance Minecraft's vanilla far plane distance
	 */
	private static Mat4f createCombinedMatrix(Mat4f projMat, Mat4f modelMat, float vanillaBlockRenderedDistance,
											  int farPlaneBlockDistance, float partialTicks)
	{
		//Create a copy of the current matrix, so the current matrix isn't modified.
		Mat4f lodProj = projMat.copy();

		float nearClipPlane;
		if (CONFIG.client().advanced().getLodOnlyMode()) {
			nearClipPlane = 0.1f;
		} else if (CONFIG.client().graphics().advancedGraphics().getUseExtendedNearClipPlane()) {
			nearClipPlane = Math.min((vanillaBlockRenderedDistance-16f),8f*16f);
		} else {
			nearClipPlane = 16f;
		}

		//Set new far and near clip plane values.
		lodProj.setClipPlanes(
				calculateNearClipPlane(nearClipPlane, partialTicks),
				(float)((farPlaneBlockDistance+LodUtil.REGION_WIDTH) * Math.sqrt(2)));

		lodProj.multiply(modelMat);

		return lodProj;
	}
	
	//======================//
	// Cleanup Functions    //
	//======================//

	/** cleanup and free all render objects. REQUIRES to be in render thread
	 *  (Many objects are Native, outside of JVM, and need manual cleanup)  */ 
	private void cleanup() {
		if (!isSetupComplete) {
			EVENT_LOGGER.warn("Renderer cleanup called but Renderer has not completed setup!");
			return;
		}
		if (!GLProxy.hasInstance()) {
			EVENT_LOGGER.warn("Renderer Cleanup called but the GLProxy has never been inited!");
			return;
		}
		isSetupComplete = false;
		EVENT_LOGGER.info("Renderer Cleanup Started");
		shaderProgram.free();
		if (quadIBO != null) quadIBO.destroy(false);
		EVENT_LOGGER.info("Renderer Cleanup Complete");
	}

	/** Calls the BufferBuilder's destroyBuffers method. */
	public void destroyBuffers()
	{
		lodBufferBuilderFactory.destroyBuffers();
	}
	
	//======================//
	// Other Misc Functions //
	//======================//
	
	/**
	 * If this is called then the next time "drawLODs" is called
	 * the LODs will be regenerated; the same as if the player moved.
	 */
	public void regenerateLODsNextFrame()
	{
		fullRegen = true;
	}
	
	// returns whether anything changed
	private boolean updateVanillaRenderedChunks(LodDimension lodDim) {
		// if the player is high enough, draw all LODs
		IWorldWrapper world = MC.getWrappedClientWorld();
		if (lastUpdatedPos.getY() > world.getHeight()-world.getMinHeight() ||
			CONFIG.client().advanced().getLodOnlyMode()) {
			if (vanillaChunks != null) {
				vanillaChunks = null;
				return true;
			}
			return false;
		}

		LagSpikeCatcher getChunks = new LagSpikeCatcher();
		EdgeDistanceBooleanGrid edgeGrid = LodUtil.readVanillaRenderedChunks(lodDim);
		if (edgeGrid == null) {
			if (vanillaChunks != null) {
				vanillaChunks = null;
				return true;
			}
			return false;
		}
		getChunks.end("LodDrawSetup:UpdateStatus:UpdateVanillaChunks:getChunks");
		PosArrayGridList<BoolType> grid = new PosArrayGridList<>(edgeGrid.gridSize, edgeGrid.getOffsetX(), edgeGrid.getOffsetY());

		int overdrawOffset = LodUtil.computeOverdrawOffset(lodDim);
		edgeGrid.flagAllWithDistance(grid, (i) -> (i >= overdrawOffset));
		vanillaChunks = grid;
		return true;
	}
	
	private void updateRegenStatus(LodDimension lodDim, float partialTicks) {
		short chunkRenderDistance = (short) MC_RENDER.getRenderDistance();
		long newTime = System.currentTimeMillis();
		AbstractBlockPosWrapper newPos = MC.getPlayerBlockPos();
		boolean shouldUpdateChunks = false;
		boolean tryPartialGen = false;
		boolean tryFullGen = false;

		// check if the view distance or config changed
		if (ApiShared.previousLodRenderDistance != CONFIG.client().graphics().quality().getLodChunkRenderDistance()
					|| chunkRenderDistance != prevRenderDistance
					|| prevFogDistance != CONFIG.client().graphics().fogQuality().getFogDistance())
		{
			DetailDistanceUtil.updateSettings(); // FIXME: This should NOT be here!
			prevFogDistance = CONFIG.client().graphics().fogQuality().getFogDistance();
			prevRenderDistance = chunkRenderDistance;
			tryFullGen = true;
		} else if (CONFIG.client().advanced().debugging().getDebugMode() != previousDebugMode)
		{ // did the user change the debug setting?
			previousDebugMode = CONFIG.client().advanced().debugging().getDebugMode();
			tryFullGen = true;
		}
		
		// check if the player has moved
		if (newTime - prevPlayerPosTime > CONFIG.client().advanced().buffers().getRebuildTimes().playerMoveTimeout) { 
			if (lastUpdatedPos == null
				|| Math.abs(newPos.getX() - lastUpdatedPos.getX()) > CONFIG.client().advanced().buffers().getRebuildTimes().playerMoveDistance*16
				|| Math.abs(newPos.getZ() - lastUpdatedPos.getZ()) > CONFIG.client().advanced().buffers().getRebuildTimes().playerMoveDistance*16)
			{
				shouldUpdateChunks = true;
			}
			prevPlayerPosTime = newTime;
		}

		// check if the vanilla rendered chunks changed
		if (newTime - prevVanillaChunkTime > CONFIG.client().advanced().buffers().getRebuildTimes().renderedChunkTimeout)
		{
			shouldUpdateChunks = true;
			prevVanillaChunkTime = newTime;
		}

		// check if there is any newly generated terrain to show
		if (newTime - prevChunkTime > CONFIG.client().advanced().buffers().getRebuildTimes().chunkChangeTimeout)
		{
			tryPartialGen = true;
			prevChunkTime = newTime;
		}
		
		shouldUpdateChunks |= tryFullGen;
		if (shouldUpdateChunks) {
			lastUpdatedPos = newPos;
			tryPartialGen |= updateVanillaRenderedChunks(lodDim);
		}
		
		if (tryFullGen) {
			fullRegen = true;
		} else if (tryPartialGen) {
			partialRegen = true;
		}
	}

}
