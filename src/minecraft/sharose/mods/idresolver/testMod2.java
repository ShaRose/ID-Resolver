package sharose.mods.idresolver;

import java.lang.reflect.Field;
import java.util.logging.Level;

import net.minecraft.src.Block;
import net.minecraft.src.ItemSaddle;
import net.minecraft.src.Material;
import net.minecraft.src.ModLoader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid="TestMod2",name="Test Mod 2",version="1.0")
public class testMod2
{
	@PreInit
	public void loadMethod(FMLPreInitializationEvent event)
	{
		//addCrap(140,110,true);
		//addCrap(390,1800,true);
		//addCrap(2270,1800,false);
		//addCrap(4100,10000,false);
	}
	
	public void addCrap(int startID,int count, boolean isBlock)
	{
		for (int i = startID; i < startID + count; i++) {
			if(isBlock)
			{
				ModLoader.registerBlock(new Block(i,Material.air));
			}
			else
			{
				new ItemSaddle(i);
			}
		}
	}
}
