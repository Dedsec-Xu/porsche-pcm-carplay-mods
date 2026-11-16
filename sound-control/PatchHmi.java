import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class PatchHmi implements Opcodes {
    static final String R = "pcm/PromptRuntime";
    static int edits;
    static InsnList call(String name, String desc, int... locals) {
        InsnList list = new InsnList();
        for (int local: locals) list.add(new VarInsnNode(ILOAD, local));
        list.add(new MethodInsnNode(INVOKESTATIC, R, name, desc, false));
        return list;
    }
    static byte[] patch(String name, byte[] bytes) {
        ClassNode cls = new ClassNode(); new ClassReader(bytes).accept(cls, 0);
        int before = edits;
        for (MethodNode m: cls.methods) {
            if (name.endsWith("ToneScreenFactory") && m.name.equals("getPartialPopup")) {
                InsnList code = new InsnList(); LabelNode original = new LabelNode();
                code.add(new VarInsnNode(ILOAD, 1)); code.add(new LdcInsnNode(1000140));
                code.add(new JumpInsnNode(IF_ICMPNE, original));
                code.add(new VarInsnNode(ALOAD, 0)); code.add(new VarInsnNode(ILOAD, 2));
                code.add(new MethodInsnNode(INVOKESTATIC, "de/audi/tghu/tone/hmi/pgen2high/PromptRotaryPopup", "create",
                    "(Lde/audi/tghu/tone/hmi/pgen2high/ToneScreenFactory;I)Lde/audi/atip/hmi/view/Screen;", false));
                code.add(new TypeInsnNode(CHECKCAST, "de/audi/atip/hmi/view/IPartialPopupController"));
                code.add(new InsnNode(ARETURN)); code.add(original);
                m.instructions.insert(code); edits++;
            }
            if (name.endsWith("ToneScreenFactory") && m.name.equals("getPartialPopupStubs")) {
                for (AbstractInsnNode i: m.instructions.toArray()) if (i.getOpcode() == ARETURN) {
                    InsnList code = new InsnList(); code.add(new VarInsnNode(ILOAD, 1));
                    code.add(new MethodInsnNode(INVOKESTATIC, "de/audi/tghu/tone/hmi/pgen2high/PromptLevelPanel", "appendStub",
                        "([Lde/audi/atip/hmi/view/IPartialPopupController;I)[Lde/audi/atip/hmi/view/IPartialPopupController;", false));
                    m.instructions.insertBefore(i, code); edits++;
                }
            }
            if (name.endsWith("ToneModelBank") && m.name.equals("<init>")) {
                int sizeChanges = 0;
                for (AbstractInsnNode i: m.instructions.toArray()) {
                    if (i instanceof IntInsnNode) {
                        IntInsnNode ii = (IntInsnNode)i;
                        if (ii.operand == 162) { ii.operand = 163; sizeChanges++; }
                        else if (ii.operand == 115) { ii.operand = 116; sizeChanges++; }
                    }
                    if (i.getOpcode() == RETURN) {
                        InsnList code = new InsnList();
                        code.add(new VarInsnNode(ALOAD, 0));
                        code.add(new FieldInsnNode(GETFIELD, "de/audi/atip/hmi/model/AbstractModelBank", "modelIDs", "[I"));
                        code.add(new IntInsnNode(BIPUSH, 115));
                        code.add(new LdcInsnNode(1000162)); code.add(new InsnNode(IASTORE));
                        m.instructions.insertBefore(i, code);
                    }
                }
                if (sizeChanges != 3) throw new IllegalStateException("model bank shape changed: " + sizeChanges);
                edits++;
            }
            if (name.endsWith("ToneModelBank") && m.name.equals("createModel")) {
                InsnList code = new InsnList(); LabelNode original = new LabelNode();
                code.add(new VarInsnNode(ILOAD, 1)); code.add(new IntInsnNode(SIPUSH, 162));
                code.add(new JumpInsnNode(IF_ICMPNE, original));
                code.add(new VarInsnNode(ALOAD, 0));
                code.add(new FieldInsnNode(GETFIELD, "de/audi/atip/hmi/model/AbstractModelBank", "models", "[Lde/audi/atip/hmi/model/HMIModel;"));
                code.add(new IntInsnNode(SIPUSH, 162));
                code.add(new MethodInsnNode(INVOKESTATIC, R, "model", "()Lde/audi/atip/hmi/model/RangeModel;", false));
                code.add(new InsnNode(AASTORE)); code.add(new InsnNode(RETURN)); code.add(original);
                m.instructions.insert(code); edits++;
            }
            if (name.endsWith("ToneScreenBag1") && m.name.equals("sOUNDVOLUME")) {
                int count = 0;
                for (AbstractInsnNode i: m.instructions.toArray()) {
                    if (i instanceof MethodInsnNode && ((MethodInsnNode)i).name.equals("setViews")) {
                        InsnList code = new InsnList();
                        code.add(new VarInsnNode(ALOAD, 0)); code.add(new VarInsnNode(ILOAD, 1));
                        code.add(new MethodInsnNode(INVOKESTATIC, "de/audi/tghu/tone/hmi/pgen2high/PromptLevelPanel", "install",
                            "(Lde/esolutions/hmi/widgets/pgen2/ScreenWidgetPGen2;[I[[Lde/audi/atip/hmi/view/HMIView;Lde/audi/tghu/tone/hmi/pgen2high/ToneScreenFactory;I)V", false));
                        m.instructions.insertBefore(i, code); m.instructions.remove(i); count++;
                    }
                }
                if (count != 1) throw new IllegalStateException("screen binding count " + count);
                edits++;
            }
            if (name.endsWith("DSISoundHandler") && m.name.equals("registerDSI")) {
                for (AbstractInsnNode i: m.instructions.toArray()) if (i.getOpcode() == RETURN) {
                    InsnList code = new InsnList(); code.add(new VarInsnNode(ALOAD, 0));
                    code.add(new MethodInsnNode(INVOKESTATIC, R, "register", "(Lde/audi/tone/app/dsi/DSISoundHandler;)V", false));
                    m.instructions.insertBefore(i, code); edits++;
                }
            }
            if (name.endsWith("DSISoundListenerImpl")) {
                if (m.name.equals("updateInputGainOffset") && m.desc.equals("(IISI)V")) {
                    m.instructions.insert(call("gainUpdate", "(IISI)V", 1, 2, 3, 4)); edits++;
                }
                if (m.name.equals("inputGainOffsetRange") && m.desc.equals("(IIII)V")) {
                    m.instructions.insert(call("gainRange", "(IIII)V", 1, 2, 3, 4)); edits++;
                }
            }
            if (name.endsWith("CarPlayDSIManager")) {
                if (m.name.equals("duckAudio") && m.desc.equals("(ID)V")) {
                    m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null;
                    m.instructions.add(new VarInsnNode(ILOAD, 1)); m.instructions.add(new VarInsnNode(DLOAD, 2));
                    m.instructions.add(new MethodInsnNode(INVOKESTATIC, R, "duck", "(ID)V", false));
                    m.instructions.add(new InsnNode(RETURN)); edits++;
                }
                if (m.name.equals("unduckAudio") && m.desc.equals("(I)V")) {
                    m.instructions.clear(); m.tryCatchBlocks.clear(); m.localVariables = null;
                    m.instructions.add(call("unduck", "()V")); m.instructions.add(new InsnNode(RETURN)); edits++;
                }
                if (m.name.equals("deactivate") && m.desc.equals("()V")) {
                    m.instructions.insert(call("disconnect", "()V")); edits++;
                }
            }
        }
        if (edits == before) throw new IllegalStateException("no edits: " + name);
        // jxe2jar infers Java 5 from generic metadata, but this target is Foundation 1.1.
        // Reject actual newer bytecode rather than silently emitting an invalid old class.
        for (MethodNode method: cls.methods) for (AbstractInsnNode insn: method.instructions.toArray()) {
            if (insn instanceof InvokeDynamicInsnNode ||
                    (insn instanceof LdcInsnNode && ((LdcInsnNode)insn).cst instanceof Type))
                throw new IllegalStateException("unsupported modern bytecode: " + name);
        }
        cls.version = V1_2;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer); return writer.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        String[] names = {
            "de/audi/atip/model/ToneModelBank",
            "de/audi/tghu/tone/hmi/pgen2high/ToneScreenBag1",
            "de/audi/tghu/tone/hmi/pgen2high/ToneScreenFactory",
            "de/audi/tone/app/dsi/DSISoundHandler",
            "de/audi/tone/app/dsi/DSISoundListenerImpl",
            "de/audi/app/terminalmode/smartphone/carplay/CarPlayDSIManager"
        };
        try (JarFile source = new JarFile(args[0]); JarOutputStream output = new JarOutputStream(new FileOutputStream(args[2]))) {
            for (String name: names) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (InputStream input = source.getInputStream(source.getJarEntry(name + ".class"))) {
                    byte[] chunk = new byte[8192]; int size;
                    while ((size = input.read(chunk)) != -1) buffer.write(chunk, 0, size);
                }
                byte[] result = patch(name, buffer.toByteArray());
                output.putNextEntry(new JarEntry(name + ".class")); output.write(result); output.closeEntry();
                System.out.println("patched " + name);
            }
            Path root = Paths.get(args[1]);
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path: (Iterable<Path>)paths.filter(p -> p.toString().endsWith(".class"))::iterator) {
                    output.putNextEntry(new JarEntry(root.relativize(path).toString().replace('\\', '/')));
                    output.write(Files.readAllBytes(path)); output.closeEntry();
                }
            }
        }
        if (edits != 11) throw new IllegalStateException("expected 11 hook points, got " + edits);
        System.out.println("Built with 11 verified hook points");
    }
}
