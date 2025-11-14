import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.util.*;

/**
 * Gère l'indexation et la recherche des références entre classes.
 */
public class ReferenceManager {
    private final Map<String, Set<String>> referencesTo = new HashMap<>();
    private final Map<String, Set<String>> referenceIndex = new HashMap<>();
    
    /**
     * Indexe les références d'une classe.
     */
    public void indexClassReferences(String className, byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
            cr.accept(cn, 0);
            Set<String> referenced = new HashSet<>();
            
            // Superclass
            if (cn.superName != null) referenced.add(cn.superName.replace('/', '.') + ".class");
            
            // Interfaces
            if (cn.interfaces != null) {
                for (Object iface : cn.interfaces) {
                    referenced.add(iface.toString().replace('/', '.') + ".class");
                }
            }
            
            // Fields
            if (cn.fields != null) {
                for (Object f : cn.fields) {
                    FieldNode fn = (FieldNode) f;
                    if (fn.desc != null && fn.desc.contains("/")) {
                        referenced.add(fn.desc.replace('/', '.') + ".class");
                    }
                }
            }
            
            // Methods
            if (cn.methods != null) {
                for (Object m : cn.methods) {
                    MethodNode mn = (MethodNode) m;
                    if (mn.desc != null && mn.desc.contains("/")) {
                        referenced.add(mn.desc.replace('/', '.') + ".class");
                    }
                    if (mn.instructions != null) {
                        for (AbstractInsnNode insn : mn.instructions) {
                            if (insn instanceof TypeInsnNode) {
                                String ref = ((TypeInsnNode) insn).desc.replace('/', '.') + ".class";
                                referenced.add(ref);
                            } else if (insn instanceof MethodInsnNode) {
                                String ref = ((MethodInsnNode) insn).owner.replace('/', '.') + ".class";
                                referenced.add(ref);
                            } else if (insn instanceof FieldInsnNode) {
                                String ref = ((FieldInsnNode) insn).owner.replace('/', '.') + ".class";
                                referenced.add(ref);
                            }
                        }
                    }
                }
            }
            
            for (String ref : referenced) {
                if (!referencesTo.containsKey(ref)) {
                    referencesTo.put(ref, new HashSet<>());
                }
                referencesTo.get(ref).add(className);
            }
        } catch (Exception e) {
            // Ignore les erreurs d'analyse ASM
        }
    }
    
    /**
     * Construit l'index des références pour Find Usages.
     */
    public void buildReferenceIndexFast(Map<String, byte[]> classBytes) {
        referenceIndex.clear();
        for (String classKey : classBytes.keySet()) {
            try {
                ClassReader cr = new ClassReader(classBytes.get(classKey));
                org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
                cr.accept(cn, 0);
                Set<String> referenced = new HashSet<>();
                
                // Superclass
                if (cn.superName != null) referenced.add(cn.superName.replace('/', '.'));
                
                // Interfaces
                if (cn.interfaces != null) {
                    for (Object iface : cn.interfaces) {
                        referenced.add(iface.toString().replace('/', '.'));
                    }
                }
                
                // Fields
                if (cn.fields != null) {
                    for (Object f : cn.fields) {
                        FieldNode fn = (FieldNode) f;
                        if (fn.desc != null && fn.desc.contains("/")) {
                            referenced.add(fn.desc.replace('/', '.'));
                        }
                    }
                }
                
                // Methods
                if (cn.methods != null) {
                    for (Object m : cn.methods) {
                        MethodNode mn = (MethodNode) m;
                        if (mn.desc != null && mn.desc.contains("/")) {
                            referenced.add(mn.desc.replace('/', '.'));
                        }
                        if (mn.instructions != null) {
                            for (AbstractInsnNode insn : mn.instructions) {
                                if (insn instanceof TypeInsnNode) {
                                    String ref = ((TypeInsnNode) insn).desc.replace('/', '.') + ".class";
                                    referenced.add(ref);
                                } else if (insn instanceof MethodInsnNode) {
                                    String ref = ((MethodInsnNode) insn).owner.replace('/', '.') + ".class";
                                    referenced.add(ref);
                                } else if (insn instanceof FieldInsnNode) {
                                    String ref = ((FieldInsnNode) insn).owner.replace('/', '.') + ".class";
                                    referenced.add(ref);
                                }
                            }
                        }
                    }
                }
                
                for (String ref : referenced) {
                    String simple = ref.contains(".") ? ref.substring(ref.lastIndexOf('.') + 1) : ref;
                    referenceIndex.computeIfAbsent(simple, k -> new HashSet<>()).add(classKey);
                }
            } catch (Exception e) {
                // Ignore les erreurs d'analyse ASM
            }
        }
    }
    
    public Map<String, Set<String>> getReferencesTo() {
        return referencesTo;
    }
    
    public Map<String, Set<String>> getReferenceIndex() {
        return referenceIndex;
    }
    
    public void clear() {
        referencesTo.clear();
        referenceIndex.clear();
    }
}

