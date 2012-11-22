package sharose.mods.idresolver;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import cpw.mods.fml.client.SpriteHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.src.BaseMod;
import net.minecraft.src.Block;
import net.minecraft.src.GuiApiHelper;
import net.minecraft.src.GuiMainMenu;
import net.minecraft.src.GuiModScreen;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.Item;
import net.minecraft.src.ModLoader;
import net.minecraft.src.ScaledResolution;
import net.minecraft.src.WidgetSimplewindow;
import net.minecraft.src.WidgetSinglecolumn;

/**
 * @author ShaRose
 * The basemod file. This is just for the tick hook basically. I could probably make IDResolver an annotation style mod via @Mod, but eh. That class is big enough as it is.
 */
public class mod_IDResolver extends BaseMod {
	private static Boolean firstTick = true;
	// Really should export all string data into a languages file or something...
	private static final String langBlockHookDisabled = "Block hook is disabled or not working! Block conflicts will not be resolved correctly.";
	private static final String langItemHookDisabled = "Item hook is disabled or not working! Item conflicts will not be resolved correctly.";
	private static Boolean secondTick = true;
	private static boolean isFMLSpritesInited = false;
	private static int totalFreeBlocks = 0;
	private static int totalFreeItems = 0;

	static {
		if(!IDResolverCorePlugin.isServer)
		{
		Logger log = ModLoader.getLogger();
		Logger idlog = IDResolver.getLogger();
		String temp = "ID Resolver - "
				+ (IDResolver.wasBlockInited() ? "Block hook is enabled and working."
						: mod_IDResolver.langBlockHookDisabled);
		log.info(temp);
		idlog.info(temp);
		temp = "ID Resolver - "
				+ (IDResolver.wasItemInited() ? "Item hook is enabled and working."
						: mod_IDResolver.langItemHookDisabled);
		log.info(temp);
		idlog.info(temp);
		}
		else
		{
			ModLoader.getLogger().log(Level.ALL, "ID Resolver has detected it was installed into a server: ID Resolver ONLY supports the client and can't work on servers.");
		}
	}

	
	/**
	 * @return the ID information in a string array. This includes the code to make sure FML's sprites are initialized, since freeSlotCount will throw an NPE if a slot sheet isn't added. I work around it by way of reflection calling a private method.
	 */
	public static String[] getIDs() {
		if(!isFMLSpritesInited)
		{
			try
			{
				SpriteHelper.freeSlotCount("/terrain.png");
				isFMLSpritesInited = true;
			}
			catch(Throwable e)
			{
				try
				{
					IDResolver.getLogger().log(Level.INFO,"IDResolver - FML Sprite maps not initialized yet. Attempting to do so:");
					Method initmethod = SpriteHelper.class.getDeclaredMethod("initMCSpriteMaps");
					initmethod.setAccessible(true);
					initmethod.invoke(null);
					isFMLSpritesInited = true;
				}
				catch(Throwable e2)
				{
					IDResolver.getLogger().log(Level.WARNING,"IDResolver - FML Sprite maps failed to initalize.",e2);
				}
			}
			
		}
		
		return new String[] {
				"Block IDs left: " + totalFreeBlocks,
				"Item IDs left: " + totalFreeItems,
				"Item Sprite IDs left: " + mod_IDResolver.getRemainingItemSpriteIDs(),
				"Terrain Sprite IDs left: " + mod_IDResolver.getRemainingTerrainSpriteIDs() };
	}

	public static String getRemainingItemSpriteIDs() {
		try {
			return Integer.toString(SpriteHelper.freeSlotCount("/gui/items.png"));
		} catch (Throwable e) {
			return "ERROR";
		}
	}
	
	public static String getRemainingTerrainSpriteIDs() {
		try {
			return Integer.toString(SpriteHelper.freeSlotCount("/terrain.png"));
		} catch (Throwable e) {
			return "ERROR";
		}
	}

	/**
	 * Basically ticks through all the Blocks and Items to see how many free IDs there are.
	 */
	public static void updateUsed() {
		mod_IDResolver.totalFreeBlocks = 0;
		mod_IDResolver.totalFreeItems = 0;
		for (int i = 1; i < Block.blocksList.length; i++) {
			if (Block.blocksList[i] != null)
				continue;
			if (Item.itemsList[i] != null)
				continue;
			mod_IDResolver.totalFreeBlocks++;
		}
		for (int i = Item.shovelSteel.shiftedIndex; i < Item.itemsList.length; i++) {
			if(i < Block.blocksList.length)
			{
				if (Block.blocksList[i] != null)
					continue;
			}
			if (Item.itemsList[i] == null)
				continue;
			
			mod_IDResolver.totalFreeItems++;
		}
	}

	@Override
	public String getPriorities() {
		return "after:*";
	}

	@Override
	public String getVersion() {
		return "1.4.5 - Update 0";
	}

	@Override
	public void load() {
		if(IDResolverCorePlugin.isServer)
			return;
		ModLoader.setInGUIHook(this, true, false);
	}

	@Override
	public boolean onTickInGUI(float partialTicks, Minecraft mc, GuiScreen scr) {
		if (scr instanceof GuiMainMenu) {
			if (!mod_IDResolver.firstTick && mod_IDResolver.secondTick) {
				if (!IDResolver.wasBlockInited()
						|| !IDResolver.wasItemInited()
						|| (IDResolver.getExtraInfo() != null)) {
					String allErrors = null;	
					if (!IDResolver.wasBlockInited()) {
						allErrors = mod_IDResolver.langBlockHookDisabled;
					}
					if (!IDResolver.wasItemInited()) {
						if (allErrors != null) {
							allErrors += "\r\n\r\n"
									+ mod_IDResolver.langItemHookDisabled;
						} else {
							allErrors = mod_IDResolver.langItemHookDisabled;
						}
					}
					if (IDResolver.getExtraInfo() != null) {
						if (allErrors != null) {
							allErrors += "\r\n\r\n" + IDResolver.getExtraInfo();
						} else {
							allErrors = IDResolver.getExtraInfo();
						}
					}

					WidgetSimplewindow errorWindow = (WidgetSimplewindow) GuiApiHelper
							.makeTextDisplayAndGoBack("ID Resolver Error",
									allErrors, "Continue to main menu", false);
					((WidgetSinglecolumn) errorWindow.mainWidget).childDefaultWidth = 350;
					GuiModScreen.show(errorWindow);
				}

				mod_IDResolver.secondTick = false;
			}
			if (mod_IDResolver.firstTick) {
				IDResolver.checkLooseSettings(true);
				mod_IDResolver.updateUsed();
				IDResolver.reLoadModGui();
				mod_IDResolver.firstTick = false;
			}
			if (IDResolver.showTick(true)) {
				ScaledResolution scaledresolution = new ScaledResolution(
						mc.gameSettings, mc.displayWidth, mc.displayHeight);
				String[] ids = mod_IDResolver.getIDs();
				for (int i = 0; i < ids.length; i++) {
					mc.fontRenderer.drawStringWithShadow(
							ids[i],
							scaledresolution.getScaledWidth()
									- mc.fontRenderer.getStringWidth(ids[i])
									- 1, i * 12, 0xffffff);
				}
			}
		}
		return true;
	}
}
