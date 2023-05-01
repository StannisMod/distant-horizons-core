package com.seibel.lod.api.methods.events.abstractEvents;



import com.seibel.lod.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.lod.api.objects.events.DhApiEventDefinition;

/**
 * @author James Seibel
 * @version 2022-11-21
 */
public abstract class DhApiBeforeDhInitEvent implements IDhApiEvent<Void>
{
	/** Fired before Distant Horizons starts its initial setup on Minecraft startup. */
	public abstract void beforeDistantHorizonsInit();
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final boolean fireEvent(Void ignoredParam)
	{
		this.beforeDistantHorizonsInit();
		return false;
	}
	
	public final static DhApiEventDefinition EVENT_DEFINITION = new DhApiEventDefinition(false, true);
	@Override
	public final DhApiEventDefinition getEventDefinition() { return EVENT_DEFINITION; }
	
}