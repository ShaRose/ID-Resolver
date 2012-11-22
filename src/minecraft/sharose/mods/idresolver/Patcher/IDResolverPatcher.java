package sharose.mods.idresolver.Patcher;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import cpw.mods.fml.relauncher.IClassTransformer;

/**
 * @author ShaRose
 *	The main transformer. This uses PatchChecker to see if it needs patching (and for what), then does it. Not really complex sounding, but the patch methods are 'huge'.
 */
public class IDResolverPatcher implements IClassTransformer, Opcodes {

	@Override
	public byte[] transform(String name, byte[] bytes) {
		
		ClassReader reader = new ClassReader(bytes);
		PatchChecker checker = new PatchChecker(ASM4);
		reader.accept(checker, 0);
		if (checker.isLikelyBlock() && !checker.isLikelyItem() && checker.relevantConstructor != null) {
			return patchBlock(bytes,checker);
		}
		if (checker.isLikelyItem() && !checker.isLikelyBlock()&& checker.relevantConstructor != null) {
			return patchItem(bytes,checker);
		}
		return bytes;
	}
	
	/**
	 * @param bytes The class data.
	 * @param checker The PatchChecker (for finding information)
	 * @return the patched data.
	 */
	private byte[] patchItem(byte[] bytes, PatchChecker checker) {
		try {
			ClassNode cn = new ClassNode(ASM4);
			ClassReader cr = new ClassReader(bytes);
			cr.accept(cn, ASM4);

			for (Object obj : cn.methods) {
				MethodNode methodNode = (MethodNode) obj;
				if ("<init>".equals(methodNode.name)
						&& methodNode.desc.equals(checker.relevantConstructor)) {
					patchItemMethod(methodNode, checker);
				}
			}
			ClassWriter cw = new ClassWriter(ASM4);
			cn.accept(cw);
			return cw.toByteArray();
		} catch (Throwable e) {
			// TODO Some kind of error message to display to the user or
			// something. I dunno.
			return bytes;
		}
	}

	/**
	 * @param bytes The class data.
	 * @param checker The PatchChecker (for finding information)
	 * @return the patched data.
	 */
	private byte[] patchBlock(byte[] bytes, PatchChecker checker) {
		try {
			ClassNode cn = new ClassNode(ASM4);
			ClassReader cr = new ClassReader(bytes);
			cr.accept(cn, ASM4);

			for (Object obj : cn.methods) {
				MethodNode methodNode = (MethodNode) obj;
				if ("<init>".equals(methodNode.name)
						&& methodNode.desc.equals(checker.relevantConstructor)) {
					patchBlockMethod(methodNode, checker);
				}
			}
			ClassWriter cw = new ClassWriter(ASM4);
			cn.accept(cw);
			return cw.toByteArray();
		} catch (Throwable e) {
			// TODO Some kind of error message to display to the user or
			// something. I dunno.
			return bytes;
		}
	}
	
	/**
	 * This patches the Item method, and is where all the instruction data is. It walks the instructions first to find out what it needs to keep, then generates ID Resolver's code, then replaces the instruction list.
	 * 
	 * @param methodNode The method that needs patching. Basically, the Constructor that the PatchChecker found.
	 * @param checker The PatchChecker used for signatures.
	 * @throws Exception Just for later use: If there's a problem I want to have it show a message or something, but it's just gonna get swallowed atm.
	 */
	private void patchItemMethod(MethodNode methodNode, PatchChecker checker) throws Exception
	{
		AbstractInsnNode currentNode = methodNode.instructions.getFirst();
		InsnList initializers = new InsnList();
		int mode = 0;
		while (true) {

			switch (mode) {
			case 0: {
				// Search for ILOAD (getting parameter)
				if (currentNode.getOpcode() == Opcodes.ILOAD) {
					mode++;
				}
				break;
			}
			case 1: {
				// Search backwards for PUTFIELD
				if (currentNode.getOpcode() == Opcodes.PUTFIELD) {
					mode++;
					initializers.insert(currentNode);
				}
				break;
			}
			case 2: {
				// Insert instructions until out of them
				// if (!(currentNode instanceof LineNumberNode))
				initializers.insert(currentNode);
				break;
			}
			}

			if (mode == 0) {
				currentNode = currentNode.getNext();
			} else {
				currentNode = currentNode.getPrevious();
			}
			if (currentNode == null) {
				if (mode == 2)
					break;
				throw new Exception("Failed to patch. Out of instructions.");
			}

		}
		LabelNode labelItemNoConflictFound = new LabelNode();

		LabelNode labelItemConflictResolved = new LabelNode();

		InsnList caughtIDRCode = new InsnList();
		caughtIDRCode.add(new IincInsnNode(1, 256));
		caughtIDRCode.add(new VarInsnNode(ILOAD, 1));
		caughtIDRCode.add(new InsnNode(ICONST_0));
		caughtIDRCode.add(new MethodInsnNode(INVOKESTATIC, "sharose/mods/idresolver/IDResolver",
				"shouldDoAssignment", "(IZ)Z"));

		caughtIDRCode.add(new JumpInsnNode(IFEQ, labelItemNoConflictFound));
		
		caughtIDRCode.add(new VarInsnNode(ILOAD, 1));
		caughtIDRCode.add(new VarInsnNode(ALOAD, 0));
		caughtIDRCode.add(new MethodInsnNode(INVOKESTATIC, "sharose/mods/idresolver/IDResolver",
				"getConflictedItemID", "(I" + checker.selfRefName + ")I"));
		caughtIDRCode.add(new VarInsnNode(ISTORE, 2));
		caughtIDRCode.add(new VarInsnNode(ILOAD, 2));
		caughtIDRCode.add(new InsnNode(ICONST_M1));

		caughtIDRCode
				.add(new JumpInsnNode(IF_ICMPNE, labelItemConflictResolved));
		
		
		caughtIDRCode.add(new FieldInsnNode(GETSTATIC, "java/lang/System",
				"out", "Ljava/io/PrintStream;"));
		caughtIDRCode.add(new TypeInsnNode(NEW, "java/lang/StringBuilder"));
		caughtIDRCode.add(new InsnNode(DUP));
		
		caughtIDRCode.add(new LdcInsnNode("CONFLICT @ "));
		caughtIDRCode.add(new MethodInsnNode(INVOKESPECIAL,
				"java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V"));
		caughtIDRCode.add(new VarInsnNode(ILOAD, 1));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "append",
				"(I)Ljava/lang/StringBuilder;"));
		
		caughtIDRCode.add(new LdcInsnNode(" item slot already occupied by "));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "append",
				"(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
		
		caughtIDRCode.add(new FieldInsnNode(GETSTATIC, checker.myName,
				checker.arrayOfSelf, checker.selfArrayRefName));
		caughtIDRCode.add(new VarInsnNode(ILOAD, 1));
		caughtIDRCode.add(new InsnNode(AALOAD));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "append",
				"(Ljava/lang/Object;)Ljava/lang/StringBuilder;"));
		
		caughtIDRCode.add(new LdcInsnNode(" while adding "));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "append",
				"(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
		
		caughtIDRCode.add(new VarInsnNode(ALOAD, 0));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "append",
				"(Ljava/lang/Object;)Ljava/lang/StringBuilder;"));
		
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "toString", "()Ljava/lang/String;"));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/io/PrintStream", "println", "(Ljava/lang/String;)V"));
		caughtIDRCode.add(new JumpInsnNode(GOTO, labelItemNoConflictFound));
		
		
		
		
		caughtIDRCode.add(labelItemConflictResolved);
		caughtIDRCode.add(new VarInsnNode(ILOAD, 2));
		caughtIDRCode.add(new VarInsnNode(ISTORE, 1));
		caughtIDRCode.add(labelItemNoConflictFound);
		caughtIDRCode.add(new VarInsnNode(ALOAD, 0));
		caughtIDRCode.add(new VarInsnNode(ILOAD, 1));
		caughtIDRCode.add(new FieldInsnNode(PUTFIELD, checker.myName,
				checker.publicFinalInt, "I"));
		caughtIDRCode.add(new FieldInsnNode(GETSTATIC, checker.myName,
				checker.arrayOfSelf, checker.selfArrayRefName));
		caughtIDRCode.add(new VarInsnNode(ILOAD, 1));
		caughtIDRCode.add(new VarInsnNode(ALOAD, 0));
		caughtIDRCode.add(new InsnNode(AASTORE));
		caughtIDRCode.add(new InsnNode(RETURN));

		// Wipe it out.
		methodNode.instructions.clear();
		// Add the initilizer stuff.
		methodNode.instructions.add(initializers);
		// Add my stuff.
		methodNode.instructions.add(caughtIDRCode);
		methodNode.maxLocals++;
	}
	
	/**
	 * This patches the Block method, and is where all the instruction data is. It walks the instructions first to find out what it needs to keep, then generates ID Resolver's code, then replaces the instruction list.
	 * 
	 * @param methodNode The method that needs patching. Basically, the Constructor that the PatchChecker found.
	 * @param checker The PatchChecker used for signatures.
	 * @throws Exception Just for later use: If there's a problem I want to have it show a message or something, but it's just gonna get swallowed atm.
	 */
	private void patchBlockMethod(MethodNode methodNode, PatchChecker checker) throws Exception
	{
		int mode = 0;

		AbstractInsnNode currentNode = methodNode.instructions.getFirst();
		InsnList initializers = new InsnList();
		InsnList finishers = new InsnList();
		boolean working = true;
		while (working) {

			switch (mode) {
			case 0: {
				if (currentNode.getOpcode() == GETSTATIC) {
					FieldInsnNode node = (FieldInsnNode) currentNode;
					if (node.desc.equals(checker.selfArrayRefName)) {
						mode++;
						break;
					}
				}
				if (currentNode.getOpcode() == ILOAD) {
					VarInsnNode node = (VarInsnNode) currentNode;
					if (node.var == 1) {
						AbstractInsnNode testingNode = currentNode.getNext();
						if (testingNode.getOpcode() == ICONST_1) {
							testingNode = testingNode.getNext();
							if (testingNode.getOpcode() == INVOKESTATIC) {
								MethodInsnNode testingNode2 = (MethodInsnNode) testingNode;
								if (testingNode2.owner.endsWith("IDResolver")) {
									mode++;
									break;
								}
							}
						}
					}
				}
				if (!(currentNode instanceof LineNumberNode))
					initializers.add(currentNode);
				break;
			}
			case 1: {
				if (currentNode instanceof JumpInsnNode) {
					JumpInsnNode node = (JumpInsnNode) currentNode;

					if (node.getOpcode() == IFNULL) {
						currentNode = node.label;
						mode++;
						break;
					}

					if (node.getOpcode() == IFNONNULL) {
						mode++;
						break;
					}
				}
				break;
			}
			case 2: {
				if (!(currentNode instanceof LineNumberNode))
					finishers.add(currentNode);

				if (currentNode.getOpcode() == ATHROW) {
					throw new Exception(
							"Found a Throw instruction in what is supposed to be finisher code: Block method is incorrect!");
				}

				if (currentNode.getOpcode() == RETURN) {
					working = false;
				}
				break;
			}
			}
			if (working) {
				currentNode = currentNode.getNext();
				if (currentNode == null)
					throw new Exception("Failed to patch. Out of instructions.");
			}
		}
		InsnList caughtIDRCode = new InsnList();

		LabelNode labelBlockFinisher = new LabelNode();
		LabelNode labelBlockConflictResolved = new LabelNode();
		LabelNode labelBlockConflictError = new LabelNode();
		
		caughtIDRCode.add(new VarInsnNode(ILOAD, 1));
		caughtIDRCode.add(new InsnNode(ICONST_1));
		caughtIDRCode.add(new MethodInsnNode(INVOKESTATIC, "sharose/mods/idresolver/IDResolver",
				"shouldDoAssignment", "(IZ)Z"));
		caughtIDRCode.add(new JumpInsnNode(IFEQ, labelBlockFinisher));

		// GetConflictedBlockID stuff
		caughtIDRCode.add(new VarInsnNode(ILOAD, 1));
		caughtIDRCode.add(new VarInsnNode(ALOAD, 0));
		caughtIDRCode
				.add(new MethodInsnNode(INVOKESTATIC, "sharose/mods/idresolver/IDResolver",
						"getConflictedBlockID", "(I" + checker.selfRefName
								+ ")I"));
		caughtIDRCode.add(new VarInsnNode(ISTORE, 3));
		caughtIDRCode.add(new VarInsnNode(ILOAD, 3));
		caughtIDRCode.add(new InsnNode(ICONST_M1));
		caughtIDRCode.add(new JumpInsnNode(IF_ICMPNE,
				labelBlockConflictResolved));

		// It failed: Check if the block is null
		caughtIDRCode.add(new FieldInsnNode(GETSTATIC, checker.myName,
				checker.finalArrayOfSelf, checker.selfArrayRefName));
		caughtIDRCode.add(new VarInsnNode(ILOAD, 1));
		caughtIDRCode.add(new InsnNode(AALOAD));
		caughtIDRCode.add(new JumpInsnNode(IFNULL, labelBlockConflictError));

		// block was null, user probably cancelled.
		caughtIDRCode.add(new TypeInsnNode(NEW,
				"java/lang/IllegalArgumentException"));
		caughtIDRCode.add(new InsnNode(DUP));
		caughtIDRCode.add(new TypeInsnNode(NEW, "java/lang/StringBuilder"));
		caughtIDRCode.add(new InsnNode(DUP));
		caughtIDRCode.add(new LdcInsnNode("Slot "));
		caughtIDRCode.add(new MethodInsnNode(INVOKESPECIAL,
				"java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V"));
		caughtIDRCode.add(new VarInsnNode(ILOAD, 1));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "append",
				"(I)Ljava/lang/StringBuilder;"));
		caughtIDRCode.add(new LdcInsnNode(" is already occupied by "));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "append",
				"(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
		caughtIDRCode.add(new FieldInsnNode(GETSTATIC, checker.myName,
				checker.finalArrayOfSelf, checker.selfArrayRefName));
		caughtIDRCode.add(new VarInsnNode(ILOAD, 1));
		caughtIDRCode.add(new InsnNode(AALOAD));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "append",
				"(Ljava/lang/Object;)Ljava/lang/StringBuilder;"));
		caughtIDRCode.add(new LdcInsnNode(" when adding "));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "append",
				"(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
		caughtIDRCode.add(new VarInsnNode(ALOAD, 0));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "append",
				"(Ljava/lang/Object;)Ljava/lang/StringBuilder;"));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "toString", "()Ljava/lang/String;"));
		caughtIDRCode.add(new MethodInsnNode(INVOKESPECIAL,
				"java/lang/IllegalArgumentException", "<init>",
				"(Ljava/lang/String;)V"));
		caughtIDRCode.add(new InsnNode(ATHROW));

		// block WASN'T null. Probably some error.
		caughtIDRCode.add(labelBlockConflictError);

		caughtIDRCode.add(new TypeInsnNode(NEW,
				"java/lang/IllegalArgumentException"));
		caughtIDRCode.add(new InsnNode(DUP));
		caughtIDRCode.add(new TypeInsnNode(NEW, "java/lang/StringBuilder"));
		caughtIDRCode.add(new InsnNode(DUP));
		caughtIDRCode.add(new LdcInsnNode("Unable to add block "));
		caughtIDRCode.add(new MethodInsnNode(INVOKESPECIAL,
				"java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V"));
		caughtIDRCode.add(new VarInsnNode(ALOAD, 0));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "append",
				"(Ljava/lang/Object;)Ljava/lang/StringBuilder;"));
		caughtIDRCode.add(new LdcInsnNode(" in slot "));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "append",
				"(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
		caughtIDRCode.add(new VarInsnNode(ILOAD, 1));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "append",
				"(I)Ljava/lang/StringBuilder;"));
		caughtIDRCode
				.add(new LdcInsnNode(
						": Error detected. Please check your IDResolver and ModLoader logs for more information."));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "append",
				"(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
		caughtIDRCode.add(new MethodInsnNode(INVOKEVIRTUAL,
				"java/lang/StringBuilder", "toString", "()Ljava/lang/String;"));
		caughtIDRCode.add(new MethodInsnNode(INVOKESPECIAL,
				"java/lang/IllegalArgumentException", "<init>",
				"(Ljava/lang/String;)V"));
		caughtIDRCode.add(new InsnNode(ATHROW));

		// Worked.
		caughtIDRCode.add(labelBlockConflictResolved);
		caughtIDRCode.add(new VarInsnNode(ILOAD, 3));
		caughtIDRCode.add(new VarInsnNode(ISTORE, 1));
		// Add the 'normal' label.
		caughtIDRCode.add(labelBlockFinisher);

		// Wipe it out.
		methodNode.instructions.clear();

		// Add the initilizer stuff.
		methodNode.instructions.add(initializers);
		// Add my stuff.
		methodNode.instructions.add(caughtIDRCode);
		// Add the finishing stuff.
		methodNode.instructions.add(finishers);

		methodNode.maxLocals++;
	}
}
