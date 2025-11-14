import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import javax.swing.*;
import java.util.Collection;

/**
 * Gère les thèmes de l'interface (clair/sombre).
 */
public class ThemeManager {
    private boolean darkTheme = true;
    private final JFrame parent;
    private Collection<RSyntaxTextArea> textAreas;
    
    public ThemeManager(JFrame parent) {
        this.parent = parent;
    }
    
    /**
     * Initialise le thème sombre par défaut.
     */
    public void initializeDarkTheme() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            System.err.println("FlatLaf Dark non disponible");
        }
    }
    
    /**
     * Bascule entre le thème clair et sombre.
     */
    public void toggleTheme() {
        try {
            if (darkTheme) {
                UIManager.setLookAndFeel(new FlatLightLaf());
                darkTheme = false;
            } else {
                UIManager.setLookAndFeel(new FlatDarkLaf());
                darkTheme = true;
            }
            SwingUtilities.updateComponentTreeUI(parent);
            // Applique le thème à tous les onglets ouverts
            if (textAreas != null) {
                for (RSyntaxTextArea area : textAreas) {
                    if (darkTheme) {
                        applyDarkTheme(area);
                    } else {
                        applyLightTheme(area);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    /**
     * Applique le thème sombre à un RSyntaxTextArea.
     */
    public void applyDarkTheme(RSyntaxTextArea area) {
        try {
            Theme theme = Theme.load(getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
            theme.apply(area);
        } catch (Exception e) {
            // Fallback : rien
        }
    }
    
    /**
     * Applique le thème clair à un RSyntaxTextArea.
     */
    public void applyLightTheme(RSyntaxTextArea area) {
        try {
            Theme theme = Theme.load(getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/default.xml"));
            theme.apply(area);
        } catch (Exception e) {
            // Fallback : rien
        }
    }
    
    public boolean isDarkTheme() {
        return darkTheme;
    }
    
    public void setTextAreas(Collection<RSyntaxTextArea> textAreas) {
        this.textAreas = textAreas;
    }
}

