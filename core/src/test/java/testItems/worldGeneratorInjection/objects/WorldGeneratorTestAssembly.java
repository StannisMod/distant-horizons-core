package testItems.worldGeneratorInjection.objects;

import com.seibel.lod.coreapi.util.StringUtil;

/**
 * assembly classes are used to reference the package they are in.
 *
 * @author james seibel
 * @version 2022-7-26
 */
public class WorldGeneratorTestAssembly
{
	
	/** Returns the first N packages in this class' path. */
	public static String getPackagePath(int numberOfPackagesToReturn)
	{
		String thisPackageName = WorldGeneratorTestAssembly.class.getPackage().getName();
		int secondPackageEndingIndex = StringUtil.nthIndexOf(thisPackageName, ".", numberOfPackagesToReturn);
		return thisPackageName.substring(0, secondPackageEndingIndex);
	}
	
}
