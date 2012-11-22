package sharose.mods.idresolver;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.logging.Level;

import net.minecraft.src.BaseMod;
import net.minecraft.src.Block;
import net.minecraft.src.ItemSaddle;
import net.minecraft.src.Material;
import net.minecraft.src.ModLoader;


public class mod_TestMod1 extends BaseMod {
	
	@Override
	public String getVersion() {
		return "TEST ONLY";
	}

	@Override
	public void load() {
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
