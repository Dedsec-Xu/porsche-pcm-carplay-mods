import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Add steering-wheel support to the existing audio JAR, avoiding duplicate class overrides. */
public final class PatchJoker implements Opcodes {
    static final String R = "pcm/JokerBridge";
    static final String H = "de/audi/app/terminalmode/AbstractTerminalModeHMIApplication";
    static final String C = "de/audi/app/terminalmode/smartphone/carplay/CarPlayDSIManager";
    static byte[] read(JarFile jar, String name) throws Exception {
        try (InputStream in = jar.getInputStream(jar.getJarEntry(name))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            return out.toByteArray();
        }
    }
    static byte[] patch(byte[] input, boolean hmi) {
        ClassNode cls = new ClassNode(); new ClassReader(input).accept(cls, 0);
        int count = 0;
        for (MethodNode m : cls.methods) {
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i instanceof MethodInsnNode && ((MethodInsnNode)i).owner.equals(R))
                    throw new IllegalStateException("Already patched");
            }
            if (hmi && m.name.equals("getVirtualButton") && m.desc.equals("(I)Lde/audi/atip/hmi/modelaccess/ButtonModelApp;")) {
                InsnList code = new InsnList(); LabelNode original = new LabelNode(), match = new LabelNode();
                code.add(new VarInsnNode(ILOAD, 1)); code.add(new IntInsnNode(BIPUSH, 13));
                code.add(new JumpInsnNode(IF_ICMPEQ, match));
                code.add(new VarInsnNode(ILOAD, 1)); code.add(new IntInsnNode(BIPUSH, 39));
                code.add(new JumpInsnNode(IF_ICMPNE, original)); code.add(match);
                code.add(new VarInsnNode(ALOAD, 0));
                code.add(new FieldInsnNode(GETFIELD, H, "context", "Lde/audi/app/terminalmode/IContext;"));
                code.add(new VarInsnNode(ILOAD, 1));
                code.add(new MethodInsnNode(INVOKESTATIC, R, "model", "(Lde/audi/app/terminalmode/IContext;I)Lde/audi/atip/hmi/modelaccess/ButtonModelApp;", false));
                code.add(new InsnNode(ARETURN)); code.add(original); m.instructions.insert(code); count++;
            }
            if (hmi && m.name.equals("deinit") && m.desc.equals("()V")) {
                InsnList code = new InsnList(); code.add(new VarInsnNode(ALOAD, 0));
                code.add(new FieldInsnNode(GETFIELD, H, "context", "Lde/audi/app/terminalmode/IContext;"));
                code.add(new MethodInsnNode(INVOKESTATIC, R, "detach", "(Lde/audi/app/terminalmode/IContext;)V", false));
                m.instructions.insert(code); count++;
            }
            if (!hmi && (m.name.equals("activate") || m.name.equals("deactivate")) && m.desc.equals("()V")) {
                if (m.name.equals("deactivate")) {
                    InsnList code = new InsnList(); code.add(new VarInsnNode(ALOAD, 0));
                    code.add(new MethodInsnNode(INVOKESTATIC, R, m.name, "(L" + C + ";)V", false));
                    m.instructions.insert(code);
                } else {
                    for (AbstractInsnNode i : m.instructions.toArray()) if (i.getOpcode() == RETURN) {
                        InsnList code = new InsnList(); code.add(new VarInsnNode(ALOAD, 0));
                        code.add(new MethodInsnNode(INVOKESTATIC, R, m.name, "(L" + C + ";)V", false));
                        m.instructions.insertBefore(i, code);
                    }
                }
                count++;
            }
        }
        if (count != 2) throw new IllegalStateException("Expected two patched methods in " + cls.name + ", got " + count);
        // The converted HMI registration method contains a Java 5 class literal.
        // Replace it with Class.forName for the target's Foundation 1.1 verifier.
        for (MethodNode m : cls.methods) for (AbstractInsnNode i : m.instructions.toArray()) {
            if (i instanceof LdcInsnNode && ((LdcInsnNode)i).cst instanceof Type) {
                Type type = (Type)((LdcInsnNode)i).cst;
                if (type.getSort() != Type.OBJECT) throw new IllegalStateException("Unsupported class literal");
                InsnList code = new InsnList(); code.add(new LdcInsnNode(type.getClassName()));
                code.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false));
                m.instructions.insertBefore(i, code); m.instructions.remove(i);
            }
            if (i instanceof InvokeDynamicInsnNode) throw new IllegalStateException("Unsupported invokedynamic");
        }
        cls.version = V1_2;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); cls.accept(writer); return writer.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("stock.jar audio.jar classes output.jar");
        Map<String,byte[]> entries = new LinkedHashMap<String,byte[]>();
        try (JarFile stock = new JarFile(args[0]); JarFile audio = new JarFile(args[1])) {
            Enumeration<JarEntry> e = audio.entries();
            while (e.hasMoreElements()) { JarEntry entry = e.nextElement(); if (!entry.isDirectory()) entries.put(entry.getName(), read(audio, entry.getName())); }
            entries.put(H + ".class", patch(read(stock, H + ".class"), true));
            entries.put(C + ".class", patch(entries.get(C + ".class"), false));
        }
        Path root = Paths.get(args[2]);
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path p : (Iterable<Path>)paths.filter(p -> p.toString().endsWith(".class"))::iterator)
                entries.put(root.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
        }
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(Paths.get(args[3])))) {
            for (Map.Entry<String,byte[]> e : entries.entrySet()) { out.putNextEntry(new JarEntry(e.getKey())); out.write(e.getValue()); out.closeEntry(); }
        }
        System.out.println("Built combined audio + programmable-key package: " + entries.size() + " entries");
    }
}
