import java.util.*;
import java.util.regex.Pattern;

public class ClassAnalysisHelper {
    
    public static class ClassAnalysisResult {
        public final String suggestedPackage;
        public final String suggestedClassName;
        public final String classType;
        public final String confidence;
        public final String reasoning;
        
        public ClassAnalysisResult(String suggestedPackage, String suggestedClassName, 
                                  String classType, String confidence, String reasoning) {
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
    
    public static boolean isObfuscatedClassName(String className) {
        if (className == null || className.length() < 3) return false;
        String clean = className.replace(".class", "");
        if (clean.length() < 3) return false;
        int upperCount = 0, lowerCount = 0, digitCount = 0;
        for (char c : clean.toCharArray()) {
            if (Character.isUpperCase(c)) upperCount++;
            else if (Character.isLowerCase(c)) lowerCount++;
            else if (Character.isDigit(c)) digitCount++;
        }
        return (upperCount + lowerCount + digitCount == clean.length()) && 
               (upperCount > 0 && lowerCount > 0) && 
               (clean.length() > 5 || (upperCount > 2 && lowerCount > 2));
    }
    
    public static String getSimpleClassName(String classKey) {
        String name = classKey.endsWith(".class") ? classKey.substring(0, classKey.length() - 6) : classKey;
        return name.substring(name.lastIndexOf('/') + 1);
    }
    
    public static boolean extendsItemClass(String code) {
        return code.matches("(?s).*extends\\s+(Item|net\\.minecraft\\.item\\.Item)\\b.*");
    }
    
    public static boolean extendsBlockClass(String code) {
        return code.matches("(?s).*extends\\s+(Block|net\\.minecraft\\.block\\.Block)\\b.*");
    }
    
    public static String extractItemNameFromCode(String code) {
        Pattern pattern = Pattern.compile("setUnlocalizedName\\([\"']([^\"']+)[\"']\\)|getUnlocalizedName\\(\\).*?substring\\(.*?\\)");
        java.util.regex.Matcher matcher = pattern.matcher(code);
        if (matcher.find()) {
            String name = matcher.group(1);
            if (name != null && name.startsWith("item.")) {
                return name.substring(5);
            }
            return name;
        }
        return null;
    }
    
    public static String extractBlockNameFromCode(String code) {
        Pattern pattern = Pattern.compile("setUnlocalizedName\\([\"']([^\"']+)[\"']\\)|getUnlocalizedName\\(\\).*?substring\\(.*?\\)");
        java.util.regex.Matcher matcher = pattern.matcher(code);
        if (matcher.find()) {
            String name = matcher.group(1);
            if (name != null && name.startsWith("block.")) {
                return name.substring(6);
            }
            return name;
        }
        return null;
    }
    
    public static String getNewItemClassName(String itemName) {
        if (itemName == null || itemName.isEmpty()) return "ItemUnknown";
        String[] parts = itemName.split("_");
        StringBuilder result = new StringBuilder("Item");
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            }
        }
        return result.toString();
    }
    
    public static String getNewBlockClassName(String blockName) {
        if (blockName == null || blockName.isEmpty()) return "BlockUnknown";
        String[] parts = blockName.split("_");
        StringBuilder result = new StringBuilder("Block");
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            }
        }
        return result.toString();
    }
    
    public static boolean isInPalamodPackage(String classKey) {
        return classKey.contains("/palamod/client/items/") ||
               classKey.contains("/palamod/client/blocks/") ||
               classKey.contains("/palamod/client/ui/") ||
               classKey.contains("/palamod/client/commands/") ||
               classKey.contains("/palamod/client/tileentity/") ||
               classKey.contains("/palamod/client/models/") ||
               classKey.contains("/palamod/client/luckyevent/") ||
               classKey.contains("/palamod/network/packet/") ||
               !isObfuscatedClassName(getSimpleClassName(classKey));
    }
    
    public static boolean isAlreadyWellNamed(String currentName, String suggestedName) {
        return currentName != null && suggestedName != null && 
               currentName.equals(suggestedName) && 
               !isObfuscatedClassName(currentName);
    }
}

