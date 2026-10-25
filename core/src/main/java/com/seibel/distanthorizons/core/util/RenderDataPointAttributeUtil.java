/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
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

package com.seibel.distanthorizons.core.util;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;

/** Texture and fluid data for each render data point, stored in one byte array per ColumnRenderSource */
public class RenderDataPointAttributeUtil
{
	// each data point is [texture palette index][fluid amount], size in bytes
	public static final int RECORD_SIZE_IN_BYTES = 2;

	// waterlogged blocks are always full, so they're stored as one past full to save space
	private static final byte WATERLOGGED_FLUID_AMOUNT = 9;



	public static void setRecord(ByteArrayList data, int offset, int texturePaletteIndex, int fluidAmount, boolean waterlogged)
	{
		data.set(offset, (byte) texturePaletteIndex);
		data.set(offset + 1, waterlogged ? WATERLOGGED_FLUID_AMOUNT : (byte) fluidAmount);
	}

	public static int getTexturePaletteIndex(ByteArrayList data, int offset) { return data.getByte(offset) & 0xFF; }
	public static int getFluidAmount(ByteArrayList data, int offset) { return Math.min(data.getByte(offset + 1), LodUtil.MAX_FLUID_AMOUNT); }
	public static boolean isWaterlogged(ByteArrayList data, int offset) { return data.getByte(offset + 1) == WATERLOGGED_FLUID_AMOUNT; }

}
