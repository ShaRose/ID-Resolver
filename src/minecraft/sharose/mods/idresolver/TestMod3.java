package sharose.mods.idresolver;

import net.minecraft.src.Material;
import net.minecraft.src.ModLoader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid="TestMod3",name="Test Mod 3",version="1.0")
public class TestMod3 {
	@PreInit
	public void loadMethod(FMLPreInitializationEvent event)
	{
		ModLoader.registerBlock(new BlockLimiterExample(200,Material.air));
		ModLoader.registerBlock(new BlockLimiterAnnotationInheritanceExample(201,Material.air));
	}
}
