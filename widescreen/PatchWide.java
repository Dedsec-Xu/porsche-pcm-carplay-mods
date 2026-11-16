import java.nio.file.*;
import java.security.MessageDigest;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Reconstruct the 50-pixel margin edit on the exact downloaded aftermarket base. */
public final class PatchWide {
    private static final String EXPECTED = "0d0a233828db0612b3431b8eb970bce5840943138b6f66bd7816c0a50bfca15c";
    private static final String ENTRY = "de/audi/app/terminalmode/pgen2/PGen2TMConfiguration.class";
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.jar output.jar");
        Path source = Paths.get(args[0]), target = Paths.get(args[1]);
        if (Files.exists(target)) throw new IllegalArgumentException("Output already exists");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b & 255));
        if (!EXPECTED.equals(hex.toString())) throw new IllegalArgumentException("Unrecognized base JAR");
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        try (JarFile jar = new JarFile(source.toFile())) {
            Enumeration<JarEntry> all = jar.entries();
            while (all.hasMoreElements()) {
                JarEntry entry = all.nextElement();
                if (entry.isDirectory()) continue;
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (InputStream in = jar.getInputStream(entry)) {
                    byte[] chunk = new byte[8192]; int n;
                    while ((n = in.read(chunk)) != -1) buffer.write(chunk, 0, n);
                }
                entries.put(entry.getName(), buffer.toByteArray());
            }
        }
        ClassNode cls = new ClassNode();
        new ClassReader(entries.get(ENTRY)).accept(cls, 0);
        int changes = 0;
        for (MethodNode m : cls.methods) {
            if (!m.name.equals("getWindowResolutionX") && !m.name.equals("getTMWindowResolutionInXAxis")) continue;
            if (!m.desc.equals("()I")) throw new IllegalStateException("Unexpected descriptor");
            int matches = 0;
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof IntInsnNode && insn.getOpcode() == Opcodes.SIPUSH && ((IntInsnNode)insn).operand == 1326) {
                    ((IntInsnNode)insn).operand = 1276; matches++; changes++;
                }
            }
            if (matches != 1) throw new IllegalStateException("Unexpected method: " + m.name);
        }
        if (changes != 2) throw new IllegalStateException("Expected exactly two width edits");
        ClassWriter writer = new ClassWriter(0); cls.accept(writer);
        entries.put(ENTRY, writer.toByteArray());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey())); out.write(entry.getValue()); out.closeEntry();
            }
        }
        System.out.println("Patched both widths: 1326 -> 1276; preserved class version " + cls.version);
    }
}
