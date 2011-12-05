package net.minecraft.src;

public class mod_TestMod3 extends BaseMod {

	@Override
	public void load() {
		Item tempitem = null;
		if(true)
		{
			tempitem = new Item(30000);
			tempitem = new Item(30001).setItemName("TestItem1");
			tempitem = new Item(30002).setItemName("TestItem2");
			ModLoader.AddName(tempitem, "Test Item 2");
		}
		if(true)
		{
			tempitem = new ItemEgg(30003);
			tempitem = new ItemEgg(30004).setItemName("TestItem4");
			tempitem = new ItemEgg(30005).setItemName("TestItem5");
			ModLoader.AddName(tempitem, "Test Item 5");
		}
		Block tempblock = null;
		if(true)
		{
			tempblock = new Block(100, Material.cakeMaterial);
			ModLoader.RegisterBlock(tempblock);
			tempblock = new Block(101, Material.cakeMaterial)
					.setBlockName("TestBlock1");
			ModLoader.RegisterBlock(tempblock);
			tempblock = new Block(102, Material.cakeMaterial)
					.setBlockName("TestBlock2");
			ModLoader.RegisterBlock(tempblock);
			ModLoader.AddName(tempblock, "Test Block 2");
		}
		if(true)
		{
			tempblock = new BlockLog(103);
			ModLoader.RegisterBlock(tempblock);
			tempblock = new BlockLog(104).setBlockName("TestBlock4");
			ModLoader.RegisterBlock(tempblock);
			tempblock = new BlockLog(105).setBlockName("TestBlock5");
			ModLoader.RegisterBlock(tempblock);
			ModLoader.AddName(tempblock, "Test Block 5");
		}
	}
	
	@Override
	public String getVersion() {
		return "TEST ONLY";
	}

}
