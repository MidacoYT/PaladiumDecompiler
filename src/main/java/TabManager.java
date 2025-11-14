
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import javax.swing.*;
import java.util.Map;

public class TabManager {
    private final JTabbedPane tabbedPane;
    private final Map<String, RSyntaxTextArea> openTabs;
    private final ThemeManager themeManager;
    
    public TabManager(JTabbedPane tabbedPane, Map<String, RSyntaxTextArea> openTabs, ThemeManager themeManager) {
        this.tabbedPane = tabbedPane;
        this.openTabs = openTabs;
        this.themeManager = themeManager;
    }
    
    public RSyntaxTextArea createOrGetTab(String className, Runnable onNewTab) {
        RSyntaxTextArea area = openTabs.get(className);
        if (area == null) {
            area = new RSyntaxTextArea(30, 80);
            area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
            area.setCodeFoldingEnabled(true);
            area.setEditable(true);
            openTabs.put(className, area);
            addClosableTab(className, area);
            if (onNewTab != null) onNewTab.run();
            tabbedPane.setSelectedComponent(area.getParent().getParent());
        } else {
            selectTab(area);
        }
        return area;
    }
    
    private void selectTab(RSyntaxTextArea area) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            java.awt.Component comp = tabbedPane.getComponentAt(i);
            if (comp instanceof RTextScrollPane) {
                java.awt.Component view = ((RTextScrollPane) comp).getViewport().getView();
                if (view == area) {
                    tabbedPane.setSelectedIndex(i);
                    break;
                }
            }
        }
    }
    
    private void addClosableTab(String className, RSyntaxTextArea area) {
        String tabTitle = className.replace(".class", "");
        RTextScrollPane scroll = new RTextScrollPane(area);
        tabbedPane.addTab(tabTitle, scroll);
        int idx = tabbedPane.indexOfComponent(scroll);
        JPanel tabPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        tabPanel.setOpaque(false);
        JLabel titleLabel = new JLabel(tabTitle + "  ");
        JButton closeButton = new JButton("x");
        closeButton.setMargin(new java.awt.Insets(0, 2, 0, 2));
        closeButton.setBorder(null);
        closeButton.setFocusable(false);
        closeButton.setContentAreaFilled(false);
        closeButton.addActionListener(e -> {
            int closeIdx = tabbedPane.indexOfComponent(scroll);
            if (closeIdx != -1) {
                tabbedPane.remove(closeIdx);
                openTabs.values().remove(area);
            }
        });
        tabPanel.add(titleLabel);
        tabPanel.add(closeButton);
        tabbedPane.setTabComponentAt(idx, tabPanel);
    }
    
    public void updateTabTitle(String oldClassName, String newClassName) {
        RSyntaxTextArea area = openTabs.get(oldClassName);
        if (area == null) return;
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            java.awt.Component comp = tabbedPane.getComponentAt(i);
            if (comp instanceof RTextScrollPane) {
                java.awt.Component view = ((RTextScrollPane) comp).getViewport().getView();
                if (view == area) {
                    String newTitle = newClassName.replace(".class", "");
                    tabbedPane.setTitleAt(i, newTitle);
                    JPanel tabPanel = (JPanel) tabbedPane.getTabComponentAt(i);
                    if (tabPanel != null) {
                        JLabel titleLabel = (JLabel) tabPanel.getComponent(0);
                        if (titleLabel != null) {
                            titleLabel.setText(newTitle + "  ");
                        }
                    }
                    break;
                }
            }
        }
    }
    
    public void closeAllTabs() {
        tabbedPane.removeAll();
        openTabs.clear();
    }
    
    public RSyntaxTextArea getCurrentTab() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx == -1) return null;
        java.awt.Component comp = tabbedPane.getComponentAt(idx);
        if (comp instanceof RTextScrollPane) {
            java.awt.Component view = ((RTextScrollPane) comp).getViewport().getView();
            if (view instanceof RSyntaxTextArea) {
                return (RSyntaxTextArea) view;
            }
        }
        return null;
    }
}

