import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
public class VerifyPatch {
    public static void main(String[] args) throws Exception {
        int classes = 0, methods = 0;
        try (JarFile jar = new JarFile(args[0])) {
            java.util.Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode c = new ClassNode(); new ClassReader(jar.getInputStream(entry)).accept(c, 0);
                for (MethodNode m : c.methods) {
                    if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
                    try { new Analyzer<BasicValue>(new BasicVerifier()).analyze(c.name, m); }
                    catch (AnalyzerException e) { throw new Exception(c.name + "." + m.name + m.desc, e); }
                    methods++;
                }
                classes++;
            }
        }
        System.out.println("PASS: stack/type verification " + classes + " classes, " + methods + " methods");
    }
}
