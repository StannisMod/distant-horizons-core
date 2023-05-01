package com.seibel.lod.api.methods.events.abstractEvents;

import com.seibel.lod.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.lod.api.objects.events.DhApiEventDefinition;

/**
 * @author James Seibel
 * @version 2022-11-21
 */
public abstract class DhApiAfterDhInitEvent implements IDhApiEvent<Void>
{
	/** Fired after Distant Horizons finishes its initial setup on Minecraft startup. */
	public abstract void afterDistantHorizonsInit();
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final boolean fireEvent(Void ignoredParam)
	{
		this.afterDistantHorizonsInit();
		return false;
	}
		
	public final static DhApiEventDefinition EVENT_DEFINITION = new DhApiEventDefinition(false, true);
	@Override
	public final DhApiEventDefinition getEventDefinition() { return EVENT_DEFINITION; }
	
}