import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/**
 * Gère la sauvegarde et le chargement de projets.
 */
public class ProjectManager {
    private final JFrame parent;
    private final Map<String, byte[]> classBytes;
    private final Map<String, String> modifiedCode;
    private final Map<String, String> classToDisplayName;
    private final Map<String, RSyntaxTextArea> openTabs;
    private final DecompilerManager decompilerManager;
    private final Runnable onProjectLoaded;
    
    public ProjectManager(JFrame parent, Map<String, byte[]> classBytes, Map<String, String> modifiedCode,
                        Map<String, String> classToDisplayName, Map<String, RSyntaxTextArea> openTabs,
                        DecompilerManager decompilerManager, Runnable onProjectLoaded) {
        this.parent = parent;
        this.classBytes = classBytes;
        this.modifiedCode = modifiedCode;
        this.classToDisplayName = classToDisplayName;
        this.openTabs = openTabs;
        this.decompilerManager = decompilerManager;
        this.onProjectLoaded = onProjectLoaded;
    }
    
    /**
     * Sauvegarde l'état du projet dans un ZIP.
     */
    public void saveProjectState() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Sauvegarder projet");
        chooser.setSelectedFile(new File("projet_decompile.zip"));
        int res = chooser.showSaveDialog(parent);
        if (res == JFileChooser.APPROVE_OPTION) {
            File zipFile = chooser.getSelectedFile();
            // Création de la popup de progression
            JDialog progressDialog = new JDialog(parent, "Sauvegarde en cours", true);
            JProgressBar progressBar = new JProgressBar(0, classBytes.size());
            progressBar.setStringPainted(true);
            progressDialog.getContentPane().add(progressBar);
            progressDialog.setSize(400, 80);
            progressDialog.setLocationRelativeTo(parent);
            // Lancement de la sauvegarde dans un thread séparé
            new Thread(() -> {
                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                    int count = 0;
                    for (String className : classBytes.keySet()) {
                        String code;
                        if (openTabs.containsKey(className)) {
                            code = openTabs.get(className).getText();
                        } else if (modifiedCode.containsKey(className)) {
                            code = modifiedCode.get(className);
                        } else {
                            code = decompilerManager.decompileClassToString(className, classBytes.get(className));
                        }
                        String javaName = className.replace(".class", ".java").replace("/", "/");
                        zos.putNextEntry(new ZipEntry("src/" + javaName));
                        zos.write(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        zos.closeEntry();
                        count++;
                        final int progress = count;
                        SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                    }
                    // Sauvegarde les mappings (renommages, arborescence)
                    Properties props = new Properties();
                    for (String k : classToDisplayName.keySet()) {
                        props.setProperty("classToDisplayName." + k, classToDisplayName.get(k));
                    }
                    for (String k : modifiedCode.keySet()) {
                        props.setProperty("modifiedCode." + k, "1");
                    }
                    zos.putNextEntry(new ZipEntry("project_mappings.properties"));
                    props.store(zos, "Mappings du projet");
                    zos.closeEntry();
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        JOptionPane.showMessageDialog(parent, "Projet sauvegardé dans : " + zipFile.getAbsolutePath());
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        JOptionPane.showMessageDialog(parent, "Erreur lors de la sauvegarde : " + ex.getMessage());
                    });
                }
            }).start();
            progressDialog.setVisible(true);
        }
    }
    
    /**
     * Charge l'état du projet depuis un ZIP.
     */
    public void loadProjectState(Runnable updateTreeCallback, Runnable autoRenameCallback) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Charger projet");
        int res = chooser.showOpenDialog(parent);
        if (res == JFileChooser.APPROVE_OPTION) {
            File zipFile = chooser.getSelectedFile();
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry entry;
                Map<String, String> loadedCode = new HashMap<>();
                Properties props = new Properties();
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().endsWith(".java")) {
                        String classKey = entry.getName().replace("src/", "").replace(".java", ".class").replace("/", "/");
                        StringBuilder sb = new StringBuilder();
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = zis.read(buf)) > 0) {
                            sb.append(new String(buf, 0, len, java.nio.charset.StandardCharsets.UTF_8));
                        }
                        loadedCode.put(classKey, sb.toString());
                    } else if (entry.getName().equals("project_mappings.properties")) {
                        props.load(zis);
                    }
                }
                // Recharge les codes modifiés
                modifiedCode.clear();
                modifiedCode.putAll(loadedCode);
                // Recharge les mappings
                classToDisplayName.clear();
                for (String k : props.stringPropertyNames()) {
                    if (k.startsWith("classToDisplayName.")) {
                        String key = k.substring("classToDisplayName.".length());
                        classToDisplayName.put(key, props.getProperty(k));
                    }
                }
                // Reconstruit classBytes et l'arborescence à partir des .java présents dans le ZIP
                classBytes.clear();
                for (String classKey : loadedCode.keySet()) {
                    classBytes.put(classKey, new byte[0]); // Bytecode vide, mais permet d'afficher la classe dans l'arbo
                }
                if (updateTreeCallback != null) {
                    updateTreeCallback.run();
                }
                if (autoRenameCallback != null) {
                    autoRenameCallback.run();
                }
                JOptionPane.showMessageDialog(parent, "Projet chargé ! (Rafraîchis l'arborescence si besoin)");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, "Erreur lors du chargement : " + ex.getMessage());
            }
        }
    }
}

