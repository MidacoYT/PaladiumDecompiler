import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.jar.JarEntry;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/**
 * Gère l'export des projets en JAR.
 */
public class ExportManager {
    private final Map<String, byte[]> classBytes;
    private final Map<String, String> modifiedCode;
    private final Map<String, RSyntaxTextArea> openTabs;
    private final DecompilerManager decompilerManager;
    private final JFrame parent;
    
    public ExportManager(JFrame parent, Map<String, byte[]> classBytes, Map<String, String> modifiedCode,
                        Map<String, RSyntaxTextArea> openTabs, DecompilerManager decompilerManager) {
        this.parent = parent;
        this.classBytes = classBytes;
        this.modifiedCode = modifiedCode;
        this.openTabs = openTabs;
        this.decompilerManager = decompilerManager;
    }
    
    /**
     * Exporte le projet en JAR source (fichiers .java).
     */
    public void exportToJar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exporter en JAR source");
        chooser.setSelectedFile(new File("exported_sources.jar"));
        int res = chooser.showSaveDialog(parent);
        if (res == JFileChooser.APPROVE_OPTION) {
            File jarFile = chooser.getSelectedFile();
            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
                for (String className : classBytes.keySet()) {
                    RSyntaxTextArea area = openTabs.get(className);
                    String code;
                    if (area != null) {
                        code = area.getText();
                    } else {
                        code = decompilerManager.decompileClassToString(className, classBytes.get(className));
                    }
                    String javaName = className.replace(".class", ".java").replace("/", "/");
                    jos.putNextEntry(new JarEntry(javaName));
                    jos.write(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    jos.closeEntry();
                }
                JOptionPane.showMessageDialog(parent, "Export JAR terminé : " + jarFile.getAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, "Erreur lors de l'export JAR : " + ex.getMessage());
            }
        }
    }
    
    /**
     * Exporte le projet en JAR compilé (fichiers .class).
     */
    public void exportToCompiledJar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exporter en JAR compilé");
        chooser.setSelectedFile(new File("exported_compiled.jar"));
        int res = chooser.showSaveDialog(parent);
        if (res == JFileChooser.APPROVE_OPTION) {
            File jarFile = chooser.getSelectedFile();
            File tempDir = null;
            try {
                // 1. Crée un dossier temporaire
                tempDir = java.nio.file.Files.createTempDirectory("decomp_export").toFile();
                // 2. Écrit tous les .java
                for (String className : classBytes.keySet()) {
                    RSyntaxTextArea area = openTabs.get(className);
                    String code;
                    if (area != null) {
                        code = area.getText();
                    } else {
                        code = decompilerManager.decompileClassToString(className, classBytes.get(className));
                    }
                    String javaPath = className.replace(".class", ".java");
                    File javaFile = new File(tempDir, javaPath);
                    javaFile.getParentFile().mkdirs();
                    try (FileWriter fw = new FileWriter(javaFile)) {
                        fw.write(code);
                    }
                }
                // 3. Compile tous les .java
                List<String> javaFiles = new ArrayList<>();
                for (File f : tempDir.listFiles()) {
                    javaFiles.addAll(listJavaFilesRec(f));
                }
                if (javaFiles.isEmpty()) throw new Exception("Aucun fichier .java à compiler");
                ProcessBuilder pb = new ProcessBuilder();
                pb.command("javac", "-d", tempDir.getAbsolutePath());
                pb.command().addAll(javaFiles);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) output.append(line).append("\n");
                }
                int exit = proc.waitFor();
                if (exit != 0) {
                    JOptionPane.showMessageDialog(parent, "Erreur compilation javac :\n" + output.toString());
                    return;
                }
                // 4. Emballe tous les .class dans le JAR
                try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
                    for (File f : tempDir.listFiles()) {
                        for (File classFile : listClassFilesRec(f)) {
                            String relPath = tempDir.toPath().relativize(classFile.toPath()).toString().replace("\\", "/");
                            jos.putNextEntry(new JarEntry(relPath));
                            jos.write(java.nio.file.Files.readAllBytes(classFile.toPath()));
                            jos.closeEntry();
                        }
                    }
                }
                JOptionPane.showMessageDialog(parent, "Export JAR compilé terminé : " + jarFile.getAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, "Erreur lors de l'export JAR compilé : " + ex.getMessage());
            } finally {
                // 5. Nettoie le dossier temporaire
                if (tempDir != null) deleteDirRec(tempDir);
            }
        }
    }
    
    private List<String> listJavaFilesRec(File f) {
        List<String> files = new ArrayList<>();
        if (f.isDirectory()) {
            for (File c : f.listFiles()) files.addAll(listJavaFilesRec(c));
        } else if (f.getName().endsWith(".java")) {
            files.add(f.getAbsolutePath());
        }
        return files;
    }
    
    private List<File> listClassFilesRec(File f) {
        List<File> files = new ArrayList<>();
        if (f.isDirectory()) {
            for (File c : f.listFiles()) files.addAll(listClassFilesRec(c));
        } else if (f.getName().endsWith(".class")) {
            files.add(f);
        }
        return files;
    }
    
    private void deleteDirRec(File f) {
        if (f.isDirectory()) for (File c : f.listFiles()) deleteDirRec(c);
        f.delete();
    }
}

