package sharose.mods.idresolver;

import java.awt.Desktop;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import net.minecraft.client.Minecraft;
import net.minecraft.src.BaseMod;
import net.minecraft.src.Block;
import net.minecraft.src.GuiApiHelper;
import net.minecraft.src.GuiModScreen;
import net.minecraft.src.GuiWidgetScreen;
import net.minecraft.src.Item;
import net.minecraft.src.ItemArmor;
import net.minecraft.src.ItemBlock;
import net.minecraft.src.ItemFood;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ModAction;
import net.minecraft.src.ModSettingScreen;
import net.minecraft.src.SettingBoolean;
import net.minecraft.src.SettingInt;
import net.minecraft.src.SettingList;
import net.minecraft.src.StatCollector;
import net.minecraft.src.WidgetBoolean;
import net.minecraft.src.WidgetInt;
import net.minecraft.src.WidgetItem2DRender;
import net.minecraft.src.WidgetList;
import net.minecraft.src.WidgetSimplewindow;
import net.minecraft.src.WidgetSingleRow;
import net.minecraft.src.WidgetSinglecolumn;
import net.minecraft.src.WidgetTick;

import org.lwjgl.Sys;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.Mod;
import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.Color;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.ScrollPane;
import de.matthiasmann.twl.Scrollbar;
import de.matthiasmann.twl.Scrollbar.Orientation;
import de.matthiasmann.twl.TextArea;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.renderer.Font;
import de.matthiasmann.twl.renderer.Image;
import de.matthiasmann.twl.renderer.Texture;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLTexture.Filter;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLTexture.Format;
import de.matthiasmann.twl.renderer.lwjgl.RenderScale;

/**
 * @author ShaRose Ahh, the big boy. This basically holds almost all logic for
 *         ID Resolver, from the ID resolving parts, to the UI, to.. well pretty
 *         much everything besides patching and the tick stuff in the
 *         mod_IDResolver class.
 */
public class IDResolver {
	private static String[] armorTypes = new String[] { "Helmet", "Chestplate",
			"Leggings", "Boots" };

	private static Boolean attemptedRecover = false;
	private static String autoAssignMod;
	private static Field blockIdField;
	private static SettingBoolean checkForLooseSettings = null;
	private static String extraInfo = null;
	private static File idPath;
	/**
	 * A mapping from Item / Block ID to mod. Used for caching when showing the
	 * Display ID Status menu.
	 */
	private static Hashtable<Integer, String> idToMod = null;
	private static boolean initialized;
	private static Field itemIdField;
	private static Properties knownIDs;
	private static final String langLooseSettingsFound = "ID resolver has found some loose settings that aren't being used. Would you like to view a menu to remove them?";
	private static final String langMinecraftShuttingDown = "Minecraft is shutting down, but ID Resolver did not attempt to do so. It is probably due to another exception, such as not enough sprite indexes. In some cases mods will attempt to shut down Minecraft due to possible ID conflicts, and don't check for ID resolver, so ID resolver will attempt to recover Minecraft. If this does not work, please check your ModLoader.txt and ID Resolver.txt for more information.";
	/**
	 * This shows all loaded entries: It's used for finding loose settings. If
	 * it's in knownIDs but isn't here, it wasn't accessed yet.
	 */
	private static HashSet<String> loadedEntries = new HashSet<String>();
	private static Logger logger;
	private static Properties modPriorities;
	private static ModSettingScreen modScreen;
	private static boolean overridesEnabled;
	private static File priorityPath;
	private static String settingsComment;
	private static SettingBoolean showOnlyConf = null;
	private static SettingBoolean showTickMM = null;
	private static SettingBoolean showTickRS = null;
	private static Boolean shutdown = false;
	/**
	 * Again, a quick lookup table for if an ID is vanilla or not.
	 */
	private static boolean[] vanillaIDs = new boolean[35840];
	private static Boolean wasBlockInited = false;
	private static Boolean wasItemInited = false;
	private static WidgetSimplewindow[] windows;
	private static StackTraceElement[] lastStackTrace;
	private static int lastStackTraceID;
	/**
	 * Is ID Resolver doing 'streaming' right now? This should only be true
	 * during actual MC init.
	 */
	private static boolean isStreamingSaves = true;
	/**
	 * Something else to try and keep IO as low as possible: Only returns true
	 * if something actually changed.
	 */
	private static boolean isPrioritiesChanged = false;
	/**
	 * So that I know if I have to write an entry or not.
	 */
	private static Hashtable<String, Boolean> wasStreamed = new Hashtable<String, Boolean>();
	private static BufferedWriter streamingOutputStream = null;
	/**
	 * Set to true if the config files were created during load: this way it
	 * knows to ask you on start.
	 */
	private static boolean isFirstStart = false;
	
	private static int firstOpenBlockCache = 0;
	private static int firstOpenItemCache = 0;
	private static int lastOpenBlockCache = 0;
	private static int lastOpenItemCache = 0;
	
	private static int autoAssignBlockLast = -1;
	
	static {
		IDResolver.logger = Logger.getLogger("IDResolver");
		try {
			FileHandler logHandler = new FileHandler(new File(
					Minecraft.getMinecraftDir(), "IDResolver.txt").getPath());
			logHandler.setFormatter(new SimpleFormatter());
			IDResolver.logger.addHandler(logHandler);
			IDResolver.logger.setLevel(Level.ALL);
		} catch (Throwable e) {
			throw new RuntimeException("Unable to create logger!", e);
		}
		IDResolver.settingsComment = "IDResolver Known / Set IDs file. Please do not edit manually.";
		IDResolver.overridesEnabled = true;
		IDResolver.autoAssignMod = null;
		IDResolver.initialized = false;
		IDResolver.setupOverrides();
		IDResolver.addModGui();
	}

	/**
	 * Sets up the mod screen, and then calls reloadIDs, which makes sure
	 * everything is up to date.
	 */
	private static void addModGui() {
		IDResolver.modScreen = new ModSettingScreen("ID Resolver");
		IDResolver.modScreen.setSingleColumn(true);
		IDResolver.modScreen.widgetColumn.childDefaultWidth = 300;
		IDResolver.modScreen.widgetColumn.add(GuiApiHelper.makeButton(
				"Reload Entries", "reLoadModGui", IDResolver.class, true));
		IDResolver.reloadIDs();
	}

	/**
	 * @param info
	 *            Extra data to append to the on-load error message.
	 */
	private static void appendExtraInfo(String info) {
		if (IDResolver.extraInfo == null) {
			IDResolver.extraInfo = info;
		} else {
			IDResolver.extraInfo += "\r\n\r\n" + info;
		}
	}

	/**
	 * This is what builds up all the info for Display ID Status.
	 * 
	 * @param builder
	 *            a StringBuilder to append info to.
	 * @param index
	 *            The Item ID.
	 * @param isBlock
	 *            If it's a block or not.
	 */
	private static void buildItemInfo(StringBuilder builder, int index,
			boolean isBlock) {
		Item item = Item.itemsList[index];

		builder.append(String.format("\r\nSubitems: %s", item.getHasSubtypes()))
				.append(String.format("\r\nIs Block: %s", isBlock));

		if (!isBlock) {
			builder.append(String.format("\r\nClassname: %s", item.getClass()
					.getName()));
		} else {
			builder.append(String.format("\r\nClassname: %s",
					Block.blocksList[index].getClass().getName()));
		}

		try {
			builder.append(String.format("\r\nMax stack: %s",
					item.getItemStackLimit()));
		} catch (Throwable e) {
			builder.append("\r\nMax stack: Error");
		}
		try {
			builder.append(String.format("\r\nDamage versus entities: %s",
					item.getDamageVsEntity(null)));
		} catch (Throwable e) {
			builder.append("\r\nDamage versus entities: Error");
		}
		try {
			builder.append(String.format("\r\nEnchantability: %s",
					item.getItemEnchantability()));
		} catch (Throwable e) {
			builder.append("\r\nEnchantability: Error");
		}
		try {
			builder.append(String.format("\r\nMax Damage: %s",
					item.getMaxDamage()));
		} catch (Throwable e) {
			builder.append("\r\nMax Damage: Error");
		}
		if (item instanceof ItemArmor) {
			ItemArmor armor = ((ItemArmor) item);
			builder.append(String.format("\r\nMax Damage Reduction: %s",
					armor.damageReduceAmount));
			builder.append(String.format("\r\nArmor Slot: %s",
					IDResolver.armorTypes[armor.armorType]));
		}

		if (item instanceof ItemFood) {
			ItemFood food = ((ItemFood) item);
			try {
				builder.append(String.format("\r\nHeal Amount: %s",
						food.getHealAmount()));
			} catch (Throwable e) {
				builder.append("\r\nHeal Amount: Error");
			}
			try {
				builder.append(String.format("\r\nHunger Modifier: %s",
						food.getSaturationModifier()));
			} catch (Throwable e) {
				builder.append("\r\nHunger Modifie: Error");
			}
			try {
				builder.append(String.format("\r\nWolves enjoy: %s",
						food.isWolfsFavoriteMeat()));
			} catch (Throwable e) {
				builder.append("\r\nWolves enjoy: Error");
			}
		}

		if (isBlock) {
			Block block = Block.blocksList[index];
			try {
				builder.append(String.format("\r\nBlock Hardness: %s",
						block.getBlockHardness(null, 0, 0, 0)));
			} catch (Throwable e) {
				builder.append("\r\nBlock Hardness: Error");
			}
			try {
				builder.append(String.format("\r\nBlock Slipperiness: %s",
						block.slipperiness));
			} catch (Throwable e) {
				builder.append("\r\nBlock Slipperiness: Error");
			}
			try {
				builder.append(String.format("\r\nBlock Light Level: %s",
						block.getLightValue(null, 0, 0, 0)));
			} catch (Throwable e) {
				builder.append("\r\nBlock Light Level: Error");
			}
			builder.append(String.format("\r\nBlock Opacity: %s",
					Block.lightOpacity[index]));

		}

		if (IDResolver.idToMod != null) {
			if (IDResolver.idToMod.containsKey(index)) {
				builder.append(String.format("\r\nMod: %s",
						IDResolver.idToMod.get(index)));
			}
		}
	}

	/**
	 * @return True if I should check for loose settings after loading.
	 */
	private static boolean checkForLooseSettings() {
		if (!IDResolver.modPriorities.containsKey("CheckForLooseSettings")) {
			IDResolver.modPriorities.setProperty("CheckForLooseSettings",
					"true");
			isPrioritiesChanged = true;
			try {
				IDResolver.storeProperties();
			} catch (Throwable e) {
				IDResolver.logger
						.log(Level.INFO,
								"Could not save properties after adding CheckForLooseSettings option!",
								e);
			}
			return true;
		}
		return IDResolver.modPriorities.getProperty("CheckForLooseSettings")
				.equalsIgnoreCase("true");
	}

	/**
	 * Just used by the Check Loose IDs button.
	 */
	@SuppressWarnings("unused")
	private static void checkLooseIDs() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'CheckLooseIDs' button.");
		IDResolver.checkLooseSettings(false);
	}

	/**
	 * What ACTUALLY checks for loose IDs.
	 * 
	 * @param automatic
	 *            True if it was done after load: False otherwise. This way if
	 *            you used the button it won't ask "don't show this again".
	 */
	public static void checkLooseSettings(boolean automatic) {
		if (IDResolver.checkForLooseSettings() || !automatic) {
			ArrayList<String> unusedIDs = IDResolver.checkUnusedIDs();
			if (unusedIDs.size() != 0) {
				Logger idlog = IDResolver.getLogger();
				idlog.info("Detected " + unusedIDs.size()
						+ " unused (Loose) IDs.");

				WidgetSinglecolumn widget = new WidgetSinglecolumn(
						new Widget[0]);
				TextArea textarea = GuiApiHelper.makeTextArea(
						IDResolver.langLooseSettingsFound, false);
				widget.add(textarea);
				widget.heightOverrideExceptions.put(textarea, 0);
				Button temp = GuiApiHelper.makeButton("Go to trim menu",
						new ModAction(IDResolver.class, "trimLooseSettings",
								new Class[] { (ArrayList.class) })
								.setDefaultArguments(unusedIDs), true);
				widget.add(temp);
				temp.setTooltipContent(GuiApiHelper.makeTextArea("This opens a menu where you can trim loose IDs on a case-by-case basis.", false));

				temp = GuiApiHelper.makeButton("Trim all loose settings",
						new ModAction(IDResolver.class, "trimLooseSettingsAll",
								new Class[] { (ArrayList.class) })
								.setDefaultArguments(unusedIDs), true);
				widget.add(temp);
				temp.setTooltipContent(GuiApiHelper.makeTextArea("This trims ALL loose settings automatically.", false));
				
				temp = GuiApiHelper.makeButton(
						"Trim all loose settings for unloaded mods",
						new ModAction(IDResolver.class,
								"trimLooseSettingsAutoUnLoaded",
								new Class[] { (ArrayList.class) })
								.setDefaultArguments(unusedIDs), true);
				widget.add(temp);
				temp.setTooltipContent(GuiApiHelper.makeTextArea("This only trims IDs for mods that don't seem to be loaded at all.", false));

				
				temp = GuiApiHelper.makeButton(
						"Trim all loose settings for loaded mods",
						new ModAction(IDResolver.class,
								"trimLooseSettingsAutoLoaded",
								new Class[] { (ArrayList.class) })
								.setDefaultArguments(unusedIDs), true);
				widget.add(temp);
				temp.setTooltipContent(GuiApiHelper.makeTextArea("This only trims IDs for mods that have loaded, but have loose IDs anyways. (For example, a mod that now detects that an equivilent item was already detected might now load it's own version)", false));
				
				if (automatic) {
					temp = GuiApiHelper.makeButton(
							"Ignore and don't ask again", new ModAction(
									IDResolver.class,
									"ignoreLooseDetectionAndDisable"), true);
					widget.add(temp);
					temp.setTooltipContent(GuiApiHelper.makeTextArea("This just goes to the main menu and sets it so that loose settings won't be checked on next load.", false));
				}

				WidgetSimplewindow window = new WidgetSimplewindow(widget,
						null, true);

				((WidgetSinglecolumn) window.mainWidget).childDefaultWidth = 250;
				GuiModScreen.show(window);
			} else {
				if (!automatic) {
					GuiModScreen.show(GuiApiHelper.makeTextDisplayAndGoBack(
							null,
							"No settings in need of trimming were detected.",
							"Back", false));
				}
			}
		}
	}

	/**
	 * @return The list of unused IDs, by signature.
	 */
	public static ArrayList<String> checkUnusedIDs() {
		ArrayList<String> unused = new ArrayList<String>();
		for (Entry<Object, Object> entry : IDResolver.knownIDs.entrySet()) {
			String key = (String) entry.getKey();
			if ("SAVEVERSION".equals(key)) {
				continue;
			}
			if (!IDResolver.loadedEntries.contains(key)) {
				unused.add(key);
			}
		}
		return unused;
	}

	/**
	 * Clears out ALL saved IDs. Priorities are left alone.
	 */
	@SuppressWarnings("unused")
	private static void clearAllIDs() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'ClearAllIDs' button.");
		IDResolver.knownIDs.clear();
		IDResolver.wasStreamed.clear();
		try {
			IDResolver.storeProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO, "Could not save properties", e);
		}
	}

	/**
	 * A helper method. Just checks for saved IDs and such before trying to load
	 * up all the UI stuff.
	 * 
	 * @param resolver
	 *            The IDResolver.
	 * @return the new ID.
	 */
	private static int conflictHelper(IDResolver resolver) {
		if (!IDResolver.initialized) {
			IDResolver.logger
					.log(Level.INFO,
							"Not initialized. This should never happen. Throwing exception.");
			throw new RuntimeException(
					"ID Resolver did not initalize! Please go to the thread and post ID resolver.txt for help resolving this error, which should basically never happen.");
		}
		if (resolver.hasStored()) {
			resolver.drawLoadingScreen();
			int id = resolver.getStored();
			IDResolver.logger.log(Level.INFO,
					"Loading saved ID " + Integer.toString(id) + " for "
							+ resolver.getTypeName() + " " + resolver.getName()
							+ ".");
			return id;
		}
		try {
			resolver.runConflict();
			if (resolver.settingIntNewID == null) {
				IDResolver.logger
						.log(Level.INFO,
								"New setting null, assuming user cancelled, returning to default behavior.");
				return -1;
			}
			if (!resolver.specialItem) {
				IDResolver.logger.log(Level.INFO, "User selected new ID "
						+ resolver.settingIntNewID.get().toString() + " for "
						+ resolver.getName()
						+ ", returning control with new ID.");
			}
			return resolver.settingIntNewID.get();
		} catch (Exception e) {
			IDResolver.logger.log(Level.INFO,
					"Unhandled exception in ConflictHelper.", e);
			throw new RuntimeException(
					"Unhandled exception in ConflictHelper.", e);
		}
	}

	/**
	 * Converts old ID Resolver saves to Version 2. Version 2 being
	 * cross-version.
	 */
	private static void convertIDRSaveOne() {
		Properties oldIDs = (Properties) IDResolver.knownIDs.clone();
		IDResolver.knownIDs.clear();

		for (Entry<Object, Object> entry : oldIDs.entrySet()) {
			String key = (String) entry.getKey();
			String[] info = IDResolver.getInfoFromSaveString(key);
			IDResolver.knownIDs.put((IDResolver.isBlockType(key) ? "BlockID."
					: "ItemID.") + info[2] + "|" + info[1], entry.getValue());
		}
	}

	/**
	 * Generates, and shows, the Display ID status menu, which is that big list
	 * of items and blocks. Shows a renderer, metadata info, etc etc.
	 */
	@SuppressWarnings("unused")
	private static void displayIDStatus() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'DisplayIDStatus' button.");

		if (IDResolver.idToMod == null) {
			IDResolver.getLogger().log(Level.INFO,
					"ID - Mod map is null. Regenerating.");
			try {
				IDResolver.idToMod = new Hashtable<Integer, String>(
						IDResolver.loadedEntries.size());
				for (Entry<Object, Object> entry : IDResolver.knownIDs
						.entrySet()) {
					String key = (String) entry.getKey();
					if ("SAVEVERSION".equals(key)) {
						continue;
					}
					if (IDResolver.loadedEntries.contains(key)) {
						String[] info = IDResolver.getInfoFromSaveString(key);
						Integer value = Integer.parseInt((String) entry
								.getValue());
						boolean isBlock = IDResolver.isBlockType(info[0]);
						if (isBlock ? (Block.blocksList[value] != null)
								: (Item.itemsList[value] != null)) {
							IDResolver.idToMod.put(value, info[1]);
						}
					}
				}

				IDResolver.getLogger().log(Level.INFO,
						"Finished generation of ID - Mod map..");
			} catch (Throwable e) {
				IDResolver
						.getLogger()
						.log(Level.INFO,
								"Could not generate ID - Mod map. Possibly corrupted database? Skipping.",
								e);
				IDResolver.idToMod = null;
			}
		}

		ModAction mergedActions = null;

		WidgetSinglecolumn area = new WidgetSinglecolumn();

		area.childDefaultWidth = 250;
		area.childDefaultHeight = 40;
		int freeSlotStart = -1;
		String[] freeName = new String[] { "block", "block or item", "item" };
		String[] freeNames = new String[] { "blocks", "blocks or items",
				"items" };
		for (int i = 1; i < Item.itemsList.length; i++) {

			boolean addTick = false;
			Label label = null;
			StringBuilder tooltipText = null;
			ItemStack stack = new ItemStack(i, 1, 0);
			int position = IDResolver.getPosition(i);
			int exists = IDResolver.getExistance(position, i);

			if (exists == 0) {
				if (freeSlotStart == -1) {
					freeSlotStart = i;
				}
				int next = i + 1;
				if (next != Item.itemsList.length) {
					int nextPosition = IDResolver.getPosition(next);
					int nextExists = IDResolver
							.getExistance(nextPosition, next);

					boolean generateRangeItem = (nextExists != 0);

					if (!generateRangeItem && (nextPosition == position)) {
						continue;
					}
				}

				if (freeSlotStart != i) {
					label = new Label(String.format(
							"Slots %-4s - %-4s: Open slots", freeSlotStart, i));
					tooltipText = new StringBuilder(
							String.format(
									"Open Slots\r\n\r\nThis slot range of %s is open for any %s to use.",
									i - freeSlotStart, freeNames[position]));
				} else {
					label = new Label(String.format("Slot %-4s: Open slot", i));
					tooltipText = new StringBuilder(
							String.format(
									"Open Slot\r\n\r\nThis slot is open for any %s to use.",
									freeName[position]));
				}
				freeSlotStart = -1;
			} else {
				String stackName = IDResolver.getItemNameForStack(stack);
				tooltipText = new StringBuilder(String.format("Slot %-4s: %s",
						i, StatCollector.translateToLocal(stackName + ".name")));
				label = new Label(tooltipText.toString());

				tooltipText.append(String.format("\r\n\r\nInternal name: %s",
						stackName));
				addTick = Item.itemsList[i].getHasSubtypes();

				IDResolver.buildItemInfo(tooltipText, i, exists == 1);
			}

			WidgetSingleRow row = new WidgetSingleRow(200, 32);
			WidgetItem2DRender renderer = new WidgetItem2DRender(i);
			row.add(renderer, 32, 32);
			TextArea tooltip = GuiApiHelper.makeTextArea(
					tooltipText.toString(), false);
			if (addTick) {
				ModAction action = new ModAction(IDResolver.class,
						"tickIDSubItem", WidgetItem2DRender.class,
						TextArea.class, Label.class).setDefaultArguments(
						renderer, tooltip, label);
				action.setTag("SubItem Tick for "
						+ tooltipText.subSequence(0,
								tooltipText.indexOf("\r\n")));
				if (mergedActions != null) {
					mergedActions = mergedActions.mergeAction(action);
				} else {
					mergedActions = action;
				}
			}
			label.setTooltipContent(tooltip);
			row.add(label);
			area.add(row);
		}

		WidgetSimplewindow window = new WidgetSimplewindow(area,
				"ID Resolver Status Report");
		if (mergedActions != null) {
			WidgetTick ticker = new WidgetTick();
			ticker.addCallback(mergedActions, 500);
			window.mainWidget.add(ticker);
		}
		window.backButton.setText("OK");
		GuiModScreen.show(window);
	}

	/**
	 * The backing method for generating the Export ID Mapping report function.
	 * Gets a list of mods and the IDs they use, then exports them all sorted
	 * and ready for you.
	 * 
	 * @return The report.
	 */
	private static String generateIDMappingReport() {
		StringBuilder report = new StringBuilder();
		String linebreak = System.getProperty("line.separator");
		report.append("ID Resolver ID Status report").append(linebreak);
		report.append("Generated on " + new Date().toString())
				.append(linebreak);

		// mod_Name => (oldID => newID)
		TreeMap<String, TreeMap<String, Integer>> mapping = new TreeMap<String, TreeMap<String, Integer>>();

		for (Entry<Object, Object> entry : IDResolver.knownIDs.entrySet()) {
			try {
				String key = (String) entry.getKey();
				String newID = (String) entry.getValue();
				if ("SAVEVERSION".equals(key))
					continue;
				String[] info = IDResolver.getInfoFromSaveString(key);
				if (!mapping.containsKey(info[1])) {
					mapping.put(info[1], new TreeMap<String, Integer>());
				}
				mapping.get(info[1]).put(info[0], Integer.parseInt(newID));
			} catch (Throwable e) {
				// Chances are, this is never going to happen, unless there's
				// corrupt data already in knownIDs.
				IDResolver.getLogger().log(
						Level.INFO,
						"Exception while generating tables for export. Skipping entry '"
								+ entry.getKey() + "'.", e);
			}
		}

		for (Entry<String, TreeMap<String, Integer>> mod : mapping.entrySet()) {
			StringBuilder modReport = new StringBuilder(linebreak + linebreak);
			modReport.append(mod.getKey()).append(linebreak)
					.append("Type  - Original ID - New ID      -  Name")
					.append(linebreak);

			for (Entry<String, Integer> entry : mod.getValue().entrySet()) {
				String bestName = "Unknown Name / Missing Name";
				boolean type = IDResolver.isBlockType(entry.getKey());
				Item entryItem = Item.itemsList[entry.getValue()];
				if (entryItem != null) {
					String itemName = entryItem.getItemName();
					if (itemName != null && !("item.".equals(itemName))) {
						String languageName = StatCollector
								.translateToLocal(itemName + ".name");
						if (languageName == null
								|| ((languageName.startsWith("item.") || languageName
										.startsWith("tile.")) && languageName
										.endsWith(".name"))) {
							bestName = itemName;
							if (bestName.endsWith(".name"))
								bestName = bestName.substring(0,
										bestName.length() - 5);
							if (bestName.startsWith("item.")
									|| bestName.startsWith("tile."))
								bestName = bestName.substring(5);
						} else {
							bestName = languageName;
						}
					}
				}

				Integer originalID = Integer.parseInt(IDResolver.trimType(
						entry.getKey(), type));
				modReport.append(
						String.format("%-5s - %-11s - %-11s - %s",
								type ? "Block" : "Item", originalID,
								entry.getValue(), bestName)).append(linebreak);
			}
			report.append(modReport);
		}

		return report.toString();
	}

	/**
	 * the Generate ID Status report method, used by the UI.
	 * 
	 * @param showFree
	 *            The type of report.
	 * @return The report.
	 */
	private static String generateIDStatusReport(int showFree) {
		if (showFree == 3) {
			return generateIDMappingReport();
		}
		StringBuilder report = new StringBuilder();
		String linebreak = System.getProperty("line.separator");
		report.append("ID Resolver ID Status report").append(linebreak);
		report.append("Generated on " + new Date().toString())
				.append(linebreak).append(linebreak);

		boolean checkClean = Block.blocksList.length != Item.shovelSteel.shiftedIndex;
		int totalRegisteredBlocks = 1;
		int totalUncleanBlockSlots = 0;
		int totalRegisteredItems = 0;

		StringBuilder reportIDs = new StringBuilder();

		String[] names = new String[] { "Free ", "Block", "Item " };

		String[] freeName = new String[] { "block", "block or item", "item" };
		String[] freeNames = new String[] { "blocks", "blocks or items",
				"items" };

		int freeSlotStart = -1;

		for (int i = 1; i < Item.itemsList.length; i++) {
			String itemName = null;
			String transName = null;
			String className = null;

			int position = IDResolver.getPosition(i);
			int exists = IDResolver.getExistance(position, i);

			switch (exists) {
			case 0: {
				if (showFree == 0) {
					continue;
				}
				if (freeSlotStart == -1) {
					freeSlotStart = i;
				}

				int next = i + 1;
				if (next != Item.itemsList.length) {
					if (showFree == 2) {
						int nextPosition = IDResolver.getPosition(next);
						int nextExists = IDResolver.getExistance(nextPosition,
								next);

						boolean generateRangeItem = (nextExists != 0);

						if (!generateRangeItem && (nextPosition == position)) {
							continue;
						}
					}
				}

				if (freeSlotStart != i) {
					reportIDs
							.append(String
									.format("%s %-8s - %-8s - This slot range of %s is open for any %s to use.",
											names[exists], freeSlotStart, i, i
													- freeSlotStart,
											freeNames[position])).append(
									linebreak);
				} else {

					reportIDs
							.append(String
									.format("%s %-8s - This slot is open for any %s to use.",
											names[exists], i,
											freeName[position])).append(
									linebreak);
				}
				freeSlotStart = -1;
				continue;
			}
			case 1: {
				Block block = Block.blocksList[i];
				totalRegisteredBlocks++;
				itemName = block.getBlockName();
				transName = StatCollector.translateToLocal(itemName + ".name");
				if (transName.endsWith(".name"))
					transName = transName.substring(0, transName.length() - 5);
				if (transName.startsWith("item.")
						|| transName.startsWith("tile."))
					transName = transName.substring(5);
				className = block.getClass().getName();
				break;
			}
			case 2: {
				if (checkClean && (i < Block.blocksList.length)) {
					totalUncleanBlockSlots++;
				}

				Item item = Item.itemsList[i];
				totalRegisteredItems++;
				itemName = item.getItemName();
				transName = StatCollector.translateToLocal(itemName + ".name");
				if (transName.endsWith(".name"))
					transName = transName.substring(0, transName.length() - 5);
				if (transName.startsWith("item.")
						|| transName.startsWith("tile."))
					transName = transName.substring(5);
				className = item.getClass().getName();
				break;
			}
			}

			reportIDs.append(
					String.format("%s %-8s - %-31s - %-31s - %s",
							names[exists], i, itemName, transName, className))
					.append(linebreak);
		}
		report.append("Quick stats:").append(linebreak);
		report.append(String.format(
				"Block ID Status: %d/%d used. %d available.",
				totalRegisteredBlocks, Block.blocksList.length,
				(Block.blocksList.length - totalUncleanBlockSlots)
						- totalRegisteredBlocks));
		if (checkClean) {
			report.append("(Unclean Block slots: ");
			report.append(totalUncleanBlockSlots);
			report.append(")" + linebreak);
		} else {
			report.append(linebreak);
		}
		report.append(
				String.format("Item ID Status: %d/%d used. %d available.",
						totalRegisteredItems, Item.itemsList.length,
						(Item.itemsList.length - Item.shovelSteel.shiftedIndex)
								- totalRegisteredItems)).append(linebreak)
				.append(linebreak);
		report.append(
				"Type  ID      - Name                           - Tooltip                        - Class")
				.append(linebreak);
		report.append(reportIDs.toString());
		return report.toString();
	}

	/**
	 * Internally used: takes the requested ID, and the Block, then tries to
	 * find the new ID either by getting a saved mapping, by asking the user,
	 * etc etc.
	 * 
	 * @param RequestedID
	 *            The original requested ID: This is used for the signatures.
	 * @param newBlock
	 *            the actual Block instance.
	 * @return The new ID
	 */
	public static int getConflictedBlockID(int RequestedID, Block newBlock) {
		IDResolver.getLogger()
				.log(Level.INFO, "'GetConflictedBlockID' called.");

		if (!IDResolver.initialized) {
			IDResolver.logger.log(Level.INFO,
					"Not initialized. Returning to default behaviour.");
			return RequestedID;
		}

		if (newBlock == null) {

			IDResolver.logger
					.log(Level.INFO,
							"Conflict requested for null Block: Returning requested ID as there is likely another crash. Logging the stacktrace to display what mod is causing issues.");
			try {
				throw new Exception("Generating Stacktrace");
			} catch (Throwable e) {
				IDResolver.logger.log(Level.INFO, e.toString());
			}

			return RequestedID;
		}

		if (!IDResolver.isModObject(RequestedID, true)) {
			IDResolver.getLogger().log(Level.INFO,
					"Detected Vanilla Block: Returning requested ID.");
			return RequestedID;
		}

		if (IDResolver.vanillaIDs[RequestedID]) {
			IDResolver
					.getLogger()
					.log(Level.INFO,
							"Mod is attempting to overwrite what seems to be a vanilla ID: Allowing the overwrite.");
			return RequestedID;
		}

		IDResolver resolver = new IDResolver(RequestedID, newBlock);
		IDResolver.getLogger().log(Level.INFO,
				"Long name of requested block is " + resolver.longName);
		resolver.setupGui(RequestedID);
		return IDResolver.conflictHelper(resolver);
	}

	/**
	 * Internally used: takes the requested ID, and the Item, then tries to find
	 * the new ID either by getting a saved mapping, by asking the user, etc
	 * etc.
	 * 
	 * @param RequestedID
	 *            The original requested ID: This is used for the signatures.
	 * @param newItem
	 *            the actual Item instance.
	 * @return The new ID
	 */
	public static int getConflictedItemID(int RequestedID, Item newItem) {
		IDResolver.getLogger().log(Level.INFO, "'GetConflictedItemID' called.");

		if (!IDResolver.initialized) {
			IDResolver.logger.log(Level.INFO,
					"Not initialized. Returning to default behaviour.");
			return RequestedID;
		}

		if (newItem == null) {

			IDResolver.logger
					.log(Level.INFO,
							"Conflict requested for null Item: Returning requested ID as there is likely another crash somewhere else. Logging the stacktrace to display what mod is causing issues just in case.");
			try {
				throw new Exception("Generating Stacktrace");
			} catch (Throwable e) {
				IDResolver.logger.log(Level.INFO, e.toString());
			}

			return RequestedID;
		}

		if (!IDResolver.isModObject(RequestedID, true)) {
			IDResolver.getLogger().log(Level.INFO,
					"Detected Vanilla Item: Returning requested ID.");
			return RequestedID;
		}
		if (IDResolver.vanillaIDs[RequestedID]) {
			IDResolver
					.getLogger()
					.log(Level.INFO,
							"Mod is attempting to overwrite what seems to be a vanilla ID: Allowing the overwrite.");
			return RequestedID;
		}
		IDResolver resolver = new IDResolver(RequestedID, newItem);
		IDResolver.getLogger().log(Level.INFO,
				"Long name of requested item is " + resolver.longName);
		resolver.setupGui(RequestedID);
		return IDResolver.conflictHelper(resolver);
	}

	/**
	 * Used for the ID reports saying what is in this slot.
	 * 
	 * @param position
	 *            The type of slot: If it can be a Block only, Block or Item,
	 *            etc.
	 * @param i
	 *            The ID.
	 * @return 0 if null, 1 if a block, 2 if an item.
	 */
	private static int getExistance(int position, int i) {
		ItemStack stack = new ItemStack(i, 1, 0);
		int exists = 0;
		switch (position) {
		case 0: {
			if ((Block.blocksList[i] != null) && (stack.getItem() != null)) {
				exists = 1;
			}
			break;
		}
		case 1: {
			boolean isBlock = false;
			if (Block.blocksList[i] != null) {
				isBlock = Block.blocksList[i].blockID != 0;
			}
			if (isBlock && (stack.getItem() != null)) {
				exists = 1;
			} else {
				if (Item.itemsList[i] != null) {
					exists = 2;
				}
			}
			break;
		}
		case 2: {
			if (Item.itemsList[i] != null) {
				exists = 2;
			}
			break;
		}
		}
		return exists;
	}

	/**
	 * @return The 'extra' info. Basically just stuff that ID Resolver wants to
	 *         print to the dialog after load for errors and such.
	 */
	public static String getExtraInfo() {
		return IDResolver.extraInfo;
	}

	/**
	 * @return The first open BlockID. (This checks for saved signatures as
	 *         well!)
	 */
	private int getFirstOpenBlock() {
		int start = 1;
		if(firstOpenBlockCache != 0)
		{
			start = firstOpenBlockCache;
		}
		int last = Block.blocksList.length;
		if(this.hasCustomMaxID)
		{
			last = this.maxID;
		}
		for (int i = start; i < last; i++) {
			if (!IDResolver.isSlotFree(i)) {
				continue;
			}
			firstOpenBlockCache = i;
			return i;
		}
		return -1;
	}

	/**
	 * @return The first open ItemID. (This checks for saved signatures as
	 *         well!)
	 */
	private int getFirstOpenItem() {
		int start = Item.shovelSteel.shiftedIndex;
		if(firstOpenItemCache != 0)
		{
			start = firstOpenItemCache;
		}
		int last = Item.itemsList.length;
		if(this.hasCustomMaxID)
		{
			last = this.maxID;
		}
		for (int i = start; i < last; i++) {
			if (!IDResolver.isSlotFree(i)) {
				continue;
			}
			firstOpenItemCache = i;
			return i;
		}
		return -1;
	}

	/**
	 * Just a helper method.
	 * 
	 * @param input
	 *            the key to split up
	 * @return an array: ID | BaseMod. USED TO BE Class|BaseMod|ID. BaseMod will have it's package stripped!
	 */
	private static String[] getInfoFromSaveString(String input) {
		return getInfoFromSaveString(input,true);
	}
	
	/**
	 * Just a helper method.
	 * 
	 * @param input
	 *            the key to split up
	 * @param stripPackage Whether to strip out the package information for the BaseMod or not.
	 * @return an array: ID | BaseMod. USED TO BE Class|BaseMod|ID.
	 */
	private static String[] getInfoFromSaveString(String input,boolean stripPackage) {
		String[] result = input.split("[|]");
		if(stripPackage)
		{
			result[1] = trimPackage(result[1]);
		}
		return result;
	}

	/**
	 * Helper method for finding Item names. Has a bit of checking basically.
	 * 
	 * @param stack
	 *            The ItemStack.
	 * @return The name, or an empty string if there's an issue getting one.
	 */
	private static String getItemNameForStack(ItemStack stack) {
		if (stack.getItem() == null) {
			return "";
		}
		try {
			return stack.getItem().getItemNameIS(stack);
		} catch (Throwable e) {
			return "";
		}
	}

	/**
	 * @return the LAST open BlockID. (This checks for saved signatures as
	 *         well!)
	 */
	private int getLastOpenBlock() {
		int start = Block.blocksList.length - 1;
		if(this.hasCustomMaxID)
		{
			start = this.maxID;
		}
		else
		if(lastOpenBlockCache != 0)
		{
			start = lastOpenBlockCache;
		}
		for (int i = start; i >= 1; i--) {
			if (!IDResolver.isSlotFree(i)) {
				continue;
			}
			if(!this.hasCustomMaxID)
				lastOpenBlockCache = i;
			return i;
		}
		return -1;
	}

	/**
	 * @return the LAST open ItemID. (This checks for saved signatures as well!)
	 */
	private int getLastOpenItem() {
		int start = Item.itemsList.length - 1;
		if(this.hasCustomMaxID)
		{
			start = this.maxID;
		}
		else
		if(lastOpenItemCache != 0)
		{
			start = lastOpenItemCache;
		}
		for (int i = start; i >= Item.shovelSteel.shiftedIndex; i--) {
			if (!IDResolver.isSlotFree(i)) {
				continue;
			}
			if(!this.hasCustomMaxID)
				lastOpenItemCache = i;
			return i;
		}
		return -1;
	}

	/**
	 * @return the logger
	 */
	public static Logger getLogger() {
		return IDResolver.logger;
	}

	/**
	 * gets the 'long' block name, or, more correctly, the block signature. This
	 * is done by walking a stacktrace, either the stored one from earlier or a
	 * new one it generates.
	 * 
	 * @param block
	 *            the Block itself.
	 * @param originalrequestedID
	 *            The requested ID.
	 * @return The signature.
	 */
	private static String getLongBlockName(Block block, int originalrequestedID) {

		String name = Integer.toString(originalrequestedID) + "|";
		StackTraceElement[] stacktrace = null;

		if (lastStackTraceID == originalrequestedID) {
			stacktrace = lastStackTrace;
		} else {
			IDResolver
					.getLogger()
					.log(Level.WARNING,
							"Cached StackTrace is for a different block! Generating new StackTrace...");
			stacktrace = Thread.currentThread().getStackTrace();
		}
		lastStackTrace = null;
		lastStackTraceID = -1;
		int bestguess = -1;
		for (int i = 1; i < stacktrace.length; i++) {
			Class<?> exceptionclass = null;
			try {
				exceptionclass = Class.forName(stacktrace[i].getClassName());
			} catch (Throwable e) {
				continue;
			}
			if (Block.class.isAssignableFrom(exceptionclass)) {
				continue;
			}
			if (IDResolver.class.isAssignableFrom(exceptionclass)) {
				continue;
			}
			if (bestguess == -1) {
				bestguess = i;
			}
			if (BaseMod.class.isAssignableFrom(exceptionclass)) {
				bestguess = i;
				break;
			}
			if (exceptionclass.isAnnotationPresent(Mod.class)) {
				bestguess = i;
				break;
			}

		}
		if (bestguess == -1) {
			name += "IDRESOLVER_UNKNOWN_OBJECT_" + block.getClass().getName();
		} else {
			name += stacktrace[bestguess]
						.getClassName();
		}
		return name;
	}

	/**
	 * gets the 'long' item name, or, more correctly, the item signature. This
	 * is done by walking a stacktrace, either the stored one from earlier or a
	 * new one it generates.
	 * 
	 * @param item
	 *            the Item itself.
	 * @param originalrequestedID
	 *            The requested ID.
	 * @return The signature.
	 */
	private static String getLongItemName(Item item, int originalrequestedID) {
		String name = Integer.toString(originalrequestedID) + "|";
		StackTraceElement[] stacktrace = null;

		if (lastStackTraceID == originalrequestedID) {
			stacktrace = lastStackTrace;
		} else {
			IDResolver
					.getLogger()
					.log(Level.WARNING,
							"Cached StackTrace is for a different item! Generating new StackTrace...");
			stacktrace = Thread.currentThread().getStackTrace();
		}
		lastStackTrace = null;
		lastStackTraceID = -1;
		int bestguess = -1;
		for (int i = 1; i < stacktrace.length; i++) {
			Class<?> exceptionclass = null;
			try {
				exceptionclass = Class.forName(stacktrace[i].getClassName());
			} catch (Throwable e) {
				continue;
			}
			if (Item.class.isAssignableFrom(exceptionclass)) {
				continue;
			}
			if (IDResolver.class.isAssignableFrom(exceptionclass)) {
				continue;
			}
			if (bestguess == -1) {
				bestguess = i;
			}
			if (BaseMod.class.isAssignableFrom(exceptionclass)) {
				bestguess = i;
				break;
			}
			if (exceptionclass.isAnnotationPresent(Mod.class)) {
				bestguess = i;
				break;
			}

		}
		if (bestguess == -1) {
			name += "IDRESOLVER_UNKNOWN_OBJECT_" + item.getClass().getName();
		} else {
				name += stacktrace[bestguess]
						.getClassName();
		}
		return name;
	}

	/**
	 * Helper that calls either getLongBlockName or getLongItemName depending on
	 * which is needed. Or, if it's neither, throws an exception and stuff. This
	 * also sets if an entry was loaded.
	 * 
	 * @param obj
	 *            the Object (Block or Item only)
	 * @param ID
	 *            the requested ID.
	 * @return The name.
	 */
	private static String getlongName(Object obj, int ID) {
		String name = null;
		if (obj instanceof Block) {
			name = "BlockID." + IDResolver.getLongBlockName((Block) obj, ID);
		}
		if (obj instanceof Item) {
			name = "ItemID." + IDResolver.getLongItemName((Item) obj, ID);
		}
		IDResolver.loadedEntries.add(name);
		if (name != null) {
			return name;
		}
		if (obj == null) {
			throw new RuntimeException(
					"You should never see this. For some reason, ID resolver attempted to get an item name for null.");
		}
		throw new RuntimeException(
				"You should never see this. For some reason, ID resolver attempted to get an item name a non-item / block. It is of type '"
						+ obj.getClass().getName()
						+ "'. The toString is: "
						+ obj.toString());
	}

	/**
	 * Helper for getting a mod's priority level. If there isn't a saved one it
	 * sets 0.
	 * 
	 * @param modname
	 *            the name of the mod.
	 * @return the priority.
	 */
	private static int getModPriority(String modname) {
		if (IDResolver.modPriorities.containsKey(modname)) {
			try {
				int value = Integer.parseInt(IDResolver.modPriorities
						.getProperty(modname));
				if (value >= 0) {
					return value;
				}
			} catch (Throwable e) {
				// this should never ever happen, and if it does someone edited
				// the settings.
			}
		}
		IDResolver.modPriorities.setProperty(modname, "0");
		isPrioritiesChanged = true;
		return 0;
	}

	/**
	 * @param i
	 *            the slot to check
	 * @return What kind of ID can be here: 0 if Block only, 1 if Block or Item,
	 *         and 2 if Item only.
	 */
	private static int getPosition(int i) {
		int position = 0;
		if (i >= Block.blocksList.length) {
			position = 2;
		} else {
			if (i >= Item.shovelSteel.shiftedIndex) {
				position = 1;
			}
		}
		return position;
	}

	/**
	 * @param ID
	 *            the ID to look for.
	 * @param block
	 *            If it's a block or not.
	 * @return the stored ID signature for this ID (with type trimmed)
	 */
	private static String getStoredIDName(int ID, boolean block) {
		return IDResolver.getStoredIDName(ID, block, true);
	}

	/**
	 * @param ID
	 *            the ID to look for.
	 * @param block
	 *            If it's a block or not.
	 * @param trim
	 *            Whether to trim the type or not.
	 * @return the stored ID signature for this ID
	 */
	private static String getStoredIDName(int ID, boolean block, boolean trim) {
		for (Map.Entry<Object, Object> entry : IDResolver.knownIDs.entrySet()) {
			String key = ((String) entry.getKey());
			if ("SAVEVERSION".equals(key)) {
				continue;
			}

			if (ID == Integer.parseInt((String) entry.getValue())) {
				return trim ? IDResolver.trimType(key, block) : key;
			}
		}
		return null;
	}

	/**
	 * Just a helper. Returns "Block" if block is true, Item otherwise.
	 */
	private static String getTypeName(Boolean block) {
		return (block ? "Block" : "Item");
	}

	/**
	 * @param ID
	 *            The ID.
	 * @param block
	 *            Whether it's a block or not.
	 * @return Whether ID Resolver should do anything with this or not:
	 *         Basically just returns true unless it's a vanilla Item.
	 */
	public static boolean shouldDoAssignment(int ID, boolean block) {
		if (block && !IDResolver.wasBlockInited) {
			IDResolver.wasBlockInited = true;
		} else if (!block && !IDResolver.wasItemInited) {
			IDResolver.wasItemInited = true;
		}
		return IDResolver.isModObject(ID, block);
	}

	/**
	 * Button helper.
	 */
	@SuppressWarnings("unused")
	private static void ignoreLooseDetectionAndDisable() {
		IDResolver.checkForLooseSettings.set(false);
		IDResolver.updateTickSettings();
	}

	/**
	 * Helper to see if there's a stored ID for this ID.
	 * 
	 * @param ID
	 *            the ID.
	 * @param block
	 *            If it's a block or Item
	 * @return true if there's a stored ID.
	 */
	private static boolean hasStoredID(int ID, boolean block) {
		if (IDResolver.initialized) {
			if (IDResolver.knownIDs.containsValue(Integer.toString(ID))) {
				return IDResolver.getStoredIDName(ID, block) != null;
			}
		}
		return false;
	}

	/**
	 * Helper for finding the type from a signature.
	 * 
	 * @param input
	 *            the input to check.
	 * @return True if it begins with BlockID., false if it begins with ItemID.,
	 *         and throws an exception if it's neither.
	 */
	private static boolean isBlockType(String input) {
		if (input.startsWith("BlockID.")) {
			return true;
		}
		if (input.startsWith("ItemID.")) {
			return false;
		}
		throw new InvalidParameterException("Input is not fully named!");
	}

	/**
	 * Internal method to scanning the stack to see if an Item or Block is a
	 * vanilla addition or not. This also sets the vanilla array.
	 * 
	 * @param id
	 *            the Original ID. Used for cache sanity checks later.
	 * @param isBlock
	 *            Whether to check for a Block or an Item.
	 * @return True if it's a mod object, false if not.
	 */
	private static boolean isModObject(int id, boolean isBlock) {
		lastStackTraceID = id;
		lastStackTrace = Thread.currentThread().getStackTrace();
		boolean possibleVanilla = false;
		for (int i = 1; i < lastStackTrace.length; i++) {
			try {
				Class classType = Class.forName(lastStackTrace[i]
						.getClassName());
				if (BaseMod.class.isAssignableFrom(classType)) {
					return true;
				}
				if (classType.isAnnotationPresent(Mod.class)) {
					return true;
				}
				if ("<clinit>".equals(lastStackTrace[i].getMethodName())
						&& (isBlock ? Block.class == classType
								: Item.class == classType)) {
					possibleVanilla = true;
				}
			} catch (Throwable e) {
				// Should never happen, but in this case just going to coast
				// right over it.
			}
		}
		if (possibleVanilla) {
			IDResolver.vanillaIDs[id] = true;
		}
		return false;
	}

	/**
	 * @param i
	 *            the slot ID to check.
	 * @return true if the slot is free. This checks saved signatures.
	 */
	private static boolean isSlotFree(int i) {
		
		if (i < Block.blocksList.length) {
			if ((Block.blocksList[i] != null)
					|| IDResolver.hasStoredID(i, true)) {
				return false;
			}
		}
		return !((Item.itemsList[i] != null) ||
				IDResolver.hasStoredID(i, false));
	}

	/**
	 * Basically a helper method for links in TextAreas.
	 * 
	 * @param url
	 *            the URL to call.
	 */
	@SuppressWarnings("unused")
	private static void linkCallback(String url) {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed link. URL is: " + url);
		File file = new File(url);
		try {
			Desktop.getDesktop().open(file);
		} catch (Throwable e) {
			e.printStackTrace();
			Sys.openURL(file.toURI().toString());
		}
	}

	/**
	 * Button helper. try and lower priority, and if it changed update the
	 * TextArea and save it.
	 * 
	 * @param modName
	 * @param textarea
	 */
	@SuppressWarnings("unused")
	private static void lowerModPriorityFromMenu(String modName,
			TextArea textarea) {
		IDResolver.getLogger().log(
				Level.INFO,
				"User pressed 'LowerModPriorityFromMenu' button with "
						+ modName);
		int intlevel = IDResolver.getModPriority(modName);
		if (intlevel > 0) {
			intlevel--;
		}
		String newlevel = Integer.toString(intlevel);
		IDResolver.modPriorities.setProperty(modName, newlevel);
		isPrioritiesChanged = true;
		GuiApiHelper.setTextAreaText(textarea, modName
				+ " - Currently at Priority Level " + newlevel);
		try {
			IDResolver.storeProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO, "Could not save properties", e);
		}
	}

	/**
	 * ID Changer helper for Blocks. Take the block in currentID, changes the internal ID setting, and moves to to newID. Swaps all the 'metadata', but does not move the block in newID, it WILL just override it.
	 * @param currentID the current / source ID.
	 * @param newID the new ID.
	 */
	private static void overrideBlockID(int currentID, int newID) {
		Block oldblock = Block.blocksList[currentID];
		Block.blocksList[currentID] = null;
		if (oldblock != null) {
			try {
				IDResolver.blockIdField.set(oldblock, newID);
			} catch (Throwable e) {
				IDResolver.logger.log(Level.INFO,
						"Unable to override blockID!", e);
				throw new IllegalArgumentException(
						"Unable to override blockID!", e);
			}
		}

		Boolean tempBoolean = Block.opaqueCubeLookup[currentID];
		Block.opaqueCubeLookup[currentID] = Block.opaqueCubeLookup[newID];
		Block.opaqueCubeLookup[newID] = tempBoolean;

		tempBoolean = Block.canBlockGrass[currentID];
		Block.canBlockGrass[currentID] = Block.canBlockGrass[newID];
		Block.canBlockGrass[newID] = tempBoolean;

		tempBoolean = Block.requiresSelfNotify[currentID];
		Block.requiresSelfNotify[currentID] = Block.requiresSelfNotify[newID];
		Block.requiresSelfNotify[newID] = tempBoolean;

		tempBoolean = Block.useNeighborBrightness[currentID];
		Block.useNeighborBrightness[currentID] = Block.useNeighborBrightness[newID];
		Block.useNeighborBrightness[newID] = tempBoolean;

		int tempInt = Block.lightValue[currentID];
		Block.lightValue[currentID] = Block.lightValue[newID];
		Block.lightValue[newID] = tempInt;

		tempInt = Block.lightOpacity[currentID];
		Block.lightOpacity[currentID] = Block.lightOpacity[newID];
		Block.lightOpacity[newID] = tempInt;

		Item oldblockitem = Item.itemsList[currentID];
		Item.itemsList[currentID] = null;
		if (oldblockitem != null) {
			try {
				IDResolver.itemIdField.set(oldblockitem, newID);
			} catch (Throwable e) {
				IDResolver.logger.log(Level.INFO,
						"Unable to override itemID for the block's item!", e);
				throw new IllegalArgumentException(
						"Unable to override itemID for the block's item!", e);
			}
		}
		Block.blocksList[newID] = oldblock;
		Item.itemsList[newID] = oldblockitem;
		IDResolver.idToMod = null;
	}

	/**
	 * ID Changer helper for Item. Take the Item in currentID, changes the internal ID setting, and moves to to newID. Does not move the Item in newID, it WILL just override it.
	 * @param currentID the current / source ID.
	 * @param newID the new ID.
	 */
	private static void overrideItemID(int currentID, int newID) {
		Item olditem = Item.itemsList[currentID];
		Item.itemsList[currentID] = null;
		if (olditem != null) {
			try {
				IDResolver.itemIdField.set(olditem, newID);
			} catch (Throwable e) {
				IDResolver.logger.log(Level.INFO, "Unable to override itemID!",
						e);
				throw new IllegalArgumentException(
						"Unable to override itemID!", e);
			}
		}
		Item.itemsList[newID] = olditem;
		IDResolver.idToMod = null;
	}

	private static String raiseModPriority(String modname) {
		String newlevel = Integer
				.toString(IDResolver.getModPriority(modname) + 1);
		IDResolver.modPriorities.setProperty(modname, newlevel);
		isPrioritiesChanged = true;
		return newlevel;
	}

	@SuppressWarnings("unused")
	private static void raiseModPriorityFromMenu(String modName,
			TextArea textarea) {
		IDResolver.getLogger().log(
				Level.INFO,
				"User pressed 'RaiseModPriorityFromMenu' button with "
						+ modName);
		GuiApiHelper.setTextAreaText(
				textarea,
				modName + " - Currently at Priority Level "
						+ IDResolver.raiseModPriority(modName));
		try {
			IDResolver.storeProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO, "Could not save properties", e);
		}
	}

	private static void reloadIDs() {
		try {
			if (streamingOutputStream != null) {
				streamingOutputStream.close();
				streamingOutputStream = null;
			}
		} catch (Throwable e) {
			IDResolver.logger
					.log(Level.INFO,
							"Exception when attempted to close 'streaming' filestream.",
							e);
		}

		IDResolver.knownIDs = new Properties();
		IDResolver.modPriorities = new Properties();
		boolean forceSave = false;
		try {
			IDResolver.idPath = new File(Minecraft.getMinecraftDir()
					.getAbsolutePath()
					+ "/config/IDResolverknownIDs.properties");

			IDResolver.idPath.getParentFile().mkdirs();

			IDResolver.priorityPath = new File(Minecraft.getMinecraftDir()
					.getAbsolutePath()
					+ "/config/IDResolvermodPriorities.properties");
			IDResolver.priorityPath.getParentFile().mkdirs();

			if (IDResolver.idPath.createNewFile()) {
				IDResolver.logger.log(Level.INFO,
						"IDs File not found, creating new one.");
				InputStream stream = IDResolver.class.getClassLoader()
						.getResourceAsStream("IDResolverDefaultIDs.properties");
				if (stream != null) {
					IDResolver.knownIDs.load(stream);
					IDResolver.logger.log(
							Level.INFO,
							"Found defaults file, loaded "
									+ Integer.toString(IDResolver.knownIDs
											.size()) + " IDs sucessfully.");
					stream.close();

					String version = IDResolver.knownIDs
							.getProperty("SAVEVERSION");

					if (version == null) {
						IDResolver.logger
								.log(Level.INFO,
										"Settings file is v1, but ID resolver now uses v2. Converting.");
						IDResolver.convertIDRSaveOne();
						IDResolver.logger.log(Level.INFO,
								"Settings file convertion complete.");
						forceSave = true;
					}
					cleanBadKeys();

				}
			} else {
				try {
					FileInputStream stream = new FileInputStream(
							IDResolver.idPath);
					IDResolver.knownIDs.load(stream);
					stream.close();
					IDResolver.logger.log(
							Level.INFO,
							"Loaded "
									+ Integer.toString(IDResolver.knownIDs
											.size()) + " IDs sucessfully.");

					String version = IDResolver.knownIDs
							.getProperty("SAVEVERSION");

					if (version == null) {
						IDResolver.logger
								.log(Level.INFO,
										"Settings file is v1, but ID resolver now uses v2. Converting.");
						IDResolver.convertIDRSaveOne();
						IDResolver.logger.log(Level.INFO,
								"Settings file convertion complete.");
						forceSave = true;
					}
					cleanBadKeys();

				} catch (IOException e) {
					IDResolver.logger
							.log(Level.INFO,
									"Existing config details are invalid: Creating new settings.");
				}
			}
			if (IDResolver.priorityPath.createNewFile()) {
				isFirstStart = true;
				IDResolver.logger.log(Level.INFO,
						"Priorities File not found, creating new one.");
				InputStream stream = IDResolver.class.getClassLoader()
						.getResourceAsStream(
								"IDResolverDefaultmodPriorities.properties");
				if (stream != null) {
					IDResolver.modPriorities.load(stream);
					IDResolver.logger.log(
							Level.INFO,
							"Found defaults file, loaded "
									+ Integer.toString(IDResolver.modPriorities
											.size())
									+ " Mod Priorities sucessfully.");
					stream.close();
				}
			} else {
				try {
					FileInputStream stream = new FileInputStream(
							IDResolver.priorityPath);
					IDResolver.modPriorities.load(stream);
					stream.close();
					int negatives = 0;
					if (IDResolver.modPriorities.containsKey("ShowTickMM")) {
						negatives++;
					}
					if (IDResolver.modPriorities.containsKey("ShowTickRS")) {
						negatives++;
					}
					IDResolver.logger.log(
							Level.INFO,
							"Loaded "
									+ Integer.toString(IDResolver.modPriorities
											.size() - negatives)
									+ " Mod Priorities sucessfully.");
				} catch (IOException e) {
					IDResolver.logger
							.log(Level.INFO,
									"Existing config details are invalid: Creating new settings.");
				}
			}
			if ((IDResolver.showTickMM == null)
					| (IDResolver.showTickRS == null)
					| (IDResolver.showOnlyConf == null)
					| (IDResolver.checkForLooseSettings == null)) {
				IDResolver.showTickMM = new SettingBoolean("ShowTickMM",
						IDResolver.showTick(true));
				IDResolver.showTickRS = new SettingBoolean("ShowTickRS",
						IDResolver.showTick(false));
				IDResolver.checkForLooseSettings = new SettingBoolean(
						"CheckForLooseSettings",
						IDResolver.checkForLooseSettings());
				IDResolver.showOnlyConf = new SettingBoolean(
						"ShowOnlyConflicts", IDResolver.showOnlyConflicts());
			}
			IDResolver.initialized = true;
			IDResolver.updateTickSettings();

			if (forceSave) {
				IDResolver.logger.log(Level.INFO,
						"Saving as changes were made.");
				IDResolver.storeProperties();
			}

		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO,
					"Error while initalizing settings.", e);
			IDResolver.initialized = false;
		}
	}

	private static void cleanBadKeys() {
		ArrayList<String> badKeys = new ArrayList<String>();
		for (Map.Entry<Object, Object> entry : IDResolver.knownIDs.entrySet()) {
			String key = (String) entry.getKey();

			if ("SAVEVERSION".equals(key)) {
				continue;
			}

			try {
				Boolean isBlock = IDResolver.isBlockType(key);
				String[] info = IDResolver.getInfoFromSaveString(IDResolver
						.trimType(key, isBlock));
				if (info.length != 2) {
					badKeys.add(key);
					continue;
				}
				Integer.parseInt(info[0]);
				if (info[1].isEmpty()) {
					badKeys.add(key);
					continue;
				}
				String value = (String) entry.getValue();
				Integer.parseInt(value);
			} catch (Throwable e) {
				badKeys.add(key);
			}
		}
		if (!badKeys.isEmpty()) {
			IDResolver.logger.log(Level.INFO,
					"Located invalid entries: Trimming");
			for (Iterator iterator = badKeys.iterator(); iterator.hasNext();) {
				String string = (String) iterator.next();
				IDResolver.knownIDs.remove(string);
			}
		}
	}

	public static void reLoadModGui() {
		isStreamingSaves = false;
		IDResolver.reloadIDs();

		IDResolver.modScreen.widgetColumn.removeAllChildren();
		Map<String, Vector<String>> IDmap = new TreeMap<String, Vector<String>>();
		Button temp = null;
		for (Map.Entry<Object, Object> entry : IDResolver.knownIDs.entrySet()) {
			String key = (String) entry.getKey();

			if ("SAVEVERSION".equals(key)) {
				continue;
			}
			String[] info = IDResolver.getInfoFromSaveString(key);
			if (IDmap.containsKey(info[1])) {
				IDmap.get(info[1]).add(key);
			} else {
				Vector<String> list = new Vector<String>();
				list.add(key);
				IDmap.put(info[1], list);
			}
		}
		IDResolver.windows = new WidgetSimplewindow[IDmap.size()];
		int id = 0;

		for (Entry<String, Vector<String>> entry : IDmap.entrySet()) {
			Collections.sort(entry.getValue());
			WidgetSinglecolumn window = new WidgetSinglecolumn();
			window.childDefaultWidth = 300;
			TextArea textWidget = GuiApiHelper.makeTextArea(
					entry.getKey() + " - Currently at Priority Level "
							+ IDResolver.getModPriority(entry.getKey()), false);
			window.add(textWidget);
			window.heightOverrideExceptions.put(textWidget, 0);

			temp = GuiApiHelper.makeButton("Raise Priority of this Mod",
					"raiseModPriorityFromMenu", IDResolver.class, true,
					new Class[] { String.class, TextArea.class },
					entry.getKey(), textWidget);
			temp.setTooltipContent(GuiApiHelper.makeTextArea(
					"This will Raise the Priority level of this mod.", false));
			window.add(temp);

			temp = GuiApiHelper.makeButton("Lower Priority of this Mod",
					"lowerModPriorityFromMenu", IDResolver.class, true,
					new Class[] { String.class, TextArea.class },
					entry.getKey(), textWidget);
			temp.setTooltipContent(GuiApiHelper.makeTextArea(
					"This will Lower the Priority level of this mod.", false));
			window.add(temp);

			temp = GuiApiHelper.makeButton("Wipe saved IDs of this mod",
					"wipeSavedIDsFromMenu", IDResolver.class, true,
					new Class[] { String.class }, entry.getKey());
			temp.setTooltipContent(GuiApiHelper.makeTextArea(
					"This will wipe all Saved ID information for this mod.",
					false));
			window.add(temp);

			for (String IDEntry : entry.getValue()) {
				int x = Integer.parseInt(IDResolver.knownIDs
						.getProperty(IDEntry));
				String name = null;
				ItemStack stack = null;
				Boolean isBlock = IDResolver.isBlockType(IDEntry);
				if (isBlock) {
					if (Block.blocksList[x] != null) {
						stack = new ItemStack(Block.blocksList[x]);
					}
				} else {
					if (Item.itemsList[x] != null) {
						stack = new ItemStack(Item.itemsList[x]);
					}
				}
				try {
					name = IDResolver.getItemNameForStack(stack);
				} catch (Throwable e) {
					stack = null;
					name = null;
				}
				if (stack != null) {
					if ((name != null) && (name.length() != 0)) {
						name = StatCollector.translateToLocal(name);
					}
					if ((name == null) || (name.length() == 0)) {
						String originalpos = IDResolver
								.getInfoFromSaveString(IDEntry)[0];
						if (isBlock) {
							name = "Unnamed "
									+ IDResolver
											.trimPackage(Block.blocksList[x]
													.getClass().getName())
									+ " originally at " + originalpos;
						} else {
							name = "Unnamed "
									+ IDResolver.trimPackage(Item.itemsList[x]
											.getClass().getName())
									+ " originally at " + originalpos;
						}
					}
				} else {
					String[] info = IDResolver.getInfoFromSaveString(IDEntry);
					name = "Loose setting for "
							+ (isBlock ? "Block '" : "Item with original ID ")
							+ info[0] + " loaded from " + info[1];
				}
				temp = GuiApiHelper.makeButton("Edit ID for " + name,
						"resolveNewID", IDResolver.class, true,
						new Class[] { String.class }, IDEntry);
				temp.setTooltipContent(GuiApiHelper.makeTextArea(
						"This will open a menu to edit the saved ID for "
								+ name
								+ " much like the initial resolve screen.",
						false));
				window.add(temp);
			}
			IDResolver.windows[id] = new WidgetSimplewindow(window,
					"Config IDs for " + entry.getKey());
			temp = GuiApiHelper.makeButton("View IDs for " + entry.getKey(),
					"showMenu", IDResolver.class, true,
					new Class[] { Integer.class }, id);
			temp.setTooltipContent(GuiApiHelper
					.makeTextArea(
							"This will open a menu to view and modify the IDs and priority information for "
									+ entry.getKey(), false));
			IDResolver.modScreen.widgetColumn.add(temp);
			id++;
		}

		IDResolver.updateTickSettings();
		Runnable callback = new ModAction(IDResolver.class,
				"updateTickSettings");
		WidgetBoolean TickMWidget = new WidgetBoolean(IDResolver.showTickMM,
				"Show ID info on Main Menu");
		TickMWidget.button
				.setTooltipContent(GuiApiHelper
						.makeTextArea(
								"Whether or not to show ID Status information on the main menu. This includes available Block IDs, available Item IDs, Free Sprite Indexes, and Free Terrain Indexes.",
								false));
		TickMWidget.button.addCallback(callback);
		IDResolver.modScreen.widgetColumn.add(TickMWidget);
		TickMWidget = new WidgetBoolean(IDResolver.showTickRS,
				"Show ID info on Resolve Screen");
		TickMWidget.button
				.setTooltipContent(GuiApiHelper
						.makeTextArea(
								"Whether or not to show ID Status information on the resolve screen. This includes available Block IDs, available Item IDs, Free Sprite Indexes, and Free Terrain Indexes.",
								false));
		TickMWidget.button.addCallback(callback);
		IDResolver.modScreen.widgetColumn.add(TickMWidget);
		TickMWidget = new WidgetBoolean(IDResolver.checkForLooseSettings,
				"Check for Loose settings");
		TickMWidget.button
				.setTooltipContent(GuiApiHelper
						.makeTextArea(
								"This says whether or not to check for any loose settings after the load is complete. If this is true, it will ask you afterwards whether or not you want to 'trim' them out.",
								false));
		TickMWidget.button.addCallback(callback);
		IDResolver.modScreen.widgetColumn.add(TickMWidget);
		TickMWidget = new WidgetBoolean(IDResolver.showOnlyConf,
				"Only Show Conflicts");
		TickMWidget.button
				.setTooltipContent(GuiApiHelper
						.makeTextArea(
								"This says whether or not to show the resolve screen only when needed: If it's true, ID Resolver will only ask you what to do when there's a conflict. If false, it will ask you for each new Block or Item it detects that doesn't have a saved signature.",
								false));
		TickMWidget.button.addCallback(callback);
		IDResolver.modScreen.widgetColumn.add(TickMWidget);

		temp = GuiApiHelper.makeButton("Wipe ALL Saved IDs", "clearAllIDs",
				IDResolver.class, true);
		temp.setTooltipContent(GuiApiHelper
				.makeTextArea(
						"This will wipe all Saved ID Information: This will NOT wipe out priorities or other settings.",
						false));
		IDResolver.modScreen.widgetColumn.add(temp);

		temp = GuiApiHelper.makeButton("Check for Loose IDs", "checkLooseIDs",
				IDResolver.class, true);
		temp.setTooltipContent(GuiApiHelper
				.makeTextArea(
						"This will tell ID Resolver to check over all settings to see if they are 'loose', or weren't loaded or used during load.",
						false));
		IDResolver.modScreen.widgetColumn.add(temp);

		temp = GuiApiHelper.makeButton("Generate ID Status Report",
				"saveIDStatusToFile", IDResolver.class, true,
				new Class[] { Integer.class, String.class }, 0,"ID Status.txt");
		temp.setTooltipContent(GuiApiHelper
				.makeTextArea(
						"This will generate an ID Status report. It will only show used slots: Free slots will not be outputted.",
						false));
		IDResolver.modScreen.widgetColumn.add(temp);

		temp = GuiApiHelper.makeButton(
				"Generate ID Status Report with Expanded Free IDs",
				"saveIDStatusToFile", IDResolver.class, true,
				new Class[] { Integer.class, String.class }, 1 , "ID Status Expanded IDs.txt");
		temp.setTooltipContent(GuiApiHelper
				.makeTextArea(
						"This will generate an ID Status report. Each free slot will be outputted, including what it can 'take': Blocks, Blocks or Items, or only Items.",
						false));
		IDResolver.modScreen.widgetColumn.add(temp);

		temp = GuiApiHelper.makeButton(
				"Generate ID Status Report with Collapsed Free IDs",
				"saveIDStatusToFile", IDResolver.class, true,
				new Class[] { Integer.class, String.class }, 2, "ID Status Collapsed Free IDs.txt");
		temp.setTooltipContent(GuiApiHelper
				.makeTextArea(
						"This will generate an ID Status report. Free slots will be collapsed, so it will show ranges: Each range will include what it can 'take': Blocks, Blocks or Items, or only Items.",
						false));
		IDResolver.modScreen.widgetColumn.add(temp);

		temp = GuiApiHelper.makeButton("Export ID Mapping Report",
				"saveIDStatusToFile", IDResolver.class, true,
				new Class[] { Integer.class, String.class }, 3, "ID Mapping.txt");
		temp.setTooltipContent(GuiApiHelper
				.makeTextArea(
						"This will export an ID Mapping report, designed for ease of transferring ID Resolver's settings to each individual mod's config files. It includes the mod that added the block, the original ID, and the ID that ID Resolver has on record.",
						false));
		IDResolver.modScreen.widgetColumn.add(temp);

		temp = GuiApiHelper.makeButton("Display ID Status Report",
				"displayIDStatus", IDResolver.class, true);
		temp.setTooltipContent(GuiApiHelper
				.makeTextArea(
						"This will generate and display an ID status report. It's a graphical report, showing each Block and Item in a big list, including what it looks like, if it uses Metadata, and various settings for each, with extra data for Blocks, Food, Armor, etc.",
						false));
		IDResolver.modScreen.widgetColumn.add(temp);

		temp = GuiApiHelper.makeButton("Reload Options",
				"reLoadModGuiAndRefresh", IDResolver.class, true);
		temp.setTooltipContent(GuiApiHelper
				.makeTextArea(
						"This will reload ID Resolver's config information from disk and refresh the mod screen.",
						false));
		IDResolver.modScreen.widgetColumn.add(temp);
	}

	@SuppressWarnings("unused")
	private static void reLoadModGuiAndRefresh() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'ReLoadModGuiAndRefresh' button.");
		GuiModScreen.back();
		IDResolver.reLoadModGui();
		GuiModScreen.show(IDResolver.modScreen.theWidget);
	}

	@SuppressWarnings("unused")
	private static void removeEntry(String key) {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'RemoveEntry' button for " + key);
		IDResolver.knownIDs.remove(key);
		try {
			IDResolver.storeProperties();
			IDResolver.logger.log(Level.INFO, "Removed the saved ID for " + key
					+ " as per use request via Settings screen.");
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO,
					"Was unable to remove the saved ID for " + key
							+ " due to an exception.", e);
		}
		IDResolver.reLoadModGui();
	}

	@SuppressWarnings("unused")
	private static void removeLooseSetting(SettingList setting) {
		int selected = ((WidgetList) setting.displayWidget).listBox
				.getSelected();
		if (selected == -1) {
			return;
		}
		String key = setting.get().get(selected);
		IDResolver.knownIDs.remove(key);
		IDResolver.logger.log(Level.INFO, "Trimmed ID: " + key);
		setting.get().remove(selected);
		setting.displayWidget.update();
		int size = setting.get().size();

		if (selected == size) {
			selected--;
		}

		if (size > 0) {
			((WidgetList) setting.displayWidget).listBox.setSelected(selected);
		} else {
			GuiModScreen.back();
			GuiModScreen.back();
		}
		try {
			IDResolver.storeProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO, "Could not save properties", e);
		}
		IDResolver.reLoadModGui();
	}

	public static void removeSettingByKey(String key) {
		if (IDResolver.knownIDs.containsKey(key)) {
			IDResolver.knownIDs.remove(key);
		}
	}

	@SuppressWarnings("unused")
	private static void resolveNewID(String key) {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'ResolveNewID' button for " + key);
		if (!IDResolver.knownIDs.containsKey(key)) {
			return;
		}
		String trimmedKey = IDResolver.trimType(key,
				IDResolver.isBlockType(key));
		IDResolver resolver = new IDResolver(Integer.parseInt(IDResolver
				.getInfoFromSaveString(trimmedKey)[0]), key);
		resolver.disableAutoAll = true;
		resolver.isMenu = true;
		resolver.setupGui(Integer.parseInt(IDResolver.knownIDs.getProperty(key)));
		resolver.runConflictMenu();
	}

	@SuppressWarnings("unused")
	private static void saveIDStatusToFile(Integer showFree,String fileName) {
		IDResolver.getLogger().log(
				Level.INFO,
				"User pressed 'SaveIDStatusToFile' button. Mode: "
						+ showFree.toString());
		File savePath = new File(new File(Minecraft.getMinecraftDir(),
				fileName).getAbsolutePath().replace("\\.\\", "\\"));
		try {
			FileOutputStream output = new FileOutputStream(savePath);
			output.write(IDResolver.generateIDStatusReport(showFree).getBytes());
			output.flush();
			output.close();

			WidgetSinglecolumn widget = new WidgetSinglecolumn(new Widget[0]);
			TextArea area = GuiApiHelper.makeTextArea(String.format(
					"Saved ID status report to <a href=\"%1$s\">%1$s</a>",
					savePath), true);
			area.addCallback(new ModAction(IDResolver.class, "linkCallback",
					"Link Clicked Callback", String.class));
			widget.add(area);
			widget.overrideHeight = false;
			WidgetSimplewindow window = new WidgetSimplewindow(widget,
					"ID Resolver");
			window.backButton.setText("OK");

			GuiModScreen.show(window);
		} catch (Throwable e) {
			IDResolver.getLogger().log(Level.INFO,
					"Exception when saving ID Status to file.", e);
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			String trace = sw.toString();
			pw.close();
			GuiModScreen.show(GuiApiHelper.makeTextDisplayAndGoBack(
					"ID Resolver",
					"Error saving to " + savePath.getAbsolutePath()
							+ ", exception was:\r\n\r\n" + trace, "OK", false));
		}
	}

	private static void setupOverrides() {
		int pubfinalmod = Modifier.FINAL + Modifier.PUBLIC;
		try {
			for (Field field : Block.class.getFields()) {
				if ((field.getModifiers() == pubfinalmod)
						&& (field.getType() == int.class)) {
					IDResolver.blockIdField = field;
					break;
				}
			}
		} catch (Throwable e3) {
			IDResolver.overridesEnabled = false;
		}

		try {
			for (Field field : Item.class.getFields()) {
				if ((field.getModifiers() == pubfinalmod)
						&& (field.getType() == int.class)) {
					IDResolver.itemIdField = field;
					break;
				}
			}
		} catch (Throwable e3) {
			IDResolver.overridesEnabled = false;
		}
		if (IDResolver.overridesEnabled) {
			IDResolver.blockIdField.setAccessible(true);
			IDResolver.itemIdField.setAccessible(true);
		}
	}

	@SuppressWarnings("unused")
	private static void showMenu(Integer i) {
		IDResolver.getLogger().log(
				Level.INFO,
				"User pressed 'ShowMenu' button for " + i.toString() + " aka "
						+ IDResolver.windows[i].titleWidget.getText());
		GuiModScreen.clicksound();
		GuiModScreen.show(IDResolver.windows[i]);
	}

	private static boolean showOnlyConflicts() {
		if (!IDResolver.modPriorities.containsKey("ShowOnlyConflicts")) {
			IDResolver.modPriorities.setProperty("ShowOnlyConflicts", "false");
			isPrioritiesChanged = true;
			try {
				IDResolver.storeProperties();
			} catch (Throwable e) {
				IDResolver.logger
						.log(Level.INFO,
								"Could not save properties after adding ShowOnlyConflicts option!",
								e);
			}
			return false;
		}
		return IDResolver.modPriorities.getProperty("ShowOnlyConflicts")
				.equalsIgnoreCase("true");
	}

	public static boolean showTick(boolean mainmenu) {
		String key = mainmenu ? "ShowTickMM" : "ShowTickRS";
		if (!IDResolver.modPriorities.containsKey(key)) {
			IDResolver.modPriorities.setProperty(key, "false");
			isPrioritiesChanged = true;
			try {
				IDResolver.storeProperties();
			} catch (Throwable e) {
				IDResolver.logger.log(Level.INFO,
						"Could not save properties after adding " + key
								+ " option!", e);
			}
			return false;
		}
		return IDResolver.modPriorities.getProperty(key).equalsIgnoreCase(
				"true");
	}

	public static void openMappingOutputStream() throws IOException {
		if (streamingOutputStream != null) {
			streamingOutputStream.close();
			streamingOutputStream = null;
		}
		streamingOutputStream = new BufferedWriter(new FileWriter(
				IDResolver.idPath));
	}

	private static void storeProperties() throws FileNotFoundException,
			IOException {
		if (isStreamingSaves) {
			if (streamingOutputStream == null) {
				openMappingOutputStream();
				streamingOutputStream.write(exportMappings());
			} else {
				for (Entry<Object, Object> entry : IDResolver.knownIDs
						.entrySet()) {
					String key = (String) entry.getKey();
					String value = (String) entry.getValue();

					Boolean streamed = wasStreamed.get(key);
					if (streamed != null && streamed == true)
						continue;

					streamingOutputStream.write(key + "=" + value);
					streamingOutputStream.newLine();
					wasStreamed.put(key, true);
				}
				streamingOutputStream.flush();
			}
		} else {
			try {
				IDResolver.getLogger().log(Level.FINER,
						"Saving all mappings...");
				openMappingOutputStream();
				streamingOutputStream.write(exportMappings());
				streamingOutputStream.close();
				streamingOutputStream = null;
			} catch (Throwable e) {
				IDResolver.getLogger().log(Level.WARNING,
						"Unable to save mappings!", e);
			}
		}
		if (isPrioritiesChanged) {
			FileOutputStream stream = new FileOutputStream(
					IDResolver.priorityPath);
			IDResolver.modPriorities.store(stream, IDResolver.settingsComment);
			stream.close();
			isPrioritiesChanged = false;
		}
	}

	public static String exportMappings() {

		String linebreak = System.getProperty("line.separator");
		StringBuilder mappingsOutput = new StringBuilder(knownIDs.size() * 30);
		mappingsOutput
				.append("#IDResolver Known / Set IDs file. Please do not edit manually.")
				.append(linebreak);
		mappingsOutput.append("SAVEVERSION=v2").append(linebreak);
		for (Entry<Object, Object> entry : IDResolver.knownIDs.entrySet()) {
			String key = (String) entry.getKey();
			String value = (String) entry.getValue();
			mappingsOutput.append(key + "=" + value).append(linebreak);
			wasStreamed.put(key, true);
		}
		return mappingsOutput.toString();
	}

	private static void syncMinecraftScreen(Minecraft mc,
			GuiWidgetScreen widgetscreen) {
		mc.displayWidth = mc.mcCanvas.getWidth();
		mc.displayHeight = mc.mcCanvas.getHeight();
		widgetscreen.layout();
		RenderScale.scale = widgetscreen.screenSize.getScaleFactor();
		GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
		widgetscreen.renderer.syncViewportSize();
	}

	@SuppressWarnings("unused")
	private static void tickIDSubItem(WidgetItem2DRender renderer,
			TextArea textArea, Label label) {
		ItemStack stack = renderer.getRenderStack();
		int damage = stack.getItemDamage();
		damage++;
		if (damage > 15) {
			damage = 0;
		}
		stack.setItemDamage(damage);

		Item item = stack.getItem();
		String stackName = IDResolver.getItemNameForStack(stack);
		StringBuilder tooltipText = new StringBuilder(String.format(
				"Slot %-4s : Metadata %s: %s", stack.itemID, damage,
				StatCollector.translateToLocal(stackName + ".name")));

		tooltipText.append(String
				.format("\r\n\r\nInternal name: %s", stackName));
		try {
			IDResolver
					.buildItemInfo(tooltipText, stack.itemID, IDResolver
							.getExistance(IDResolver.getPosition(stack.itemID),
									stack.itemID) == 1);
		} catch (Throwable e) {
			// Ignore this exception
		}

		GuiApiHelper.setTextAreaText(textArea, tooltipText.toString());
	}

	@SuppressWarnings("unused")
	private static void trimLooseSettings(ArrayList<String> unused) {
		WidgetSinglecolumn widgetSingleColumn = new WidgetSinglecolumn();
		widgetSingleColumn.childDefaultWidth = 250;
		SettingList s = new SettingList("unusedSettings", unused);
		WidgetList w = new WidgetList(s, "Loose Settings to Remove");
		w.listBox.setSelected(0);
		widgetSingleColumn.add(w);
		widgetSingleColumn.heightOverrideExceptions.put(w, 140);
		widgetSingleColumn.add(GuiApiHelper.makeButton("Remove Selected",
				"removeLooseSetting", IDResolver.class, true,
				new Class[] { SettingList.class }, s));

		GuiModScreen.show(new WidgetSimplewindow(widgetSingleColumn,
				"Loose Setting Removal"));
	}

	@SuppressWarnings("unused")
	private static void trimLooseSettingsAll(ArrayList<String> unused) {
		for (String key : unused) {
			IDResolver.knownIDs.remove(key);
			IDResolver.logger.log(Level.INFO, "Trimmed ID: " + key);
		}
		try {
			IDResolver.storeProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO,
					"Could not save properties after trimming loose settings!",
					e);
		}
		IDResolver.reLoadModGui();
	}
	
	@SuppressWarnings("unused")
	private static void trimLooseSettingsAutoLoaded(ArrayList<String> unused) {
		Map<String, Boolean> classMap = new HashMap<String, Boolean>();
		
		for (String key : unused) {
			String[] info = IDResolver.getInfoFromSaveString(key,false);
			String classname = info[1];
			if (!classMap.containsKey(classname)) {
				try {
					Class modClass = Class.forName(classname);
					classMap.put(classname, false);
				} catch (ClassNotFoundException e) {
					classMap.put(classname, true);
				}
			}
			if (!classMap.get(classname)) {
				IDResolver.knownIDs.remove(key);
				IDResolver.logger.log(Level.INFO, "Trimmed ID: " + key);
			}
		}
		try {
			IDResolver.storeProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO,
					"Could not save properties after trimming loose settings!",
					e);
		}
		IDResolver.reLoadModGui();
	}

	@SuppressWarnings("unused")
	private static void trimLooseSettingsAutoUnLoaded(ArrayList<String> unused) {
		Map<String, Boolean> classMap = new HashMap<String, Boolean>();
		
		for (String key : unused) {
			String[] info = IDResolver.getInfoFromSaveString(key,false);
			String classname = info[1];
			if (!classMap.containsKey(classname)) {
				try {
					Class modClass = Class.forName(classname);
					classMap.put(classname, true);
				} catch (ClassNotFoundException e) {
					classMap.put(classname, false);
				}
			}
			if (!classMap.get(classname)) {
				IDResolver.knownIDs.remove(key);
				IDResolver.logger.log(Level.INFO, "Trimmed ID: " + key);
			}
		}
		try {
			IDResolver.storeProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO,
					"Could not save properties after trimming loose settings!",
					e);
		}
		IDResolver.reLoadModGui();
	}

	private static String trimPackage(String name) {
		if (name.indexOf('.') == -1)
			return name;
		return name.substring(name.lastIndexOf('.') + 1);
	}

	private static String trimType(String input, boolean block) {
		String type = (block ? "BlockID." : "ItemID.");
		if (input.startsWith(type)) {
			return input.substring(type.length());
		}
		return input;
	}

	private static void updateTickSettings() {
		IDResolver.getLogger().log(Level.INFO, "'UpdateTickSettings' called.");
		if (IDResolver.modPriorities.containsKey("ShowTickMM")) {
			IDResolver.modPriorities.remove("ShowTickMM");
		}
		IDResolver.modPriorities.setProperty("ShowTickMM",
				IDResolver.showTickMM.get().toString());
		if (IDResolver.modPriorities.containsKey("ShowTickRS")) {
			IDResolver.modPriorities.remove("ShowTickRS");
		}
		IDResolver.modPriorities.setProperty("ShowTickRS",
				IDResolver.showTickRS.get().toString());

		if (IDResolver.modPriorities.containsKey("CheckForLooseSettings")) {
			IDResolver.modPriorities.remove("CheckForLooseSettings");
		}
		IDResolver.modPriorities.setProperty("CheckForLooseSettings",
				IDResolver.checkForLooseSettings.get().toString());

		if (IDResolver.modPriorities.containsKey("ShowOnlyConflicts")) {
			IDResolver.modPriorities.remove("ShowOnlyConflicts");
		}
		IDResolver.modPriorities.setProperty("ShowOnlyConflicts",
				IDResolver.showOnlyConf.get().toString());
		isPrioritiesChanged = true;
		try {
			IDResolver.storeProperties();
		} catch (Throwable e) {
			IDResolver.logger
					.log(Level.INFO, "Was unable to save settings.", e);
		}
	}

	public static Boolean wasBlockInited() {
		return IDResolver.wasBlockInited;
	}

	public static Boolean wasItemInited() {
		return IDResolver.wasItemInited;
	}

	@SuppressWarnings("unused")
	private static void wipeSavedIDsFromMenu(String modName) {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'WipeSavedIDsFromMenu' button with " + modName);
		Vector<String> temp = new Vector<String>();
		for (Object key : IDResolver.knownIDs.keySet()) {
			String string = (String) key;
			if ("SAVEVERSION".equals(key)) {
				continue;
			}
			temp.add(string);
		}
		for (String key : temp) {
			if (IDResolver.getInfoFromSaveString(key)[1].equals(modName)) {
				IDResolver.knownIDs.remove(key);
			}
		}
		try {
			IDResolver.storeProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO, "Could not save properties", e);
		}
		GuiModScreen.back();
	}

	private boolean disableAutoAll = false;
	private boolean disableOverride = false;

	private boolean isBlock = true;

	private boolean isMenu = false;

	private String longName;

	private Widget oldsubscreenIDSetter;

	private int originalID;
	private String overrideName;
	private boolean overridingSetting = false;
	private Block requestedBlock;
	private Item requestedItem;
	private Button resolveScreenContinue;
	private TextArea resolveScreenLabel;
	private TextArea resolveScreenLabelTooltip;
	private WidgetItem2DRender resolveScreenLabelTooltipItem;
	private Button resolveScreenOverride;
	
	//TODO: Enable
	/*
	private Button resolveScreenAutoAssignFromID;
	private Button resolveScreenAutoAssignFromIDStrict;
	*/
	private GuiWidgetScreen widgetscreen;
	private Minecraft mc;
	private boolean running = false;
	private boolean restart = false;
	private boolean hasCustomMaxID = false;
	private int maxID = 0;
	
	
	private Scrollbar scrollBar;
	private SettingInt settingIntNewID;

	private boolean specialItem = false;

	private Widget subscreenIDSetter;

	private IDResolver(int Startid, Block Offender) {
		if (IDResolver.initialized) {
			this.isBlock = true;
			this.requestedBlock = Offender;
		}
		Class blockClass = Offender.getClass();
		while (blockClass != null && !this.hasCustomMaxID) {
			Annotation[] annotations = blockClass.getAnnotations();
			for (int i = 0; i < annotations.length; i++) {
				Annotation annotation = annotations[i];
				try {
					Method getMaxValue = annotation.getClass()
							.getDeclaredMethod("maxIDRValue");
					getMaxValue.setAccessible(true);
					Integer res = (Integer) getMaxValue.invoke(annotation);
					if (res.intValue() != 0) {
						this.maxID = res;
						this.hasCustomMaxID = true;
						break;
					}
				} catch (Throwable e) {
					// Ignore this exception
				}
			}
			blockClass = blockClass.getSuperclass();
		}
		this.originalID = Startid;
		this.longName = IDResolver.getlongName(this.requestedBlock,
				this.originalID);
		this.mc = Minecraft.getMinecraft();
		this.widgetscreen = GuiWidgetScreen.getInstance();
	}

	private IDResolver(int Startid, Item Offender) {
		if (IDResolver.initialized) {
			this.isBlock = false;
			if (Offender instanceof ItemBlock) {
				this.specialItem = true;
			}
			this.requestedItem = Offender;
		}
		if (!this.specialItem) {
			Class itemClass = Offender.getClass();
			while (itemClass != null && !this.hasCustomMaxID) {
				Annotation[] annotations = itemClass.getAnnotations();
				for (int i = 0; i < annotations.length; i++) {
					Annotation annotation = annotations[i];
					try {
						Method getMaxValue = annotation.getClass()
								.getDeclaredMethod("maxIDRValue");
						getMaxValue.setAccessible(true);
						Integer res = (Integer) getMaxValue.invoke(annotation);
						if (res.intValue() != 0) {
							this.maxID = res;
							this.hasCustomMaxID = true;
							break;
						}
					} catch (Throwable e) {
						// Ignore this exception
					}
				}
				itemClass = itemClass.getSuperclass();
			}
		}
		this.originalID = Startid;
		this.longName = IDResolver.getlongName(this.requestedItem,
				this.originalID);
		this.mc = Minecraft.getMinecraft();
		this.widgetscreen = GuiWidgetScreen.getInstance();
	}

	private IDResolver(int currentID, String savedname) {
		if (IDResolver.initialized) {
			this.isBlock = IDResolver.isBlockType(savedname);
			String[] info = IDResolver.getInfoFromSaveString(IDResolver
					.trimType(savedname, this.isBlock));
			this.overrideName = "ID " + info[0] + " from " + info[1];
			this.longName = savedname;
			this.originalID = currentID;
		}
		this.mc = Minecraft.getMinecraft();
		this.widgetscreen = GuiWidgetScreen.getInstance();
	}

	private IDResolver(String name, boolean block) {
		if (IDResolver.initialized) {
			this.isBlock = block;
			String[] info = IDResolver.getInfoFromSaveString(name);
			this.overrideName = "ID " + info[0] + " from " + info[1];
		}
		this.mc = Minecraft.getMinecraft();
		this.widgetscreen = GuiWidgetScreen.getInstance();
	}

	@SuppressWarnings("unused")
	private void autoAssign() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'AutoAssign' button.");
		autoAssign(false, false);
	}

	private void autoAssign(boolean skipMessage, boolean reverse) {
		IDResolver.getLogger().log(
				Level.INFO,
				"Automatically assigning ID: Skip Message: " + skipMessage
						+ " Reverse: " + reverse);
		int firstid = (this.isBlock ? (reverse ? this.getLastOpenBlock()
				: this.getFirstOpenBlock()) : (reverse ? this
				.getLastOpenItem() : this.getFirstOpenItem()));
		IDResolver.getLogger().log(Level.INFO,
				"Automatic assign returned new ID " + firstid);
		if (firstid == -1) {
			return;
		}
		this.settingIntNewID.set(firstid);
		if (skipMessage) {
			this.running = false;
		} else {
			displayMessage("Automatically assigned ID "
					+ Integer.toString(firstid) + " for " + getName());
		}
	}
	
	
	private boolean autoAssignFromSelected(boolean strict) {
		//TODO Finish this. Or start it. Whichever.
		
		return false;
	}

	@SuppressWarnings("unused")
	private void autoAssignAll() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'AutoAssignAll' button.");
		IDResolver.autoAssignMod = IDResolver
				.getInfoFromSaveString(this.longName)[1];
		IDResolver.autoAssignBlockLast = -1;
		autoAssign(true, false);
		displayMessage("Automatically assigning IDs...");
	}

	@SuppressWarnings("unused")
	private void autoAssignAllRev() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'AutoAssignAllRev' button.");
		IDResolver.autoAssignMod = "!"
				+ IDResolver.getInfoFromSaveString(this.longName)[1];
		IDResolver.autoAssignBlockLast = -1;
		autoAssign(true, true);
		displayMessage("Automatically assigning IDs...");
	}
	
	@SuppressWarnings("unused")
	private void autoAssignAllFromID() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'AutoAssignAllFromID' button.");
		IDResolver.autoAssignMod = IDResolver
				.getInfoFromSaveString(this.longName)[1];
		IDResolver.autoAssignBlockLast = this.settingIntNewID.get();
		autoAssignFromSelected(false);
		displayMessage("Automatically assigning IDs...");
	}

	@SuppressWarnings("unused")
	private void autoAssignAllFromIDStrict() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'AutoAssignAllFromIDStrict' button.");
		IDResolver.autoAssignMod = "!"
				+ IDResolver.getInfoFromSaveString(this.longName)[1];
		IDResolver.autoAssignBlockLast = this.settingIntNewID.get();
		autoAssignFromSelected(true);
		displayMessage("Automatically assigning IDs...");
	}
	

	@SuppressWarnings("unused")
	private void autoAssignRev() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'AutoAssignRev' button.");
		autoAssign(false, true);
	}

	@SuppressWarnings("unused")
	private void cancel() {
		IDResolver.getLogger().log(Level.INFO, "User pressed 'Cancel' button.");
		this.settingIntNewID = null;
		this.running = false;
	}

	private void displayMessage(String msg) {
		IDResolver.getLogger().log(Level.INFO, "Message Displayed: " + msg);
		this.oldsubscreenIDSetter = this.subscreenIDSetter;

		WidgetSinglecolumn column = new WidgetSinglecolumn();
		{
			TextArea textarea = GuiApiHelper.makeTextArea(msg, false);
			column.add(textarea);
			column.heightOverrideExceptions.put(textarea, 0);
			column.add(GuiApiHelper
					.makeButton("Continue", "finish", this, true));
		}

		WidgetSimplewindow window = new WidgetSimplewindow(column,
				"ID Resolver Notice", false);

		WidgetTick ticker = new WidgetTick();
		window.add(ticker);
		ticker.addTimedCallback(new ModAction(this, "previousScreen"), 5000);

		this.subscreenIDSetter = window;

		this.widgetscreen.resetScreen();
		this.widgetscreen.setScreen(this.subscreenIDSetter);

		loadBackground(this.subscreenIDSetter);
	}

	@SuppressWarnings("unused")
	private void finish() {
		IDResolver.getLogger().log(Level.INFO, "User pressed 'Finish' button.");
		this.running = false;
	}

	private String getBlockName(Block block) {
		try {
			ItemStack stack = new ItemStack(block);
			String name = stack.getItem().getItemDisplayName(stack);
			if ((name != null) && (name.length() != 0)) {
				return name;
			}
			if (!IDResolver.hasStoredID(block.blockID, true)) {
				return IDResolver.getItemNameForStack(new ItemStack(block));
			}
		} catch (Throwable e) {
			// Ignore this exception
		}
		String[] info = IDResolver.getInfoFromSaveString((IDResolver
				.hasStoredID(block.blockID, true) ? IDResolver.getStoredIDName(
				block.blockID, true, true) : IDResolver.getLongBlockName(block,
				this.originalID)));
		return "ID " + info[0] + " (Class: " + block.getClass().getName()
				+ ") from " + info[1];
	}

	private String getItemName(Item item) {
		try {
			ItemStack stack = new ItemStack(item);
			String name = stack.getItem().getItemDisplayName(stack);
			if ((name != null) && (name.length() != 0)) {
				return name;
			}
			if (!IDResolver.hasStoredID(item.shiftedIndex, true)) {
				return IDResolver.getItemNameForStack(new ItemStack(item));
			}
		} catch (Throwable e) {
			// Ignore this exception
		}
		String[] info = IDResolver.getInfoFromSaveString((IDResolver
				.hasStoredID(item.shiftedIndex, false) ? IDResolver
				.getStoredIDName(item.shiftedIndex, false, true) : IDResolver
				.getLongItemName(item, this.originalID)));
		return "ID " + info[0] + " (Class: " + item.getClass().getName()
				+ ") from " + info[1];
	}

	private String getName() {
		String name = "";
		if (this.overrideName != null) {
			return this.overrideName;
		}
		if (this.isBlock) {
			if (this.requestedBlock != null) {
				name = getBlockName(this.requestedBlock);
			} else {
				String[] info = IDResolver.getInfoFromSaveString(IDResolver
						.getStoredIDName(this.originalID, true));
				name = "ID " + info[0] + " from " + info[1];
			}
		} else {
			if (this.requestedItem != null) {
				name = getItemName(this.requestedItem);
			} else {
				String[] info = IDResolver.getInfoFromSaveString(IDResolver
						.getStoredIDName(this.originalID, false));
				name = "ID " + info[0] + " from " + info[1];
			}
		}
		if ((name == null) || "".equals(name)) {
			name = this.longName;
		}
		return name;
	}

	private int getStored() {
		return Integer.parseInt(IDResolver.knownIDs.getProperty(this.longName));
	}

	private String getTypeName() {
		return IDResolver.getTypeName(this.isBlock);
	}

	private boolean hasOpenSlot() {
		return ((this.isBlock ? this.getFirstOpenBlock() : this
				.getFirstOpenItem()) != -1) || this.specialItem;
	}

	private boolean hasStored() {
		return IDResolver.knownIDs.containsKey(this.longName);
	}

	@SuppressWarnings("unused")
	private void itemForceOverwrite() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'ItemForceOverwrite' button.");
		this.running = false;
	}

	private void loadBackground(Widget screen) {
		try {
			Texture tex = this.widgetscreen.renderer.load(Minecraft.class
					.getClassLoader().getResource("gui/background.png"),
					Format.RGB, Filter.NEAREST);
			Image img = tex.getImage(0, 0, tex.getWidth(), tex.getHeight(),
					Color.parserColor("#303030"), true, Texture.Rotation.NONE);
			if (img != null) {
				screen.setBackground(img);
			}
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO, "Failed to load background.");
		}
	}

	@SuppressWarnings("unused")
	private void menuDeleteSavedID() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'MenuDeleteSavedID' button.");
		String oldname = IDResolver.getStoredIDName(this.settingIntNewID.get(),
				this.isBlock, false);
		IDResolver.knownIDs.remove(oldname);
		this.settingIntNewID = null;
		this.running = false;
		try {
			IDResolver.storeProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO,
					"Was unable to store settings for " + getTypeName() + " "
							+ getName() + " due to an exception.", e);
		}
	}

	@SuppressWarnings("unused")
	private void overrideOld() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'OverrideOld' button.");
		this.overridingSetting = true;
		Integer ID = this.settingIntNewID.get();
		if ((this.isBlock && (Block.blocksList[ID] == null))
				|| (!this.isBlock && (Item.itemsList[ID] == null))) {

			displayMessage("Override requested for "
					+ getTypeName()
					+ " at slot "
					+ ID
					+ ", but it is currently just a setting. Overriding now, IDResolver will ask later for the new block ID.");
		} else {
			this.running = false;
		}
	}

	@SuppressWarnings("unused")
	private void previousScreen() {
		this.subscreenIDSetter = this.oldsubscreenIDSetter;
		this.running = false;
	}

	private void showFirstStart() {
		showQuickSettings("ID Resolver has detected that this is it's first run: So, it will ask you for some basic settings.");
	}

	@SuppressWarnings("unused")
	private void showQuickSettings() {
		showQuickSettings("ID Resolver Basic settings menu");
		isFirstStart = true;
		this.restart = true;
	}

	private void showQuickSettings(String message) {
		this.oldsubscreenIDSetter = this.subscreenIDSetter;

		WidgetSinglecolumn column = new WidgetSinglecolumn();
		{
			TextArea textarea = GuiApiHelper.makeTextArea(message, false);
			column.add(textarea);
			column.heightOverrideExceptions.put(textarea, 0);

			IDResolver.updateTickSettings();
			Runnable callback = new ModAction(IDResolver.class,
					"updateTickSettings");
			WidgetBoolean TickMWidget = new WidgetBoolean(
					IDResolver.showTickMM, "Show ID info on Main Menu");
			TickMWidget.button
					.setTooltipContent(GuiApiHelper
							.makeTextArea(
									"Whether or not to show ID Status information on the main menu. This includes available Block IDs, available Item IDs, Free Sprite Indexes, and Free Terrain Indexes.",
									false));
			TickMWidget.button.addCallback(callback);
			column.add(TickMWidget);
			TickMWidget = new WidgetBoolean(IDResolver.showTickRS,
					"Show ID info on Resolve Screen");
			TickMWidget.button
					.setTooltipContent(GuiApiHelper
							.makeTextArea(
									"Whether or not to show ID Status information on the resolve screen. This includes available Block IDs, available Item IDs, Free Sprite Indexes, and Free Terrain Indexes.",
									false));
			TickMWidget.button.addCallback(callback);
			column.add(TickMWidget);
			TickMWidget = new WidgetBoolean(IDResolver.checkForLooseSettings,
					"Check for Loose settings");
			TickMWidget.button
					.setTooltipContent(GuiApiHelper
							.makeTextArea(
									"This says whether or not to check for any loose settings after the load is complete. If this is true, it will ask you afterwards whether or not you want to 'trim' them out.",
									false));
			TickMWidget.button.addCallback(callback);
			column.add(TickMWidget);
			TickMWidget = new WidgetBoolean(IDResolver.showOnlyConf,
					"Only Show Conflicts");
			TickMWidget.button
					.setTooltipContent(GuiApiHelper
							.makeTextArea(
									"This says whether or not to show the resolve screen only when needed: If it's true, ID Resolver will only ask you what to do when there's a conflict. If false, it will ask you for each new Block or Item it detects that doesn't have a saved signature.",
									false));
			TickMWidget.button.addCallback(callback);
			column.add(TickMWidget);

			column.add(GuiApiHelper.makeButton("Continue", "previousScreen",
					this, true));
		}

		WidgetSimplewindow window = new WidgetSimplewindow(column,
				"ID Resolver Notice", false);

		this.subscreenIDSetter = window;

		this.widgetscreen.resetScreen();
		this.widgetscreen.setScreen(this.subscreenIDSetter);

		loadBackground(this.subscreenIDSetter);
	}

	private void priorityConflict(String newobject, String oldobject,
			Boolean isTypeBlock) {
		this.oldsubscreenIDSetter = this.subscreenIDSetter;
		this.subscreenIDSetter = new WidgetSinglecolumn(new Widget[0]);
		{
			this.subscreenIDSetter.add(new Label(""));
			TextArea textarea = GuiApiHelper
					.makeTextArea(
							String.format(
									"There is a mod priority conflict for a %s between two mods. Both has the same priority set. Please select which should take priority.",
									IDResolver.getTypeName(isTypeBlock)), false);
			this.subscreenIDSetter.add(textarea);
			((WidgetSinglecolumn) this.subscreenIDSetter).heightOverrideExceptions
					.put(textarea, 0);
			String[] info = IDResolver.getInfoFromSaveString(newobject);
			this.subscreenIDSetter.add(GuiApiHelper.makeButton("ID " + info[0]
					+ " from " + info[1], "priorityResolver", this, true,
					new Class[] { Boolean.class }, true));
			info = IDResolver.getInfoFromSaveString(oldobject);
			this.subscreenIDSetter.add(GuiApiHelper.makeButton("ID " + info[0]
					+ " from " + info[1], "priorityResolver", this, true,
					new Class[] { Boolean.class }, false));
		}
		this.widgetscreen.resetScreen();
		this.widgetscreen.setScreen(this.subscreenIDSetter);
		loadBackground(this.subscreenIDSetter);
	}

	@SuppressWarnings("unused")
	private void priorityResolver(Boolean overrideold) {
		IDResolver.getLogger().log(
				Level.INFO,
				"User pressed 'PriorityResolver' button with "
						+ overrideold.toString());
		if (overrideold) {
			this.overridingSetting = true;
			this.running = false;
		} else {
			this.subscreenIDSetter = this.oldsubscreenIDSetter;
			this.widgetscreen.resetScreen();
			this.widgetscreen.setScreen(this.subscreenIDSetter);
			loadBackground(this.subscreenIDSetter);
		}
	}

	@SuppressWarnings("unused")
	private void raisePriorityAndOK() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'RaisePriorityAndOK' button.");
		String modname = IDResolver.getInfoFromSaveString(this.longName)[1];
		displayMessage(modname + " is now specified as a Priority Mod Level "
				+ IDResolver.raiseModPriority(modname));
	}

	@SuppressWarnings("unused")
	private void resetIDtoDefault() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'ResetIDtoDefault' button.");
		this.settingIntNewID.set(this.originalID);
		updateUI();
	}

	private void drawLoadingScreen()
	{
		if (this.mc == null)
		{
			return;
		}
		this.widgetscreen.resetScreen();
		WidgetSimplewindow window = new WidgetSimplewindow(GuiApiHelper.makeTextArea("Loading...\r\n" + this.longName, false),null, false);
		this.widgetscreen.setScreen(window);
		loadBackground(window);
		this.widgetscreen.layout();
		this.widgetscreen.gui.update();
		Display.update();
	}
	
	private void runConflict() throws Exception {
		if (this.specialItem) {
			return;
		}
		if (this.mc == null) {
			IDResolver
					.appendExtraInfo("Warning: When resolving "
							+ this.longName
							+ " ID resolver detected that the Minecraft object was NULL! Assuming 'special' object handling. Please report this!");
			return;
		}
		if (IDResolver.shutdown) {
			IDResolver
					.getLogger()
					.log(Level.INFO,
							"IDResolver is attempting to shut down due to user request, skipping assignment.");
			throw new Exception("Minecraft is shutting down.");
		}

		this.running = true;
		drawLoadingScreen();
		
		if (IDResolver.autoAssignMod != null) {
			boolean rev = IDResolver.autoAssignMod.startsWith("!");
			if (IDResolver.getInfoFromSaveString(this.longName)[1]
					.equals(IDResolver.autoAssignMod.substring(rev ? 1 : 0))) {
				
				if(IDResolver.autoAssignBlockLast == -1)
				{
					autoAssign(true, rev);
				}
				else
				{
					autoAssignFromSelected(rev);
				}
			}
		}
		if (!hasOpenSlot()) {
			IDResolver.logger.log(Level.INFO, "no open slots are available.");
			throw new RuntimeException("No open " + getTypeName()
					+ " IDs are available.");
		}
		
		
		int priority = IDResolver.getModPriority(IDResolver
				.getInfoFromSaveString(this.longName)[1]);
		this.restart = false;
		do {
			this.widgetscreen.resetScreen();
			this.widgetscreen.setScreen(this.subscreenIDSetter);
			loadBackground(this.subscreenIDSetter);
			if (this.restart) {
				this.running = true;
			}
			updateUI();
			Font fnt = this.widgetscreen.theme.getDefaultFont();
			if (isFirstStart && this.restart) {
				isFirstStart = false;
			}
			this.restart = false;
			if (isFirstStart) {
				showFirstStart();
				this.restart = true;
			}
			mod_IDResolver.updateUsed();
			if (priority > 0) {
				if (IDResolver.hasStoredID(this.settingIntNewID.defaultValue,
						this.isBlock)) {
					String otherobject = IDResolver.getStoredIDName(
							this.settingIntNewID.defaultValue, this.isBlock,
							true);
					String otherclass = IDResolver
							.getInfoFromSaveString(otherobject)[1];
					int otherpri = IDResolver.getModPriority(otherclass);
					if (priority > otherpri) {
						this.running = false;
						this.overridingSetting = true;
						IDResolver.logger
								.log(Level.INFO,
										"Override will be called due to mod priority for "
												+ IDResolver
														.getInfoFromSaveString(this.longName)[1]
												+ " is greater than for "
												+ otherclass);
					} else if (priority == otherpri) {
						priorityConflict(IDResolver.trimType(this.longName,
								this.isBlock), otherobject, this.isBlock);
					}
				} else {
					this.running = false;
					if (!((this.isBlock && (Block.blocksList[this.settingIntNewID.defaultValue] == null)) || (!this.isBlock && (Item.itemsList[this.settingIntNewID.defaultValue] == null)))) {
						IDResolver.logger
								.log(Level.INFO,
										"Override will be called due to mod priority for "
												+ IDResolver
														.getInfoFromSaveString(this.longName)[1]);
					} else {
						IDResolver.logger
								.log(Level.INFO,
										"Automatically returning default ID due to mod priority for "
												+ IDResolver
														.getInfoFromSaveString(this.longName)[1]);
					}
				}
			}
			if (IDResolver.showOnlyConflicts()) {
				if (this.resolveScreenContinue.isEnabled()) {
					this.running = false;
					IDResolver.logger
							.log(Level.INFO,
									"Automatically returning default ID as no conflict exists.");
				}
			}
			this.widgetscreen.layout();
			boolean wasRunning = this.mc.running;
			while (this.running) {

				if (((this.mc.displayWidth != this.mc.mcCanvas.getWidth()) || (this.mc.displayHeight != this.mc.mcCanvas
						.getHeight()))) {
					IDResolver.syncMinecraftScreen(this.mc, this.widgetscreen);
				}
				this.widgetscreen.gui.update();
				if ((fnt != null) && IDResolver.showTick(false)) {
					this.widgetscreen.renderer.startRendering();
					String[] ids = mod_IDResolver.getIDs();
					for (int i = 0; i < ids.length; i++) {
						fnt.drawText(null, 1, 12 + (i * 12), ids[i]);
					}
					this.widgetscreen.renderer.endRendering();
				}
				Display.update();
				Thread.yield();
				if (!this.mc.running) {
					if (wasRunning) {
						IDResolver.shutdown = true;
					}

					if (!IDResolver.shutdown && IDResolver.attemptedRecover) {
						IDResolver.shutdown = true;
					}
					if (IDResolver.shutdown) {
						IDResolver
								.getLogger()
								.log(Level.INFO,
										"Minecraft has reported that it is shutting down, skipping assignment.");
						this.settingIntNewID = null;
						this.running = false;
					} else {

						displayMessage(IDResolver.langMinecraftShuttingDown);
						IDResolver.shutdown = false;
						this.mc.running = true;
						IDResolver.attemptedRecover = true;
					}
				}
			}
			if (IDResolver.shutdown) {
				this.mc.running = false;
			}
			if (!(isFirstStart && this.restart) && this.settingIntNewID != null) {
				Integer ID = this.settingIntNewID.get();
				if (!this.overridingSetting) {
					IDResolver.knownIDs.setProperty(this.longName,
							ID.toString());
				} else {
					String oldname = IDResolver.getStoredIDName(ID,
							this.isBlock, false);
					if ((this.isBlock && (Block.blocksList[ID] == null))
							|| (!this.isBlock && (Item.itemsList[ID] == null))) {
						IDResolver.knownIDs.remove(oldname);
						IDResolver.knownIDs.setProperty(this.longName,
								ID.toString());
						IDResolver.logger
								.log(Level.INFO,
										"Override requested for "
												+ getTypeName()
												+ " at slot "
												+ ID
												+ ", but it is currently just a setting. Overriding now, IDResolver will ask later for the new block ID.");
					} else {
						IDResolver.logger.log(Level.INFO,
								"Overriding setting. Requesting new ID for old "
										+ getTypeName() + " at slot " + ID
										+ ".");
						IDResolver resolver = null;
						if (this.isBlock) {
							resolver = new IDResolver(ID, Block.blocksList[ID]);
						} else {
							resolver = new IDResolver(ID, Item.itemsList[ID]);
						}
						resolver.longName = oldname;
						resolver.disableOverride = true;
						resolver.setupGui(ID);
						IDResolver.knownIDs.remove(oldname);
						IDResolver.knownIDs.setProperty(this.longName,
								ID.toString());
						resolver.updateUI();
						resolver.runConflict();
						Integer oldid = resolver.settingIntNewID.get();
						if (resolver.settingIntNewID == null) {
							IDResolver.logger
									.log(Level.INFO,
											"User cancelled request for override. Resetting.");
							IDResolver.knownIDs.remove(this.longName);
							IDResolver.knownIDs.setProperty(oldname,
									ID.toString());
							this.restart = true;
							continue;
						}
						IDResolver.logger.log(Level.INFO, "Overriding "
								+ getTypeName() + " as requested.");
						if (this.isBlock) {
							IDResolver.overrideBlockID(ID, oldid);
						} else {
							IDResolver.overrideItemID(ID, oldid);
						}
						IDResolver.logger.log(Level.INFO,
								"Sucessfully overrode IDs. Setting ID " + ID
										+ " for " + getName() + ", Overriding "
										+ resolver.getName() + " to " + oldid
										+ " as requested.");
					}
				}
				try {
					IDResolver.storeProperties();
				} catch (Throwable e) {
					IDResolver.logger
							.log(Level.INFO,
									"Was unable to store settings for "
											+ getTypeName() + " " + getName()
											+ " due to an exception.", e);
				}
			}
		} while (this.restart);
		this.widgetscreen.resetScreen();
	}

	private void runConflictMenu() {
		this.running = true;
		GuiModScreen modscreen;
		boolean restart = false;
		if (!this.mc.running) {
			IDResolver
					.getLogger()
					.log(Level.INFO,
							"Minecraft has reported that it is shutting down, skipping assignment.");
			this.settingIntNewID = null;
			GuiModScreen.back();
			return;
		}
		do {
			modscreen = new GuiModScreen(GuiModScreen.currentScreen,
					this.subscreenIDSetter);
			GuiModScreen.show(modscreen);
			if (restart) {
				this.running = true;
			}
			updateUI();
			Font fnt = this.widgetscreen.theme.getDefaultFont();
			restart = false;
			mod_IDResolver.updateUsed();
			if (this.isMenu) {
				if (this.mc.theWorld != null) {
					displayMessage("You cannot modify IDs while in game. Please exit to main menu and try again.");
				}
			}
			this.widgetscreen.layout();
			while (this.running) {
				if ((this.mc.displayWidth != this.mc.mcCanvas.getWidth())
						|| (this.mc.displayHeight != this.mc.mcCanvas.getHeight())) {
					syncMinecraftScreen(this.mc, this.widgetscreen);
				}
				modscreen.drawScreen(0, 0, 0);
				if ((fnt != null) && IDResolver.showTick(false)) {
					this.widgetscreen.renderer.startRendering();
					String[] ids = mod_IDResolver.getIDs();
					for (int i = 0; i < ids.length; i++) {
						fnt.drawText(null, 1, 12 + (i * 12), ids[i]);
					}
					this.widgetscreen.renderer.endRendering();
				}
				Display.update();
				Thread.yield();
				if (!this.mc.running) {
					IDResolver
							.getLogger()
							.log(Level.INFO,
									"Minecraft has reported that it is shutting down, skipping assignment.");
					this.settingIntNewID = null;
					this.running = false;
				}
			}
			if (this.settingIntNewID != null) {
				Integer ID = this.settingIntNewID.get();
				if (!this.overridingSetting) {
					IDResolver.knownIDs.setProperty(this.longName,
							ID.toString());
					if (this.isBlock) {
						IDResolver.overrideBlockID(
								this.settingIntNewID.defaultValue, ID);
					} else {
						IDResolver.overrideItemID(
								this.settingIntNewID.defaultValue, ID);
					}
				} else {
					String oldname = IDResolver.getStoredIDName(ID,
							this.isBlock, false);
					if ((this.isBlock && (Block.blocksList[ID] == null))
							|| (!this.isBlock && (Item.itemsList[ID] == null))) {
						IDResolver.knownIDs.remove(oldname);
						IDResolver.knownIDs.setProperty(this.longName,
								ID.toString());
						IDResolver.logger
								.log(Level.INFO,
										"Override requested for "
												+ getTypeName()
												+ " at slot "
												+ ID
												+ ", but it is currently just a setting. Removing the old setting as it may be a loose end.");
						if (this.isBlock) {
							IDResolver.overrideBlockID(
									this.settingIntNewID.defaultValue, ID);
						} else {
							IDResolver.overrideItemID(
									this.settingIntNewID.defaultValue, ID);
						}
					} else {
						IDResolver.logger.log(Level.INFO,
								"Overriding setting. Requesting new ID for old "
										+ getTypeName() + " at slot " + ID
										+ ".");
						Object oldObject = null;
						IDResolver resolver = null;
						if (this.isBlock) {
							resolver = new IDResolver(ID, Block.blocksList[ID]);
							oldObject = Block.blocksList[this.settingIntNewID.defaultValue];
							Block.blocksList[this.settingIntNewID.defaultValue] = null;
						} else {
							resolver = new IDResolver(ID, Item.itemsList[ID]);
							oldObject = Item.itemsList[this.settingIntNewID.defaultValue];
							Item.itemsList[this.settingIntNewID.defaultValue] = null;
						}
						resolver.longName = oldname;
						resolver.disableOverride = true;
						resolver.setupGui(ID);
						IDResolver.knownIDs.remove(oldname);
						IDResolver.knownIDs.setProperty(this.longName,
								ID.toString());
						resolver.updateUI();
						resolver.runConflictMenu();
						Integer oldid = resolver.settingIntNewID.get();
						if (this.isBlock) {
							Block.blocksList[ID] = (Block) oldObject;
							oldObject = Block.blocksList[oldid];
							Block.blocksList[oldid] = Block.blocksList[ID];
							Block.blocksList[ID] = null;
						} else {
							Item.itemsList[ID] = (Item) oldObject;
							oldObject = Item.itemsList[oldid];
							Item.itemsList[oldid] = Item.itemsList[ID];
							Item.itemsList[ID] = null;
						}
						if (resolver.settingIntNewID == null) {
							IDResolver.logger
									.log(Level.INFO,
											"User cancelled request for override. Resetting.");
							IDResolver.knownIDs.remove(this.longName);
							IDResolver.knownIDs.setProperty(oldname,
									ID.toString());
							restart = true;
							continue;
						}
						IDResolver.logger.log(Level.INFO, "Overriding "
								+ getTypeName() + " as requested.");
						if (this.isBlock) {
							IDResolver.overrideBlockID(oldid, ID);
						} else {
							IDResolver.overrideItemID(oldid, ID);
						}
						if (this.isBlock) {
							Block.blocksList[oldid] = (Block) oldObject;
						} else {
							Item.itemsList[oldid] = (Item) oldObject;
						}
						IDResolver.logger.log(Level.INFO,
								"Sucessfully overrode IDs. Setting ID " + ID
										+ " for " + getName() + ", Overriding "
										+ resolver.getName() + " to " + oldid
										+ " as requested.");
					}
				}
				try {
					IDResolver.storeProperties();
				} catch (Throwable e) {
					IDResolver.logger
							.log(Level.INFO,
									"Was unable to store settings for "
											+ getTypeName() + " " + getName()
											+ " due to an exception.", e);
				}
			}
		} while (restart);
		GuiModScreen.back();
	}

	private int getMaxID()
	{
		if(this.hasCustomMaxID)
			return this.maxID;
		if(this.isBlock || this.specialItem)
		{
			return Block.blocksList.length - 1;
		}
		return Item.itemsList.length - 1;
	}
	
	private void setupGui(int RequestedID) {
		this.subscreenIDSetter = new WidgetSinglecolumn(new Widget[0]);
		{
			this.overrideName = getName();
			String text = "";
			if (!this.isMenu) {
				text = "New " + getTypeName() + " detected. Select ID for "
						+ this.overrideName;
			} else {
				text = "Select ID for " + this.overrideName;
			}
			TextArea area = GuiApiHelper.makeTextArea(text, false);
			area.setTooltipContent(GuiApiHelper.makeTextArea(text, false));
			this.subscreenIDSetter.add(area);
			((WidgetSinglecolumn) this.subscreenIDSetter).heightOverrideExceptions
					.put(area, 0);
			this.settingIntNewID = new SettingInt("New ID", RequestedID,
					(this.isBlock || this.specialItem ? 1
							: Item.shovelSteel.shiftedIndex), 1, getMaxID());
			this.settingIntNewID.defaultValue = RequestedID;
			WidgetInt intdisplay = new WidgetInt(this.settingIntNewID, "New ID");
			this.subscreenIDSetter.add(intdisplay);
			intdisplay.slider.getModel().addCallback(
					new ModAction(this, "updateUI"));
			intdisplay.slider.setCanEdit(true);
			intdisplay.slider.setTooltipContent(GuiApiHelper.makeTextArea(
					"Double click to enter your new ID in text form!", false));

			this.scrollBar = new Scrollbar(Orientation.HORIZONTAL);
			this.subscreenIDSetter.add(this.scrollBar);
			this.scrollBar.setMinMaxValue(this.settingIntNewID.minimumValue,
					this.settingIntNewID.maximumValue);
			this.scrollBar.setValue(this.settingIntNewID.get());
			this.scrollBar.addCallback(new ModAction(this, "updateUISB"));
			this.scrollBar.setTooltipContent(GuiApiHelper.makeTextArea(
					"You can use this to select the new ID Easily as well!",
					false));

			this.resolveScreenLabel = GuiApiHelper.makeTextArea("", false);
			this.subscreenIDSetter.add(this.resolveScreenLabel);
			((WidgetSinglecolumn) this.subscreenIDSetter).heightOverrideExceptions
					.put(this.resolveScreenLabel, 0);

			this.resolveScreenLabelTooltipItem = new WidgetItem2DRender();
			this.resolveScreenLabelTooltip = GuiApiHelper.makeTextArea("",
					false);
			WidgetSingleRow row = new WidgetSingleRow(150, 30);
			row.add(this.resolveScreenLabelTooltipItem, 32, 32);
			row.add(this.resolveScreenLabelTooltip);
			this.resolveScreenLabel.setTooltipContent(row);
			this.resolveScreenContinue = GuiApiHelper.makeButton(
					"Save and Continue loading", "finish", this, true);
			this.resolveScreenContinue
					.setTooltipContent(GuiApiHelper
							.makeTextArea(
									"Click this to use the selected ID and continue the loading process.",
									false));
			this.resolveScreenContinue.setEnabled(false);
			this.subscreenIDSetter.add(this.resolveScreenContinue);
			if (!this.disableOverride) {
				this.resolveScreenOverride = GuiApiHelper.makeButton(
						"Override old setting", "overrideOld", this, true);
				this.resolveScreenOverride
						.setTooltipContent(GuiApiHelper
								.makeTextArea(
										"Click this change the selected ID so the current ID can replace it. Please restart Minecraft after load is complete to ensure all data is correct though!",
										false));
				this.resolveScreenOverride.setEnabled(IDResolver.hasStoredID(
						RequestedID, this.isBlock)
						&& IDResolver.overridesEnabled);
				this.subscreenIDSetter.add(this.resolveScreenOverride);
			}
			Button button;
			if (!this.isBlock && !this.isMenu) {
				button = GuiApiHelper.makeButton("Force Overwrite",
						"itemForceOverwrite", this, true);
				this.subscreenIDSetter.add(button);
				button.setTooltipContent(GuiApiHelper
						.makeTextArea(
								"This will tell ID Resolver to Force overwrite an ID. This will not reassign the other ID!",
								false));
			}

			if (this.isMenu) {
				button = GuiApiHelper.makeButton("Delete saved ID",
						"menuDeleteSavedID", this, true);
				this.subscreenIDSetter.add(button);
				button.setTooltipContent(GuiApiHelper
						.makeTextArea(
								"This will delete the saved reference for this ID, so that on next start it will ask you to resolve it again.",
								false));
			} else {
				button = GuiApiHelper.makeButton(
						"Show ID Resolver settings menu", "showQuickSettings",
						this, true);
				this.subscreenIDSetter.add(button);
				button.setTooltipContent(GuiApiHelper
						.makeTextArea(
								"This will open the quick settings menu, like the first start: This lets you change whether to ask you about conflicts and disable the ID Status information on the fly.",
								false));
			}

			button = GuiApiHelper.makeButton("Automatically assign an ID",
					"autoAssign", this, true);
			this.subscreenIDSetter.add(button);
			button.setTooltipContent(GuiApiHelper
					.makeTextArea(
							"This will find the first available ID for you, and continue the loading process!",
							false));
			button = GuiApiHelper.makeButton(
					"Automatically assign an ID in Reverse", "autoAssignRev",
					this, true);
			this.subscreenIDSetter.add(button);
			button.setTooltipContent(GuiApiHelper
					.makeTextArea(
							"This will find the first available ID for you (going backwards, from the last ID to the first), and continue the loading process!",
							false));

			if (!this.disableAutoAll) {
				button = GuiApiHelper.makeButton(
						"Automatically assign an ID to All\r\nfrom this mod",
						"autoAssignAll", this, true);
				this.subscreenIDSetter.add(button);
				button.setTooltipContent(GuiApiHelper
						.makeTextArea(
								"This will flag a mod to automatically use the 'Automatically assign an ID' button for every ID for this load!",
								false));
				((WidgetSinglecolumn) this.subscreenIDSetter).heightOverrideExceptions
						.put(button, 30);

				button = GuiApiHelper
						.makeButton(
								"Automatically assign an ID to All from\r\nthis mod in Reverse",
								"autoAssignAllRev", this, true);
				button.setTooltipContent(GuiApiHelper
						.makeTextArea(
								"This will flag a mod to automatically use the 'Automatically assign an ID in Reverse' button for every ID for this load!",
								false));
				this.subscreenIDSetter.add(button);

				((WidgetSinglecolumn) this.subscreenIDSetter).heightOverrideExceptions
						.put(button, 30);
				
				//TODO: Enable this later.
				
				/*
				
				this.resolveScreenAutoAssignFromID = GuiApiHelper.makeButton(
						"Automatically assign an ID to All\r\nfrom this mod, from selected ID",
						"autoAssignAllFromID", this, true);
				this.subscreenIDSetter.add(this.resolveScreenAutoAssignFromID);
				this.resolveScreenAutoAssignFromID.setTooltipContent(GuiApiHelper
						.makeTextArea(
								"This will flag a mod to automatically assign an ID starting from the selected ID, skipping to the next ID if needed.",
								false));
				((WidgetSinglecolumn) this.subscreenIDSetter).heightOverrideExceptions
						.put(this.resolveScreenAutoAssignFromID, 30);

				this.resolveScreenAutoAssignFromID.setEnabled(false);
				
				
				this.resolveScreenAutoAssignFromIDStrict = GuiApiHelper
						.makeButton(
								"Automatically assign an ID to All\r\nfrom this mod, from selected\r\nID in Strict mode",
								"autoAssignAllFromIDStrict", this, true);
				this.resolveScreenAutoAssignFromIDStrict.setTooltipContent(GuiApiHelper
						.makeTextArea(
								"This will flag a mod to automatically assign an ID starting from the selected ID, but will show the conflict menu if there's a collision along the way.",
								false));
				this.subscreenIDSetter.add(this.resolveScreenAutoAssignFromIDStrict);

				((WidgetSinglecolumn) this.subscreenIDSetter).heightOverrideExceptions
						.put(this.resolveScreenAutoAssignFromIDStrict, 40);
				
				this.resolveScreenAutoAssignFromIDStrict.setEnabled(false);
				
				*/
				
				
				if (IDResolver.getModPriority(IDResolver
						.getInfoFromSaveString(this.longName)[1]) < Integer.MAX_VALUE) {
					button = GuiApiHelper.makeButton(
							"Raise the priority for this mod",
							"raisePriorityAndOK", this, true);
					button.setTooltipContent(GuiApiHelper
							.makeTextArea(
									"This will flag a mod so that it has a higher 'priority', and ID Resolver will try it's best to get it it's original IDs, even if it means asking you to reassign IDs you previously set.",
									false));
					this.subscreenIDSetter.add(button);
				}
			}
			button = GuiApiHelper.makeButton("Reset to default ID",
					"resetIDtoDefault", this, true);
			button.setTooltipContent(GuiApiHelper
					.makeTextArea(
							"This will reset the ID Display on this screen to the original ID for you.",
							false));
			this.subscreenIDSetter.add(button);

			button = GuiApiHelper.makeButton("Cancel and Return", "cancel",
					this, true);
			button.setTooltipContent(GuiApiHelper
					.makeTextArea(
							"This will cancel out of ID Resolver: If you are in the menu, it will just go back to settings. If you aren't, Minecraft will fall back to default behaviour, be that overwrite or crash!",
							false));
			this.subscreenIDSetter.add(button);
		}
		this.subscreenIDSetter = new ScrollPane(this.subscreenIDSetter);
		((ScrollPane) this.subscreenIDSetter)
				.setFixed(ScrollPane.Fixed.HORIZONTAL);
	}

	private void updateUI() {
		int ID = this.settingIntNewID.get();
		this.scrollBar.setValue(ID, false);
		String Name = null;
		boolean isFromSettings = false;
		try {
			if (this.isBlock) {
				Block selectedBlock = Block.blocksList[ID];
				if (selectedBlock != null) {
					Name = getBlockName(selectedBlock);
				}
			}
			if (Name == null) {
				Item selectedItem = Item.itemsList[ID];
				if (selectedItem != null) {
					Name = getItemName(selectedItem);
				}
			}
			if (Name == null && vanillaIDs[ID]) {
				Name = "Un-named Vanilla " + getTypeName();
			}
			if (Name == null) {
				isFromSettings = true;
				if (this.isBlock && IDResolver.hasStoredID(ID, true)) {
					String[] info = IDResolver.getInfoFromSaveString(IDResolver
							.getStoredIDName(ID, true));
					Name = "ID " + info[0] + " from " + info[1];
				} else {
					if (IDResolver.hasStoredID(ID, false)) {
						String[] info = IDResolver
								.getInfoFromSaveString(IDResolver
										.getStoredIDName(ID, false));
						Name = "ID " + info[0] + " from " + info[1];
					}
				}

			}

		} catch (Throwable e) {
			Name = "ERROR";
		}
		boolean originalmenu = (this.isMenu && (ID == this.settingIntNewID.defaultValue));
		if (!this.disableOverride) {
			this.resolveScreenOverride.setEnabled(IDResolver.hasStoredID(ID,
					this.isBlock)
					&& IDResolver.overridesEnabled
					&& !originalmenu);
		}
		if (!originalmenu) {
			if (Name == null) {
				GuiApiHelper.setTextAreaText(this.resolveScreenLabel,
						getTypeName() + " ID " + Integer.toString(ID)
								+ " is available!");
				GuiApiHelper.setTextAreaText(this.resolveScreenLabelTooltip,
						getTypeName() + " ID " + Integer.toString(ID)
								+ " is available!");

				this.resolveScreenContinue.setEnabled(true);
			} else {
				GuiApiHelper.setTextAreaText(this.resolveScreenLabel,
						getTypeName() + " ID " + Integer.toString(ID)
								+ " is used by " + Name + ".");
				if (isFromSettings) {
					GuiApiHelper.setTextAreaText(
							this.resolveScreenLabelTooltip, getTypeName()
									+ " ID " + Integer.toString(ID)
									+ " is used by " + Name + ".\nThis is ");
				} else {
					String text = "ID " + Integer.toString(ID)
							+ " is in use.\n";
					if (vanillaIDs[ID]) {
						text += "It is a vanilla ID.";
					} else {
						if (IDResolver.hasStoredID(ID, true)) {
							text += "This is an Item that was added by "
									+ IDResolver
											.getInfoFromSaveString(IDResolver
													.getStoredIDName(ID, true))[1];
						} else {
							if (IDResolver.hasStoredID(ID, false)) {
								text += "This is an Item that was added by "
										+ IDResolver
												.getInfoFromSaveString(IDResolver
														.getStoredIDName(ID,
																false))[1];
							}
						}
					}
					this.resolveScreenLabelTooltipItem.setRenderID(ID);
					GuiApiHelper.setTextAreaText(
							this.resolveScreenLabelTooltip, text);
				}
				this.resolveScreenContinue.setEnabled(false);
			}
			//TODO: Enable this
			/*
			this.resolveScreenAutoAssignFromID.setEnabled(this.resolveScreenContinue.isEnabled());
			this.resolveScreenAutoAssignFromIDStrict.setEnabled(this.resolveScreenContinue.isEnabled());
			*/
		} else {
			GuiApiHelper.setTextAreaText(this.resolveScreenLabel,
					"This is the currently saved ID.");
			this.resolveScreenContinue.setEnabled(true);
		}
	}

	@SuppressWarnings("unused")
	private void updateUISB() {
		this.settingIntNewID.set(this.scrollBar.getValue());
	}

	@SuppressWarnings("unused")
	private void upPrioritizeMod() {
		IDResolver.getLogger().log(Level.INFO,
				"User pressed 'UpPrioritizeMod' button.");
		String classname = IDResolver.getInfoFromSaveString(this.longName)[1];
		Boolean override = false;
		if (this.isBlock) {
			Block selectedBlock = Block.blocksList[this.settingIntNewID.defaultValue];
			if (selectedBlock != null) {
				override = getBlockName(selectedBlock) != null;
			}
		} else {
			Item selectedItem = Item.itemsList[this.settingIntNewID.defaultValue];
			if (selectedItem != null) {
				override = getItemName(selectedItem) != null;
			}
		}
		if (IDResolver.hasStoredID(this.settingIntNewID.defaultValue,
				this.isBlock)) {
			override = true;
		}
		this.overridingSetting = override;
		this.settingIntNewID.reset();
		displayMessage(classname + " is now specified as a Priority Mod Level "
				+ IDResolver.raiseModPriority(classname));
	}
}
