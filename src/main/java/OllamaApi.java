import java.net.*;
import java.io.*;

public class OllamaApi {

    public static String askOllama(String model, String prompt) throws IOException {
        URL url = new URL("http://localhost:11434/api/generate");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        System.out.println("[Ollama API] Prompt envoyé:\n" + prompt);

        conn.setConnectTimeout(10000); // 10 sec
        conn.setReadTimeout(90000);    // 90 sec
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // Construction du JSON de requête
        String json = "{\"model\":\"" + model + "\",\"prompt\":\""
                + prompt.replace("\"","\\\"").replace("\n","\\n")
                + "\",\"stream\":false}";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes("UTF-8"));
        }

        int status = conn.getResponseCode();
        System.out.println("[Ollama API] Statut réponse HTTP : " + status);
        if (status != 200) {
            throw new IOException("Ollama HTTP " + status);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
        }
        String raw = sb.toString();
        System.out.println("[Ollama API] Retour complet (max 500c) :\n"
                + (raw.length() > 500 ? raw.substring(0, 500) + "..." : raw));

        // ✅ Extraction propre du champ "response" du JSON retourné par Ollama.
        // Le format ressemble à :
        // { "model": "...", "created_at": "...", "response": "....", "done": true, ... }

        try {
            int keyIndex = raw.indexOf("\"response\"");
            if (keyIndex != -1) {
                // Cherche le ':' après "response"
                int colon = raw.indexOf(':', keyIndex);
                if (colon != -1) {
                    // Premier guillemet qui ouvre la chaîne du champ "response"
                    int firstQuote = raw.indexOf('"', colon + 1);
                    if (firstQuote != -1) {
                        StringBuilder resp = new StringBuilder();
                        boolean escaped = false;
                        for (int i = firstQuote + 1; i < raw.length(); i++) {
                            char c = raw.charAt(i);
                            if (escaped) {
                                // Gestion minimale des séquences d'échappement importantes
                                switch (c) {
                                    case 'n': resp.append('\n'); break;
                                    case 't': resp.append('\t'); break;
                                    case 'r': resp.append('\r'); break;
                                    case '"': resp.append('"');  break;
                                    case '\\': resp.append('\\'); break;
                                    default: resp.append(c); break;
                                }
                                escaped = false;
                            } else if (c == '\\') {
                                escaped = true;
                            } else if (c == '"') {
                                // Fin de la valeur du champ "response"
                                break;
                            } else {
                                resp.append(c);
                            }
                        }
                        return resp.toString();
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("[Ollama API] Erreur lors de l'extraction du champ response: " + ex.getMessage());
        }

        // Si on n'a pas réussi à extraire "response", on renvoie le JSON brut
        return raw;
    }
}
