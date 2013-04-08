package sharose.mods.idresolver;

import java.util.Map;

import cpw.mods.fml.relauncher.FMLRelauncher;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * @author ShaRose
 * The core plugin. Just tells FML to use the patcher class.
 */
@IFMLLoadingPlugin.MCVersion("1.5.1")
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
