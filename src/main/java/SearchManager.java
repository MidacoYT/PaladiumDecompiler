import javax.swing.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import java.util.*;

/**
 * Gère la recherche dans le code.
 */
public class SearchManager {
    private final Map<String, byte[]> classBytes;
    private final Map<String, String> modifiedCode;
    private final Map<String, Integer> resultToLine = new HashMap<>();
    private final Map<String, String> resultToClass = new HashMap<>();
    private final DefaultListModel<String> globalSearchListModel;
    private final DecompilerManager decompilerManager;
    
    public SearchManager(Map<String, byte[]> classBytes, Map<String, String> modifiedCode, 
                        DefaultListModel<String> globalSearchListModel, DecompilerManager decompilerManager) {
        this.classBytes = classBytes;
        this.modifiedCode = modifiedCode;
        this.globalSearchListModel = globalSearchListModel;
        this.decompilerManager = decompilerManager;
    }
    
    /**
     * Effectue une recherche globale dans toutes les classes.
     */
    public void performGlobalSearch(String searchText, Map<String, RSyntaxTextArea> openTabs) {
        if (searchText == null || searchText.isEmpty()) {
            globalSearchListModel.clear();
            return;
        }
        
        globalSearchListModel.clear();
        resultToLine.clear();
        resultToClass.clear();
        
        for (String className : classBytes.keySet()) {
            String code;
            RSyntaxTextArea area = openTabs.get(className);
            if (area != null) {
                code = area.getText();
            } else if (modifiedCode.containsKey(className)) {
                code = modifiedCode.get(className);
            } else {
                code = decompilerManager.decompileClassToString(className, classBytes.get(className));
            }
            
            String[] lines = code.split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains(searchText)) {
                    String preview = lines[i].trim();
                    if (preview.length() > 120) preview = preview.substring(0, 120) + "...";
                    String key = className.replace(".class", "") + ":" + (i + 1) + "  " + preview;
                    globalSearchListModel.addElement(key);
                    resultToLine.put(key, i);
                    resultToClass.put(key, className);
                }
            }
        }
    }
    
    /**
     * Recherche dans le code actuel.
     */
    public void searchInCode(RSyntaxTextArea area, String searchText) {
        if (area == null || searchText == null || searchText.isEmpty()) {
            return;
        }
        
        String code = area.getText();
        int idx = code.indexOf(searchText);
        if (idx >= 0) {
            area.setCaretPosition(idx);
            area.requestFocus();
            area.select(idx, idx + searchText.length());
        }
    }
    
    public Map<String, Integer> getResultToLine() {
        return resultToLine;
    }
    
    public Map<String, String> getResultToClass() {
        return resultToClass;
    }
}

