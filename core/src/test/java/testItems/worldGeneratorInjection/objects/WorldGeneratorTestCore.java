package testItems.worldGeneratorInjection.objects;

import com.seibel.lod.api.enums.worldGeneration.EDhApiWorldGenThreadMode;
import com.seibel.lod.coreapi.DependencyInjection.OverrideInjector;
/**
 * Dummy test implementation object for world generator injection unit tests.
 *
 * @author James Seibel
 * @version 2022-12-5
 */
public class WorldGeneratorTestCore extends TestWorldGenerator
{
	public static int PRIORITY = OverrideInjector.CORE_PRIORITY;
	public static EDhApiWorldGenThreadMode THREAD_MODE = EDhApiWorldGenThreadMode.SINGLE_THREADED;
	
	
	@Override
	public int getPriority() { return PRIORITY; }
	
	@Override 
	public EDhApiWorldGenThreadMode getThreadingMode() { return THREAD_MODE; }
	
}
