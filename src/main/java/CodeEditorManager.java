
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import javax.swing.*;
import javax.swing.undo.UndoManager;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CannotRedoException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

public class CodeEditorManager {
    private final Map<RSyntaxTextArea, UndoManager> undoManagers = new HashMap<>();
    private final Map<RSyntaxTextArea, Map<String, List<Object>>> multiHighlights = new HashMap<>();
    private final ThemeManager themeManager;
    private final IdentifierAnalyzer identifierAnalyzer;
    private final RenameManager renameManager;
    private final NavigationManager navigationManager;
    
    public CodeEditorManager(ThemeManager themeManager, IdentifierAnalyzer identifierAnalyzer, 
                            RenameManager renameManager, NavigationManager navigationManager) {
        this.themeManager = themeManager;
        this.identifierAnalyzer = identifierAnalyzer;
        this.renameManager = renameManager;
        this.navigationManager = navigationManager;
    }
    
    public void setupEditor(RSyntaxTextArea area, Runnable onCtrlClick) {
        addUndoRedo(area);
        addCtrlClickListener(area, onCtrlClick);
        addHighlightOccurrences(area);
        if (themeManager.isDarkTheme()) {
            themeManager.applyDarkTheme(area);
        } else {
            themeManager.applyLightTheme(area);
        }
    }
    
    private void addUndoRedo(RSyntaxTextArea area) {
        UndoManager undoManager = new UndoManager();
        undoManagers.put(area, undoManager);
        area.getDocument().addUndoableEditListener(undoManager);
        
        area.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "undo");
        area.getActionMap().put("undo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                try {
                    if (undoManager.canUndo()) undoManager.undo();
                } catch (CannotUndoException ex) {}
            }
        });
        
        area.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "redo");
        area.getActionMap().put("redo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                try {
                    if (undoManager.canRedo()) undoManager.redo();
                } catch (CannotRedoException ex) {}
            }
        });
    }
    
    private void addCtrlClickListener(RSyntaxTextArea area, Runnable onCtrlClick) {
        area.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if ((e.getModifiersEx() & java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0 && onCtrlClick != null) {
                    onCtrlClick.run();
                }
            }
        });
    }
    
    private void addHighlightOccurrences(RSyntaxTextArea area) {
        area.addCaretListener(e -> {
            RSyntaxTextArea textArea = (RSyntaxTextArea) e.getSource();
            int pos = textArea.getCaretPosition();
            if (pos < 0 || pos >= textArea.getText().length()) return;
            
            String text = textArea.getText();
            int start = pos, end = pos;
            while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
            while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) end++;
            if (start == end) return;
            
            String word = text.substring(start, end);
            if (word.isEmpty() || !Character.isJavaIdentifierStart(word.charAt(0))) return;
            
            updateMultiSelectHighlights(textArea, word);
        });
    }
    
    private void updateMultiSelectHighlights(RSyntaxTextArea area, String word) {
        clearMultiHighlights(area);
        Map<String, List<Object>> highlights = multiHighlights.computeIfAbsent(area, k -> new HashMap<>());
        List<Object> tags = new ArrayList<>();
        
        String text = area.getText();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(word) + "\\b");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        
        javax.swing.text.Highlighter highlighter = area.getHighlighter();
        while (matcher.find()) {
            try {
                Object tag = highlighter.addHighlight(matcher.start(), matcher.end(), 
                    new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(
                        themeManager.isDarkTheme() ? new java.awt.Color(100, 100, 200, 100) : new java.awt.Color(200, 200, 255, 100)));
                tags.add(tag);
            } catch (Exception ex) {}
        }
        highlights.put(word, tags);
    }
    
    private void clearMultiHighlights(RSyntaxTextArea area) {
        Map<String, List<Object>> highlights = multiHighlights.get(area);
        if (highlights == null) return;
        javax.swing.text.Highlighter highlighter = area.getHighlighter();
        for (List<Object> tags : highlights.values()) {
            for (Object tag : tags) {
                highlighter.removeHighlight(tag);
            }
        }
        highlights.clear();
    }
    
    public void clearHighlights(RSyntaxTextArea area) {
        clearMultiHighlights(area);
    }
}

