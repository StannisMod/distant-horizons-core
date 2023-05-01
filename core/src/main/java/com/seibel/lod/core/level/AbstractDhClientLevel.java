package com.seibel.lod.core.level;

import com.seibel.lod.core.config.Config;
import com.seibel.lod.core.dataObjects.fullData.accessor.ChunkSizedFullDataAccessor;
import com.seibel.lod.core.dataObjects.fullData.sources.CompleteFullDataSource;
import com.seibel.lod.core.dataObjects.transformers.ChunkToLodBuilder;
import com.seibel.lod.core.dependencyInjection.SingletonInjector;
import com.seibel.lod.core.file.fullDatafile.FullDataFileHandler;
import com.seibel.lod.core.file.fullDatafile.IFullDataSourceProvider;
import com.seibel.lod.core.file.fullDatafile.RemoteFullDataFileHandler;
import com.seibel.lod.core.file.structure.AbstractSaveStructure;
import com.seibel.lod.core.level.states.ClientRenderState;
import com.seibel.lod.core.logging.DhLoggerBuilder;
import com.seibel.lod.core.pos.DhBlockPos2D;
import com.seibel.lod.core.pos.DhLodPos;
import com.seibel.lod.core.pos.DhSectionPos;
import com.seibel.lod.core.util.FileScanUtil;
import com.seibel.lod.core.util.LodUtil;
import com.seibel.lod.coreapi.util.math.Mat4f;
import com.seibel.lod.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.lod.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.lod.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.lod.core.wrapperInterfaces.world.ILevelWrapper;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This contains code that is shared between {@link DhClientLevel} {@link DhClientServerLevel}
 */
public abstract class AbstractDhClientLevel implements IDhClientLevel
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	public final AbstractSaveStructure saveStructure;
	public final ChunkToLodBuilder chunkToLodBuilder;
	
	public FullDataFileHandler fullDataFileHandler;
	
	public final AtomicReference<ClientRenderState> ClientRenderStateRef = new AtomicReference<>();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public AbstractDhClientLevel(AbstractSaveStructure saveStructure, ILevelWrapper levelWrapper)
	{
		this.saveStructure = saveStructure;
		if (this.saveStructure.getFullDataFolder(levelWrapper).mkdirs())
		{
			LOGGER.warn("unable to create full data folder.");
		}
		if (this.saveStructure.getRenderCacheFolder(levelWrapper).mkdirs())
		{
			LOGGER.warn("unable to create cache folder.");
		}
		
		this.fullDataFileHandler = new RemoteFullDataFileHandler(this, this.saveStructure.getFullDataFolder(levelWrapper));
		FileScanUtil.scanFiles(saveStructure, levelWrapper, this.fullDataFileHandler, null);
		
		this.chunkToLodBuilder = new ChunkToLodBuilder();
	}
	
	
	
	//==============//
	// tick methods //
	//==============//
	
	/**
	 * Includes logic used by both {@link DhClientServerLevel} and {@link DhClientServerLevel}
	 * @return whether the tick method completed
	 */
	protected boolean baseClientTick()
	{
		ClientRenderState clientRenderState = this.ClientRenderStateRef.get();
		if (clientRenderState == null)
		{
			return false;
		}
		
		if (clientRenderState.quadtree.blockRenderDistance != Config.Client.Graphics.Quality.lodChunkRenderDistance.get() * LodUtil.CHUNK_WIDTH)
		{
			if (!this.ClientRenderStateRef.compareAndSet(clientRenderState, null))
			{
				return false; //If we fail, we'll just wait for the next tick
			}
			
			clientRenderState.closeAsync().join(); //TODO: Make it async.
			clientRenderState = new ClientRenderState(this, this.fullDataFileHandler, this.saveStructure);
			if (!this.ClientRenderStateRef.compareAndSet(null, clientRenderState))
			{
				//FIXME: How to handle this?
				LOGGER.warn("Failed to set render state due to concurrency after changing view distance");
				clientRenderState.closeAsync();
				return false;
			}
		}
		
		clientRenderState.quadtree.tick(new DhBlockPos2D(MC_CLIENT.getPlayerBlockPos()));
		clientRenderState.renderer.bufferHandler.updateQuadTreeRenderSources();
		
		return true;
	}
	
	
	
	//========//
	// render //
	//========//
	
	/** @return if the {@link ClientRenderState} was successfully swapped */
	protected boolean setAndStartRenderer()
	{
		ClientRenderState ClientRenderState = new ClientRenderState(this, this.fullDataFileHandler, this.saveStructure);
		if (!this.ClientRenderStateRef.compareAndSet(null, ClientRenderState))
		{
			LOGGER.warn("Failed to start renderer due to concurrency");
			ClientRenderState.closeAsync();
			return false;
		}
		else
		{
			return true;
		}
	}
	
	@Override
	public void render(Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks, IProfilerWrapper profiler)
	{
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState == null)
		{
			LOGGER.error("Tried to call render() on "+this+" when renderer has not been started!");
			return;
		}
		ClientRenderState.renderer.drawLODs(mcModelViewMatrix, mcProjectionMatrix, partialTicks, profiler);
	}
	
	public void stopRenderer()
	{
		LOGGER.info("Stopping renderer for "+this);
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState == null)
		{
			LOGGER.warn("Tried to stop renderer for "+this+" when it was not started!");
			return;
		}
		
		// stop the render state
		while (!this.ClientRenderStateRef.compareAndSet(ClientRenderState, null)) // TODO why is there a while loop here?
		{
			ClientRenderState = this.ClientRenderStateRef.get();
			if (ClientRenderState == null)
			{
				return;
			}
		}
		ClientRenderState.closeAsync().join(); //TODO: Make it async.
	}
	
	
	
	//===============//
	// data handling //
	//===============//
	
	@Override
	public void updateChunkAsync(IChunkWrapper chunk)
	{
		CompletableFuture<ChunkSizedFullDataAccessor> future = this.chunkToLodBuilder.tryGenerateData(chunk);
		if (future != null)
		{
			future.thenAccept(this::saveWrites);
		}
	}
	private void saveWrites(ChunkSizedFullDataAccessor data)
	{
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		DhLodPos pos = data.getLodPos().convertToDetailLevel(CompleteFullDataSource.SECTION_SIZE_OFFSET);
		if (ClientRenderState != null)
		{
			ClientRenderState.renderSourceFileHandler.writeChunkDataToFile(new DhSectionPos(pos.detailLevel, pos.x, pos.z), data);
		}
		else
		{
			this.fullDataFileHandler.write(new DhSectionPos(pos.detailLevel, pos.x, pos.z), data);
		}
	}
	
	@Override
	public CompletableFuture<Void> saveAsync()
	{
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState != null)
		{
			return ClientRenderState.renderSourceFileHandler.flushAndSaveAsync().thenCombine(this.fullDataFileHandler.flushAndSave(), (voidA, voidB) -> null);
		}
		else
		{
			return this.fullDataFileHandler.flushAndSave();
		}
	}
	
	
	
	/** Includes logic used by both {@link DhClientServerLevel} and {@link DhClientServerLevel} */
	protected void baseClose()
	{
		// shut down to prevent reading/writing files after the client has left the world
		this.fullDataFileHandler.close();
		
		// clear the chunk builder to prevent generating LODs for chunks that are unloaded
		this.chunkToLodBuilder.clearCurrentTasks();
		
		
		// shutdown the renderer
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState != null)
		{
			// TODO does this have to be in a while loop, if so why?
			while (!this.ClientRenderStateRef.compareAndSet(ClientRenderState, null))
			{
				ClientRenderState = this.ClientRenderStateRef.get();
				if (ClientRenderState == null)
				{
					break;
				}
			}
			
			if (ClientRenderState != null)
			{
				ClientRenderState.closeAsync().join(); //TODO: Make this async.
			}
		}
	}
	
	
	
	
	//=======================//
	// misc helper functions //
	//=======================//
	
	@Override
	public void dumpRamUsage()
	{
		//TODO
	}
	
	/** Returns what should be displayed in Minecraft's F3 debug menu */
	protected String[] f3Log()
	{
		String dimName = this.getLevelWrapper().getDimensionType().getDimensionName();
		ClientRenderState renderState = this.ClientRenderStateRef.get();
		if (renderState == null)
		{
			return new String[] { "level @ "+dimName+": Inactive" };
		}
		else
		{
			return new String[] { "level @ "+dimName+": Active" };
		}
	}
	
	@Override
	public IFullDataSourceProvider getFileHandler() { return this.fullDataFileHandler; }
	
	@Override
	public void clearRenderDataCache()
	{
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState != null && ClientRenderState.quadtree != null)
		{
			ClientRenderState.quadtree.clearRenderDataCache();
		}
	}
	
}
