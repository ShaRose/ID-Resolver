package sharose.mods.idresolver;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import sharose.mods.guiapi.GuiApiHelper;
import sharose.mods.guiapi.GuiModScreen;
import sharose.mods.guiapi.WidgetSimplewindow;
import sharose.mods.guiapi.WidgetSinglecolumn;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;

/**
 * @author ShaRose
 * The basemod file. This is just for the tick hook basically. I could probably make IDResolver an annotation style mod via @Mod, but eh. That class is big enough as it is.
 */
@Mod(name = "ID Resolver", modid = "IDResolver", version = "1.5.1.0", acceptedMinecraftVersions = "1.5.1",dependencies="after:*")
public class IDResolverMod implements ITickHandler {
	private static Boolean firstTick = true;
	// Really should export all string data into a languages file or something...
	private static final String langBlockHookDisabled = "Block hook is disabled or not working! Block conflicts will not be resolved correctly.";
	private static final String langItemHookDisabled = "Item hook is disabled or not working! Item conflicts will not be resolved correctly.";
	private static Boolean secondTick = true;
	private static int totalFreeBlocks = 0;
	private static int totalFreeItems = 0;

	static {
		if(!IDResolverCorePlugin.isServer)
		{
		Logger log = FMLLog.getLogger();
		Logger idlog = IDResolver.getLogger();
		String temp = "ID Resolver - "
				+ (IDResolver.wasBlockInited() ? "Block hook is enabled and working."
						: IDResolverMod.langBlockHookDisabled);
		log.info(temp);
		idlog.info(temp);
		temp = "ID Resolver - "
				+ (IDResolver.wasItemInited() ? "Item hook is enabled and working."
						: IDResolverMod.langItemHookDisabled);
		log.info(temp);
		idlog.info(temp);
		}
		else
		{
			FMLLog.getLogger().log(Level.ALL, "ID Resolver has detected it was installed into a server: ID Resolver ONLY supports the client and can't work on servers.");
		}
	}

	/**
	 * @return the ID information in a string array. This includes the code to make sure FML's sprites are initialized, since freeSlotCount will throw an NPE if a slot sheet isn't added. I work around it by way of reflection calling a private method.
	 */
	@SideOnly(Side.CLIENT)
	public static String[] getIDs() {
		return new String[] {
				"Block IDs left: " + totalFreeBlocks,
				"Item IDs left: " + totalFreeItems };
	}

	/**
	 * Basically ticks through all the Blocks and Items to see how many free IDs there are.
	 */
	public static void updateUsed() {
		IDResolverMod.totalFreeBlocks = 0;
		IDResolverMod.totalFreeItems = 0;
		for (int i = 1; i < Block.blocksList.length; i++) {
			if (Block.blocksList[i] != null)
				continue;
			if (Item.itemsList[i] != null)
				continue;
			IDResolverMod.totalFreeBlocks++;
		}
		for (int i = Item.shovelSteel.itemID; i < Item.itemsList.length; i++) {
			if(i < Block.blocksList.length)
			{
				if (Block.blocksList[i] != null)
					continue;
			}
			if (Item.itemsList[i] != null)
				continue;
			
			IDResolverMod.totalFreeItems++;
		}
	}

	@Init
	@SideOnly(Side.CLIENT)
	public void init(FMLInitializationEvent event) {
		if(IDResolverCorePlugin.isServer)
			return;
		TickRegistry.registerTickHandler(this, Side.CLIENT);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
		if (!type.contains(TickType.RENDER)) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null) {
			return; // what
		}
		if (mc.currentScreen == null) {
			return;
		}

		if (mc.currentScreen instanceof GuiMainMenu) {
			if (!IDResolverMod.firstTick && IDResolverMod.secondTick) {
				if (!IDResolver.wasBlockInited()
						|| !IDResolver.wasItemInited()
						|| (IDResolver.getExtraInfo() != null)) {
					String allErrors = null;	
					if (!IDResolver.wasBlockInited()) {
						allErrors = IDResolverMod.langBlockHookDisabled;
					}
					if (!IDResolver.wasItemInited()) {
						if (allErrors != null) {
							allErrors += "\r\n\r\n"
									+ IDResolverMod.langItemHookDisabled;
						} else {
							allErrors = IDResolverMod.langItemHookDisabled;
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

				IDResolverMod.secondTick = false;
			}
			if (IDResolverMod.firstTick) {
				IDResolver.checkLooseSettings(true);
				IDResolverMod.updateUsed();
				IDResolver.reLoadModGui();
				IDResolverMod.firstTick = false;
			}
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {
		if (!type.contains(TickType.RENDER)) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null) {
			return; // what
		}
		if (mc.currentScreen == null) {
			return;
		}

		if (mc.currentScreen instanceof GuiMainMenu) {
			if (IDResolver.showTick(true)) {
				ScaledResolution scaledresolution = new ScaledResolution(
						mc.gameSettings, mc.displayWidth, mc.displayHeight);
				String[] ids = IDResolverMod.getIDs();
				for (int i = 0; i < ids.length; i++) {
					mc.fontRenderer.drawStringWithShadow(
							ids[i],
							scaledresolution.getScaledWidth()
									- mc.fontRenderer.getStringWidth(ids[i])
									- 1, i * 12, 0xffffff);
				}
			}
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public EnumSet<TickType> ticks() {
		return EnumSet.of(TickType.RENDER);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public String getLabel() {
		return "ID Resolver main menu alerter";
	}
}
