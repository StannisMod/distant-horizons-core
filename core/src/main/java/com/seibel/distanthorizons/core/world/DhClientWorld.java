/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
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

package com.seibel.distanthorizons.core.world;

import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelUnloadEvent;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.enums.MinecraftTextFormat;
import com.seibel.distanthorizons.core.file.structure.ClientOnlySaveStructure;
import com.seibel.distanthorizons.core.level.DhClientLevel;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.multiplayer.client.ClientNetworkState;
import com.seibel.distanthorizons.core.util.TimerUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DhClientWorld extends AbstractDhWorld implements IDhClientWorld
{
	private final ConcurrentHashMap<IClientLevelWrapper, DhClientLevel> levels;
	public final ClientOnlySaveStructure saveStructure;
	public final ClientNetworkState networkState = new ClientNetworkState();
	
	private final Timer clientTickTimer = TimerUtil.CreateTimer("ClientTickTimer");
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public DhClientWorld()
	{
		super(EWorldEnvironment.CLIENT_ONLY);
		
		this.saveStructure = new ClientOnlySaveStructure();
		this.levels = new ConcurrentHashMap<>();
		
		LOGGER.info("Started DhWorld of type " + this.environment);
		
		this.clientTickTimer.scheduleAtFixedRate(new TimerTask()
		{
			@Override
			public void run()
			{
				DhClientWorld.this.levels.values().forEach(DhClientLevel::clientTick);
			}
		}, 0, IDhClientWorld.TICK_RATE_IN_MS);
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public DhClientLevel getOrLoadLevel(@NotNull ILevelWrapper wrapper)
	{
		if (!(wrapper instanceof IClientLevelWrapper))
		{
			return null;
		}
		((IClientLevelWrapper) wrapper).markAccessed();
		return this.levels.computeIfAbsent((IClientLevelWrapper) wrapper,
			(clientLevelWrapper) ->
			{
				try
				{
					if (!ClientApi.INSTANCE.canLoadClientLevel(clientLevelWrapper)) 
					{
						return null;
					}
					
					DhClientLevel level = new DhClientLevel(this.saveStructure, clientLevelWrapper, this.networkState);
					ApiEventInjector.INSTANCE.fireAllEvents(DhApiLevelLoadEvent.class, new DhApiLevelLoadEvent.EventParam(wrapper));
					ClientApi.INSTANCE.loadWaitingChunksForLevel(clientLevelWrapper);
					return level;
				}
				catch (Exception e)
				{
					LOGGER.fatal("Failed to load client level, error: ["+e.getMessage()+"].", e);
					
					ClientApi.INSTANCE.showChatMessageNextFrame(
						MinecraftTextFormat.RED + "Distant Horizons: Client level loading failed." + MinecraftTextFormat.CLEAR_FORMATTING + "\n" +
						"Unable to load level ["+clientLevelWrapper.getDhIdentifier()+"], LODs may not appear. See log for more information.");
					
					return null;
				}
			});
	}
	
	@Override
	public DhClientLevel getLevel(@NotNull ILevelWrapper wrapper)
	{
		if (!(wrapper instanceof IClientLevelWrapper))
		{
			return null;
		}
		
		return this.levels.get(wrapper);
	}
	
	@Override
	public Iterable<? extends IDhLevel> getAllLoadedLevels() { return this.levels.values(); }
	@Override
	public int getLoadedLevelCount() { return this.levels.size(); }
	
	@Override
	public boolean unloadLevel(@NotNull ILevelWrapper wrapper)
	{
		if (!(wrapper instanceof IClientLevelWrapper))
		{
			return false;
		}
		
		if (this.levels.containsKey(wrapper))
		{
			LOGGER.info("Unloading level " + this.levels.get(wrapper));
			wrapper.onUnload();
			this.levels.remove(wrapper).close();
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiLevelUnloadEvent.class, new DhApiLevelUnloadEvent.EventParam(wrapper));
			return true;
		}
		return false;
	}
	
	@Override
	public void addDebugMenuStringsToList(List<String> messageList)
	{
		super.addDebugMenuStringsToList(messageList);
		this.networkState.addDebugMenuStringsToList(messageList);
	}
	
	@Override
	public void close()
	{
		this.networkState.close();
		
		ArrayList<CompletableFuture<Void>> closeFutures = new ArrayList<>();
		for (DhClientLevel dhClientLevel : this.levels.values())
		{
			// level wrapper shouldn't be null, but just in case
			IClientLevelWrapper clientLevelWrapper = dhClientLevel.getClientLevelWrapper();
			if (clientLevelWrapper != null)
			{
				clientLevelWrapper.onUnload();
				ApiEventInjector.INSTANCE.fireAllEvents(DhApiLevelUnloadEvent.class, new DhApiLevelUnloadEvent.EventParam(clientLevelWrapper));
			}
			
			
			// close levels asynchronously to speed up
			// shutdown on servers with a lot of levels
			CompletableFuture<Void> closeFuture = new CompletableFuture<>();
			Thread closeThread = new Thread(() ->
			{
				dhClientLevel.close();
				closeFuture.complete(null);
			}, "level shutdown");
			closeThread.start();
			closeFutures.add(closeFuture);
		}
		
		// wait for all the levels to finish closing
		for (CompletableFuture<Void> future : closeFutures)
		{
			future.join();
		}
		
		this.levels.clear();
		this.clientTickTimer.cancel();
		LOGGER.info("Closed DhWorld of type [" + this.environment + "].");
	}
	
}
