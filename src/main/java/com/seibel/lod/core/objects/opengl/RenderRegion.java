/*
 *    This file is part of the Distant Horizons mod (formerly the LOD Mod),
 *    licensed under the GNU GPL v3 License.
 *
 *    Copyright (C) 2020-2022  James Seibel
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
 
package com.seibel.lod.core.objects.opengl;

import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.seibel.lod.core.api.ClientApi;
import com.seibel.lod.core.builders.lodBuilding.bufferBuilding.CubicLodTemplate;
import com.seibel.lod.core.builders.lodBuilding.LodBuilder;
import com.seibel.lod.core.builders.lodBuilding.bufferBuilding.LodQuadBuilder;
import com.seibel.lod.core.enums.LodDirection;
import com.seibel.lod.core.enums.config.GpuUploadMethod;
import com.seibel.lod.core.enums.rendering.DebugMode;
import com.seibel.lod.core.enums.rendering.GLProxyContext;
import com.seibel.lod.core.handlers.dependencyInjection.SingletonHandler;
import com.seibel.lod.core.objects.BoolType;
import com.seibel.lod.core.objects.PosToRenderContainer;
import com.seibel.lod.core.objects.lod.LodDimension;
import com.seibel.lod.core.objects.lod.LodRegion;
import com.seibel.lod.core.objects.lod.RegionPos;
import com.seibel.lod.core.objects.math.Vec3d;
import com.seibel.lod.core.objects.math.Vec3f;
import com.seibel.lod.core.render.GLProxy;
import com.seibel.lod.core.render.LodRenderProgram;
import com.seibel.lod.core.render.RenderUtil;
import com.seibel.lod.core.util.DataPointUtil;
import com.seibel.lod.core.util.LevelPosUtil;
import com.seibel.lod.core.util.LodUtil;
import com.seibel.lod.core.util.StatsMap;
import com.seibel.lod.core.util.gridList.PosArrayGridList;
import com.seibel.lod.core.wrapperInterfaces.block.AbstractBlockPosWrapper;
import com.seibel.lod.core.wrapperInterfaces.config.ILodConfigWrapperSingleton;

import static com.seibel.lod.core.render.LodRenderer.EVENT_LOGGER;

public class RenderRegion implements AutoCloseable
{
	private static final ILodConfigWrapperSingleton CONFIG = SingletonHandler.get(ILodConfigWrapperSingleton.class);
	
	/** stores if the region at the given x and z index needs to be regenerated */
	// Use int because I need Tri state:
	private final AtomicInteger needRegen = new AtomicInteger(2);
	
	private enum BackState {
		Unused,
		Building,
		Complete,
	}
	private enum FrontState {
		Unused,
		Rendering,
		Invalidated,
	}
	
	final RegionPos regionPos;
	RenderBuffer renderBufferBack = null;
	AtomicReference<BackState> backState =
			new AtomicReference<BackState>(BackState.Unused);
	AtomicReference<FrontState> frontState =
			new AtomicReference<FrontState>(FrontState.Unused);
	RenderBuffer renderBufferFront = null;
	final LodDimension lodDim;
	
	public RenderRegion(RegionPos regPos, LodDimension lodDim) {
		regionPos = regPos;
		this.lodDim = lodDim;
	}
	
	public boolean canRender(LodDimension lodDim, RegionPos regPos) {
		return lodDim == this.lodDim && regPos.equals(regionPos);
	}
	
	public void setNeedRegen() {
		needRegen.set(2);
	} 
	
	public Optional<CompletableFuture<Void>> updateStatus(Executor bufferUploader, Executor bufferBuilder, boolean alwaysRegen, int playerPosX, int playerPosZ, boolean doCaveCulling) {
		if (alwaysRegen) setNeedRegen();
		
		BackState state = backState.get();
		if (state != BackState.Unused) {
			EVENT_LOGGER.trace("{}: UpdateStatus rejected. Cause: BackState is {}", regionPos, state);
			return Optional.empty();
		}

		LodRegion r = lodDim.getRegion(regionPos.x, regionPos.z);
		if (r==null) {
			EVENT_LOGGER.trace("{}: UpdateStatus rejected. Cause: Region is null", regionPos);
			return Optional.empty();
		}
		if (needRegen.get() == 0) {
			EVENT_LOGGER.trace("{}: UpdateStatus rejected. Cause: Region doesn't need regen", regionPos);
			return Optional.empty();
		}
		
		if (!backState.compareAndSet(BackState.Unused, BackState.Building)) {
			EVENT_LOGGER.trace("{}: UpdateStatus rejected. Cause: CAS on BackState failed: ", backState.get());
			return Optional.empty();
		}
		needRegen.decrementAndGet();
		return Optional.of(startBuid(bufferUploader, bufferBuilder, r, lodDim, playerPosX, playerPosZ, doCaveCulling));
	}
	
	public boolean render(LodDimension renderDim,
			Vec3d cameraPos, AbstractBlockPosWrapper cameraBlockPos, Vec3f cameraDir,
			boolean enableDirectionalCulling, LodRenderProgram program) {
		if (!frontState.compareAndSet(FrontState.Unused, FrontState.Rendering)) return false;
		try {
		if (renderDim != lodDim) return false;
		if (enableDirectionalCulling &&
				!RenderUtil.isRegionInViewFrustum(cameraBlockPos,
						cameraDir, regionPos.x, regionPos.z)) return false;
		BackState state = backState.get();
		if (state == BackState.Complete) {
			if (renderBufferBack != null) {
				EVENT_LOGGER.debug("RenderRegion swap @ {}", regionPos);
				boolean shouldKeep = renderBufferFront != null && renderBufferFront.onSwapToBack();
				RenderBuffer temp = shouldKeep ? renderBufferFront : null;
				renderBufferFront = renderBufferBack;
				renderBufferBack = temp;
				if (renderBufferFront != null) renderBufferFront.onSwapToFront();
			}
			if (!backState.compareAndSet(BackState.Complete, BackState.Unused)) {
				EVENT_LOGGER.error("RenderRegion.render() got illegal state on swapping buffer!");
			}
		}
		if (renderBufferFront == null) return false;
		program.setModelPos(new Vec3f(
				(float) ((regionPos.x * LodUtil.REGION_WIDTH) - cameraPos.x),
				(float) (LodBuilder.MIN_WORLD_HEIGHT - cameraPos.y),
				(float) ((regionPos.z * LodUtil.REGION_WIDTH) - cameraPos.z)));

		return renderBufferFront.render(program);
		} finally {
			frontState.compareAndSet(FrontState.Rendering, FrontState.Unused);
		}
		 
	}
	
	private void recreateBuffer(LodQuadBuilder builder) {
		if (renderBufferBack != null) throw new RuntimeException("Assert Error");
		boolean useSimpleBuffer = (builder.getCurrentNeededVertexBufferCount() <= 6) || true;
		renderBufferBack = useSimpleBuffer ?
				new SimpleRenderBuffer()
				: null; //new ComplexRenderRegion(regPos);
	}
	
	private CompletableFuture<Void> startBuid(Executor bufferUploader, Executor bufferBuilder, LodRegion region, LodDimension lodDim, int playerPosX, int playerPosZ, boolean doCaveCulling) {
		EVENT_LOGGER.trace("RenderRegion startBuild @ {}", regionPos);
		LodRegion[] adjRegions = new LodRegion[4];
		try {
			if (renderBufferBack != null) renderBufferBack.onReuse();
			for (LodDirection dir : LodDirection.ADJ_DIRECTIONS) {
				adjRegions[dir.ordinal() - 2] = lodDim.getRegion(regionPos.x+dir.getNormal().x, regionPos.z+dir.getNormal().z);
			}
		} catch (NullPointerException | ArrayIndexOutOfBoundsException e) { // HOTFIX: Error ignoring for a concurrency caused issue
			setNeedRegen();
			if (!backState.compareAndSet(BackState.Building, BackState.Unused)) {
				EVENT_LOGGER.error("\"Lod Builder Starter\""
								+ " encountered error on catching exceptions and fallback on starting build task: ",
						new ConcurrentModificationException("RenderRegion Illegal State"));
			}
			EVENT_LOGGER.info("\"Lod Builder Starter\" failed due to possible known concurrency issue: ", e);
			return CompletableFuture.completedFuture(null);
		} catch (Throwable t) {
			setNeedRegen();
			if (!backState.compareAndSet(BackState.Building, BackState.Unused)) {
				EVENT_LOGGER.error("\"Lod Builder Starter\""
					+ " encountered error on catching exceptions and fallback on starting build task: ",
					new ConcurrentModificationException("RenderRegion Illegal State"));
			}
			throw t;
		}
		return CompletableFuture.supplyAsync(() -> {
			try {
				EVENT_LOGGER.trace("RenderRegion start QuadBuild @ {}", regionPos);
				int skyLightCullingBelow = CONFIG.client().graphics().advancedGraphics().getCaveCullingHeight();
				// FIXME: Clamp also to the max world height.
				skyLightCullingBelow = Math.max(skyLightCullingBelow, LodBuilder.MIN_WORLD_HEIGHT);
				LodQuadBuilder builder = new LodQuadBuilder(doCaveCulling, skyLightCullingBelow);
				Runnable buildRun = ()->{
					makeLodRenderData(builder, region, adjRegions, playerPosX, playerPosZ);
				};
				if (renderBufferBack != null)
					renderBufferBack.build(buildRun);
				else
					buildRun.run();
				EVENT_LOGGER.trace("RenderRegion end QuadBuild @ {}", regionPos);
				return builder;
			} catch (NullPointerException | ArrayIndexOutOfBoundsException e) {
				EVENT_LOGGER.info("\"LodNodeBufferBuilder\" failed due to possible known concurrency issue: ", e);
				throw e; // HOTFIX: Error ignoring for a concurrency caused issue
			} catch (Throwable e3) {
				EVENT_LOGGER.error("\"LodNodeBufferBuilder\" was unable to build quads: ", e3);
				throw e3;
			}
		}, bufferBuilder)
				
				.thenAcceptAsync((builder) -> {
			try {
				EVENT_LOGGER.trace("RenderRegion start Upload @ {}", regionPos);
				GLProxy glProxy = GLProxy.getInstance();
				GpuUploadMethod method = GLProxy.getInstance().getGpuUploadMethod();
				GLProxyContext oldContext = glProxy.getGlContext();
				glProxy.setGlContext(GLProxyContext.LOD_BUILDER);
				try {
					if (renderBufferBack == null) recreateBuffer(builder);
					if (!renderBufferBack.tryUploadBuffers(builder, method)) {
						renderBufferBack = null;
						recreateBuffer(builder);
						if (!renderBufferBack.tryUploadBuffers(builder, method)) {
							throw new RuntimeException("Newly created renderBuffer "
									+ "is still returning false on tryUploadBuffers!");
						}
					}
				} finally {
					glProxy.setGlContext(oldContext);
				}
				EVENT_LOGGER.trace("RenderRegion end Upload @ {}", regionPos);
			} catch (Throwable e3) {
				EVENT_LOGGER.error("\"LodNodeBufferBuilder\" was unable to upload buffer: ", e3);
				throw e3;
			}
		}, bufferUploader).handle((v, e) -> {
			if (e != null) {
				setNeedRegen();
				if (!backState.compareAndSet(BackState.Building, BackState.Unused)) {
					EVENT_LOGGER.error("\"LodNodeBufferBuilder\""
						+ " encountered error on exit: ",
						new ConcurrentModificationException("RenderRegion Illegal State"));
				}
			} else {
				if (!backState.compareAndSet(BackState.Building, BackState.Complete)) {
					EVENT_LOGGER.error("\"LodNodeBufferBuilder\""
							+ " encountered error on exit: ",
							new ConcurrentModificationException("RenderRegion Illegal State"));
				}
			}
			return (Void) null;
		});
	}

	private static final int ADJACENT8[][] = {
			{-1,-1},
			{-1, 0},
			{-1, 1},
			{ 0,-1},
			//{ 0, 0},
			{ 0, 1},
			{ 1,-1},
			{ 1, 0},
			{ 1, 1}
	};

	private static void makeLodRenderData(LodQuadBuilder quadBuilder, LodRegion region, LodRegion[] adjRegions, int playerX,
			int playerZ) {
		byte minDetail = region.getMinDetailLevel();

		// Variable initialization
		DebugMode debugMode = CONFIG.client().advanced().debugging().getDebugMode();

		// We ask the lod dimension which block we have to render given the player
		// position
		PosToRenderContainer posToRender = new PosToRenderContainer(minDetail, region.regionPosX, region.regionPosZ);
		region.getPosToRender(posToRender, playerX, playerZ);
		PosArrayGridList<BoolType> chunkGrid = ClientApi.renderer.vanillaChunks;

		for (int index = 0; index < posToRender.getNumberOfPos(); index++) {

			byte detailLevel = posToRender.getNthDetailLevel(index);
			int posX = posToRender.getNthPosX(index);
			int posZ = posToRender.getNthPosZ(index);

			// TODO: In the future, We don't need to ignore rendered chunks! Just build it
			// and leave it for the renderer to decide!
			// We don't want to render this fake block if
			// The block is inside the render distance with, is not bigger than a chunk and
			// is positioned in a chunk set as vanilla rendered

			// The block is in the player chunk or in a chunk adjacent to the player
			if (detailLevel <= LodUtil.CHUNK_DETAIL_LEVEL) {
				int chunkX = LevelPosUtil.getChunkPos(detailLevel, posX);
				int chunkZ = LevelPosUtil.getChunkPos(detailLevel, posZ);
				// skip any chunks that Minecraft is going to render
				if (chunkGrid != null && chunkGrid.get(chunkX, chunkZ) != null) continue;
			}

			long[] posData = region.getAllData(detailLevel, posX, posZ);
			if (posData == null || posData.length == 0 || !DataPointUtil.doesItExist(posData[0])
					|| DataPointUtil.isVoid(posData[0]))
				continue;

			long[][][] adjData = new long[4][][];
			boolean[] adjUseBlack = new boolean[4];

			// We extract the adj data in the four cardinal direction

			// we first reset the adjShadeDisabled. This is used to disable the shade on the
			// border when we have transparent block like water or glass
			// to avoid having a "darker border" underground
			// Arrays.fill(adjShadeDisabled, false);

			// We check every adj block in each direction

			// If the adj block is rendered in the same region and with same detail
			// and is positioned in a place that is not going to be rendered by vanilla game
			// then we can set this position as adj
			// We avoid cases where the adjPosition is in player chunk while the position is
			// not
			// to always have a wall underwater

			/**TODO REMOVE THIS UGLY TRANSPARENCY THING AND MAKE IT MORE SIMPLE*/
			for (int transparency = 0; transparency <= 0; transparency++)
			{
				for (LodDirection lodDirection : LodDirection.ADJ_DIRECTIONS) {
					try {
						int xAdj = posX + lodDirection.getNormal().x;
						int zAdj = posZ + lodDirection.getNormal().z;
						int chunkXAdj = LevelPosUtil.getChunkPos(detailLevel, xAdj);
						int chunkZAdj = LevelPosUtil.getChunkPos(detailLevel, zAdj);
						if (chunkGrid != null && chunkGrid.get(chunkXAdj, chunkZAdj) != null) {
							adjUseBlack[lodDirection.ordinal() - 2] = true;
						}

						boolean isCrossRegionBoundary = LevelPosUtil.getRegion(detailLevel, xAdj) != region.regionPosX ||
								LevelPosUtil.getRegion(detailLevel, zAdj) != region.regionPosZ;

						LodRegion adjRegion;
						byte adjDetail;
						int childXAdj = xAdj * 2 + (lodDirection.getNormal().x < 0 ? 1 : 0);
						int childZAdj = zAdj * 2 + (lodDirection.getNormal().z < 0 ? 1 : 0);

						//we check if the detail of the adjPos is equal to the correct one (region border fix)
						//or if the detail is wrong by 1 value (region+circle border fix)
						if (isCrossRegionBoundary) {
							//we compute at which detail that position should be rendered
							adjRegion = adjRegions[lodDirection.ordinal() - 2];
							if (adjRegion == null) continue;
							adjDetail = adjRegion.getRenderDetailLevelAt(playerX, playerZ, detailLevel, xAdj, zAdj);
						} else {
							adjRegion = region;
							if (posToRender.contains(detailLevel, xAdj, zAdj)) adjDetail = detailLevel;
							else if (detailLevel > 0 &&
									posToRender.contains((byte) (detailLevel - 1), childXAdj, childZAdj))
								adjDetail = (byte) (detailLevel - 1);
							else if (detailLevel < LodUtil.REGION_DETAIL_LEVEL &&
									posToRender.contains((byte) (detailLevel + 1), xAdj / 2, zAdj / 2))
								adjDetail = (byte) (detailLevel + 1);
							else continue;
						}

						if (adjDetail < detailLevel - 1 || adjDetail > detailLevel + 1) {
							continue;
						}

						if (adjDetail == detailLevel || adjDetail > detailLevel) {
							adjData[lodDirection.ordinal() - 2] = new long[1][];
							adjData[lodDirection.ordinal() - 2][0] = adjRegion.getAllData(adjDetail,
									LevelPosUtil.convert(detailLevel, xAdj, adjDetail),
									LevelPosUtil.convert(detailLevel, zAdj, adjDetail));
						} else {
							adjData[lodDirection.ordinal() - 2] = new long[2][];
							adjData[lodDirection.ordinal() - 2][0] = adjRegion.getAllData(adjDetail,
									childXAdj, childZAdj);
							adjData[lodDirection.ordinal() - 2][1] = adjRegion.getAllData(adjDetail,
									childXAdj + (lodDirection.getAxis() == LodDirection.Axis.X ? 0 : 1),
									childZAdj + (lodDirection.getAxis() == LodDirection.Axis.Z ? 0 : 1));
						}
					} catch (RuntimeException e) {
						EVENT_LOGGER.warn("Failed to get adj data for [{}:{},{}] at [{}]", detailLevel, posX, posZ, lodDirection);
						EVENT_LOGGER.warn("Detail exception: ", e);
					}
				}

			// We render every vertical lod present in this position
			// We only stop when we find a block that is void or non-existing block
			for (int i = 0; i < posData.length; i++) {
				long data = posData[i];
				if (DataPointUtil.isTransparent(data) == (transparency == 0))
					continue;
				// If the data is not renderable (Void or non-existing) we stop since there is
				// no data left in this position
				if (DataPointUtil.isVoid(data) || !DataPointUtil.doesItExist(data))
					break;

				long adjDataTop = i - 1 >= 0 ? posData[i - 1] : DataPointUtil.EMPTY_DATA;
				long adjDataBot = i + 1 < posData.length ? posData[i + 1] : DataPointUtil.EMPTY_DATA;

				// We send the call to create the vertices
				CubicLodTemplate.addLodToBuffer(data, adjDataTop, adjDataBot, adjData, adjUseBlack, detailLevel,
						LevelPosUtil.getRegionModule(detailLevel, posX),
						LevelPosUtil.getRegionModule(detailLevel, posZ), quadBuilder, debugMode);
			}
		}
	}// for pos to in list to render
			// the thread executed successfully
		// Merge all quads
		quadBuilder.mergeQuads();
	}

	
	@Override
	public void close()
	{
		if (renderBufferBack != null) renderBufferBack.close();
		while (frontState.get() != FrontState.Invalidated && !frontState.compareAndSet(FrontState.Unused, FrontState.Invalidated)) {
			Thread.yield(); //FIXME: If on java 9, use Thread.onSpinWait();
		}
		if (renderBufferFront != null) renderBufferFront.close();
	}

	public void debugDumpStats(StatsMap statsMap)
	{
		statsMap.incStat("RenderRegions");
		RenderBuffer front = renderBufferFront;
		if (front!=null) {
			statsMap.incStat("FrontBuffers");
			front.debugDumpStats(statsMap);
		}
		
		RenderBuffer back = renderBufferBack;
		if (back!=null) {
			statsMap.incStat("BackBuffers");
			back.debugDumpStats(statsMap);
		}
		
		
	}
}
