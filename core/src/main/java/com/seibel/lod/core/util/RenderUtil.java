/*
 *    This file is part of the Distant Horizons mod (formerly the LOD Mod),
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020-2022  James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.lod.core.util;

import com.seibel.lod.core.level.IDhClientLevel;
import com.seibel.lod.core.world.IDhClientWorld;
import com.seibel.lod.core.api.internal.SharedApi;
import com.seibel.lod.core.config.Config;
import com.seibel.lod.core.dependencyInjection.SingletonInjector;
import com.seibel.lod.core.pos.DhBlockPos;
import com.seibel.lod.core.pos.DhChunkPos;
import com.seibel.lod.coreapi.util.MathUtil;
import com.seibel.lod.coreapi.util.math.Mat4f;
import com.seibel.lod.coreapi.util.math.Vec3f;
import com.seibel.lod.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.lod.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.lod.core.wrapperInterfaces.world.ILevelWrapper;

/**
 * This holds miscellaneous helper code
 * to be used in the rendering process.
 * 
 * @author James Seibel
 * @version 2022-8-21
 */
public class RenderUtil
{
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	
	//=================//
	// culling methods //
	//=================//
	
	/**
	 * Returns if the given ChunkPos is in the loaded area of the world.
	 * @param center the center of the loaded world (probably the player's ChunkPos)
	 */
	public static boolean isChunkPosInLoadedArea(DhChunkPos pos, DhChunkPos center)
	{
		return (pos.getX() >= center.getX() - MC_RENDER.getRenderDistance()
				&& pos.getX() <= center.getX() + MC_RENDER.getRenderDistance())
				&&
				(pos.getZ() >= center.getZ() - MC_RENDER.getRenderDistance()
				&& pos.getZ() <= center.getZ() + MC_RENDER.getRenderDistance());
	}
	
	/**
	 * Returns if the given coordinate is in the loaded area of the world.
	 * @param centerCoordinate the center of the loaded world
	 */
	public static boolean isCoordinateInLoadedArea(int x, int z, int centerCoordinate)
	{
		return (x >= centerCoordinate - MC_RENDER.getRenderDistance()
				&& x <= centerCoordinate + MC_RENDER.getRenderDistance())
				&&
				(z >= centerCoordinate - MC_RENDER.getRenderDistance()
						&& z <= centerCoordinate + MC_RENDER.getRenderDistance());
	}
	
	/**
	 * Find the coordinates that are in the center half of the given
	 * 2D matrix, starting at (0,0) and going to (2 * lodRadius, 2 * lodRadius).
	 */
	public static boolean isCoordinateInNearFogArea(int i, int j, int lodRadius)
	{
		int halfRadius = lodRadius / 2;
		
		return (i >= lodRadius - halfRadius
				&& i <= lodRadius + halfRadius)
				&&
				(j >= lodRadius - halfRadius
						&& j <= lodRadius + halfRadius);
	}
	
	/**
	 * Returns true if one of the region's 4 corners is in front
	 * of the camera.
	 */
	public static boolean isRegionInViewFrustum(DhBlockPos playerBlockPos, Vec3f cameraDir, int vboRegionX, int vboRegionZ)
	{
		// convert the vbo position into a direction vector
		// starting from the player's position
		Vec3f vboVec = new Vec3f(vboRegionX * LodUtil.REGION_WIDTH, 0, vboRegionZ * LodUtil.REGION_WIDTH);
		Vec3f playerVec = new Vec3f(playerBlockPos.getX(), playerBlockPos.getY(), playerBlockPos.getZ());
		
		vboVec.subtract(playerVec);
		
		// calculate the 4 corners
		Vec3f vboSeVec = new Vec3f(vboVec.x + LodUtil.REGION_WIDTH, vboVec.y, vboVec.z + LodUtil.REGION_WIDTH);
		Vec3f vboSwVec = new Vec3f(vboVec.x                       , vboVec.y, vboVec.z + LodUtil.REGION_WIDTH);
		Vec3f vboNwVec = new Vec3f(vboVec.x                       , vboVec.y, vboVec.z);
		Vec3f vboNeVec = new Vec3f(vboVec.x + LodUtil.REGION_WIDTH, vboVec.y, vboVec.z);
		
		// if any corner is visible, this region should be rendered
		return isNormalizedVectorInViewFrustum(vboSeVec, cameraDir) ||
				isNormalizedVectorInViewFrustum(vboSwVec, cameraDir) ||
				isNormalizedVectorInViewFrustum(vboNwVec, cameraDir) ||
				isNormalizedVectorInViewFrustum(vboNeVec, cameraDir);
	}
	
	/**
	 * Currently takes the dot product of the two vectors,
	 * but in the future could do more complicated frustum culling tests.
	 */
	private static boolean isNormalizedVectorInViewFrustum(Vec3f objectVector, Vec3f cameraDir)
	{
		// the -0.1 is to offer a slight buffer, so we are
		// more likely to render LODs and thus, hopefully prevent
		// flickering or odd disappearances
		return objectVector.dotProduct(cameraDir) > -0.1;
	}
	
	
	
	//=====================//
	// matrix manipulation //
	//=====================//
	
	/**
	 * create and return a new projection matrix based on MC's modelView and projection matrices
	 * @param mcProjMat Minecraft's current projection matrix
	 */
	public static Mat4f createLodProjectionMatrix(Mat4f mcProjMat, float partialTicks)
	{
		int farPlaneDistanceInBlocks = RenderUtil.getFarClipPlaneDistanceInBlocks();
		
		// Create a copy of the current matrix, so it won't be modified.
		Mat4f lodProj = mcProjMat.copy();
		
		// Set new far and near clip plane values.
		lodProj.setClipPlanes(
				getNearClipPlaneDistanceInBlocks(partialTicks),
				(float)((farPlaneDistanceInBlocks+LodUtil.REGION_WIDTH) * Math.sqrt(2)));
		
		return lodProj;
	}
	
	/** create and return a new projection matrix based on MC's modelView and projection matrices */
	public static Mat4f createLodModelViewMatrix(Mat4f mcModelViewMat)
	{
		// nothing beyond copying needs to be done to MC's MVM currently,
		// this method is just here in case that changes in the future
		return mcModelViewMat.copy();
	}
	
	/**
	 * create and return a new combined modelView/projection matrix based on MC's modelView and projection matrices
	 * @param mcProjMat Minecraft's current projection matrix
	 * @param mcModelViewMat Minecraft's current model view matrix
	 */
	public static Mat4f createCombinedModelViewProjectionMatrix(Mat4f mcProjMat, Mat4f mcModelViewMat, float partialTicks)
	{
		Mat4f lodProj = createLodProjectionMatrix(mcProjMat, partialTicks);
		lodProj.multiply(createLodModelViewMatrix(mcModelViewMat));
		return lodProj;
	}
	
	public static float getNearClipPlaneDistanceInBlocks(float partialTicks)
	{
		int vanillaBlockRenderedDistance = MC_RENDER.getRenderDistance() * LodUtil.CHUNK_WIDTH;
		
		float nearClipPlane;
		if (Config.Client.Advanced.lodOnlyMode.get())
		{
			nearClipPlane = 0.1f;
		}
		else if (Config.Client.Graphics.AdvancedGraphics.useExtendedNearClipPlane.get())
		{
			nearClipPlane = Math.min(vanillaBlockRenderedDistance - LodUtil.CHUNK_WIDTH, (float) 8 * LodUtil.CHUNK_WIDTH); // allow a max near clip plane of 8 chunks
		}
		else
		{
			nearClipPlane = 16f;
		}
		
		// modify the based on the player's FOV
		double fov = MC_RENDER.getFov(partialTicks);
		double aspectRatio = (double) MC_RENDER.getScreenWidth() / MC_RENDER.getScreenHeight();
		
		return (float) (nearClipPlane
				/ Math.sqrt(1d + MathUtil.pow2(Math.tan(fov / 180d * Math.PI / 2d))
				* (MathUtil.pow2(aspectRatio) + 1d)));
	}
	public static int getFarClipPlaneDistanceInBlocks()
	{
		int lodChunkDist = Config.Client.Graphics.Quality.lodChunkRenderDistance.get();
		return lodChunkDist * LodUtil.CHUNK_WIDTH;
	}
	
	/** @return false if LODs shouldn't be rendered for any reason */
	public static boolean shouldLodsRender(ILevelWrapper levelWrapper)
	{
		if (!MC.playerExists())
		{
			return false;
		}
		
		if (levelWrapper == null)
		{
			return false;
		}
		
		IDhClientWorld clientWorld = SharedApi.getIDhClientWorld();
		if (clientWorld == null)
		{
			return false;
		}
		
		IDhClientLevel level = clientWorld.getOrLoadClientLevel(levelWrapper);
		if (level == null)
		{
			return false; //Level is not ready yet.
		}
		
		if (MC_RENDER.playerHasBlindnessEffect())
		{
			// if the player is blind, don't render LODs,
			// and don't change minecraft's fog
			// which blindness relies on.
			return false;
		}
		
		if (MC_RENDER.getLightmapWrapper() == null)
		{
			return false;
		}
		
		return true;
	}
	
}
