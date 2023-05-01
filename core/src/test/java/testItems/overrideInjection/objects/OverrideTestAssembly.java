package testItems.overrideInjection.objects;

import com.seibel.lod.coreapi.util.StringUtil;

/**
 * assembly classes are used to reference the package they are in.
 *
 * @author james seibel
 * @version 2022-7-19
 */
public class OverrideTestAssembly
{
	
	/** Returns the first N packages in this class' path. */
	public static String getPackagePath(int numberOfPackagesToReturn)
	{
		String thisPackageName = OverrideTestAssembly.class.getPackage().getName();
		int secondPackageEndingIndex = StringUtil.nthIndexOf(thisPackageName, ".", numberOfPackagesToReturn);
		return thisPackageName.substring(0, secondPackageEndingIndex);
	}
	
}
