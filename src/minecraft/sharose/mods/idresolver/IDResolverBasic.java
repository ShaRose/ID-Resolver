package sharose.mods.idresolver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import cpw.mods.fml.common.Mod;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.src.BaseMod;

public class IDResolverBasic {

	public static boolean isHeadless()
	{
		return IDResolverCorePlugin.isServer;
	}
	
	protected static Field blockIdField;
	protected static boolean initialized;
	protected static Field itemIdField;
	protected static Properties knownIDs;
	protected static boolean[] vanillaIDs = new boolean[35840];
	protected static Boolean wasBlockInited = false;
	protected static Boolean wasItemInited = false;
	protected static StackTraceElement[] lastStackTrace;
	protected static int lastStackTraceID;
	protected static boolean overridesEnabled;
	protected static Logger logger;
	protected static String settingsComment;
	
	static
	{
		IDResolverBasic.logger = Logger.getLogger("IDResolver");
		settingsComment = "IDResolver Known / Set IDs file. Please do not edit manually.";
		overridesEnabled = true;
		initialized = false;
		setupOverrides();
		if(isHeadless())
		{
			reloadIDs();
		}
	}
	
	protected static void reloadIDs() {
		IDResolverBasic.knownIDs = new Properties();
		try {
			File idPath = new File("config/IDResolverknownIDs.properties").getAbsoluteFile();

			idPath.getParentFile().mkdirs();
			
			if (idPath.createNewFile()) {
				throw new RuntimeException("No mappings found! Please make sure mappings file in located in '" + idPath + "'.");
			} else {
				try {
					FileInputStream stream = new FileInputStream(
							idPath);
					IDResolverBasic.knownIDs.load(stream);
					stream.close();
					IDResolverBasic.logger.log(
							Level.INFO,
							"Loaded "
									+ Integer.toString(IDResolverBasic.knownIDs
											.size()) + " IDs sucessfully.");

					String version = IDResolverBasic.knownIDs
							.getProperty("SAVEVERSION");

					if (version == null) {
						IDResolverBasic.logger
								.log(Level.INFO,
										"Invalid settings file: Please upconvert with client version of ID Resolver.");
						throw new RuntimeException("Invalid settings file: Please upconvert mappings with client version of ID Resolver.");
					}
				} catch (IOException e) {
					throw new RuntimeException("Exception while loading mappings.",e);
				}
			}
			IDResolverBasic.initialized = true;

		} catch (Throwable e) {
			IDResolverBasic.logger.log(Level.INFO,
					"Error while initalizing settings.", e);
			IDResolverBasic.initialized = false;
			throw new RuntimeException("Exception while loading mappings.",e);
		}
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
		if(!isHeadless())
		{
			return IDResolver.getConflictedBlockID(RequestedID, newBlock);
		}
		if (newBlock == null) {
			return RequestedID;
		}

		if (!isModObject(RequestedID, true)) { 
			return RequestedID;
		}
		if (IDResolverBasic.vanillaIDs[RequestedID]) {
			return RequestedID;
		}
		String longName = getlongName(newBlock,RequestedID);
		if(!hasStored(longName))
		{
			throw new RuntimeException("IDResolver - No mapping found for signature " + longName+ ", please make sure mappings are complete.");
		}
		return getStored(longName);
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
		if(!isHeadless())
		{
			return IDResolver.getConflictedItemID(RequestedID, newItem);
		}
		if (newItem == null) {
			return RequestedID;
		}

		if (!isModObject(RequestedID, true)) {
			return RequestedID;
		}
		if (IDResolverBasic.vanillaIDs[RequestedID]) {
			return RequestedID;
		}
		String longName = getlongName(newItem,RequestedID);
		
		if (newItem instanceof ItemBlock) {
			return RequestedID;
		}
		if(!hasStored(longName))
		{
			throw new RuntimeException("IDResolver - No mapping found for signature " + longName+ ", please make sure mappings are complete.");
		}
		return getStored(longName);
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
		if (block && !wasBlockInited) {
			wasBlockInited = true;
		} else if (!block && !wasItemInited) {
			wasItemInited = true;
		}
		return isModObject(ID, block);
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
	protected static String getLongBlockName(Block block, int originalrequestedID) {

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
			if (IDResolverBasic.class.isAssignableFrom(exceptionclass)) {
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
	protected static String getLongItemName(Item item, int originalrequestedID) {
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
			if (IDResolverBasic.class.isAssignableFrom(exceptionclass)) {
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
	protected static String getlongName(Object obj, int ID) {
		String name = null;
		if (obj instanceof Block) {
			name = "BlockID." + getLongBlockName((Block) obj, ID);
		}
		if (obj instanceof Item) {
			name = "ItemID." + getLongItemName((Item) obj, ID);
		}
		if(!isHeadless())
		{
			IDResolver.loadedEntries.add(name);
		}
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
	 * Internal method to scanning the stack to see if an Item or Block is a
	 * vanilla addition or not. This also sets the vanilla array.
	 * 
	 * @param id
	 *            the Original ID. Used for cache sanity checks later.
	 * @param isBlock
	 *            Whether to check for a Block or an Item.
	 * @return True if it's a mod object, false if not.
	 */
	protected static boolean isModObject(int id, boolean isBlock) {
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
			IDResolverBasic.vanillaIDs[id] = true;
		}
		return false;
	}
	
	protected static boolean hasStored(String name) {
		return IDResolverBasic.knownIDs.containsKey(name);
	}
	
	protected static int getStored(String name) {
		return Integer.parseInt(IDResolverBasic.knownIDs.getProperty(name));
	}

	
	public static Boolean wasBlockInited() {
		return IDResolverBasic.wasBlockInited;
	}

	public static Boolean wasItemInited() {
		return IDResolverBasic.wasItemInited;
	}
	
	protected static void setupOverrides() {
		int pubfinalmod = Modifier.FINAL + Modifier.PUBLIC;
		try {
			for (Field field : Block.class.getFields()) {
				if ((field.getModifiers() == pubfinalmod)
						&& (field.getType() == int.class)) {
					IDResolverBasic.blockIdField = field;
					break;
				}
			}
		} catch (Throwable e3) {
			IDResolverBasic.overridesEnabled = false;
		}

		try {
			for (Field field : Item.class.getFields()) {
				if ((field.getModifiers() == pubfinalmod)
						&& (field.getType() == int.class)) {
					IDResolverBasic.itemIdField = field;
					break;
				}
			}
		} catch (Throwable e3) {
			IDResolverBasic.overridesEnabled = false;
		}
		if (IDResolverBasic.overridesEnabled) {
			IDResolverBasic.blockIdField.setAccessible(true);
			IDResolverBasic.itemIdField.setAccessible(true);
		}
	}
}
