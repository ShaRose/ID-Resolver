package sharose.mods.idresolver;

import net.minecraft.src.Block;
import net.minecraft.src.Material;

@IDRestrictionAnnotation(maxIDRValue = 255)
public class BlockLimiterExample extends Block {
	public BlockLimiterExample(int par1, Material par2Material) {
		super(par1, par2Material);
	}
}
