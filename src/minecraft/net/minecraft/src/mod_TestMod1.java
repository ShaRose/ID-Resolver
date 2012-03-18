package net.minecraft.src;

public class mod_TestMod1 extends BaseMod {

	@Override
	public String getVersion() {
		return "TEST ONLY";
	}

	@Override
	public void load() {
		Item tempitem = null;
		if (true) {
			tempitem = new Item(20000);
			tempitem = new Item(20001).setItemName("TestItem1");
			tempitem = new Item(20002).setItemName("TestItem2");
			ModLoader.addName(tempitem, "Test Item 2");
		}
		if (true) {
			tempitem = new ItemEgg(20003);
			tempitem = new ItemEgg(20004).setItemName("TestItem4");
			tempitem = new ItemEgg(20005).setItemName("TestItem5");
			ModLoader.addName(tempitem, "Test Item 5");
		}
		Block tempblock = null;
		if (true) {
			tempblock = new Block(200, Material.cake);
			ModLoader.registerBlock(tempblock);
			tempblock = new Block(201, Material.cake)
					.setBlockName("TestBlock1");
			ModLoader.registerBlock(tempblock);
			tempblock = new Block(202, Material.cake)
					.setBlockName("TestBlock2");
			ModLoader.registerBlock(tempblock);
			ModLoader.addName(tempblock, "Test Block 2");
		}
		if (true) {
			tempblock = new BlockLog(203);
			ModLoader.registerBlock(tempblock);
			tempblock = new BlockLog(204).setBlockName("TestBlock4");
			ModLoader.registerBlock(tempblock);
			tempblock = new BlockLog(205).setBlockName("TestBlock5");
			ModLoader.registerBlock(tempblock);
			ModLoader.addName(tempblock, "Test Block 5");
		}
	}

}
