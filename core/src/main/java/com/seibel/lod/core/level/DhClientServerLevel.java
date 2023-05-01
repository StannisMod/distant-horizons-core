package com.seibel.lod.core.level;

import com.seibel.lod.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.lod.coreapi.DependencyInjection.WorldGeneratorInjector;
import com.seibel.lod.core.config.AppliedConfigState;
import com.seibel.lod.core.dependencyInjection.SingletonInjector;
import com.seibel.lod.core.file.fullDatafile.FullDataFileHandler;
import com.seibel.lod.core.file.structure.AbstractSaveStructure;
import com.seibel.lod.core.generation.BatchGenerator;
import com.seibel.lod.core.generation.WorldGenerationQueue;
import com.seibel.lod.core.file.fullDatafile.GeneratedFullDataFileHandler;
import com.seibel.lod.core.level.states.ClientRenderState;
import com.seibel.lod.core.logging.f3.F3Screen;
import com.seibel.lod.core.pos.DhSectionPos;
import com.seibel.lod.core.util.FileScanUtil;
import com.seibel.lod.core.pos.DhBlockPos2D;
import com.seibel.lod.core.config.Config;
import com.seibel.lod.core.logging.DhLoggerBuilder;
import com.seibel.lod.core.pos.DhBlockPos;
import com.seibel.lod.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.lod.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.lod.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.lod.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.lod.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.lod.core.wrapperInterfaces.world.IServerLevelWrapper;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** The level used on a singleplayer world */
public class DhClientServerLevel extends AbstractDhClientLevel implements IDhClientLevel, IDhServerLevel, GeneratedFullDataFileHandler.IOnWorldGenCompleteListener
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	public final IServerLevelWrapper serverLevelWrapper;
	public final F3Screen.NestedMessage f3Message;
	
	/**
	 * This is separate from {@link AbstractDhClientLevel#fullDataFileHandler}
	 * since the base {@link FullDataFileHandler} doesn't support world generation
	 */
	public final GeneratedFullDataFileHandler generatedFullDataFileHandler;
	
	private final AppliedConfigState<Boolean> worldGeneratorEnabledConfig;
	private final AtomicReference<WorldGenState> worldGenStateRef = new AtomicReference<>();
	
	
	
	public DhClientServerLevel(AbstractSaveStructure saveStructure, IServerLevelWrapper serverLevelWrapper)
	{
		super(saveStructure, serverLevelWrapper);
		
		this.serverLevelWrapper = serverLevelWrapper;
		this.f3Message = new F3Screen.NestedMessage(super::f3Log);
		
		if (this.fullDataFileHandler != null)
		{
			// done since the super() constructor creates a FullDataFileHandler first that we don't want // TODO in the future it would be nice if we didn't have to overwrite an existing object like this
			this.fullDataFileHandler.close();
		}
		this.generatedFullDataFileHandler = new GeneratedFullDataFileHandler(this, saveStructure.getFullDataFolder(serverLevelWrapper));
		this.fullDataFileHandler = this.generatedFullDataFileHandler;
		
		FileScanUtil.scanFiles(saveStructure, this.serverLevelWrapper, this.fullDataFileHandler, null);
		
		this.worldGeneratorEnabledConfig = new AppliedConfigState<>(Config.Client.WorldGenerator.enableDistantGeneration);
		
		
		LOGGER.info("Started "+DhClientServerLevel.class.getSimpleName()+" for "+ serverLevelWrapper +" with saves at "+saveStructure);
	}
	
	
	
	//==============//
	// tick methods //
	//==============//
	
	@Override
	public void clientTick()
	{
		if (!super.baseClientTick())
		{
			return;
		}
		
		// additional tick logic can be added if necessary
	}
	
	@Override
	public void serverTick() { this.chunkToLodBuilder.tick(); }
	
	@Override
	public void doWorldGen()
	{
		WorldGenState worldGenState = this.worldGenStateRef.get();
		
		// create/destroy the world generator as necessary
		boolean shouldDoWorldGen = this.worldGeneratorEnabledConfig.get() && this.ClientRenderStateRef.get() != null;
		if (shouldDoWorldGen && worldGenState == null)
		{
			// create the new world generator
			WorldGenState newWgs = new WorldGenState(this);
			if (!this.worldGenStateRef.compareAndSet(null, newWgs))
			{
				LOGGER.warn("Failed to start world gen due to concurrency");
				newWgs.closeAsync(false);
			}
		}
		else if (!shouldDoWorldGen && worldGenState != null)
		{
			// shut down the world generator
			while (!this.worldGenStateRef.compareAndSet(worldGenState, null))
			{
				worldGenState = this.worldGenStateRef.get();
				if (worldGenState == null)
				{
					return;
				}
			}
			worldGenState.closeAsync(true).join(); //TODO: Make it async.
		}
		
		
		if (worldGenState != null)
		{
			// queue new world generation requests
			worldGenState.chunkGenerator.preGeneratorTaskStart();
			worldGenState.worldGenerationQueue.runCurrentGenTasksUntilBusy(new DhBlockPos2D(MC_CLIENT.getPlayerBlockPos()));
		}
	}
	
	
	
	//========//
	// render //
	//========//
	
	public void startRenderer(IClientLevelWrapper clientLevel)
	{
		LOGGER.info("Starting renderer for "+this);
		if (super.setAndStartRenderer())
		{
			this.worldGeneratorEnabledConfig.pollNewValue();
			if (this.worldGeneratorEnabledConfig.get() && this.worldGenStateRef.get() == null)
			{
				WorldGenState worldGenState = new WorldGenState(this);
				if (!this.worldGenStateRef.compareAndSet(null, worldGenState))
				{
					LOGGER.warn("Failed to start world gen due to concurrency");
					worldGenState.closeAsync(false);
				}
			}
			
		}
	}
	
	public void stopRenderer()
	{
		super.stopRenderer();
		
		// stop the world generator
		WorldGenState worldGenState = this.worldGenStateRef.get();
		if (worldGenState != null)
		{
			while (!this.worldGenStateRef.compareAndSet(worldGenState, null))
			{
				worldGenState = this.worldGenStateRef.get();
				if (worldGenState == null)
				{
					return;
				}
			}
			worldGenState.closeAsync(true).join(); //TODO: Make it async.
		}
	}
	
	
	
	//================//
	// level handling //
	//================//
	
	@Override //FIXME this can fail if the clientLevel isn't available yet, maybe in that case we could return -1 and handle it upstream?
	public int computeBaseColor(DhBlockPos pos, IBiomeWrapper biome, IBlockStateWrapper block)
	{
		IClientLevelWrapper clientLevel = this.getClientLevelWrapper();
		if (clientLevel == null)
		{
			return 0;
		}
		else
		{
			return clientLevel.computeBaseColor(pos, biome, block);
		}
	}
	
	@Override
	public IClientLevelWrapper getClientLevelWrapper() { return this.serverLevelWrapper.tryGetClientLevelWrapper(); }
	@Override
	public IServerLevelWrapper getServerLevelWrapper() { return this.serverLevelWrapper; }
	@Override
	public ILevelWrapper getLevelWrapper() { return this.serverLevelWrapper; }
	
	@Override
	public int getMinY() { return this.serverLevelWrapper.getMinHeight(); }
	
	
	
	//===============//
	// data handling //
	//===============//
	
	@Override
	public void close()
	{
		super.baseClose();
		
		WorldGenState worldGenState = this.worldGenStateRef.get();
		if (worldGenState != null)
		{
			// TODO does this have to be in a while loop, if so why?
			while (!this.worldGenStateRef.compareAndSet(worldGenState, null))
			{
				worldGenState = this.worldGenStateRef.get();
				if (worldGenState == null)
				{
					break;
				}
			}
			
			if (worldGenState != null)
			{
				worldGenState.closeAsync(true).join(); //TODO: Make it async.
			}
		}
		
		LOGGER.info("Closed "+this.getClass().getSimpleName()+" for "+this.serverLevelWrapper);
	}
	
	
	@Override 
	public void onWorldGenTaskComplete(DhSectionPos pos)
	{
		ClientRenderState clientRenderState = this.ClientRenderStateRef.get();
		if (clientRenderState != null && clientRenderState.quadtree != null)
		{
			clientRenderState.quadtree.reloadPos(pos);
		}
	}
	
	
	
	
	//================//
	// helper classes //
	//================//
	
	private class WorldGenState
	{
		public final IDhApiWorldGenerator chunkGenerator;
		public final WorldGenerationQueue worldGenerationQueue;
		
		
		
		WorldGenState(DhClientServerLevel level)
		{
			IDhApiWorldGenerator worldGenerator = WorldGeneratorInjector.INSTANCE.get(level.getLevelWrapper());
			if (worldGenerator == null)
			{
				// no override generator is bound, use the Core world generator
				worldGenerator = new BatchGenerator(level);
				// binding the core generator won't prevent other mods from binding their own generators 
				// since core world generator's should have the lowest override priority
				WorldGeneratorInjector.INSTANCE.bind(level.getLevelWrapper(), worldGenerator);
			}
			this.chunkGenerator = worldGenerator;
			
			this.worldGenerationQueue = new WorldGenerationQueue(this.chunkGenerator);
			DhClientServerLevel.this.generatedFullDataFileHandler.setGenerationQueue(this.worldGenerationQueue);
			DhClientServerLevel.this.generatedFullDataFileHandler.addWorldGenCompleteListener(level);
		}
		
		
		
		CompletableFuture<Void> closeAsync(boolean doInterrupt)
		{
			DhClientServerLevel.this.generatedFullDataFileHandler.clearGenerationQueue();
			
			return this.worldGenerationQueue.startClosing(true, doInterrupt)
				.exceptionally(ex ->
				{
					LOGGER.error("Error closing generation queue", ex);
					return null;
				}
				).thenRun(this.chunkGenerator::close)
				.exceptionally(ex ->
				{
					LOGGER.error("Error closing world gen", ex);
					return null;
				});
		}
	}
	
}
