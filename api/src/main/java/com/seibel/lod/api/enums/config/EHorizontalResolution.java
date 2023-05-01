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

package com.seibel.lod.api.enums.config;

import java.util.ArrayList;
import java.util.Collections;

import com.seibel.lod.coreapi.util.MathUtil;

/**
 * BLOCK <Br>
 * TWO_BLOCKS <Br>
 * FOUR_BLOCKS <br>
 * HALF_CHUNK <Br>
 * CHUNK <br>
 * 
 * @author James Seibel
 * @author Leonardo Amato
 * @version 2022-7-5
 */
public enum EHorizontalResolution
{
	/** render 256 LODs for each chunk */
	BLOCK(16, 0),
	
	/** render 64 LODs for each chunk */
	TWO_BLOCKS(8, 1),
	
	/** render 16 LODs for each chunk */
	FOUR_BLOCKS(4, 2),
	
	/** render 4 LODs for each chunk */
	HALF_CHUNK(2, 3),
	
	/** render 1 LOD for each chunk */
	CHUNK(1, 4);
	
	
	
	
	
	/**
	 * How many DataPoints should
	 * be drawn per side, per LodChunk
	 */
	public final int dataPointLengthCount;
	
	/** How wide each LOD DataPoint is */
	public final int dataPointWidth;
	
	/**
	 * This is the same as detailLevel in LodQuadTreeNode,
	 * lowest is 0 highest is 9
	 */
	public final byte detailLevel;
	
	/* Start/End X/Z give the block positions
	 * for each individual dataPoint in a LodChunk */
	public final int[] startX;
	public final int[] startZ;
	
	public final int[] endX;
	public final int[] endZ;
	
	
	/**
	 * 1st dimension: LodDetail.detailLevel <br>
	 * 2nd dimension: An array of all LodDetails that are less than or <br>
	 * equal to that detailLevel
	 */
	private static EHorizontalResolution[][] lowerDetailArrays;
	
	
	
	
	EHorizontalResolution(int newLengthCount, int newDetailLevel)
	{
		detailLevel = (byte) newDetailLevel;
		dataPointLengthCount = newLengthCount;
		dataPointWidth = 16 / dataPointLengthCount;
		
		startX = new int[dataPointLengthCount * dataPointLengthCount];
		endX = new int[dataPointLengthCount * dataPointLengthCount];
		
		startZ = new int[dataPointLengthCount * dataPointLengthCount];
		endZ = new int[dataPointLengthCount * dataPointLengthCount];
		
		
		int index = 0;
		for (int x = 0; x < newLengthCount; x++)
		{
			for (int z = 0; z < newLengthCount; z++)
			{
				startX[index] = x * dataPointWidth;
				startZ[index] = z * dataPointWidth;
				
				endX[index] = (x * dataPointWidth) + dataPointWidth;
				endZ[index] = (z * dataPointWidth) + dataPointWidth;
				
				index++;
			}
		}
		
	}// constructor
	
	
	
	
	
	
	/**
	 * Returns an array of all LodDetails that have a detail level
	 * that is less than or equal to the given LodDetail
	 */
	public static EHorizontalResolution[] getSelfAndLowerDetails(EHorizontalResolution detail)
	{
		if (lowerDetailArrays == null)
		{
			// run first time setup
			lowerDetailArrays = new EHorizontalResolution[EHorizontalResolution.values().length][];
			
			// go through each LodDetail
			for (EHorizontalResolution currentDetail : EHorizontalResolution.values())
			{
				ArrayList<EHorizontalResolution> lowerDetails = new ArrayList<>();
				
				// find the details lower than currentDetail
				for (EHorizontalResolution compareDetail : EHorizontalResolution.values())
				{
					if (currentDetail.detailLevel <= compareDetail.detailLevel)
					{
						lowerDetails.add(compareDetail);
					}
				}
				
				// have the highest detail item first in the list
				Collections.sort(lowerDetails);
				Collections.reverse(lowerDetails);
				
				lowerDetailArrays[currentDetail.detailLevel] = lowerDetails.toArray(new EHorizontalResolution[lowerDetails.size()]);
			}
		}
		
		return lowerDetailArrays[detail.detailLevel];
	}
	
	/** Returns what detail level should be used at a given distance and maxDistance. */
	public static EHorizontalResolution getDetailForDistance(EHorizontalResolution maxDetailLevel, int distance, int maxDistance)
	{
		EHorizontalResolution[] lowerDetails = getSelfAndLowerDetails(maxDetailLevel);
		int distanceBetweenDetails = maxDistance / lowerDetails.length;
		int index = MathUtil.clamp(0, distance / distanceBetweenDetails, lowerDetails.length - 1);
		
		return lowerDetails[index];
		
	}
	
}
