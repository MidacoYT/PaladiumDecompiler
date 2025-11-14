
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class IdentifierAnalyzer {
    
    public static class IdentifierContext {
        public final String identifier;
        public final int startPos;
        public final int endPos;
        public final String beforeContext;
        public final String afterContext;
        public final String fullContext;
        
        public IdentifierContext(String identifier, int startPos, int endPos, 
                                String beforeContext, String afterContext, String fullContext) {
            this.identifier = identifier;
            this.startPos = startPos;
            this.endPos = endPos;
            this.beforeContext = beforeContext;
            this.afterContext = afterContext;
            this.fullContext = fullContext;
        }
    }
    
    public static class VariableDeclarationContext {
        public final String variableName;
        public final String className;
        public final String packageName;
        public final String fullContext;
        public final int declarationLine;
        
        public VariableDeclarationContext(String variableName, String className, String packageName, 
                                         String fullContext, int declarationLine) {
            this.variableName = variableName;
            this.className = className;
            this.packageName = packageName;
            this.fullContext = fullContext;
            this.declarationLine = declarationLine;
        }
    }
    
    public List<IdentifierContext> findAllIdentifiers(String code, String targetIdentifier) {
        List<IdentifierContext> contexts = new ArrayList<>();
        int pos = 0;
        
        while (pos < code.length()) {
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
            if (identifier.equals(targetIdentifier)) {
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
    
    public boolean isClassInContext(IdentifierContext context) {
        String before = context.beforeContext;
        String after = context.afterContext;
        
        return before.matches(".*(public\\s+)?(final\\s+)?(abstract\\s+)?class\\s+$") ||
               before.matches(".*extends\\s+$") ||
               before.matches(".*implements\\s+$") ||
               before.matches(".*import\\s+[a-zA-Z0-9_\\.]*\\.$") ||
               before.matches(".*new\\s+$") ||
               before.matches(".*<\\s*$") || before.matches(".*,\\s*$") ||
               (before.matches(".*\\(\\s*$") && after.trim().startsWith(")")) ||
               before.matches(".*(public|private|protected|static|final|transient|volatile)\\s+[\\w<>\\[\\]]+\\s+$") ||
               (before.matches(".*[\\w<>\\[\\]]+\\s+$") && after.trim().matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]"));
    }
    
    public boolean isMethodInContext(IdentifierContext context) {
        String before = context.beforeContext;
        String after = context.afterContext;
        return after.trim().startsWith("(") ||
               before.matches(".*(public|private|protected|static|final|synchronized|native|abstract|strictfp)?\\s*[\\w<>\\[\\]]+\\s+$") ||
               before.matches(".*\\b(return|if|while|for|switch)\\s+$");
    }
    
    public boolean isVariableInContext(IdentifierContext context) {
        String before = context.beforeContext;
        String after = context.afterContext;
        
        return before.matches(".*(String|int|float|double|boolean|char|byte|short|long|\\w+\\[\\]|\\w+<.*>)\\s+$") ||
               before.matches(".*(public|private|protected|static|final|transient|volatile)\\s+(String|int|float|double|boolean|char|byte|short|long|\\w+\\[\\]|\\w+<.*>)\\s+$") ||
               (before.matches(".*[\\w<>\\[\\]]+\\s+$") && after.trim().matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]")) ||
               after.trim().matches("^[=;,\\)\\]]") ||
               before.matches(".*(=|\\+|\\-|\\*|/|%|\\||&|\\^|<<|>>|>>>)\\s*$") ||
               before.matches(".*\\.\\s*$") ||
               before.matches(".*\\(\\s*$") || before.matches(".*,\\s*$") ||
               before.matches(".*return\\s+$") ||
               before.matches(".*(if|while|for)\\s*\\(.*$") ||
               before.matches(".*for\\s*\\([^)]*\\s+$");
    }
    
    public boolean isVariableNameInDeclaration(IdentifierContext context) {
        String before = context.beforeContext.trim();
        String after = context.afterContext.trim();
        return before.matches(".*(String|int|float|double|boolean|char|byte|short|long|\\w+\\[\\]|\\w+<.*>)\\s+$") &&
               (after.startsWith("=") || after.startsWith(";") || after.startsWith(","));
    }
    
    public boolean isVariableTypeInDeclaration(IdentifierContext context) {
        String before = context.beforeContext.trim();
        String after = context.afterContext.trim();
        return (before.matches(".*(public|private|protected|static|final|transient|volatile)\\s+$") ||
                before.matches(".*(String|int|float|double|boolean|char|byte|short|long)\\s+$")) &&
               after.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*\\s*[=;,\\[\\]]");
    }
    
    public String renameIdentifierInContext(String code, IdentifierContext context, String newName) {
        if (context.startPos > 0 && Character.isJavaIdentifierPart(code.charAt(context.startPos - 1))) {
            return code;
        }
        if (context.endPos < code.length() && Character.isJavaIdentifierPart(code.charAt(context.endPos))) {
            return code;
        }
        return code.substring(0, context.startPos) + newName + code.substring(context.endPos);
    }
    
    public VariableDeclarationContext findVariableDeclaration(String code, String variableName, String currentClassName) {
        String[] lines = code.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.matches(".*\\b" + Pattern.quote(variableName) + "\\b.*[;=]")) {
                if (line.matches(".*(String|int|float|double|boolean|char|byte|short|long|\\w+\\[\\]|\\w+<.*>)\\s+" + Pattern.quote(variableName) + "\\b.*")) {
                    String className = extractClassNameFromCode(code);
                    String packageName = extractPackageNameFromCode(code);
                    return new VariableDeclarationContext(variableName, className, packageName, line, i + 1);
                }
            }
        }
        return null;
    }
    
    public String extractClassNameFromCode(String code) {
        Pattern pattern = Pattern.compile("(?:public\\s+)?(?:final\\s+)?(?:abstract\\s+)?class\\s+(\\w+)");
        java.util.regex.Matcher matcher = pattern.matcher(code);
        return matcher.find() ? matcher.group(1) : null;
    }
    
    public String extractPackageNameFromCode(String code) {
        Pattern pattern = Pattern.compile("package\\s+([\\w\\.]+);");
        java.util.regex.Matcher matcher = pattern.matcher(code);
        return matcher.find() ? matcher.group(1) : null;
    }
    
    public boolean isVariableFromSameDeclaration(IdentifierContext context, VariableDeclarationContext targetDeclaration, 
                                                  String currentClassName, String currentPackageName) {
        if (targetDeclaration != null) {
            if (!currentClassName.equals(targetDeclaration.className)) return false;
            if (targetDeclaration.packageName != null && currentPackageName != null) {
                if (!currentPackageName.equals(targetDeclaration.packageName)) return false;
            }
            String contextBefore = context.beforeContext;
            String contextAfter = context.afterContext;
            if (contextBefore.matches(".*\\b(this|" + Pattern.quote(targetDeclaration.className) + ")\\s*\\.\\s*$")) {
                return true;
            }
            if (contextBefore.matches(".*\\b(String|int|float|double|boolean|char|byte|short|long|\\w+\\[\\]|\\w+<.*>)\\s+$")) {
                return true;
            }
            if (contextAfter.trim().startsWith("=") || contextAfter.trim().startsWith(";")) {
                return true;
            }
        }
        return true;
    }
}

