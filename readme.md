ID Resolver
======

Building
--------

* ID resolver requires you have GuiAPI and ModLoader installed into MCP. So make sure you have that done before you start. This includes patching instructions, should you wish to make a merge with ID resolver yourself.
* Also, for ID resolver to work you must make a few edits to Block.java and Item.java. These are not included with this repo, you can get them by decompiling with MCP.
* Now, we are going to edit Block.

    - We are going to need to edit one method in this class: protected Block(int var1, Material var2). Note, I decompile with FernFlower, so your parameter names might be different.

    - We are going to need to flat out replace a portion of the code here, and do a bit a cleanup. First, find the following block of code (It may be SLIGHTLY different depending on the decompiler you use).
```java
		if(blocksList[var1] != null) {
			throw new IllegalArgumentException("Slot " + var1 + " is already occupied by " + blocksList[var1] + " when adding " + this);
		} else {
```
    - Now, replace it with the following code:
```java
		if (blocksList[var1] != null || IDResolver.HasStoredID(var1, true)) {
			int newid = IDResolver.GetConflictedBlockID(var1, this);
			if (newid == -1) {
				if (blocksList[var1] != null) {
					throw new IllegalArgumentException("Slot " + var1 + " is already occupied by " + blocksList[var1] + " when adding " + this); // User probably hit cancel.
				}
				throw new IllegalArgumentException("Unable to add block " + this + " in slot " + var1 + ": Error detected. Please check your IDResolver and ModLoader logs for more information."); // Chances are, some other mod tried to kill Minecraft, and since that causes some errors we drop too. This would have been the cause of those Slot X is already occupied by null when adding whatever logs I had.
			}
			var1 = newid;
		}
```
    - Now, to clean it up a bit. See how the previous code left open a code block? We'll need to clean that up. So, go right to the end of that method and get rid of that ending bracket. It should be right after isBlockContainer[var1] = false;, as least for Minecraft 1.0.0.

* Block should now be done, and ID resolver should be able to hook correctly! But wait, we still need to edit Item, or else they will automatically overwrite if there are any conflicts, and depending on what's overwritten it may cause crashes! So let's do that now.

    - For Item, we are going to edit one method (Yes, it's the constructor again) as well as remove a final modifier that some mods require edited out. It won't change anything, but it means some mods don't need to have merges done, so. Since that is faster, let's do that first.

    - Here's the method we need to fix. (Again note the parameter name might be different!)
```java
		public final int getIconIndex(ItemStack itemstack)
```
    - Just replace that with the following.
```java
		public int getIconIndex(ItemStack itemstack)
```
    - See? A nice small change. Now to edit that constructor. So, let's go to protected Item(int var1). (Again note the parameter name might be different!) Again, this is pretty much just 'replace the code', but you might have to do a bit more depending on the decompiler. Let's get right to it, shall we? Since this is the whole method pretty much, I'll show what FernFlower decompiles, and what the finished version is. You should be able to make any changes you need to do.
```java
		this.shiftedIndex = 256 + var1;
		if(itemsList[256 + var1] != null) {
			System.out.println("CONFLICT @ " + var1);
		}
      itemsList[256 + var1] = this;
```
    - And now, we need to replace that with this.
```java
		var1 += 256; // See how we are increasing the value?
		if (itemsList[var1] != null || IDResolver.HasStoredID(var1, false)) { // This is all pretty much the same as block.
			int newid = IDResolver.GetConflictedItemID(var1, this);
			if (newid == -1)
				System.out.println("CONFLICT @ " + var1); // We don't bother throwing an error if it returns -1: We just fall back on overwrite. Well, if there's an item there. It doesn't matter to us in any case.
			else
				var1 = newid;
		}
		this.shiftedIndex = var1; // Finally, after it's all done we replace the shiftedIndex. It's a final field, so we need to keep it for later.
		itemsList[var1] = this;
```
	- All done editing Item.

* And with that, both Items and Blocks are set to automatically divert to ID Resolver in a way that works for (Almost) all mods. Feel free to look at the source code, although I'm not happy with it. Personally, I think it's quite messy, but I've had people say it was decent, so. If you want to do a reformat of it by all means do so, and submit the pull request. Also of note are all those example mods: They are just there for testing ID resolver, and I don't actually release them. They don't do anything besides add dummy blocks and IDs in varying states of completeness (Which I did to test the naming). I think that's about it.


Credits
-------

- ShaRose (Me. Should be obvious)
- lahwran (For starting the GuiAPI project and getting me interested in it)
- Lots of people who asked me to create a Block for my old AutoFertilizer mod, me again for being paranoid, and people who suggested I release ID resolver as it's own mod.
- People who submit good bug reports. I love bugs because it gives me a reason to work on it and not put it off. Sometimes.
- People who submitted feature requests (That weren't too hard to implement that is).