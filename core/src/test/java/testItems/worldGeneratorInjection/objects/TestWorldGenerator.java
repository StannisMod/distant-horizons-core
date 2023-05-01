package testItems.worldGeneratorInjection.objects;

import com.seibel.lod.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.lod.api.enums.worldGeneration.EDhApiWorldGenThreadMode;
import com.seibel.lod.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.lod.coreapi.DependencyInjection.OverrideInjector;
import com.seibel.lod.core.util.LodUtil;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Dummy test implementation object for world generator injection unit tests.
 *
 * @author James Seibel
 * @version 2022-12-5
 */
public class TestWorldGenerator implements IDhApiWorldGenerator
{
	public static EDhApiWorldGenThreadMode THREAD_MODE = EDhApiWorldGenThreadMode.SINGLE_THREADED;
	
	
	// testable methods //
	
	@Override
	public int getPriority() { return OverrideInjector.CORE_PRIORITY; }
	
	@Override
	public EDhApiWorldGenThreadMode getThreadingMode() { return THREAD_MODE; }
	
	
	
	
	// not used when unit testing //
	
	//======================//
	// generator parameters //
	//======================//
	
	@Override
	public byte getMinDataDetailLevel() { return LodUtil.BLOCK_DETAIL_LEVEL; }
	
	@Override
	public byte getMaxDataDetailLevel() { return LodUtil.BLOCK_DETAIL_LEVEL; }
	
	@Override
	public byte getMinGenerationGranularity() { return LodUtil.CHUNK_DETAIL_LEVEL; }
	
	@Override
	public byte getMaxGenerationGranularity() { return LodUtil.CHUNK_DETAIL_LEVEL + 2; }
	
	
	//===================//
	// generator methods //
	//===================//
	
	@Override
	public void close() {  }
	
	@Override
	public boolean isBusy() { return false; }
	
	@Override
	public CompletableFuture<Void> generateChunks(int chunkPosMinX, int chunkPosMinZ, byte granularity, byte targetDataDetail, EDhApiDistantGeneratorMode maxGenerationStep, Consumer<Object[]> resultConsumer) { return null; }
	
	@Override
	public void preGeneratorTaskStart() {  }
	
	
}

