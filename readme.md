ID Resolver
======

Building
--------

* Before we begin, make sure you have Minecraft Forge and GuiAPI installed and working in MCP.
* Now after you pull and make sure that ID resolver's files are in the right location, we need to make sure that FML calls ID Resolver's coremod. We can't just export a jar because it will error saying the mod is installed twice.
* So, open up cpw.mods.fml.relauncher.RelaunchLibraryManager and make the following change:

Change this:
```java
    private static String[] rootPlugins =  { "cpw.mods.fml.relauncher.FMLCorePlugin" , "net.minecraftforge.classloading.FMLForgePlugin" };
```

To this:
```java
    private static String[] rootPlugins =  { "cpw.mods.fml.relauncher.FMLCorePlugin" , "net.minecraftforge.classloading.FMLForgePlugin", "sharose.mods.idresolver.IDResolverCorePlugin" };
```

* All done! You should be good to go, and the Coremod should pipe block and item creation to ID resolver correctly.


Credits
-------

- ShaRose (Me. Should be obvious)
- lahwran (For starting the GuiAPI project and getting me interested in it)
- Lots of people who asked me to create a Block for my old AutoFertilizer mod, me again for being paranoid, and people who suggested I release ID resolver as it's own mod.
- People who submit good bug reports. I love bugs because it gives me a reason to work on it and not put it off. Sometimes.
- People who submitted feature requests (That weren't too hard to implement that is).