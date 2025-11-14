import java.io.File;

/**
 * Gère le cache de décompilation.
 */
public class CacheManager {
    private final File cacheDir;
    
    public CacheManager(File cacheDir) {
        this.cacheDir = cacheDir;
    }
    
    /**
     * Vide le cache.
     */
    public void clearCache() {
        if (cacheDir.exists()) {
            for (File f : cacheDir.listFiles()) {
                if (f.isFile()) {
                    f.delete();
                }
            }
        }
    }
    
    /**
     * Vérifie si le cache existe.
     */
    public boolean cacheExists() {
        return cacheDir.exists();
    }
    
    /**
     * Crée le répertoire de cache s'il n'existe pas.
     */
    public void ensureCacheDir() {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }
}

