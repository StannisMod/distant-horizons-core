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

package com.seibel.distanthorizons.api.enums.rendering;

/**
 * POS_META_COLOR_MATERIAL_NORMAL_TEXTURE,			<br>
 * POS_META_COLOR_MATERIAL_NORMAL_TEXTURE_OFFSET_UV_NORMAL,	<br><br>
 *
 * The layout of each vertex in DH's terrain buffers.
 *
 * @since API 7.3.0
 * @version 2026-10-25
 */
public enum EDhApiTerrainVertexFormat
{
	/**
	 * 16 bytes per vertex, positions are whole blocks so fluids aren't lowered. <br>
	 * <code>
	 * ushort x, y, z <br>
	 * ushort meta (sky light, block light, micro offset) <br>
	 * ubyte r, g, b, a <br>
	 * ubyte material id <br>
	 * ubyte normal index <br>
	 * ushort texture tile id
	 * </code>
	 */
	POS_META_COLOR_MATERIAL_NORMAL_TEXTURE(16),
	
	/**
	 * 24 bytes per vertex, the same as {@link #POS_META_COLOR_MATERIAL_NORMAL_TEXTURE} followed by: <br>
	 * <code>
	 * ushort x, y, z <br>
	 * ushort meta (sky light, block light, micro offset) <br>
	 * ubyte r, g, b, a <br>
	 * ubyte material id <br>
	 * ubyte normal index <br>
	 * ushort texture tile id <br>
	 * ubyte sub-block offset xyz, and unused flag w
	 * </code>
	 * The offsets are in 256ths of a block and are added to the position (lowered fluid surfaces). <br>
	 * The flags, u, v, and normal are reserved for future block model work and are currently always 0.
	 */
	POS_META_COLOR_MATERIAL_NORMAL_TEXTURE_OFFSET(20);
	
	
	
	public final int bytesPerVertex;
	
	EDhApiTerrainVertexFormat(int bytesPerVertex) 
	{
		this.bytesPerVertex = bytesPerVertex;
		if (this.bytesPerVertex % 4 != 0)
		{
			// This is just for dev time validation
			throw new UnsupportedOperationException("Vertex Formats must be multiples of 4.");
		}
	}
	
	
	
}
