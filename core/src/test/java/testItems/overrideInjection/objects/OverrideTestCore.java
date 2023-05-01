package testItems.overrideInjection.objects;

import com.seibel.lod.coreapi.DependencyInjection.OverrideInjector;
import testItems.overrideInjection.interfaces.IOverrideTest;

/**
 * Dummy test implementation object for dependency injection unit tests.
 *
 * @author James Seibel
 * @version 2022-9-5
 */
public class OverrideTestCore implements IOverrideTest
{
	public static int VALUE = 1;
	
	
	@Override
	public int getValue() { return VALUE; }
	
	@Override
	public int getPriority() { return OverrideInjector.CORE_PRIORITY; }
	
}
