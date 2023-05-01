package com.seibel.lod.api.methods.override;

import com.seibel.lod.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.lod.api.interfaces.override.worldGenerator.IDhApiWorldGeneratorOverrideRegister;
import com.seibel.lod.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.lod.api.objects.DhApiResult;
import com.seibel.lod.coreapi.DependencyInjection.WorldGeneratorInjector;

/**
 * Handles adding world generator overrides.
 *
 * @author James Seibel
 * @version 2022-12-10
 */
public class DhApiWorldGeneratorOverrideRegister implements IDhApiWorldGeneratorOverrideRegister
{
	public static DhApiWorldGeneratorOverrideRegister INSTANCE = new DhApiWorldGeneratorOverrideRegister();
	
	private DhApiWorldGeneratorOverrideRegister() {  }
	
	
	
	@Override
	public DhApiResult<Void> registerWorldGeneratorOverride(IDhApiLevelWrapper levelWrapper, IDhApiWorldGenerator worldGenerator)
	{
		try
		{
			WorldGeneratorInjector.INSTANCE.bind(levelWrapper, worldGenerator);
			return DhApiResult.createSuccess();
		}
		catch (Exception e)
		{
			return DhApiResult.createFail(e.getMessage());
		}
	}
	
}
