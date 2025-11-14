import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import javax.swing.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class NavigationManager {
    private final Map<String, byte[]> classBytes;
    private final Map<String, RSyntaxTextArea> openTabs;
    private final IdentifierAnalyzer identifierAnalyzer;
    private final RenameManager renameManager;
    private final DecompilerManager decompilerManager;
    private final Runnable decompileClassBytes;
    
    public NavigationManager(Map<String, byte[]> classBytes, Map<String, RSyntaxTextArea> openTabs,
                           IdentifierAnalyzer identifierAnalyzer, RenameManager renameManager,
                           DecompilerManager decompilerManager, Runnable decompileClassBytes) {
        this.classBytes = classBytes;
        this.openTabs = openTabs;
        this.identifierAnalyzer = identifierAnalyzer;
        this.renameManager = renameManager;
        this.decompilerManager = decompilerManager;
        this.decompileClassBytes = decompileClassBytes;
    }
    
    public void handleCtrlClick(RSyntaxTextArea area, java.awt.event.MouseEvent e) {
        int pos = area.viewToModel(e.getPoint());
        try {
            String text = area.getText();
            int start = pos, end = pos;
            while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
            while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) end++;
            if (start == end) return;
            
            String word = text.substring(start, end);
            String beforeWord = text.substring(Math.max(0, start - 50), start);
            String afterWord = text.substring(end, Math.min(text.length(), end + 50));
            
            Pattern refPattern = Pattern.compile("([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*\\.\\s*" + Pattern.quote(word) + "\\b");
            java.util.regex.Matcher refMatcher = refPattern.matcher(beforeWord + word + afterWord);
            
            if (refMatcher.find()) {
                String className = refMatcher.group(1);
                String memberName = word;
                String classToOpen = findClassByName(className);
                if (classToOpen != null) {
                    navigateToClassAndMember(classToOpen, memberName);
                    return;
                }
            }
            
            boolean isDotClass = false;
            int after = end;
            while (after < text.length() && Character.isWhitespace(text.charAt(after))) after++;
            if (after + 6 <= text.length() && text.substring(after, after + 6).equals(".class")) {
                isDotClass = true;
            }
            
            String classToOpen = findClassByName(word);
            if (isDotClass && classToOpen != null) {
                decompileClassBytes.run();
                return;
            }
            if (!isDotClass && classToOpen != null) {
                decompileClassBytes.run();
            }
        } catch (Exception ex) {
            System.out.println("DEBUG: Erreur navigation Ctrl+clic: " + ex.getMessage());
        }
    }
    
    public void navigateToClassAndMember(String classKey, String memberName) {
        try {
            RSyntaxTextArea targetArea = openTabs.get(classKey);
            if (targetArea == null) return;
            
            String code = targetArea.getText();
            int memberPos = -1;
            
            String[] patterns = {
                "\\b(public|private|protected|static|final|synchronized|native|abstract|strictfp)?\\s*[\\w<>\\[\\]]+\\s+" + Pattern.quote(memberName) + "\\s*\\(",
                "\\b(public|private|protected|static|final|transient|volatile)?\\s*[\\w<>\\[\\]]+\\s+" + Pattern.quote(memberName) + "\\s*[=;]",
                "\\b" + Pattern.quote(memberName) + "\\s*[,\\(;]",
                "\\b(public|private|protected|static|final|abstract)?\\s*class\\s+" + Pattern.quote(memberName) + "\\b",
                "\\b(public|private|protected|static|final|abstract)?\\s*interface\\s+" + Pattern.quote(memberName) + "\\b"
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
                targetArea.setCaretPosition(memberPos);
                targetArea.requestFocus();
                targetArea.select(memberPos, memberPos + memberName.length());
                targetArea.scrollRectToVisible(targetArea.modelToView(memberPos));
            } else {
                targetArea.setCaretPosition(0);
                targetArea.requestFocus();
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Erreur navigation vers membre: " + e.getMessage());
        }
    }
    
    private String findClassByName(String simpleName) {
        for (String className : classBytes.keySet()) {
            String simple = className.endsWith(".class") ? className.substring(0, className.length() - 6) : className;
            simple = simple.substring(simple.lastIndexOf('/') + 1);
            if (simpleName.equals(simple)) {
                return className;
            }
        }
        return null;
    }
    
    public JPopupMenu createContextMenu(String word, String code, String currentClassName, 
                                        Map<String, String> modifiedCode, Map<String, byte[]> classBytes,
                                        JFrame parent) {
        JPopupMenu popup = new JPopupMenu();
        boolean isClass = false;
        boolean isMethod = false;
        boolean isVar = false;
        
        String foundClass = findClassByName(word);
        if (foundClass != null) isClass = true;
        
        List<IdentifierAnalyzer.IdentifierContext> contexts = identifierAnalyzer.findAllIdentifiers(code, word);
        if (!contexts.isEmpty()) {
            IdentifierAnalyzer.IdentifierContext context = contexts.get(0);
            if (identifierAnalyzer.isVariableNameInDeclaration(context)) {
                isVar = true;
            } else if (identifierAnalyzer.isVariableTypeInDeclaration(context)) {
                isClass = true;
            } else {
                isClass = identifierAnalyzer.isClassInContext(context);
                isMethod = identifierAnalyzer.isMethodInContext(context);
                isVar = identifierAnalyzer.isVariableInContext(context);
            }
        }
        
        if (foundClass != null && !isClass && !isMethod && !isVar) {
            isClass = true;
        }
        
        if (isClass) {
            JMenuItem renameClass = new JMenuItem("Renommer la classe partout...");
            String finalFound = foundClass;
            renameClass.addActionListener(ev -> {
                String newName = JOptionPane.showInputDialog(parent, "Nouveau nom de classe :", word);
                if (newName != null && !newName.trim().isEmpty() && !newName.equals(word)) {
                    renameManager.renameClassEverywhereAndUpdateKeys(finalFound, word, newName, null, null);
                }
            });
            popup.add(renameClass);
        }
        
        if (isMethod) {
            JMenuItem renameMethod = new JMenuItem("Renommer la méthode partout...");
            renameMethod.addActionListener(ev -> {
                String newName = JOptionPane.showInputDialog(parent, "Nouveau nom de méthode :", word);
                if (newName != null && !newName.trim().isEmpty() && !newName.equals(word)) {
                    renameManager.renameMethodInContext(word, newName, modifiedCode, openTabs, decompilerManager, classBytes);
                }
            });
            popup.add(renameMethod);
        }
        
        if (isVar) {
            JMenuItem renameVar = new JMenuItem("Renommer la variable...");
            renameVar.addActionListener(ev -> {
                String newName = JOptionPane.showInputDialog(parent, "Nouveau nom de variable :", word);
                if (newName != null && !newName.trim().isEmpty() && !newName.equals(word)) {
                    // Logique de renommage de variable
                }
            });
            popup.add(renameVar);
        }
        
        return popup;
    }
}

