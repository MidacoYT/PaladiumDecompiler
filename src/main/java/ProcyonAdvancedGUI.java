import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.*;
import java.util.List;
import java.awt.datatransfer.DataFlavor;
import javax.swing.undo.UndoManager;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CannotRedoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeNode;
import javax.swing.TransferHandler;
import java.awt.datatransfer.Transferable;


public class ProcyonAdvancedGUI extends JFrame {
    private RSyntaxTextArea codeArea;
    private JButton openButton, searchButton;
    private JTextField searchField;
    private JTree classTree;
    private Map<String, byte[]> classBytes = new HashMap<>();
    private Map<String, String> classToDisplayName = new HashMap<>();
    private String lastDecompiledCode = null;
    private String lastClassName = null;

    // Managers
    private DecompilerManager decompilerManager;
    private CacheManager cacheManager;
    private ReferenceManager referenceManager;
    private TreeManager treeManager;
    private SearchManager searchManager;
    private ExportManager exportManager;
    private ProjectManager projectManager;
    private ThemeManager themeManager;
    private ClassRenamer classRenamer = new ClassRenamer();
    private TabManager tabManager;
    private CodeEditorManager codeEditorManager;
    private NavigationManager navigationManager;
    private RenameManager renameManager;
    private IdentifierAnalyzer identifierAnalyzer;

    // Références aux maps du ReferenceManager (pour compatibilité)
    private Map<String, Set<String>> referencesTo;
    private Map<String, Set<String>> referenceIndex;

    private JTabbedPane tabbedPane;
    // Map : nom complet de la classe -> RSyntaxTextArea de l'onglet
    private Map<String, RSyntaxTextArea> openTabs = new HashMap<>();
    private JTextField globalSearchField;
    private JButton globalSearchButton;
    private JList<String> globalSearchResults;
    private DefaultListModel<String> globalSearchListModel;
    private Map<String, Integer> resultToLine = new HashMap<>();
    private Map<String, String> resultToClass = new HashMap<>();
    private JButton exportJarButton;
    private JButton exportCompiledJarButton;
    // Map pour stocker le code modifié de toutes les classes (même non ouvertes)
    private Map<String, String> modifiedCode = new HashMap<>();
    private boolean darkTheme = true;
    private JButton themeButton;
    private JButton saveProjectButton;
    private JButton loadProjectButton;
    private JButton saveButton;
    private JButton resetButton;
    // Map pour stocker le code décompilé d'origine de toutes les classes
    private Map<String, String> originalCode = new HashMap<>();
    private final File cacheDir = new File(".paladiumcache");
    private JButton clearCacheButton;
    private JButton refreshTreeButton;
    private JLabel obfuscatedClassesLabel; // Nouveau label pour afficher le compteur

    public ProcyonAdvancedGUI() {
        // Config fenêtre
        setTitle("Procyon Decompiler GUI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 850);
        setLayout(new BorderLayout());

        // ========== MANAGERS DE BASE (pas de composants Swing nécessaires) ==========
        cacheManager = new CacheManager(cacheDir);
        decompilerManager = new DecompilerManager(cacheDir, classBytes, modifiedCode);
        referenceManager = new ReferenceManager();
        referencesTo = referenceManager.getReferencesTo();
        referenceIndex = referenceManager.getReferenceIndex();
        exportManager = new ExportManager(this, classBytes, modifiedCode, openTabs, decompilerManager);
        projectManager = new ProjectManager(this, classBytes, modifiedCode, classToDisplayName, openTabs, decompilerManager, null);

        themeManager = new ThemeManager(this);
        themeManager.initializeDarkTheme();
        themeManager.setTextAreas(openTabs.values());

        // ⭐ IMPORTANT : tabbedPane AVANT TabManager
        tabbedPane = new JTabbedPane();

        // TabManager utilise un tabbedPane non null
        tabManager = new TabManager(
                tabbedPane,
                openTabs,
                themeManager
        );

        // ========== BOUTONS & CHAMPS ==========
        openButton = new JButton("Ouvrir un .class ou .jar");
        saveButton = new JButton("Enregistrer");
        resetButton = new JButton("Réinitialiser");
        exportJarButton = new JButton("Exporter en JAR");
        exportCompiledJarButton = new JButton("Exporter en JAR compilé");
        themeButton = new JButton("Thème clair/sombre");
        saveProjectButton = new JButton("Sauvegarder projet");
        loadProjectButton = new JButton("Charger projet");
        searchButton = new JButton("Rechercher");
        globalSearchButton = new JButton("Recherche globale");

        // Nouveau bouton pour l'analyse IA avancée
        JButton aiAnalysisButton = new JButton("🤖 Analyse IA avancée");
        aiAnalysisButton.setToolTipText("Analyse IA avancée pour renommer automatiquement les classes");
        aiAnalysisButton.addActionListener(e -> advancedAIAnalysisAndRename());

        searchField = new JTextField(30);
        globalSearchField = new JTextField(20);
        globalSearchListModel = new DefaultListModel<>();
        globalSearchResults = new JList<>(globalSearchListModel);
        globalSearchResults.setVisibleRowCount(5);
        globalSearchResults.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // SearchManager (dépend du modèle de recherche globale)
        searchManager = new SearchManager(classBytes, modifiedCode, globalSearchListModel, decompilerManager);

        clearCacheButton = new JButton("Vider le cache");
        clearCacheButton.addActionListener(e -> {
            cacheManager.clearCache();
            JOptionPane.showMessageDialog(this, "Cache vidé !");
        });

        refreshTreeButton = new JButton("Rafraîchir l'arborescence");
        refreshTreeButton.addActionListener(e -> refreshTree());

        obfuscatedClassesLabel = new JLabel("Classes obfusquées: 0");
        obfuscatedClassesLabel.setForeground(java.awt.Color.ORANGE);
        obfuscatedClassesLabel.setFont(obfuscatedClassesLabel.getFont().deriveFont(java.awt.Font.BOLD));

        JButton refreshObfuscatedButton = new JButton("🔄");
        refreshObfuscatedButton.setToolTipText("Rafraîchir le compteur de classes obfusquées");
        refreshObfuscatedButton.addActionListener(e -> updateObfuscatedClassesCount());

        // Listeners boutons principaux
        openButton.addActionListener(this::onOpen);
        saveButton.addActionListener(e -> saveEditedCode());
        resetButton.addActionListener(e -> resetDecompiledCode());
        exportJarButton.addActionListener(e -> exportManager.exportToJar());
        exportCompiledJarButton.addActionListener(e -> exportManager.exportToCompiledJar());
        themeButton.addActionListener(e -> themeManager.toggleTheme());
        saveProjectButton.addActionListener(e -> projectManager.saveProjectState());
        loadProjectButton.addActionListener(e -> projectManager.loadProjectState(
                () -> treeManager.updateTreeWithPackages(new ArrayList<>(classBytes.keySet())),
                () -> autoRenameAllClasses()
        ));
        searchButton.addActionListener(e -> searchManager.searchInCode(getCurrentCodeArea(), searchField.getText()));
        globalSearchButton.addActionListener(e -> {
            searchManager.performGlobalSearch(globalSearchField.getText(), openTabs);
            updateGlobalSearchVisibility();
        });
        globalSearchResults.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String key = globalSearchResults.getSelectedValue();
                if (key != null && resultToClass.containsKey(key)) {
                    String className = resultToClass.get(key);
                    int line = resultToLine.getOrDefault(key, 0);
                    decompileClassBytes(className, classBytes.get(className));
                    RSyntaxTextArea area = openTabs.get(className);
                    if (area != null) {
                        try {
                            int pos = area.getLineStartOffset(line);
                            area.setCaretPosition(pos);
                            area.requestFocus();
                        } catch (Exception ex) {}
                    }
                }
            }
        });

        codeArea = new RSyntaxTextArea(30, 80);
        codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        codeArea.setCodeFoldingEnabled(true);
        codeArea.setEditable(true);

        // Panels haut (action / recherches) — (ton code existant ici...)

        JPanel actionPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        actionPanel.add(openButton);
        actionPanel.add(saveButton);
        actionPanel.add(resetButton);
        actionPanel.add(exportJarButton);
        actionPanel.add(exportCompiledJarButton);
        actionPanel.add(themeButton);
        actionPanel.add(saveProjectButton);
        actionPanel.add(loadProjectButton);
        actionPanel.add(aiAnalysisButton);
        actionPanel.add(clearCacheButton);
        actionPanel.add(refreshTreeButton);
        actionPanel.add(obfuscatedClassesLabel);
        actionPanel.add(refreshObfuscatedButton);

        JPanel globalSearchPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        globalSearchPanel.add(new JLabel("Recherche globale :"));
        globalSearchPanel.add(globalSearchField);
        globalSearchPanel.add(globalSearchButton);

        globalSearchResults.setVisible(false);
        globalSearchListModel.addListDataListener(new javax.swing.event.ListDataListener() {
            public void intervalAdded(javax.swing.event.ListDataEvent e) { updateGlobalSearchVisibility(); }
            public void intervalRemoved(javax.swing.event.ListDataEvent e) { updateGlobalSearchVisibility(); }
            public void contentsChanged(javax.swing.event.ListDataEvent e) { updateGlobalSearchVisibility(); }
        });
        JScrollPane globalSearchScroll = new JScrollPane(globalSearchResults);
        globalSearchScroll.setPreferredSize(new java.awt.Dimension(400, 80));
        globalSearchPanel.add(globalSearchScroll);

        JPanel localSearchPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        localSearchPanel.add(new JLabel("Recherche :"));
        localSearchPanel.add(searchField);
        localSearchPanel.add(searchButton);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new javax.swing.BoxLayout(topPanel, javax.swing.BoxLayout.Y_AXIS));
        topPanel.add(actionPanel);
        topPanel.add(globalSearchPanel);
        topPanel.add(localSearchPanel);

        // ========== ARBRE DE CLASSES & TREEMANAGER ==========
        classTree = new JTree(new DefaultMutableTreeNode("Aucun fichier ouvert"));
        classTree.setRootVisible(true);

        treeManager = new TreeManager(classTree, classToDisplayName);

        classTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) classTree.getLastSelectedPathComponent();
            if (node == null || node.getUserObject() == null) return;
            Object userObj = node.getUserObject();
            if (userObj instanceof TreeManager.ClassNode) {
                String className = ((TreeManager.ClassNode) userObj).fullPath;
                System.out.println("[JTree select] User sélectionné : " + className);
                if (classBytes.containsKey(className)) {
                    decompileClassBytes(className, classBytes.get(className));
                } else {
                    System.out.println("[JTree select] Classe non trouvée en mémoire: " + className);
                }
            }
        });

        // ⭐ ICI : MANAGERS DÉPENDANTS DE treeManager / tabManager, etc.
        identifierAnalyzer = new IdentifierAnalyzer();

        renameManager = new RenameManager(
                this,
                classBytes,
                modifiedCode,
                openTabs,
                referencesTo,
                classRenamer,
                decompilerManager,
                treeManager,
                tabManager,
                identifierAnalyzer
        );

        navigationManager = new NavigationManager(
                classBytes,
                openTabs,
                identifierAnalyzer,
                renameManager,
                decompilerManager,
                () -> {
                    // TODO : à adapter si tu veux utiliser Ctrl+clic pour ouvrir une classe précise.
                    // Par exemple : stocker la classe ciblée dans NavigationManager et l’utiliser ici.
                }
        );

        codeEditorManager = new CodeEditorManager(
                themeManager,
                identifierAnalyzer,
                renameManager,
                navigationManager
        );

        // === Menu contextuel pour renommer / analyser / Ollama ===
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem renameItem = new JMenuItem("Renommer la classe...");
        JMenuItem analyzeItem = new JMenuItem("🤖 Analyser avec IA...");
        JMenuItem analyzeOllamaItem = new JMenuItem("🔎 Analyse IA Ollama");
        popupMenu.add(renameItem);
        popupMenu.add(analyzeItem);
        popupMenu.add(analyzeOllamaItem);
        classTree.setComponentPopupMenu(popupMenu);
        classTree.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    int row = classTree.getClosestRowForLocation(e.getX(), e.getY());
                    classTree.setSelectionRow(row);
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) classTree.getLastSelectedPathComponent();
                    if (node != null && node.getUserObject() instanceof TreeManager.ClassNode) {
                        popupMenu.show(classTree, e.getX(), e.getY());
                    }
                }
            }
            public void mouseReleased(java.awt.event.MouseEvent e) {
                mousePressed(e);
            }
        });

        // Action pour l'analyse IA simple
        analyzeItem.addActionListener(ev -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) classTree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof TreeManager.ClassNode) {
                TreeManager.ClassNode classNode = (TreeManager.ClassNode) node.getUserObject();
                String className = classNode.fullPath;

                if (classBytes.containsKey(className)) {
                    String code = modifiedCode.containsKey(className)
                            ? modifiedCode.get(className)
                            : decompilerManager.decompileClassToString(className, classBytes.get(className));
                    String simpleClassName = getSimpleClassName(className);

                    ClassAnalysisResult analysis = performAdvancedAIAnalysis(code, simpleClassName);

                    JOptionPane.showMessageDialog(this,
                            "Analyse IA pour la classe : " + simpleClassName + "\n\n" +
                                    "Type détecté : " + analysis.classType + "\n" +
                                    "Package suggéré : " + (analysis.suggestedPackage != null ? analysis.suggestedPackage : "Aucun") + "\n" +
                                    "Nom suggéré : " + analysis.suggestedClassName + "\n" +
                                    "Niveau de confiance : " + analysis.confidence + "\n" +
                                    "Raisonnement : " + analysis.reasoning,
                            "Analyse IA", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });

        // Action pour l'analyse IA via Ollama (modèle gpt-oss:30b)
        analyzeOllamaItem.addActionListener(ev -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) classTree.getLastSelectedPathComponent();
            if (node == null || !(node.getUserObject() instanceof TreeManager.ClassNode)) return;
            TreeManager.ClassNode classNode = (TreeManager.ClassNode) node.getUserObject();
            String className = classNode.fullPath;
            String simpleClassName = getSimpleClassName(className);
            String code = modifiedCode.containsKey(className)
                    ? modifiedCode.get(className)
                    : decompilerManager.decompileClassToString(className, classBytes.get(className));

            // Récupérer contexte usages
            Set<String> usages = new HashSet<>();
            for (Map.Entry<String, Set<String>> e : referencesTo.entrySet()) {
                if (e.getValue().contains(className)) usages.add(e.getKey());
            }
            StringBuilder usagesCode = new StringBuilder();
            for (String usage : usages) {
                usagesCode.append("// Classe où utilisée: ").append(usage).append("\n");
                String usageCode = (modifiedCode.containsKey(usage) ? modifiedCode.get(usage) : decompilerManager.decompileClassToString(usage, classBytes.get(usage)));
                usagesCode.append(usageCode.substring(0, Math.min(usageCode.length(), 800))).append("\n\n");
            }

            String prompt = "Tu es un assistant expert Minecraft modding, tu reçois le code d'une classe d'un mod Minecraft 1.7.10 obfusqué et des extraits de classes où elle est utilisée. " +
                    "Déduis le rôle de la classe, propose un nom Java valide clair et adapté (si déjà nommé, indique-le mais propose tout de même un package plus adapté), " +
                    "explique brièvement, et suggère aussi un chemin package.\n\n" +
                    "// code de la classe\n" + code + "\n\n" +
                    "// extraits de classes où cette classe est utilisée:\n" + usagesCode.toString() + "\n\n" +
                    "Retourne en JSON bien formaté: { \"suggestedClassName\": ..., \"suggestedPackage\": ..., \"reasoning\": ... }";

            System.out.println("[OllamaIA] Construction prompt (class=" + className + ")\nPrompt:\n" + prompt);
            final JDialog waitDialog = new JDialog(this, "Analyse IA en cours", true);
            waitDialog.getContentPane().add(new JLabel("Analyse IA Ollama en cours..."), BorderLayout.CENTER);
            waitDialog.setSize(350, 90);
            waitDialog.setLocationRelativeTo(this);

            new Thread(() -> {
                try {
                    // 🔁 Utilisation du modèle demandé : gpt-oss:30b
                    String ollamaResponse = OllamaApi.askOllama("gpt-oss:20b", prompt);
                    System.out.println("[OllamaIA] Réponse brute :\n" + ollamaResponse);
                    SwingUtilities.invokeLater(waitDialog::dispose);

                    String suggestedClassName = null;
                    String suggestedPackage = null;
                    String reasoning = null;
                    try {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{[^}]+\\}").matcher(ollamaResponse);
                        if (m.find()) {
                            String json = m.group();
                            suggestedClassName = extractJsonField(json, "suggestedClassName");
                            suggestedPackage = extractJsonField(json, "suggestedPackage");
                            reasoning = extractJsonField(json, "reasoning");
                        }
                    } catch (Exception jex) {
                        suggestedClassName = null;
                    }
                    if (suggestedClassName == null) suggestedClassName = simpleClassName;
                    if (suggestedPackage == null) suggestedPackage = "fr.paladium.palamod.autoai";
                    if (reasoning == null) reasoning = ollamaResponse;

                    final String sClassName = suggestedClassName;
                    final String sPackage = suggestedPackage;
                    final String sReason = reasoning;

                    SwingUtilities.invokeLater(() -> {
                        JPanel panel = new JPanel(new BorderLayout(10, 10));
                        String alreadyWellNamedMsg = "";
                        if (!ClassAnalysisHelper.isObfuscatedClassName(simpleClassName)) {
                            alreadyWellNamedMsg = "Nom déjà correct (" + simpleClassName + "), suggestion de package seulement.";
                        }
                        JTextField classField = new JTextField(sClassName);
                        JTextField packageField = new JTextField(sPackage);
                        JTextArea reasonText = new JTextArea(sReason, 3, 40);
                        reasonText.setWrapStyleWord(true);
                        reasonText.setLineWrap(true);
                        reasonText.setEditable(false);

                        panel.add(new JLabel(alreadyWellNamedMsg), BorderLayout.NORTH);
                        JPanel edits = new JPanel(new GridLayout(2, 2));
                        edits.add(new JLabel("Classe"));
                        edits.add(classField);
                        edits.add(new JLabel("Package"));
                        edits.add(packageField);
                        panel.add(edits, BorderLayout.CENTER);
                        panel.add(new JScrollPane(reasonText), BorderLayout.SOUTH);

                        Object[] options = {"Appliquer renommage", "Corriger manuellement", "Annuler"};
                        int res = JOptionPane.showOptionDialog(
                                ProcyonAdvancedGUI.this,
                                panel,
                                "Suggestion IA Ollama",
                                JOptionPane.DEFAULT_OPTION,
                                JOptionPane.PLAIN_MESSAGE,
                                null,
                                options,
                                options[0]
                        );
                        if (res == 0 || res == 1) {
                            String newClass = classField.getText().trim();
                            String newPkg = packageField.getText().trim();
                            if (!newPkg.isEmpty() && !newClass.isEmpty()) {
                                String newFull = newPkg.replace('.', '/') + "/" + newClass + ".class";
                                renameClassEverywhereAndUpdateKeys(className.replace("\\.class", ".class"), simpleClassName, newClass);
                                classToDisplayName.put(newFull, newClass);
                                if (res == 1) {
                                    JOptionPane.showMessageDialog(ProcyonAdvancedGUI.this,
                                            "Vous pouvez réexécuter une suggestion IA pour trouver mieux ou saisir un nom plus parlant.");
                                }
                            }
                        }
                    });
                } catch (Exception ex) {
                    System.out.println("[OllamaIA] ERREUR: " + ex.getMessage() + "\nPrompt:\n" + prompt);
                    SwingUtilities.invokeLater(() -> {
                        waitDialog.dispose();
                        JOptionPane.showMessageDialog(ProcyonAdvancedGUI.this, "Erreur Ollama: " + ex.getMessage());
                    });
                }
            }).start();
            waitDialog.setVisible(true);
        });

        // Action pour le renommage manuel via le menu contextuel
        renameItem.addActionListener(ev -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) classTree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof TreeManager.ClassNode) {
                TreeManager.ClassNode classNode = (TreeManager.ClassNode) node.getUserObject();
                String oldName = classNode.displayName;
                String newName = JOptionPane.showInputDialog(ProcyonAdvancedGUI.this, "Nouveau nom de classe :", oldName);
                if (newName != null && !newName.trim().isEmpty() && !newName.equals(oldName)) {
                    classNode.displayName = newName;
                    treeManager.getTreeModel().nodeChanged(node);
                    String sep = "(^|[\\s\\(\\)\\[\\]\\{\\}<>,;.=+\\-*/%!?:&|^~])";
                    Set<String> impacted = referencesTo.getOrDefault(classNode.fullPath, new HashSet<>());
                    impacted.add(classNode.fullPath);
                    for (String classKey : impacted) {
                        byte[] bytes = classBytes.get(classKey);
                        if (bytes == null) continue;
                        String code;
                        if (classKey.equals(lastClassName) && lastDecompiledCode != null) {
                            code = lastDecompiledCode;
                        } else {
                            code = decompilerManager.decompileClassToString(classKey, bytes);
                        }
                        code = classRenamer.renameClassInCode(code, oldName, newName);
                        if (classKey.equals(lastClassName)) {
                            codeArea.setText(code);
                            lastDecompiledCode = code;
                        }
                    }
                }
            }
        });

        // ========== TABBEDPANE & SPLITPANE ==========
        JPopupMenu tabPopupMenu = new JPopupMenu();
        JMenuItem closeAllTabsItem = new JMenuItem("Fermer tous les onglets");
        closeAllTabsItem.addActionListener(e -> tabManager.closeAllTabs());
        tabPopupMenu.add(closeAllTabsItem);

        tabbedPane.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    tabPopupMenu.show(tabbedPane, e.getX(), e.getY());
                }
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                mousePressed(e);
            }
        });

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(classTree),
                tabbedPane
        );
        splitPane.setDividerLocation(350);

        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        // DropTarget pour permettre le drag & drop de fichiers JAR/CLASS sur la fenêtre
        new java.awt.dnd.DropTarget(this, new java.awt.dnd.DropTargetListener() {
            @Override public void dragEnter(java.awt.dnd.DropTargetDragEvent dtde) {}
            @Override public void dragOver(java.awt.dnd.DropTargetDragEvent dtde) {}
            @Override public void dropActionChanged(java.awt.dnd.DropTargetDragEvent dtde) {}
            @Override public void dragExit(java.awt.dnd.DropTargetEvent dte) {}
            @Override public void drop(java.awt.dnd.DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) dtde.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    if (droppedFiles != null && !droppedFiles.isEmpty()) {
                        File file = droppedFiles.get(0);
                        if (file.getName().endsWith(".class")) {
                            openClass(file);
                        } else if (file.getName().endsWith(".jar")) {
                            openJar(file);
                        } else {
                            JOptionPane.showMessageDialog(ProcyonAdvancedGUI.this, "Déposez un fichier .class ou .jar");
                        }
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ProcyonAdvancedGUI.this, "Erreur lors du drag & drop : " + ex.getMessage());
                }
            }
        });

        // Drag & drop dans l'arbre (inchangé)
        classTree.setDragEnabled(true);
        classTree.setDropMode(DropMode.ON_OR_INSERT);
        classTree.setTransferHandler(new TransferHandler() {
            public int getSourceActions(JComponent c) { return MOVE; }
            protected Transferable createTransferable(JComponent c) {
                TreePath[] paths = classTree.getSelectionPaths();
                if (paths == null || paths.length == 0) return null;
                return new Transferable() {
                    public DataFlavor[] getTransferDataFlavors() {
                        try {
                            return new DataFlavor[]{new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType)};
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    public boolean isDataFlavorSupported(DataFlavor flavor) {
                        return flavor.getMimeType().equals(DataFlavor.javaJVMLocalObjectMimeType);
                    }
                    public Object getTransferData(DataFlavor flavor) {
                        return paths;
                    }
                };
            }
            public boolean canImport(TransferSupport support) {
                if (!support.isDrop()) return false;
                for (DataFlavor flavor : support.getDataFlavors()) {
                    if (flavor.getMimeType().equals(DataFlavor.javaJVMLocalObjectMimeType)) {
                        support.setShowDropLocation(true);
                        return true;
                    }
                }
                return false;
            }
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    Transferable t = support.getTransferable();
                    Object data = t.getTransferData(new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType));
                    TreePath[] paths;
                    if (data instanceof TreePath[]) {
                        paths = (TreePath[]) data;
                    } else if (data instanceof List) {
                        List<?> list = (List<?>) data;
                        paths = list.toArray(new TreePath[0]);
                    } else {
                        return false;
                    }
                    TreePath dest = ((JTree.DropLocation) support.getDropLocation()).getPath();
                    if (paths == null || dest == null) return false;
                    DefaultMutableTreeNode destNode = (DefaultMutableTreeNode) dest.getLastPathComponent();
                    for (TreePath path : paths) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.getUserObject() instanceof TreeManager.ClassNode) {
                            TreeManager.ClassNode classNode = (TreeManager.ClassNode) node.getUserObject();
                            // Nouveau package = chemin du destNode
                            StringBuilder newPkg = new StringBuilder();
                            TreeNode[] destPath = destNode.getPath();
                            for (int i = 1; i < destPath.length; i++) { // skip root
                                String s = destPath[i].toString();
                                if (!s.equals("Classes")) {
                                    if (newPkg.length() > 0) newPkg.append("/");
                                    newPkg.append(s);
                                }
                            }
                            String oldFull = classNode.fullPath;
                            String className = oldFull.substring(oldFull.lastIndexOf('/') + 1);
                            String newFull = (newPkg.length() > 0 ? newPkg + "/" : "") + className;
                            // Met à jour la clé dans classBytes, classToDisplayName, openTabs
                            byte[] bytes = classBytes.remove(oldFull);
                            classBytes.put(newFull, bytes);
                            classToDisplayName.put(newFull, classToDisplayName.remove(oldFull));
                            RSyntaxTextArea area = openTabs.remove(oldFull);
                            if (area != null) openTabs.put(newFull, area);
                            // Met à jour l'objet ClassNode
                            classNode.fullPath = newFull;
                            // Met à jour toutes les références dans toutes les classes
                            renameClassEverywhere(oldFull, className.replace(".class", ""), className.replace(".class", ""));
                            // Met à jour l'arbre
                            node.removeFromParent();
                            destNode.add(node);
                        }
                    }
                    treeManager.getTreeModel().reload();
                    return true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return false;
                }
            }
        });
    }

    private void onOpen(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (file.getName().endsWith(".class")) {
                openClass(file);
            } else if (file.getName().endsWith(".jar")) {
                openJar(file);
            } else {
                JOptionPane.showMessageDialog(this, "Sélectionnez un .class ou .jar");
            }
        }
    }

    // Affiche une popup de progression lors de la toute première ouverture d'un onglet (décompilation lazy de masse)
    private void showLazyDecompileProgressIfNeeded(Set<String> classesToDecompile) {
        if (classesToDecompile.size() <= 1) return; // Pas besoin si une seule classe
        JDialog progressDialog = new JDialog(this, "Décompilation initiale en cours", true);
        JProgressBar progressBar = new JProgressBar(0, classesToDecompile.size());
        progressBar.setStringPainted(true);
        progressDialog.getContentPane().add(progressBar);
        progressDialog.setSize(400, 80);
        progressDialog.setLocationRelativeTo(this);
        new Thread(() -> {
            int count = 0;
            for (String className : classesToDecompile) {
                if (!modifiedCode.containsKey(className) && !openTabs.containsKey(className)) {
                    decompilerManager.decompileClassToString(className, classBytes.get(className));
                }
                count++;
                final int progress = count;
                javax.swing.SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
            }
            javax.swing.SwingUtilities.invokeLater(progressDialog::dispose);
        }).start();
        progressDialog.setVisible(true);
    }

    private void openJar(File jarFile) {
        try {
            classBytes.clear();
            classToDisplayName.clear();
            referenceManager.clear();
            originalCode.clear();
            modifiedCode.clear();
            List<String> classNames = decompilerManager.loadJar(jarFile);
            for (String className : classNames) {
                classToDisplayName.put(className, className);
                referenceManager.indexClassReferences(className, classBytes.get(className));
            }
            referenceManager.buildReferenceIndexFast(classBytes);
            treeManager.updateTreeWithPackages(classNames);
            // Ajoute ceci :
            if (!classNames.isEmpty()) {
                decompileClassBytes(classNames.get(0), classBytes.get(classNames.get(0)));
            }
            // Décompilation de toutes les classes en tâche de fond avec popup de progression
            javax.swing.JDialog progressDialog = new javax.swing.JDialog(this, "Décompilation initiale", true);
            javax.swing.JProgressBar progressBar = new javax.swing.JProgressBar(0, classNames.size());
            progressBar.setStringPainted(true);
            progressDialog.add(progressBar);
            progressDialog.setSize(400, 80);
            progressDialog.setLocationRelativeTo(this);
            new Thread(() -> {
                int i = 0;
                System.out.println("DEBUG: Début décompilation de " + classNames.size() + " classes");
                try {
                for (String className : classNames) {
                    try {
                        System.out.println("DEBUG: Décompilation " + (i+1) + "/" + classNames.size() + " : " + className);
                        String code = decompilerManager.decompileClassToString(className, classBytes.get(className));
                        modifiedCode.put(className, code);
                        System.out.println("DEBUG: ✓ " + className + " décompilée et mise en cache");
                    } catch (Exception ex) {
                        System.out.println("DEBUG: ✗ Erreur pour " + className + " : " + ex.getMessage());
                        modifiedCode.put(className, "// Erreur de décompilation: " + ex.getMessage());
                    }
                    final int progress = ++i;
                    javax.swing.SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                }
                System.out.println("DEBUG: Fin décompilation. Total traitées : " + i + "/" + classNames.size());
                } catch (Exception e) {
                    System.out.println("DEBUG: Erreur générale dans la décompilation : " + e.getMessage());
                } finally {
                    // Ferme d'abord la popup de progression
                    javax.swing.SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();
                        
                        // Puis lance le renommage automatique dans un thread séparé
                        new Thread(() -> {
                            try {
                                System.out.println("DEBUG: Début renommage automatique");
                                autoRenameAllClasses();
                                System.out.println("DEBUG: Fin renommage automatique");
                                
                                // Ouvre la première classe automatiquement après le renommage
                if (!classNames.isEmpty()) {
                                    javax.swing.SwingUtilities.invokeLater(() -> {
                                        try {
                                            decompileClassBytes(classNames.get(0), classBytes.get(classNames.get(0)));
                                        } catch (Exception e) {
                                            System.out.println("DEBUG: Erreur ouverture première classe : " + e.getMessage());
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                System.out.println("DEBUG: Erreur renommage automatique : " + e.getMessage());
                                // En cas d'erreur, ouvre quand même la première classe
                                if (!classNames.isEmpty()) {
                                    javax.swing.SwingUtilities.invokeLater(() -> {
                                        try {
                                            decompileClassBytes(classNames.get(0), classBytes.get(classNames.get(0)));
                                        } catch (Exception ex) {
                                            System.out.println("DEBUG: Erreur ouverture première classe : " + ex.getMessage());
                                        }
                                    });
                                }
                            }
                        }).start();
                    });
                }
            }).start();
            progressDialog.setVisible(true);
        } catch (Exception ex) {
            codeArea.setText("Erreur de lecture du jar : " + ex.getMessage());
        }
    }

    // Méthode déléguée à ReferenceManager
    private void indexClassReferences(String className, byte[] bytes) {
        referenceManager.indexClassReferences(className, bytes);
    }

    private void openClass(File file) {
        try {
        classBytes.clear();
        classToDisplayName.clear();
            referenceManager.clear();
            decompilerManager.loadClass(file);
            // classBytes est déjà rempli par loadClass
            referenceManager.indexClassReferences(file.getName(), classBytes.get(file.getName()));
            treeManager.updateTreeWithPackages(Collections.singletonList(file.getName()));
            // Ajoute :
        decompileClassBytes(file.getName(), classBytes.get(file.getName()));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erreur lors de l'ouverture du fichier : " + e.getMessage());
        }
    }

    // Méthode déléguée à TreeManager
    private void updateTreeWithPackages(List<String> classNames) {
        treeManager.updateTreeWithPackages(classNames);
        updateObfuscatedClassesCount();
    }

    private String getFullClassName(DefaultMutableTreeNode node) {
        // Remonte l'arbre pour reconstituer le nom complet
        List<String> parts = new ArrayList<>();
        while (node != null && node.getParent() != null) {
            String s = node.getUserObject().toString();
            if (!s.equals("Classes")) parts.add(s);
            node = (DefaultMutableTreeNode) node.getParent();
        }
        Collections.reverse(parts);
        String name = String.join("/", parts);
        if (classBytes.containsKey(name)) return name;
        return "";
    }

    private void decompileClassBytes(String className, byte[] bytes) {
        System.out.println("[decompileClassBytes] Appel pour : " + className);
        try {
            String code = modifiedCode.containsKey(className)
                ? modifiedCode.get(className)
                : decompilerManager.decompileClassToString(className, bytes);
            RSyntaxTextArea area = tabManager.createOrGetTab(className, () -> {
                RSyntaxTextArea newArea = openTabs.get(className);
                if (newArea != null) {
                    codeEditorManager.setupEditor(newArea, () -> {});
                    themeManager.setTextAreas(openTabs.values());
                }
            });
            if (area != null) {
                int oldPos = area.getCaretPosition();
                area.setText(code);
                if (oldPos < code.length()) area.setCaretPosition(oldPos);
            lastDecompiledCode = code;
            lastClassName = className;
                System.out.println("[decompileClassBytes] Code affiché pour : " + className);
            }
        } catch (Exception ex) {
            System.out.println("[decompileClassBytes] Erreur pour : " + className + " -> " + ex.getMessage());
            RSyntaxTextArea area = openTabs.get(className);
            if (area != null) {
                area.setText("Erreur de décompilation : " + ex.getMessage());
            }
        }
    }

    private void saveEditedCode() {
        RSyntaxTextArea area = getCurrentCodeArea();
        if (area == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Enregistrer sous");
        String fileName = (lastClassName != null ? lastClassName.replace("/", "_").replace(".class", ".java") : "Decompile.java");
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) classTree.getLastSelectedPathComponent();
        if (node != null && node.getUserObject() instanceof TreeManager.ClassNode) {
            String displayName = ((TreeManager.ClassNode) node.getUserObject()).displayName;
            if (displayName != null && !displayName.isEmpty()) {
                fileName = displayName + ".java";
            }
        }
        chooser.setSelectedFile(new File(fileName));
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (FileWriter fw = new FileWriter(file)) {
                fw.write(area.getText());
                JOptionPane.showMessageDialog(this, "Code enregistré dans : " + file.getAbsolutePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Erreur lors de l'enregistrement : " + ex.getMessage());
            }
        }
    }

    private void resetDecompiledCode() {
        if (lastDecompiledCode != null && tabbedPane.getSelectedIndex() != -1) {
            RSyntaxTextArea area = getCurrentCodeArea();
            if (area != null) {
                area.setText(lastDecompiledCode);
                area.setCaretPosition(0);
            }
        }
    }

    private RSyntaxTextArea getCurrentCodeArea() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx == -1) return null;
        java.awt.Component comp = tabbedPane.getComponentAt(idx);
        if (comp instanceof RTextScrollPane) {
            RTextScrollPane scroll = (RTextScrollPane) comp;
            java.awt.Component view = scroll.getViewport().getView();
            if (view instanceof RSyntaxTextArea) {
                return (RSyntaxTextArea) view;
            }
        }
        return null;
    }

    // Méthode déléguée à SearchManager
    private void searchInCode() {
        searchManager.searchInCode(getCurrentCodeArea(), searchField.getText());
    }

    private byte[] readAllBytes(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return readAllBytes(fis); // <-- Utilise la méthode utilitaire
        } catch (IOException e) {
            return new byte[0];
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

    // Ajoute une méthode utilitaire pour décompiler une classe en String sans affecter l'affichage
    // Méthode déléguée à DecompilerManager
    private String decompileClassToString(String className, byte[] bytes) {
        return decompilerManager.decompileClassToString(className, bytes);
    }

    // Ajoute Undo/Redo (Ctrl+Z/Ctrl+Y) à un RSyntaxTextArea
    private void addUndoRedo(RSyntaxTextArea area) {
        UndoManager undoManager = new UndoManager();
        area.getDocument().addUndoableEditListener(undoManager);
        area.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("control Z"), "Undo");
        area.getActionMap().put("Undo", new javax.swing.AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                try { if (undoManager.canUndo()) undoManager.undo(); } catch (CannotUndoException ex) {}
            }
        });
        area.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("control Y"), "Redo");
        area.getActionMap().put("Redo", new javax.swing.AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                try { if (undoManager.canRedo()) undoManager.redo(); } catch (CannotRedoException ex) {}
            }
        });
    }

    // Ajoute un MouseListener et MouseMotionListener à chaque RSyntaxTextArea pour Ctrl+clic, menu contextuel et surlignage/tooltip
    private void addCtrlClickListener(RSyntaxTextArea area) {
        // Variables pour la sélection multiple
        final String[] multiSelectWord = {null};
        final List<Integer> multiSelectPositions = new ArrayList<>();
        final List<Object> multiSelectHighlights = new ArrayList<>();
        
        area.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    // Double-clic pour sélection multiple
                    int pos = area.viewToModel(e.getPoint());
                    try {
                        String text = area.getText();
                        // Trouver le mot exact sous le curseur
                        int start = pos, end = pos;
                        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
                        while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) end++;
                        if (start == end) return; // rien sous le curseur
                        
                        String word = text.substring(start, end);
                        
                        // Nettoyer les anciens surlignages multiples
                        for (Object highlight : multiSelectHighlights) {
                            try {
                                area.getHighlighter().removeHighlight(highlight);
                            } catch (Exception ex) {
                                // Ignore
                            }
                        }
                        multiSelectHighlights.clear();
                        multiSelectPositions.clear();
                        
                        // Trouver toutes les occurrences du mot
                        int searchPos = 0;
                        while (searchPos < text.length()) {
                            int found = text.indexOf(word, searchPos);
                            if (found == -1) break;
                            
                            // Vérifier que c'est bien un identifiant délimité
                            boolean isDelimited = true;
                            if (found > 0 && Character.isJavaIdentifierPart(text.charAt(found - 1))) {
                                isDelimited = false;
                            }
                            if (found + word.length() < text.length() && Character.isJavaIdentifierPart(text.charAt(found + word.length()))) {
                                isDelimited = false;
                            }
                            
                            if (isDelimited) {
                                multiSelectPositions.add(found);
                            }
                            searchPos = found + 1;
                        }
                        
                        if (multiSelectPositions.size() > 1) {
                            // Surligner toutes les occurrences
                            javax.swing.text.Highlighter.HighlightPainter painter = 
                                new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(java.awt.Color.CYAN);
                            
                            for (Integer position : multiSelectPositions) {
                                try {
                                    Object highlight = area.getHighlighter().addHighlight(position, position + word.length(), painter);
                                    multiSelectHighlights.add(highlight);
                                } catch (Exception ex) {
                                    System.out.println("DEBUG: Erreur surlignage: " + ex.getMessage());
                                }
                            }
                            
                            multiSelectWord[0] = word;
                            
                            // Sélectionner la première occurrence
                            area.setCaretPosition(multiSelectPositions.get(0));
                            area.select(multiSelectPositions.get(0), multiSelectPositions.get(0) + word.length());
                            
                            System.out.println("DEBUG: Sélection multiple activée pour '" + word + "' (" + multiSelectPositions.size() + " occurrences)");
                        }
                        
                    } catch (Exception ex) {
                        System.out.println("DEBUG: Erreur sélection multiple: " + ex.getMessage());
                    }
                } else if (e.isControlDown() && e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    int pos = area.viewToModel(e.getPoint());
                    try {
                        String text = area.getText();
                        // Trouver le mot exact sous le curseur
                        int start = pos, end = pos;
                        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
                        while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) end++;
                        if (start == end) return; // rien sous le curseur
                        String word = text.substring(start, end);
                        
                        // Analyser le contexte pour détecter les références comme Config.IiIiiiiiIiiIi
                        String beforeWord = text.substring(Math.max(0, start - 50), start);
                        String afterWord = text.substring(end, Math.min(text.length(), end + 50));
                        
                        // Chercher un pattern comme "Config.IiIiiiiiIiiIi"
                        java.util.regex.Pattern refPattern = java.util.regex.Pattern.compile("([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*\\.\\s*" + java.util.regex.Pattern.quote(word) + "\\b");
                        java.util.regex.Matcher refMatcher = refPattern.matcher(beforeWord + word + afterWord);
                        
                        if (refMatcher.find()) {
                            String className = refMatcher.group(1);
                            String memberName = word;
                            
                            System.out.println("DEBUG: Navigation vers " + className + "." + memberName);
                            
                            // Chercher la classe correspondante
                            String classToOpen = null;
                            for (String classKey : classBytes.keySet()) {
                                String simple = classKey.endsWith(".class") ? classKey.substring(0, classKey.length() - 6) : classKey;
                                simple = simple.substring(simple.lastIndexOf('/') + 1);
                                if (className.equals(simple)) {
                                    classToOpen = classKey;
                                    break;
                                }
                            }
                            
                            if (classToOpen != null) {
                                // Ouvrir la classe et naviguer vers le membre
                                navigateToClassAndMember(classToOpen, memberName);
                                return;
                            }
                        }
                        
                        // Vérifier si le mot est suivi de '.class' dans le code
                        boolean isDotClass = false;
                        int after = end;
                        while (after < text.length() && Character.isWhitespace(text.charAt(after))) after++;
                        if (after + 6 <= text.length() && text.substring(after, after + 6).equals(".class")) {
                            isDotClass = true;
                        }
                        
                        // Chercher la classe correspondante
                        String classToOpen = null;
                        for (String className : classBytes.keySet()) {
                            String simple = className.endsWith(".class") ? className.substring(0, className.length() - 6) : className;
                            simple = simple.substring(simple.lastIndexOf('/') + 1);
                            if (word.equals(simple)) {
                                classToOpen = className;
                                break;
                            }
                        }
                        
                        // Si c'est un .class explicite, n'ouvrir que si la classe existe
                        if (isDotClass && classToOpen != null) {
                            decompileClassBytes(classToOpen, classBytes.get(classToOpen));
                            return;
                        }
                        
                        // Si ce n'est pas un .class explicite, n'ouvrir que si c'est une vraie classe connue
                        if (!isDotClass && classToOpen != null) {
                            decompileClassBytes(classToOpen, classBytes.get(classToOpen));
                            return;
                        }
                        
                        // Sinon, ne rien faire (pas de navigation sur variable ou méthode)
                    } catch (Exception ex) {
                        System.out.println("DEBUG: Erreur navigation Ctrl+clic: " + ex.getMessage());
                    }
                }
            }
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    int pos = area.viewToModel(e.getPoint());
                    try {
                        String text = area.getText();
                        // Trouver le mot exact sous le curseur
                        int start = pos, end = pos;
                        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
                        while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) end++;
                        if (start == end) return;
                        String word = text.substring(start, end);
                        JPopupMenu popup = new JPopupMenu();
                        boolean isClass = false;
                        boolean isMethod = false;
                        boolean isVar = false;
                        boolean isEnumConst = false;
                        // Vérifie si c'est une classe connue
                        String foundClass = null;
                        for (String className : classBytes.keySet()) {
                            String simple = className.endsWith(".class") ? className.substring(0, className.length() - 6) : className;
                            simple = simple.substring(simple.lastIndexOf('/') + 1);
                            if (word.equals(simple)) {
                                foundClass = className;
                                isClass = true;
                                break;
                            }
                        }
                        
                        // Analyse contextuelle plus précise pour déterminer le type d'identifiant
                        List<IdentifierContext> contexts = findAllIdentifiers(text, word);
                        if (!contexts.isEmpty()) {
                            IdentifierContext context = contexts.get(0); // Prend le premier contexte trouvé
                            
                            // Vérifier d'abord si c'est un nom de variable dans une déclaration
                            if (isVariableNameInDeclaration(context)) {
                                isVar = true;
                                isClass = false;
                                isMethod = false;
                            }
                            // Vérifier si c'est un type de variable dans une déclaration
                            else if (isVariableTypeInDeclaration(context)) {
                                isClass = true;
                                isVar = false;
                                isMethod = false;
                            }
                            // Sinon, utiliser la détection normale
                            else {
                                isClass = isClassInContext(context);
                                isMethod = isMethodInContext(context);
                                isVar = isVariableInContext(context);
                                
                                // Si c'est détecté comme variable, vérifie qu'elle est bien déclarée dans cette classe
                                if (isVar) {
                                    String currentClassName = extractClassNameFromCode(text);
                                    VariableDeclarationContext varDecl = findVariableDeclaration(text, word, currentClassName);
                                    if (varDecl == null) {
                                        // Si pas de déclaration trouvée, ce n'est peut-être pas une variable de cette classe
                                        isVar = false;
                                    }
                                }
                            }
                        }
                        
                        // Si c'est une classe connue mais pas détectée comme classe dans le contexte, on la considère quand même comme classe
                        if (foundClass != null && !isClass && !isMethod && !isVar) {
                            isClass = true;
                        }
                        // Vérifie le contexte autour du mot (avant et après)
                        String before = text.substring(Math.max(0, start - 20), start);
                        String after = text.substring(end, Math.min(text.length(), end + 20));
                        // Si précédé de 'new', 'class', 'extends', 'implements', 'import', '(', '[', '<', '>', '=', ':' etc. on privilégie le type
                        if (before.matches(".*(new\\s+|class\\s+|extends\\s+|implements\\s+|import\\s+|\\(|\\[|<|>|=|:|,|\\n|\\r)\\s*$")) {
                            isClass = isClass;
                        }
                        // Si suivi de '(' → probablement une méthode
                        if (after.trim().startsWith("(")) {
                            isMethod = true;
                        }
                        // Si précédé de type ou mot-clé ou '=' ou ',' ou '(' ou '[' ou '<' ou '>' ou ':' ou fin de ligne, probablement une variable
                        if (before.matches(".*(int|float|double|String|boolean|char|byte|short|long|final|private|protected|public|static|=|,|\\(|\\[|<|>|:|\\n|\\r)\\s*$")) {
                            isVar = true;
                        }
                        // Vérifie dans le code si c'est une déclaration de méthode
                        RSyntaxTextArea area2 = getCurrentCodeArea();
                        if (area2 != null) {
                            String code2 = area2.getText();
                            java.util.regex.Pattern pMeth = java.util.regex.Pattern.compile("(public|private|protected|static|final|synchronized|native|abstract|strictfp)?\\s*[\\w<>\\[\\]]+\\s+" + java.util.regex.Pattern.quote(word) + "\\s*\\(");
                            java.util.regex.Matcher mMeth = pMeth.matcher(code2);
                            if (mMeth.find()) isMethod = true;
                        }
                        // Détection robuste des constantes d'enum (toutes, même après des champs ou méthodes)
                        int enumStart = text.lastIndexOf("enum ", start);
                        if (enumStart != -1) {
                            int braceOpen = text.indexOf('{', enumStart);
                            int braceClose = text.indexOf('}', braceOpen);
                            if (braceOpen != -1 && braceOpen < start && braceClose != -1) {
                                String enumBlock = text.substring(braceOpen + 1, braceClose);
                                String[] lines = enumBlock.split("\n");
                                for (String lineEnum : lines) {
                                    String trimmed = lineEnum.trim();
                                    // Extraire les identifiants séparés par virgule
                                    String[] candidates = trimmed.split(",");
                                    for (String candidate : candidates) {
                                        String candidateName = candidate.trim();
                                        if (candidateName.isEmpty()) continue;
                                        // On prend le nom avant le premier '(' ou espace ou ';'
                                        int parenIdx = candidateName.indexOf('(');
                                        int spaceIdx = candidateName.indexOf(' ');
                                        int semiIdx = candidateName.indexOf(';');
                                        int cutIdx = candidateName.length();
                                        if (parenIdx != -1 && parenIdx < cutIdx) cutIdx = parenIdx;
                                        if (spaceIdx != -1 && spaceIdx < cutIdx) cutIdx = spaceIdx;
                                        if (semiIdx != -1 && semiIdx < cutIdx) cutIdx = semiIdx;
                                        candidateName = candidateName.substring(0, cutIdx);
                                        // On considère comme constante d'enum si la ligne commence par un identifiant suivi de '(' ou ',' ou ';'
                                        if (!candidateName.isEmpty() && word.equals(candidateName)) {
                                            isEnumConst = true;
                                            break;
                                        }
                                    }
                                    if (isEnumConst) break;
                                }
                            }
                        }
                        // Si la ligne commence par le mot et est suivie de '(', ',' ou ';', c'est probablement une constante d'enum
                        if ((before.startsWith(word + "(") || before.startsWith(word + ",") || before.startsWith(word + ";")) && after.trim().startsWith("(")) {
                            isEnumConst = true;
                        }
                        // Si la ligne commence par le mot seul (ex: IIIiIiiiiIIiI,), c'est aussi une constante d'enum
                        if (before.equals(word + ",") || before.equals(word + ";")) {
                            isEnumConst = true;
                        }
                        // Si le mot est à la fois une classe et une variable/méthode, proposer les deux options
                        if (isClass) {
                            JMenuItem renameClass = new JMenuItem("Renommer la classe partout...");
                            String finalFound = foundClass;
                            renameClass.addActionListener(ev -> {
                                String newName = JOptionPane.showInputDialog(ProcyonAdvancedGUI.this, "Nouveau nom de classe :", word);
                                if (newName != null && !newName.trim().isEmpty() && !newName.equals(word)) {
                                    renameClassEverywhereAndUpdateKeys(finalFound, word, newName);
                                }
                            });
                            popup.add(renameClass);
                        }
                        if (isMethod) {
                            JMenuItem renameMethod = new JMenuItem("Renommer la méthode partout...");
                            renameMethod.addActionListener(ev -> {
                                String newName = JOptionPane.showInputDialog(ProcyonAdvancedGUI.this, "Nouveau nom de méthode :", word);
                                if (newName != null && !newName.trim().isEmpty() && !newName.equals(word)) {
                                    // Renommage intelligent avec analyse contextuelle
                                    for (String classKey : modifiedCode.keySet()) {
                                        String code = modifiedCode.get(classKey);
                                        if (code == null) continue;
                                        
                                        // Trouve tous les contextes de cette méthode
                                        List<IdentifierContext> methodContexts = findAllIdentifiers(code, word);
                                        String newCode = code;
                                        
                                        // Applique le renommage seulement aux contextes de méthodes
                                        for (int i = methodContexts.size() - 1; i >= 0; i--) {
                                            IdentifierContext context = methodContexts.get(i);
                                            if (isMethodInContext(context)) {
                                                newCode = renameIdentifierInContext(newCode, context, newName);
                                            }
                                        }
                                        
                                        modifiedCode.put(classKey, newCode);
                                        if (openTabs.containsKey(classKey)) {
                                            openTabs.get(classKey).setText(newCode);
                                        }
                                    }
                                    
                                    // Appliquer aussi sur les classes non encore modifiées
                                    for (String classKey : classBytes.keySet()) {
                                        if (!modifiedCode.containsKey(classKey)) {
                                            String code = decompileClassToString(classKey, classBytes.get(classKey));
                                            List<IdentifierContext> methodContexts2 = findAllIdentifiers(code, word);
                                            String newCode = code;
                                            
                                            for (int i = methodContexts2.size() - 1; i >= 0; i--) {
                                                IdentifierContext context = methodContexts2.get(i);
                                                if (isMethodInContext(context)) {
                                                    newCode = renameIdentifierInContext(newCode, context, newName);
                                                }
                                            }
                                            
                                            modifiedCode.put(classKey, newCode);
                                        }
                                    }
                                    JOptionPane.showMessageDialog(ProcyonAdvancedGUI.this, "Méthode renommée partout !");
                                }
                            });
                            popup.add(renameMethod);
                        }
                        if (!isMethod && isVar) {
                            JMenuItem renameVar = new JMenuItem("Renommer la variable partout...");
                            renameVar.addActionListener(ev -> {
                                String newName = JOptionPane.showInputDialog(ProcyonAdvancedGUI.this, "Nouveau nom de variable :", word);
                                if (newName != null && !newName.trim().isEmpty() && !newName.equals(word)) {
                                    // Trouve la classe courante et sa déclaration de variable
                                    String currentClassName = null;
                                    String currentPackageName = null;
                                    VariableDeclarationContext targetDeclaration = null;
                                    
                                    // Cherche dans la classe courante pour trouver la déclaration
                                    for (String classKey : modifiedCode.keySet()) {
                                        String code = modifiedCode.get(classKey);
                                        if (code == null) continue;
                                        
                                        currentClassName = extractClassNameFromCode(code);
                                        currentPackageName = extractPackageNameFromCode(code);
                                        
                                        if (currentClassName != null) {
                                            targetDeclaration = findVariableDeclaration(code, word, currentClassName);
                                            if (targetDeclaration != null) {
                                                break; // Trouvé la déclaration
                                            }
                                        }
                                    }
                                    
                                    // Si pas trouvé dans les classes modifiées, cherche dans toutes les classes
                                    if (targetDeclaration == null) {
                                        for (String classKey : classBytes.keySet()) {
                                            if (modifiedCode.containsKey(classKey)) continue; // Déjà vérifié
                                            
                                            String code = decompileClassToString(classKey, classBytes.get(classKey));
                                            currentClassName = extractClassNameFromCode(code);
                                            currentPackageName = extractPackageNameFromCode(code);
                                            
                                            if (currentClassName != null) {
                                                targetDeclaration = findVariableDeclaration(code, word, currentClassName);
                                                if (targetDeclaration != null) {
                                                    break; // Trouvé la déclaration
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Trouve toutes les classes qui seront affectées
                                    Set<String> affectedClasses = findClassesWithVariableUsage(word, targetDeclaration);
                                    
                                    // Demande confirmation avant le renommage
                                    if (!confirmVariableRename(word, newName, targetDeclaration, affectedClasses)) {
                                        return; // Annule le renommage
                                    }
                                    
                                    // Renommage intelligent avec vérification de déclaration
                                    for (String classKey : modifiedCode.keySet()) {
                                        String code = modifiedCode.get(classKey);
                                        if (code == null) continue;
                                        
                                        String classClassName = extractClassNameFromCode(code);
                                        String classPackageName = extractPackageNameFromCode(code);
                                        
                                        // Trouve tous les contextes de cette variable
                                        List<IdentifierContext> varContexts = findAllIdentifiers(code, word);
                                        String newCode = code;
                                        
                                        // Applique le renommage seulement aux contextes de variables qui correspondent à la déclaration
                                        for (int i = varContexts.size() - 1; i >= 0; i--) {
                                            IdentifierContext context = varContexts.get(i);
                                            if (isVariableInContext(context) && 
                                                isVariableFromSameDeclaration(context, targetDeclaration, classClassName, classPackageName)) {
                                                newCode = renameIdentifierInContext(newCode, context, newName);
                                            }
                                        }
                                        
                                        modifiedCode.put(classKey, newCode);
                                        if (openTabs.containsKey(classKey)) {
                                            openTabs.get(classKey).setText(newCode);
                                        }
                                    }
                                    
                                    // Appliquer aussi sur les classes non encore modifiées
                                    for (String classKey : classBytes.keySet()) {
                                        if (!modifiedCode.containsKey(classKey)) {
                                            String code = decompileClassToString(classKey, classBytes.get(classKey));
                                            String classClassName = extractClassNameFromCode(code);
                                            String classPackageName = extractPackageNameFromCode(code);
                                            
                                            List<IdentifierContext> varContexts2 = findAllIdentifiers(code, word);
                                            String newCode = code;
                                            
                                            for (int i = varContexts2.size() - 1; i >= 0; i--) {
                                                IdentifierContext context = varContexts2.get(i);
                                                if (isVariableInContext(context) && 
                                                    isVariableFromSameDeclaration(context, targetDeclaration, classClassName, classPackageName)) {
                                                    newCode = renameIdentifierInContext(newCode, context, newName);
                                                }
                                            }
                                            
                                            modifiedCode.put(classKey, newCode);
                                        }
                                    }
                                    
                                    if (targetDeclaration != null) {
                                        JOptionPane.showMessageDialog(ProcyonAdvancedGUI.this, 
                                            "Variable renommée partout !\nDéclaration trouvée dans " + 
                                            targetDeclaration.className + " (ligne " + targetDeclaration.declarationLine + ")");
                                    } else {
                                        JOptionPane.showMessageDialog(ProcyonAdvancedGUI.this, 
                                            "Variable renommée partout !\n(Aucune déclaration trouvée - renommage basé sur le contexte)");
                                    }
                                }
                            });
                            popup.add(renameVar);
                            
                            // Ajouter l'option de renommage local
                            JMenuItem renameVarLocal = new JMenuItem("Renommer la variable localement...");
                            renameVarLocal.addActionListener(ev -> {
                                String newName = JOptionPane.showInputDialog(ProcyonAdvancedGUI.this, "Nouveau nom de variable (local) :", word);
                                if (newName != null && !newName.trim().isEmpty() && !newName.equals(word)) {
                                    // Renommage local dans la classe courante seulement
                                    RSyntaxTextArea currentArea = getCurrentCodeArea();
                                    if (currentArea != null) {
                                        String code = currentArea.getText();
                                        String newCode = code;
                                        
                                        // Trouve tous les contextes de cette variable dans le code courant
                                        List<IdentifierContext> varContexts = findAllIdentifiers(code, word);
                                        
                                        // Applique le renommage seulement aux contextes de variables
                                        for (int i = varContexts.size() - 1; i >= 0; i--) {
                                            IdentifierContext context = varContexts.get(i);
                                            if (isVariableInContext(context)) {
                                                newCode = renameIdentifierInContext(newCode, context, newName);
                                            }
                                        }
                                        
                                        // Met à jour l'onglet courant
                                        currentArea.setText(newCode);
                                        
                                        // Met à jour le code dans modifiedCode si c'est une classe chargée
                                        for (String classKey : modifiedCode.keySet()) {
                                            if (openTabs.containsKey(classKey) && openTabs.get(classKey) == currentArea) {
                                                modifiedCode.put(classKey, newCode);
                                                break;
                                            }
                                        }
                                        
                                        JOptionPane.showMessageDialog(ProcyonAdvancedGUI.this, 
                                            "Variable renommée localement dans cette classe !");
                                    }
                                }
                            });
                            popup.add(renameVarLocal);
                        }
                        if (isEnumConst) {
                            JMenuItem renameEnum = new JMenuItem("Renommer la constante d'enum partout...");
                            renameEnum.addActionListener(ev -> {
                                String newName = JOptionPane.showInputDialog(ProcyonAdvancedGUI.this, "Nouveau nom de constante d'enum :", word);
                                if (newName != null && !newName.trim().isEmpty() && !newName.equals(word)) {
                                    for (String classKey : classBytes.keySet()) {
                                        String code = modifiedCode.containsKey(classKey) ? modifiedCode.get(classKey) : decompileClassToString(classKey, classBytes.get(classKey));
                                        // 1. Remplacer les usages LuckyBlock.IIIIiIiIII -> LuckyBlock.TEST
                                        code = code.replaceAll("(\\b[A-Za-z0-9_]+\\s*\\.\\s*)" + java.util.regex.Pattern.quote(word) + "\\b", "$1" + newName);
                                        // 2. Si c'est le fichier de l'enum, remplacer la déclaration et les usages directs dans le bloc enum
                                        if (code.contains("enum ")) {
                                            int enumIdx = code.indexOf("enum ");
                                            int braceOpen = code.indexOf('{', enumIdx);
                                            int braceClose = code.indexOf('}', braceOpen);
                                            if (braceOpen != -1 && braceClose != -1) {
                                                String beforeEnum = code.substring(0, braceOpen + 1);
                                                String enumBlock = code.substring(braceOpen + 1, braceClose);
                                                String afterEnum = code.substring(braceClose);
                                                // Remplacer la déclaration de la constante (début de ligne)
                                                enumBlock = enumBlock.replaceAll("(^|\\n)" + java.util.regex.Pattern.quote(word) + "([\\t ]*[\\(,;])", "$1" + newName + "$2");
                                                // Remplacer les usages directs dans le bloc enum
                                                enumBlock = enumBlock.replaceAll("(?<![A-Za-z0-9_\\.])" + java.util.regex.Pattern.quote(word) + "(?![A-Za-z0-9_])", newName);
                                                code = beforeEnum + enumBlock + afterEnum;
                                            }
                                        }
                                        modifiedCode.put(classKey, code);
                                        if (openTabs.containsKey(classKey)) {
                                            openTabs.get(classKey).setText(code);
                                        }
                                    }
                                    JOptionPane.showMessageDialog(ProcyonAdvancedGUI.this, "Constante d'enum renommée partout !");
                                }
                            });
                            popup.add(renameEnum);
                        }
                        if (popup.getComponentCount() > 0) {
                            popup.show(area, e.getX(), e.getY());
                        }
                    } catch (Exception ex) {
                        // Ignore
                    }
                }
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                mousePressed(e);
            }
        });
        // Surlignage et tooltip sur Ctrl+hover
        area.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            int lastStart = -1, lastEnd = -1;
            javax.swing.text.Highlighter.HighlightPainter painter = new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(java.awt.Color.BLUE);
            Object lastTag = null;
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                if (e.isControlDown()) {
                    int pos = area.viewToModel(e.getPoint());
                    try {
                        String text = area.getText();
                        // Trouver le mot exact sous le curseur
                        int start = pos, end = pos;
                        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
                        while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) end++;
                        if (start == end) {
                            area.setCursor(java.awt.Cursor.getDefaultCursor());
                            area.setToolTipText(null);
                            if (lastTag != null) { area.getHighlighter().removeHighlight(lastTag); lastTag = null; }
                            return;
                        }
                        String word = text.substring(start, end);
                        // Vérifier si c'est une classe connue
                        String found = null;
                        String fullPath = null;
                        for (String className : classBytes.keySet()) {
                            String simple = className.endsWith(".class") ? className.substring(0, className.length() - 6) : className;
                            simple = simple.substring(simple.lastIndexOf('/') + 1);
                            if (word.equals(simple)) {
                                found = className;
                                fullPath = className.replace("/", ".").replace(".class", "");
                                break;
                            }
                        }
                        if (found != null) {
                            area.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                            area.setToolTipText(fullPath);
                            if (lastTag != null) area.getHighlighter().removeHighlight(lastTag);
                            lastTag = area.getHighlighter().addHighlight(start, end, painter);
                            lastStart = start; lastEnd = end;
                        } else {
                            area.setCursor(java.awt.Cursor.getDefaultCursor());
                            area.setToolTipText(null);
                            if (lastTag != null) { area.getHighlighter().removeHighlight(lastTag); lastTag = null; }
                        }
                    } catch (Exception ex) {
                        area.setCursor(java.awt.Cursor.getDefaultCursor());
                        area.setToolTipText(null);
                        if (lastTag != null) { area.getHighlighter().removeHighlight(lastTag); lastTag = null; }
                    }
                } else {
                    area.setCursor(java.awt.Cursor.getDefaultCursor());
                    area.setToolTipText(null);
                    if (lastTag != null) { area.getHighlighter().removeHighlight(lastTag); lastTag = null; }
                }
            }
        });
        
        // Ajouter un DocumentListener pour la modification simultanée
        area.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                handleMultiSelectChange();
            }
            
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                handleMultiSelectChange();
            }
            
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                handleMultiSelectChange();
            }
            
            private void handleMultiSelectChange() {
                if (multiSelectWord[0] != null && !multiSelectPositions.isEmpty()) {
                    try {
                        String selectedText = area.getSelectedText();
                        if (selectedText != null && !selectedText.equals(multiSelectWord[0])) {
                            // Le texte sélectionné a changé, appliquer aux autres occurrences
                            String text = area.getText();
                            
                            // Appliquer le changement à toutes les autres occurrences
                            for (int i = multiSelectPositions.size() - 1; i >= 0; i--) {
                                int pos = multiSelectPositions.get(i);
                                if (pos < text.length() && pos + multiSelectWord[0].length() <= text.length()) {
                                    String textAtPos = text.substring(pos, pos + multiSelectWord[0].length());
                                    if (textAtPos.equals(multiSelectWord[0])) {
                                        // Remplacer cette occurrence
                                        area.getDocument().remove(pos, multiSelectWord[0].length());
                                        area.getDocument().insertString(pos, selectedText, null);
                                    }
                                }
                            }
                            
                            // Mettre à jour le mot et les positions
                            multiSelectWord[0] = selectedText;
                            
                            // Mettre à jour les surlignages
                            updateMultiSelectHighlights(selectedText);
                        }
                    } catch (Exception ex) {
                        System.out.println("DEBUG: Erreur modification simultanée: " + ex.getMessage());
                    }
                }
            }
            
            private void updateMultiSelectHighlights(String newWord) {
                try {
                    // Nettoyer les anciens surlignages
                    for (Object highlight : multiSelectHighlights) {
                        try {
                            area.getHighlighter().removeHighlight(highlight);
                        } catch (Exception ex) {
                            // Ignore
                        }
                    }
                    multiSelectHighlights.clear();
                    multiSelectPositions.clear();
                    
                    // Trouver les nouvelles positions
                    String text = area.getText();
                    int searchPos = 0;
                    while (searchPos < text.length()) {
                        int found = text.indexOf(newWord, searchPos);
                        if (found == -1) break;
                        
                        // Vérifier que c'est bien un identifiant délimité
                        boolean isDelimited = true;
                        if (found > 0 && Character.isJavaIdentifierPart(text.charAt(found - 1))) {
                            isDelimited = false;
                        }
                        if (found + newWord.length() < text.length() && Character.isJavaIdentifierPart(text.charAt(found + newWord.length()))) {
                            isDelimited = false;
                        }
                        
                        if (isDelimited) {
                            multiSelectPositions.add(found);
                        }
                        searchPos = found + 1;
                    }
                    
                    // Créer les nouveaux surlignages
                    if (multiSelectPositions.size() > 1) {
                        javax.swing.text.Highlighter.HighlightPainter painter = 
                            new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(java.awt.Color.CYAN);
                        
                        for (Integer position : multiSelectPositions) {
                            try {
                                Object highlight = area.getHighlighter().addHighlight(position, position + newWord.length(), painter);
                                multiSelectHighlights.add(highlight);
                            } catch (Exception ex) {
                                System.out.println("DEBUG: Erreur surlignage: " + ex.getMessage());
                            }
                        }
                    }
                    
                } catch (Exception ex) {
                    System.out.println("DEBUG: Erreur mise à jour surlignages: " + ex.getMessage());
                }
            }
        });
    }

    // Ajoute la coloration des occurrences du mot sélectionné dans un RSyntaxTextArea
    private void addHighlightOccurrences(RSyntaxTextArea area) {
        area.addCaretListener(e -> {
            String selected = area.getSelectedText();
            if (selected == null || selected.length() == 0) {
                area.getHighlighter().removeAllHighlights();
                return;
            }
            String text = area.getText();
            javax.swing.text.Highlighter.HighlightPainter painter = new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(java.awt.Color.YELLOW);
            int idx = 0;
            area.getHighlighter().removeAllHighlights();
            while ((idx = text.indexOf(selected, idx)) >= 0) {
                try {
                    area.getHighlighter().addHighlight(idx, idx + selected.length(), painter);
                } catch (Exception ex) {}
                idx += selected.length();
            }
        });
    }

    // Ajoute une croix de fermeture sur chaque onglet

    // Confirmation avant renommage global (classe ou méthode)
    private boolean confirmGlobalRename(Set<String> impacted, String oldName, String newName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Le renommage de '").append(oldName).append("' en '").append(newName).append("' va impacter les classes suivantes :\n\n");
        for (String k : impacted) {
            sb.append("- ").append(k.replace(".class", "")).append("\n");
        }
        sb.append("\nContinuer ?");
        int res = JOptionPane.showConfirmDialog(this, sb.toString(), "Confirmation du renommage global", JOptionPane.YES_NO_OPTION);
        return res == JOptionPane.YES_OPTION;
    }

    // Renommage intelligent de classe partout (extrait pour réutilisation)
    private void renameClassEverywhere(String classKey, String oldName, String newName) {
        renameManager.renameClassEverywhere(classKey, oldName, newName, lastClassName, lastDecompiledCode);
    }

    // Nouvelle méthode : renommage global + mise à jour des clés (déléguée à RenameManager)
    private void renameClassEverywhereAndUpdateKeys(String fullPath, String oldName, String newName) {
        renameManager.renameClassEverywhereAndUpdateKeys(fullPath, oldName, newName, lastClassName, lastDecompiledCode);
    }

    // Refactoring global : renommage de méthode partout (déléguée à RenameManager)
    private void renameMethodEverywhere(String oldName, String newName) {
        renameManager.renameMethodEverywhere(oldName, newName);
    }

    // Méthode déléguée à TreeManager
    private void updateClassNodeFullPath(DefaultMutableTreeNode node, String oldFull, String newFull, String newDisplay) {
        treeManager.updateClassNodeFullPath(node, oldFull, newFull, newDisplay);
    }

    // Met à jour le nom de l'onglet lors du renommage de la classe (déléguée à TabManager)
    private void updateTabTitle(String oldClassName, String newClassName) {
        tabManager.updateTabTitle(oldClassName, newClassName);
    }

    // Méthode déléguée à SearchManager
    private void performGlobalSearch() {
        searchManager.performGlobalSearch(globalSearchField.getText(), openTabs);
        resultToLine.putAll(searchManager.getResultToLine());
        resultToClass.putAll(searchManager.getResultToClass());
        updateGlobalSearchVisibility();
    }

    // Vérifie les conflits de noms dans le code courant
    private boolean hasNameConflict(String code, String name, String type) {
        String sep = "(^|[\\s\\(\\)\\[\\]\\{\\}<>,;.=+\\-*/%!?:&|^~])";
        if (type.equals("var")) {
            // Conflit si une déclaration de variable existe déjà
            return code.matches("(?s).*" + sep + name + sep + ".*=.*;");
        } else if (type.equals("method")) {
            // Conflit si une déclaration de méthode existe déjà
            return code.matches("(?s).*(public|private|protected|static|final|synchronized|native|abstract|strictfp)?\\s*[\\w<>\\[\\]]+\\s+" + java.util.regex.Pattern.quote(name) + "\\s*\\(.*");
        } else if (type.equals("class")) {
            // Conflit si une déclaration de classe existe déjà
            return code.matches("(?s).*(public\\s+class|class|public\\s+final\\s+class|final\\s+class)\\s+" + name + "\\b.*");
        }
        return false;
    }

    // Méthodes déléguées à ExportManager
    private void exportToJar() {
        exportManager.exportToJar();
    }

    private void exportToCompiledJar() {
        exportManager.exportToCompiledJar();
    }

    // Construit l'index des références pour Find Usages (version rapide avec ASM)
    // Méthode déléguée à ReferenceManager
    private void buildReferenceIndexFast() {
        referenceManager.buildReferenceIndexFast(classBytes);
    }

    // Méthodes déléguées à ThemeManager
    private void applyDarkTheme(RSyntaxTextArea area) {
        themeManager.applyDarkTheme(area);
    }
    
    private void applyLightTheme(RSyntaxTextArea area) {
        themeManager.applyLightTheme(area);
    }

    // Méthodes déléguées à ProjectManager
    private void saveProjectState() {
        projectManager.saveProjectState();
    }
    
    private void loadProjectState() {
        projectManager.loadProjectState(
            () -> treeManager.updateTreeWithPackages(new ArrayList<>(classBytes.keySet())),
            () -> autoRenameAllClasses()
        );
    }

    // Met à jour la visibilité de la JList des résultats de recherche globale
    private void updateGlobalSearchVisibility() {
        globalSearchResults.setVisible(globalSearchListModel.getSize() > 0);
    }

    private void refreshTree() {
        treeManager.updateTreeIncrementally(new ArrayList<>(classBytes.keySet()));
        updateObfuscatedClassesCount();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ProcyonAdvancedGUI().setVisible(true));
    }

    // Renommage automatique des classes d'items après chargement
    private void autoRenameAllClasses() {
        System.out.println("DEBUG: Début autoRenameAllClasses() - " + classBytes.size() + " classes à analyser");
        
        // Création de la popup de progression pour le renommage automatique
        JDialog renameProgressDialog = new JDialog(this, "Renommage automatique en cours", true);
        JProgressBar renameProgressBar = new JProgressBar(0, classBytes.size());
        renameProgressBar.setStringPainted(true);
        JLabel statusLabel = new JLabel("Analyse des classes...");
        renameProgressDialog.setLayout(new BorderLayout());
        renameProgressDialog.add(renameProgressBar, BorderLayout.CENTER);
        renameProgressDialog.add(statusLabel, BorderLayout.SOUTH);
        renameProgressDialog.setSize(500, 100);
        renameProgressDialog.setLocationRelativeTo(this);
        
        // Lancement du renommage dans un thread séparé
        new Thread(() -> {
            try {
        Map<String, String> renameMap = new HashMap<>();
        Map<String, String> oldToNewSimpleName = new HashMap<>();
                int processedCount = 0;
                
        for (String classKey : new ArrayList<>(classBytes.keySet())) {
                    processedCount++;
                    final int currentProgress = processedCount;
                    final String currentClass = classKey;
                    
                    // Mise à jour de la progression
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        renameProgressBar.setValue(currentProgress);
                        statusLabel.setText("Analyse de " + currentClass + " (" + currentProgress + "/" + classBytes.size() + ")");
                    });
                    
                    System.out.println("DEBUG: Analyse classe " + processedCount + "/" + classBytes.size() + " : " + classKey);
                    
                    if (isInPalamodPackage(classKey)) {
                        System.out.println("DEBUG: ✓ " + classKey + " déjà dans package palamod, ignoré");
                        continue; // Ignore déjà dans palamod/
                    }
                    
            String code = modifiedCode.containsKey(classKey)
                ? modifiedCode.get(classKey)
                : decompileClassToString(classKey, classBytes.get(classKey));
            String className = getSimpleClassName(classKey);

            String newKey = null;
            String newClassName = className;

                    System.out.println("DEBUG: Analyse du code pour " + className);

            if (extendsItemClass(code)) {
                        System.out.println("DEBUG: ✓ " + className + " étend Item");
                String itemName = extractItemNameFromCode(code);
                        if (itemName != null) {
                            newClassName = getNewItemClassName(itemName);
                            System.out.println("DEBUG: Nom d'item trouvé: " + itemName + " -> " + newClassName);
                        }
                
                // Vérifier si la classe est déjà bien nommée
                boolean alreadyWellNamed = isAlreadyWellNamed(className, newClassName);
                
                if (alreadyWellNamed) {
                    // Si déjà bien nommée, juste déplacer sans renommer
                    newKey = "fr/paladium/palamod/client/items/" + className + ".class";
                    System.out.println("DEBUG: Classe " + className + " déjà bien nommée, déplacement seulement");
                } else {
                    // Si pas bien nommée, renommer et déplacer
                    newKey = "fr/paladium/palamod/client/items/" + newClassName + ".class";
                    System.out.println("DEBUG: Classe " + className + " renommée en " + newClassName);
                }
            } else if (extendsBlockClass(code)) {
                        System.out.println("DEBUG: ✓ " + className + " étend Block");
                String blockName = extractBlockNameFromCode(code);
                        if (blockName != null) {
                            newClassName = getNewBlockClassName(blockName);
                            System.out.println("DEBUG: Nom de block trouvé: " + blockName + " -> " + newClassName);
                        }
                
                // Vérifier si la classe est déjà bien nommée
                boolean alreadyWellNamed = isAlreadyWellNamed(className, newClassName);
                
                if (alreadyWellNamed) {
                    // Si déjà bien nommée, juste déplacer sans renommer
                    newKey = "fr/paladium/palamod/client/blocks/" + className + ".class";
                    System.out.println("DEBUG: Classe " + className + " déjà bien nommée, déplacement seulement");
                } else {
                    // Si pas bien nommée, renommer et déplacer
                    newKey = "fr/paladium/palamod/client/blocks/" + newClassName + ".class";
                    System.out.println("DEBUG: Classe " + className + " renommée en " + newClassName);
                }
            } else if (isUIClass(code, className)) {
                        System.out.println("DEBUG: ✓ " + className + " détecté comme classe UI");
                newKey = "fr/paladium/palamod/client/ui/" + className + ".class";
            } else if (isCommandClass(code)) {
                        System.out.println("DEBUG: ✓ " + className + " détecté comme classe Command");
                newKey = "fr/paladium/palamod/client/commands/" + className + ".class";
            } else if (isTileEntityClass(code)) {
                        System.out.println("DEBUG: ✓ " + className + " détecté comme classe TileEntity");
                newKey = "fr/paladium/palamod/client/tileentity/" + className + ".class";
            } else if (isModelClass(code, className)) {
                        System.out.println("DEBUG: ✓ " + className + " détecté comme classe Model");
                newKey = "fr/paladium/palamod/client/models/" + className + ".class";
            } else if (isNetworkPacketClass(code)) {
                        System.out.println("DEBUG: ✓ " + className + " détecté comme classe NetworkPacket");
                // Remplace le package dans le code source décompilé
                String newCode = code;
                if (code.contains("package ")) {
                    newCode = code.replaceFirst("^package [^;]+;", "package fr.paladium.palamod.network.packet;");
                    System.out.println("DEBUG: Package modifié pour " + className + " -> fr.paladium.palamod.network.packet");
                } else {
                    // Si pas de package, ajouter le package
                    newCode = "package fr.paladium.palamod.network.packet;\n\n" + code;
                    System.out.println("DEBUG: Package ajouté pour " + className + " -> fr.paladium.palamod.network.packet");
                }
                modifiedCode.put(classKey, newCode);
                newKey = "fr/paladium/palamod/network/packet/" + className + ".class";
            } else if (extendsALuckyEvent(code)) {
                        System.out.println("DEBUG: ✓ " + className + " étend ALuckyEvent");
                String luckyEventName = extractLuckyEventNameFromCode(code);
                        if (luckyEventName != null) {
                            newClassName = luckyEventName;
                            System.out.println("DEBUG: Nom d'événement trouvé: " + luckyEventName);
                        }
                
                // Vérifier si la classe est déjà bien nommée
                boolean alreadyWellNamed = isAlreadyWellNamed(className, luckyEventName);
                
                // Remplace le package dans le code source décompilé
                String newCode = code;
                if (code.contains("package ")) {
                    newCode = code.replaceFirst("^package [^;]+;", "package fr.paladium.palamod.client.luckyevent;");
                    System.out.println("DEBUG: Package modifié pour " + className + " -> fr.paladium.palamod.client.luckyevent");
                } else {
                    // Si pas de package, ajouter le package
                    newCode = "package fr.paladium.palamod.client.luckyevent;\n\n" + code;
                    System.out.println("DEBUG: Package ajouté pour " + className + " -> fr.paladium.palamod.client.luckyevent");
                }
                
                modifiedCode.put(classKey, newCode);
                
                if (alreadyWellNamed) {
                    // Si déjà bien nommée, juste déplacer sans renommer
                    newKey = "fr/paladium/palamod/client/luckyevent/" + className + ".class";
                    System.out.println("DEBUG: Classe " + className + " déjà bien nommée, déplacement seulement");
                } else {
                    // Si pas bien nommée, renommer et déplacer
                    newKey = "fr/paladium/palamod/client/luckyevent/" + newClassName + ".class";
                    System.out.println("DEBUG: Classe " + className + " renommée en " + newClassName);
                }
                    } else {
                        System.out.println("DEBUG: ✗ " + className + " ne correspond à aucun type connu");
            }

            if (newKey != null && !classKey.equals(newKey)) {
                        System.out.println("DEBUG: Renommage prévu: " + classKey + " -> " + newKey);
                renameMap.put(classKey, newKey);
            }
            if (newKey != null) {
                oldToNewSimpleName.put(className, newClassName);
            }
        }
                
                System.out.println("DEBUG: " + renameMap.size() + " classes à renommer");
                
                // Mise à jour du statut pour la phase de renommage
                javax.swing.SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Application des renommages (" + renameMap.size() + " classes)...");
                });
                
        // 2. Appliquer les renommages et packages
                int renameCount = 0;
        for (Map.Entry<String, String> entry : renameMap.entrySet()) {
            String oldKey = entry.getKey();
            String newKey = entry.getValue();
                    renameCount++;
                    
                    final int currentRename = renameCount;
                    final String oldClass = oldKey;
                    final String newClass = newKey;
                    
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Renommage " + currentRename + "/" + renameMap.size() + ": " + 
                                          oldClass.substring(oldClass.lastIndexOf('/') + 1) + " -> " + 
                                          newClass.substring(newClass.lastIndexOf('/') + 1));
                    });
                    
                    System.out.println("DEBUG: Application du renommage: " + oldKey + " -> " + newKey);
                    
            classBytes.put(newKey, classBytes.remove(oldKey));
            if (modifiedCode.containsKey(oldKey)) modifiedCode.put(newKey, modifiedCode.remove(oldKey));
            if (openTabs.containsKey(oldKey)) openTabs.put(newKey, openTabs.remove(oldKey));
            if (classToDisplayName.containsKey(oldKey)) classToDisplayName.put(newKey, classToDisplayName.remove(oldKey));
        }
                
                // Mise à jour du statut pour la phase de mise à jour des références
                javax.swing.SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Mise à jour des références...");
                });
                
        // 3. Met à jour les références dans le code de toutes les classes
                System.out.println("DEBUG: Mise à jour des références dans " + modifiedCode.size() + " classes");
                int refUpdateCount = 0;
                int totalRefUpdates = modifiedCode.size();
                
        for (String k : modifiedCode.keySet()) {
                    refUpdateCount++;
                    final int currentRefUpdate = refUpdateCount;
                    
                    // Mise à jour de la progression pour les références
                    if (refUpdateCount % 10 == 0 || refUpdateCount == totalRefUpdates) { // Mise à jour tous les 10 ou à la fin
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Mise à jour des références (" + currentRefUpdate + "/" + totalRefUpdates + ")...");
                        });
                    }
                    
            String code = modifiedCode.get(k);
                    boolean codeChanged = false;
                    
            for (Map.Entry<String, String> entry : oldToNewSimpleName.entrySet()) {
                String oldSimple = entry.getKey();
                String newSimple = entry.getValue();
                        if (!oldSimple.equals(newSimple)) {
                            // Utiliser ClassRenamer pour renommer uniquement les références de classe
                            String newCode = classRenamer.renameClassReferencesOnly(code, oldSimple, newSimple);
                            if (!newCode.equals(code)) {
                                code = newCode;
                                codeChanged = true;
                            }
                        }
                    }
                    
                    if (codeChanged) {
            modifiedCode.put(k, code);
                        if (openTabs.containsKey(k)) {
                            openTabs.get(k).setText(code);
                        }
                    }
                    
                    // Petite pause pour éviter de bloquer l'interface
                    if (refUpdateCount % 50 == 0) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                
                // Mise à jour du statut pour la phase finale
                javax.swing.SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Mise à jour de l'arborescence...");
                });
                
        // 4. Rafraîchir l'arborescence
                System.out.println("DEBUG: Rafraîchissement de l'arborescence");
                // Utilise les nouvelles clés pour mettre à jour l'arborescence
                List<String> newClassNames = new ArrayList<>(classBytes.keySet());
                System.out.println("DEBUG: Nouvelles clés pour l'arborescence: " + newClassNames.size() + " classes");
                
                // Mise à jour de l'arborescence dans l'EDT pour éviter les blocages
                javax.swing.SwingUtilities.invokeLater(() -> {
                    try {
                        updateTreeIncrementally(newClassNames);
                        System.out.println("DEBUG: Arborescence mise à jour avec succès");
                    } catch (Exception e) {
                        System.out.println("DEBUG: Erreur lors de la mise à jour de l'arborescence: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                
                // Attendre que l'arborescence soit mise à jour
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                System.out.println("DEBUG: Fin autoRenameAllClasses() - " + renameMap.size() + " classes renommées");
                
                // Fermeture de la popup et affichage du résumé
                javax.swing.SwingUtilities.invokeLater(() -> {
                    renameProgressDialog.dispose();
                    if (renameMap.size() > 0) {
                        JOptionPane.showMessageDialog(this, 
                            "Renommage automatique terminé !\n\n" +
                            "Classes renommées : " + renameMap.size() + "\n" +
                            "Classes analysées : " + classBytes.size() + "\n\n" +
                            "L'arborescence a été mise à jour avec les nouveaux packages.",
                            "Renommage terminé", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, 
                            "Aucune classe à renommer trouvée.\n\n" +
                            "Classes analysées : " + classBytes.size() + "\n\n" +
                            "Toutes les classes sont déjà correctement organisées ou ne correspondent pas aux critères de renommage.",
                            "Aucun renommage nécessaire", JOptionPane.INFORMATION_MESSAGE);
                    }
                });
                
            } catch (Exception e) {
                System.out.println("DEBUG: Erreur dans autoRenameAllClasses: " + e.getMessage());
                e.printStackTrace();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    renameProgressDialog.dispose();
                    JOptionPane.showMessageDialog(this, 
                        "Erreur lors du renommage automatique :\n" + e.getMessage(),
                        "Erreur", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
        
        // Affichage de la popup
        renameProgressDialog.setVisible(true);
    }

    // Utilitaires pour le déplacement intelligent
    private boolean isUIClass(String code, String className) {
        return code.matches("(?s).*extends\\s+(Gui|GuiScreen|GuiContainer)\\b.*")
            || className.startsWith("Gui") || className.endsWith("Gui") || className.contains("UI");
    }
    private boolean isCommandClass(String code) {
        return code.matches("(?s).*extends\\s+(Command|CommandBase)\\b.*");
    }
    private boolean isTileEntityClass(String code) {
        return code.matches("(?s).*extends\\s+(TileEntity|TileEntitySpecialRenderer)\\b.*");
    }
    private boolean isModelClass(String code, String className) {
        return code.matches("(?s).*extends\\s+(Model[A-Za-z0-9_]*)\\b.*")
            || className.startsWith("Model");
    }
    private boolean isNetworkPacketClass(String code) {
        return code.matches("(?s).*class\\s+\\w+\\s+extends\\s+IMessage\\b.*")
            || code.matches("(?s).*class\\s+\\w+.*implements\\s+IMessageHandler\\b.*");
    }
    private boolean isInPalamodPackage(String classKey) {
        // Vérifie si la classe est déjà dans un package palamod organisé
        return classKey.contains("/palamod/client/items/") ||
               classKey.contains("/palamod/client/blocks/") ||
               classKey.contains("/palamod/client/ui/") ||
               classKey.contains("/palamod/client/commands/") ||
               classKey.contains("/palamod/client/tileentity/") ||
               classKey.contains("/palamod/client/models/") ||
               classKey.contains("/palamod/client/luckyevent/") ||
               classKey.contains("/palamod/client/handlers/") ||
               classKey.contains("/palamod/client/renderers/") ||
               classKey.contains("/palamod/client/utils/") ||
               classKey.contains("/palamod/client/managers/") ||
               classKey.contains("/palamod/network/packet/") ||
               // Vérifier aussi si la classe a déjà un nom descriptif (non obfusqué)
               !isObfuscatedClassName(getSimpleClassName(classKey));
    }

    // Vérifie si la classe étend explicitement Item
    private boolean extendsItemClass(String code) {
        // Patterns plus robustes pour détecter les classes Item
        String[] itemPatterns = {
            "extends\\s+Item\\b",
            "extends\\s+ItemFood\\b", 
            "extends\\s+ItemTool\\b",
            "extends\\s+ItemAxe\\b",
            "extends\\s+ItemSword\\b",
            "extends\\s+ItemPickaxe\\b",
            "extends\\s+ItemSpade\\b",
            "extends\\s+ItemHoe\\b",
            "extends\\s+ItemArmor\\b",
            "extends\\s+ItemBlock\\b"
        };
        
        for (String pattern : itemPatterns) {
            if (code.matches("(?s).*" + pattern + ".*")) {
                System.out.println("DEBUG: Pattern Item trouvé: " + pattern);
                return true;
            }
        }
        return false;
    }
    
    // Vérifie si la classe étend explicitement Block
    private boolean extendsBlockClass(String code) {
        // Patterns plus robustes pour détecter les classes Block
        String[] blockPatterns = {
            "extends\\s+Block\\b",
            "extends\\s+BlockContainer\\b",
            "extends\\s+BlockBush\\b", 
            "extends\\s+BlockCrops\\b",
            "extends\\s+BlockTrapDoor\\b",
            "extends\\s+BlockDoor\\b",
            "extends\\s+BlockFence\\b",
            "extends\\s+BlockWall\\b",
            "extends\\s+BlockStairs\\b",
            "extends\\s+BlockSlab\\b"
        };
        
        for (String pattern : blockPatterns) {
            if (code.matches("(?s).*" + pattern + ".*")) {
                System.out.println("DEBUG: Pattern Block trouvé: " + pattern);
                return true;
            }
        }
        return false;
    }
    // Utilitaires pour le renommage
    private String extractItemNameFromCode(String code) {
        // Patterns pour extraire le nom d'item
        String[] patterns = {
            "setTextureName\\(\"palamod:([^\"]+)\"\\)",
            "setUnlocalizedName\\(\"([^\"]+)\"\\)",
            "setRegistryName\\(\"palamod\",\\s*\"([^\"]+)\"\\)",
            "setRegistryName\\(\"([^\"]+)\"\\)",
            "super\\([^)]*\"([^\"]+)\"[^)]*\\)",
            "this\\.setUnlocalizedName\\(\"([^\"]+)\"\\)"
        };
        
        for (String pattern : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(code);
            if (m.find()) {
                String name = m.group(1);
                System.out.println("DEBUG: Nom d'item extrait avec pattern '" + pattern + "': " + name);
                // Nettoie le nom (enlève le chemin si présent)
            int slash = name.lastIndexOf('/');
                if (slash != -1 && slash < name.length() - 1) {
                    name = name.substring(slash + 1);
        }
            return name;
            }
        }
        return null;
    }
    
    private String extractBlockNameFromCode(String code) {
        // Patterns pour extraire le nom de block
        String[] patterns = {
            "setBlockTextureName\\(\"palamod:([^\"]+)\"\\)",
            "setBlockName\\(\"([^\":]+)\"\\)",
            "setBlockName\\(\"[^\":]+:([^\"]+)\"\\)",
            "setRegistryName\\(\"palamod\",\\s*\"([^\"]+)\"\\)",
            "setRegistryName\\(\"([^\"]+)\"\\)",
            "super\\([^)]*\"([^\"]+)\"[^)]*\\)",
            "this\\.setBlockName\\(\"([^\"]+)\"\\)"
        };
        
        for (String pattern : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(code);
            if (m.find()) {
                String name = m.group(1);
                System.out.println("DEBUG: Nom de block extrait avec pattern '" + pattern + "': " + name);
                // Nettoie le nom (enlève le chemin si présent)
            int slash = name.lastIndexOf('/');
                if (slash != -1 && slash < name.length() - 1) {
                    name = name.substring(slash + 1);
        }
            return name;
        }
        }
        return null;
    }
    private String getNewItemClassName(String itemName) {
        String[] parts = itemName.split("_");
        StringBuilder sb = new StringBuilder("Item");
        for (String part : parts) sb.append(capitalize(part));
        return sb.toString();
    }
    private String getNewBlockClassName(String blockName) {
        String[] parts = blockName.split("_");
        StringBuilder sb = new StringBuilder("Block");
        for (String part : parts) sb.append(capitalize(part));
        return sb.toString();
    }
    private String getSimpleClassName(String classKey) {
        String name = classKey.substring(classKey.lastIndexOf('/') + 1);
        return name.replace(".class", "");
    }
    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // Nouvelle méthode utilitaire pour analyser le contexte d'un identifiant dans le code
    private static class IdentifierContext {
        String identifier;
        int startPos;
        int endPos;
        String beforeContext;
        String afterContext;
        String fullContext;
        
        IdentifierContext(String identifier, int startPos, int endPos, String beforeContext, String afterContext, String fullContext) {
            this.identifier = identifier;
            this.startPos = startPos;
            this.endPos = endPos;
            this.beforeContext = beforeContext;
            this.afterContext = afterContext;
            this.fullContext = fullContext;
        }
    }
    
    // Trouve tous les identifiants dans le code avec leur contexte exact
    private List<IdentifierContext> findAllIdentifiers(String code, String targetIdentifier) {
        List<IdentifierContext> contexts = new ArrayList<>();
        int pos = 0;
        
        while (pos < code.length()) {
            // Trouve le prochain identifiant
            while (pos < code.length() && !Character.isJavaIdentifierStart(code.charAt(pos))) {
                pos++;
            }
            
            if (pos >= code.length()) break;
            
            int start = pos;
            while (pos < code.length() && Character.isJavaIdentifierPart(code.charAt(pos))) {
                pos++;
            }
            int end = pos;
            
            String identifier = code.substring(start, end);
            
            // Vérifie si c'est l'identifiant cible
            if (identifier.equals(targetIdentifier)) {
                // Détermine le contexte avant et après
                int contextStart = Math.max(0, start - 50);
                int contextEnd = Math.min(code.length(), end + 50);
                
                String beforeContext = code.substring(contextStart, start);
                String afterContext = code.substring(end, contextEnd);
                String fullContext = code.substring(contextStart, contextEnd);
                
                contexts.add(new IdentifierContext(identifier, start, end, beforeContext, afterContext, fullContext));
            }
        }
        
        return contexts;
    }
    
    // Vérifie si un identifiant dans un contexte donné est une classe
    private boolean isClassInContext(IdentifierContext context) {
        String before = context.beforeContext;
        String after = context.afterContext;
        
        // Patterns pour identifier une classe
        // 1. Déclaration de classe
        if (before.matches(".*(public\\s+)?(final\\s+)?(abstract\\s+)?class\\s+$")) {
            return true;
        }
        
        // 2. Héritage (extends)
        if (before.matches(".*extends\\s+$")) {
            return true;
        }
        
        // 3. Implémentation (implements)
        if (before.matches(".*implements\\s+$")) {
            return true;
        }
        
        // 4. Import statement
        if (before.matches(".*import\\s+[a-zA-Z0-9_\\.]*\\.$")) {
            return true;
        }
        
        // 5. Instanciation (new)
        if (before.matches(".*new\\s+$")) {
            return true;
        }
        
        // 6. Type de paramètre générique
        if (before.matches(".*<\\s*$") || before.matches(".*,\\s*$")) {
            return true;
        }
        
        // 7. Cast explicite
        if (before.matches(".*\\(\\s*$") && after.trim().startsWith(")")) {
            return true;
        }
        
        // 8. Type de retour de méthode
        if (before.matches(".*(public|private|protected|static|final|synchronized|native|abstract|strictfp)?\\s*[\\w<>\\[\\]]+\\s+\\w+\\s*\\([^)]*\\)\\s*\\{\\s*$")) {
            return true;
        }
        
        // 9. Type de variable de classe (pas de variable locale)
        if (before.matches(".*(public|private|protected|static|final|transient|volatile)\\s+[\\w<>\\[\\]]+\\s+$")) {
            // Vérifier que ce n'est pas une variable locale
            if (!after.trim().startsWith("=") && !after.trim().startsWith(";")) {
                return true;
            }
        }
        
        // 10. Type dans une déclaration de variable de classe
        if (before.matches(".*[\\w<>\\[\\]]+\\s+$") && !after.trim().startsWith("=")) {
            // Vérifier que ce n'est pas une variable locale
            if (!after.trim().startsWith(";")) {
                return true;
            }
        }
        
        // 11. Type de variable (ex: IIiIIiII iiIIiiII;)
        // Si c'est suivi d'un nom de variable, c'est un type de classe
        if (before.matches(".*[\\w<>\\[\\]]+\\s+$")) {
            // Vérifier si c'est suivi d'un nom de variable
            String afterTrimmed = after.trim();
            if (afterTrimmed.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]")) {
                return true; // C'est un type de classe
            }
        }
        
        // 12. Type de classe dans déclaration avec modificateurs
        // Ex: "public static final Logger " + "IiIiiiiiIiiIi" + ";"
        if (before.matches(".*(public|private|protected|static|final|transient|volatile)\\s+[\\w<>\\[\\]]+\\s+$")) {
            String afterTrimmed = after.trim();
            if (afterTrimmed.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]")) {
                return true; // C'est un type de classe
            }
        }
        
        // 13. Type de classe dans déclaration avec modificateurs multiples
        // Ex: "public static final Logger " + "IiIiiiiiIiiIi" + ";"
        if (before.matches(".*(public|private|protected|static|final|transient|volatile)\\s+(public|private|protected|static|final|transient|volatile)?\\s+(public|private|protected|static|final|transient|volatile)?\\s+[\\w<>\\[\\]]+\\s+$")) {
            String afterTrimmed = after.trim();
            if (afterTrimmed.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]")) {
                return true; // C'est un type de classe
            }
        }
        
        return false;
    }
    
    // Vérifie si un identifiant dans un contexte donné est une méthode
    private boolean isMethodInContext(IdentifierContext context) {
        String before = context.beforeContext;
        String after = context.afterContext;
        
        // Patterns pour identifier une méthode
        return after.trim().startsWith("(") ||
               before.matches(".*(public|private|protected|static|final|synchronized|native|abstract|strictfp)?\\s*[\\w<>\\[\\]]+\\s+$") ||
               before.matches(".*\\b(return|if|while|for|switch)\\s+$");
    }
    
    // Vérifie si un identifiant dans un contexte donné est une variable
    private boolean isVariableInContext(IdentifierContext context) {
        String before = context.beforeContext;
        String after = context.afterContext;
        
        // Patterns pour identifier une variable
        // 1. Déclaration de variable (type + nom)
        if (before.matches(".*(String|int|float|double|boolean|char|byte|short|long|\\w+\\[\\]|\\w+<.*>)\\s+$")) {
            return true;
        }
        
        // 2. Modificateurs de déclaration
        if (before.matches(".*(public|private|protected|static|final|transient|volatile)\\s+(String|int|float|double|boolean|char|byte|short|long|\\w+\\[\\]|\\w+<.*>)\\s+$")) {
            return true;
        }
        
        // 3. Nom de variable dans une déclaration (ex: IIiIIiII iiIIiiII;)
        if (before.matches(".*[\\w<>\\[\\]]+\\s+$")) {
            // Vérifier si c'est suivi d'un nom de variable
            String afterTrimmed = after.trim();
            if (afterTrimmed.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]")) {
                return true; // C'est le nom de la variable
            }
        }
        
        // 4. Assignation ou utilisation
        if (after.trim().startsWith("=") || after.trim().startsWith(";") || after.trim().startsWith(",") || 
            after.trim().startsWith(")") || after.trim().startsWith("]")) {
            return true;
        }
        
        // 5. Dans une expression (après un opérateur)
        if (before.matches(".*(=|\\+|\\-|\\*|/|%|\\||&|\\^|<<|>>|>>>)\\s*$")) {
            return true;
        }
        
        // 6. Dans une méthode (après un point)
        if (before.matches(".*\\.\\s*$")) {
            return true;
        }
        
        // 7. Dans un paramètre de méthode
        if (before.matches(".*\\(\\s*$") || before.matches(".*,\\s*$")) {
            return true;
        }
        
        // 8. Dans un return statement
        if (before.matches(".*return\\s+$")) {
            return true;
        }
        
        // 9. Dans une condition (if, while, for)
        if (before.matches(".*(if|while|for)\\s*\\(.*$")) {
            return true;
        }
        
        // 10. Dans un cast
        if (before.matches(".*\\(\\s*$") && after.trim().startsWith(")")) {
            return true;
        }
        
        // 11. Variable de boucle for
        if (before.matches(".*for\\s*\\([^)]*\\s+$")) {
            return true;
        }
        
        return false;
    }
    
    // Renomme un identifiant spécifique dans un contexte donné
    private String renameIdentifierInContext(String code, IdentifierContext context, String newName) {
        // Vérifie que l'identifiant est bien délimité par des caractères non-identifiants
        if (context.startPos > 0 && Character.isJavaIdentifierPart(code.charAt(context.startPos - 1))) {
            return code; // Pas un identifiant délimité
        }
        if (context.endPos < code.length() && Character.isJavaIdentifierPart(code.charAt(context.endPos))) {
            return code; // Pas un identifiant délimité
        }
        
        // Effectue le remplacement
        return code.substring(0, context.startPos) + newName + code.substring(context.endPos);
    }

    // Nouvelle méthode pour analyser le contexte de déclaration d'une variable
    private static class VariableDeclarationContext {
        String variableName;
        String className;
        String packageName;
        String fullContext;
        int declarationLine;
        
        VariableDeclarationContext(String variableName, String className, String packageName, String fullContext, int declarationLine) {
            this.variableName = variableName;
            this.className = className;
            this.packageName = packageName;
            this.fullContext = fullContext;
            this.declarationLine = declarationLine;
        }
    }
    
    // Trouve la déclaration d'une variable dans une classe
    private VariableDeclarationContext findVariableDeclaration(String code, String variableName, String currentClassName) {
        String[] lines = code.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            // Pattern pour détecter une déclaration de variable
            // Ex: String IiiIiIIiI; ou private String IiiIiIIiI = ...;
            if (line.matches(".*\\b" + java.util.regex.Pattern.quote(variableName) + "\\b.*[;=]")) {
                // Vérifie que c'est bien une déclaration (pas un usage)
                if (line.matches(".*(String|int|float|double|boolean|char|byte|short|long|\\w+\\[\\]|\\w+<.*>)\\s+" + java.util.regex.Pattern.quote(variableName) + "\\b.*")) {
                    // Extrait le nom de la classe depuis le code
                    String className = extractClassNameFromCode(code);
                    String packageName = extractPackageNameFromCode(code);
                    
                    return new VariableDeclarationContext(variableName, className, packageName, line, i + 1);
                }
            }
        }
        return null;
    }
    
    // Extrait le nom de la classe depuis le code
    private String extractClassNameFromCode(String code) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:public\\s+)?(?:final\\s+)?(?:abstract\\s+)?class\\s+(\\w+)");
        java.util.regex.Matcher matcher = pattern.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    // Extrait le nom du package depuis le code
    private String extractPackageNameFromCode(String code) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("package\\s+([\\w\\.]+);");
        java.util.regex.Matcher matcher = pattern.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    // Vérifie si une variable dans un contexte donné correspond à la déclaration cible
    private boolean isVariableFromSameDeclaration(IdentifierContext context, VariableDeclarationContext targetDeclaration, String currentClassName, String currentPackageName) {
        // Si on a trouvé une déclaration pour la variable cible
        if (targetDeclaration != null) {
            // Vérifie que la classe courante correspond à la classe de déclaration
            if (!currentClassName.equals(targetDeclaration.className)) {
                return false;
            }
            
            // Vérifie que le package correspond
            if (targetDeclaration.packageName != null && currentPackageName != null) {
                if (!currentPackageName.equals(targetDeclaration.packageName)) {
                    return false;
                }
            }
            
            // Vérifie le contexte autour de l'usage pour s'assurer que c'est bien la même variable
            String contextBefore = context.beforeContext;
            String contextAfter = context.afterContext;
            
            // Si c'est précédé par "this." ou le nom de la classe, c'est probablement la bonne variable
            if (contextBefore.matches(".*\\b(this|" + java.util.regex.Pattern.quote(targetDeclaration.className) + ")\\s*\\.\\s*$")) {
                return true;
            }
            
            // Si c'est dans un contexte de déclaration ou d'assignation, c'est probablement la bonne variable
            if (contextBefore.matches(".*\\b(String|int|float|double|boolean|char|byte|short|long|\\w+\\[\\]|\\w+<.*>)\\s+$")) {
                return true;
            }
            
            // Si c'est dans un contexte d'assignation
            if (contextAfter.trim().startsWith("=") || contextAfter.trim().startsWith(";")) {
                return true;
            }
        }
        
        // Si on n'a pas de déclaration cible, on considère que c'est la même variable
        // (cas où la variable est déclarée dans une classe parent ou interface)
        return true;
    }

    // Affiche un dialogue de confirmation pour le renommage de variable
    private boolean confirmVariableRename(String variableName, String newName, VariableDeclarationContext declaration, Set<String> affectedClasses) {
        StringBuilder message = new StringBuilder();
        message.append("Renommage de la variable '").append(variableName).append("' en '").append(newName).append("'\n\n");
        
        if (declaration != null) {
            message.append("Déclaration trouvée dans : ").append(declaration.className);
            if (declaration.packageName != null) {
                message.append(" (").append(declaration.packageName).append(")");
            }
            message.append(" (ligne ").append(declaration.declarationLine).append(")\n");
            message.append("Contexte : ").append(declaration.fullContext).append("\n\n");
        }
        
        message.append("Classes qui seront affectées :\n");
        for (String className : affectedClasses) {
            message.append("- ").append(className).append("\n");
        }
        
        message.append("\nContinuer le renommage ?");
        
        int result = JOptionPane.showConfirmDialog(this, message.toString(), 
            "Confirmation du renommage de variable", JOptionPane.YES_NO_OPTION);
        return result == JOptionPane.YES_OPTION;
    }
    
    // Trouve toutes les classes qui contiennent des usages de la variable
    private Set<String> findClassesWithVariableUsage(String variableName, VariableDeclarationContext declaration) {
        Set<String> affectedClasses = new HashSet<>();
        
        for (String classKey : modifiedCode.keySet()) {
            String code = modifiedCode.get(classKey);
            if (code == null) continue;
            
            String className = extractClassNameFromCode(code);
            String packageName = extractPackageNameFromCode(code);
            
            List<IdentifierContext> contexts = findAllIdentifiers(code, variableName);
            for (IdentifierContext context : contexts) {
                if (isVariableInContext(context) && 
                    isVariableFromSameDeclaration(context, declaration, className, packageName)) {
                    affectedClasses.add(classKey.replace(".class", ""));
                    break;
                }
            }
        }
        
        // Vérifie aussi dans les classes non modifiées
        for (String classKey : classBytes.keySet()) {
            if (modifiedCode.containsKey(classKey)) continue;
            
            String code = decompileClassToString(classKey, classBytes.get(classKey));
            String className = extractClassNameFromCode(code);
            String packageName = extractPackageNameFromCode(code);
            
            List<IdentifierContext> contexts = findAllIdentifiers(code, variableName);
            for (IdentifierContext context : contexts) {
                if (isVariableInContext(context) && 
                    isVariableFromSameDeclaration(context, declaration, className, packageName)) {
                    affectedClasses.add(classKey.replace(".class", ""));
                    break;
                }
            }
        }
        
        return affectedClasses;
    }

    // Méthode pour mettre à jour l'arborescence de manière incrémentale
    // Méthode déléguée à TreeManager
    private void updateTreeIncrementally(List<String> newClassNames) {
        treeManager.updateTreeIncrementally(newClassNames);
        updateObfuscatedClassesCount();
    }

    // Analyse le contexte pour déterminer si on clique sur le type ou le nom de variable
    private boolean isVariableNameInDeclaration(IdentifierContext context) {
        String before = context.beforeContext;
        String after = context.afterContext;
        
        // Pattern pour détecter si on est sur le nom de variable dans une déclaration
        // Ex: "IIiIIiII " + "iiIIiiII" + ";"
        //     before     +  identifier  + after
        
        // Vérifier si on est dans une déclaration de variable avec type de classe
        // Ex: "Logger " + "IiIiiiiiIiiIi" + ";"
        if (before.matches(".*[\\w<>\\[\\]]+\\s+$")) {
            String afterTrimmed = after.trim();
            // Si c'est suivi d'un nom de variable valide
            if (afterTrimmed.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]")) {
                // Vérifier que ce n'est pas une déclaration de classe (pas de "class" avant)
                if (!before.matches(".*\\bclass\\s+$") && !before.matches(".*\\binterface\\s+$") && !before.matches(".*\\benum\\s+$")) {
                    return true;
                }
            }
        }
        
        // Vérifier si on est dans une déclaration avec modificateurs et type de classe
        // Ex: "public static final Logger " + "IiIiiiiiIiiIi" + ";"
        if (before.matches(".*(public|private|protected|static|final|transient|volatile)\\s+[\\w<>\\[\\]]+\\s+$")) {
            String afterTrimmed = after.trim();
            if (afterTrimmed.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]")) {
                if (!before.matches(".*\\bclass\\s+$") && !before.matches(".*\\binterface\\s+$") && !before.matches(".*\\benum\\s+$")) {
                    return true;
                }
            }
        }
        
        // Vérifier si on est dans une déclaration avec modificateurs multiples et type de classe
        // Ex: "public static final Logger " + "IiIiiiiiIiiIi" + ";"
        if (before.matches(".*(public|private|protected|static|final|transient|volatile)\\s+(public|private|protected|static|final|transient|volatile)?\\s+(public|private|protected|static|final|transient|volatile)?\\s+[\\w<>\\[\\]]+\\s+$")) {
            String afterTrimmed = after.trim();
            if (afterTrimmed.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]")) {
                if (!before.matches(".*\\bclass\\s+$") && !before.matches(".*\\binterface\\s+$") && !before.matches(".*\\benum\\s+$")) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    // Analyse le contexte pour déterminer si on clique sur le type de variable
    private boolean isVariableTypeInDeclaration(IdentifierContext context) {
        String before = context.beforeContext;
        String after = context.afterContext;
        
        // Pattern pour détecter si on est sur le type dans une déclaration
        // Ex: "String " + "IIiIIiII" + " iiIIiiII;"
        //     before    + identifier  + after
        
        // Vérifier si on est dans une déclaration de variable avec types primitifs
        if (before.matches(".*(String|int|float|double|boolean|char|byte|short|long|\\w+\\[\\]|\\w+<.*>)\\s+$")) {
            String afterTrimmed = after.trim();
            // Si c'est suivi d'un nom de variable valide
            if (afterTrimmed.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]")) {
                return true;
            }
        }
        
        // Vérifier si on est dans une déclaration avec modificateurs et types primitifs
        if (before.matches(".*(public|private|protected|static|final|transient|volatile)\\s+(String|int|float|double|boolean|char|byte|short|long|\\w+\\[\\]|\\w+<.*>)\\s+$")) {
            String afterTrimmed = after.trim();
            if (afterTrimmed.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]")) {
                return true;
            }
        }
        
        // Vérifier si on est dans une déclaration avec modificateurs multiples et type de classe
        // Ex: "public static final Logger " + "IiIiiiiiIiiIi" + ";"
        if (before.matches(".*(public|private|protected|static|final|transient|volatile)\\s+(public|private|protected|static|final|transient|volatile)?\\s+(public|private|protected|static|final|transient|volatile)?\\s+[\\w<>\\[\\]]+\\s+$")) {
            String afterTrimmed = after.trim();
            if (afterTrimmed.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]")) {
                return true;
            }
        }
        
        // Vérifier si on est dans une déclaration simple avec type de classe
        // Ex: "Logger " + "IiIiiiiiIiiIi" + ";"
        if (before.matches(".*[\\w<>\\[\\]]+\\s+$")) {
            String afterTrimmed = after.trim();
            // Si c'est suivi d'un nom de variable valide ET que ce n'est pas une déclaration de classe
            if (afterTrimmed.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]")) {
                // Vérifier que ce n'est pas une déclaration de classe (pas de "class" avant)
                if (!before.matches(".*\\bclass\\s+$") && !before.matches(".*\\binterface\\s+$") && !before.matches(".*\\benum\\s+$")) {
                    return true;
                }
            }
        }
        
        return false;
    }

    // Navigue vers une classe et positionne le curseur sur un membre spécifique
    private void navigateToClassAndMember(String classKey, String memberName) {
        try {
            // Ouvrir la classe
            decompileClassBytes(classKey, classBytes.get(classKey));
            
            // Trouver l'onglet de la classe
            RSyntaxTextArea targetArea = openTabs.get(classKey);
            if (targetArea == null) {
                System.out.println("DEBUG: Onglet non trouvé pour " + classKey);
                return;
            }
            
            // Chercher le membre dans le code
            String code = targetArea.getText();
            int memberPos = -1;
            
            // Chercher différents types de membres
            String[] patterns = {
                // Méthodes
                "\\b(public|private|protected|static|final|synchronized|native|abstract|strictfp)?\\s*[\\w<>\\[\\]]+\\s+" + java.util.regex.Pattern.quote(memberName) + "\\s*\\(",
                // Variables
                "\\b(public|private|protected|static|final|transient|volatile)?\\s*[\\w<>\\[\\]]+\\s+" + java.util.regex.Pattern.quote(memberName) + "\\s*[=;]",
                // Constantes d'enum
                "\\b" + java.util.regex.Pattern.quote(memberName) + "\\s*[,\\(;]",
                // Classes internes
                "\\b(public|private|protected|static|final|abstract)?\\s*class\\s+" + java.util.regex.Pattern.quote(memberName) + "\\b",
                // Interfaces
                "\\b(public|private|protected|static|final|abstract)?\\s*interface\\s+" + java.util.regex.Pattern.quote(memberName) + "\\b"
            };
            
            for (String pattern : patterns) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher m = p.matcher(code);
                if (m.find()) {
                    memberPos = m.start();
                    break;
                }
            }
            
            if (memberPos != -1) {
                // Positionner le curseur sur le membre
                targetArea.setCaretPosition(memberPos);
                targetArea.requestFocus();
                
                // Surligner le membre
                targetArea.select(memberPos, memberPos + memberName.length());
                
                // Faire défiler pour rendre visible
                targetArea.scrollRectToVisible(targetArea.modelToView(memberPos));
                
                System.out.println("DEBUG: Navigation vers " + memberName + " dans " + classKey + " à la position " + memberPos);
            } else {
                System.out.println("DEBUG: Membre " + memberName + " non trouvé dans " + classKey);
                // Si le membre n'est pas trouvé, juste positionner au début
                targetArea.setCaretPosition(0);
                targetArea.requestFocus();
            }
            
        } catch (Exception e) {
            System.out.println("DEBUG: Erreur navigation vers membre: " + e.getMessage());
        }
    }

    // Nettoie les surlignages multiples
    private void clearMultiHighlights(RSyntaxTextArea area, Map<String, List<Object>> multiHighlights) {
        for (List<Object> highlights : multiHighlights.values()) {
            for (Object highlight : highlights) {
                try {
                    area.getHighlighter().removeHighlight(highlight);
                } catch (Exception ex) {
                    // Ignore
                }
            }
        }
        multiHighlights.clear();
    }

    // Détecte les classes avec des noms obfusqués et met à jour le compteur
    private void updateObfuscatedClassesCount() {
        int obfuscatedCount = 0;
        List<String> obfuscatedClasses = new ArrayList<>();
        
        for (String classKey : classBytes.keySet()) {
            String className = getSimpleClassName(classKey);
            if (isObfuscatedClassName(className)) {
                obfuscatedCount++;
                obfuscatedClasses.add(className);
            }
        }
        
        // Mettre à jour le label
        obfuscatedClassesLabel.setText("Classes obfusquées: " + obfuscatedCount);
        
        // Changer la couleur selon le nombre
        if (obfuscatedCount == 0) {
            obfuscatedClassesLabel.setForeground(java.awt.Color.GREEN);
        } else if (obfuscatedCount <= 10) {
            obfuscatedClassesLabel.setForeground(java.awt.Color.ORANGE);
        } else {
            obfuscatedClassesLabel.setForeground(java.awt.Color.RED);
        }
        
        System.out.println("DEBUG: " + obfuscatedCount + " classes obfusquées détectées");
        if (obfuscatedCount > 0) {
            System.out.println("DEBUG: Classes obfusquées: " + String.join(", ", obfuscatedClasses.subList(0, Math.min(10, obfuscatedClasses.size()))));
            if (obfuscatedClasses.size() > 10) {
                System.out.println("DEBUG: ... et " + (obfuscatedClasses.size() - 10) + " autres");
            }
        }
    }
    
    // Vérifie si un nom de classe est obfusqué
    private boolean isObfuscatedClassName(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        
        // Règles pour détecter les noms obfusqués
        // 1. Noms très courts (1-2 caractères) et composés uniquement de lettres
        if (className.length() <= 2 && className.matches("^[a-zA-Z]+$")) {
            return false; // Probablement des noms légitimes comme "A", "B", etc.
        }
        
        // 2. Noms composés uniquement de 'I', 'i', 'l', '1', 'O', '0'
        if (className.matches("^[Ii1lO0]+$")) {
            return true;
        }
        
        // 3. Noms avec beaucoup de caractères similaires consécutifs
        if (className.length() >= 4) {
            int consecutiveCount = 1;
            char lastChar = className.charAt(0);
            for (int i = 1; i < className.length(); i++) {
                if (className.charAt(i) == lastChar) {
                    consecutiveCount++;
                    if (consecutiveCount >= 4) {
                        return true; // Trop de caractères identiques consécutifs
                    }
                } else {
                    consecutiveCount = 1;
                    lastChar = className.charAt(i);
                }
            }
        }
        
        // 4. Noms avec un ratio élevé de caractères similaires
        if (className.length() >= 6) {
            int iCount = 0, lCount = 0, oCount = 0, zeroCount = 0;
            for (char c : className.toCharArray()) {
                if (c == 'I' || c == 'i') iCount++;
                if (c == 'l' || c == '1') lCount++;
                if (c == 'O') oCount++;
                if (c == '0') zeroCount++;
            }
            
            int totalSimilar = iCount + lCount + oCount + zeroCount;
            double ratio = (double) totalSimilar / className.length();
            
            if (ratio >= 0.7) { // 70% ou plus de caractères similaires
                return true;
            }
        }
        
        // 5. Noms qui ressemblent à des patterns obfusqués
        if (className.matches(".*[Ii]{3,}.*") || // 3 'I' ou 'i' consécutifs
            className.matches(".*[l1]{3,}.*") || // 3 'l' ou '1' consécutifs
            className.matches(".*[O0]{3,}.*")) { // 3 'O' ou '0' consécutifs
            return true;
        }
        
        return false;
    }

    // Vérifie si la classe étend explicitement ALuckyEvent
    private boolean extendsALuckyEvent(String code) {
        // Patterns pour détecter les classes qui étendent ALuckyEvent
        String[] luckyEventPatterns = {
            "extends\\s+ALuckyEvent\\b",
            "extends\\s+[\\w\\.]*ALuckyEvent\\b"
        };
        
        for (String pattern : luckyEventPatterns) {
            if (code.matches("(?s).*" + pattern + ".*")) {
                System.out.println("DEBUG: Pattern ALuckyEvent trouvé: " + pattern);
                return true;
            }
        }
        return false;
    }
    
    // Extrait le nom de l'événement à partir des méthodes a() ou xa()
    private String extractLuckyEventNameFromCode(String code) {
        // Chercher d'abord la méthode xa() (priorité)
        String methodXAPattern = "public\\s+String\\s+xa\\s*\\(\\)\\s*\\{[^}]*return\\s*\"([^\"]+)\"[^}]*\\}";
        java.util.regex.Pattern patternXA = java.util.regex.Pattern.compile(methodXAPattern, java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcherXA = patternXA.matcher(code);
        
        if (matcherXA.find()) {
            String returnValue = matcherXA.group(1);
            System.out.println("DEBUG: Méthode xa() trouvée avec valeur: " + returnValue);
            
            // Extraire le nom à partir de la valeur de retour
            // Exemple: "july/treasure_chest_july" -> "TreasureChestJulyLuckyEvent"
            String name = extractNameFromMethodXA(returnValue);
            if (name != null) {
                return name + "LuckyEvent";
            }
        }
        
        // Si pas de méthode xa(), chercher la méthode a() (fallback)
        String methodAPattern = "public\\s+String\\s+a\\s*\\(\\)\\s*\\{[^}]*return\\s*\"([^\"]+)\"[^}]*\\}";
        java.util.regex.Pattern patternA = java.util.regex.Pattern.compile(methodAPattern, java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcherA = patternA.matcher(code);
        
        if (matcherA.find()) {
            String returnValue = matcherA.group(1);
            System.out.println("DEBUG: Méthode a() trouvée avec valeur: " + returnValue);
            
            // Extraire le nom à partir de la valeur de retour
            // Exemple: "Coffre (au trésor) de juillet" -> "CoffreDeJuilletLuckyEvent"
            String name = extractNameFromMethodA(returnValue);
            if (name != null) {
                return name + "LuckyEvent";
            }
        }
        
        return null;
    }
    
    // Extrait le nom à partir de la méthode a()
    private String extractNameFromMethodA(String returnValue) {
        if (returnValue == null || returnValue.trim().isEmpty()) {
            return null;
        }
        
        // Nettoyer et formater le nom
        String name = returnValue.trim();
        
        // Supprimer les caractères spéciaux et parenthèses
        name = name.replaceAll("[\\(\\)]", "");
        
        // Remplacer les espaces et caractères spéciaux par des underscores
        name = name.replaceAll("[\\s\\-\\.,;:!?]", "_");
        
        // Supprimer les underscores multiples
        name = name.replaceAll("_+", "_");
        
        // Supprimer les underscores au début et à la fin
        name = name.replaceAll("^_+|_+$", "");
        
        // Capitaliser chaque mot
        String[] words = name.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(capitalize(word));
            }
        }
        
        return result.toString();
    }
    
    // Extrait le nom à partir de la méthode xa()
    private String extractNameFromMethodXA(String returnValue) {
        if (returnValue == null || returnValue.trim().isEmpty()) {
            return null;
        }
        
        // Prendre le dernier mot après le dernier '/'
        String[] parts = returnValue.split("/");
        if (parts.length == 0) {
            return null;
        }
        
        String lastPart = parts[parts.length - 1];
        
        // Nettoyer et formater le nom
        String name = lastPart.trim();
        
        // Remplacer les underscores par des espaces pour la capitalisation
        String[] words = name.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(capitalize(word));
            }
        }
        
        return result.toString();
    }
    
    // Vérifie si une classe est déjà bien nommée
    private boolean isAlreadyWellNamed(String currentName, String suggestedName) {
        if (currentName == null || suggestedName == null) {
            return false;
        }
        
        // Si le nom actuel est déjà le nom suggéré, c'est déjà bien nommé
        if (currentName.equals(suggestedName)) {
            return true;
        }
        
        // Vérifier si le nom actuel suit un pattern de nommage correct
        // Exemples de noms bien nommés :
        // - CoffreDeJuilletLuckyEvent
        // - TreasureChestJulyLuckyEvent
        // - MonEvenementLuckyEvent
        
        // Règles pour un nom bien nommé :
        // 1. Ne doit pas être obfusqué (pas de IIIiIIIi)
        if (isObfuscatedClassName(currentName)) {
            return false;
        }
        
        // 2. Doit contenir des mots significatifs (pas juste des lettres aléatoires)
        if (currentName.length() < 5) {
            return false;
        }
        
        // 3. Doit contenir au moins une majuscule (style CamelCase)
        if (!currentName.matches(".*[A-Z].*")) {
            return false;
        }
        
        // 4. Ne doit pas contenir que des caractères similaires
        if (currentName.matches("^[Ii1lO0]+$")) {
            return false;
        }
        
        // 5. Doit ressembler à un nom de classe Java valide
        if (!currentName.matches("^[A-Z][a-zA-Z0-9_]*$")) {
            return false;
        }
        
        // 6. Si le nom suggéré contient "LuckyEvent", vérifier que le nom actuel aussi
        if (suggestedName.contains("LuckyEvent") && !currentName.contains("LuckyEvent")) {
            return false;
        }
        
        return true;
    }

    // Nouvelle méthode d'analyse IA avancée pour le renommage automatique
    private void advancedAIAnalysisAndRename() {
        System.out.println("DEBUG: Début advancedAIAnalysisAndRename() - Analyse IA avancée");
        
        // Création de la popup de progression pour l'analyse IA
        JDialog aiProgressDialog = new JDialog(this, "Analyse IA avancée en cours", true);
        JProgressBar aiProgressBar = new JProgressBar(0, 100);
        aiProgressBar.setStringPainted(true);
        JLabel statusLabel = new JLabel("Analyse IA des classes...");
        JTextArea analysisLog = new JTextArea(10, 50);
        analysisLog.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(analysisLog);
        
        aiProgressDialog.setLayout(new BorderLayout());
        aiProgressDialog.add(aiProgressBar, BorderLayout.NORTH);
        aiProgressDialog.add(statusLabel, BorderLayout.CENTER);
        aiProgressDialog.add(scrollPane, BorderLayout.SOUTH);
        aiProgressDialog.setSize(700, 500);
        aiProgressDialog.setLocationRelativeTo(this);
        
        // Lancement de l'analyse IA dans un thread séparé
        new Thread(() -> {
            try {
                Map<String, String> renameMap = new HashMap<>();
                Map<String, String> oldToNewSimpleName = new HashMap<>();
                Map<String, String> classAnalysis = new HashMap<>();
                int processedCount = 0;
                int totalClasses = classBytes.size();
                
                // Phase 1: Analyse IA de chaque classe (0-70%)
                javax.swing.SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Phase 1/4: Analyse IA des classes (0/" + totalClasses + ")");
                    aiProgressBar.setValue(0);
                });
                
                final int[] skippedClasses = {0};
                final int[] analyzedClasses = {0};
                
                for (String classKey : new ArrayList<>(classBytes.keySet())) {
                    processedCount++;
                    final int currentProgress = processedCount;
                    final String currentClass = classKey;
                    
                    // Mise à jour de la progression (0-70%)
                    int progressPercent = (int)((currentProgress * 70.0) / totalClasses);
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        aiProgressBar.setValue(progressPercent);
                        statusLabel.setText("Phase 1/4: Analyse IA de " + currentClass + " (" + currentProgress + "/" + totalClasses + ")");
                    });
                    
                    // Vérifier si la classe doit être ignorée
                    if (isInPalamodPackage(classKey)) {
                        skippedClasses[0]++;
                        System.out.println("DEBUG: ⏭️ " + classKey + " déjà organisée, ignorée");
                        continue; // Ignore déjà dans palamod/ ou déjà bien nommée
                    }
                    
                    analyzedClasses[0]++;
                    System.out.println("DEBUG: 🔍 Analyse de " + classKey);
                    
                    String code = modifiedCode.containsKey(classKey)
                        ? modifiedCode.get(classKey)
                        : decompileClassToString(classKey, classBytes.get(classKey));
                    String className = getSimpleClassName(classKey);
                    
                    // Analyse IA avancée
                    ClassAnalysisResult analysis = performAdvancedAIAnalysis(code, className);
                    classAnalysis.put(classKey, analysis.toString());
                    
                    // Log de l'analyse
                    final String analysisText = "Classe: " + className + "\n" + analysis.toString() + "\n---\n";
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        analysisLog.append(analysisText);
                        analysisLog.setCaretPosition(analysisLog.getDocument().getLength());
                    });
                    
                    // Application des suggestions de l'IA
                    if (analysis.suggestedPackage != null && analysis.suggestedClassName != null) {
                        String newKey = analysis.suggestedPackage + "/" + analysis.suggestedClassName + ".class";
                        
                        if (!classKey.equals(newKey)) {
                            renameMap.put(classKey, newKey);
                            oldToNewSimpleName.put(className, analysis.suggestedClassName);
                            
                            // Mise à jour du code si nécessaire
                            if (analysis.suggestedPackage != null) {
                                String newCode = updatePackageInCode(code, analysis.suggestedPackage);
                                modifiedCode.put(classKey, newCode);
                            }
                            
                            System.out.println("DEBUG: ✅ " + classKey + " -> " + newKey);
                        } else {
                            System.out.println("DEBUG: ℹ️ " + classKey + " pas de changement nécessaire");
                        }
                    }
                    
                    // Petite pause pour éviter de bloquer l'interface
                    if (processedCount % 10 == 0) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                
                System.out.println("DEBUG: 📊 Résumé de l'analyse: " + analyzedClasses[0] + " classes analysées, " + skippedClasses[0] + " classes ignorées");
                
                // Phase 2: Application des renommages (70-85%)
                if (!renameMap.isEmpty()) {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        aiProgressBar.setValue(70);
                        statusLabel.setText("Phase 2/4: Application des renommages IA (" + renameMap.size() + " classes)...");
                    });
                    
                    int renameCount = 0;
                    for (Map.Entry<String, String> entry : renameMap.entrySet()) {
                        String oldKey = entry.getKey();
                        String newKey = entry.getValue();
                        renameCount++;
                        
                        final int currentRename = renameCount;
                        final String oldClass = oldKey;
                        final String newClass = newKey;
                        
                        // Progression de 70% à 85%
                        int progressPercent = 70 + (int)((currentRename * 15.0) / renameMap.size());
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            aiProgressBar.setValue(progressPercent);
                            statusLabel.setText("Phase 2/4: Renommage IA " + currentRename + "/" + renameMap.size() + ": " + 
                                              oldClass.substring(oldClass.lastIndexOf('/') + 1) + " -> " + 
                                              newClass.substring(newClass.lastIndexOf('/') + 1));
                        });
                        
                        classBytes.put(newKey, classBytes.remove(oldKey));
                        if (modifiedCode.containsKey(oldKey)) modifiedCode.put(newKey, modifiedCode.remove(oldKey));
                        if (openTabs.containsKey(oldKey)) openTabs.put(newKey, openTabs.remove(oldKey));
                        if (classToDisplayName.containsKey(oldKey)) classToDisplayName.put(newKey, classToDisplayName.remove(oldKey));
                        
                        // Petite pause
                        if (renameCount % 5 == 0) {
                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                    
                    // Phase 3: Mise à jour des références (85-95%)
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        aiProgressBar.setValue(85);
                        statusLabel.setText("Phase 3/4: Mise à jour des références IA...");
                    });
                    
                    updateReferencesAfterAIRenameWithProgress(oldToNewSimpleName, aiProgressBar, statusLabel);
                    
                    // Phase 4: Rafraîchissement de l'arborescence (95-100%)
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        aiProgressBar.setValue(95);
                        statusLabel.setText("Phase 4/4: Mise à jour de l'arborescence IA...");
                    });
                    
                    List<String> newClassNames = new ArrayList<>(classBytes.keySet());
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        try {
                            updateTreeIncrementally(newClassNames);
                            aiProgressBar.setValue(100);
                            statusLabel.setText("Terminé !");
                        } catch (Exception e) {
                            System.out.println("DEBUG: Erreur lors de la mise à jour de l'arborescence IA: " + e.getMessage());
                        }
                    });
                } else {
                    // Si aucun renommage, passer directement à 100%
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        aiProgressBar.setValue(100);
                        statusLabel.setText("Aucun renommage nécessaire");
                    });
                }
                
                // Attendre un peu avant de fermer
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Fermeture de la popup et affichage du résumé
                javax.swing.SwingUtilities.invokeLater(() -> {
                    aiProgressDialog.dispose();
                    if (renameMap.size() > 0) {
                        JOptionPane.showMessageDialog(this, 
                            "Analyse IA terminée !\n\n" +
                            "Classes totales : " + totalClasses + "\n" +
                            "Classes analysées : " + analyzedClasses[0] + "\n" +
                            "Classes ignorées (déjà organisées) : " + skippedClasses[0] + "\n" +
                            "Classes renommées : " + renameMap.size() + "\n\n" +
                            "L'IA a analysé le code et suggéré des noms plus descriptifs basés sur le contenu et la fonctionnalité.\n" +
                            "Les classes déjà bien organisées ont été préservées.",
                            "Analyse IA terminée", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, 
                            "Analyse IA terminée.\n\n" +
                            "Classes totales : " + totalClasses + "\n" +
                            "Classes analysées : " + analyzedClasses[0] + "\n" +
                            "Classes ignorées (déjà organisées) : " + skippedClasses[0] + "\n" +
                            "Aucune classe renommée.\n\n" +
                            "L'IA n'a pas trouvé de suggestions d'amélioration pour les noms de classes.\n" +
                            "Toutes les classes sont déjà correctement organisées ou ne correspondent pas aux critères de renommage.",
                            "Analyse IA terminée", JOptionPane.INFORMATION_MESSAGE);
                    }
                });
                
            } catch (Exception e) {
                System.out.println("DEBUG: Erreur dans advancedAIAnalysisAndRename: " + e.getMessage());
                e.printStackTrace();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    aiProgressDialog.dispose();
                    JOptionPane.showMessageDialog(this, 
                        "Erreur lors de l'analyse IA :\n" + e.getMessage(),
                        "Erreur", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
        
        // Affichage de la popup
        aiProgressDialog.setVisible(true);
    }
    
    // Classe pour stocker les résultats d'analyse IA
    private static class ClassAnalysisResult {
        String suggestedPackage;
        String suggestedClassName;
        String classType;
        String confidence;
        String reasoning;
        
        ClassAnalysisResult(String suggestedPackage, String suggestedClassName, String classType, String confidence, String reasoning) {
            this.suggestedPackage = suggestedPackage;
            this.suggestedClassName = suggestedClassName;
            this.classType = classType;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
        
        @Override
        public String toString() {
            return String.format("Type: %s | Package suggéré: %s | Nom suggéré: %s | Confiance: %s | Raison: %s", 
                               classType, suggestedPackage, suggestedClassName, confidence, reasoning);
        }
    }
    
    // Méthode d'analyse IA avancée
    private ClassAnalysisResult performAdvancedAIAnalysis(String code, String className) {
        // Vérifier d'abord si la classe est déjà bien nommée
        if (!isObfuscatedClassName(className)) {
            return new ClassAnalysisResult(null, className, "AlreadyNamed", "Très élevée", "Classe déjà bien nommée, aucune modification nécessaire");
        }
        
        // Analyse du type de classe
        String classType = analyzeClassType(code, className);
        String suggestedPackage = null;
        String suggestedClassName = className;
        String confidence = "Moyenne";
        String reasoning = "";
        
        // Analyse basée sur le type de classe
        switch (classType) {
            case "Item":
                suggestedPackage = "fr/paladium/palamod/client/items";
                String itemName = extractItemNameFromCode(code);
                if (itemName != null) {
                    suggestedClassName = getNewItemClassName(itemName);
                    confidence = "Élevée";
                    reasoning = "Nom d'item extrait: " + itemName;
                } else {
                    reasoning = "Classe Item détectée mais nom non extrait";
                }
                break;
                
            case "Block":
                suggestedPackage = "fr/paladium/palamod/client/blocks";
                String blockName = extractBlockNameFromCode(code);
                if (blockName != null) {
                    suggestedClassName = getNewBlockClassName(blockName);
                    confidence = "Élevée";
                    reasoning = "Nom de block extrait: " + blockName;
                } else {
                    reasoning = "Classe Block détectée mais nom non extrait";
                }
                break;
                
            case "UI":
                suggestedPackage = "fr/paladium/palamod/client/ui";
                confidence = "Élevée";
                reasoning = "Classe UI détectée par patterns";
                break;
                
            case "Command":
                suggestedPackage = "fr/paladium/palamod/client/commands";
                confidence = "Élevée";
                reasoning = "Classe Command détectée par patterns";
                break;
                
            case "TileEntity":
                suggestedPackage = "fr/paladium/palamod/client/tileentity";
                confidence = "Élevée";
                reasoning = "Classe TileEntity détectée par patterns";
                break;
                
            case "Model":
                suggestedPackage = "fr/paladium/palamod/client/models";
                confidence = "Élevée";
                reasoning = "Classe Model détectée par patterns";
                break;
                
            case "NetworkPacket":
                suggestedPackage = "fr/paladium/palamod/network/packet";
                confidence = "Élevée";
                reasoning = "Classe NetworkPacket détectée par patterns";
                break;
                
            case "LuckyEvent":
                suggestedPackage = "fr/paladium/palamod/client/luckyevent";
                String luckyEventName = extractLuckyEventNameFromCode(code);
                if (luckyEventName != null) {
                    suggestedClassName = luckyEventName;
                    confidence = "Élevée";
                    reasoning = "Nom d'événement extrait: " + luckyEventName;
                } else {
                    reasoning = "Classe LuckyEvent détectée mais nom non extrait";
                }
                break;
                
            case "Utility":
                suggestedPackage = "fr/paladium/palamod/client/utils";
                confidence = "Moyenne";
                reasoning = "Classe utilitaire détectée par analyse du code";
                break;
                
            case "Handler":
                suggestedPackage = "fr/paladium/palamod/client/handlers";
                confidence = "Moyenne";
                reasoning = "Classe handler détectée par analyse du code";
                break;
                
            case "Renderer":
                suggestedPackage = "fr/paladium/palamod/client/renderers";
                confidence = "Moyenne";
                reasoning = "Classe renderer détectée par analyse du code";
                break;
                
            case "Unknown":
                confidence = "Faible";
                reasoning = "Type de classe non déterminé";
                break;
        }
        
        // Analyse supplémentaire pour les classes non typées
        if (classType.equals("Unknown")) {
            String inferredType = inferClassTypeFromContent(code, className);
            if (!inferredType.equals("Unknown")) {
                classType = inferredType;
                confidence = "Moyenne";
                reasoning = "Type inféré par analyse du contenu: " + inferredType;
                
                // Suggérer un package basé sur le type inféré
                switch (inferredType) {
                    case "Utility":
                        suggestedPackage = "fr/paladium/palamod/client/utils";
                        break;
                    case "Handler":
                        suggestedPackage = "fr/paladium/palamod/client/handlers";
                        break;
                    case "Renderer":
                        suggestedPackage = "fr/paladium/palamod/client/renderers";
                        break;
                    case "Manager":
                        suggestedPackage = "fr/paladium/palamod/client/managers";
                        break;
                }
            }
        }
        
        // Suggérer un meilleur nom si le nom actuel est obfusqué
        if (isObfuscatedClassName(className)) {
            String suggestedName = suggestClassNameFromContent(code, className);
            if (!suggestedName.equals(className)) {
                suggestedClassName = suggestedName;
                confidence = "Élevée";
                reasoning += " | Nom obfusqué détecté et remplacé par: " + suggestedName;
            }
        }
        
        // Analyse de la complexité pour ajuster la confiance
        int complexity = analyzeClassComplexity(code);
        if (complexity > 100) {
            confidence = "Très élevée";
            reasoning += " | Classe complexe détectée (" + complexity + " éléments)";
        } else if (complexity > 50) {
            confidence = "Élevée";
            reasoning += " | Classe moyennement complexe (" + complexity + " éléments)";
        }
        
        // Détecter si c'est une classe importante
        if (isImportantClass(code, className)) {
            reasoning += " | Classe importante détectée";
        }
        
        return new ClassAnalysisResult(suggestedPackage, suggestedClassName, classType, confidence, reasoning);
    }
    
    // Méthode pour analyser le type de classe
    private String analyzeClassType(String code, String className) {
        if (extendsItemClass(code)) return "Item";
        if (extendsBlockClass(code)) return "Block";
        if (isUIClass(code, className)) return "UI";
        if (isCommandClass(code)) return "Command";
        if (isTileEntityClass(code)) return "TileEntity";
        if (isModelClass(code, className)) return "Model";
        if (isNetworkPacketClass(code)) return "NetworkPacket";
        if (extendsALuckyEvent(code)) return "LuckyEvent";
        
        return "Unknown";
    }
    
    // Méthode pour inférer le type de classe à partir du contenu
    private String inferClassTypeFromContent(String code, String className) {
        // Analyse des méthodes et champs pour déterminer le type
        String lowerCode = code.toLowerCase();
        
        // Patterns pour les classes utilitaires
        if (lowerCode.contains("static") && 
            (lowerCode.contains("public static") || lowerCode.contains("private static")) &&
            !lowerCode.contains("extends") && !lowerCode.contains("implements")) {
            return "Utility";
        }
        
        // Patterns pour les handlers
        if (lowerCode.contains("handler") || lowerCode.contains("event") ||
            lowerCode.contains("on") && (lowerCode.contains("click") || lowerCode.contains("key") || lowerCode.contains("mouse"))) {
            return "Handler";
        }
        
        // Patterns pour les renderers
        if (lowerCode.contains("render") || lowerCode.contains("draw") || 
            lowerCode.contains("gl") || lowerCode.contains("opengl")) {
            return "Renderer";
        }
        
        // Patterns pour les managers
        if (lowerCode.contains("manager") || lowerCode.contains("registry") ||
            lowerCode.contains("register") || lowerCode.contains("unregister")) {
            return "Manager";
        }
        
        return "Unknown";
    }
    
    // Méthode pour mettre à jour le package dans le code
    private String updatePackageInCode(String code, String newPackage) {
        String packagePath = newPackage.replace("/", ".");
        
        if (code.contains("package ")) {
            return code.replaceFirst("^package [^;]+;", "package " + packagePath + ";");
        } else {
            return "package " + packagePath + ";\n\n" + code;
        }
    }
    
    // Méthode pour mettre à jour les références après renommage IA avec progression
    private void updateReferencesAfterAIRenameWithProgress(Map<String, String> oldToNewSimpleName, JProgressBar progressBar, JLabel statusLabel) {
        System.out.println("DEBUG: Mise à jour des références après renommage IA avec progression");
        
        // Créer une liste des classes à traiter pour avoir un compteur précis
        List<String> classesToUpdate = new ArrayList<>(modifiedCode.keySet());
        int totalClasses = classesToUpdate.size();
        int processedClasses = 0;
        
        System.out.println("DEBUG: Mise à jour des références pour " + totalClasses + " classes");
        
        // Calculer la progression de base (85% à 95%)
        int baseProgress = 85;
        int progressRange = 10; // 85% à 95%
        
        for (String k : classesToUpdate) {
            processedClasses++;
            
            // Calculer la progression actuelle (85% à 95%)
            int currentProgressPercent = baseProgress + (int)((processedClasses * progressRange) / totalClasses);
            
            // Mise à jour de la progression tous les 10 classes ou à la fin
            if (processedClasses % 10 == 0 || processedClasses == totalClasses) {
                final int currentProgress = processedClasses;
                final int total = totalClasses;
                final int progressPercent = currentProgressPercent;
                javax.swing.SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(progressPercent);
                    statusLabel.setText("Phase 3/4: Mise à jour des références (" + currentProgress + "/" + total + ")...");
                    System.out.println("DEBUG: Mise à jour des références: " + currentProgress + "/" + total + " (" + progressPercent + "%)");
                });
            }
            
            String code = modifiedCode.get(k);
            boolean codeChanged = false;
            
            for (Map.Entry<String, String> entry : oldToNewSimpleName.entrySet()) {
                String oldSimple = entry.getKey();
                String newSimple = entry.getValue();
                
                if (!oldSimple.equals(newSimple)) {
                    // Utiliser ClassRenamer pour renommer uniquement les références de classe
                    String newCode = classRenamer.renameClassReferencesOnly(code, oldSimple, newSimple);
                    if (!newCode.equals(code)) {
                        code = newCode;
                        codeChanged = true;
                    }
                }
            }
            
            if (codeChanged) {
                modifiedCode.put(k, code);
                if (openTabs.containsKey(k)) {
                    openTabs.get(k).setText(code);
                }
            }
            
            // Petite pause pour éviter de bloquer l'interface
            if (processedClasses % 50 == 0) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // Mise à jour finale à 95%
        javax.swing.SwingUtilities.invokeLater(() -> {
            progressBar.setValue(95);
            statusLabel.setText("Phase 3/4: Mise à jour des références terminée");
        });
        
        System.out.println("DEBUG: Mise à jour des références terminée pour " + processedClasses + " classes");
    }
    
    // Méthode avancée pour suggérer des noms de classes basés sur le contenu
    private String suggestClassNameFromContent(String code, String currentClassName) {
        // Si le nom actuel est déjà descriptif, le garder
        if (!isObfuscatedClassName(currentClassName)) {
            return currentClassName;
        }
        
        // Analyse du contenu pour suggérer un nom
        String lowerCode = code.toLowerCase();
        
        // Patterns pour détecter des fonctionnalités spécifiques
        if (lowerCode.contains("inventory") || lowerCode.contains("container")) {
            return "InventoryManager";
        }
        
        if (lowerCode.contains("crafting") || lowerCode.contains("recipe")) {
            return "CraftingHandler";
        }
        
        if (lowerCode.contains("world") || lowerCode.contains("dimension")) {
            return "WorldHandler";
        }
        
        if (lowerCode.contains("player") || lowerCode.contains("entity")) {
            return "PlayerHandler";
        }
        
        if (lowerCode.contains("block") || lowerCode.contains("tile")) {
            return "BlockHandler";
        }
        
        if (lowerCode.contains("item") || lowerCode.contains("stack")) {
            return "ItemHandler";
        }
        
        if (lowerCode.contains("network") || lowerCode.contains("packet")) {
            return "NetworkHandler";
        }
        
        if (lowerCode.contains("gui") || lowerCode.contains("screen")) {
            return "GuiHandler";
        }
        
        if (lowerCode.contains("render") || lowerCode.contains("draw")) {
            return "Renderer";
        }
        
        if (lowerCode.contains("config") || lowerCode.contains("setting")) {
            return "ConfigManager";
        }
        
        if (lowerCode.contains("save") || lowerCode.contains("load")) {
            return "DataManager";
        }
        
        if (lowerCode.contains("random") || lowerCode.contains("chance")) {
            return "RandomHandler";
        }
        
        if (lowerCode.contains("time") || lowerCode.contains("tick")) {
            return "TimeHandler";
        }
        
        if (lowerCode.contains("sound") || lowerCode.contains("audio")) {
            return "SoundHandler";
        }
        
        if (lowerCode.contains("particle") || lowerCode.contains("effect")) {
            return "ParticleHandler";
        }
        
        // Analyse des méthodes pour suggérer un nom
        String methodPattern = "public\\s+(?:static\\s+)?(?:void|\\w+)\\s+(\\w+)\\s*\\(";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(methodPattern);
        java.util.regex.Matcher matcher = pattern.matcher(code);
        
        java.util.Map<String, Integer> methodCounts = new java.util.HashMap<>();
        while (matcher.find()) {
            String methodName = matcher.group(1);
            methodCounts.put(methodName, methodCounts.getOrDefault(methodName, 0) + 1);
        }
        
        // Trouver la méthode la plus fréquente
        String mostCommonMethod = methodCounts.entrySet().stream()
            .max(java.util.Map.Entry.comparingByValue())
            .map(java.util.Map.Entry::getKey)
            .orElse("");
        
        if (!mostCommonMethod.isEmpty()) {
            // Suggérer un nom basé sur la méthode la plus commune
            if (mostCommonMethod.startsWith("on")) {
                return mostCommonMethod.substring(2) + "Handler";
            } else if (mostCommonMethod.endsWith("Manager")) {
                return mostCommonMethod;
            } else {
                return mostCommonMethod + "Handler";
            }
        }
        
        // Analyse des champs pour suggérer un nom
        String fieldPattern = "(?:public|private|protected)\\s+(?:static\\s+)?(?:final\\s+)?(\\w+)\\s+(\\w+)\\s*;";
        pattern = java.util.regex.Pattern.compile(fieldPattern);
        matcher = pattern.matcher(code);
        
        java.util.Map<String, Integer> fieldCounts = new java.util.HashMap<>();
        while (matcher.find()) {
            String fieldType = matcher.group(1);
            String fieldName = matcher.group(2);
            fieldCounts.put(fieldType, fieldCounts.getOrDefault(fieldType, 0) + 1);
        }
        
        // Trouver le type de champ le plus fréquent
        String mostCommonFieldType = fieldCounts.entrySet().stream()
            .max(java.util.Map.Entry.comparingByValue())
            .map(java.util.Map.Entry::getKey)
            .orElse("");
        
        if (!mostCommonFieldType.isEmpty()) {
            if (mostCommonFieldType.equals("ItemStack")) {
                return "ItemStackHandler";
            } else if (mostCommonFieldType.equals("Block")) {
                return "BlockHandler";
            } else if (mostCommonFieldType.equals("Entity")) {
                return "EntityHandler";
            } else if (mostCommonFieldType.equals("World")) {
                return "WorldHandler";
            } else if (mostCommonFieldType.equals("Player")) {
                return "PlayerHandler";
            }
        }
        
        // Si rien n'est trouvé, suggérer un nom générique
        return "CustomHandler";
    }
    
    // Méthode pour analyser la complexité d'une classe
    private int analyzeClassComplexity(String code) {
        int complexity = 0;
        
        // Compter les méthodes
        java.util.regex.Pattern methodPattern = java.util.regex.Pattern.compile("public\\s+(?:static\\s+)?(?:void|\\w+)\\s+\\w+\\s*\\(");
        java.util.regex.Matcher matcher = methodPattern.matcher(code);
        while (matcher.find()) {
            complexity++;
        }
        
        // Compter les champs
        java.util.regex.Pattern fieldPattern = java.util.regex.Pattern.compile("(?:public|private|protected)\\s+(?:static\\s+)?(?:final\\s+)?\\w+\\s+\\w+\\s*;");
        matcher = fieldPattern.matcher(code);
        while (matcher.find()) {
            complexity++;
        }
        
        // Compter les lignes de code
        String[] lines = code.split("\n");
        complexity += lines.length;
        
        return complexity;
    }
    
    // Méthode pour détecter les classes importantes basées sur la complexité
    private boolean isImportantClass(String code, String className) {
        int complexity = analyzeClassComplexity(code);
        
        // Une classe est considérée importante si elle a une complexité élevée
        // ou si elle contient des mots-clés importants
        String lowerCode = code.toLowerCase();
        
        return complexity > 50 || 
               lowerCode.contains("main") ||
               lowerCode.contains("init") ||
               lowerCode.contains("setup") ||
               lowerCode.contains("register") ||
               lowerCode.contains("handler") ||
               lowerCode.contains("manager");
    }
    
    // Méthode pour améliorer l'analyse IA avec des suggestions de noms basées sur le contenu
    private void enhanceAIAnalysisWithContentBasedNaming() {
        System.out.println("DEBUG: Amélioration de l'analyse IA avec suggestions de noms basées sur le contenu");
        
        // Cette méthode peut être appelée pour améliorer les suggestions de noms
        // en analysant plus en profondeur le contenu des classes
        for (String classKey : classBytes.keySet()) {
            if (isInPalamodPackage(classKey)) {
                continue; // Ignore déjà dans palamod/
            }
            
            String code = modifiedCode.containsKey(classKey)
                ? modifiedCode.get(classKey)
                : decompileClassToString(classKey, classBytes.get(classKey));
            String className = getSimpleClassName(classKey);
            
            // Si la classe a un nom obfusqué, suggérer un meilleur nom
            if (isObfuscatedClassName(className)) {
                String suggestedName = suggestClassNameFromContent(code, className);
                if (!suggestedName.equals(className)) {
                    System.out.println("DEBUG: Suggestion de nom pour " + className + " -> " + suggestedName);
                }
            }
        }
    }
    
    // Méthode pour mettre à jour les références après renommage IA (ancienne version pour compatibilité)
    private void updateReferencesAfterAIRename(Map<String, String> oldToNewSimpleName) {
        System.out.println("DEBUG: Mise à jour des références après renommage IA");
        
        // Créer une liste des classes à traiter pour avoir un compteur précis
        List<String> classesToUpdate = new ArrayList<>(modifiedCode.keySet());
        int totalClasses = classesToUpdate.size();
        int processedClasses = 0;
        
        System.out.println("DEBUG: Mise à jour des références pour " + totalClasses + " classes");
        
        for (String k : classesToUpdate) {
            processedClasses++;
            
            // Mise à jour de la progression tous les 10 classes ou à la fin
            if (processedClasses % 10 == 0 || processedClasses == totalClasses) {
                final int currentProgress = processedClasses;
                final int total = totalClasses;
                javax.swing.SwingUtilities.invokeLater(() -> {
                    System.out.println("DEBUG: Mise à jour des références: " + currentProgress + "/" + total);
                });
            }
            
            String code = modifiedCode.get(k);
            boolean codeChanged = false;
            
            for (Map.Entry<String, String> entry : oldToNewSimpleName.entrySet()) {
                String oldSimple = entry.getKey();
                String newSimple = entry.getValue();
                
                if (!oldSimple.equals(newSimple)) {
                    // Utiliser ClassRenamer pour renommer uniquement les références de classe
                    String newCode = classRenamer.renameClassReferencesOnly(code, oldSimple, newSimple);
                    if (!newCode.equals(code)) {
                        code = newCode;
                        codeChanged = true;
                    }
                }
            }
            
            if (codeChanged) {
                modifiedCode.put(k, code);
                if (openTabs.containsKey(k)) {
                    openTabs.get(k).setText(code);
                }
            }
            
            // Petite pause pour éviter de bloquer l'interface
            if (processedClasses % 50 == 0) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        System.out.println("DEBUG: Mise à jour des références terminée pour " + processedClasses + " classes");
    }

    // Ajoute cette méthode utilitaire dans la classe :
    private static String extractJsonField(String json, String key) {
        String regex = "\\\"" + key + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(json);
        if (m.find()) return m.group(1);
        return "";
    }
} 