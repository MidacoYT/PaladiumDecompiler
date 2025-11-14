import javax.swing.*;
import javax.swing.tree.*;
import java.util.*;

/**
 * Gère l'arbre de classes dans l'interface.
 */
public class TreeManager {
    private JTree classTree;
    private DefaultTreeModel treeModel;
    private final Map<String, String> classToDisplayName;
    
    // Classe interne pour l'arbre
    public static class ClassNode {
        String displayName;
        String fullPath;
        
        public ClassNode(String displayName, String fullPath) {
            this.displayName = displayName;
            this.fullPath = fullPath;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    public TreeManager(JTree classTree, Map<String, String> classToDisplayName) {
        this.classTree = classTree;
        this.classToDisplayName = classToDisplayName;
    }
    
    /**
     * Met à jour l'arbre avec les packages.
     */
    public void updateTreeWithPackages(List<String> classNames) {
        System.out.println("DEBUG: updateTreeWithPackages() - " + classNames.size() + " classes");
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Classes");
        Map<String, DefaultMutableTreeNode> packageNodes = new HashMap<>();
        
        // Désactiver temporairement les événements de l'arbre pour améliorer les performances
        classTree.setEnabled(false);
        
        for (String name : classNames) {
            String[] parts = name.split("/");
            DefaultMutableTreeNode parent = root;
            StringBuilder pkg = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (pkg.length() > 0) pkg.append("/");
                pkg.append(parts[i]);
                String pkgKey = pkg.toString();
                if (!packageNodes.containsKey(pkgKey)) {
                    DefaultMutableTreeNode pkgNode = new DefaultMutableTreeNode(parts[i]);
                    parent.add(pkgNode);
                    packageNodes.put(pkgKey, pkgNode);
                    parent = pkgNode;
                } else {
                    parent = packageNodes.get(pkgKey);
                }
            }
            // Feuille = classe
            String classFile = parts[parts.length - 1];
            String displayName = classFile.replace(".class", "");
            DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(new ClassNode(displayName, name));
            parent.add(classNode);
        }
        
        // Mise à jour du modèle d'arbre
        treeModel = new DefaultTreeModel(root);
        classTree.setModel(treeModel);
        classTree.setRootVisible(true);
        
        // Réactiver l'arbre
        classTree.setEnabled(true);
        
        // Développer les nœuds de manière optimisée
        try {
            for (int i = 0; i < Math.min(classTree.getRowCount(), 100); i++) {
                classTree.expandRow(i);
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Erreur lors du développement de l'arbre: " + e.getMessage());
        }
        
        System.out.println("DEBUG: Arborescence mise à jour avec " + packageNodes.size() + " packages");
    }
    
    /**
     * Met à jour l'arborescence de manière incrémentale.
     */
    public void updateTreeIncrementally(List<String> newClassNames) {
        System.out.println("DEBUG: updateTreeIncrementally() - " + newClassNames.size() + " classes");
        
        // Sauvegarder l'état actuel de l'arbre
        Set<String> expandedPaths = new HashSet<>();
        String selectedPath = null;
        
        // Sauvegarder les chemins développés
        for (int i = 0; i < classTree.getRowCount(); i++) {
            TreePath path = classTree.getPathForRow(i);
            if (classTree.isExpanded(path)) {
                expandedPaths.add(pathToString(path));
            }
        }
        
        // Sauvegarder la sélection actuelle
        TreePath currentSelection = classTree.getSelectionPath();
        if (currentSelection != null) {
            selectedPath = pathToString(currentSelection);
        }
        
        // Désactiver temporairement les événements
        classTree.setEnabled(false);
        
        // Créer la nouvelle arborescence
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Classes");
        Map<String, DefaultMutableTreeNode> packageNodes = new HashMap<>();
        
        for (String name : newClassNames) {
            String[] parts = name.split("/");
            DefaultMutableTreeNode parent = root;
            StringBuilder pkg = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (pkg.length() > 0) pkg.append("/");
                pkg.append(parts[i]);
                String pkgKey = pkg.toString();
                if (!packageNodes.containsKey(pkgKey)) {
                    DefaultMutableTreeNode pkgNode = new DefaultMutableTreeNode(parts[i]);
                    parent.add(pkgNode);
                    packageNodes.put(pkgKey, pkgNode);
                    parent = pkgNode;
                } else {
                    parent = packageNodes.get(pkgKey);
                }
            }
            // Feuille = classe
            String classFile = parts[parts.length - 1];
            String displayName = classFile.replace(".class", "");
            DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(new ClassNode(displayName, name));
            parent.add(classNode);
        }
        
        // Mettre à jour le modèle
        treeModel = new DefaultTreeModel(root);
        classTree.setModel(treeModel);
        classTree.setRootVisible(true);
        
        // Réactiver l'arbre
        classTree.setEnabled(true);
        
        // Restaurer l'état développé
        for (int i = 0; i < classTree.getRowCount(); i++) {
            TreePath path = classTree.getPathForRow(i);
            String pathStr = pathToString(path);
            if (expandedPaths.contains(pathStr)) {
                classTree.expandPath(path);
            }
        }
        
        // Restaurer la sélection si possible
        if (selectedPath != null) {
            TreePath newSelectionPath = stringToPath(selectedPath, root);
            if (newSelectionPath != null) {
                classTree.setSelectionPath(newSelectionPath);
                classTree.scrollPathToVisible(newSelectionPath);
            }
        }
        
        System.out.println("DEBUG: Arborescence mise à jour incrémentalement avec " + packageNodes.size() + " packages");
    }
    
    private String pathToString(TreePath path) {
        if (path == null) return null;
        StringBuilder sb = new StringBuilder();
        Object[] pathArray = path.getPath();
        for (int i = 0; i < pathArray.length; i++) {
            if (i > 0) sb.append("/");
            sb.append(pathArray[i].toString());
        }
        return sb.toString();
    }
    
    private TreePath stringToPath(String pathStr, DefaultMutableTreeNode root) {
        if (pathStr == null) return null;
        String[] parts = pathStr.split("/");
        if (parts.length == 0) return null;
        
        // Commencer par la racine
        if (!parts[0].equals("Classes")) return null;
        
        DefaultMutableTreeNode current = root;
        for (int i = 1; i < parts.length; i++) {
            boolean found = false;
            for (int j = 0; j < current.getChildCount(); j++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) current.getChildAt(j);
                if (child.getUserObject().toString().equals(parts[i])) {
                    current = child;
                    found = true;
                    break;
                }
            }
            if (!found) return null;
        }
        
        return new TreePath(current.getPath());
    }
    
    public DefaultTreeModel getTreeModel() {
        return treeModel;
    }
    
    public JTree getClassTree() {
        return classTree;
    }
    
    /**
     * Met à jour le fullPath et displayName dans l'arbre.
     */
    public void updateClassNodeFullPath(DefaultMutableTreeNode node, String oldFull, String newFull, String newDisplay) {
        if (node.getUserObject() instanceof ClassNode) {
            ClassNode cn = (ClassNode) node.getUserObject();
            if (cn.fullPath.equals(oldFull)) {
                cn.fullPath = newFull;
                cn.displayName = newDisplay;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            updateClassNodeFullPath((DefaultMutableTreeNode) node.getChildAt(i), oldFull, newFull, newDisplay);
        }
    }
}

