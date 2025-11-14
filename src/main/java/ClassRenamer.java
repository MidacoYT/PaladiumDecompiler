
import java.util.*;
import java.util.regex.Pattern;

/**
 * Classe responsable du renommage intelligent des classes avec détection contextuelle précise.
 * Ne renomme que dans des contextes où on est sûr qu'il s'agit d'une classe.
 */
public class ClassRenamer {
    
    /**
     * Renomme une classe dans le code en ne remplaçant que les occurrences dans des contextes de classe.
     * 
     * @param code Le code source
     * @param oldClassName L'ancien nom de classe
     * @param newClassName Le nouveau nom de classe
     * @return Le code avec les renommages appliqués
     */
    public String renameClassInCode(String code, String oldClassName, String newClassName) {
        if (oldClassName.equals(newClassName)) {
            return code;
        }
        
        String result = code;
        String escapedOldName = Pattern.quote(oldClassName);
        
        // 1. Déclaration de classe (public class, class, final class, etc.)
        result = result.replaceAll(
            "(public\\s+)?(final\\s+)?(abstract\\s+)?(static\\s+)?class\\s+" + escapedOldName + "\\b",
            "$1$2$3$4class " + newClassName
        );
        
        // 2. Constructeur (nom de classe suivi de parenthèse)
        result = result.replaceAll(
            "\\b" + escapedOldName + "\\s*\\(",
            newClassName + "("
        );
        
        // 3. Import statements
        result = result.replaceAll(
            "(import\\s+[a-zA-Z0-9_\\.]*\\.)" + escapedOldName + "(\\s*;|\\s*$)",
            "$1" + newClassName + "$2"
        );
        
        // 4. Type de variable après modificateurs (public, private, static, final, etc.)
        // Pattern: (modifiers)? type variableName;
        result = result.replaceAll(
            "(public|private|protected|static|final|transient|volatile|abstract|synchronized|native|strictfp)\\s+" + escapedOldName + "\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*[=;,\\[\\]]",
            "$1 " + newClassName + " $2"
        );
        
        // 5. Type de variable simple (sans modificateurs mais avec nom de variable après)
        // Pattern: Type variableName;
        result = result.replaceAll(
            "(?<![\\w$])" + escapedOldName + "\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*[=;,\\[\\]]",
            newClassName + " $1"
        );
        
        // 6. Instanciation avec new
        result = result.replaceAll(
            "\\bnew\\s+" + escapedOldName + "\\s*\\(",
            "new " + newClassName + "("
        );
        
        // 7. Type de retour de méthode
        // Pattern: (modifiers)? returnType methodName(
        result = result.replaceAll(
            "(public|private|protected|static|final|synchronized|native|abstract|strictfp)?\\s*" + escapedOldName + "\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*\\(",
            "$1 " + newClassName + " $2("
        );
        
        // 8. Type de paramètre de méthode
        // Pattern: methodName(Type paramName) ou methodName(Type param1, Type param2)
        result = result.replaceAll(
            "\\b" + escapedOldName + "\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*[,)]",
            newClassName + " $1"
        );
        
        // 9. Type générique <Type>
        result = result.replaceAll(
            "<\\s*" + escapedOldName + "\\s*>",
            "<" + newClassName + ">"
        );
        result = result.replaceAll(
            "<([^>]*)\\b" + escapedOldName + "\\b([^>]*)>",
            "<$1" + newClassName + "$2>"
        );
        
        // 10. Cast explicite (Type) expression
        result = result.replaceAll(
            "\\(\\s*" + escapedOldName + "\\s*\\)",
            "(" + newClassName + ")"
        );
        
        // 11. Extends et implements
        result = result.replaceAll(
            "extends\\s+" + escapedOldName + "\\b",
            "extends " + newClassName
        );
        result = result.replaceAll(
            "implements\\s+([^\\{]*\\b)" + escapedOldName + "\\b",
            "implements $1" + newClassName
        );
        
        // 12. Javadoc references
        result = result.replaceAll(
            "(@see|@link|@throws|@exception|@param|@return)\\s+" + escapedOldName + "\\b",
            "$1 " + newClassName
        );
        
        // 13. Type dans tableau Type[]
        result = result.replaceAll(
            "\\b" + escapedOldName + "\\s*\\[\\s*\\]",
            newClassName + "[]"
        );
        
        // 14. Type dans déclaration de variable avec initialisation
        // Pattern: Type var = new Type();
        result = result.replaceAll(
            "\\b" + escapedOldName + "\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*=",
            newClassName + " $1 ="
        );
        
        return result;
    }
    
    /**
     * Renomme une classe dans plusieurs fichiers de code.
     * 
     * @param codeMap Map des clés de classe vers leur code source
     * @param oldClassName L'ancien nom de classe
     * @param newClassName Le nouveau nom de classe
     * @return Map mise à jour avec les codes modifiés
     */
    public Map<String, String> renameClassInMultipleFiles(
            Map<String, String> codeMap, 
            String oldClassName, 
            String newClassName) {
        
        Map<String, String> result = new HashMap<>();
        
        for (Map.Entry<String, String> entry : codeMap.entrySet()) {
            String classKey = entry.getKey();
            String code = entry.getValue();
            
            String newCode = renameClassInCode(code, oldClassName, newClassName);
            result.put(classKey, newCode);
        }
        
        return result;
    }
    
    /**
     * Vérifie si un identifiant dans le code est utilisé comme type de classe.
     * 
     * @param code Le code source
     * @param identifier L'identifiant à vérifier
     * @param position La position de l'identifiant dans le code
     * @return true si l'identifiant est utilisé comme type de classe
     */
    public boolean isClassTypeUsage(String code, String identifier, int position) {
        if (position < 0 || position >= code.length()) {
            return false;
        }
        
        // Extraire le contexte avant et après
        int contextStart = Math.max(0, position - 100);
        int contextEnd = Math.min(code.length(), position + identifier.length() + 100);
        String before = code.substring(contextStart, position);
        String after = code.substring(position + identifier.length(), contextEnd);
        
        // Patterns pour détecter un usage de type de classe
        
        // 1. Après modificateurs (public, private, static, final, etc.)
        if (before.matches(".*\\b(public|private|protected|static|final|transient|volatile|abstract|synchronized|native|strictfp)\\s+$")) {
            // Vérifier que c'est suivi d'un nom de variable
            String afterTrimmed = after.trim();
            if (afterTrimmed.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]")) {
                return true;
            }
        }
        
        // 2. Après "new"
        if (before.matches(".*\\bnew\\s+$")) {
            return true;
        }
        
        // 3. Dans une déclaration de classe (extends, implements)
        if (before.matches(".*\\b(extends|implements)\\s+$")) {
            return true;
        }
        
        // 4. Type de retour de méthode
        if (before.matches(".*\\b(public|private|protected|static|final|synchronized|native|abstract|strictfp)?\\s*$") &&
            after.trim().matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*\\(")) {
            return true;
        }
        
        // 5. Type de paramètre
        if (before.matches(".*\\([^)]*$") && after.trim().matches("^\\s+[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[,)]")) {
            return true;
        }
        
        // 6. Cast explicite
        if (before.matches(".*\\(\\s*$") && after.trim().startsWith(")")) {
            return true;
        }
        
        // 7. Type générique
        if (before.matches(".*<\\s*$") || before.matches(".*,\\s*$")) {
            return true;
        }
        
        // 8. Import statement
        if (before.matches(".*import\\s+[a-zA-Z0-9_\\.]*\\.$")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Renomme uniquement les références de classe dans le code (pas les déclarations).
     * Utilisé pour mettre à jour les références après un renommage automatique.
     * 
     * @param code Le code source
     * @param oldClassName L'ancien nom de classe
     * @param newClassName Le nouveau nom de classe
     * @return Le code avec les références renommées
     */
    public String renameClassReferencesOnly(String code, String oldClassName, String newClassName) {
        if (oldClassName.equals(newClassName)) {
            return code;
        }
        
        String result = code;
        String escapedOldName = Pattern.quote(oldClassName);
        
        // Ne pas toucher aux déclarations de classe, seulement aux usages
        
        // 1. Import statements
        result = result.replaceAll(
            "(import\\s+[a-zA-Z0-9_\\.]*\\.)" + escapedOldName + "(\\s*;|\\s*$)",
            "$1" + newClassName + "$2"
        );
        
        // 2. Type de variable après modificateurs (public, private, static, final, etc.)
        result = result.replaceAll(
            "(public|private|protected|static|final|transient|volatile|abstract|synchronized|native|strictfp)\\s+" + escapedOldName + "\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*[=;,\\[\\]]",
            "$1 " + newClassName + " $2"
        );
        
        // 3. Type de variable simple (sans modificateurs mais avec nom de variable après)
        result = result.replaceAll(
            "(?<![\\w$])" + escapedOldName + "\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*[=;,\\[\\]]",
            newClassName + " $1"
        );
        
        // 4. Instanciation avec new
        result = result.replaceAll(
            "\\bnew\\s+" + escapedOldName + "\\s*\\(",
            "new " + newClassName + "("
        );
        
        // 5. Type de retour de méthode
        result = result.replaceAll(
            "(public|private|protected|static|final|synchronized|native|abstract|strictfp)?\\s*" + escapedOldName + "\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*\\(",
            "$1 " + newClassName + " $2("
        );
        
        // 6. Type de paramètre de méthode
        result = result.replaceAll(
            "\\b" + escapedOldName + "\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*[,)]",
            newClassName + " $1"
        );
        
        // 7. Type générique <Type>
        result = result.replaceAll(
            "<\\s*" + escapedOldName + "\\s*>",
            "<" + newClassName + ">"
        );
        result = result.replaceAll(
            "<([^>]*)\\b" + escapedOldName + "\\b([^>]*)>",
            "<$1" + newClassName + "$2>"
        );
        
        // 8. Cast explicite (Type) expression
        result = result.replaceAll(
            "\\(\\s*" + escapedOldName + "\\s*\\)",
            "(" + newClassName + ")"
        );
        
        // 9. Extends et implements
        result = result.replaceAll(
            "extends\\s+" + escapedOldName + "\\b",
            "extends " + newClassName
        );
        result = result.replaceAll(
            "implements\\s+([^\\{]*\\b)" + escapedOldName + "\\b",
            "implements $1" + newClassName
        );
        
        // 10. Javadoc references
        result = result.replaceAll(
            "(@see|@link|@throws|@exception|@param|@return)\\s+" + escapedOldName + "\\b",
            "$1 " + newClassName
        );
        
        // 11. Type dans tableau Type[]
        result = result.replaceAll(
            "\\b" + escapedOldName + "\\s*\\[\\s*\\]",
            newClassName + "[]"
        );
        
        // 12. Type dans déclaration de variable avec initialisation
        result = result.replaceAll(
            "\\b" + escapedOldName + "\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*=",
            newClassName + " $1 ="
        );
        
        return result;
    }
    
}

