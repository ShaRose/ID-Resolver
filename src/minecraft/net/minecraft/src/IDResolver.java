package net.minecraft.src;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import net.minecraft.client.Minecraft;

import org.lwjgl.Sys;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.Color;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.ScrollPane;
import de.matthiasmann.twl.TextArea;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.renderer.Font;
import de.matthiasmann.twl.renderer.Image;
import de.matthiasmann.twl.renderer.Texture;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLTexture.Filter;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLTexture.Format;
import de.matthiasmann.twl.renderer.lwjgl.RenderScale;
import de.matthiasmann.twl.textarea.SimpleTextAreaModel;

public class IDResolver implements Runnable {
	private static String autoAssignMod;

	private static Field blockidfield;
	private static SettingBoolean checkForLooseSettings = null;
	private static String extraInfo = null;
	private static File idPath;
	private static boolean initialized;
	private static Field itemidfield;
	private static Properties knownIDs;
	private static final String langLooseSettingsFound = "ID resolver has found some loose settings that aren't being used. Would you like to view a menu to remove them?";
	private static final String langMinecraftShuttingDown = "Minecraft is shutting down. If you did not try and exit the game, there is likely an exception such as not enough sprite indexes in your Modloader.txt, or another mod is attempting to shut down minecraft. Please post in the ID resolver thread with your Modloader.txt, IDResolver.txt, and describe what was happening.";
	private static HashSet<String> loadedEntries = new HashSet<String>();
	private static Logger logger;
	private static Properties modPriorities;
	private static ModSettingScreen modscreen;
	private static boolean overridesenabled;
	private static File priorityPath;
	private static String settingsComment;
	private static SettingBoolean showOnlyConf = null;
	private static SettingBoolean showTickMM = null;
	private static SettingBoolean showTickRS = null;
	private static Boolean shutdown = false;
	private static Boolean wasBlockInited = false;
	private static Boolean wasItemInited = false;
	private static Hashtable<Integer, String> idToMod = null;

	private static WidgetSimplewindow[] windows;

	static {
		IDResolver.logger = Logger.getLogger("IDResolver");
		try {
			FileHandler logHandler = new FileHandler(new File(
					Minecraft.getMinecraftDir(), "IDResolver.txt").getPath());
			logHandler.setFormatter(new SimpleFormatter());
			IDResolver.logger.addHandler(logHandler);
			IDResolver.logger.setLevel(Level.ALL);
		} catch (Throwable e) {
			throw new RuntimeException("IDResolver - Unable to create logger!",
					e);
		}
		IDResolver.settingsComment = "IDResolver Known / Set IDs file. Please do not edit manually.";
		IDResolver.overridesenabled = true;
		IDResolver.autoAssignMod = null;
		IDResolver.initialized = false;
		IDResolver.SetupOverrides();
		IDResolver.AddModGui();
	}

	private static void AddModGui() {
		IDResolver.modscreen = new ModSettingScreen("ID Resolver");
		IDResolver.modscreen.setSingleColumn(true);
		IDResolver.modscreen.widgetColumn.childDefaultWidth = 300;
		IDResolver.modscreen.widgetColumn.add(GuiApiHelper.makeButton("Reload Entries",
				"ReLoadModGui", IDResolver.class, true));
		IDResolver.ReloadIDs();
	}

	private static void AppendExtraInfo(String info) {
		if (IDResolver.extraInfo == null) {
			IDResolver.extraInfo = info;
		} else {
			IDResolver.extraInfo += "\r\n\r\n" + info;
		}
	}

	private static boolean CheckForLooseSettings() {
		if (!IDResolver.modPriorities.containsKey("CheckForLooseSettings")) {
			IDResolver.modPriorities.setProperty("CheckForLooseSettings",
					"true");
			try {
				IDResolver.StoreProperties();
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

	@SuppressWarnings("unused")
	private static void CheckLooseIDs() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'CheckLooseIDs' button.");
		IDResolver.CheckLooseSettings(false);
	}

	public static void CheckLooseSettings(boolean automatic) {
		if (IDResolver.CheckForLooseSettings() || !automatic) {
			ArrayList<String> unusedIDs = IDResolver.CheckUnusedIDs();
			if (unusedIDs.size() != 0) {
				Logger idlog = IDResolver.GetLogger();
				idlog.info("Detected " + unusedIDs.size()
						+ " unused (Loose) IDs.");
				GuiApiHelper choiceBuilder = GuiApiHelper
						.createChoiceMenu(IDResolver.langLooseSettingsFound);
				choiceBuilder.addButton("Go to trim menu", "TrimLooseSettings",
						IDResolver.class, new Class[] { (ArrayList.class) },
						false, unusedIDs);
				choiceBuilder.addButton("Trim all loose settings",
						"TrimLooseSettingsAll", IDResolver.class,
						new Class[] { (ArrayList.class) }, true, unusedIDs);
				choiceBuilder.addButton(
						"Trim all loose settings for unloaded mods",
						"TrimLooseSettingsAuto", IDResolver.class,
						new Class[] { (ArrayList.class) }, true, unusedIDs);
				if (automatic) {
					choiceBuilder.addButton("Ignore and don't ask again",
							"IgnoreLooseDetectionAndDisable", IDResolver.class,
							true);
				}
				WidgetSimplewindow window = choiceBuilder.genWidget(true);
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

	public static ArrayList<String> CheckUnusedIDs() {
		ArrayList<String> unused = new ArrayList<String>();
		for (Entry<Object, Object> entry : IDResolver.knownIDs.entrySet()) {
			String key = (String) entry.getKey();
			if("SAVEVERSION".equals(key))
				continue;
			if (!IDResolver.loadedEntries.contains(key)) {
				unused.add(key);
			}
		}
		return unused;
	}

	@SuppressWarnings("unused")
	private static void ClearAllIDs() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'ClearAllIDs' button.");
		IDResolver.knownIDs.clear();
		try {
			IDResolver.StoreProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO, "Could not save properties", e);
		}
	}

	private static int ConflictHelper(IDResolver resolver) {
		if (!IDResolver.initialized) {
			IDResolver.logger
					.log(Level.INFO,
							"IDResolver - Not initialized. This should never happen. Throwing exception.");
			throw new RuntimeException(
					"ID Resolver did not initalize! Please go to the thread and post ID resolver.txt for help resolving this error, which should basically never happen.");
		}
		if (resolver.HasStored()) {
			int id = resolver.GetStored();
			IDResolver.logger.log(Level.INFO, "IDResolver - Loading saved ID "
					+ Integer.toString(id) + " for " + resolver.GetTypeName()
					+ " " + resolver.GetName() + ".");
			return id;
		}
		try {
			resolver.RunConflict();
			if (resolver.settingIntNewID == null) {
				IDResolver.logger
						.log(Level.INFO,
								"IDResolver - New setting null, assuming user cancelled, returning to default behavior.");
				return -1;
			}
			if (!resolver.specialItem) {
				IDResolver.logger.log(Level.INFO,
						"IDResolver - User selected new ID "
								+ resolver.settingIntNewID.get().toString()
								+ " for " + resolver.GetName()
								+ ", returning control with new ID.");
			}
			return resolver.settingIntNewID.get();
		} catch (Exception e) {
			IDResolver.logger.log(Level.INFO,
					"IDResolver - Unhandled exception in ConflictHelper.", e);
			throw new RuntimeException("Unhandled exception in ConflictHelper.",e);
		}
	}

	public static void DisableLooseSettingsCheck() {
		IDResolver.checkForLooseSettings.set(false);
		IDResolver.UpdateTickSettings();
	}

	@SuppressWarnings("unused")
	private static void DisplayIDStatus() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'DisplayIDStatus' button.");

		if(idToMod == null)
		{
			IDResolver.GetLogger().log(Level.INFO,
					"IDResolver - ID - Mod map is null. Regenerating.");
			try
			{
				idToMod = new Hashtable<Integer, String>(IDResolver.loadedEntries.size());
			for (Entry<Object, Object> entry : IDResolver.knownIDs.entrySet()) {
				String key = (String) entry.getKey();
				if("SAVEVERSION".equals(key))
					continue;
				if(IDResolver.loadedEntries.contains(key))
				{
					String[] info = GetInfoFromSaveString(key);
					Integer value = Integer.parseInt((String) entry.getValue());
					boolean isBlock = IsBlockType(info[0]);
					if(isBlock ? (Block.blocksList[value] != null) : (Item.itemsList[value] != null))
					{
						idToMod.put(value, info[1]);
					}
				}
			}
			
			IDResolver.GetLogger().log(Level.INFO,
					"IDResolver - Finished generation of ID - Mod map..");
			}
			catch(Throwable e)
			{
				IDResolver.GetLogger().log(Level.INFO,
						"IDResolver - Could not generate ID - Mod map. Possibly corrupted database? Skipping.",e);
				idToMod = null;
			}
		}
		
		ModAction mergedActions = null;
		
		WidgetSinglecolumn area = new WidgetSinglecolumn();

		area.childDefaultWidth = 250;
		area.childDefaultHeight = 40;
		int freeSlotStart = -1;
		String[] freeName = new String[]{"block", "block or item","item"};
		String[] freeNames = new String[]{"blocks", "blocks or items","items"};
		for (int i = 1; i < Item.itemsList.length; i++) {
			
			
			boolean addTick = false;
			Label label = null;
			StringBuilder tooltipText = null;
			ItemStack stack = new ItemStack(i, 1, 0);
			int position = GetPosition(i);
			int exists = GetExistance(position,i);
			
			if (exists == 0) {
				if (freeSlotStart == -1) {
					freeSlotStart = i;
				}
				int next = i + 1;
				if(next != Item.itemsList.length)
				{
				int nextPosition = GetPosition(next);
				int nextExists = GetExistance(nextPosition,next);
				
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
									i - freeSlotStart,
									freeNames[position]));
				} else {
					label = new Label(String.format("Slot %-4s: Open slot", i));
					tooltipText = new StringBuilder(
							String.format(
									"Open Slot\r\n\r\nThis slot is open for any %s to use.",
									freeName[position]));
				}
				freeSlotStart = -1;
			}
			else
			{
				String stackName = GetItemNameForStack(stack);
				tooltipText = new StringBuilder(String.format(
						"Slot %-4s: %s",
						i,
						StringTranslate.getInstance().translateNamedKey(
								stackName)));
				label = new Label(tooltipText.toString());

				tooltipText.append(String.format("\r\n\r\nInternal name: %s",
						stackName));
				addTick = Item.itemsList[i].hasSubtypes;
				
				BuildItemInfo(tooltipText,i,exists == 1);
			}
			
			WidgetSingleRow row = new WidgetSingleRow(200, 32);
			WidgetItem2DRender renderer = new WidgetItem2DRender(i);
			row.add(renderer, 32, 32);
			TextArea tooltip = GuiApiHelper.makeTextArea(tooltipText.toString(), false);
			if(addTick)
			{
				ModAction action = new ModAction(IDResolver.class, "TickIDSubItem", WidgetItem2DRender.class, TextArea.class,Label.class).setDefaultArguments(renderer,tooltip,label);
				action.setTag("SubItem Tick for " + tooltipText.subSequence(0, tooltipText.indexOf("\r\n")));
				if(mergedActions != null)
				{
					mergedActions = mergedActions.mergeAction(action);
				}
				else
				{
					mergedActions = action;
				}
			}
			label.setTooltipContent(tooltip);
			row.add(label);
			area.add(row);
		}

		WidgetSimplewindow window = new WidgetSimplewindow(area,
				"ID Resolver Status Report");
		if(mergedActions != null)
		{
		WidgetTick ticker = new WidgetTick();
		ticker.addCallback(mergedActions, 500);
		window.mainWidget.add(ticker);
		}
		window.backButton.setText("OK");
		GuiModScreen.show(window);
	}
	
	private static int GetPosition(int i)
	{
		int position = 0;
		if(i >= Block.blocksList.length)
		{
			position = 2;
		}
		else
		{
			if(i >= Item.shovelSteel.shiftedIndex)
			{
				position = 1;
			}
		}
		return position;
	}
	
	private static int GetExistance(int position,int i)
	{
		ItemStack stack = new ItemStack(i, 1, 0);
		int exists = 0;
		switch(position)
		{
		case 0:
		{
			if((Block.blocksList[i] != null) && (stack.getItem() != null))
			{
				exists = 1;
			}
			break;
		}
		case 1:
		{
			if((Block.blocksList[i] != null) && (stack.getItem() != null))
			{
				exists = 1;
			}
			else
			{
				if(Item.itemsList[i] != null)
				{
					exists = 2;
				}
			}
			break;
		}
		case 2:
		{
			if(Item.itemsList[i] != null)
			{
				exists = 2;
			}
			break;
		}
		}
		return exists;
	}
	
	private static String[] armorTypes = new String[]{"Helmet","Chestplate","Leggings","Boots"};
	
	private static void BuildItemInfo(StringBuilder builder,int index,boolean isBlock)
	{
		Item item = Item.itemsList[index];
		
		builder.append(String.format("\r\nSubitems: %s",
				item.hasSubtypes));
		
		builder.append(String.format("\r\nIs Block: %s",
				isBlock));
		
		if(!isBlock)
		{
			
			builder.append(String.format("\r\nClassname: %s",
					item.getClass().getName()));
		}
		else
		{
			builder.append(String.format("\r\nClassname: %s",
					Block.blocksList[index].getClass().getName()));
		}
		
		builder.append(String.format("\r\nMax stack: %s",
				item.getItemStackLimit()));
		builder.append(String.format(
				"\r\nDamage versus entities: %s",
				item.getDamageVsEntity(null)));
		builder.append(String.format("\r\nEnchantability: %s",
				item.getItemEnchantability()));
		builder.append(String.format("\r\nMax Damage: %s",
				item.getMaxDamage()));
		if(item instanceof ItemArmor)
		{
			ItemArmor armor = ((ItemArmor)item);
			builder.append(String.format("\r\nMax Damage Reduction: %s",
					armor.damageReduceAmount));
			builder.append(String.format("\r\nArmor Slot: %s",
					IDResolver.armorTypes[armor.armorType]));
		}
		
		if(item instanceof ItemFood)
		{
			ItemFood food = ((ItemFood)item);
			builder.append(String.format("\r\nHeal Amount: %s",
					food.getHealAmount()));
			builder.append(String.format("\r\nHunger Modifier: %s",
					food.getSaturationModifier()));
			builder.append(String.format("\r\nWolves enjoy: %s",
					food.isWolfsFavoriteMeat()));
		}
		
		if (isBlock) {
			Block block = Block.blocksList[index];
			builder.append(String.format("\r\nBlock Hardness: %s",
					block.getHardness()));
			builder.append(String.format(
					"\r\nBlock Slipperiness: %s",
					block.slipperiness));
			builder.append(String.format(
					"\r\nBlock Light Level: %s",
					Block.lightValue[index]));
			builder.append(String.format(
					"\r\nBlock Opacity: %s",
					Block.lightOpacity[index]));
		}
		
		if(idToMod != null)
		{
			if(idToMod.containsKey(index))
			{
				builder.append(String.format("\r\nMod: %s",
						idToMod.get(index)));
			}
		}
	}
	
	@SuppressWarnings("unused")
	private static void TickIDSubItem(WidgetItem2DRender renderer, TextArea textArea,Label label)
	{
		ItemStack stack = renderer.getRenderStack();
		int damage = stack.getItemDamage();
		damage++;
		if(damage > 15)
		{
			damage = 0;
		}
		stack.setItemDamage(damage);
		
		Item item = stack.getItem();
		String stackName = GetItemNameForStack(stack);
		StringBuilder tooltipText = new StringBuilder(String.format(
				"Slot %-4s : Metadata %s: %s",
				stack.itemID,damage,
				StringTranslate.getInstance().translateNamedKey(
						stackName)));

		tooltipText.append(String.format("\r\n\r\nInternal name: %s",
				stackName));
		
		BuildItemInfo(tooltipText,stack.itemID,GetExistance(GetPosition(stack.itemID),stack.itemID) == 1);
		
		GuiApiHelper.setTextAreaText(textArea, tooltipText.toString());
	}
	
	private static String GenerateIDStatusReport(int showFree) {
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
		
		String[] names = new String[]{"Free ","Block","Item "};
		
		String[] freeName = new String[]{"block", "block or item","item"};
		String[] freeNames = new String[]{"blocks", "blocks or items","items"};
		
		int freeSlotStart = -1;
		
		for (int i = 1; i < Item.itemsList.length; i++) {
			String itemName = null;
			String transName = null;
			String className = null;
			
			int position = IDResolver.GetPosition(i);
			int exists = IDResolver.GetExistance(position,i);
			
			switch(exists)
			{
			case 0:
			{
				if(showFree == 0)
				{
					continue;
				}
				if (freeSlotStart == -1) {
					freeSlotStart = i;
				}
				
				int next = i + 1;
				if(next != Item.itemsList.length)
				{
					if(showFree == 2)
					{
				int nextPosition = GetPosition(next);
				int nextExists = GetExistance(nextPosition,next);
				
				boolean generateRangeItem = (nextExists != 0);
				
				if (!generateRangeItem && (nextPosition == position)) {
					continue;
				}
					}
				}
				
				if (freeSlotStart != i) {
					reportIDs.append(
							String.format("%s %-8s - %-8s - This slot range of %s is open for any %s to use.", names[exists], freeSlotStart, i, i - freeSlotStart, freeNames[position])).append(
							linebreak);
				} else {
					
					reportIDs.append(
							String.format("%s %-8s - This slot is open for any %s to use.", names[exists], i, freeName[position])).append(
							linebreak);
				}
				freeSlotStart = -1;
				continue;
			}
			case 1:
			{
				Block block = Block.blocksList[i];
				totalRegisteredBlocks++;
				itemName = block.getBlockName();
				transName = StatCollector.translateToLocal(itemName + ".name");
				className = block.getClass().getName();
				break;
			}
			case 2:
			{
				if(checkClean && i < Block.blocksList.length)
				{
					totalUncleanBlockSlots++;
				}
				
				Item item = Item.itemsList[i];
				totalRegisteredItems++;
				itemName = item.getItemName();
				transName = StatCollector.translateToLocal(itemName
						+ ".name");
				className = item.getClass().getName();
				break;
			}
			}
			
			reportIDs.append(
					String.format("%s %-8s - %-31s - %-31s - %s", names[exists], i, itemName,
							transName, className)).append(
					linebreak);
		}
		report.append("Quick stats:").append(linebreak);
		report.append(
				String.format("Block ID Status: %d/%d used. %d available.",
						totalRegisteredBlocks, Block.blocksList.length,
						(Block.blocksList.length - totalUncleanBlockSlots) - totalRegisteredBlocks));
		if(checkClean)
		{
			report.append("(Unclean Block slots: ");
			report.append(totalUncleanBlockSlots);
			report.append(")" + linebreak);
		}
		else
		{
			report.append(linebreak);
		}
		report.append(
				String.format("Item ID Status: %d/%d used. %d available.",
						totalRegisteredItems, Item.itemsList.length,
						(Item.itemsList.length - Item.shovelSteel.shiftedIndex) - totalRegisteredItems)).append(linebreak)
				.append(linebreak);
		report.append(
				"Type  ID      - Name                           - Tooltip                        - Class")
				.append(linebreak);
		report.append(reportIDs.toString());
		return report.toString();
	}

	public static int GetConflictedBlockID(int RequestedID, Block newBlock) {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - 'GetConflictedBlockID' called.");

		if (!IDResolver.initialized) {
			IDResolver.logger
					.log(Level.INFO,
							"IDResolver - Not initialized. Returning to default behaviour.");
			return RequestedID;
		}

		if (newBlock == null) {

			IDResolver.logger
					.log(Level.INFO,
							"IDResolver - Conflict requested for null Block: Returning requested ID as there is likely another crash. Logging the stacktrace to display what mod is causing issues.");
			try {
				throw new Exception("Generating Stacktrace");
			} catch (Throwable e) {
				IDResolver.logger.log(Level.INFO, e.toString());
			}

			return RequestedID;
		}

		if (!IDResolver.IsModObject()) {
			IDResolver
					.GetLogger()
					.log(Level.INFO,
							"IDResolver - Detected Vanilla Block: Returning requested ID.");
			return RequestedID;
		}
		IDResolver resolver = new IDResolver(RequestedID, newBlock);
		IDResolver.GetLogger().log(
				Level.INFO,
				"IDResolver - Long name of requested block is "
						+ resolver.longName);
		resolver.SetupGui(RequestedID);
		return IDResolver.ConflictHelper(resolver);
	}

	public static int GetConflictedItemID(int RequestedID, Item newItem) {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - 'GetConflictedItemID' called.");

		if (!IDResolver.initialized) {
			IDResolver.logger
					.log(Level.INFO,
							"IDResolver - Not initialized. Returning to default behaviour.");
			return RequestedID;
		}

		if (newItem == null) {

			IDResolver.logger
					.log(Level.INFO,
							"IDResolver - Conflict requested for null Item: Returning requested ID as there is likely another crash somewhere else. Logging the stacktrace to display what mod is causing issues just in case.");
			try {
				throw new Exception("Generating Stacktrace");
			} catch (Throwable e) {
				IDResolver.logger.log(Level.INFO, e.toString());
			}

			return RequestedID;
		}

		if (!IDResolver.IsModObject()) {
			IDResolver
					.GetLogger()
					.log(Level.INFO,
							"IDResolver - Detected Vanilla Item: Returning requested ID.");
			return RequestedID;
		}
		IDResolver resolver = new IDResolver(RequestedID, newItem);
		IDResolver.GetLogger().log(
				Level.INFO,
				"IDResolver - Long name of requested item is "
						+ resolver.longName);
		resolver.SetupGui(RequestedID);
		return IDResolver.ConflictHelper(resolver);
	}

	public static String GetExtraInfo() {
		return IDResolver.extraInfo;
	}

	private static int GetFirstOpenBlock() {
		for (int i = 1; i < Block.blocksList.length; i++) {
			if ((Block.blocksList[i] != null)
					|| IDResolver.IntHasStoredID(i, true)) {
				continue;
			}
			return i;
		}
		return -1;
	}

	private static int GetFirstOpenItem() {
		for (int i = Block.blocksList.length; i < Item.itemsList.length; i++) {
			if ((Item.itemsList[i] != null)
					|| IDResolver.IntHasStoredID(i, false)) {
				continue;
			}
			return i;
		}
		return -1;
	}

	/**
	 * Just a helper method.
	 * 
	 * @param input the key to split up
	 * @return an array: ID | BaseMod. USED TO BE Class|BaseMod|ID
	 */
	private static String[] GetInfoFromSaveString(String input) {
		return input.split("[|]");
	}

	private static String GetItemNameForStack(ItemStack stack) {
		if(stack.getItem() == null)
		{
			return "";
		}
		return stack.getItem().getItemNameIS(stack);
	}

	private static int GetLastOpenBlock() {
		for (int i = Block.blocksList.length - 1; i >= 1; i--) {
			if ((Block.blocksList[i] != null)
					|| IDResolver.IntHasStoredID(i, true)) {
				continue;
			}
			return i;
		}
		return -1;
	}

	private static int GetLastOpenItem() {
		for (int i = Item.itemsList.length - 1; i >= Block.blocksList.length; i--) {
			if ((Item.itemsList[i] != null)
					|| IDResolver.IntHasStoredID(i, false)) {
				continue;
			}
			return i;
		}
		return -1;
	}

	public static Logger GetLogger() {
		return IDResolver.logger;
	}

	private static String GetLongBlockName(Block block, int originalrequestedID) {
		String name = Integer.toString(originalrequestedID) + "|";
		StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
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
		}
		if (bestguess == -1) {
			name += "IDRESOLVER_UNKNOWN_BLOCK_" + block.getClass().getName();
		} else {
			name += IDResolver.TrimMCP(stacktrace[bestguess].getClassName());
		}
		return name;
	}

	private static String GetLongItemName(Item item, int originalrequestedID) {
		String name = Integer.toString(originalrequestedID) + "|";
		StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
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
		}
		if (bestguess == -1) {
			name += "IDRESOLVER_UNKNOWN_BLOCK_" + item.getClass().getName();
		} else {
			name += IDResolver.TrimMCP(stacktrace[bestguess].getClassName());
		}
		return name;
	}

	private static String GetlongName(Object obj, int ID) {
		String name = null;
		if (obj instanceof Block) {
			name = "BlockID." + IDResolver.GetLongBlockName((Block) obj, ID);
		}
		if (obj instanceof Item) {
			name = "ItemID." + IDResolver.GetLongItemName((Item) obj, ID);
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

	private static int GetModPriority(String modname) {
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
		return 0;
	}

	private static String GetStoredIDName(int ID, boolean block) {
		return IDResolver.GetStoredIDName(ID, block, true);
	}

	private static String GetStoredIDName(int ID, boolean block, boolean trim) {
		for (Map.Entry<Object, Object> entry : IDResolver.knownIDs.entrySet()) {
			String key = ((String) entry.getKey());
			if (key.startsWith("PRIORITY|")) {
				continue;
			}
			if("SAVEVERSION".equals(key))
				continue;
			
			if (ID == Integer.parseInt((String) entry.getValue())) {
				return trim ? IDResolver.TrimType(key, block) : key;
			}
		}
		return null;
	}

	private static String GetTypeName(Boolean block) {
		return (block ? " Block" : " Item").substring(1);
	}

	public static boolean HasStoredID(int ID, boolean block) {
		if (block && !IDResolver.wasBlockInited) {
			IDResolver.wasBlockInited = true;
		} else if (!block && !IDResolver.wasItemInited) {
			IDResolver.wasItemInited = true;
		}
		return IDResolver.IntHasStoredID(ID, block) | IDResolver.IsModObject();
	}

	@SuppressWarnings("unused")
	private static void IgnoreLooseDetectionAndDisable() {
		IDResolver.DisableLooseSettingsCheck();
	}

	private static boolean IntHasStoredID(int ID, boolean block) {
		if (IDResolver.initialized) {
			if (IDResolver.knownIDs.containsValue(Integer.toString(ID))) {
				return IDResolver.GetStoredIDName(ID, block) != null;
			}
		}
		return false;
	}

	private static boolean IsBlockType(String input) {
		if (input.startsWith("BlockID.")) {
			return true;
		}
		if (input.startsWith("ItemID.")) {
			return false;
		}
		throw new InvalidParameterException("Input is not fully named!");
	}

	private static Boolean IsModObject() {
		StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
		for (int i = 1; i < stacktrace.length; i++) {
			try {
				if (BaseMod.class.isAssignableFrom(Class.forName(stacktrace[i]
						.getClassName()))) {
					return true;
				}
			} catch (Throwable e) {
				// Should never happen, but in this case just going to coast
				// right over it.
			}
		}
		return false;
	}

	@SuppressWarnings("unused")
	private static void LowerModPriorityFromMenu(String modName,
			SimpleTextAreaModel textarea) {
		IDResolver.GetLogger().log(
				Level.INFO,
				"IDResolver - User pressed 'LowerModPriorityFromMenu' button with "
						+ modName);
		int intlevel = IDResolver.GetModPriority(modName);
		if (intlevel > 0) {
			intlevel--;
		}
		String newlevel = Integer.toString(intlevel);
		IDResolver.modPriorities.setProperty(modName, newlevel);
		textarea.setText(modName + " - Currently at Priority Level " + newlevel);
		try {
			IDResolver.StoreProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO, "Could not save properties", e);
		}
	}

	private static void OverrideBlockID(int newid, int oldid) {
		Block oldblock = Block.blocksList[newid];
		Block.blocksList[newid] = null;
		if (oldblock != null) {
			try {
				IDResolver.blockidfield.set(oldblock, oldid);
			} catch (Throwable e) {
				IDResolver.logger.log(Level.INFO,
						"IDResolver - Unable to override blockID!", e);
				throw new IllegalArgumentException(
						"IDResolver - Unable to override blockID!", e);
			}
		}

		Boolean tempBoolean = Block.opaqueCubeLookup[newid];
		Block.opaqueCubeLookup[newid] = Block.opaqueCubeLookup[oldid];
		Block.opaqueCubeLookup[oldid] = tempBoolean;

		tempBoolean = Block.canBlockGrass[newid];
		Block.canBlockGrass[newid] = Block.canBlockGrass[oldid];
		Block.canBlockGrass[oldid] = tempBoolean;

		tempBoolean = Block.requiresSelfNotify[newid];
		Block.requiresSelfNotify[newid] = Block.requiresSelfNotify[oldid];
		Block.requiresSelfNotify[oldid] = tempBoolean;

		tempBoolean = Block.useNeighborBrightness[newid];
		Block.useNeighborBrightness[newid] = Block.useNeighborBrightness[oldid];
		Block.useNeighborBrightness[oldid] = tempBoolean;

		int tempInt = Block.lightValue[newid];
		Block.lightValue[newid] = Block.lightValue[oldid];
		Block.lightValue[oldid] = tempInt;

		tempInt = Block.lightOpacity[newid];
		Block.lightOpacity[newid] = Block.lightOpacity[oldid];
		Block.lightOpacity[oldid] = tempInt;

		Item oldblockitem = Item.itemsList[newid];
		Item.itemsList[newid] = null;
		if (oldblockitem != null) {
			try {
				IDResolver.itemidfield.set(oldblockitem, oldid);
			} catch (Throwable e) {
				IDResolver.logger
						.log(Level.INFO,
								"IDResolver - Unable to override itemID for the block's item!",
								e);
				throw new IllegalArgumentException(
						"IDResolver - Unable to override itemID for the block's item!",
						e);
			}
		}
		Block.blocksList[oldid] = oldblock;
		Item.itemsList[oldid] = oldblockitem;
		idToMod = null;
	}

	private static void OverrideItemID(int newid, int oldid) {
		Item olditem = Item.itemsList[newid];
		Item.itemsList[newid] = null;
		if (olditem != null) {
			try {
				IDResolver.itemidfield.set(olditem, oldid);
			} catch (Throwable e) {
				IDResolver.logger.log(Level.INFO,
						"IDResolver - Unable to override itemID!", e);
				throw new IllegalArgumentException(
						"IDResolver - Unable to override itemID!", e);
			}
		}
		Item.itemsList[oldid] = olditem;
		idToMod = null;
	}

	private static String RaiseModPriority(String modname) {
		String newlevel = Integer
				.toString(IDResolver.GetModPriority(modname) + 1);
		IDResolver.modPriorities.setProperty(modname, newlevel);
		return newlevel;
	}

	@SuppressWarnings("unused")
	private static void RaiseModPriorityFromMenu(String modName,
			SimpleTextAreaModel textarea) {
		IDResolver.GetLogger().log(
				Level.INFO,
				"IDResolver - User pressed 'RaiseModPriorityFromMenu' button with "
						+ modName);
		textarea.setText(modName + " - Currently at Priority Level "
				+ IDResolver.RaiseModPriority(modName));
		try {
			IDResolver.StoreProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO, "Could not save properties", e);
		}
	}
	
	private static void ConvertIDRSaveOne()
	{
		Properties oldIDs = (Properties) IDResolver.knownIDs.clone();
		IDResolver.knownIDs.clear();
		
		for (Entry<Object, Object> entry : oldIDs.entrySet())
		{
			String key = (String)entry.getKey();
			String[] info = GetInfoFromSaveString(key);
			IDResolver.knownIDs.put((IsBlockType(key) ? "BlockID." : "ItemID.") + info[2] + "|" + info[1], entry.getValue());
		}
	}

	private static void ReloadIDs() {
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
						"IDResolver - IDs File not found, creating new one.");
				InputStream stream = IDResolver.class.getClassLoader()
						.getResourceAsStream("IDResolverDefaultIDs.properties");
				if (stream != null) {
					IDResolver.knownIDs.load(stream);
					IDResolver.logger.log(
							Level.INFO,
							"IDResolver - Found defaults file, loaded "
									+ Integer.toString(IDResolver.knownIDs
											.size()) + " IDs sucessfully.");
					stream.close();
					
					String version = IDResolver.knownIDs.getProperty("SAVEVERSION");
					
					if(version == null)
					{
						IDResolver.logger.log(Level.INFO,"IDResolver - Settings file is v1, but ID resolver now uses v2. Converting.");
						ConvertIDRSaveOne();
						IDResolver.logger.log(Level.INFO,"IDResolver - Settings file convertion complete.");
						forceSave = true;
					}
				}
			} else {
				try {
					FileInputStream stream = new FileInputStream(
							IDResolver.idPath);
					IDResolver.knownIDs.load(stream);
					stream.close();
					IDResolver.logger.log(Level.INFO, "IDResolver - Loaded "
							+ Integer.toString(IDResolver.knownIDs.size())
							+ " IDs sucessfully.");
					
					String version = IDResolver.knownIDs.getProperty("SAVEVERSION");
					
					if(version == null)
					{
						IDResolver.logger.log(Level.INFO,"IDResolver - Settings file is v1, but ID resolver now uses v2. Converting.");
						ConvertIDRSaveOne();
						IDResolver.logger.log(Level.INFO,"IDResolver - Settings file convertion complete.");
						forceSave = true;
					}
					
				} catch (IOException e) {
					IDResolver.logger
							.log(Level.INFO,
									"IDResolver - Existing config details are invalid: Creating new settings.");
				}
			}
			if (IDResolver.priorityPath.createNewFile()) {
				IDResolver.logger
						.log(Level.INFO,
								"IDResolver - Priorities File not found, creating new one.");
				InputStream stream = IDResolver.class.getClassLoader()
						.getResourceAsStream(
								"IDResolverDefaultmodPriorities.properties");
				if (stream != null) {
					IDResolver.modPriorities.load(stream);
					IDResolver.logger.log(
							Level.INFO,
							"IDResolver - Found defaults file, loaded "
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
							"IDResolver - Loaded "
									+ Integer.toString(IDResolver.modPriorities
											.size() - negatives)
									+ " Mod Priorities sucessfully.");
				} catch (IOException e) {
					IDResolver.logger
							.log(Level.INFO,
									"IDResolver - Existing config details are invalid: Creating new settings.");
				}
			}
			if ((IDResolver.showTickMM == null)
					| (IDResolver.showTickRS == null)
					| (IDResolver.showOnlyConf == null)
					| (IDResolver.checkForLooseSettings == null)) {
				IDResolver.showTickMM = new SettingBoolean("ShowTickMM",
						IDResolver.ShowTick(true));
				IDResolver.showTickRS = new SettingBoolean("ShowTickRS",
						IDResolver.ShowTick(false));
				IDResolver.checkForLooseSettings = new SettingBoolean(
						"CheckForLooseSettings",
						IDResolver.CheckForLooseSettings());
				IDResolver.showOnlyConf = new SettingBoolean(
						"ShowOnlyConflicts", IDResolver.ShowOnlyConflicts());
			}
			IDResolver.initialized = true;
			IDResolver.UpdateTickSettings();
			
			if(forceSave)
			{
				IDResolver.logger.log(Level.INFO,"IDResolver - Saving as changes were made.");
				StoreProperties();
			}
			
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO,
					"IDResolver - Error while initalizing settings.", e);
			IDResolver.initialized = false;
		}
	}

	public static void ReLoadModGui() {
		IDResolver.ReloadIDs();
		
		IDResolver.modscreen.widgetColumn.removeAllChildren();
		Map<String, Vector<String>> IDmap = new HashMap<String, Vector<String>>();
		for (Map.Entry<Object, Object> entry : IDResolver.knownIDs.entrySet()) {
			String key = (String) entry.getKey();
			
			if("SAVEVERSION".equals(key))
				continue;
			String[] info = IDResolver.GetInfoFromSaveString(key);
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
		StringTranslate translate = StringTranslate.getInstance();
		for (Entry<String, Vector<String>> entry : IDmap.entrySet()) {
			WidgetSinglecolumn window = new WidgetSinglecolumn();
			window.childDefaultWidth = 300;
			SimpleTextAreaModel textarea = new SimpleTextAreaModel();
			textarea.setText(entry.getKey() + " - Currently at Priority Level "
					+ IDResolver.GetModPriority(entry.getKey()), false);
			TextArea textWidget = new TextArea(textarea);
			window.add(textWidget);
			window.heightOverrideExceptions.put(textWidget, 0);
			window.add(GuiApiHelper.makeButton("Raise Priority of this Mod",
					"RaiseModPriorityFromMenu", IDResolver.class, true,
					new Class[] { String.class, SimpleTextAreaModel.class },
					entry.getKey(), textarea));
			window.add(GuiApiHelper.makeButton("Lower Priority of this Mod",
					"LowerModPriorityFromMenu", IDResolver.class, true,
					new Class[] { String.class, SimpleTextAreaModel.class },
					entry.getKey(), textarea));
			window.add(GuiApiHelper.makeButton("Wipe saved IDs of this mod",
					"WipeSavedIDsFromMenu", IDResolver.class, true,
					new Class[] { String.class }, entry.getKey()));
			for (String IDEntry : entry.getValue()) {
				int x = Integer.parseInt(IDResolver.knownIDs
						.getProperty(IDEntry));
				String name = null;
				ItemStack stack = null;
				Boolean isBlock = IDResolver.IsBlockType(IDEntry);
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
					name = IDResolver.GetItemNameForStack(stack);
				} catch (Throwable e) {
					stack = null;
					name = null;
				}
				if (stack != null) {
					if ((name != null) && (name.length() != 0)) {
						name = translate.translateNamedKey(name);
						if ((name == null) || (name.length() == 0)) {
							name = IDResolver.GetItemNameForStack(stack);
						}
					}
					if ((name == null) || (name.length() == 0)) {
						String originalpos = IDResolver
								.GetInfoFromSaveString(IDEntry)[0];
						if (isBlock) {
							name = "Unnamed "
									+ IDResolver.TrimMCP(Block.blocksList[x]
											.getClass().getName())
									+ " originally at " + originalpos;
						} else {
							name = "Unnamed "
									+ IDResolver.TrimMCP(Item.itemsList[x]
											.getClass().getName())
									+ " originally at " + originalpos;
						}
					}
				} else {
					String[] info = IDResolver.GetInfoFromSaveString(IDEntry);
					name = "Loose setting for "
							+ (isBlock ? "Block '" : "Item with original ID ") + info[0]
							+ " loaded from " + info[1];
				}
				window.add(GuiApiHelper.makeButton("Edit ID for " + name,
						"ResolveNewID", IDResolver.class, true,
						new Class[] { String.class }, IDEntry));
			}
			IDResolver.windows[id] = new WidgetSimplewindow(window,
					"Config IDs for " + entry.getKey());
			IDResolver.modscreen.widgetColumn.add(GuiApiHelper.makeButton(
					"View IDs for " + entry.getKey(), "ShowMenu",
					IDResolver.class, true, new Class[] { Integer.class }, id));
			id++;
		}
		IDResolver.UpdateTickSettings();
		Runnable callback = new ModAction(IDResolver.class,
				"UpdateTickSettings");
		WidgetBoolean TickMWidget = new WidgetBoolean(IDResolver.showTickMM,
				"Show ID info on Main Menu");
		TickMWidget.button.addCallback(callback);
		IDResolver.modscreen.widgetColumn.add(TickMWidget);
		TickMWidget = new WidgetBoolean(IDResolver.showTickRS,
				"Show ID info on Resolve Screen");
		TickMWidget.button.addCallback(callback);
		IDResolver.modscreen.widgetColumn.add(TickMWidget);
		TickMWidget = new WidgetBoolean(IDResolver.checkForLooseSettings,
				"Check for Loose settings");
		TickMWidget.button.addCallback(callback);
		IDResolver.modscreen.widgetColumn.add(TickMWidget);
		TickMWidget = new WidgetBoolean(IDResolver.showOnlyConf,
				"Only Show Conflicts");
		TickMWidget.button.addCallback(callback);
		IDResolver.modscreen.widgetColumn.add(TickMWidget);
		IDResolver.modscreen.widgetColumn.add(GuiApiHelper.makeButton("Wipe ALL Saved IDs", "ClearAllIDs",
				IDResolver.class, true));
		IDResolver.modscreen.widgetColumn.add(GuiApiHelper.makeButton("Check for Loose IDs",
				"CheckLooseIDs", IDResolver.class, true));
		
		IDResolver.modscreen.widgetColumn.add(GuiApiHelper.makeButton("Generate ID Status Report",
				"SaveIDStatusToFile", IDResolver.class, true,new Class[]{Integer.class},0));
		
		IDResolver.modscreen.widgetColumn.add(GuiApiHelper.makeButton("Generate ID Status Report with Expanded Free IDs",
				"SaveIDStatusToFile", IDResolver.class, true,new Class[]{Integer.class},1));
		
		IDResolver.modscreen.widgetColumn.add(GuiApiHelper.makeButton("Generate ID Status Report with Collapsed Free IDs",
				"SaveIDStatusToFile", IDResolver.class, true,new Class[]{Integer.class},2));
		

		IDResolver.modscreen.widgetColumn.add(GuiApiHelper.makeButton("Display ID Status Report",
				"DisplayIDStatus", IDResolver.class, true));

		IDResolver.modscreen.widgetColumn.add(GuiApiHelper.makeButton("Reload Options",
				"ReLoadModGuiAndRefresh", IDResolver.class, true));
	}

	@SuppressWarnings("unused")
	private static void ReLoadModGuiAndRefresh() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'ReLoadModGuiAndRefresh' button.");
		GuiModScreen.back();
		IDResolver.ReLoadModGui();
		GuiModScreen.show(IDResolver.modscreen.theWidget);
	}

	@SuppressWarnings("unused")
	private static void RemoveEntry(String key) {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'RemoveEntry' button for " + key);
		IDResolver.knownIDs.remove(key);
		try {
			IDResolver.StoreProperties();
			IDResolver.logger.log(Level.INFO,
					"IDResolver - Removed the saved ID for " + key
							+ " as per use request via Settings screen.");
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO,
					"IDResolver - Was unable to remove the saved ID for " + key
							+ " due to an exception.", e);
		}
		IDResolver.ReLoadModGui();
	}

	@SuppressWarnings("unused")
	private static void RemoveLooseSetting(SettingList setting) {
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
			IDResolver.StoreProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO, "Could not save properties", e);
		}
		IDResolver.ReLoadModGui();
	}

	public static void RemoveSettingByKey(String key) {
		if (IDResolver.knownIDs.containsKey(key)) {
			IDResolver.knownIDs.remove(key);
		}
	}

	@SuppressWarnings("unused")
	private static void ResolveNewID(String key) {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'ResolveNewID' button for " + key);
		if (!IDResolver.knownIDs.containsKey(key)) {
			return;
		}
		String trimmedKey = TrimType(key, IDResolver.IsBlockType(key));
		IDResolver resolver = new IDResolver(Integer.parseInt(IDResolver
				.GetInfoFromSaveString(trimmedKey)[0]), key);
		resolver.disableAutoAll = true;
		resolver.isMenu = true;
		resolver.SetupGui(Integer.parseInt(IDResolver.knownIDs.getProperty(key)));
		resolver.RunConflictMenu();
	}
	
	@SuppressWarnings("unused")
	private static void LinkCallback(String url)
	{
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed link. URL is: " + url);
		File file = new File(url);
		try {
			Desktop.getDesktop().open(file);
		}
		catch (Throwable e) {
			e.printStackTrace();
			Sys.openURL(file.toURI().toString());
		}
	}
	
	@SuppressWarnings("unused")
	private static void SaveIDStatusToFile(Integer showFree) {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'SaveIDStatusToFile' button. Mode: " + showFree.toString());
		File savePath = new File(new File(Minecraft.getMinecraftDir(), "ID Status.txt").getAbsolutePath().replace("\\.\\", "\\"));
		try {
			FileOutputStream output = new FileOutputStream(savePath);
			output.write(IDResolver.GenerateIDStatusReport(showFree).getBytes());
			output.flush();
			output.close();
			
			WidgetSinglecolumn widget = new WidgetSinglecolumn(new Widget[0]);
			TextArea area = GuiApiHelper.makeTextArea(String.format("Saved ID status report to <a href=\"%1$s\">%1$s</a>", savePath), true);
			area.addCallback(new ModAction(IDResolver.class,"LinkCallback","Link Clicked Callback",String.class));
			widget.add(area);
			widget.overrideHeight = false;
			WidgetSimplewindow window = new WidgetSimplewindow(widget, "ID Resolver");
			window.backButton.setText("OK");
			
			
			GuiModScreen.show(window);
		} catch (Throwable e) {
			IDResolver.GetLogger().log(Level.INFO,
					"IDResolver - Exception when saving ID Status to file.", e);
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
	
	private static void SetupOverrides() {
		int pubfinalmod = Modifier.FINAL + Modifier.PUBLIC;
		try {
			for (Field field : Block.class.getFields()) {
				if ((field.getModifiers() == pubfinalmod)
						&& (field.getType() == int.class)) {
					IDResolver.blockidfield = field;
					break;
				}
			}
		} catch (Throwable e3) {
			IDResolver.overridesenabled = false;
		}

		try {
			for (Field field : Item.class.getFields()) {
				if ((field.getModifiers() == pubfinalmod)
						&& (field.getType() == int.class)) {
					IDResolver.itemidfield = field;
					break;
				}
			}
		} catch (Throwable e3) {
			IDResolver.overridesenabled = false;
		}
		if (IDResolver.overridesenabled) {
			IDResolver.blockidfield.setAccessible(true);
			IDResolver.itemidfield.setAccessible(true);
		}
	}

	@SuppressWarnings("unused")
	private static void ShowMenu(Integer i) {
		IDResolver.GetLogger().log(
				Level.INFO,
				"IDResolver - User pressed 'ShowMenu' button for "
						+ i.toString() + " aka "
						+ IDResolver.windows[i].titleWidget.getText());
		GuiModScreen.clicksound();
		GuiModScreen.show(IDResolver.windows[i]);
	}

	private static boolean ShowOnlyConflicts() {
		if (!IDResolver.modPriorities.containsKey("ShowOnlyConflicts")) {
			IDResolver.modPriorities.setProperty("ShowOnlyConflicts", "true");
			try {
				IDResolver.StoreProperties();
			} catch (Throwable e) {
				IDResolver.logger
						.log(Level.INFO,
								"Could not save properties after adding ShowOnlyConflicts option!",
								e);
			}
			return true;
		}
		return IDResolver.modPriorities.getProperty("ShowOnlyConflicts")
				.equalsIgnoreCase("true");
	}

	public static boolean ShowTick(boolean mainmenu) {
		String key = mainmenu ? "ShowTickMM" : "ShowTickRS";
		if (!IDResolver.modPriorities.containsKey(key)) {
			IDResolver.modPriorities.setProperty(key, "true");
			try {
				IDResolver.StoreProperties();
			} catch (Throwable e) {
				IDResolver.logger.log(Level.INFO,
						"Could not save properties after adding " + key
								+ " option!", e);
			}
			return true;
		}
		return IDResolver.modPriorities.getProperty(key).equalsIgnoreCase(
				"true");
	}

	private static void StoreProperties() throws FileNotFoundException,
			IOException {
		FileOutputStream stream = new FileOutputStream(IDResolver.idPath);
		IDResolver.knownIDs.setProperty("SAVEVERSION","v2");
		IDResolver.knownIDs.store(stream, IDResolver.settingsComment);
		stream.close();
		stream = new FileOutputStream(IDResolver.priorityPath);
		IDResolver.modPriorities.store(stream, IDResolver.settingsComment);
		stream.close();
	}

	@SuppressWarnings("unused")
	private static void TrimLooseSettings(ArrayList<String> unused) {
		WidgetSinglecolumn widgetSingleColumn = new WidgetSinglecolumn();
		widgetSingleColumn.childDefaultWidth = 250;
		SettingList s = new SettingList("unusedSettings", unused);
		WidgetList w = new WidgetList(s, "Loose Settings to Remove");
		w.listBox.setSelected(0);
		widgetSingleColumn.add(w);
		widgetSingleColumn.heightOverrideExceptions.put(w, 140);
		widgetSingleColumn.add(GuiApiHelper.makeButton("Remove Selected",
				"RemoveLooseSetting", IDResolver.class, true,
				new Class[] { SettingList.class }, s));

		GuiModScreen.show(new WidgetSimplewindow(widgetSingleColumn,
				"Loose Setting Removal"));
	}

	@SuppressWarnings("unused")
	private static void TrimLooseSettingsAll(ArrayList<String> unused) {
		for (String key : unused) {
			IDResolver.knownIDs.remove(key);
			IDResolver.logger.log(Level.INFO, "Trimmed ID: " + key);
		}
		try {
			IDResolver.StoreProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO,
					"Could not save properties after trimming loose settings!",
					e);
		}
		IDResolver.ReLoadModGui();
	}

	@SuppressWarnings("unused")
	private static void TrimLooseSettingsAuto(ArrayList<String> unused) {
		Map<String, Boolean> classMap = new HashMap<String, Boolean>();
		ArrayList<Class> loadedMods = new ArrayList<Class>(ModLoader
				.getLoadedMods().size());
		for (Iterator iterator = ModLoader.getLoadedMods().iterator(); iterator
				.hasNext();) {
			loadedMods.add(iterator.next().getClass());
		}
		Boolean isMCP = IDResolver.class.getName().startsWith(
				"net.minecraft.src.");
		for (String key : unused) {
			String[] info = IDResolver.GetInfoFromSaveString(key);
			String classname = info[1];
			if (!classMap.containsKey(classname)) {
				try {
					Class modClass;
					if (isMCP) {
						modClass = Class.forName("net.minecraft.src."
								+ classname);
					} else {
						modClass = Class.forName(classname);
					}
					if (!loadedMods.contains(modClass)) {
						IDResolver
								.AppendExtraInfo("Unsure if I should trim IDs from "
										+ classname
										+ ": Class file is still found, but the mod is not loaded into ModLoader! If you wish to trim these IDs, please remove them with the Settings screen.");
					}
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
			IDResolver.StoreProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO,
					"Could not save properties after trimming loose settings!",
					e);
		}
		IDResolver.ReLoadModGui();
	}

	private static String TrimMCP(String name) {
		if (name.startsWith("net.minecraft.src")) {
			name = name.substring(18);
		}
		return name;
	}

	private static String TrimType(String input, boolean block) {
		String type = (block ? "BlockID." : "ItemID.");
		if (input.startsWith(type)) {
			return input.substring(type.length());
		}
		return input;
	}

	private static void UpdateTickSettings() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - 'UpdateTickSettings' called.");
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
		try {
			IDResolver.StoreProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO,
					"IDResolver - Was unable to save settings.", e);
		}
	}

	public static Boolean WasBlockInited() {
		return IDResolver.wasBlockInited;
	}

	public static Boolean WasItemInited() {
		return IDResolver.wasItemInited;
	}

	@SuppressWarnings("unused")
	private static void WipeSavedIDsFromMenu(String modName) {
		IDResolver.GetLogger().log(
				Level.INFO,
				"IDResolver - User pressed 'WipeSavedIDsFromMenu' button with "
						+ modName);
		Vector<String> temp = new Vector<String>();
		for (Object key : IDResolver.knownIDs.keySet()) {
			temp.add((String) key);
		}
		for (String key : temp) {
			if (IDResolver.GetInfoFromSaveString(key)[1].equals(modName)) {
				IDResolver.knownIDs.remove(key);
			}
		}
		try {
			IDResolver.StoreProperties();
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
	private Button resolveScreenOverride;
	private boolean running = false;

	private SettingInt settingIntNewID;

	private boolean specialItem = false;

	private Widget subscreenIDSetter;

	private IDResolver(int Startid, Block Offender) {
		if (IDResolver.initialized) {
			isBlock = true;
			requestedBlock = Offender;
		}
		originalID = Startid;
		longName = IDResolver.GetlongName(requestedBlock, originalID);
	}

	private IDResolver(int Startid, Item Offender) {
		if (IDResolver.initialized) {
			isBlock = false;
			if (Offender instanceof ItemBlock) {
				specialItem = true;
			}
			requestedItem = Offender;
		}
		originalID = Startid;
		longName = IDResolver.GetlongName(requestedItem, originalID);
	}

	private IDResolver(int currentID, String savedname) {
		if (IDResolver.initialized) {
			isBlock = IDResolver.IsBlockType(savedname);
			String[] info = IDResolver.GetInfoFromSaveString(IDResolver
					.TrimType(savedname, isBlock));
			overrideName = "ID " + info[0] + " from " + info[1];
			longName = savedname;
			originalID = currentID;
		}
	}

	private IDResolver(String name, boolean block) {
		if (IDResolver.initialized) {
			isBlock = block;
			String[] info = IDResolver.GetInfoFromSaveString(name);
			overrideName = "ID " + info[0] + " from " + info[1];
		}
	}

	@SuppressWarnings("unused")
	private void AutoAssign() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'AutoAssign' button.");
		AutoAssign(false, false);
	}

	private void AutoAssign(boolean skipMessage, boolean reverse) {
		IDResolver.GetLogger().log(
				Level.INFO,
				"IDResolver - Automatically assigning ID: Skip Message: "
						+ skipMessage + " Reverse: " + reverse);
		int firstid = (isBlock ? (reverse ? IDResolver.GetLastOpenBlock()
				: IDResolver.GetFirstOpenBlock()) : (reverse ? IDResolver
				.GetLastOpenItem() : IDResolver.GetFirstOpenItem()));
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - Automatic assign returned new ID " + firstid);
		if (firstid == -1) {
			return;
		}
		settingIntNewID.set(firstid);
		if (skipMessage) {
			running = false;
		} else {
			DisplayMessage("Automatically assigned ID "
					+ Integer.toString(firstid) + " for " + GetName());
		}
	}

	@SuppressWarnings("unused")
	private void AutoAssignAll() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'AutoAssignAll' button.");
		IDResolver.autoAssignMod = IDResolver.GetInfoFromSaveString(longName)[1];
		AutoAssign(true, false);
		DisplayMessage("Automatically assigning IDs...");
	}

	@SuppressWarnings("unused")
	private void AutoAssignAllRev() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'AutoAssignAllRev' button.");
		IDResolver.autoAssignMod = "!"
				+ IDResolver.GetInfoFromSaveString(longName)[1];
		AutoAssign(true, true);
		DisplayMessage("Automatically assigning IDs...");
	}

	@SuppressWarnings("unused")
	private void AutoAssignRev() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'AutoAssignRev' button.");
		AutoAssign(false, true);
	}

	@SuppressWarnings("unused")
	private void Cancel() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'Cancel' button.");
		settingIntNewID = null;
		running = false;
	}

	private void DisplayMessage(String msg) {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - Message Displayed: " + msg);
		oldsubscreenIDSetter = subscreenIDSetter;
		
		
		
		WidgetSinglecolumn column =  new WidgetSinglecolumn(new Widget[0]);
		{
			TextArea textarea = GuiApiHelper.makeTextArea(msg, false);
			column.add(textarea);
			column.heightOverrideExceptions.put(textarea, 0);
			column.add(GuiApiHelper.makeButton("Continue", "Finish", this, true));
		}
		
		WidgetSimplewindow window = new WidgetSimplewindow(column,"ID Resolver Notice",false);
		
		WidgetTick ticker = new WidgetTick();
		window.add(ticker);
		ticker.addTimedCallback(new ModAction(this,"PreviousScreen"),5000);
		
		subscreenIDSetter = window;
		
		GuiWidgetScreen screen = GuiWidgetScreen.getInstance();
		screen.resetScreen();
		screen.setScreen(subscreenIDSetter);
		
		LoadBackground(screen);
	}
	
	@SuppressWarnings("unused")
	private void PreviousScreen()
	{
		subscreenIDSetter = oldsubscreenIDSetter;
		running = false;
	}

	@SuppressWarnings("unused")
	private void Finish() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'Finish' button.");
		running = false;
	}

	private String GetBlockName(Block block) {
		try {
			String name = StringTranslate.getInstance().translateNamedKey(
					IDResolver.GetItemNameForStack(new ItemStack(block)));
			if ((name != null) && (name.length() != 0)) {
				return name;
			}
			if (!IDResolver.IntHasStoredID(block.blockID, true)) {
				return IDResolver.GetItemNameForStack(new ItemStack(block));
			}
		} catch (Throwable e) {
		}
		String[] info = IDResolver.GetInfoFromSaveString((IDResolver
				.IntHasStoredID(block.blockID, true) ? IDResolver
				.GetStoredIDName(block.blockID, true, true) : IDResolver
				.GetLongBlockName(block, originalID)));
		return "ID " + info[0] + " (Class: " + block.getClass().getName() + ") from " + info[1];
	}

	private String GetItemName(Item item) {
		try {
			String name = StringTranslate.getInstance().translateNamedKey(
					IDResolver.GetItemNameForStack(new ItemStack(item)));
			if ((name != null) && (name.length() != 0)) {
				return name;
			}
			if (!IDResolver.IntHasStoredID(item.shiftedIndex, true)) {
				return IDResolver.GetItemNameForStack(new ItemStack(item));
			}
		} catch (Throwable e) {
		}
		String[] info = IDResolver.GetInfoFromSaveString((IDResolver
				.IntHasStoredID(item.shiftedIndex, false) ? IDResolver
				.GetStoredIDName(item.shiftedIndex, false, true) : IDResolver
				.GetLongItemName(item, originalID)));
		return "ID " + info[0] + " (Class: " + item.getClass().getName() + ") from " + info[1];
	}

	private String GetName() {
		String name = "";
		if (overrideName != null) {
			return overrideName;
		}
		if (isBlock) {
			if (requestedBlock != null) {
				name = GetBlockName(requestedBlock);
			} else {
				String[] info = IDResolver.GetInfoFromSaveString(IDResolver
						.GetStoredIDName(originalID, true));
				name = "ID " + info[0] + " from " + info[1];
			}
		} else {
			if (requestedItem != null) {
				name = GetItemName(requestedItem);
			} else {
				String[] info = IDResolver.GetInfoFromSaveString(IDResolver
						.GetStoredIDName(originalID, false));
				name = "ID " + info[0] + " from " + info[1];
			}
		}
		if(name == null || "".equals(name))
		{
			name = longName;
		}
		return name;
	}

	private int GetStored() {
		return Integer.parseInt(IDResolver.knownIDs.getProperty(longName));
	}

	private String GetTypeName() {
		return IDResolver.GetTypeName(isBlock);
	}

	private boolean HasOpenSlot() {
		return ((isBlock ? IDResolver.GetFirstOpenBlock() : IDResolver
				.GetFirstOpenItem()) != -1) || specialItem;
	}

	private boolean HasStored() {
		return IDResolver.knownIDs.containsKey(longName);
	}

	@SuppressWarnings("unused")
	private void ItemForceOverwrite() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'ItemForceOverwrite' button.");
		running = false;
	}

	private void LoadBackground(GuiWidgetScreen widgetscreen) {
		try {
			Texture tex = widgetscreen.renderer.load(Minecraft.class
					.getClassLoader().getResource("gui/background.png"),
					Format.RGB, Filter.NEAREST);
			Image img = tex.getImage(0, 0, tex.getWidth(), tex.getHeight(),
					Color.parserColor("#303030"), true, Texture.Rotation.NONE);
			if (img != null) {
				subscreenIDSetter.setBackground(img);
			}
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO,
					"IDResolver - Failed to load background.");
		}
	}

	@SuppressWarnings("unused")
	private void MenuDeleteSavedID() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'MenuDeleteSavedID' button.");
		String oldname = IDResolver.GetStoredIDName(settingIntNewID.get(),
				isBlock, false);
		IDResolver.knownIDs.remove(oldname);
		settingIntNewID = null;
		running = false;
		try {
			IDResolver.StoreProperties();
		} catch (Throwable e) {
			IDResolver.logger.log(Level.INFO,
					"IDResolver - Was unable to store settings for "
							+ GetTypeName() + " " + GetName()
							+ " due to an exception.", e);
		}
	}

	@SuppressWarnings("unused")
	private void OverrideOld() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'OverrideOld' button.");
		overridingSetting = true;
		Integer ID = settingIntNewID.get();
		if ((isBlock && (Block.blocksList[ID] == null))
				|| (!isBlock && (Item.itemsList[ID] == null))) {

			DisplayMessage("Override requested for "
					+ GetTypeName()
					+ " at slot "
					+ ID
					+ ", but it is currently just a setting. Overriding now, IDResolver will ask later for the new block ID.");
		} else {
			running = false;
		}
	}

	private void PriorityConflict(String newobject, String oldobject,
			Boolean isTypeBlock) {
		oldsubscreenIDSetter = subscreenIDSetter;
		subscreenIDSetter = new WidgetSinglecolumn(new Widget[0]);
		{
			subscreenIDSetter.add(new Label(""));
			SimpleTextAreaModel model = new SimpleTextAreaModel();
			model.setText(
					String.format(
							"There is a mod priority conflict for a %s between two mods. Both has the same priority set. Please select which should take priority.",
							IDResolver.GetTypeName(isTypeBlock)), false);
			TextArea textarea = new TextArea(model);
			subscreenIDSetter.add(textarea);
			((WidgetSinglecolumn) subscreenIDSetter).heightOverrideExceptions
					.put(textarea, 0);
			String[] info = IDResolver.GetInfoFromSaveString(newobject);
			subscreenIDSetter.add(GuiApiHelper.makeButton("ID " + info[0] + " from " + info[1], "PriorityResolver", this, true,
					new Class[] { Boolean.class }, true));
			info = IDResolver.GetInfoFromSaveString(oldobject);
			subscreenIDSetter.add(GuiApiHelper.makeButton("ID " + info[0] + " from " + info[1], "PriorityResolver", this, true,
					new Class[] { Boolean.class }, false));
		}
		GuiWidgetScreen screen = GuiWidgetScreen.getInstance();
		screen.resetScreen();
		screen.setScreen(subscreenIDSetter);
		LoadBackground(screen);
	}

	@SuppressWarnings("unused")
	private void PriorityResolver(Boolean overrideold) {
		IDResolver.GetLogger().log(
				Level.INFO,
				"IDResolver - User pressed 'PriorityResolver' button with "
						+ overrideold.toString());
		if (overrideold) {
			overridingSetting = true;
			running = false;
		} else {
			subscreenIDSetter = oldsubscreenIDSetter;
			GuiWidgetScreen widgetscreen = GuiWidgetScreen.getInstance();
			widgetscreen.resetScreen();
			widgetscreen.setScreen(subscreenIDSetter);
			LoadBackground(widgetscreen);
		}
	}

	@SuppressWarnings("unused")
	private void RaisePriorityAndOK() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'RaisePriorityAndOK' button.");
		String modname = IDResolver.GetInfoFromSaveString(longName)[1];
		DisplayMessage(modname + " is now specified as a Priority Mod Level "
				+ IDResolver.RaiseModPriority(modname));
	}

	@SuppressWarnings("unused")
	private void ResetIDtoDefault() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'ResetIDtoDefault' button.");
		settingIntNewID.set(originalID);
		run();
	}
	
	

	@Override
	public void run() {
		int ID = settingIntNewID.get();
		String Name = null;
		try {
			if (isBlock) {
				Block selectedBlock = Block.blocksList[ID];
				if (selectedBlock != null) {
					Name = GetBlockName(selectedBlock);
				}
			} else {
				Item selectedItem = Item.itemsList[ID];
				if (selectedItem != null) {
					Name = GetItemName(selectedItem);
				}
			}
			if (IDResolver.IntHasStoredID(ID, isBlock)) {
				String[] info = IDResolver.GetInfoFromSaveString(IDResolver
						.GetStoredIDName(ID, isBlock));
				Name = "ID " + info[0] + " from " + info[1];
			}
		} catch (Throwable e) {
			Name = "ERROR";
		}
		boolean originalmenu = (isMenu && (ID == settingIntNewID.defaultValue));
		if (!disableOverride) {
			resolveScreenOverride.setEnabled(IDResolver.IntHasStoredID(ID,
					isBlock) && IDResolver.overridesenabled && !originalmenu);
		}
		if (!originalmenu) {
			if (Name == null) {
				GuiApiHelper.setTextAreaText(resolveScreenLabel, GetTypeName()
						+ " ID " + Integer.toString(ID) + " is available!");
				resolveScreenContinue.setEnabled(true);
			} else {
				GuiApiHelper.setTextAreaText(resolveScreenLabel, GetTypeName()
						+ " ID " + Integer.toString(ID) + " is used by " + Name
						+ ".");
				resolveScreenContinue.setEnabled(false);
			}
		} else {
			GuiApiHelper.setTextAreaText(resolveScreenLabel,
					"This is the currently saved ID.");
			resolveScreenContinue.setEnabled(true);
		}
	}
	
	private static void SyncMinecraftScreen(Minecraft mc,GuiWidgetScreen widgetscreen)
	{
		mc.displayWidth = mc.mcCanvas.getWidth();
		mc.displayHeight = mc.mcCanvas.getHeight();
		widgetscreen.layout();
		RenderScale.scale = widgetscreen.screenSize.scaleFactor;
		GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
		widgetscreen.renderer.syncViewportSize();
	}

	private void RunConflict() throws Exception {
		if (specialItem) {
			return;
		}
		Minecraft mc = ModLoader.getMinecraftInstance();
		if (mc == null) {
			IDResolver
					.AppendExtraInfo("Warning: When resolving "
							+ longName
							+ " ID resolver detected that the Minecraft object was NULL! Assuming 'special' object handling. Please report this!");
			return;
		}
		if (!mc.running && IDResolver.shutdown) {
			IDResolver
					.GetLogger()
					.log(Level.INFO,
							"IDResolver - Minecraft has reported that it is shutting down, skipping assignment.");
			throw new Exception("Minecraft is shutting down.");
		}
		running = true;
		if (IDResolver.autoAssignMod != null) {
			boolean rev = IDResolver.autoAssignMod.startsWith("!");
			if (IDResolver.GetInfoFromSaveString(longName)[1]
					.equals(IDResolver.autoAssignMod.substring(rev ? 1 : 0))) {
				AutoAssign(true, rev);
			}
		}
		if (!HasOpenSlot()) {
			IDResolver.logger.log(Level.INFO,
					"IDResolver - no open slots are available.");
			throw new RuntimeException("No open " + GetTypeName()
					+ " IDs are available.");
		}
		GuiWidgetScreen widgetscreen = GuiWidgetScreen.getInstance();
		SyncMinecraftScreen(mc,widgetscreen);
		int priority = IDResolver.GetModPriority(IDResolver
				.GetInfoFromSaveString(longName)[1]);
		boolean restart = false;
		do {
			widgetscreen.resetScreen();
			widgetscreen.setScreen(subscreenIDSetter);
			LoadBackground(widgetscreen);
			if (restart) {
				running = true;
			}
			run();
			Font fnt = widgetscreen.theme.getDefaultFont();
			restart = false;
			mod_IDResolver.UpdateUsed();
			if (priority > 0) {
				if (IDResolver.IntHasStoredID(settingIntNewID.defaultValue,
						isBlock)) {
					String otherobject = IDResolver.GetStoredIDName(
							settingIntNewID.defaultValue, isBlock, true);
					String otherclass = IDResolver
							.GetInfoFromSaveString(otherobject)[1];
					int otherpri = IDResolver.GetModPriority(otherclass);
					if (priority > otherpri) {
						running = false;
						overridingSetting = true;
						IDResolver.logger
								.log(Level.INFO,
										"IDResolver - Override will be called due to mod priority for "
												+ IDResolver
														.GetInfoFromSaveString(longName)[1]
												+ " is greater than for "
												+ otherclass);
					} else if (priority == otherpri) {
						PriorityConflict(
								IDResolver.TrimType(longName, isBlock),
								otherobject, isBlock);
					}
				} else {
					running = false;
					if (!((isBlock && (Block.blocksList[settingIntNewID.defaultValue] == null)) || (!isBlock && (Item.itemsList[settingIntNewID.defaultValue] == null)))) {
						IDResolver.logger
								.log(Level.INFO,
										"IDResolver - Override will be called due to mod priority for "
												+ IDResolver
														.GetInfoFromSaveString(longName)[1]);
					} else {
						IDResolver.logger
								.log(Level.INFO,
										"IDResolver - Automatically returning default ID due to mod priority for "
												+ IDResolver
														.GetInfoFromSaveString(longName)[1]);
					}
				}
			}
			if (IDResolver.ShowOnlyConflicts()) {
				if (resolveScreenContinue.isEnabled()) {
					running = false;
					IDResolver.logger
							.log(Level.INFO,
									"IDResolver - Automatically returning default ID as no conflict exists.");
				}
			}
			widgetscreen.layout();
			while (running) {

				if (((mc.displayWidth != mc.mcCanvas.getWidth()) || (mc.displayHeight != mc.mcCanvas
						.getHeight()))) {
					SyncMinecraftScreen(mc,widgetscreen);
				}

				widgetscreen.gui.update();
				if ((fnt != null) && IDResolver.ShowTick(false)) {
					widgetscreen.renderer.startRendering();
					String[] ids = mod_IDResolver.getIDs();
					for (int i = 0; i < ids.length; i++) {
						fnt.drawText(null, 1, 12 + (i * 12), ids[i]);
					}
					widgetscreen.renderer.endRendering();
				}
				Display.update();
				Thread.yield();
				if (!mc.running) {
					if (IDResolver.shutdown) {
						IDResolver
								.GetLogger()
								.log(Level.INFO,
										"IDResolver - Minecraft has reported that it is shutting down, skipping assignment.");
						settingIntNewID = null;
						running = false;
					} else {
						DisplayMessage(IDResolver.langMinecraftShuttingDown);
						IDResolver.shutdown = true;
						mc.running = true;
					}
				}
			}
			if (IDResolver.shutdown) {
				mc.running = false;
			}
			if (settingIntNewID != null) {
				Integer ID = settingIntNewID.get();
				if (!overridingSetting) {
					IDResolver.knownIDs.setProperty(longName, ID.toString());
				} else {
					String oldname = IDResolver.GetStoredIDName(ID, isBlock,
							false);
					if ((isBlock && (Block.blocksList[ID] == null))
							|| (!isBlock && (Item.itemsList[ID] == null))) {
						IDResolver.knownIDs.remove(oldname);
						IDResolver.knownIDs
								.setProperty(longName, ID.toString());
						IDResolver.logger
								.log(Level.INFO,
										"IDResolver - Override requested for "
												+ GetTypeName()
												+ " at slot "
												+ ID
												+ ", but it is currently just a setting. Overriding now, IDResolver will ask later for the new block ID.");
					} else {
						IDResolver.logger.log(Level.INFO,
								"IDResolver - Overriding setting. Requesting new ID for old "
										+ GetTypeName() + " at slot " + ID
										+ ".");
						IDResolver resolver = null;
						if (isBlock) {
							resolver = new IDResolver(ID, Block.blocksList[ID]);
						} else {
							resolver = new IDResolver(ID, Item.itemsList[ID]);
						}
						resolver.longName = oldname;
						resolver.disableOverride = true;
						resolver.SetupGui(ID);
						IDResolver.knownIDs.remove(oldname);
						IDResolver.knownIDs
								.setProperty(longName, ID.toString());
						resolver.run();
						resolver.RunConflict();
						Integer oldid = resolver.settingIntNewID.get();
						if (resolver.settingIntNewID == null) {
							IDResolver.logger
									.log(Level.INFO,
											"IDResolver - User cancelled request for override. Resetting.");
							IDResolver.knownIDs.remove(longName);
							IDResolver.knownIDs.setProperty(oldname,
									ID.toString());
							restart = true;
							continue;
						}
						IDResolver.logger.log(Level.INFO,
								"IDResolver - Overriding " + GetTypeName()
										+ " as requested.");
						if (isBlock) {
							IDResolver.OverrideBlockID(ID, oldid);
						} else {
							IDResolver.OverrideItemID(ID, oldid);
						}
						IDResolver.logger.log(Level.INFO,
								"IDResolver - Sucessfully overrode IDs. Setting ID "
										+ ID + " for " + GetName()
										+ ", Overriding " + resolver.GetName()
										+ " to " + oldid + " as requested.");
					}
				}
				try {
					IDResolver.StoreProperties();
				} catch (Throwable e) {
					IDResolver.logger.log(Level.INFO,
							"IDResolver - Was unable to store settings for "
									+ GetTypeName() + " " + GetName()
									+ " due to an exception.", e);
				}
			}
		} while (restart);
		widgetscreen.resetScreen();
	}

	private void RunConflictMenu() {
		running = true;
		GuiModScreen modscreen;
		GuiWidgetScreen widgetscreen = GuiWidgetScreen.getInstance();
		boolean restart = false;
		Minecraft mc = ModLoader.getMinecraftInstance();
		if (!mc.running) {
			IDResolver
					.GetLogger()
					.log(Level.INFO,
							"IDResolver - Minecraft has reported that it is shutting down, skipping assignment.");
			settingIntNewID = null;
			GuiModScreen.back();
			return;
		}
		do {
			modscreen = new GuiModScreen(GuiModScreen.currentScreen,
					subscreenIDSetter);
			GuiModScreen.show(modscreen);
			if (restart) {
				running = true;
			}
			run();
			Font fnt = widgetscreen.theme.getDefaultFont();
			restart = false;
			mod_IDResolver.UpdateUsed();
			if (isMenu) {
				if (mc.theWorld != null) {
					DisplayMessage("You cannot modify IDs while in game. Please exit to main menu and try again.");
				}
			}
			while (running) {

				if ((mc.displayWidth != mc.mcCanvas.getWidth())
						|| (mc.displayHeight != mc.mcCanvas.getHeight())) {
					mc.displayWidth = mc.mcCanvas.getWidth();
					mc.displayHeight = mc.mcCanvas.getHeight();
					((MinecraftImpl) mc).mcFrame.pack();
				}

				modscreen.drawScreen(0, 0, 0);
				if ((fnt != null) && IDResolver.ShowTick(false)) {
					widgetscreen.renderer.startRendering();
					String[] ids = mod_IDResolver.getIDs();
					for (int i = 0; i < ids.length; i++) {
						fnt.drawText(null, 1, 12 + (i * 12), ids[i]);
					}
					widgetscreen.renderer.endRendering();
				}
				Display.update();
				Thread.yield();
				if (!mc.running) {
					IDResolver
							.GetLogger()
							.log(Level.INFO,
									"IDResolver - Minecraft has reported that it is shutting down, skipping assignment.");
					settingIntNewID = null;
					running = false;
				}
			}
			if (settingIntNewID != null) {
				Integer ID = settingIntNewID.get();
				if (!overridingSetting) {
					IDResolver.knownIDs.setProperty(longName, ID.toString());
					if (isBlock) {
						IDResolver.OverrideBlockID(
								settingIntNewID.defaultValue, ID);
					} else {
						IDResolver.OverrideItemID(settingIntNewID.defaultValue,
								ID);
					}
				} else {
					String oldname = IDResolver.GetStoredIDName(ID, isBlock,
							false);
					if ((isBlock && (Block.blocksList[ID] == null))
							|| (!isBlock && (Item.itemsList[ID] == null))) {
						IDResolver.knownIDs.remove(oldname);
						IDResolver.knownIDs
								.setProperty(longName, ID.toString());
						IDResolver.logger
								.log(Level.INFO,
										"IDResolver - Override requested for "
												+ GetTypeName()
												+ " at slot "
												+ ID
												+ ", but it is currently just a setting. Removing the old setting as it may be a loose end.");
						if (isBlock) {
							IDResolver.OverrideBlockID(
									settingIntNewID.defaultValue, ID);
						} else {
							IDResolver.OverrideItemID(
									settingIntNewID.defaultValue, ID);
						}
					} else {
						IDResolver.logger.log(Level.INFO,
								"IDResolver - Overriding setting. Requesting new ID for old "
										+ GetTypeName() + " at slot " + ID
										+ ".");
						Object oldObject = null;
						IDResolver resolver = null;
						if (isBlock) {
							resolver = new IDResolver(ID, Block.blocksList[ID]);
							oldObject = Block.blocksList[settingIntNewID.defaultValue];
							Block.blocksList[settingIntNewID.defaultValue] = null;
						} else {
							resolver = new IDResolver(ID, Item.itemsList[ID]);
							oldObject = Item.itemsList[settingIntNewID.defaultValue];
							Item.itemsList[settingIntNewID.defaultValue] = null;
						}
						resolver.longName = oldname;
						resolver.disableOverride = true;
						resolver.SetupGui(ID);
						IDResolver.knownIDs.remove(oldname);
						IDResolver.knownIDs
								.setProperty(longName, ID.toString());
						resolver.run();
						resolver.RunConflictMenu();
						Integer oldid = resolver.settingIntNewID.get();
						if (isBlock) {
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
											"IDResolver - User cancelled request for override. Resetting.");
							IDResolver.knownIDs.remove(longName);
							IDResolver.knownIDs.setProperty(oldname,
									ID.toString());
							restart = true;
							continue;
						}
						IDResolver.logger.log(Level.INFO,
								"IDResolver - Overriding " + GetTypeName()
										+ " as requested.");
						if (isBlock) {
							IDResolver.OverrideBlockID(oldid, ID);
						} else {
							IDResolver.OverrideItemID(oldid, ID);
						}
						if (isBlock) {
							Block.blocksList[oldid] = (Block) oldObject;
						} else {
							Item.itemsList[oldid] = (Item) oldObject;
						}
						IDResolver.logger.log(Level.INFO,
								"IDResolver - Sucessfully overrode IDs. Setting ID "
										+ ID + " for " + GetName()
										+ ", Overriding " + resolver.GetName()
										+ " to " + oldid + " as requested.");
					}
				}
				try {
					IDResolver.StoreProperties();
				} catch (Throwable e) {
					IDResolver.logger.log(Level.INFO,
							"IDResolver - Was unable to store settings for "
									+ GetTypeName() + " " + GetName()
									+ " due to an exception.", e);
				}
			}
		} while (restart);
		GuiModScreen.back();
	}

	private void SetupGui(int RequestedID) {
		subscreenIDSetter = new WidgetSinglecolumn(new Widget[0]);
		{
			overrideName = GetName();
			SimpleTextAreaModel model = new SimpleTextAreaModel();
			if (!isMenu) {
				model.setText("New " + GetTypeName()
						+ " detected. Select ID for " + overrideName, false);
			} else {
				model.setText("Select ID for " + overrideName, false);
			}
			TextArea area = new TextArea(model);
			subscreenIDSetter.add(area);
			((WidgetSinglecolumn) subscreenIDSetter).heightOverrideExceptions
					.put(area, 0);
			settingIntNewID = new SettingInt("New ID", RequestedID, (isBlock
					|| specialItem ? 1 : Block.blocksList.length), 1, (isBlock
					|| specialItem ? Block.blocksList.length - 1
					: Item.itemsList.length - 1));
			settingIntNewID.defaultValue = RequestedID;
			WidgetInt intdisplay = new WidgetInt(settingIntNewID, "New ID");
			subscreenIDSetter.add(intdisplay);
			intdisplay.slider.getModel().addCallback(this);
			model = new SimpleTextAreaModel();
			model.setText("", false);
			resolveScreenLabel = new TextArea(model);
			subscreenIDSetter.add(resolveScreenLabel);
			resolveScreenContinue = GuiApiHelper.makeButton(
					"Save and Continue loading", "Finish", this, true);
			resolveScreenContinue.setEnabled(false);
			subscreenIDSetter.add(resolveScreenContinue);
			if (!disableOverride) {
				resolveScreenOverride = GuiApiHelper.makeButton(
						"Override old setting", "OverrideOld", this, true);
				resolveScreenOverride.setEnabled(IDResolver.IntHasStoredID(
						RequestedID, isBlock) && IDResolver.overridesenabled);
				subscreenIDSetter.add(resolveScreenOverride);
			}

			if (!isBlock && !isMenu) {
				subscreenIDSetter.add(GuiApiHelper.makeButton(
						"Force Overwrite", "ItemForceOverwrite", this, true));
			}

			if (isMenu) {
				subscreenIDSetter.add(GuiApiHelper.makeButton(
						"Delete saved ID", "MenuDeleteSavedID", this, true));
			}
			subscreenIDSetter.add(GuiApiHelper.makeButton(
					"Automatically assign an ID", "AutoAssign", this, true));
			subscreenIDSetter.add(GuiApiHelper.makeButton(
					"Automatically assign an ID in Reverse", "AutoAssignRev",
					this, true));
			if (!disableAutoAll) {
				Button assignallbuttons = GuiApiHelper.makeButton(
						"Automatically assign an ID to All\r\nfrom this mod",
						"AutoAssignAll", this, true);
				subscreenIDSetter.add(assignallbuttons);

				((WidgetSinglecolumn) subscreenIDSetter).heightOverrideExceptions
						.put(assignallbuttons, 30);

				assignallbuttons = GuiApiHelper
						.makeButton(
								"Automatically assign an ID to All from\r\nthis mod in Reverse",
								"AutoAssignAllRev", this, true);

				subscreenIDSetter.add(assignallbuttons);

				((WidgetSinglecolumn) subscreenIDSetter).heightOverrideExceptions
						.put(assignallbuttons, 30);

				if (IDResolver.GetModPriority(IDResolver
						.GetInfoFromSaveString(longName)[1]) < Integer.MAX_VALUE) {
					subscreenIDSetter.add(GuiApiHelper.makeButton(
							"Raise the priority for this mod",
							"RaisePriorityAndOK", this, true));
				}
			}
			subscreenIDSetter.add(GuiApiHelper.makeButton(
					"Reset to default ID", "ResetIDtoDefault", this, true));
			subscreenIDSetter.add(GuiApiHelper.makeButton("Cancel and Return",
					"Cancel", this, true));
		}
		subscreenIDSetter = new ScrollPane(subscreenIDSetter);
		((ScrollPane) subscreenIDSetter).setFixed(ScrollPane.Fixed.HORIZONTAL);
	}

	@SuppressWarnings("unused")
	private void UpPrioritizeMod() {
		IDResolver.GetLogger().log(Level.INFO,
				"IDResolver - User pressed 'UpPrioritizeMod' button.");
		String classname = IDResolver.GetInfoFromSaveString(longName)[1];
		Boolean override = false;
		if (isBlock) {
			Block selectedBlock = Block.blocksList[settingIntNewID.defaultValue];
			if (selectedBlock != null) {
				override = GetBlockName(selectedBlock) != null;
			}
		} else {
			Item selectedItem = Item.itemsList[settingIntNewID.defaultValue];
			if (selectedItem != null) {
				override = GetItemName(selectedItem) != null;
			}
		}
		if (IDResolver.IntHasStoredID(settingIntNewID.defaultValue, isBlock)) {
			override = true;
		}
		overridingSetting = override;
		settingIntNewID.reset();
		DisplayMessage(classname + " is now specified as a Priority Mod Level "
				+ IDResolver.RaiseModPriority(classname));
	}
}
