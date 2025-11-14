import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class AutoClassRenamer {
    private final Path srcDir;
    private final Path projectRoot;
    private final Path logFile;

    public AutoClassRenamer(String src, String projectRoot, String logFile) {
        this.srcDir = Paths.get(src);
        this.projectRoot = Paths.get(projectRoot);
        this.logFile = Paths.get(logFile);
    }

    public void run() throws IOException {
        if (Files.exists(logFile)) Files.delete(logFile);
        Files.walk(srcDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(this::processFile);
        System.out.println("\n📄 Mapping de renommage enregistré dans: " + logFile);
        System.out.println("\n🎉 Script terminé avec succès.");
    }

    private void processFile(Path file) {
        try {
            String content = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("public\\s+class\\s+(\\w+)\\b").matcher(content);
            if (!m.find()) return;
            String oldClassName = m.group(1);

            // Recherche du nom d'item
            String itemName = extractItemName(content);
            if (itemName == null) {
                System.out.println("❌ [" + oldClassName + "] Aucun nom d'item trouvé, fichier ignoré.");
                return;
            }
            String newClassName = getNewClassName(itemName);
            String newVarName = getNewVarName(itemName);

            if (oldClassName.equals(newClassName)) {
                System.out.println("✔️ [" + oldClassName + "] Déjà correctement nommé, ignoré.");
                return;
            }

            System.out.println("\n🔁 Renommage de " + oldClassName + " -> " + newClassName + " ; variable -> " + newVarName);

            // Remplace le nom de la classe et le constructeur
            String newContent = content.replaceAll("\\bpublic\\s+class\\s+" + oldClassName + "\\b", "public class " + newClassName);
            newContent = newContent.replaceAll("\\b" + oldClassName + "\\s*\\(", newClassName + "(");

            Files.write(file, newContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("✅ Fichier source modifié : " + file.getFileName());

            // Renomme le fichier
            Path newFile = file.resolveSibling(newClassName + ".java");
            Files.move(file, newFile, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("📄 Fichier renommé : " + oldClassName + ".java -> " + newClassName + ".java");

            // Log
            logRename(oldClassName, newClassName, file.getFileName().toString(), newFile.getFileName().toString(), newVarName);

            // Mise à jour des références dans tout le projet
            updateReferences(oldClassName, newClassName, newVarName);

        } catch (Exception e) {
            System.out.println("Erreur sur " + file + " : " + e.getMessage());
        }
    }

    private String extractItemName(String content) {
        Matcher m1 = Pattern.compile("setTextureName\\(\"palamod:([^\"]+)\"\\)").matcher(content);
        if (m1.find()) return m1.group(1);
        Matcher m2 = Pattern.compile("setUnlocalizedName\\(\"([^\"]+)\"\\)").matcher(content);
        if (m2.find()) return m2.group(1);
        return null;
    }

    private String getNewClassName(String itemName) {
        String[] parts = itemName.split("_");
        StringBuilder sb = new StringBuilder("Item");
        for (String part : parts) sb.append(capitalize(part));
        return sb.toString();
    }

    private String getNewVarName(String itemName) {
        String[] parts = itemName.split("_");
        if (parts.length == 1) return parts[0].toLowerCase();
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) sb.append(capitalize(parts[i]));
        return sb.toString();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void logRename(String oldClass, String newClass, String oldFile, String newFile, String varName) throws IOException {
        List<String> lines = Arrays.asList(
            "[" + new java.util.Date() + "]",
            "Class: " + oldClass + " => " + newClass,
            "File:  " + oldFile + " => " + newFile,
            "Var:   " + varName,
            "----------------------------------"
        );
        Files.write(logFile, lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void updateReferences(String oldClass, String newClass, String newVar) throws IOException {
        Pattern varDecl = Pattern.compile("\\b" + oldClass + "\\s+(\\w+)\\s*=");
        Pattern instanciation = Pattern.compile("new\\s+" + oldClass + "\\s*\\(");
        Pattern typeParam = Pattern.compile("\\b" + oldClass + "\\b(?=\\s+[\\w]+\\s*[),;])");
        Pattern genericUse = Pattern.compile("\\b" + oldClass + "\\b");

        Files.walk(projectRoot)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(refFile -> {
                try {
                    String refContent = new String(Files.readAllBytes(refFile), java.nio.charset.StandardCharsets.UTF_8);
                    String original = refContent;

                    refContent = varDecl.matcher(refContent).replaceAll(newClass + " " + newVar + " =");
                    refContent = instanciation.matcher(refContent).replaceAll("new " + newClass + "(");
                    refContent = typeParam.matcher(refContent).replaceAll(newClass);
                    refContent = genericUse.matcher(refContent).replaceAll(newClass);

                    if (!refContent.equals(original)) {
                        Files.write(refFile, refContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        System.out.println("✏️ Références mises à jour dans : " + refFile.getFileName());
                    }
                } catch (Exception e) {
                    System.out.println("Erreur update ref " + refFile + " : " + e.getMessage());
                }
            });
    }

    public static void main(String[] args) throws IOException {
        // À adapter selon ton projet
        String src = "src/main/java/fr/paladium/palamod/client/item";
        String projectRoot = "src/main/java";
        String logFile = "renaming-log.txt";
        new AutoClassRenamer(src, projectRoot, logFile).run();
    }
} 