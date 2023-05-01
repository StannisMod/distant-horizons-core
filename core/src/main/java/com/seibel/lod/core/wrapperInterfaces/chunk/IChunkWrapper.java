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

package com.seibel.lod.core.wrapperInterfaces.chunk;

import com.seibel.lod.coreapi.interfaces.dependencyInjection.IBindable;
import com.seibel.lod.core.pos.DhChunkPos;
import com.seibel.lod.core.util.LodUtil;
import com.seibel.lod.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.lod.core.wrapperInterfaces.world.IBiomeWrapper;

/**
 * @author James Seibel
 * @version 3-16-2022
 */
public interface IChunkWrapper extends IBindable
{
	default int getHeight() {
		return getMaxBuildHeight()-getMinBuildHeight();
	}
	int getMinBuildHeight();
	int getMaxBuildHeight();

	// FIXME: getHeightMapValue & getMaxY is the same! Which one to keep?
	int getHeightMapValue(int xRel, int zRel);
	int getMaxY(int x, int z);
	
	int getMaxX();
	int getMaxZ();
	int getMinX();
	int getMinZ();

	long getLongChunkPos();
	
	boolean isLightCorrect();
	
	default int getBlockLight(int x, int y, int z) {return -1;}
	
	default int getSkyLight(int x, int y, int z) {return -1;}
	
	default boolean blockPosInsideChunk(int x, int y, int z) {
		return (x>=getMinX() && x<=getMaxX()
				&& y>=getMinBuildHeight() && y<getMaxBuildHeight()
				&& z>=getMinZ() && z<=getMaxZ());
	}
	
	boolean doesNearbyChunksExist();
	String toString();
	
	/** This is a bad hash algorithm, but can be used for rough debugging. */
	default int roughHashCode()
	{
		int hash = 31;
		int primeMultiplier = 227;
		
		for(int x = 0; x < LodUtil.CHUNK_WIDTH; x++)
		{
			for(int z = 0; z < LodUtil.CHUNK_WIDTH; z++)
			{
				hash = hash * primeMultiplier + Integer.hashCode(getMaxY(x, z));
			}
		}
		
		return hash;
	}

	IBlockStateWrapper getBlockState(int x, int y, int z);
	IBiomeWrapper getBiome(int x, int y, int z);

	DhChunkPos getChunkPos();

    boolean isStillValid();
}
