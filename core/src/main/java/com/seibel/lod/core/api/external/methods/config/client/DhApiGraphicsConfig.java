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

package com.seibel.lod.core.api.external.methods.config.client;

import com.seibel.lod.api.enums.config.*;
import com.seibel.lod.api.interfaces.config.IDhApiConfigValue;
import com.seibel.lod.api.interfaces.config.client.IDhApiGraphicsConfig;
import com.seibel.lod.api.objects.config.DhApiConfigValue;
import com.seibel.lod.coreapi.util.converters.RenderModeEnabledConverter;
import com.seibel.lod.core.config.Config;
import com.seibel.lod.api.enums.rendering.ERendererMode;
import com.seibel.lod.core.config.Config.Client.Graphics.Quality;
import com.seibel.lod.core.config.Config.Client.Advanced.Debugging;
import com.seibel.lod.core.config.Config.Client.Graphics.AdvancedGraphics;

/**
 * Distant Horizons' graphics/rendering configuration.
 *
 * @author James Seibel
 * @version 2022-9-15
 */
public class DhApiGraphicsConfig implements IDhApiGraphicsConfig
{
	public static DhApiGraphicsConfig INSTANCE = new DhApiGraphicsConfig();
	
	private DhApiGraphicsConfig() { }
	
	
	
	//========================//
	// basic graphic settings //
	//========================//
	
	@Override
	public IDhApiConfigValue<Integer> getChunkRenderDistance()
	{ return new DhApiConfigValue<>(Quality.lodChunkRenderDistance); }
	
	@Override
	public IDhApiConfigValue<Boolean> getRenderingEnabled()
	{ return new DhApiConfigValue<ERendererMode, Boolean>(Debugging.rendererMode, new RenderModeEnabledConverter()); }
	
	@Override
	public IDhApiConfigValue<ERendererMode> getRenderingMode()
	{ return new DhApiConfigValue<>(Debugging.rendererMode); }
	
	
	
	//==================//
	// graphic settings //
	//==================//
	
	@Override
	public IDhApiConfigValue<EHorizontalResolution> getMaxDetailLevel()
	{ return new DhApiConfigValue<>(Quality.drawResolution); }
	
	@Override
	public IDhApiConfigValue<EVerticalQuality> getVerticalQuality()
	{ return new DhApiConfigValue<>(Quality.verticalQuality); }
	
	@Override
	public IDhApiConfigValue<EHorizontalQuality> getHorizontalQualityDropoff()
	{ return new DhApiConfigValue<>(Quality.horizontalQuality); }
	
	@Override
	public IDhApiConfigValue<Integer> getBiomeBlending()
	{ return new DhApiConfigValue<>(Quality.lodBiomeBlending); }
	
	
	
	//===========================//
	// advanced graphic settings //
	//===========================//
	
	@Override
	public IDhApiConfigValue<Boolean> getDisableDirectionalCulling()
	{ return new DhApiConfigValue<>(AdvancedGraphics.disableDirectionalCulling); }
	
	@Override
	public IDhApiConfigValue<EVanillaOverdraw> getVanillaOverdraw()
	{ return new DhApiConfigValue<>(AdvancedGraphics.vanillaOverdraw); }
	
	@Override
	public IDhApiConfigValue<Integer> getVanillaOverdrawOffset()
	{ return new DhApiConfigValue<>(AdvancedGraphics.overdrawOffset); }
	
	@Override
	public IDhApiConfigValue<Boolean> getUseExtendedNearClipPlane()
	{ return new DhApiConfigValue<>(AdvancedGraphics.useExtendedNearClipPlane); }
	
	@Override
	public IDhApiConfigValue<Double> getBrightnessMultiplier()
	{ return new DhApiConfigValue<>(AdvancedGraphics.brightnessMultiplier); }
	
	@Override
	public IDhApiConfigValue<Double> getSaturationMultiplier()
	{ return new DhApiConfigValue<>(AdvancedGraphics.saturationMultiplier); }
	
	@Override
	public IDhApiConfigValue<Boolean> getCaveCullingEnabled()
	{ return new DhApiConfigValue<>(AdvancedGraphics.enableCaveCulling); }
	
	@Override
	public IDhApiConfigValue<Integer> getCaveCullingHeight()
	{ return new DhApiConfigValue<>(AdvancedGraphics.caveCullingHeight); }
	
	@Override
	public IDhApiConfigValue<Integer> getEarthCurvatureRatio()
	{ return new DhApiConfigValue<>(AdvancedGraphics.earthCurveRatio); }
	
	@Override
	public IDhApiConfigValue<Boolean> getEnableLodOnlyMode()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.lodOnlyMode); }
	
	@Override
	public IDhApiConfigValue<EBufferRebuildTimes> getGeometryRebuildFrequency()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Buffers.rebuildTimes); }
	
	
	
}
