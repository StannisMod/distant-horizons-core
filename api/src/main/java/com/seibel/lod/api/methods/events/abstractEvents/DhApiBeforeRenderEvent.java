package com.seibel.lod.api.methods.events.abstractEvents;

import com.seibel.lod.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.lod.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.lod.api.objects.events.DhApiEventDefinition;

/**
 * @author James Seibel
 * @version 2022-11-21
 */
public abstract class DhApiBeforeRenderEvent implements IDhApiEvent<DhApiBeforeRenderEvent.EventParam>
{
	/**
	 * Fired before Distant Horizons renders fake chunks.
	 *
	 * @return whether the event should be canceled or not.
	 */
	public abstract boolean beforeRender(EventParam input);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final boolean fireEvent(EventParam input) { return this.beforeRender(input); }
	
	public final static DhApiEventDefinition EVENT_DEFINITION = new DhApiEventDefinition(true, false);
	@Override
	public final DhApiEventDefinition getEventDefinition() { return EVENT_DEFINITION; }
	
	
	//==================//
	// parameter object //
	//==================//
	
	public static class EventParam extends DhApiRenderParam
	{
		public EventParam(DhApiRenderParam parent)
		{
			super(parent.mcProjectionMatrix, parent.mcModelViewMatrix, parent.dhProjectionMatrix, parent.dhModelViewMatrix, parent.partialTicks);
		}
	}
	
}