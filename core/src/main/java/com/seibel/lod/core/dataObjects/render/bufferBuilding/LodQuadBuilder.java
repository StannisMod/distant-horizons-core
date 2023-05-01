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

package com.seibel.lod.core.dataObjects.render.bufferBuilding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Objects;

import com.seibel.lod.core.render.AbstractRenderBuffer;
import com.seibel.lod.core.enums.ELodDirection;
import com.seibel.lod.core.enums.ELodDirection.Axis;
import com.seibel.lod.api.enums.config.EGpuUploadMethod;
import com.seibel.lod.core.logging.DhLoggerBuilder;
import com.seibel.lod.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.lod.core.util.ColorUtil;
import com.seibel.lod.coreapi.util.MathUtil;
import org.apache.logging.log4j.Logger;

//TODO: Recheck this class for refactoring

/**
 * Used to create the quads before they are converted to renderable buffers.
 *
 * @version 2022-4-9
 */
public class LodQuadBuilder
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	public final boolean skipQuadsWithZeroSkylight;
	public final short skyLightCullingBelow;
	@SuppressWarnings("unchecked")
	final ArrayList<BufferQuad>[] opaqueQuads = (ArrayList<BufferQuad>[]) new ArrayList[6];
	@SuppressWarnings("unchecked")
	final ArrayList<BufferQuad>[] transparentQuads = (ArrayList<BufferQuad>[]) new ArrayList[6];
	final boolean doTransparency;
	
	public static final int[][][] DIRECTION_VERTEX_IBO_QUAD = new int[][][]
		{
			// X,Z //
			{ // UP
				{ 1, 0 }, // 0
				{ 1, 1 }, // 1
				{ 0, 1 }, // 2
				{ 0, 0 }, // 3
			},
			{ // DOWN
				{ 0, 0 }, // 0
				{ 0, 1 }, // 1
				{ 1, 1 }, // 2
				{ 1, 0 }, // 3
			},
			
			// X,Y //
			{ // NORTH
				{ 0, 0 }, // 0
				{ 0, 1 }, // 1
				{ 1, 1 }, // 2

				{ 1, 0 }, // 3
			},
			{ // SOUTH
				{ 1, 0 }, // 0
				{ 1, 1 }, // 1
				{ 0, 1 }, // 2

				{ 0, 0 }, // 3
			},
			
			// Z,Y //
			{ // WEST
				{ 0, 0 }, // 0
				{ 1, 0 }, // 1
				{ 1, 1 }, // 2

				{ 0, 1 }, // 3
			},
			{ // EAST
				{ 0, 1 }, // 0
				{ 1, 1 }, // 1
				{ 1, 0 }, // 2

				{ 0, 0 }, // 3
			},
		};
	
	private int premergeCount = 0;

	
	
	public LodQuadBuilder(boolean enableSkylightCulling, short skyLightCullingBelow, boolean doTransparency)
	{
		this.doTransparency = doTransparency;
		for (int i = 0; i < 6; i++) {
			opaqueQuads[i] = new ArrayList<>();
			if (doTransparency) transparentQuads[i] = new ArrayList<>();
		}
		this.skipQuadsWithZeroSkylight = enableSkylightCulling;
		this.skyLightCullingBelow = skyLightCullingBelow;
	}
	
	
	
	public void addQuadAdj(ELodDirection dir, short x, short y, short z,
			short widthEastWest, short widthNorthSouthOrUpDown,
			int color, byte skylight, byte blocklight)
	{
		if (dir.ordinal() <= ELodDirection.DOWN.ordinal())
			throw new IllegalArgumentException("addQuadAdj() is only for adj direction! Not UP or Down!");
		if (skipQuadsWithZeroSkylight && skylight == 0 && y+widthNorthSouthOrUpDown < skyLightCullingBelow)
			return;
		BufferQuad quad = new BufferQuad(x, y, z, widthEastWest, widthNorthSouthOrUpDown, color, skylight, blocklight, dir);
		ArrayList<BufferQuad> qs = (doTransparency && ColorUtil.getAlpha(color) < 255)
				? transparentQuads[dir.ordinal()] : opaqueQuads[dir.ordinal()];
		if (!qs.isEmpty() &&
				(qs.get(qs.size()-1).tryMerge(quad, BufferMergeDirectionEnum.EastWest)
				|| qs.get(qs.size()-1).tryMerge(quad, BufferMergeDirectionEnum.NorthSouthOrUpDown))
			) {
			premergeCount++;
			return;
		}
		qs.add(quad);
	}
	
	// XZ
	public void addQuadUp(short x, short y, short z, short width, short wz, int color, byte skylight, byte blocklight)
	{
		if (skipQuadsWithZeroSkylight && skylight == 0 && y < skyLightCullingBelow)
			return;
		BufferQuad quad = new BufferQuad(x, y, z, width, wz, color, skylight, blocklight, ELodDirection.UP);
		ArrayList<BufferQuad> qs = (doTransparency && ColorUtil.getAlpha(color) < 255)
				? transparentQuads[ELodDirection.UP.ordinal()] : opaqueQuads[ELodDirection.UP.ordinal()];
		if (!qs.isEmpty() &&
				(qs.get(qs.size()-1).tryMerge(quad, BufferMergeDirectionEnum.EastWest)
						|| qs.get(qs.size()-1).tryMerge(quad, BufferMergeDirectionEnum.NorthSouthOrUpDown))
		) {
			premergeCount++;
			return;
		}
		qs.add(quad);
	}
	
	public void addQuadDown(short x, short y, short z, short width, short wz, int color, byte skylight, byte blocklight)
	{
		if (skipQuadsWithZeroSkylight && skylight == 0 && y < skyLightCullingBelow)
			return;
		BufferQuad quad = new BufferQuad(x, y, z, width, wz, color, skylight, blocklight, ELodDirection.DOWN);
		ArrayList<BufferQuad> qs = (doTransparency && ColorUtil.getAlpha(color) < 255)
				? transparentQuads[ELodDirection.DOWN.ordinal()] : opaqueQuads[ELodDirection.DOWN.ordinal()];
		if (!qs.isEmpty() &&
				(qs.get(qs.size()-1).tryMerge(quad, BufferMergeDirectionEnum.EastWest)
						|| qs.get(qs.size()-1).tryMerge(quad, BufferMergeDirectionEnum.NorthSouthOrUpDown))
		) {
			premergeCount++;
			return;
		}
		qs.add(quad);
	}
	
	private static void putVertex(ByteBuffer bb, short x, short y, short z, int color, byte skylight, byte blocklight, int mx, int my, int mz)
	{
		skylight %= 16;
		blocklight %= 16;
		
		bb.putShort(x);
		bb.putShort(y);
		bb.putShort(z);

		short meta = 0;
		meta |= (skylight | (blocklight << 4));
		byte mirco = 0;
		// mirco offset which is a xyz 2bit value
		// 0b00 = no offset
		// 0b01 = positive offset
		// 0b11 = negative offset
		// format is: 0b00zzyyxx
		if (mx != 0) mirco |= mx > 0 ? 0b01 : 0b11;
		if (my != 0) mirco |= my > 0 ? 0b0100 : 0b1100;
		if (mz != 0) mirco |= mz > 0 ? 0b010000 : 0b110000;
		meta |= mirco << 8;

		bb.putShort(meta);
		byte r = (byte) ColorUtil.getRed(color);
		byte g = (byte) ColorUtil.getGreen(color);
		byte b = (byte) ColorUtil.getBlue(color);
		byte a = (byte) ColorUtil.getAlpha(color);
		bb.put(r);
		bb.put(g);
		bb.put(b);
		bb.put(a);
	}
	
	private static void putQuad(ByteBuffer bb, BufferQuad quad)
	{
		int[][] quadBase = DIRECTION_VERTEX_IBO_QUAD[quad.direction.ordinal()];
		short widthEastWest = quad.widthEastWest;
		short widthNorthSouth = quad.widthNorthSouthOrUpDown;
		Axis axis = quad.direction.getAxis();
		for (int i = 0; i < quadBase.length; i++)
		{
			short dx, dy, dz;
			int mx, my, mz;
			switch (axis)
			{
			case X: // ZY
				dx = 0;
				dy = quadBase[i][1] == 1 ? widthNorthSouth : 0;
				dz = quadBase[i][0] == 1 ? widthEastWest : 0;
				mx = 0;
				my = quadBase[i][1] == 1 ? 1 : -1;
				mz = quadBase[i][0] == 1 ? 1 : -1;
				break;
			case Y: // XZ
				dx = quadBase[i][0] == 1 ? widthEastWest : 0;
				dy = 0;
				dz = quadBase[i][1] == 1 ? widthNorthSouth : 0;
				mx = quadBase[i][0] == 1 ? 1 : -1;
				my = 0;
				mz = quadBase[i][1] == 1 ? 1 : -1;
				break;
			case Z: // XY
				dx = quadBase[i][0] == 1 ? widthEastWest : 0;
				dy = quadBase[i][1] == 1 ? widthNorthSouth : 0;
				dz = 0;
				mx = quadBase[i][0] == 1 ? 1 : -1;
				my = quadBase[i][1] == 1 ? 1 : -1;
				mz = 0;
				break;
			default:
				throw new IllegalArgumentException("Invalid Axis enum: " + axis);
			}
			putVertex(bb, (short) (quad.x + dx), (short) (quad.y + dy), (short) (quad.z + dz),
					quad.hasError ? ColorUtil.RED : quad.color,
					quad.hasError ? 15 : quad.skyLight,
					quad.hasError ? 15 : quad.blockLight,
					mx, my, mz);
		}
	}
	
	/** Uses Greedy meshing to merge this builder's Quads. */
	public void mergeQuads()
	{
		long mergeCount = 0;
		long preQuadsCount = getCurrentOpaqueQuadsCount() + getCurrentTransparentQuadsCount();
		if (preQuadsCount <= 1)
			return;

		for (int directionIndex = 0; directionIndex < 6; directionIndex++)
		{
			mergeCount += mergeQuadsInternal(opaqueQuads, directionIndex, BufferMergeDirectionEnum.EastWest);
			if (doTransparency)
				mergeCount += mergeQuadsInternal(transparentQuads, directionIndex, BufferMergeDirectionEnum.EastWest);
			// only run the second merge if the face is the top or bottom
			if (directionIndex == ELodDirection.UP.ordinal() || directionIndex == ELodDirection.DOWN.ordinal())
			{
				mergeCount += mergeQuadsInternal(opaqueQuads, directionIndex, BufferMergeDirectionEnum.NorthSouthOrUpDown);
				if (doTransparency)
					mergeCount += mergeQuadsInternal(transparentQuads, directionIndex, BufferMergeDirectionEnum.NorthSouthOrUpDown);
			}
		}
		long postQuadsCount = getCurrentOpaqueQuadsCount() + getCurrentTransparentQuadsCount();
		//if (mergeCount != 0)
		LOGGER.debug("Merged {}/{}({}) quads", mergeCount, preQuadsCount, mergeCount / (double) preQuadsCount);
	}

	/** Merges all of this builder's quads for the given directionIndex (up, down, left, etc.) in the given direction */
	private static long mergeQuadsInternal(ArrayList<BufferQuad>[] list, int directionIndex, BufferMergeDirectionEnum mergeDirection)
	{
		if (list[directionIndex].size() <= 1)
			return 0;

		list[directionIndex].sort( (objOne, objTwo) -> objOne.compare(objTwo, mergeDirection) );
		
		long mergeCount = 0;
		ListIterator<BufferQuad> iter = list[directionIndex].listIterator();
		BufferQuad currentQuad = iter.next();
		while (iter.hasNext())
		{
			BufferQuad nextQuad = iter.next();
			
			if (currentQuad.tryMerge(nextQuad, mergeDirection))
			{
				// merge successful, attempt to merge the next quad
				mergeCount++;
				iter.set(null);
			}
			else
			{
				// merge fail, move on to the next quad
				currentQuad = nextQuad;
			}
		}
		list[directionIndex].removeIf(Objects::isNull);
		return mergeCount;
	}
	
	
	
	public Iterator<ByteBuffer> makeOpaqueVertexBuffers()
	{
		return new Iterator<ByteBuffer>()
		{
			final ByteBuffer bb = ByteBuffer.allocateDirect(AbstractRenderBuffer.FULL_SIZED_BUFFER)
					.order(ByteOrder.nativeOrder());
			int dir = skipEmpty(0);
			int quad = 0;
			
			private int skipEmpty(int d)
			{
				while (d < 6 && opaqueQuads[d].isEmpty())
					d++;
				return d;
			}
			
			@Override
			public boolean hasNext()
			{
				return dir < 6;
			}
			
			@Override
			public ByteBuffer next()
			{
				if (dir >= 6)
				{
					return null;
				}
				bb.clear();
				bb.limit(AbstractRenderBuffer.FULL_SIZED_BUFFER);
				while (bb.hasRemaining() && dir < 6)
				{
					writeData();
				}
				bb.limit(bb.position());
				bb.rewind();
				return bb;
			}
			
			private void writeData()
			{
				int i = quad;
				for (; i < opaqueQuads[dir].size(); i++)
				{
					if (!bb.hasRemaining())
					{
						break;
					}
					putQuad(bb, opaqueQuads[dir].get(i));
				}
				
				if (i >= opaqueQuads[dir].size())
				{
					quad = 0;
					dir++;
					dir = skipEmpty(dir);
				}
				else
				{
					quad = i;
				}
			}
		};
	}

	public Iterator<ByteBuffer> makeTransparentVertexBuffers()
	{
		return new Iterator<ByteBuffer>()
		{
			final ByteBuffer bb = ByteBuffer.allocateDirect(AbstractRenderBuffer.FULL_SIZED_BUFFER)
					.order(ByteOrder.nativeOrder());
			int dir = skipEmpty(0);
			int quad = 0;

			private int skipEmpty(int d)
			{
				while (d < 6 && transparentQuads[d].isEmpty())
					d++;
				return d;
			}

			@Override
			public boolean hasNext()
			{
				return dir < 6;
			}

			@Override
			public ByteBuffer next()
			{
				if (dir >= 6)
				{
					return null;
				}
				bb.clear();
				bb.limit(AbstractRenderBuffer.FULL_SIZED_BUFFER);
				while (bb.hasRemaining() && dir < 6)
				{
					writeData();
				}
				bb.limit(bb.position());
				bb.rewind();
				return bb;
			}

			private void writeData()
			{
				int i = quad;
				for (; i < transparentQuads[dir].size(); i++)
				{
					if (!bb.hasRemaining())
					{
						break;
					}
					putQuad(bb, transparentQuads[dir].get(i));
				}

				if (i >= transparentQuads[dir].size())
				{
					quad = 0;
					dir++;
					dir = skipEmpty(dir);
				}
				else
				{
					quad = i;
				}
			}
		};
	}
	public interface BufferFiller
	{
		/** If true: more data needs to be filled */
		boolean fill(GLVertexBuffer vbo);
	}
	
	public BufferFiller makeOpaqueBufferFiller(EGpuUploadMethod method)
	{
		return new BufferFiller()
		{
			int dir = 0;
			int quad = 0;
			
			public boolean fill(GLVertexBuffer vbo)
			{
				if (dir >= 6)
				{
					vbo.setVertexCount(0);
					return false;
				}
				
				int numOfQuads = _countRemainingQuads();
				if (numOfQuads > AbstractRenderBuffer.MAX_QUADS_PER_BUFFER)
					numOfQuads = AbstractRenderBuffer.MAX_QUADS_PER_BUFFER;
				if (numOfQuads == 0)
				{
					vbo.setVertexCount(0);
					return false;
				}
				ByteBuffer bb = vbo.mapBuffer(numOfQuads * AbstractRenderBuffer.QUADS_BYTE_SIZE, method,
						AbstractRenderBuffer.FULL_SIZED_BUFFER);
				if (bb == null)
					throw new NullPointerException("mapBuffer returned null");
				bb.clear();
				bb.limit(numOfQuads * AbstractRenderBuffer.QUADS_BYTE_SIZE);
				while (bb.hasRemaining() && dir < 6)
				{
					writeData(bb);
				}
				bb.rewind();
				vbo.unmapBuffer();
				vbo.setVertexCount(numOfQuads*4);
				return dir < 6;
			}
			
			private int _countRemainingQuads()
			{
				int a = opaqueQuads[dir].size() - quad;
				for (int i = dir + 1; i < opaqueQuads.length; i++)
				{
					a += opaqueQuads[i].size();
				}
				return a;
			}
			
			private void writeData(ByteBuffer bb)
			{
				int startQ = quad;
				
				int i = startQ;
				for (i = startQ; i < opaqueQuads[dir].size(); i++)
				{
					if (!bb.hasRemaining())
					{
						break;
					}
					putQuad(bb, opaqueQuads[dir].get(i));
				}
				
				if (i >= opaqueQuads[dir].size())
				{
					quad = 0;
					dir++;
					while (dir < 6 && opaqueQuads[dir].isEmpty())
						dir++;
				}
				else
				{
					quad = i;
				}
			}
		};
	}

	public BufferFiller makeTransparentBufferFiller(EGpuUploadMethod method)
	{
		return new BufferFiller()
		{
			int dir = 0;
			int quad = 0;

			public boolean fill(GLVertexBuffer vbo)
			{
				if (dir >= 6)
				{
					vbo.setVertexCount(0);
					return false;
				}

				int numOfQuads = _countRemainingQuads();
				if (numOfQuads > AbstractRenderBuffer.MAX_QUADS_PER_BUFFER)
					numOfQuads = AbstractRenderBuffer.MAX_QUADS_PER_BUFFER;
				if (numOfQuads == 0)
				{
					vbo.setVertexCount(0);
					return false;
				}
				ByteBuffer bb = vbo.mapBuffer(numOfQuads * AbstractRenderBuffer.QUADS_BYTE_SIZE, method,
						AbstractRenderBuffer.FULL_SIZED_BUFFER);
				if (bb == null)
					throw new NullPointerException("mapBuffer returned null");
				bb.clear();
				bb.limit(numOfQuads * AbstractRenderBuffer.QUADS_BYTE_SIZE);
				while (bb.hasRemaining() && dir < 6)
				{
					writeData(bb);
				}
				bb.rewind();
				vbo.unmapBuffer();
				vbo.setVertexCount(numOfQuads*4);
				return dir < 6;
			}

			private int _countRemainingQuads()
			{
				int a = transparentQuads[dir].size() - quad;
				for (int i = dir + 1; i < transparentQuads.length; i++)
				{
					a += transparentQuads[i].size();
				}
				return a;
			}

			private void writeData(ByteBuffer bb)
			{
				int startQ = quad;

				int i = startQ;
				for (i = startQ; i < transparentQuads[dir].size(); i++)
				{
					if (!bb.hasRemaining())
					{
						break;
					}
					putQuad(bb, transparentQuads[dir].get(i));
				}

				if (i >= transparentQuads[dir].size())
				{
					quad = 0;
					dir++;
					while (dir < 6 && transparentQuads[dir].isEmpty())
						dir++;
				}
				else
				{
					quad = i;
				}
			}
		};
	}



	public int getCurrentOpaqueQuadsCount()
	{
		int i = 0;
		for (ArrayList<BufferQuad> qs : opaqueQuads)
			i += qs.size();
		return i;
	}
	public int getCurrentTransparentQuadsCount()
	{
		if (!doTransparency) return 0;
		int i = 0;
		for (ArrayList<BufferQuad> qs : transparentQuads)
			i += qs.size();
		return i;
	}

	/** Returns how many Buffers will be needed to render opaque quads in this builder. */
	public int getCurrentNeededOpaqueVertexBufferCount()
	{
		return MathUtil.ceilDiv(getCurrentOpaqueQuadsCount(), AbstractRenderBuffer.MAX_QUADS_PER_BUFFER);
	}
	/** Returns how many Buffers will be needed to render transparent quads in this builder. */
	public int getCurrentNeededTransparentVertexBufferCount()
	{
		if (!doTransparency) return 0;
		return MathUtil.ceilDiv(getCurrentTransparentQuadsCount(), AbstractRenderBuffer.MAX_QUADS_PER_BUFFER);
	}
}
