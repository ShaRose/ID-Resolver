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
import de.matthiasmann.twl.textarea.SimpleTextAreaModel;

public class IDResolver
{
	private static String[] armorTypes = new String[] { "Helmet", "Chestplate", "Leggings", "Boots" };
	
	private static Boolean attemptedRecover = false;
	private static String autoAssignMod;
	private static Field blockIdField;
	private static SettingBoolean checkForLooseSettings = null;
	private static String extraInfo = null;
	private static File idPath;
	private static Hashtable<Integer, String> idToMod = null;
	private static boolean initialized;
	private static Field itemIdField;
	private static Properties knownIDs;
	private static final String langLooseSettingsFound = "ID resolver has found some loose settings that aren't being used. Would you like to view a menu to remove them?";
	private static final String langMinecraftShuttingDown = "Minecraft is shutting down, but ID Resolver did not attempt to do so. It is probably due to another exception, such as not enough sprite indexes. In some cases mods will attempt to shut down Minecraft due to possible ID conflicts, and don't check for ID resolver, so ID resolver will attempt to recover Minecraft. If this does not work, please check your ModLoader.txt and ID Resolver.txt for more information.";
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
	private static boolean[] vanillaIDs = new boolean[35840];
	private static Boolean wasBlockInited = false;
	private static Boolean wasItemInited = false;
	private static WidgetSimplewindow[] windows;
	
	static
	{
		IDResolver.logger = Logger.getLogger("IDResolver");
		try
		{
			FileHandler logHandler = new FileHandler(
					new File(Minecraft.getMinecraftDir(), "IDResolver.txt").getPath());
			logHandler.setFormatter(new SimpleFormatter());
			IDResolver.logger.addHandler(logHandler);
			IDResolver.logger.setLevel(Level.ALL);
		} catch (Throwable e)
		{
			throw new RuntimeException("IDResolver - Unable to create logger!", e);
		}
		IDResolver.settingsComment = "IDResolver Known / Set IDs file. Please do not edit manually.";
		IDResolver.overridesEnabled = true;
		IDResolver.autoAssignMod = null;
		IDResolver.initialized = false;
		IDResolver.setupOverrides();
		IDResolver.addModGui();
	}
	
	private static void addModGui()
	{
		IDResolver.modScreen = new ModSettingScreen("ID Resolver");
		IDResolver.modScreen.setSingleColumn(true);
		IDResolver.modScreen.widgetColumn.childDefaultWidth = 300;
		IDResolver.modScreen.widgetColumn.add(GuiApiHelper.makeButton("Reload Entries", "reLoadModGui",
				IDResolver.class, true));
		IDResolver.reloadIDs();
	}
	
	private static void appendExtraInfo(String info)
	{
		if (IDResolver.extraInfo == null)
		{
			IDResolver.extraInfo = info;
		} else
		{
			IDResolver.extraInfo += "\r\n\r\n" + info;
		}
	}
	
	private static void buildItemInfo(StringBuilder builder, int index, boolean isBlock)
	{
		Item item = Item.itemsList[index];
		
		builder.append(String.format("\r\nSubitems: %s", item.hasSubtypes)).append(
				String.format("\r\nIs Block: %s", isBlock));
		
		if (!isBlock)
		{
			builder.append(String.format("\r\nClassname: %s", item.getClass().getName()));
		} else
		{
			builder.append(String.format("\r\nClassname: %s", Block.blocksList[index].getClass().getName()));
		}
		
		builder.append(String.format("\r\nMax stack: %s", item.getItemStackLimit()))
				.append(String.format("\r\nDamage versus entities: %s", item.getDamageVsEntity(null)))
				.append(String.format("\r\nEnchantability: %s", item.getItemEnchantability()))
				.append(String.format("\r\nMax Damage: %s", item.getMaxDamage()));
		if (item instanceof ItemArmor)
		{
			ItemArmor armor = ((ItemArmor) item);
			builder.append(String.format("\r\nMax Damage Reduction: %s", armor.damageReduceAmount)).append(
					String.format("\r\nArmor Slot: %s", IDResolver.armorTypes[armor.armorType]));
		}
		
		if (item instanceof ItemFood)
		{
			ItemFood food = ((ItemFood) item);
			builder.append(String.format("\r\nHeal Amount: %s", food.getHealAmount()))
					.append(String.format("\r\nHunger Modifier: %s", food.getSaturationModifier()))
					.append(String.format("\r\nWolves enjoy: %s", food.isWolfsFavoriteMeat()));
		}
		
		if (isBlock)
		{
			Block block = Block.blocksList[index];
			builder.append(String.format("\r\nBlock Hardness: %s", block.getHardness()))
					.append(String.format("\r\nBlock Slipperiness: %s", block.slipperiness))
					.append(String.format("\r\nBlock Light Level: %s", Block.lightValue[index]))
					.append(String.format("\r\nBlock Opacity: %s", Block.lightOpacity[index]));
		}
		
		if (IDResolver.idToMod != null)
		{
			if (IDResolver.idToMod.containsKey(index))
			{
				builder.append(String.format("\r\nMod: %s", IDResolver.idToMod.get(index)));
			}
		}
	}
	
	private static boolean checkForLooseSettings()
	{
		if (!IDResolver.modPriorities.containsKey("CheckForLooseSettings"))
		{
			IDResolver.modPriorities.setProperty("CheckForLooseSettings", "true");
			try
			{
				IDResolver.storeProperties();
			} catch (Throwable e)
			{
				IDResolver.logger.log(Level.INFO,
						"Could not save properties after adding CheckForLooseSettings option!", e);
			}
			return true;
		}
		return IDResolver.modPriorities.getProperty("CheckForLooseSettings").equalsIgnoreCase("true");
	}
	
	@SuppressWarnings("unused")
	private static void checkLooseIDs()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'CheckLooseIDs' button.");
		IDResolver.checkLooseSettings(false);
	}
	
	public static void checkLooseSettings(boolean automatic)
	{
		if (IDResolver.checkForLooseSettings() || !automatic)
		{
			ArrayList<String> unusedIDs = IDResolver.checkUnusedIDs();
			if (unusedIDs.size() != 0)
			{
				Logger idlog = IDResolver.getLogger();
				idlog.info("Detected " + unusedIDs.size() + " unused (Loose) IDs.");
				GuiApiHelper choiceBuilder = GuiApiHelper.createChoiceMenu(IDResolver.langLooseSettingsFound);
				choiceBuilder.addButton("Go to trim menu", "trimLooseSettings", IDResolver.class,
						new Class[] { (ArrayList.class) }, false, unusedIDs);
				choiceBuilder.addButton("Trim all loose settings", "trimLooseSettingsAll", IDResolver.class,
						new Class[] { (ArrayList.class) }, true, unusedIDs);
				choiceBuilder.addButton("Trim all loose settings for unloaded mods", "trimLooseSettingsAuto",
						IDResolver.class, new Class[] { (ArrayList.class) }, true, unusedIDs);
				if (automatic)
				{
					choiceBuilder.addButton("Ignore and don't ask again", "ignoreLooseDetectionAndDisable",
							IDResolver.class, true);
				}
				WidgetSimplewindow window = choiceBuilder.genWidget(true);
				((WidgetSinglecolumn) window.mainWidget).childDefaultWidth = 250;
				GuiModScreen.show(window);
			} else
			{
				if (!automatic)
				{
					GuiModScreen.show(GuiApiHelper.makeTextDisplayAndGoBack(null,
							"No settings in need of trimming were detected.", "Back", false));
				}
			}
		}
	}
	
	public static ArrayList<String> checkUnusedIDs()
	{
		ArrayList<String> unused = new ArrayList<String>();
		for (Entry<Object, Object> entry : IDResolver.knownIDs.entrySet())
		{
			String key = (String) entry.getKey();
			if ("SAVEVERSION".equals(key))
			{
				continue;
			}
			if (!IDResolver.loadedEntries.contains(key))
			{
				unused.add(key);
			}
		}
		return unused;
	}
	
	@SuppressWarnings("unused")
	private static void clearAllIDs()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'ClearAllIDs' button.");
		IDResolver.knownIDs.clear();
		try
		{
			IDResolver.storeProperties();
		} catch (Throwable e)
		{
			IDResolver.logger.log(Level.INFO, "Could not save properties", e);
		}
	}
	
	private static int conflictHelper(IDResolver resolver)
	{
		if (!IDResolver.initialized)
		{
			IDResolver.logger.log(Level.INFO,
					"IDResolver - Not initialized. This should never happen. Throwing exception.");
			throw new RuntimeException(
					"ID Resolver did not initalize! Please go to the thread and post ID resolver.txt for help resolving this error, which should basically never happen.");
		}
		if (resolver.hasStored())
		{
			int id = resolver.getStored();
			IDResolver.logger.log(Level.INFO, "IDResolver - Loading saved ID " + Integer.toString(id)
					+ " for " + resolver.getTypeName() + " " + resolver.getName() + ".");
			return id;
		}
		try
		{
			resolver.runConflict();
			if (resolver.settingIntNewID == null)
			{
				IDResolver.logger
						.log(Level.INFO,
								"IDResolver - New setting null, assuming user cancelled, returning to default behavior.");
				return -1;
			}
			if (!resolver.specialItem)
			{
				IDResolver.logger.log(Level.INFO, "IDResolver - User selected new ID "
						+ resolver.settingIntNewID.get().toString() + " for " + resolver.getName()
						+ ", returning control with new ID.");
			}
			return resolver.settingIntNewID.get();
		} catch (Exception e)
		{
			IDResolver.logger.log(Level.INFO, "IDResolver - Unhandled exception in ConflictHelper.", e);
			throw new RuntimeException("Unhandled exception in ConflictHelper.", e);
		}
	}
	
	private static void convertIDRSaveOne()
	{
		Properties oldIDs = (Properties) IDResolver.knownIDs.clone();
		IDResolver.knownIDs.clear();
		
		for (Entry<Object, Object> entry : oldIDs.entrySet())
		{
			String key = (String) entry.getKey();
			String[] info = IDResolver.getInfoFromSaveString(key);
			IDResolver.knownIDs.put((IDResolver.isBlockType(key) ? "BlockID." : "ItemID.") + info[2] + "|"
					+ info[1], entry.getValue());
		}
	}
	
	public static void disableLooseSettingsCheck()
	{
		IDResolver.checkForLooseSettings.set(false);
		IDResolver.updateTickSettings();
	}
	
	@SuppressWarnings("unused")
	private static void displayIDStatus()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'DisplayIDStatus' button.");
		
		if (IDResolver.idToMod == null)
		{
			IDResolver.getLogger().log(Level.INFO, "IDResolver - ID - Mod map is null. Regenerating.");
			try
			{
				IDResolver.idToMod = new Hashtable<Integer, String>(IDResolver.loadedEntries.size());
				for (Entry<Object, Object> entry : IDResolver.knownIDs.entrySet())
				{
					String key = (String) entry.getKey();
					if ("SAVEVERSION".equals(key))
					{
						continue;
					}
					if (IDResolver.loadedEntries.contains(key))
					{
						String[] info = IDResolver.getInfoFromSaveString(key);
						Integer value = Integer.parseInt((String) entry.getValue());
						boolean isBlock = IDResolver.isBlockType(info[0]);
						if (isBlock ? (Block.blocksList[value] != null) : (Item.itemsList[value] != null))
						{
							IDResolver.idToMod.put(value, info[1]);
						}
					}
				}
				
				IDResolver.getLogger().log(Level.INFO, "IDResolver - Finished generation of ID - Mod map..");
			} catch (Throwable e)
			{
				IDResolver
						.getLogger()
						.log(Level.INFO,
								"IDResolver - Could not generate ID - Mod map. Possibly corrupted database? Skipping.",
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
		String[] freeNames = new String[] { "blocks", "blocks or items", "items" };
		for (int i = 1; i < Item.itemsList.length; i++)
		{
			
			boolean addTick = false;
			Label label = null;
			StringBuilder tooltipText = null;
			ItemStack stack = new ItemStack(i, 1, 0);
			int position = IDResolver.getPosition(i);
			int exists = IDResolver.getExistance(position, i);
			
			if (exists == 0)
			{
				if (freeSlotStart == -1)
				{
					freeSlotStart = i;
				}
				int next = i + 1;
				if (next != Item.itemsList.length)
				{
					int nextPosition = IDResolver.getPosition(next);
					int nextExists = IDResolver.getExistance(nextPosition, next);
					
					boolean generateRangeItem = (nextExists != 0);
					
					if (!generateRangeItem && (nextPosition == position))
					{
						continue;
					}
				}
				
				if (freeSlotStart != i)
				{
					label = new Label(String.format("Slots %-4s - %-4s: Open slots", freeSlotStart, i));
					tooltipText = new StringBuilder(String.format(
							"Open Slots\r\n\r\nThis slot range of %s is open for any %s to use.", i
									- freeSlotStart, freeNames[position]));
				} else
				{
					label = new Label(String.format("Slot %-4s: Open slot", i));
					tooltipText = new StringBuilder(String.format(
							"Open Slot\r\n\r\nThis slot is open for any %s to use.", freeName[position]));
				}
				freeSlotStart = -1;
			} else
			{
				String stackName = IDResolver.getItemNameForStack(stack);
				tooltipText = new StringBuilder(String.format("Slot %-4s: %s", i, StringTranslate
						.getInstance().translateNamedKey(stackName)));
				label = new Label(tooltipText.toString());
				
				tooltipText.append(String.format("\r\n\r\nInternal name: %s", stackName));
				addTick = Item.itemsList[i].hasSubtypes;
				
				IDResolver.buildItemInfo(tooltipText, i, exists == 1);
			}
			
			WidgetSingleRow row = new WidgetSingleRow(200, 32);
			WidgetItem2DRender renderer = new WidgetItem2DRender(i);
			row.add(renderer, 32, 32);
			TextArea tooltip = GuiApiHelper.makeTextArea(tooltipText.toString(), false);
			if (addTick)
			{
				ModAction action = new ModAction(IDResolver.class, "tickIDSubItem", WidgetItem2DRender.class,
						TextArea.class, Label.class).setDefaultArguments(renderer, tooltip, label);
				action.setTag("SubItem Tick for " + tooltipText.subSequence(0, tooltipText.indexOf("\r\n")));
				if (mergedActions != null)
				{
					mergedActions = mergedActions.mergeAction(action);
				} else
				{
					mergedActions = action;
				}
			}
			label.setTooltipContent(tooltip);
			row.add(label);
			area.add(row);
		}
		
		WidgetSimplewindow window = new WidgetSimplewindow(area, "ID Resolver Status Report");
		if (mergedActions != null)
		{
			WidgetTick ticker = new WidgetTick();
			ticker.addCallback(mergedActions, 500);
			window.mainWidget.add(ticker);
		}
		window.backButton.setText("OK");
		GuiModScreen.show(window);
	}
	
	private static String generateIDStatusReport(int showFree)
	{
		StringBuilder report = new StringBuilder();
		String linebreak = System.getProperty("line.separator");
		report.append("ID Resolver ID Status report").append(linebreak);
		report.append("Generated on " + new Date().toString()).append(linebreak).append(linebreak);
		
		boolean checkClean = Block.blocksList.length != Item.shovelSteel.shiftedIndex;
		int totalRegisteredBlocks = 1;
		int totalUncleanBlockSlots = 0;
		int totalRegisteredItems = 0;
		
		StringBuilder reportIDs = new StringBuilder();
		
		String[] names = new String[] { "Free ", "Block", "Item " };
		
		String[] freeName = new String[] { "block", "block or item", "item" };
		String[] freeNames = new String[] { "blocks", "blocks or items", "items" };
		
		int freeSlotStart = -1;
		
		for (int i = 1; i < Item.itemsList.length; i++)
		{
			String itemName = null;
			String transName = null;
			String className = null;
			
			int position = IDResolver.getPosition(i);
			int exists = IDResolver.getExistance(position, i);
			
			switch (exists)
			{
				case 0:
				{
					if (showFree == 0)
					{
						continue;
					}
					if (freeSlotStart == -1)
					{
						freeSlotStart = i;
					}
					
					int next = i + 1;
					if (next != Item.itemsList.length)
					{
						if (showFree == 2)
						{
							int nextPosition = IDResolver.getPosition(next);
							int nextExists = IDResolver.getExistance(nextPosition, next);
							
							boolean generateRangeItem = (nextExists != 0);
							
							if (!generateRangeItem && (nextPosition == position))
							{
								continue;
							}
						}
					}
					
					if (freeSlotStart != i)
					{
						reportIDs.append(
								String.format(
										"%s %-8s - %-8s - This slot range of %s is open for any %s to use.",
										names[exists], freeSlotStart, i, i - freeSlotStart,
										freeNames[position])).append(linebreak);
					} else
					{
						
						reportIDs.append(
								String.format("%s %-8s - This slot is open for any %s to use.",
										names[exists], i, freeName[position])).append(linebreak);
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
					if (checkClean && (i < Block.blocksList.length))
					{
						totalUncleanBlockSlots++;
					}
					
					Item item = Item.itemsList[i];
					totalRegisteredItems++;
					itemName = item.getItemName();
					transName = StatCollector.translateToLocal(itemName + ".name");
					className = item.getClass().getName();
					break;
				}
			}
			
			reportIDs.append(
					String.format("%s %-8s - %-31s - %-31s - %s", names[exists], i, itemName, transName,
							className)).append(linebreak);
		}
		report.append("Quick stats:").append(linebreak);
		report.append(String.format("Block ID Status: %d/%d used. %d available.", totalRegisteredBlocks,
				Block.blocksList.length, (Block.blocksList.length - totalUncleanBlockSlots)
						- totalRegisteredBlocks));
		if (checkClean)
		{
			report.append("(Unclean Block slots: ");
			report.append(totalUncleanBlockSlots);
			report.append(")" + linebreak);
		} else
		{
			report.append(linebreak);
		}
		report.append(
				String.format("Item ID Status: %d/%d used. %d available.", totalRegisteredItems,
						Item.itemsList.length, (Item.itemsList.length - Item.shovelSteel.shiftedIndex)
								- totalRegisteredItems)).append(linebreak).append(linebreak);
		report.append(
				"Type  ID      - Name                           - Tooltip                        - Class")
				.append(linebreak);
		report.append(reportIDs.toString());
		return report.toString();
	}
	
	public static int getConflictedBlockID(int RequestedID, Block newBlock)
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - 'GetConflictedBlockID' called.");
		
		if (!IDResolver.initialized)
		{
			IDResolver.logger
					.log(Level.INFO, "IDResolver - Not initialized. Returning to default behaviour.");
			return RequestedID;
		}
		
		if (newBlock == null)
		{
			
			IDResolver.logger
					.log(Level.INFO,
							"IDResolver - Conflict requested for null Block: Returning requested ID as there is likely another crash. Logging the stacktrace to display what mod is causing issues.");
			try
			{
				throw new Exception("Generating Stacktrace");
			} catch (Throwable e)
			{
				IDResolver.logger.log(Level.INFO, e.toString());
			}
			
			return RequestedID;
		}
		
		if (!IDResolver.isModObject(RequestedID, true))
		{
			IDResolver.getLogger().log(Level.INFO,
					"IDResolver - Detected Vanilla Block: Returning requested ID.");
			return RequestedID;
		}
		
		if (IDResolver.vanillaIDs[RequestedID])
		{
			IDResolver
					.getLogger()
					.log(Level.INFO,
							"IDResolver - Mod is attempting to overwrite what seems to be a vanilla ID: Allowing the overwrite.");
			return RequestedID;
		}
		
		IDResolver resolver = new IDResolver(RequestedID, newBlock);
		IDResolver.getLogger().log(Level.INFO,
				"IDResolver - Long name of requested block is " + resolver.longName);
		resolver.setupGui(RequestedID);
		return IDResolver.conflictHelper(resolver);
	}
	
	public static int GetConflictedBlockID(int RequestedID, Block newBlock)
	{
		return IDResolver.getConflictedBlockID(RequestedID, newBlock);
	}
	
	public static int getConflictedItemID(int RequestedID, Item newItem)
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - 'GetConflictedItemID' called.");
		
		if (!IDResolver.initialized)
		{
			IDResolver.logger
					.log(Level.INFO, "IDResolver - Not initialized. Returning to default behaviour.");
			return RequestedID;
		}
		
		if (newItem == null)
		{
			
			IDResolver.logger
					.log(Level.INFO,
							"IDResolver - Conflict requested for null Item: Returning requested ID as there is likely another crash somewhere else. Logging the stacktrace to display what mod is causing issues just in case.");
			try
			{
				throw new Exception("Generating Stacktrace");
			} catch (Throwable e)
			{
				IDResolver.logger.log(Level.INFO, e.toString());
			}
			
			return RequestedID;
		}
		
		if (!IDResolver.isModObject(RequestedID, true))
		{
			IDResolver.getLogger().log(Level.INFO,
					"IDResolver - Detected Vanilla Item: Returning requested ID.");
			return RequestedID;
		}
		if (IDResolver.vanillaIDs[RequestedID])
		{
			IDResolver
					.getLogger()
					.log(Level.INFO,
							"IDResolver - Mod is attempting to overwrite what seems to be a vanilla ID: Allowing the overwrite.");
			return RequestedID;
		}
		IDResolver resolver = new IDResolver(RequestedID, newItem);
		IDResolver.getLogger().log(Level.INFO,
				"IDResolver - Long name of requested item is " + resolver.longName);
		resolver.setupGui(RequestedID);
		return IDResolver.conflictHelper(resolver);
	}
	
	public static int GetConflictedItemID(int RequestedID, Item newItem)
	{
		return IDResolver.getConflictedItemID(RequestedID, newItem);
	}
	
	private static int getExistance(int position, int i)
	{
		ItemStack stack = new ItemStack(i, 1, 0);
		int exists = 0;
		switch (position)
		{
			case 0:
			{
				if ((Block.blocksList[i] != null) && (stack.getItem() != null))
				{
					exists = 1;
				}
				break;
			}
			case 1:
			{
				if ((Block.blocksList[i] != null) && (stack.getItem() != null))
				{
					exists = 1;
				} else
				{
					if (Item.itemsList[i] != null)
					{
						exists = 2;
					}
				}
				break;
			}
			case 2:
			{
				if (Item.itemsList[i] != null)
				{
					exists = 2;
				}
				break;
			}
		}
		return exists;
	}
	
	public static String getExtraInfo()
	{
		
		return IDResolver.extraInfo;
	}
	
	private static int getFirstOpenBlock()
	{
		for (int i = 1; i < Block.blocksList.length; i++)
		{
			if (!IDResolver.isSlotFree(i))
			{
				continue;
			}
			return i;
		}
		return -1;
	}
	
	private static int getFirstOpenItem()
	{
		for (int i = Item.shovelSteel.shiftedIndex; i < Item.itemsList.length; i++)
		{
			if (!IDResolver.isSlotFree(i))
			{
				continue;
			}
			return i;
		}
		return -1;
	}
	
	/**
	 * Just a helper method.
	 * 
	 * @param input
	 *            the key to split up
	 * @return an array: ID | BaseMod. USED TO BE Class|BaseMod|ID
	 */
	private static String[] getInfoFromSaveString(String input)
	{
		return input.split("[|]");
	}
	
	private static String getItemNameForStack(ItemStack stack)
	{
		if (stack.getItem() == null)
		{
			return "";
		}
		try
		{
			return stack.getItem().getItemNameIS(stack);
		} catch (Throwable e)
		{
			return "";
		}
	}
	
	private static int getLastOpenBlock()
	{
		for (int i = Block.blocksList.length - 1; i >= 1; i--)
		{
			if (!IDResolver.isSlotFree(i))
			{
				continue;
			}
			return i;
		}
		return -1;
	}
	
	private static int getLastOpenItem()
	{
		for (int i = Item.itemsList.length - 1; i >= Item.shovelSteel.shiftedIndex; i--)
		{
			if (!IDResolver.isSlotFree(i))
			{
				continue;
			}
			return i;
		}
		return -1;
	}
	
	public static Logger getLogger()
	{
		return IDResolver.logger;
	}
	
	private static String getLongBlockName(Block block, int originalrequestedID)
	{
		String name = Integer.toString(originalrequestedID) + "|";
		StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
		int bestguess = -1;
		for (int i = 1; i < stacktrace.length; i++)
		{
			Class<?> exceptionclass = null;
			try
			{
				exceptionclass = Class.forName(stacktrace[i].getClassName());
			} catch (Throwable e)
			{
				continue;
			}
			if (Block.class.isAssignableFrom(exceptionclass))
			{
				continue;
			}
			if (IDResolver.class.isAssignableFrom(exceptionclass))
			{
				continue;
			}
			if (bestguess == -1)
			{
				bestguess = i;
			}
			if (BaseMod.class.isAssignableFrom(exceptionclass))
			{
				bestguess = i;
				break;
			}
		}
		if (bestguess == -1)
		{
			name += "IDRESOLVER_UNKNOWN_BLOCK_" + block.getClass().getName();
		} else
		{
			name += IDResolver.trimMCP(stacktrace[bestguess].getClassName());
		}
		return name;
	}
	
	private static String getLongItemName(Item item, int originalrequestedID)
	{
		String name = Integer.toString(originalrequestedID) + "|";
		StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
		int bestguess = -1;
		for (int i = 1; i < stacktrace.length; i++)
		{
			Class<?> exceptionclass = null;
			try
			{
				exceptionclass = Class.forName(stacktrace[i].getClassName());
			} catch (Throwable e)
			{
				continue;
			}
			if (Item.class.isAssignableFrom(exceptionclass))
			{
				continue;
			}
			if (IDResolver.class.isAssignableFrom(exceptionclass))
			{
				continue;
			}
			if (bestguess == -1)
			{
				bestguess = i;
			}
			if (BaseMod.class.isAssignableFrom(exceptionclass))
			{
				bestguess = i;
				break;
			}
		}
		if (bestguess == -1)
		{
			name += "IDRESOLVER_UNKNOWN_BLOCK_" + item.getClass().getName();
		} else
		{
			name += IDResolver.trimMCP(stacktrace[bestguess].getClassName());
		}
		return name;
	}
	
	private static String getlongName(Object obj, int ID)
	{
		String name = null;
		if (obj instanceof Block)
		{
			name = "BlockID." + IDResolver.getLongBlockName((Block) obj, ID);
		}
		if (obj instanceof Item)
		{
			name = "ItemID." + IDResolver.getLongItemName((Item) obj, ID);
		}
		IDResolver.loadedEntries.add(name);
		if (name != null)
		{
			return name;
		}
		if (obj == null)
		{
			throw new RuntimeException(
					"You should never see this. For some reason, ID resolver attempted to get an item name for null.");
		}
		throw new RuntimeException(
				"You should never see this. For some reason, ID resolver attempted to get an item name a non-item / block. It is of type '"
						+ obj.getClass().getName() + "'. The toString is: " + obj.toString());
	}
	
	private static int getModPriority(String modname)
	{
		if (IDResolver.modPriorities.containsKey(modname))
		{
			try
			{
				int value = Integer.parseInt(IDResolver.modPriorities.getProperty(modname));
				if (value >= 0)
				{
					return value;
				}
			} catch (Throwable e)
			{
				// this should never ever happen, and if it does someone edited
				// the settings.
			}
		}
		IDResolver.modPriorities.setProperty(modname, "0");
		return 0;
	}
	
	private static int getPosition(int i)
	{
		int position = 0;
		if (i >= Block.blocksList.length)
		{
			position = 2;
		} else
		{
			if (i >= Item.shovelSteel.shiftedIndex)
			{
				position = 1;
			}
		}
		return position;
	}
	
	private static String getStoredIDName(int ID, boolean block)
	{
		return IDResolver.getStoredIDName(ID, block, true);
	}
	
	private static String getStoredIDName(int ID, boolean block, boolean trim)
	{
		for (Map.Entry<Object, Object> entry : IDResolver.knownIDs.entrySet())
		{
			String key = ((String) entry.getKey());
			if (key.startsWith("PRIORITY|"))
			{
				continue;
			}
			if ("SAVEVERSION".equals(key))
			{
				continue;
			}
			
			if (ID == Integer.parseInt((String) entry.getValue()))
			{
				return trim ? IDResolver.trimType(key, block) : key;
			}
		}
		return null;
	}
	
	private static String getTypeName(Boolean block)
	{
		return (block ? " Block" : " Item").substring(1);
	}
	
	public static boolean hasStoredID(int ID, boolean block)
	{
		if (block && !IDResolver.wasBlockInited)
		{
			IDResolver.wasBlockInited = true;
		} else if (!block && !IDResolver.wasItemInited)
		{
			IDResolver.wasItemInited = true;
		}
		return IDResolver.intHasStoredID(ID, block) | IDResolver.isModObject(ID, block);
	}
	
	public static boolean HasStoredID(int ID, boolean block)
	{
		return IDResolver.hasStoredID(ID, block);
	}
	
	@SuppressWarnings("unused")
	private static void ignoreLooseDetectionAndDisable()
	{
		IDResolver.disableLooseSettingsCheck();
	}
	
	private static boolean intHasStoredID(int ID, boolean block)
	{
		if (IDResolver.initialized)
		{
			if (IDResolver.knownIDs.containsValue(Integer.toString(ID)))
			{
				return IDResolver.getStoredIDName(ID, block) != null;
			}
		}
		return false;
	}
	
	private static boolean isBlockType(String input)
	{
		if (input.startsWith("BlockID."))
		{
			return true;
		}
		if (input.startsWith("ItemID."))
		{
			return false;
		}
		throw new InvalidParameterException("Input is not fully named!");
	}
	
	private static Boolean isModObject(int id, boolean isBlock)
	{
		StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
		boolean possibleVanilla = false;
		for (int i = 1; i < stacktrace.length; i++)
		{
			try
			{
				Class classType = Class.forName(stacktrace[i].getClassName());
				if (BaseMod.class.isAssignableFrom(classType))
				{
					return true;
				}
				if ("<clinit>".equals(stacktrace[i].getMethodName())
						&& (isBlock ? Block.class == classType : Item.class == classType))
				{
					possibleVanilla = true;
				}
			} catch (Throwable e)
			{
				// Should never happen, but in this case just going to coast
				// right over it.
			}
		}
		if (possibleVanilla)
		{
			IDResolver.vanillaIDs[id] = true;
		}
		return false;
	}
	
	private static boolean isSlotFree(int i)
	{
		if (i < Block.blocksList.length)
		{
			if ((Block.blocksList[i] != null) || IDResolver.intHasStoredID(i, true))
			{
				return false;
			}
		}
		return !(Item.itemsList[i] != null) || IDResolver.intHasStoredID(i, false);
	}
	
	@SuppressWarnings("unused")
	private static void linkCallback(String url)
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed link. URL is: " + url);
		File file = new File(url);
		try
		{
			Desktop.getDesktop().open(file);
		} catch (Throwable e)
		{
			e.printStackTrace();
			Sys.openURL(file.toURI().toString());
		}
	}
	
	@SuppressWarnings("unused")
	private static void lowerModPriorityFromMenu(String modName, SimpleTextAreaModel textarea)
	{
		IDResolver.getLogger().log(Level.INFO,
				"IDResolver - User pressed 'LowerModPriorityFromMenu' button with " + modName);
		int intlevel = IDResolver.getModPriority(modName);
		if (intlevel > 0)
		{
			intlevel--;
		}
		String newlevel = Integer.toString(intlevel);
		IDResolver.modPriorities.setProperty(modName, newlevel);
		textarea.setText(modName + " - Currently at Priority Level " + newlevel);
		try
		{
			IDResolver.storeProperties();
		} catch (Throwable e)
		{
			IDResolver.logger.log(Level.INFO, "Could not save properties", e);
		}
	}
	
	private static void overrideBlockID(int newid, int oldid)
	{
		Block oldblock = Block.blocksList[newid];
		Block.blocksList[newid] = null;
		if (oldblock != null)
		{
			try
			{
				IDResolver.blockIdField.set(oldblock, oldid);
			} catch (Throwable e)
			{
				IDResolver.logger.log(Level.INFO, "IDResolver - Unable to override blockID!", e);
				throw new IllegalArgumentException("IDResolver - Unable to override blockID!", e);
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
		if (oldblockitem != null)
		{
			try
			{
				IDResolver.itemIdField.set(oldblockitem, oldid);
			} catch (Throwable e)
			{
				IDResolver.logger.log(Level.INFO,
						"IDResolver - Unable to override itemID for the block's item!", e);
				throw new IllegalArgumentException(
						"IDResolver - Unable to override itemID for the block's item!", e);
			}
		}
		Block.blocksList[oldid] = oldblock;
		Item.itemsList[oldid] = oldblockitem;
		IDResolver.idToMod = null;
	}
	
	private static void overrideItemID(int newid, int oldid)
	{
		Item olditem = Item.itemsList[newid];
		Item.itemsList[newid] = null;
		if (olditem != null)
		{
			try
			{
				IDResolver.itemIdField.set(olditem, oldid);
			} catch (Throwable e)
			{
				IDResolver.logger.log(Level.INFO, "IDResolver - Unable to override itemID!", e);
				throw new IllegalArgumentException("IDResolver - Unable to override itemID!", e);
			}
		}
		Item.itemsList[oldid] = olditem;
		IDResolver.idToMod = null;
	}
	
	private static String raiseModPriority(String modname)
	{
		String newlevel = Integer.toString(IDResolver.getModPriority(modname) + 1);
		IDResolver.modPriorities.setProperty(modname, newlevel);
		return newlevel;
	}
	
	@SuppressWarnings("unused")
	private static void raiseModPriorityFromMenu(String modName, SimpleTextAreaModel textarea)
	{
		IDResolver.getLogger().log(Level.INFO,
				"IDResolver - User pressed 'RaiseModPriorityFromMenu' button with " + modName);
		textarea.setText(modName + " - Currently at Priority Level " + IDResolver.raiseModPriority(modName));
		try
		{
			IDResolver.storeProperties();
		} catch (Throwable e)
		{
			IDResolver.logger.log(Level.INFO, "Could not save properties", e);
		}
	}
	
	private static void reloadIDs()
	{
		IDResolver.knownIDs = new Properties();
		IDResolver.modPriorities = new Properties();
		boolean forceSave = false;
		try
		{
			IDResolver.idPath = new File(Minecraft.getMinecraftDir().getAbsolutePath()
					+ "/config/IDResolverknownIDs.properties");
			
			IDResolver.idPath.getParentFile().mkdirs();
			
			IDResolver.priorityPath = new File(Minecraft.getMinecraftDir().getAbsolutePath()
					+ "/config/IDResolvermodPriorities.properties");
			IDResolver.priorityPath.getParentFile().mkdirs();
			
			if (IDResolver.idPath.createNewFile())
			{
				IDResolver.logger.log(Level.INFO, "IDResolver - IDs File not found, creating new one.");
				InputStream stream = IDResolver.class.getClassLoader().getResourceAsStream(
						"IDResolverDefaultIDs.properties");
				if (stream != null)
				{
					IDResolver.knownIDs.load(stream);
					IDResolver.logger.log(
							Level.INFO,
							"IDResolver - Found defaults file, loaded "
									+ Integer.toString(IDResolver.knownIDs.size()) + " IDs sucessfully.");
					stream.close();
					
					String version = IDResolver.knownIDs.getProperty("SAVEVERSION");
					
					if (version == null)
					{
						IDResolver.logger.log(Level.INFO,
								"IDResolver - Settings file is v1, but ID resolver now uses v2. Converting.");
						IDResolver.convertIDRSaveOne();
						IDResolver.logger.log(Level.INFO, "IDResolver - Settings file convertion complete.");
						forceSave = true;
					}
				}
			} else
			{
				try
				{
					FileInputStream stream = new FileInputStream(IDResolver.idPath);
					IDResolver.knownIDs.load(stream);
					stream.close();
					IDResolver.logger.log(Level.INFO,
							"IDResolver - Loaded " + Integer.toString(IDResolver.knownIDs.size())
									+ " IDs sucessfully.");
					
					String version = IDResolver.knownIDs.getProperty("SAVEVERSION");
					
					if (version == null)
					{
						IDResolver.logger.log(Level.INFO,
								"IDResolver - Settings file is v1, but ID resolver now uses v2. Converting.");
						IDResolver.convertIDRSaveOne();
						IDResolver.logger.log(Level.INFO, "IDResolver - Settings file convertion complete.");
						forceSave = true;
					}
					
				} catch (IOException e)
				{
					IDResolver.logger.log(Level.INFO,
							"IDResolver - Existing config details are invalid: Creating new settings.");
				}
			}
			if (IDResolver.priorityPath.createNewFile())
			{
				IDResolver.logger
						.log(Level.INFO, "IDResolver - Priorities File not found, creating new one.");
				InputStream stream = IDResolver.class.getClassLoader().getResourceAsStream(
						"IDResolverDefaultmodPriorities.properties");
				if (stream != null)
				{
					IDResolver.modPriorities.load(stream);
					IDResolver.logger.log(
							Level.INFO,
							"IDResolver - Found defaults file, loaded "
									+ Integer.toString(IDResolver.modPriorities.size())
									+ " Mod Priorities sucessfully.");
					stream.close();
				}
			} else
			{
				try
				{
					FileInputStream stream = new FileInputStream(IDResolver.priorityPath);
					IDResolver.modPriorities.load(stream);
					stream.close();
					int negatives = 0;
					if (IDResolver.modPriorities.containsKey("ShowTickMM"))
					{
						negatives++;
					}
					if (IDResolver.modPriorities.containsKey("ShowTickRS"))
					{
						negatives++;
					}
					IDResolver.logger.log(
							Level.INFO,
							"IDResolver - Loaded "
									+ Integer.toString(IDResolver.modPriorities.size() - negatives)
									+ " Mod Priorities sucessfully.");
				} catch (IOException e)
				{
					IDResolver.logger.log(Level.INFO,
							"IDResolver - Existing config details are invalid: Creating new settings.");
				}
			}
			if ((IDResolver.showTickMM == null) | (IDResolver.showTickRS == null)
					| (IDResolver.showOnlyConf == null) | (IDResolver.checkForLooseSettings == null))
			{
				IDResolver.showTickMM = new SettingBoolean("ShowTickMM", IDResolver.showTick(true));
				IDResolver.showTickRS = new SettingBoolean("ShowTickRS", IDResolver.showTick(false));
				IDResolver.checkForLooseSettings = new SettingBoolean("CheckForLooseSettings",
						IDResolver.checkForLooseSettings());
				IDResolver.showOnlyConf = new SettingBoolean("ShowOnlyConflicts",
						IDResolver.showOnlyConflicts());
			}
			IDResolver.initialized = true;
			IDResolver.updateTickSettings();
			
			if (forceSave)
			{
				IDResolver.logger.log(Level.INFO, "IDResolver - Saving as changes were made.");
				IDResolver.storeProperties();
			}
			
		} catch (Throwable e)
		{
			IDResolver.logger.log(Level.INFO, "IDResolver - Error while initalizing settings.", e);
			IDResolver.initialized = false;
		}
	}
	
	public static void reLoadModGui()
	{
		IDResolver.reloadIDs();
		
		IDResolver.modScreen.widgetColumn.removeAllChildren();
		Map<String, Vector<String>> IDmap = new HashMap<String, Vector<String>>();
		for (Map.Entry<Object, Object> entry : IDResolver.knownIDs.entrySet())
		{
			String key = (String) entry.getKey();
			
			if ("SAVEVERSION".equals(key))
			{
				continue;
			}
			String[] info = IDResolver.getInfoFromSaveString(key);
			if (IDmap.containsKey(info[1]))
			{
				IDmap.get(info[1]).add(key);
			} else
			{
				Vector<String> list = new Vector<String>();
				list.add(key);
				IDmap.put(info[1], list);
			}
		}
		IDResolver.windows = new WidgetSimplewindow[IDmap.size()];
		int id = 0;
		StringTranslate translate = StringTranslate.getInstance();
		for (Entry<String, Vector<String>> entry : IDmap.entrySet())
		{
			WidgetSinglecolumn window = new WidgetSinglecolumn();
			window.childDefaultWidth = 300;
			SimpleTextAreaModel textarea = new SimpleTextAreaModel();
			textarea.setText(
					entry.getKey() + " - Currently at Priority Level "
							+ IDResolver.getModPriority(entry.getKey()), false);
			TextArea textWidget = new TextArea(textarea);
			window.add(textWidget);
			window.heightOverrideExceptions.put(textWidget, 0);
			window.add(GuiApiHelper.makeButton("Raise Priority of this Mod", "raiseModPriorityFromMenu",
					IDResolver.class, true, new Class[] { String.class, SimpleTextAreaModel.class },
					entry.getKey(), textarea));
			window.add(GuiApiHelper.makeButton("Lower Priority of this Mod", "lowerModPriorityFromMenu",
					IDResolver.class, true, new Class[] { String.class, SimpleTextAreaModel.class },
					entry.getKey(), textarea));
			window.add(GuiApiHelper.makeButton("Wipe saved IDs of this mod", "wipeSavedIDsFromMenu",
					IDResolver.class, true, new Class[] { String.class }, entry.getKey()));
			for (String IDEntry : entry.getValue())
			{
				int x = Integer.parseInt(IDResolver.knownIDs.getProperty(IDEntry));
				String name = null;
				ItemStack stack = null;
				Boolean isBlock = IDResolver.isBlockType(IDEntry);
				if (isBlock)
				{
					if (Block.blocksList[x] != null)
					{
						stack = new ItemStack(Block.blocksList[x]);
					}
				} else
				{
					if (Item.itemsList[x] != null)
					{
						stack = new ItemStack(Item.itemsList[x]);
					}
				}
				try
				{
					name = IDResolver.getItemNameForStack(stack);
				} catch (Throwable e)
				{
					stack = null;
					name = null;
				}
				if (stack != null)
				{
					if ((name != null) && (name.length() != 0))
					{
						name = translate.translateNamedKey(name);
						if ((name == null) || (name.length() == 0))
						{
							name = IDResolver.getItemNameForStack(stack);
						}
					}
					if ((name == null) || (name.length() == 0))
					{
						String originalpos = IDResolver.getInfoFromSaveString(IDEntry)[0];
						if (isBlock)
						{
							name = "Unnamed " + IDResolver.trimMCP(Block.blocksList[x].getClass().getName())
									+ " originally at " + originalpos;
						} else
						{
							name = "Unnamed " + IDResolver.trimMCP(Item.itemsList[x].getClass().getName())
									+ " originally at " + originalpos;
						}
					}
				} else
				{
					String[] info = IDResolver.getInfoFromSaveString(IDEntry);
					name = "Loose setting for " + (isBlock ? "Block '" : "Item with original ID ") + info[0]
							+ " loaded from " + info[1];
				}
				window.add(GuiApiHelper.makeButton("Edit ID for " + name, "resolveNewID", IDResolver.class,
						true, new Class[] { String.class }, IDEntry));
			}
			IDResolver.windows[id] = new WidgetSimplewindow(window, "Config IDs for " + entry.getKey());
			IDResolver.modScreen.widgetColumn.add(GuiApiHelper.makeButton("View IDs for " + entry.getKey(),
					"showMenu", IDResolver.class, true, new Class[] { Integer.class }, id));
			id++;
		}
		IDResolver.updateTickSettings();
		Runnable callback = new ModAction(IDResolver.class, "updateTickSettings");
		WidgetBoolean TickMWidget = new WidgetBoolean(IDResolver.showTickMM, "Show ID info on Main Menu");
		TickMWidget.button.addCallback(callback);
		IDResolver.modScreen.widgetColumn.add(TickMWidget);
		TickMWidget = new WidgetBoolean(IDResolver.showTickRS, "Show ID info on Resolve Screen");
		TickMWidget.button.addCallback(callback);
		IDResolver.modScreen.widgetColumn.add(TickMWidget);
		TickMWidget = new WidgetBoolean(IDResolver.checkForLooseSettings, "Check for Loose settings");
		TickMWidget.button.addCallback(callback);
		IDResolver.modScreen.widgetColumn.add(TickMWidget);
		TickMWidget = new WidgetBoolean(IDResolver.showOnlyConf, "Only Show Conflicts");
		TickMWidget.button.addCallback(callback);
		IDResolver.modScreen.widgetColumn.add(TickMWidget);
		IDResolver.modScreen.widgetColumn.add(GuiApiHelper.makeButton("Wipe ALL Saved IDs", "clearAllIDs",
				IDResolver.class, true));
		IDResolver.modScreen.widgetColumn.add(GuiApiHelper.makeButton("Check for Loose IDs", "checkLooseIDs",
				IDResolver.class, true));
		
		IDResolver.modScreen.widgetColumn.add(GuiApiHelper.makeButton("Generate ID Status Report",
				"saveIDStatusToFile", IDResolver.class, true, new Class[] { Integer.class }, 0));
		
		IDResolver.modScreen.widgetColumn.add(GuiApiHelper.makeButton(
				"Generate ID Status Report with Expanded Free IDs", "saveIDStatusToFile", IDResolver.class,
				true, new Class[] { Integer.class }, 1));
		
		IDResolver.modScreen.widgetColumn.add(GuiApiHelper.makeButton(
				"Generate ID Status Report with Collapsed Free IDs", "saveIDStatusToFile", IDResolver.class,
				true, new Class[] { Integer.class }, 2));
		
		IDResolver.modScreen.widgetColumn.add(GuiApiHelper.makeButton("Display ID Status Report",
				"displayIDStatus", IDResolver.class, true));
		
		IDResolver.modScreen.widgetColumn.add(GuiApiHelper.makeButton("Reload Options",
				"reLoadModGuiAndRefresh", IDResolver.class, true));
	}
	
	@SuppressWarnings("unused")
	private static void reLoadModGuiAndRefresh()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'ReLoadModGuiAndRefresh' button.");
		GuiModScreen.back();
		IDResolver.reLoadModGui();
		GuiModScreen.show(IDResolver.modScreen.theWidget);
	}
	
	@SuppressWarnings("unused")
	private static void removeEntry(String key)
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'RemoveEntry' button for " + key);
		IDResolver.knownIDs.remove(key);
		try
		{
			IDResolver.storeProperties();
			IDResolver.logger.log(Level.INFO, "IDResolver - Removed the saved ID for " + key
					+ " as per use request via Settings screen.");
		} catch (Throwable e)
		{
			IDResolver.logger.log(Level.INFO, "IDResolver - Was unable to remove the saved ID for " + key
					+ " due to an exception.", e);
		}
		IDResolver.reLoadModGui();
	}
	
	@SuppressWarnings("unused")
	private static void removeLooseSetting(SettingList setting)
	{
		int selected = ((WidgetList) setting.displayWidget).listBox.getSelected();
		if (selected == -1)
		{
			return;
		}
		String key = setting.get().get(selected);
		IDResolver.knownIDs.remove(key);
		IDResolver.logger.log(Level.INFO, "Trimmed ID: " + key);
		setting.get().remove(selected);
		setting.displayWidget.update();
		int size = setting.get().size();
		
		if (selected == size)
		{
			selected--;
		}
		
		if (size > 0)
		{
			((WidgetList) setting.displayWidget).listBox.setSelected(selected);
		} else
		{
			GuiModScreen.back();
			GuiModScreen.back();
		}
		try
		{
			IDResolver.storeProperties();
		} catch (Throwable e)
		{
			IDResolver.logger.log(Level.INFO, "Could not save properties", e);
		}
		IDResolver.reLoadModGui();
	}
	
	public static void removeSettingByKey(String key)
	{
		if (IDResolver.knownIDs.containsKey(key))
		{
			IDResolver.knownIDs.remove(key);
		}
	}
	
	@SuppressWarnings("unused")
	private static void resolveNewID(String key)
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'ResolveNewID' button for " + key);
		if (!IDResolver.knownIDs.containsKey(key))
		{
			return;
		}
		String trimmedKey = IDResolver.trimType(key, IDResolver.isBlockType(key));
		IDResolver resolver = new IDResolver(
				Integer.parseInt(IDResolver.getInfoFromSaveString(trimmedKey)[0]), key);
		resolver.disableAutoAll = true;
		resolver.isMenu = true;
		resolver.setupGui(Integer.parseInt(IDResolver.knownIDs.getProperty(key)));
		resolver.runConflictMenu();
	}
	
	@SuppressWarnings("unused")
	private static void saveIDStatusToFile(Integer showFree)
	{
		IDResolver.getLogger().log(Level.INFO,
				"IDResolver - User pressed 'SaveIDStatusToFile' button. Mode: " + showFree.toString());
		File savePath = new File(new File(Minecraft.getMinecraftDir(), "ID Status.txt").getAbsolutePath()
				.replace("\\.\\", "\\"));
		try
		{
			FileOutputStream output = new FileOutputStream(savePath);
			output.write(IDResolver.generateIDStatusReport(showFree).getBytes());
			output.flush();
			output.close();
			
			WidgetSinglecolumn widget = new WidgetSinglecolumn(new Widget[0]);
			TextArea area = GuiApiHelper.makeTextArea(
					String.format("Saved ID status report to <a href=\"%1$s\">%1$s</a>", savePath), true);
			area.addCallback(new ModAction(IDResolver.class, "linkCallback", "Link Clicked Callback",
					String.class));
			widget.add(area);
			widget.overrideHeight = false;
			WidgetSimplewindow window = new WidgetSimplewindow(widget, "ID Resolver");
			window.backButton.setText("OK");
			
			GuiModScreen.show(window);
		} catch (Throwable e)
		{
			IDResolver.getLogger()
					.log(Level.INFO, "IDResolver - Exception when saving ID Status to file.", e);
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			String trace = sw.toString();
			pw.close();
			GuiModScreen.show(GuiApiHelper.makeTextDisplayAndGoBack("ID Resolver", "Error saving to "
					+ savePath.getAbsolutePath() + ", exception was:\r\n\r\n" + trace, "OK", false));
		}
	}
	
	private static void setupOverrides()
	{
		int pubfinalmod = Modifier.FINAL + Modifier.PUBLIC;
		try
		{
			for (Field field : Block.class.getFields())
			{
				if ((field.getModifiers() == pubfinalmod) && (field.getType() == int.class))
				{
					IDResolver.blockIdField = field;
					break;
				}
			}
		} catch (Throwable e3)
		{
			IDResolver.overridesEnabled = false;
		}
		
		try
		{
			for (Field field : Item.class.getFields())
			{
				if ((field.getModifiers() == pubfinalmod) && (field.getType() == int.class))
				{
					IDResolver.itemIdField = field;
					break;
				}
			}
		} catch (Throwable e3)
		{
			IDResolver.overridesEnabled = false;
		}
		if (IDResolver.overridesEnabled)
		{
			IDResolver.blockIdField.setAccessible(true);
			IDResolver.itemIdField.setAccessible(true);
		}
	}
	
	@SuppressWarnings("unused")
	private static void showMenu(Integer i)
	{
		IDResolver.getLogger().log(
				Level.INFO,
				"IDResolver - User pressed 'ShowMenu' button for " + i.toString() + " aka "
						+ IDResolver.windows[i].titleWidget.getText());
		GuiModScreen.clicksound();
		GuiModScreen.show(IDResolver.windows[i]);
	}
	
	private static boolean showOnlyConflicts()
	{
		if (!IDResolver.modPriorities.containsKey("ShowOnlyConflicts"))
		{
			IDResolver.modPriorities.setProperty("ShowOnlyConflicts", "true");
			try
			{
				IDResolver.storeProperties();
			} catch (Throwable e)
			{
				IDResolver.logger.log(Level.INFO,
						"Could not save properties after adding ShowOnlyConflicts option!", e);
			}
			return true;
		}
		return IDResolver.modPriorities.getProperty("ShowOnlyConflicts").equalsIgnoreCase("true");
	}
	
	public static boolean showTick(boolean mainmenu)
	{
		String key = mainmenu ? "ShowTickMM" : "ShowTickRS";
		if (!IDResolver.modPriorities.containsKey(key))
		{
			IDResolver.modPriorities.setProperty(key, "true");
			try
			{
				IDResolver.storeProperties();
			} catch (Throwable e)
			{
				IDResolver.logger.log(Level.INFO, "Could not save properties after adding " + key
						+ " option!", e);
			}
			return true;
		}
		return IDResolver.modPriorities.getProperty(key).equalsIgnoreCase("true");
	}
	
	private static void storeProperties() throws FileNotFoundException, IOException
	{
		FileOutputStream stream = new FileOutputStream(IDResolver.idPath);
		IDResolver.knownIDs.setProperty("SAVEVERSION", "v2");
		IDResolver.knownIDs.store(stream, IDResolver.settingsComment);
		stream.close();
		stream = new FileOutputStream(IDResolver.priorityPath);
		IDResolver.modPriorities.store(stream, IDResolver.settingsComment);
		stream.close();
	}
	
	private static void syncMinecraftScreen(Minecraft mc, GuiWidgetScreen widgetscreen)
	{
		mc.displayWidth = mc.mcCanvas.getWidth();
		mc.displayHeight = mc.mcCanvas.getHeight();
		widgetscreen.layout();
		RenderScale.scale = widgetscreen.screenSize.scaleFactor;
		GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
		widgetscreen.renderer.syncViewportSize();
	}
	
	@SuppressWarnings("unused")
	private static void tickIDSubItem(WidgetItem2DRender renderer, TextArea textArea, Label label)
	{
		ItemStack stack = renderer.getRenderStack();
		int damage = stack.getItemDamage();
		damage++;
		if (damage > 15)
		{
			damage = 0;
		}
		stack.setItemDamage(damage);
		
		Item item = stack.getItem();
		String stackName = IDResolver.getItemNameForStack(stack);
		StringBuilder tooltipText = new StringBuilder(String.format("Slot %-4s : Metadata %s: %s",
				stack.itemID, damage, StringTranslate.getInstance().translateNamedKey(stackName)));
		
		tooltipText.append(String.format("\r\n\r\nInternal name: %s", stackName));
		try
		{
			IDResolver.buildItemInfo(tooltipText, stack.itemID,
					IDResolver.getExistance(IDResolver.getPosition(stack.itemID), stack.itemID) == 1);
		} catch (Throwable e)
		{
			
		}
		
		GuiApiHelper.setTextAreaText(textArea, tooltipText.toString());
	}
	
	@SuppressWarnings("unused")
	private static void trimLooseSettings(ArrayList<String> unused)
	{
		WidgetSinglecolumn widgetSingleColumn = new WidgetSinglecolumn();
		widgetSingleColumn.childDefaultWidth = 250;
		SettingList s = new SettingList("unusedSettings", unused);
		WidgetList w = new WidgetList(s, "Loose Settings to Remove");
		w.listBox.setSelected(0);
		widgetSingleColumn.add(w);
		widgetSingleColumn.heightOverrideExceptions.put(w, 140);
		widgetSingleColumn.add(GuiApiHelper.makeButton("Remove Selected", "removeLooseSetting",
				IDResolver.class, true, new Class[] { SettingList.class }, s));
		
		GuiModScreen.show(new WidgetSimplewindow(widgetSingleColumn, "Loose Setting Removal"));
	}
	
	@SuppressWarnings("unused")
	private static void trimLooseSettingsAll(ArrayList<String> unused)
	{
		for (String key : unused)
		{
			IDResolver.knownIDs.remove(key);
			IDResolver.logger.log(Level.INFO, "Trimmed ID: " + key);
		}
		try
		{
			IDResolver.storeProperties();
		} catch (Throwable e)
		{
			IDResolver.logger.log(Level.INFO, "Could not save properties after trimming loose settings!", e);
		}
		IDResolver.reLoadModGui();
	}
	
	@SuppressWarnings("unused")
	private static void trimLooseSettingsAuto(ArrayList<String> unused)
	{
		Map<String, Boolean> classMap = new HashMap<String, Boolean>();
		ArrayList<Class> loadedMods = new ArrayList<Class>(ModLoader.getLoadedMods().size());
		for (Iterator iterator = ModLoader.getLoadedMods().iterator(); iterator.hasNext();)
		{
			loadedMods.add(iterator.next().getClass());
		}
		Boolean isMCP = IDResolver.class.getName().startsWith("net.minecraft.src.");
		for (String key : unused)
		{
			String[] info = IDResolver.getInfoFromSaveString(key);
			String classname = info[1];
			if (!classMap.containsKey(classname))
			{
				try
				{
					Class modClass;
					if (isMCP)
					{
						modClass = Class.forName("net.minecraft.src." + classname);
					} else
					{
						modClass = Class.forName(classname);
					}
					if (!loadedMods.contains(modClass))
					{
						IDResolver
								.appendExtraInfo("Unsure if I should trim IDs from "
										+ classname
										+ ": Class file is still found, but the mod is not loaded into ModLoader! If you wish to trim these IDs, please remove them with the Settings screen.");
					}
					classMap.put(classname, true);
				} catch (ClassNotFoundException e)
				{
					classMap.put(classname, false);
				}
			}
			if (!classMap.get(classname))
			{
				IDResolver.knownIDs.remove(key);
				IDResolver.logger.log(Level.INFO, "Trimmed ID: " + key);
			}
		}
		try
		{
			IDResolver.storeProperties();
		} catch (Throwable e)
		{
			IDResolver.logger.log(Level.INFO, "Could not save properties after trimming loose settings!", e);
		}
		IDResolver.reLoadModGui();
	}
	
	private static String trimMCP(String name)
	{
		if (name.startsWith("net.minecraft.src"))
		{
			name = name.substring(18);
		}
		return name;
	}
	
	private static String trimType(String input, boolean block)
	{
		String type = (block ? "BlockID." : "ItemID.");
		if (input.startsWith(type))
		{
			return input.substring(type.length());
		}
		return input;
	}
	
	private static void updateTickSettings()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - 'UpdateTickSettings' called.");
		if (IDResolver.modPriorities.containsKey("ShowTickMM"))
		{
			IDResolver.modPriorities.remove("ShowTickMM");
		}
		IDResolver.modPriorities.setProperty("ShowTickMM", IDResolver.showTickMM.get().toString());
		if (IDResolver.modPriorities.containsKey("ShowTickRS"))
		{
			IDResolver.modPriorities.remove("ShowTickRS");
		}
		IDResolver.modPriorities.setProperty("ShowTickRS", IDResolver.showTickRS.get().toString());
		
		if (IDResolver.modPriorities.containsKey("CheckForLooseSettings"))
		{
			IDResolver.modPriorities.remove("CheckForLooseSettings");
		}
		IDResolver.modPriorities.setProperty("CheckForLooseSettings", IDResolver.checkForLooseSettings.get()
				.toString());
		
		if (IDResolver.modPriorities.containsKey("ShowOnlyConflicts"))
		{
			IDResolver.modPriorities.remove("ShowOnlyConflicts");
		}
		IDResolver.modPriorities.setProperty("ShowOnlyConflicts", IDResolver.showOnlyConf.get().toString());
		try
		{
			IDResolver.storeProperties();
		} catch (Throwable e)
		{
			IDResolver.logger.log(Level.INFO, "IDResolver - Was unable to save settings.", e);
		}
	}
	
	public static Boolean wasBlockInited()
	{
		return IDResolver.wasBlockInited;
	}
	
	public static Boolean wasItemInited()
	{
		return IDResolver.wasItemInited;
	}
	
	@SuppressWarnings("unused")
	private static void wipeSavedIDsFromMenu(String modName)
	{
		IDResolver.getLogger().log(Level.INFO,
				"IDResolver - User pressed 'WipeSavedIDsFromMenu' button with " + modName);
		Vector<String> temp = new Vector<String>();
		for (Object key : IDResolver.knownIDs.keySet())
		{
			String string = (String) key;
			if ("SAVEVERSION".equals(key))
			{
				continue;
			}
			temp.add(string);
		}
		for (String key : temp)
		{
			if (IDResolver.getInfoFromSaveString(key)[1].equals(modName))
			{
				IDResolver.knownIDs.remove(key);
			}
		}
		try
		{
			IDResolver.storeProperties();
		} catch (Throwable e)
		{
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
	
	private Scrollbar scrollBar;
	private SettingInt settingIntNewID;
	
	private boolean specialItem = false;
	
	private Widget subscreenIDSetter;
	
	private IDResolver(int Startid, Block Offender)
	{
		if (IDResolver.initialized)
		{
			isBlock = true;
			requestedBlock = Offender;
		}
		originalID = Startid;
		longName = IDResolver.getlongName(requestedBlock, originalID);
	}
	
	private IDResolver(int Startid, Item Offender)
	{
		if (IDResolver.initialized)
		{
			isBlock = false;
			if (Offender instanceof ItemBlock)
			{
				specialItem = true;
			}
			requestedItem = Offender;
		}
		originalID = Startid;
		longName = IDResolver.getlongName(requestedItem, originalID);
	}
	
	private IDResolver(int currentID, String savedname)
	{
		if (IDResolver.initialized)
		{
			isBlock = IDResolver.isBlockType(savedname);
			String[] info = IDResolver.getInfoFromSaveString(IDResolver.trimType(savedname, isBlock));
			overrideName = "ID " + info[0] + " from " + info[1];
			longName = savedname;
			originalID = currentID;
		}
	}
	
	private IDResolver(String name, boolean block)
	{
		if (IDResolver.initialized)
		{
			isBlock = block;
			String[] info = IDResolver.getInfoFromSaveString(name);
			overrideName = "ID " + info[0] + " from " + info[1];
		}
	}
	
	@SuppressWarnings("unused")
	private void autoAssign()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'AutoAssign' button.");
		autoAssign(false, false);
	}
	
	private void autoAssign(boolean skipMessage, boolean reverse)
	{
		IDResolver.getLogger().log(
				Level.INFO,
				"IDResolver - Automatically assigning ID: Skip Message: " + skipMessage + " Reverse: "
						+ reverse);
		int firstid = (isBlock ? (reverse ? IDResolver.getLastOpenBlock() : IDResolver.getFirstOpenBlock())
				: (reverse ? IDResolver.getLastOpenItem() : IDResolver.getFirstOpenItem()));
		IDResolver.getLogger().log(Level.INFO, "IDResolver - Automatic assign returned new ID " + firstid);
		if (firstid == -1)
		{
			return;
		}
		settingIntNewID.set(firstid);
		if (skipMessage)
		{
			running = false;
		} else
		{
			displayMessage("Automatically assigned ID " + Integer.toString(firstid) + " for " + getName());
		}
	}
	
	@SuppressWarnings("unused")
	private void autoAssignAll()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'AutoAssignAll' button.");
		IDResolver.autoAssignMod = IDResolver.getInfoFromSaveString(longName)[1];
		autoAssign(true, false);
		displayMessage("Automatically assigning IDs...");
	}
	
	@SuppressWarnings("unused")
	private void autoAssignAllRev()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'AutoAssignAllRev' button.");
		IDResolver.autoAssignMod = "!" + IDResolver.getInfoFromSaveString(longName)[1];
		autoAssign(true, true);
		displayMessage("Automatically assigning IDs...");
	}
	
	@SuppressWarnings("unused")
	private void autoAssignRev()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'AutoAssignRev' button.");
		autoAssign(false, true);
	}
	
	@SuppressWarnings("unused")
	private void cancel()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'Cancel' button.");
		settingIntNewID = null;
		running = false;
	}
	
	private void displayMessage(String msg)
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - Message Displayed: " + msg);
		oldsubscreenIDSetter = subscreenIDSetter;
		
		WidgetSinglecolumn column = new WidgetSinglecolumn(new Widget[0]);
		{
			TextArea textarea = GuiApiHelper.makeTextArea(msg, false);
			column.add(textarea);
			column.heightOverrideExceptions.put(textarea, 0);
			column.add(GuiApiHelper.makeButton("Continue", "finish", this, true));
		}
		
		WidgetSimplewindow window = new WidgetSimplewindow(column, "ID Resolver Notice", false);
		
		WidgetTick ticker = new WidgetTick();
		window.add(ticker);
		ticker.addTimedCallback(new ModAction(this, "previousScreen"), 5000);
		
		subscreenIDSetter = window;
		
		GuiWidgetScreen screen = GuiWidgetScreen.getInstance();
		screen.resetScreen();
		screen.setScreen(subscreenIDSetter);
		
		loadBackground(screen);
	}
	
	@SuppressWarnings("unused")
	private void finish()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'Finish' button.");
		running = false;
	}
	
	private String getBlockName(Block block)
	{
		try
		{
			String name = StringTranslate.getInstance().translateNamedKey(
					IDResolver.getItemNameForStack(new ItemStack(block)));
			if ((name != null) && (name.length() != 0))
			{
				return name;
			}
			if (!IDResolver.intHasStoredID(block.blockID, true))
			{
				return IDResolver.getItemNameForStack(new ItemStack(block));
			}
		} catch (Throwable e)
		{
		}
		String[] info = IDResolver
				.getInfoFromSaveString((IDResolver.intHasStoredID(block.blockID, true) ? IDResolver
						.getStoredIDName(block.blockID, true, true) : IDResolver.getLongBlockName(block,
						originalID)));
		return "ID " + info[0] + " (Class: " + block.getClass().getName() + ") from " + info[1];
	}
	
	private String getItemName(Item item)
	{
		try
		{
			String name = StringTranslate.getInstance().translateNamedKey(
					IDResolver.getItemNameForStack(new ItemStack(item)));
			if ((name != null) && (name.length() != 0))
			{
				return name;
			}
			if (!IDResolver.intHasStoredID(item.shiftedIndex, true))
			{
				return IDResolver.getItemNameForStack(new ItemStack(item));
			}
		} catch (Throwable e)
		{
		}
		String[] info = IDResolver
				.getInfoFromSaveString((IDResolver.intHasStoredID(item.shiftedIndex, false) ? IDResolver
						.getStoredIDName(item.shiftedIndex, false, true) : IDResolver.getLongItemName(item,
						originalID)));
		return "ID " + info[0] + " (Class: " + item.getClass().getName() + ") from " + info[1];
	}
	
	private String getName()
	{
		String name = "";
		if (overrideName != null)
		{
			return overrideName;
		}
		if (isBlock)
		{
			if (requestedBlock != null)
			{
				name = getBlockName(requestedBlock);
			} else
			{
				String[] info = IDResolver
						.getInfoFromSaveString(IDResolver.getStoredIDName(originalID, true));
				name = "ID " + info[0] + " from " + info[1];
			}
		} else
		{
			if (requestedItem != null)
			{
				name = getItemName(requestedItem);
			} else
			{
				String[] info = IDResolver.getInfoFromSaveString(IDResolver
						.getStoredIDName(originalID, false));
				name = "ID " + info[0] + " from " + info[1];
			}
		}
		if ((name == null) || "".equals(name))
		{
			name = longName;
		}
		return name;
	}
	
	private int getStored()
	{
		return Integer.parseInt(IDResolver.knownIDs.getProperty(longName));
	}
	
	private String getTypeName()
	{
		return IDResolver.getTypeName(isBlock);
	}
	
	private boolean hasOpenSlot()
	{
		return ((isBlock ? IDResolver.getFirstOpenBlock() : IDResolver.getFirstOpenItem()) != -1)
				|| specialItem;
	}
	
	private boolean hasStored()
	{
		return IDResolver.knownIDs.containsKey(longName);
	}
	
	@SuppressWarnings("unused")
	private void itemForceOverwrite()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'ItemForceOverwrite' button.");
		running = false;
	}
	
	private void loadBackground(GuiWidgetScreen widgetscreen)
	{
		try
		{
			Texture tex = widgetscreen.renderer.load(
					Minecraft.class.getClassLoader().getResource("gui/background.png"), Format.RGB,
					Filter.NEAREST);
			Image img = tex.getImage(0, 0, tex.getWidth(), tex.getHeight(), Color.parserColor("#303030"),
					true, Texture.Rotation.NONE);
			if (img != null)
			{
				subscreenIDSetter.setBackground(img);
			}
		} catch (Throwable e)
		{
			IDResolver.logger.log(Level.INFO, "IDResolver - Failed to load background.");
		}
	}
	
	@SuppressWarnings("unused")
	private void menuDeleteSavedID()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'MenuDeleteSavedID' button.");
		String oldname = IDResolver.getStoredIDName(settingIntNewID.get(), isBlock, false);
		IDResolver.knownIDs.remove(oldname);
		settingIntNewID = null;
		running = false;
		try
		{
			IDResolver.storeProperties();
		} catch (Throwable e)
		{
			IDResolver.logger.log(Level.INFO, "IDResolver - Was unable to store settings for "
					+ getTypeName() + " " + getName() + " due to an exception.", e);
		}
	}
	
	@SuppressWarnings("unused")
	private void overrideOld()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'OverrideOld' button.");
		overridingSetting = true;
		Integer ID = settingIntNewID.get();
		if ((isBlock && (Block.blocksList[ID] == null)) || (!isBlock && (Item.itemsList[ID] == null)))
		{
			
			displayMessage("Override requested for "
					+ getTypeName()
					+ " at slot "
					+ ID
					+ ", but it is currently just a setting. Overriding now, IDResolver will ask later for the new block ID.");
		} else
		{
			running = false;
		}
	}
	
	@SuppressWarnings("unused")
	private void previousScreen()
	{
		subscreenIDSetter = oldsubscreenIDSetter;
		running = false;
	}
	
	private void priorityConflict(String newobject, String oldobject, Boolean isTypeBlock)
	{
		oldsubscreenIDSetter = subscreenIDSetter;
		subscreenIDSetter = new WidgetSinglecolumn(new Widget[0]);
		{
			subscreenIDSetter.add(new Label(""));
			SimpleTextAreaModel model = new SimpleTextAreaModel();
			model.setText(
					String.format(
							"There is a mod priority conflict for a %s between two mods. Both has the same priority set. Please select which should take priority.",
							IDResolver.getTypeName(isTypeBlock)), false);
			TextArea textarea = new TextArea(model);
			subscreenIDSetter.add(textarea);
			((WidgetSinglecolumn) subscreenIDSetter).heightOverrideExceptions.put(textarea, 0);
			String[] info = IDResolver.getInfoFromSaveString(newobject);
			subscreenIDSetter.add(GuiApiHelper.makeButton("ID " + info[0] + " from " + info[1],
					"priorityResolver", this, true, new Class[] { Boolean.class }, true));
			info = IDResolver.getInfoFromSaveString(oldobject);
			subscreenIDSetter.add(GuiApiHelper.makeButton("ID " + info[0] + " from " + info[1],
					"priorityResolver", this, true, new Class[] { Boolean.class }, false));
		}
		GuiWidgetScreen screen = GuiWidgetScreen.getInstance();
		screen.resetScreen();
		screen.setScreen(subscreenIDSetter);
		loadBackground(screen);
	}
	
	@SuppressWarnings("unused")
	private void priorityResolver(Boolean overrideold)
	{
		IDResolver.getLogger().log(Level.INFO,
				"IDResolver - User pressed 'PriorityResolver' button with " + overrideold.toString());
		if (overrideold)
		{
			overridingSetting = true;
			running = false;
		} else
		{
			subscreenIDSetter = oldsubscreenIDSetter;
			GuiWidgetScreen widgetscreen = GuiWidgetScreen.getInstance();
			widgetscreen.resetScreen();
			widgetscreen.setScreen(subscreenIDSetter);
			loadBackground(widgetscreen);
		}
	}
	
	@SuppressWarnings("unused")
	private void raisePriorityAndOK()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'RaisePriorityAndOK' button.");
		String modname = IDResolver.getInfoFromSaveString(longName)[1];
		displayMessage(modname + " is now specified as a Priority Mod Level "
				+ IDResolver.raiseModPriority(modname));
	}
	
	@SuppressWarnings("unused")
	private void resetIDtoDefault()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'ResetIDtoDefault' button.");
		settingIntNewID.set(originalID);
		updateUI();
	}
	
	private void runConflict() throws Exception
	{
		if (specialItem)
		{
			return;
		}
		Minecraft mc = ModLoader.getMinecraftInstance();
		if (mc == null)
		{
			IDResolver
					.appendExtraInfo("Warning: When resolving "
							+ longName
							+ " ID resolver detected that the Minecraft object was NULL! Assuming 'special' object handling. Please report this!");
			return;
		}
		if (IDResolver.shutdown)
		{
			IDResolver
					.getLogger()
					.log(Level.INFO,
							"IDResolver - IDResolver is attempting to shut down due to user request, skipping assignment.");
			throw new Exception("Minecraft is shutting down.");
		}
		
		running = true;
		if (IDResolver.autoAssignMod != null)
		{
			boolean rev = IDResolver.autoAssignMod.startsWith("!");
			if (IDResolver.getInfoFromSaveString(longName)[1].equals(IDResolver.autoAssignMod
					.substring(rev ? 1 : 0)))
			{
				autoAssign(true, rev);
			}
		}
		if (!hasOpenSlot())
		{
			IDResolver.logger.log(Level.INFO, "IDResolver - no open slots are available.");
			throw new RuntimeException("No open " + getTypeName() + " IDs are available.");
		}
		GuiWidgetScreen widgetscreen = GuiWidgetScreen.getInstance();
		IDResolver.syncMinecraftScreen(mc, widgetscreen);
		int priority = IDResolver.getModPriority(IDResolver.getInfoFromSaveString(longName)[1]);
		boolean restart = false;
		do
		{
			widgetscreen.resetScreen();
			widgetscreen.setScreen(subscreenIDSetter);
			loadBackground(widgetscreen);
			if (restart)
			{
				running = true;
			}
			updateUI();
			Font fnt = widgetscreen.theme.getDefaultFont();
			restart = false;
			mod_IDResolver.updateUsed();
			if (priority > 0)
			{
				if (IDResolver.intHasStoredID(settingIntNewID.defaultValue, isBlock))
				{
					String otherobject = IDResolver.getStoredIDName(settingIntNewID.defaultValue, isBlock,
							true);
					String otherclass = IDResolver.getInfoFromSaveString(otherobject)[1];
					int otherpri = IDResolver.getModPriority(otherclass);
					if (priority > otherpri)
					{
						running = false;
						overridingSetting = true;
						IDResolver.logger.log(
								Level.INFO,
								"IDResolver - Override will be called due to mod priority for "
										+ IDResolver.getInfoFromSaveString(longName)[1]
										+ " is greater than for " + otherclass);
					} else if (priority == otherpri)
					{
						priorityConflict(IDResolver.trimType(longName, isBlock), otherobject, isBlock);
					}
				} else
				{
					running = false;
					if (!((isBlock && (Block.blocksList[settingIntNewID.defaultValue] == null)) || (!isBlock && (Item.itemsList[settingIntNewID.defaultValue] == null))))
					{
						IDResolver.logger.log(
								Level.INFO,
								"IDResolver - Override will be called due to mod priority for "
										+ IDResolver.getInfoFromSaveString(longName)[1]);
					} else
					{
						IDResolver.logger.log(Level.INFO,
								"IDResolver - Automatically returning default ID due to mod priority for "
										+ IDResolver.getInfoFromSaveString(longName)[1]);
					}
				}
			}
			if (IDResolver.showOnlyConflicts())
			{
				if (resolveScreenContinue.isEnabled())
				{
					running = false;
					IDResolver.logger.log(Level.INFO,
							"IDResolver - Automatically returning default ID as no conflict exists.");
				}
			}
			widgetscreen.layout();
			boolean wasRunning = mc.running;
			while (running)
			{
				
				if (((mc.displayWidth != mc.mcCanvas.getWidth()) || (mc.displayHeight != mc.mcCanvas
						.getHeight())))
				{
					IDResolver.syncMinecraftScreen(mc, widgetscreen);
				}
				
				widgetscreen.gui.update();
				if ((fnt != null) && IDResolver.showTick(false))
				{
					widgetscreen.renderer.startRendering();
					String[] ids = mod_IDResolver.getIDs();
					for (int i = 0; i < ids.length; i++)
					{
						fnt.drawText(null, 1, 12 + (i * 12), ids[i]);
					}
					widgetscreen.renderer.endRendering();
				}
				Display.update();
				Thread.yield();
				if (!mc.running)
				{
					if (wasRunning)
					{
						IDResolver.shutdown = true;
					}
					
					if (!IDResolver.shutdown && IDResolver.attemptedRecover)
					{
						IDResolver.shutdown = true;
					}
					if (IDResolver.shutdown)
					{
						IDResolver
								.getLogger()
								.log(Level.INFO,
										"IDResolver - Minecraft has reported that it is shutting down, skipping assignment.");
						settingIntNewID = null;
						running = false;
					} else
					{
						
						displayMessage(IDResolver.langMinecraftShuttingDown);
						IDResolver.shutdown = false;
						mc.running = true;
						IDResolver.attemptedRecover = true;
					}
				}
			}
			if (IDResolver.shutdown)
			{
				mc.running = false;
			}
			if (settingIntNewID != null)
			{
				Integer ID = settingIntNewID.get();
				if (!overridingSetting)
				{
					IDResolver.knownIDs.setProperty(longName, ID.toString());
				} else
				{
					String oldname = IDResolver.getStoredIDName(ID, isBlock, false);
					if ((isBlock && (Block.blocksList[ID] == null))
							|| (!isBlock && (Item.itemsList[ID] == null)))
					{
						IDResolver.knownIDs.remove(oldname);
						IDResolver.knownIDs.setProperty(longName, ID.toString());
						IDResolver.logger
								.log(Level.INFO,
										"IDResolver - Override requested for "
												+ getTypeName()
												+ " at slot "
												+ ID
												+ ", but it is currently just a setting. Overriding now, IDResolver will ask later for the new block ID.");
					} else
					{
						IDResolver.logger.log(Level.INFO,
								"IDResolver - Overriding setting. Requesting new ID for old " + getTypeName()
										+ " at slot " + ID + ".");
						IDResolver resolver = null;
						if (isBlock)
						{
							resolver = new IDResolver(ID, Block.blocksList[ID]);
						} else
						{
							resolver = new IDResolver(ID, Item.itemsList[ID]);
						}
						resolver.longName = oldname;
						resolver.disableOverride = true;
						resolver.setupGui(ID);
						IDResolver.knownIDs.remove(oldname);
						IDResolver.knownIDs.setProperty(longName, ID.toString());
						resolver.updateUI();
						resolver.runConflict();
						Integer oldid = resolver.settingIntNewID.get();
						if (resolver.settingIntNewID == null)
						{
							IDResolver.logger.log(Level.INFO,
									"IDResolver - User cancelled request for override. Resetting.");
							IDResolver.knownIDs.remove(longName);
							IDResolver.knownIDs.setProperty(oldname, ID.toString());
							restart = true;
							continue;
						}
						IDResolver.logger.log(Level.INFO, "IDResolver - Overriding " + getTypeName()
								+ " as requested.");
						if (isBlock)
						{
							IDResolver.overrideBlockID(ID, oldid);
						} else
						{
							IDResolver.overrideItemID(ID, oldid);
						}
						IDResolver.logger.log(Level.INFO,
								"IDResolver - Sucessfully overrode IDs. Setting ID " + ID + " for "
										+ getName() + ", Overriding " + resolver.getName() + " to " + oldid
										+ " as requested.");
					}
				}
				try
				{
					IDResolver.storeProperties();
				} catch (Throwable e)
				{
					IDResolver.logger.log(Level.INFO, "IDResolver - Was unable to store settings for "
							+ getTypeName() + " " + getName() + " due to an exception.", e);
				}
			}
		} while (restart);
		widgetscreen.resetScreen();
	}
	
	private void runConflictMenu()
	{
		running = true;
		GuiModScreen modscreen;
		GuiWidgetScreen widgetscreen = GuiWidgetScreen.getInstance();
		boolean restart = false;
		Minecraft mc = ModLoader.getMinecraftInstance();
		if (!mc.running)
		{
			IDResolver.getLogger().log(Level.INFO,
					"IDResolver - Minecraft has reported that it is shutting down, skipping assignment.");
			settingIntNewID = null;
			GuiModScreen.back();
			return;
		}
		do
		{
			modscreen = new GuiModScreen(GuiModScreen.currentScreen, subscreenIDSetter);
			GuiModScreen.show(modscreen);
			if (restart)
			{
				running = true;
			}
			updateUI();
			Font fnt = widgetscreen.theme.getDefaultFont();
			restart = false;
			mod_IDResolver.updateUsed();
			if (isMenu)
			{
				if (mc.theWorld != null)
				{
					displayMessage("You cannot modify IDs while in game. Please exit to main menu and try again.");
				}
			}
			while (running)
			{
				
				if ((mc.displayWidth != mc.mcCanvas.getWidth())
						|| (mc.displayHeight != mc.mcCanvas.getHeight()))
				{
					mc.displayWidth = mc.mcCanvas.getWidth();
					mc.displayHeight = mc.mcCanvas.getHeight();
					((MinecraftImpl) mc).mcFrame.pack();
				}
				
				modscreen.drawScreen(0, 0, 0);
				if ((fnt != null) && IDResolver.showTick(false))
				{
					widgetscreen.renderer.startRendering();
					String[] ids = mod_IDResolver.getIDs();
					for (int i = 0; i < ids.length; i++)
					{
						fnt.drawText(null, 1, 12 + (i * 12), ids[i]);
					}
					widgetscreen.renderer.endRendering();
				}
				Display.update();
				Thread.yield();
				if (!mc.running)
				{
					IDResolver
							.getLogger()
							.log(Level.INFO,
									"IDResolver - Minecraft has reported that it is shutting down, skipping assignment.");
					settingIntNewID = null;
					running = false;
				}
			}
			if (settingIntNewID != null)
			{
				Integer ID = settingIntNewID.get();
				if (!overridingSetting)
				{
					IDResolver.knownIDs.setProperty(longName, ID.toString());
					if (isBlock)
					{
						IDResolver.overrideBlockID(settingIntNewID.defaultValue, ID);
					} else
					{
						IDResolver.overrideItemID(settingIntNewID.defaultValue, ID);
					}
				} else
				{
					String oldname = IDResolver.getStoredIDName(ID, isBlock, false);
					if ((isBlock && (Block.blocksList[ID] == null))
							|| (!isBlock && (Item.itemsList[ID] == null)))
					{
						IDResolver.knownIDs.remove(oldname);
						IDResolver.knownIDs.setProperty(longName, ID.toString());
						IDResolver.logger
								.log(Level.INFO,
										"IDResolver - Override requested for "
												+ getTypeName()
												+ " at slot "
												+ ID
												+ ", but it is currently just a setting. Removing the old setting as it may be a loose end.");
						if (isBlock)
						{
							IDResolver.overrideBlockID(settingIntNewID.defaultValue, ID);
						} else
						{
							IDResolver.overrideItemID(settingIntNewID.defaultValue, ID);
						}
					} else
					{
						IDResolver.logger.log(Level.INFO,
								"IDResolver - Overriding setting. Requesting new ID for old " + getTypeName()
										+ " at slot " + ID + ".");
						Object oldObject = null;
						IDResolver resolver = null;
						if (isBlock)
						{
							resolver = new IDResolver(ID, Block.blocksList[ID]);
							oldObject = Block.blocksList[settingIntNewID.defaultValue];
							Block.blocksList[settingIntNewID.defaultValue] = null;
						} else
						{
							resolver = new IDResolver(ID, Item.itemsList[ID]);
							oldObject = Item.itemsList[settingIntNewID.defaultValue];
							Item.itemsList[settingIntNewID.defaultValue] = null;
						}
						resolver.longName = oldname;
						resolver.disableOverride = true;
						resolver.setupGui(ID);
						IDResolver.knownIDs.remove(oldname);
						IDResolver.knownIDs.setProperty(longName, ID.toString());
						resolver.updateUI();
						resolver.runConflictMenu();
						Integer oldid = resolver.settingIntNewID.get();
						if (isBlock)
						{
							Block.blocksList[ID] = (Block) oldObject;
							oldObject = Block.blocksList[oldid];
							Block.blocksList[oldid] = Block.blocksList[ID];
							Block.blocksList[ID] = null;
						} else
						{
							Item.itemsList[ID] = (Item) oldObject;
							oldObject = Item.itemsList[oldid];
							Item.itemsList[oldid] = Item.itemsList[ID];
							Item.itemsList[ID] = null;
						}
						if (resolver.settingIntNewID == null)
						{
							IDResolver.logger.log(Level.INFO,
									"IDResolver - User cancelled request for override. Resetting.");
							IDResolver.knownIDs.remove(longName);
							IDResolver.knownIDs.setProperty(oldname, ID.toString());
							restart = true;
							continue;
						}
						IDResolver.logger.log(Level.INFO, "IDResolver - Overriding " + getTypeName()
								+ " as requested.");
						if (isBlock)
						{
							IDResolver.overrideBlockID(oldid, ID);
						} else
						{
							IDResolver.overrideItemID(oldid, ID);
						}
						if (isBlock)
						{
							Block.blocksList[oldid] = (Block) oldObject;
						} else
						{
							Item.itemsList[oldid] = (Item) oldObject;
						}
						IDResolver.logger.log(Level.INFO,
								"IDResolver - Sucessfully overrode IDs. Setting ID " + ID + " for "
										+ getName() + ", Overriding " + resolver.getName() + " to " + oldid
										+ " as requested.");
					}
				}
				try
				{
					IDResolver.storeProperties();
				} catch (Throwable e)
				{
					IDResolver.logger.log(Level.INFO, "IDResolver - Was unable to store settings for "
							+ getTypeName() + " " + getName() + " due to an exception.", e);
				}
			}
		} while (restart);
		GuiModScreen.back();
	}
	
	private void setupGui(int RequestedID)
	{
		subscreenIDSetter = new WidgetSinglecolumn(new Widget[0]);
		{
			overrideName = getName();
			SimpleTextAreaModel model = new SimpleTextAreaModel();
			if (!isMenu)
			{
				model.setText("New " + getTypeName() + " detected. Select ID for " + overrideName, false);
			} else
			{
				model.setText("Select ID for " + overrideName, false);
			}
			TextArea area = new TextArea(model);
			subscreenIDSetter.add(area);
			((WidgetSinglecolumn) subscreenIDSetter).heightOverrideExceptions.put(area, 0);
			settingIntNewID = new SettingInt("New ID", RequestedID, (isBlock || specialItem ? 1
					: Item.shovelSteel.shiftedIndex), 1,
					(isBlock || specialItem ? Block.blocksList.length - 1 : Item.itemsList.length - 1));
			settingIntNewID.defaultValue = RequestedID;
			WidgetInt intdisplay = new WidgetInt(settingIntNewID, "New ID");
			subscreenIDSetter.add(intdisplay);
			intdisplay.slider.getModel().addCallback(new ModAction(this, "updateUI"));
			intdisplay.slider.setCanEdit(true);
			
			scrollBar = new Scrollbar(Orientation.HORIZONTAL);
			subscreenIDSetter.add(scrollBar);
			scrollBar.setMinMaxValue(settingIntNewID.minimumValue, settingIntNewID.maximumValue);
			scrollBar.setValue(settingIntNewID.get());
			scrollBar.addCallback(new ModAction(this, "updateUISB"));
			
			model = new SimpleTextAreaModel();
			model.setText("", false);
			resolveScreenLabel = new TextArea(model);
			subscreenIDSetter.add(resolveScreenLabel);
			resolveScreenContinue = GuiApiHelper
					.makeButton("Save and Continue loading", "finish", this, true);
			resolveScreenContinue.setEnabled(false);
			subscreenIDSetter.add(resolveScreenContinue);
			if (!disableOverride)
			{
				resolveScreenOverride = GuiApiHelper.makeButton("Override old setting", "overrideOld", this,
						true);
				resolveScreenOverride.setEnabled(IDResolver.intHasStoredID(RequestedID, isBlock)
						&& IDResolver.overridesEnabled);
				subscreenIDSetter.add(resolveScreenOverride);
			}
			
			if (!isBlock && !isMenu)
			{
				subscreenIDSetter.add(GuiApiHelper.makeButton("Force Overwrite", "itemForceOverwrite", this,
						true));
			}
			
			if (isMenu)
			{
				subscreenIDSetter.add(GuiApiHelper.makeButton("Delete saved ID", "menuDeleteSavedID", this,
						true));
			}
			subscreenIDSetter.add(GuiApiHelper.makeButton("Automatically assign an ID", "autoAssign", this,
					true));
			subscreenIDSetter.add(GuiApiHelper.makeButton("Automatically assign an ID in Reverse",
					"autoAssignRev", this, true));
			if (!disableAutoAll)
			{
				Button assignallbuttons = GuiApiHelper.makeButton(
						"Automatically assign an ID to All\r\nfrom this mod", "autoAssignAll", this, true);
				subscreenIDSetter.add(assignallbuttons);
				
				((WidgetSinglecolumn) subscreenIDSetter).heightOverrideExceptions.put(assignallbuttons, 30);
				
				assignallbuttons = GuiApiHelper.makeButton(
						"Automatically assign an ID to All from\r\nthis mod in Reverse", "autoAssignAllRev",
						this, true);
				
				subscreenIDSetter.add(assignallbuttons);
				
				((WidgetSinglecolumn) subscreenIDSetter).heightOverrideExceptions.put(assignallbuttons, 30);
				
				if (IDResolver.getModPriority(IDResolver.getInfoFromSaveString(longName)[1]) < Integer.MAX_VALUE)
				{
					subscreenIDSetter.add(GuiApiHelper.makeButton("Raise the priority for this mod",
							"raisePriorityAndOK", this, true));
				}
			}
			subscreenIDSetter.add(GuiApiHelper.makeButton("Reset to default ID", "resetIDtoDefault", this,
					true));
			subscreenIDSetter.add(GuiApiHelper.makeButton("Cancel and Return", "cancel", this, true));
		}
		subscreenIDSetter = new ScrollPane(subscreenIDSetter);
		((ScrollPane) subscreenIDSetter).setFixed(ScrollPane.Fixed.HORIZONTAL);
	}
	
	private void updateUI()
	{
		int ID = settingIntNewID.get();
		scrollBar.setValue(ID, false);
		String Name = null;
		try
		{
			if (isBlock)
			{
				Block selectedBlock = Block.blocksList[ID];
				if (selectedBlock != null)
				{
					Name = getBlockName(selectedBlock);
				}
			}
			if (Name == null)
			{
				Item selectedItem = Item.itemsList[ID];
				if (selectedItem != null)
				{
					Name = getItemName(selectedItem);
				}
			}
			if (Name == null)
			{
				if (isBlock && IDResolver.intHasStoredID(ID, true))
				{
					String[] info = IDResolver.getInfoFromSaveString(IDResolver.getStoredIDName(ID, true));
					Name = "ID " + info[0] + " from " + info[1];
				} else
				{
					if (IDResolver.intHasStoredID(ID, false))
					{
						String[] info = IDResolver.getInfoFromSaveString(IDResolver
								.getStoredIDName(ID, false));
						Name = "ID " + info[0] + " from " + info[1];
					}
				}
			}
			
		} catch (Throwable e)
		{
			Name = "ERROR";
		}
		boolean originalmenu = (isMenu && (ID == settingIntNewID.defaultValue));
		if (!disableOverride)
		{
			resolveScreenOverride.setEnabled(IDResolver.intHasStoredID(ID, isBlock)
					&& IDResolver.overridesEnabled && !originalmenu);
		}
		if (!originalmenu)
		{
			if (Name == null)
			{
				GuiApiHelper.setTextAreaText(resolveScreenLabel,
						getTypeName() + " ID " + Integer.toString(ID) + " is available!");
				resolveScreenContinue.setEnabled(true);
			} else
			{
				GuiApiHelper.setTextAreaText(resolveScreenLabel,
						getTypeName() + " ID " + Integer.toString(ID) + " is used by " + Name + ".");
				resolveScreenContinue.setEnabled(false);
			}
		} else
		{
			GuiApiHelper.setTextAreaText(resolveScreenLabel, "This is the currently saved ID.");
			resolveScreenContinue.setEnabled(true);
		}
	}
	
	@SuppressWarnings("unused")
	private void updateUISB()
	{
		settingIntNewID.set(scrollBar.getValue());
	}
	
	@SuppressWarnings("unused")
	private void upPrioritizeMod()
	{
		IDResolver.getLogger().log(Level.INFO, "IDResolver - User pressed 'UpPrioritizeMod' button.");
		String classname = IDResolver.getInfoFromSaveString(longName)[1];
		Boolean override = false;
		if (isBlock)
		{
			Block selectedBlock = Block.blocksList[settingIntNewID.defaultValue];
			if (selectedBlock != null)
			{
				override = getBlockName(selectedBlock) != null;
			}
		} else
		{
			Item selectedItem = Item.itemsList[settingIntNewID.defaultValue];
			if (selectedItem != null)
			{
				override = getItemName(selectedItem) != null;
			}
		}
		if (IDResolver.intHasStoredID(settingIntNewID.defaultValue, isBlock))
		{
			override = true;
		}
		overridingSetting = override;
		settingIntNewID.reset();
		displayMessage(classname + " is now specified as a Priority Mod Level "
				+ IDResolver.raiseModPriority(classname));
	}
}
