package com.seibel.lod.api.methods.events.interfaces;

import com.seibel.lod.api.objects.events.DhApiEventDefinition;
import com.seibel.lod.coreapi.interfaces.dependencyInjection.IBindable;

/**
 * The interface used by all DH Api events.
 * 
 * @param <T> This is the datatype that will be passed into the event handler's method.
 * 
 * @author James Seibel
 * @version 2022-11-20
 */
public interface IDhApiEvent<T> extends IBindable
{
	//==========//
	// external //
	//==========//
	
	/**
	 * Returns true if the event should be automatically unbound
	 * after firing. <br>
	 * Can be useful for one time setup events or waiting for a specific game state. <br> <Br>
	 *
	 * Defaults to False
	 * IE: The event will not be removed after firing and will continue firing until removed.
	 */
	default boolean removeAfterFiring() { return false; };
	
	
	//==========//
	// internal //
	//==========//
	
	/** 
	 * The event definition includes meta information about how the event will behave. <br>
	 * For example: if the event is cancelable or not.
	 */
	DhApiEventDefinition getEventDefinition();
	
	/**
	 * Called internally by Distant Horizons when the event happens.
	 * This method shouldn't directly be overridden and
	 * should call a more specific method instead.
	 *
	 * @param input the parameter object passed in from the event source. Can be null.
	 * @return whether the event should be canceled or not.
	 * 		   A canceled event will still fire the other event handlers that are queued.
	 */
	boolean fireEvent(T input);
	
}
