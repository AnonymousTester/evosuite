/**
 * 
 */
package de.unisb.cs.st.evosuite.cfg.instrumentation.mutation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unisb.cs.st.evosuite.cfg.BytecodeInstruction;
import de.unisb.cs.st.evosuite.coverage.mutation.Mutation;
import de.unisb.cs.st.evosuite.coverage.mutation.MutationPool;

/**
 * @author fraser
 * 
 */
public class ReplaceVariable implements MutationOperator {

	private static Logger logger = LoggerFactory.getLogger(ReplaceVariable.class);

	/* (non-Javadoc)
	 * @see de.unisb.cs.st.evosuite.cfg.instrumentation.mutation.MutationOperator#apply(org.objectweb.asm.tree.MethodNode, java.lang.String, java.lang.String, de.unisb.cs.st.evosuite.cfg.BytecodeInstruction)
	 */
	@Override
	public List<Mutation> apply(MethodNode mn, String className, String methodName,
	        BytecodeInstruction instruction) {

		List<Mutation> mutations = new LinkedList<Mutation>();

		for (InsnList mutation : getReplacements(mn, className, instruction.getASMNode())) {
			// insert mutation into pool			
			Mutation mutationObject = MutationPool.addMutation(className,
			                                                   methodName,
			                                                   "ReplaceVariable",
			                                                   instruction,
			                                                   mutation,
			                                                   Mutation.getDefaultInfectionDistance());
			mutations.add(mutationObject);
		}
		return mutations;
	}

	/**
	 * Retrieve the set of variables that have the same type and are in scope
	 * 
	 * @param node
	 * @return
	 */
	private List<InsnList> getReplacements(MethodNode mn, String className,
	        AbstractInsnNode node) {
		List<InsnList> variables = new ArrayList<InsnList>();

		if (node instanceof VarInsnNode) {
			VarInsnNode var = (VarInsnNode) node;

			LocalVariableNode origVar = getLocal(mn, node, var.var);
			//LocalVariableNode origVar = (LocalVariableNode) mn.localVariables.get(var.var);
			logger.info("Looking for replacements for " + origVar.name + " of type "
			        + origVar.desc + " at index " + origVar.index);

			variables.addAll(getLocalReplacements(mn, origVar.desc, node));
			variables.addAll(getFieldReplacements(mn, className, origVar.desc, node));
		} else if (node instanceof FieldInsnNode) {
			FieldInsnNode field = (FieldInsnNode) node;
			if (field.owner.replace("/", ".").equals(className)) {
				logger.info("Looking for replacements for static field " + field.name
				        + " of type " + field.desc);
				variables.addAll(getLocalReplacements(mn, field.desc, node));
				variables.addAll(getFieldReplacements(mn, className, field.desc, node));
			}
		} else if (node instanceof IincInsnNode) {
			IincInsnNode incNode = (IincInsnNode) node;
			LocalVariableNode origVar = getLocal(mn, node, incNode.var);

			variables.addAll(getLocalReplacementsInc(mn, origVar.desc, incNode));

		} else {
			throw new RuntimeException("Unknown type: " + node);
		}

		return variables;
	}

	private LocalVariableNode getLocal(MethodNode mn, AbstractInsnNode node, int index) {
		int currentId = mn.instructions.indexOf(node);
		for (Object v : mn.localVariables) {
			LocalVariableNode localVar = (LocalVariableNode) v;
			int startId = mn.instructions.indexOf(localVar.start);
			int endId = mn.instructions.indexOf(localVar.end);
			if (currentId >= startId && currentId <= endId && localVar.index == index)
				return localVar;
		}

		throw new RuntimeException("Could not find local variable " + index);
	}

	private List<InsnList> getLocalReplacements(MethodNode mn, String desc,
	        AbstractInsnNode node) {
		List<InsnList> replacements = new ArrayList<InsnList>();

		int otherNum = -1;
		if (node instanceof VarInsnNode) {
			VarInsnNode vNode = (VarInsnNode) node;
			otherNum = vNode.var;
		}
		int currentId = mn.instructions.indexOf(node);

		for (Object v : mn.localVariables) {
			LocalVariableNode localVar = (LocalVariableNode) v;
			int startId = mn.instructions.indexOf(localVar.start);
			int endId = mn.instructions.indexOf(localVar.end);
			logger.info("Checking local variable " + localVar.name + " of type "
			        + localVar.desc + " at index " + localVar.index);
			if (!localVar.desc.equals(desc))
				logger.info("- Types do not match");
			if (localVar.index == otherNum)
				logger.info("- Replacement = original");
			if (currentId < startId)
				logger.info("- Out of scope (start)");
			if (currentId > endId)
				logger.info("- Out of scope (end)");

			if (localVar.desc.equals(desc) && localVar.index != otherNum
			        && currentId >= startId && currentId <= endId) {

				logger.info("Adding local variable " + localVar.name + " of type "
				        + localVar.desc + " at index " + localVar.index);
				InsnList list = new InsnList();
				if (node.getOpcode() == Opcodes.GETFIELD) {
					list.add(new InsnNode(Opcodes.POP)); // Remove field owner from stack
				}

				list.add(new VarInsnNode(getLoadOpcode(localVar), localVar.index));
				replacements.add(list);
			}
		}
		return replacements;
	}

	private List<InsnList> getLocalReplacementsInc(MethodNode mn, String desc,
	        IincInsnNode node) {
		List<InsnList> replacements = new ArrayList<InsnList>();

		int otherNum = -1;
		otherNum = node.var;
		int currentId = mn.instructions.indexOf(node);

		for (Object v : mn.localVariables) {
			LocalVariableNode localVar = (LocalVariableNode) v;
			int startId = mn.instructions.indexOf(localVar.start);
			int endId = mn.instructions.indexOf(localVar.end);
			logger.info("Checking local variable " + localVar.name + " of type "
			        + localVar.desc + " at index " + localVar.index);
			if (!localVar.desc.equals(desc))
				logger.info("- Types do not match");
			if (localVar.index == otherNum)
				logger.info("- Replacement = original");
			if (currentId < startId)
				logger.info("- Out of scope (start)");
			if (currentId > endId)
				logger.info("- Out of scope (end)");

			if (localVar.desc.equals(desc) && localVar.index != otherNum
			        && currentId >= startId && currentId <= endId) {

				logger.info("Adding local variable " + localVar.name + " of type "
				        + localVar.desc + " at index " + localVar.index);
				InsnList list = new InsnList();
				list.add(new IincInsnNode(localVar.index, node.incr));
				replacements.add(list);
			}
		}
		return replacements;
	}

	private int getLoadOpcode(LocalVariableNode var) {
		Type type = Type.getType(var.desc);
		return type.getOpcode(Opcodes.ILOAD);
	}

	private List<InsnList> getFieldReplacements(MethodNode mn, String className,
	        String desc, AbstractInsnNode node) {
		List<InsnList> alternatives = new ArrayList<InsnList>();

		String otherName = "";
		if (node instanceof FieldInsnNode) {
			FieldInsnNode fNode = (FieldInsnNode) node;
			otherName = fNode.name;
		}
		try {
			logger.info("Checking class " + className);
			Class<?> clazz = Class.forName(className);
			for (Field field : clazz.getFields()) {
				Type type = Type.getType(field.getType());
				logger.info("Checking replacement field variable " + field.getName());

				if (field.getName().equals(otherName))
					continue;

				if (type.getDescriptor().equals(desc)) {
					logger.info("Adding replacement field variable " + field.getName());
					InsnList list = new InsnList();
					if (node.getOpcode() == Opcodes.GETFIELD) {
						list.add(new InsnNode(Opcodes.POP)); // Remove field owner from stack
					}

					// new fieldinsnnode
					if (Modifier.isStatic(field.getModifiers()))
						list.add(new FieldInsnNode(Opcodes.GETSTATIC,
						        className.replace(".", "/"), field.getName(),
						        type.getDescriptor()));
					else {
						list.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
						list.add(new FieldInsnNode(Opcodes.GETFIELD,
						        className.replace(".", "/"), field.getName(),
						        type.getDescriptor()));
					}
					alternatives.add(list);
				}
			}
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return alternatives;
	}

	private boolean isGetField(BytecodeInstruction instruction) {
		if (instruction.getASMNode().getOpcode() == Opcodes.ALOAD) {
			VarInsnNode var = (VarInsnNode) instruction.getASMNode();
			if (var.var == 0) {
				AbstractInsnNode next = instruction.getASMNode().getNext();
				if (next.getOpcode() == Opcodes.GETFIELD)
					return true;
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see de.unisb.cs.st.evosuite.cfg.instrumentation.mutation.MutationOperator#isApplicable(de.unisb.cs.st.evosuite.cfg.BytecodeInstruction)
	 */
	@Override
	public boolean isApplicable(BytecodeInstruction instruction) {
		return instruction.isLocalVarUse()
		        || instruction.getASMNode().getOpcode() == Opcodes.GETSTATIC
		        || instruction.getASMNode().getOpcode() == Opcodes.GETFIELD;
	}

}
