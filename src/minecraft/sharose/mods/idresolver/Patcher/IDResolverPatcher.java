package sharose.mods.idresolver.Patcher;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import net.minecraft.client.Minecraft;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import sharose.mods.idresolver.IDResolverBasic;


import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import cpw.mods.fml.relauncher.IClassTransformer;

public class IDResolverPatcher implements IClassTransformer, Opcodes {

	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {
		if (transformedName.equals("net.minecraft.block.Block")) {
			return transformPrimaryHook(
					name.replace('.', '/'),
					// I feel a bit dirty for doing this, but if they are equal it's deobfuscated (aka mcp) and if not obfuscated (aka normally)
					"(IL"+ (name.equals(transformedName) ? "net/minecraft/block/material/Material" : 
						FMLDeobfuscatingRemapper.INSTANCE.unmap("net/minecraft/block/material/Material"))
							+ ";)V", true, bytes);
		} 

		if (transformedName.equals("net.minecraft.item.Item")) {
			return transformPrimaryHook(name.replace('.', '/'), "(I)V", false, bytes);
		}

		return bytes;
	}

	public byte[] transformPrimaryHook(String name, String constructorDesc,
			boolean isBlock, byte[] bytes) {
		ClassNode cn = new ClassNode(ASM4);
		ClassReader cr = new ClassReader(bytes);
		cr.accept(cn, ASM4);
		
		for (Object obj : cn.methods) {
			MethodNode methodNode = (MethodNode) obj;

			if ("<init>".equals(methodNode.name)
					&& (methodNode.desc.equals(constructorDesc))) {

				AbstractInsnNode currentNode = methodNode.instructions
						.getFirst();

				// let's just loop past INVOKESPECIAL
				// java/lang/Object.<init>()V, then inject our stuff.
				// Simple, easy, and works with any other hacks that might be
				// here.

				boolean working = true;

				while (working) {
					if (currentNode.getOpcode() == INVOKESPECIAL) {
						MethodInsnNode testingNode = (MethodInsnNode) currentNode;
						if (testingNode.owner.equals("java/lang/Object")
								&& testingNode.name.equals("<init>")
								&& testingNode.desc.equals("()V")) {
							// Bingo!
							working = false;
						}
					}
					currentNode = currentNode.getNext();
				}
				// Ok, so now we have skipped past the Object init that we need
				// to leave alone. Now let's add our stuff.
				
				methodNode.instructions.insertBefore(currentNode, (isBlock ? getBlockInstructions(name) : getItemInstructions(name)));
			}
		}
		ClassWriter cw = new ClassWriter(ASM4);
		cn.accept(cw);
		return cw.toByteArray();
	}
	
	
	public InsnList getBlockInstructions(String obfName)
	{
		InsnList IDRCode = new InsnList();

		//if(IDResolverBasic.shouldDoAssignment(par1, true))
    	//{
    	//	par1 = IDResolverBasic.getConflictedBlockID(par1, this);
    	//}
		
		IDRCode.add(new VarInsnNode(ILOAD, 1));
		IDRCode.add(new InsnNode(ICONST_1));
		IDRCode.add(new MethodInsnNode(INVOKESTATIC, "sharose/mods/idresolver/IDResolverBasic", "shouldDoAssignment", "(IZ)Z"));
		LabelNode l6 = new LabelNode();
		IDRCode.add(new JumpInsnNode(IFEQ, l6));
		LabelNode l7 = new LabelNode();
		IDRCode.add(l7);
		IDRCode.add(new LineNumberNode(338, l7));
		IDRCode.add(new VarInsnNode(ILOAD, 1));
		IDRCode.add(new VarInsnNode(ALOAD, 0));
		IDRCode.add(new MethodInsnNode(INVOKESTATIC, "sharose/mods/idresolver/IDResolverBasic", "getConflictedBlockID", "(IL" + obfName + ";)I"));
		IDRCode.add(new VarInsnNode(ISTORE, 1));
		IDRCode.add(l6);
		return IDRCode;
	}
	
	
	public InsnList getItemInstructions(String obfName)
	{
		InsnList IDRCode = new InsnList();

		//if(IDResolverBasic.shouldDoAssignment(256 + par1, false))
    	//{
    	//	par1 = IDResolverBasic.getConflictedItemID(256 + par1, this) - 256;
    	//}
		
		
		IDRCode.add(new IntInsnNode(SIPUSH, 256));
		IDRCode.add(new VarInsnNode(ILOAD, 1));
		IDRCode.add(new InsnNode(IADD));
		IDRCode.add(new InsnNode(ICONST_0));
		IDRCode.add(new MethodInsnNode(INVOKESTATIC, "sharose/mods/idresolver/IDResolverBasic", "shouldDoAssignment", "(IZ)Z"));
		LabelNode l6 = new LabelNode();
		IDRCode.add(new JumpInsnNode(IFEQ, l6));
		LabelNode l7 = new LabelNode();
		IDRCode.add(l7);
		IDRCode.add(new LineNumberNode(338, l7));
		IDRCode.add(new IntInsnNode(SIPUSH, 256));
		IDRCode.add(new VarInsnNode(ILOAD, 1));
		IDRCode.add(new InsnNode(IADD));
		IDRCode.add(new VarInsnNode(ALOAD, 0));
		IDRCode.add(new MethodInsnNode(INVOKESTATIC, "sharose/mods/idresolver/IDResolverBasic", "getConflictedItemID", "(IL" + obfName + ";)I"));
		IDRCode.add(new IntInsnNode(SIPUSH, 256));
		IDRCode.add(new InsnNode(ISUB));
		IDRCode.add(new VarInsnNode(ISTORE, 1));
		IDRCode.add(l6);
		return IDRCode;
	}
}
