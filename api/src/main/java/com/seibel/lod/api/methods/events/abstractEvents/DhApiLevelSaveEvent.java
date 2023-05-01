package com.seibel.lod.api.methods.events.abstractEvents;

import com.seibel.lod.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.lod.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.lod.api.objects.events.DhApiEventDefinition;

/**
 * @author James Seibel
 * @version 2022-11-21
 */
public abstract class DhApiLevelSaveEvent implements IDhApiEvent<DhApiLevelSaveEvent.EventParam>
{
	/** Fired after Distant Horizons saves LOD data for the server. */
	public abstract void onLevelSave(EventParam input);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final boolean fireEvent(EventParam input)
	{
		this.onLevelSave(input);
		return false;
	}
	
	public final static DhApiEventDefinition EVENT_DEFINITION = new DhApiEventDefinition(false, false);
	@Override
	public final DhApiEventDefinition getEventDefinition() { return EVENT_DEFINITION; }
	
	
	//==================//
	// parameter object //
	//==================//
	
	public static class EventParam
	{
		/** The saved level. */
		public final IDhApiLevelWrapper levelWrapper;
		
		
		public EventParam(IDhApiLevelWrapper newLevelWrapper) { this.levelWrapper = newLevelWrapper; }
	}
	
}