package com.seibel.lod.coreapi.util.converters;

import com.seibel.lod.api.enums.rendering.ERendererMode;
import com.seibel.lod.coreapi.interfaces.config.IConverter;

/**
 * Used for simplifying the fake chunk rendering on/off setting.
 *
 * @author James Seibel
 * @version 2022-6-30
 */
public class RenderModeEnabledConverter implements IConverter<ERendererMode, Boolean>
{
	
	@Override public ERendererMode convertToCoreType(Boolean renderingEnabled)
	{
		return renderingEnabled ? ERendererMode.DEFAULT : ERendererMode.DISABLED;
	}
	
	@Override public Boolean convertToApiType(ERendererMode renderingMode)
	{
		return renderingMode == ERendererMode.DEFAULT;
	}
	
}
