package com.glasscraft.common.mixin.client.sodium;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Plugin de config de mixins de Sodium: solo aplica si Sodium esta presente.
 *
 * La deteccion JAMAS usa Class.forName — cargar la clase objetivo antes de
 * que Mixin la transforme dispara MixinTargetAlreadyLoadedException en los
 * mixins de OTROS mods (Iris mixinea AbstractBlockRenderContext con
 * required=true). Se detecta por RECURSOS en el classpath:
 * 1) el .class objetivo (funciona en NeoForge/FML),
 * 2) fabric.mod.json de sodium (KnotClassLoader de Fabric resuelve los
 *    recursos de mods por nombre de mod jar; el .class dentro del jar
 *    tambien deberia verse, pero el fabric.mod.json es mas fiable porque
 *    Knot lo consulta al descubrir mods).
 */
public class GlasscraftSodiumMixinPlugin implements IMixinConfigPlugin {

    private static Boolean cachedPresent;

    private static boolean sodiumPresent() {
        if (cachedPresent == null) {
            ClassLoader cl = GlasscraftSodiumMixinPlugin.class.getClassLoader();
            boolean found = cl.getResource("net/caffeinemc/mods/sodium/client/render/model/AbstractBlockRenderContext.class") != null
                || cl.getResource("fabric.mod.json") != null && hasSodiumMarker(cl);
            if (!found) {
                // ultimo recurso: listar resources con getResources (algunos
                // classloaders solo exponen via enumeracion)
                try {
                    java.util.Enumeration<java.net.URL> urls = cl.getResources("sodium.mixins.json");
                    found = urls != null && urls.hasMoreElements();
                } catch (Exception ignored) {}
            }
            cachedPresent = found;
        }
        return cachedPresent;
    }

    private static boolean hasSodiumMarker(ClassLoader cl) {
        // fabric.mod.json del PROPIO classloader: no dice nada de sodium.
        // Estrategia segura: obtener todos los fabric.mod.json visibles.
        try {
            java.util.Enumeration<java.net.URL> urls = cl.getResources("fabric.mod.json");
            while (urls != null && urls.hasMoreElements()) {
                java.net.URL url = urls.nextElement();
                try (java.io.InputStream is = url.openStream()) {
                    byte[] buf = is.readAllBytes();
                    String s = new String(buf, java.nio.charset.StandardCharsets.UTF_8);
                    if (s.contains("\"id\":\\s*\"sodium\"") || s.contains("\"id\": \"sodium\"") || s.contains("\"id\":\"sodium\"")) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return sodiumPresent();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
