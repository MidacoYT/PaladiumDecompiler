
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import javax.swing.*;
import java.util.*;
import java.util.List;

public class RenameManager {
    private final Map<String, byte[]> classBytes;
    private final Map<String, String> modifiedCode;
    private final Map<String, RSyntaxTextArea> openTabs;
    private final Map<String, Set<String>> referencesTo;
    private final ClassRenamer classRenamer;
    private final DecompilerManager decompilerManager;
    private final TreeManager treeManager;
    private final TabManager tabManager;
    private final IdentifierAnalyzer identifierAnalyzer;
    private final JFrame parent;
    
    public RenameManager(JFrame parent, Map<String, byte[]> classBytes, Map<String, String> modifiedCode,
                         Map<String, RSyntaxTextArea> openTabs, Map<String, Set<String>> referencesTo,
                         ClassRenamer classRenamer, DecompilerManager decompilerManager,
                         TreeManager treeManager, TabManager tabManager, IdentifierAnalyzer identifierAnalyzer) {
        this.parent = parent;
        this.classBytes = classBytes;
        this.modifiedCode = modifiedCode;
        this.openTabs = openTabs;
        this.referencesTo = referencesTo;
        this.classRenamer = classRenamer;
        this.decompilerManager = decompilerManager;
        this.treeManager = treeManager;
        this.tabManager = tabManager;
        this.identifierAnalyzer = identifierAnalyzer;
    }
    
    public boolean confirmGlobalRename(Set<String> impacted, String oldName, String newName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Le renommage de '").append(oldName).append("' en '").append(newName).append("' va impacter les classes suivantes :\n\n");
        for (String k : impacted) {
            sb.append("- ").append(k.replace(".class", "")).append("\n");
        }
        sb.append("\nContinuer ?");
        return JOptionPane.showConfirmDialog(parent, sb.toString(), "Confirmation du renommage global", 
                                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }
    
    public void renameClassEverywhere(String classKey, String oldName, String newName, 
                                      String lastClassName, String lastDecompiledCode) {
        Set<String> impacted = referencesTo.getOrDefault(classKey, new HashSet<>());
        impacted.add(classKey);
        if (!confirmGlobalRename(impacted, oldName, newName)) return;
        
        for (String impactedKey : impacted) {
            byte[] bytes = classBytes.get(impactedKey);
            if (bytes == null) continue;
            String code;
            if (openTabs.containsKey(impactedKey)) {
                code = openTabs.get(impactedKey).getText();
            } else if (impactedKey.equals(lastClassName) && lastDecompiledCode != null) {
                code = lastDecompiledCode;
            } else {
                code = decompilerManager.decompileClassToString(impactedKey, bytes);
            }
            if (hasNameConflict(code, newName, "class")) {
                JOptionPane.showMessageDialog(parent, "Conflit : une classe de ce nom existe déjà dans ce package.");
                return;
            }
            code = classRenamer.renameClassInCode(code, oldName, newName);
            if (openTabs.containsKey(impactedKey)) {
                openTabs.get(impactedKey).setText(code);
            }
            if (impactedKey.equals(classKey)) {
                tabManager.updateTabTitle(classKey, newName + ".class");
            }
        }
    }
    
    public void renameClassEverywhereAndUpdateKeys(String fullPath, String oldName, String newName,
                                                   String lastClassName, String lastDecompiledCode) {
        Set<String> impactedFiles = new HashSet<>(modifiedCode.keySet());
        impactedFiles.add(fullPath);
        String newFullPath = fullPath.replace("/" + oldName + ".class", "/" + newName + ".class")
                                     .replace(oldName + ".class", newName + ".class");
        JDialog progressDialog = new JDialog(parent, "Renommage en cours", true);
        JProgressBar progressBar = new JProgressBar(0, impactedFiles.size());
        progressBar.setStringPainted(true);
        progressDialog.getContentPane().add(progressBar);
        progressDialog.setSize(400, 80);
        progressDialog.setLocationRelativeTo(parent);
        new Thread(() -> {
            int count = 0;
            for (String classKey : impactedFiles) {
                String code;
                if (modifiedCode.containsKey(classKey)) {
                    code = modifiedCode.get(classKey);
                } else if (openTabs.containsKey(classKey)) {
                    code = openTabs.get(classKey).getText();
                } else {
                    code = decompilerManager.decompileClassToString(classKey, classBytes.get(classKey));
                }
                String newCode = classRenamer.renameClassInCode(code, oldName, newName);
                modifiedCode.put(classKey, newCode);
                if (openTabs.containsKey(classKey)) {
                    openTabs.get(classKey).setText(newCode);
                }
                if (classKey.equals(fullPath)) {
                    tabManager.updateTabTitle(fullPath, newFullPath);
                }
                count++;
                final int progress = count;
                javax.swing.SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
            }
            javax.swing.tree.DefaultMutableTreeNode root = 
                (javax.swing.tree.DefaultMutableTreeNode) treeManager.getTreeModel().getRoot();
            treeManager.updateClassNodeFullPath(root, fullPath, newFullPath, newName);
            if (!fullPath.equals(newFullPath)) {
                if (classBytes.containsKey(fullPath)) classBytes.put(newFullPath, classBytes.remove(fullPath));
                if (openTabs.containsKey(fullPath)) openTabs.put(newFullPath, openTabs.remove(fullPath));
                if (modifiedCode.containsKey(fullPath)) modifiedCode.put(newFullPath, modifiedCode.remove(fullPath));
            }
            javax.swing.SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();
                JOptionPane.showMessageDialog(parent, 
                    "Classe renommée avec succès !\n\n" +
                    "Ancien nom : " + oldName + "\n" +
                    "Nouveau nom : " + newName + "\n\n" +
                    "Toutes les références ont été mises à jour.",
                    "Renommage terminé", JOptionPane.INFORMATION_MESSAGE);
            });
        }).start();
        progressDialog.setVisible(true);
    }
    
    public void renameMethodEverywhere(String oldName, String newName) {
        Set<String> impactedFiles = new HashSet<>(modifiedCode.keySet());
        JDialog progressDialog = new JDialog(parent, "Renommage méthode global", true);
        JProgressBar progressBar = new JProgressBar(0, impactedFiles.size());
        progressBar.setStringPainted(true);
        progressDialog.getContentPane().add(progressBar);
        progressDialog.setSize(400, 80);
        progressDialog.setLocationRelativeTo(parent);
        new Thread(() -> {
            int count = 0;
            for (String classKey : impactedFiles) {
                String code = modifiedCode.get(classKey);
                if (code == null) continue;
                code = code.replaceAll("((?:public|private|protected|static|final|synchronized|native|abstract|strictfp)?\\s*[\\w<>\\[\\]]+\\s+)" + 
                                      java.util.regex.Pattern.quote(oldName) + "(\\s*\\()", "$1" + newName + "$2");
                code = code.replaceAll("(?<![\\w$])" + java.util.regex.Pattern.quote(oldName) + "\\s*\\(", newName + "(");
                code = code.replaceAll("(@Override\\s+public\\s+[\\w<>\\[\\]]+\\s+)" + 
                                      java.util.regex.Pattern.quote(oldName) + "(\\s*\\()", "$1" + newName + "$2");
                code = code.replaceAll("(@see|@link|@throws|@exception)\\s+" + 
                                      java.util.regex.Pattern.quote(oldName) + "\\b", "$1 " + newName);
                modifiedCode.put(classKey, code);
                if (openTabs.containsKey(classKey)) {
                    openTabs.get(classKey).setText(code);
                }
                count++;
                final int progress = count;
                javax.swing.SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
            }
            javax.swing.SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();
                JOptionPane.showMessageDialog(parent, "Renommage de la méthode terminé dans tout le projet.");
            });
        }).start();
        progressDialog.setVisible(true);
    }
    
    public void renameMethodInContext(String oldName, String newName, Map<String, String> modifiedCode, 
                                      Map<String, RSyntaxTextArea> openTabs, DecompilerManager decompilerManager,
                                      Map<String, byte[]> classBytes) {
        for (String classKey : modifiedCode.keySet()) {
            String code = modifiedCode.get(classKey);
            if (code == null) continue;
            List<IdentifierAnalyzer.IdentifierContext> methodContexts = identifierAnalyzer.findAllIdentifiers(code, oldName);
            String newCode = code;
            for (int i = methodContexts.size() - 1; i >= 0; i--) {
                IdentifierAnalyzer.IdentifierContext context = methodContexts.get(i);
                if (identifierAnalyzer.isMethodInContext(context)) {
                    newCode = identifierAnalyzer.renameIdentifierInContext(newCode, context, newName);
                }
            }
            modifiedCode.put(classKey, newCode);
            if (openTabs.containsKey(classKey)) {
                openTabs.get(classKey).setText(newCode);
            }
        }
        for (String classKey : classBytes.keySet()) {
            if (!modifiedCode.containsKey(classKey)) {
                String code = decompilerManager.decompileClassToString(classKey, classBytes.get(classKey));
                List<IdentifierAnalyzer.IdentifierContext> methodContexts = identifierAnalyzer.findAllIdentifiers(code, oldName);
                String newCode = code;
                for (int i = methodContexts.size() - 1; i >= 0; i--) {
                    IdentifierAnalyzer.IdentifierContext context = methodContexts.get(i);
                    if (identifierAnalyzer.isMethodInContext(context)) {
                        newCode = identifierAnalyzer.renameIdentifierInContext(newCode, context, newName);
                    }
                }
                modifiedCode.put(classKey, newCode);
                if (openTabs.containsKey(classKey)) {
                    openTabs.get(classKey).setText(newCode);
                }
            }
        }
    }
    
    private boolean hasNameConflict(String code, String name, String type) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:public|private|protected)?\\s*" + type + "\\s+" + 
                                                                          java.util.regex.Pattern.quote(name) + "\\b");
        return pattern.matcher(code).find();
    }
}

