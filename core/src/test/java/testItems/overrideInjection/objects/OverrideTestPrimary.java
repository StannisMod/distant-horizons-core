package testItems.overrideInjection.objects;

import com.seibel.lod.coreapi.DependencyInjection.OverrideInjector;
import testItems.overrideInjection.interfaces.IOverrideTest;

/**
 * Dummy test implementation object for dependency injection unit tests.
 *
 * @author James Seibel
 * @version 2022-9-5
 */
public class OverrideTestPrimary implements IOverrideTest
{
	public static int PRIORITY = OverrideInjector.DEFAULT_NON_CORE_OVERRIDE_PRIORITY;
	
	public static int VALUE = 3;
	
	
	@Override
	public int getValue() { return VALUE; }
	
	@Override
	public int getPriority() { return PRIORITY; }
	
}
