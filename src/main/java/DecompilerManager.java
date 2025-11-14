import com.strobel.decompiler.Decompiler;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Gère la décompilation des classes Java.
 */
public class DecompilerManager {
    private final File cacheDir;
    private final Map<String, byte[]> classBytes;
    private final Map<String, String> modifiedCode;
    
    public DecompilerManager(File cacheDir, Map<String, byte[]> classBytes, Map<String, String> modifiedCode) {
        this.cacheDir = cacheDir;
        this.classBytes = classBytes;
        this.modifiedCode = modifiedCode;
    }
    
    /**
     * Décompile une classe en String sans affecter l'affichage.
     */
    public String decompileClassToString(String className, byte[] bytes) {
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs();
            // Génère un hash MD5 hexadécimal stable pour le nom de fichier cache
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(className.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            String cacheKey = sb.toString() + ".java";
            File cacheFile = new File(cacheDir, cacheKey);
            System.out.println("DEBUG CACHE: " + className + " -> " + cacheKey);
            if (cacheFile.exists()) {
                System.out.println("DEBUG CACHE: ✓ " + className + " trouvé en cache");
                return new String(java.nio.file.Files.readAllBytes(cacheFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            }
            System.out.println("DEBUG CACHE: ✗ " + className + " pas en cache, décompilation...");
            File temp = File.createTempFile("procyon_", ".class");
            try (FileOutputStream fos = new FileOutputStream(temp)) {
                fos.write(bytes);
            }
            StringWriter sw = new StringWriter();
            DecompilerSettings settings = DecompilerSettings.javaDefaults();
            settings.setForceExplicitImports(true);
            PrintStream originalErr = System.err;
            System.setErr(new PrintStream(new OutputStream() {
                public void write(int b) {}
            }));
            Decompiler.decompile(temp.getAbsolutePath(), new PlainTextOutput(sw), settings);
            System.setErr(originalErr);
            temp.deleteOnExit();
            String code = sw.toString();
            java.nio.file.Files.write(cacheFile.toPath(), code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("DEBUG CACHE: ✓ " + className + " écrit en cache : " + cacheFile.getAbsolutePath());
            return code;
        } catch (Exception ex) {
            System.out.println("DEBUG CACHE: ✗ Erreur pour " + className + " : " + ex.getMessage());
            return "Erreur de décompilation : " + ex.getMessage();
        }
    }
    
    /**
     * Charge un fichier JAR et extrait toutes les classes.
     */
    public List<String> loadJar(File jarFile) throws IOException {
        List<String> classNames = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    classNames.add(entry.getName());
                    byte[] bytes = readAllBytes(jar.getInputStream(entry));
                    classBytes.put(entry.getName(), bytes);
                }
            }
        }
        return classNames;
    }
    
    /**
     * Charge un fichier .class unique.
     */
    public void loadClass(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = readAllBytes(fis);
            classBytes.put(file.getName(), bytes);
        }
    }
    
    private byte[] readAllBytes(InputStream in) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) baos.write(buf, 0, len);
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}

