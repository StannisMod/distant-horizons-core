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

package com.seibel.lod.coreapi.events;

import com.seibel.lod.api.methods.events.abstractEvents.*;
import com.seibel.lod.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.lod.api.objects.events.DhApiEventDefinition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;

/**
 * Holds the API event definitions
 * so, they can be accessed without an
 * event instance.
 * 
 * @author James Seibel
 * @version 2022-11-24
 */
public class ApiEventDefinitionHandler
{
	private static final Logger LOGGER = LogManager.getLogger(ApiEventDefinitionHandler.class.getSimpleName());
	// note to self: don't try adding a generic interface type here, the event dependency handler's constructor method won't accept it
	private final HashMap<Class<? extends IDhApiEvent>, DhApiEventDefinition> DEFINITIONS_BY_EVENT_INTERFACE = new HashMap<>();
	
	public static final ApiEventDefinitionHandler INSTANCE = new ApiEventDefinitionHandler();
	
	
	
	private ApiEventDefinitionHandler() { this.addInitialBindings(); }
	
	/** This must include all available events */
	public void addInitialBindings()
	{
		this.setEventDefinition(DhApiAfterDhInitEvent.class, DhApiAfterDhInitEvent.EVENT_DEFINITION);
		this.setEventDefinition(DhApiAfterRenderEvent.class, DhApiAfterRenderEvent.EVENT_DEFINITION);
		this.setEventDefinition(DhApiBeforeDhInitEvent.class, DhApiBeforeDhInitEvent.EVENT_DEFINITION);
		this.setEventDefinition(DhApiBeforeRenderEvent.class, DhApiBeforeRenderEvent.EVENT_DEFINITION);
		this.setEventDefinition(DhApiLevelLoadEvent.class, DhApiLevelLoadEvent.EVENT_DEFINITION);
		this.setEventDefinition(DhApiLevelSaveEvent.class, DhApiLevelSaveEvent.EVENT_DEFINITION);
		this.setEventDefinition(DhApiLevelUnloadEvent.class, DhApiLevelUnloadEvent.EVENT_DEFINITION);
		
	}
	
	
	/** 
	 * This should only be used for unit testing.
	 * Under normal circumstances there isn't any reason to clear the event definitions. 
	 */
	public void clear() { this.DEFINITIONS_BY_EVENT_INTERFACE.clear(); }
	
	
	
	public void setEventDefinition(Class<? extends IDhApiEvent> eventInterface, DhApiEventDefinition definition)
	{
		if (this.DEFINITIONS_BY_EVENT_INTERFACE.containsKey(eventInterface))
		{
			LOGGER.warn("duplicate key added [" + eventInterface.getSimpleName() + "]");
		}
		
		this.DEFINITIONS_BY_EVENT_INTERFACE.put(eventInterface, definition);
	}
	
	public DhApiEventDefinition getEventDefinition(Class<? extends IDhApiEvent> eventInterface)
	{
		if (!this.DEFINITIONS_BY_EVENT_INTERFACE.containsKey(eventInterface))
		{
			throw new NullPointerException("event definition missing for: [" + eventInterface.getSimpleName() + "]");
		}
		
		return this.DEFINITIONS_BY_EVENT_INTERFACE.get(eventInterface);
	}
	
}
