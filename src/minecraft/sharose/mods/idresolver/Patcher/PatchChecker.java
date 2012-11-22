package sharose.mods.idresolver.Patcher;

import java.lang.reflect.Modifier;

import org.objectweb.asm.*;

/**
 * @author ShaRose
 *	This is a really ugly class pretty much ripped from the Patcher I made a long time ago. It's used to ID if it's a block or item, as well as find some information used when patching.
 */
public class PatchChecker extends ClassVisitor {

	public boolean unAcceptableClass = false;
	public String publicFinalInt = null;
	
	public String arrayOfSelf = null;
	public String finalArrayOfSelf = null;
	public int numSelfRefs = 0;
	public int numFinalSelfRefs = 0;
	
	public String myName;
	
	public String selfRefName;
	public String selfArrayRefName;
	
	public boolean isLikelyItem()
	{
		return !this.unAcceptableClass && this.arrayOfSelf != null && this.numSelfRefs > 50 && this.publicFinalInt != null;
	}
	
	public boolean isLikelyBlock()
	{
		return !this.unAcceptableClass && this.finalArrayOfSelf != null && this.numFinalSelfRefs > 50 && this.publicFinalInt != null;
	}
	
	public String relevantConstructor = null;
	
	public PatchChecker(int paramInt) {
		super(paramInt);
	}

	public PatchChecker(int paramInt, ClassVisitor paramClassVisitor) {
		super(paramInt, paramClassVisitor);
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		if(!"java/lang/Object".equals(superName))
			this.unAcceptableClass = true;
		this.myName = name;
		this.selfRefName = "L" + name + ";";
		this.selfArrayRefName = "[" + this.selfRefName;
	}
	
	@Override
	public FieldVisitor visitField(int access, String name, String desc,
			String signature, Object value) {
		if("I".equals(desc))
		{
			if(access == Modifier.FINAL + Modifier.PUBLIC)
			{
				this.publicFinalInt = name;
			}
		}
		if(this.selfRefName.equals(desc))
		{
			if(access == Modifier.STATIC + Modifier.PUBLIC)
			{
				this.numSelfRefs++;
			}
			else
			{
				if(access == Modifier.FINAL + Modifier.STATIC + Modifier.PUBLIC)
				{
					this.numFinalSelfRefs++;
				}
			}
		}
		
		if(this.selfArrayRefName.equals(desc))
		{
			if(access == Modifier.STATIC + Modifier.PUBLIC)
			{
				this.arrayOfSelf = name;
			}
			else
			{
				if(access == Modifier.FINAL + Modifier.STATIC + Modifier.PUBLIC)
				{
					this.finalArrayOfSelf = name;
				}
			}
		}
		return null;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {
		if("<init>".equals(name))
		{
			if(isLikelyBlock())
			{
				if(desc.startsWith("(IL") && desc.endsWith(";)V"))
				{
					this.relevantConstructor = desc;
				}
			}
			else
			{
			if(isLikelyItem())
			{
				if(desc.equals("(I)V"))
				{
					this.relevantConstructor = desc;
				}
			}
			}
			
		}
		return null;
	}

}