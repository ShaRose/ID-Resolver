package net.minecraft.src;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.client.Minecraft;
import de.matthiasmann.twl.ScrollPane;

public class mod_IDResolver extends BaseMod {
	private static Boolean firstTick = true;
	private static Boolean secondTick = true;
	private static final String langBlockHookDisabled = "Block hook is disabled or not working! Block conflicts will not be resolved correctly.";
	private static final String langItemHookDisabled = "Item hook is disabled or not working! Item conflicts will not be resolved correctly.";
	private static final String langNotInstalledCorrectly = "ID Resolver has detected that it is not installed correctly. ID resolver needs to be installed directly into the minecraft jar, as it needs to overwrite some classes. Modloader's mod folder does not support this as dictated on it's topic, and ID resolver's. Please install correctly, and restart minecraft.";
	private static Field modLoaderSprites;
	private static Boolean showError = false;
	private static int totalRegisteredBlocks = 0;
	private static int totalRegisteredItems = 0;
	static {
		try {
			mod_IDResolver.modLoaderSprites = ModLoader.class
					.getDeclaredField("itemSpritesLeft");
			mod_IDResolver.modLoaderSprites.setAccessible(true);
		} catch (Throwable e) {
			IDResolver
					.GetLogger()
					.log(Level.INFO,
							"IDResolver - Unable to get itemSpritesLeft field from Modloader.",
							e);
		}
		if (mod_IDResolver.class.getProtectionDomain().getCodeSource()
				.getLocation().toString().indexOf(".minecraft/mods") != -1) {
			mod_IDResolver.showError = true;
			IDResolver.GetLogger().log(Level.INFO,
					mod_IDResolver.langNotInstalledCorrectly);
		}
	}

	public static String[] getIDs() {
		return new String[] {
				String.format("Block IDs left: %s",
						mod_IDResolver.getRemainingBlockIDs()),
				String.format("Item IDs left: %s",
						mod_IDResolver.getRemainingItemIDs()),
				String.format("Sprite IDs left: %s",
						mod_IDResolver.getRemainingSpriteIDs()) };
	}

	public static Integer getRemainingBlockIDs() {
		return Block.blocksList.length - mod_IDResolver.totalRegisteredBlocks;
	}

	public static Integer getRemainingItemIDs() {
		return Item.itemsList.length - mod_IDResolver.totalRegisteredItems;
	}

	public static String getRemainingSpriteIDs() {
		try {
			return ((Integer) mod_IDResolver.modLoaderSprites.get(null))
					.toString();
		} catch (Throwable e) {
			return "ERROR";
		}
	}

	public static void UpdateUsed() {
		mod_IDResolver.totalRegisteredBlocks = 1;
		mod_IDResolver.totalRegisteredItems = 0;
		for (int i = 1; i < Block.blocksList.length; i++) {
			if (Block.blocksList[i] != null) {
				mod_IDResolver.totalRegisteredBlocks++;
			}
		}
		for (int i = Block.blocksList.length; i < Item.itemsList.length; i++) {
			if (Item.itemsList[i] != null) {
				mod_IDResolver.totalRegisteredItems++;
			}
		}
	}

	@Override
	public String getVersion() {
		return "1.0.0 - Update 0";
	}

	@Override
	public void load() {
		Logger log = ModLoader.getLogger();
		Logger idlog = IDResolver.GetLogger();
		String temp = "ID Resolver - "
				+ (IDResolver.WasBlockInited() ? "Block hook is enabled and working."
						: mod_IDResolver.langBlockHookDisabled);
		log.info(temp);
		idlog.info(temp);
		temp = "ID Resolver - "
				+ (IDResolver.WasItemInited() ? "Item hook is enabled and working."
						: mod_IDResolver.langItemHookDisabled);
		log.info(temp);
		idlog.info(temp);
		ModLoader.SetInGUIHook(this, true, false);
	}

	@Override
	public boolean OnTickInGUI(float partialTicks, Minecraft mc, GuiScreen scr) {
		if (scr instanceof GuiMainMenu) {
			if (!mod_IDResolver.firstTick && mod_IDResolver.secondTick) {
				if (mod_IDResolver.showError || !IDResolver.WasBlockInited()
						|| !IDResolver.WasItemInited() || IDResolver.GetExtraInfo() != null) {
					String allErrors = null;
					if (mod_IDResolver.showError) {
						allErrors = mod_IDResolver.langNotInstalledCorrectly;
					}
					if (!IDResolver.WasBlockInited()) {
						if (allErrors != null) {
							allErrors += "\r\n\r\n"
									+ mod_IDResolver.langBlockHookDisabled;
						} else {
							allErrors = mod_IDResolver.langBlockHookDisabled;
						}
					}
					if (!IDResolver.WasItemInited()) {
						if (allErrors != null) {
							allErrors += "\r\n\r\n"
									+ mod_IDResolver.langItemHookDisabled;
						} else {
							allErrors = mod_IDResolver.langItemHookDisabled;
						}
					}
					if (IDResolver.GetExtraInfo() != null) {
						if (allErrors != null) {
							allErrors += "\r\n\r\n"
									+ IDResolver.GetExtraInfo();
						} else {
							allErrors = IDResolver.GetExtraInfo();
						}
					}
					
					WidgetSimplewindow errorWindow = (WidgetSimplewindow) GuiApiHelper
							.makeTextDisplayAndGoBack("ID Resolver Error",
									allErrors, "Continue to main menu", false);
					((WidgetSinglecolumn) (((ScrollPane) errorWindow.mainWidget)
							.getContent())).childDefaultWidth = 350;
					GuiModScreen.show(errorWindow);
				}
				
				mod_IDResolver.secondTick = false;
			}
			if (mod_IDResolver.firstTick) {
				IDResolver.CheckLooseSettings(true);
				mod_IDResolver.UpdateUsed();
				IDResolver.ReLoadModGui();
				mod_IDResolver.firstTick = false;
			}
			if (IDResolver.ShowTick(true)) {
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
