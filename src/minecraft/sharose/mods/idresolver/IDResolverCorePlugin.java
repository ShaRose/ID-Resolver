package sharose.mods.idresolver;

import java.util.Map;

import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.FMLRelauncher;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * @author ShaRose
 * The core plugin. Just tells FML to use the patcher class.
 */
public class IDResolverCorePlugin implements IFMLLoadingPlugin {

	public static boolean isServer;
	static
	{
		isServer = FMLRelauncher.side().equals("SERVER");
	}

	
	@Override
	public String[] getLibraryRequestClass() {
		return null;
	}

	@Override
	public String[] getASMTransformerClass() {
		if(isServer)
		{
			FMLRelaunchLog.warning("ID Resolver has detected that it is in a server environment: Skipping load.");
			return null;
		}
		return new String[]{"sharose.mods.idresolver.Patcher.IDResolverPatcher"};
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {
		// Not needed.
	}
	
}
