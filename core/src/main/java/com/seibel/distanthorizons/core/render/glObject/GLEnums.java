/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020-2023 James Seibel
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

package com.seibel.distanthorizons.core.render.glObject;

import static org.lwjgl.opengl.GL46.*;

// Turns GL int enums back to readable strings
public class GLEnums
{
	
	public static String getString(int glEnum)
	{
		return switch (glEnum)
		{
			// Zero
			case GL_ZERO -> "GL_ZERO";
			
			// blend stuff
			case GL_ONE -> "GL_ONE";
			case GL_SRC_COLOR -> "GL_SRC_COLOR";
			case GL_ONE_MINUS_SRC_COLOR -> "GL_ONE_MINUS_SRC_COLOR";
			case GL_DST_COLOR -> "GL_DST_COLOR";
			case GL_ONE_MINUS_DST_COLOR -> "GL_ONE_MINUS_DST_COLOR";
			case GL_SRC_ALPHA -> "GL_SRC_ALPHA";
			case GL_ONE_MINUS_SRC_ALPHA -> "GL_ONE_MINUS_SRC_ALPHA";
			case GL_DST_ALPHA -> "GL_DST_ALPHA";
			case GL_ONE_MINUS_DST_ALPHA -> "GL_ONE_MINUS_DST_ALPHA";
			case GL_CONSTANT_COLOR -> "GL_CONSTANT_COLOR";
			case GL_ONE_MINUS_CONSTANT_COLOR -> "GL_ONE_MINUS_CONSTANT_COLOR";
			case GL_CONSTANT_ALPHA -> "GL_CONSTANT_ALPHA";
			case GL_ONE_MINUS_CONSTANT_ALPHA -> "GL_ONE_MINUS_CONSTANT_ALPHA";
			
			// shader stuff
			case GL_VERTEX_SHADER -> "GL_VERTEX_SHADER";
			case GL_GEOMETRY_SHADER -> "GL_GEOMETRY_SHADER";
			case GL_FRAGMENT_SHADER -> "GL_FRAGMENT_SHADER";
			
			// stencil stuff
			case GL_KEEP -> "GL_KEEP";
			case GL_REPLACE -> "GL_REPLACE";
			case GL_INCR -> "GL_INCR";
			case GL_DECR -> "GL_DECR";
			case GL_INVERT -> "GL_INVERT";
			case GL_INCR_WRAP -> "GL_INCR_WRAP";
			case GL_DECR_WRAP -> "GL_DECR_WRAP";
			
			// depth stuff
			case GL_NEVER -> "GL_NEVER";
			case GL_LESS -> "GL_LESS";
			case GL_EQUAL -> "GL_EQUAL";
			case GL_LEQUAL -> "GL_LEQUAL";
			case GL_GREATER -> "GL_GREATER";
			case GL_NOTEQUAL -> "GL_NOTEQUAL";
			case GL_GEQUAL -> "GL_GEQUAL";
			case GL_ALWAYS -> "GL_ALWAYS";
			
			// Texture binding points
			case GL_TEXTURE0 -> "GL_TEXTURE0";
			case GL_TEXTURE1 -> "GL_TEXTURE1";
			case GL_TEXTURE2 -> "GL_TEXTURE2";
			case GL_TEXTURE3 -> "GL_TEXTURE3";
			case GL_TEXTURE4 -> "GL_TEXTURE4";
			case GL_TEXTURE5 -> "GL_TEXTURE5";
			case GL_TEXTURE6 -> "GL_TEXTURE6";
			case GL_TEXTURE7 -> "GL_TEXTURE7";
			case GL_TEXTURE8 -> "GL_TEXTURE8";
			case GL_TEXTURE9 -> "GL_TEXTURE9";
			case GL_TEXTURE10 -> "GL_TEXTURE10";
			case GL_TEXTURE11 -> "GL_TEXTURE11";
			case GL_TEXTURE12 -> "GL_TEXTURE12";
			case GL_TEXTURE13 -> "GL_TEXTURE13";
			case GL_TEXTURE14 -> "GL_TEXTURE14";
			case GL_TEXTURE15 -> "GL_TEXTURE15";
			case GL_TEXTURE16 -> "GL_TEXTURE16";
			case GL_TEXTURE17 -> "GL_TEXTURE17";
			case GL_TEXTURE18 -> "GL_TEXTURE18";
			case GL_TEXTURE19 -> "GL_TEXTURE19";
			case GL_TEXTURE20 -> "GL_TEXTURE20";
			case GL_TEXTURE21 -> "GL_TEXTURE21";
			case GL_TEXTURE22 -> "GL_TEXTURE22";
			case GL_TEXTURE23 -> "GL_TEXTURE23";
			case GL_TEXTURE24 -> "GL_TEXTURE24";
			case GL_TEXTURE25 -> "GL_TEXTURE25";
			case GL_TEXTURE26 -> "GL_TEXTURE26";
			case GL_TEXTURE27 -> "GL_TEXTURE27";
			case GL_TEXTURE28 -> "GL_TEXTURE28";
			case GL_TEXTURE29 -> "GL_TEXTURE29";
			case GL_TEXTURE30 -> "GL_TEXTURE30";
			case GL_TEXTURE31 -> "GL_TEXTURE31";
			
			
			// Polygon modes
			case GL_POINT -> "GL_POINT";
			case GL_LINE -> "GL_LINE";
			case GL_FILL -> "GL_FILL";
			
			// Culling modes
			case GL_FRONT -> "GL_FRONT";
			case GL_BACK -> "GL_BACK";
			case GL_FRONT_AND_BACK -> "GL_FRONT_AND_BACK";
			default -> "GL_UNKNOWN(" + glEnum + ")";
		};
	}
	
	public static int getTypeSize(int glTypeEnum)
	{
		return switch (glTypeEnum)
		{
			case GL_BYTE, GL_UNSIGNED_BYTE -> 1;
			case GL_SHORT, GL_UNSIGNED_SHORT -> 2;
			case GL_INT, GL_UNSIGNED_INT -> 4;
			case GL_FLOAT -> 4;
			case GL_DOUBLE -> 8;
			default -> throw new IllegalArgumentException("Unknown type enum: " + getString(glTypeEnum));
		};
	}
	
}