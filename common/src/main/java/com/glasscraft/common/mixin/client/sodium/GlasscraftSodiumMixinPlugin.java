package com.glasscraft.common.mixin.client.sodium;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * Plugin de config de mixins de Sodium: solo aplica si Sodium esta presente.
 *
 * JAMAS usar Class.forName sobre la CLASE OBJETIVO (AbstractBlockRenderContext)
 * — cargarla antes de que Mixin la transforme dispara
 * MixinTargetAlreadyLoadedException en los mixins de OTROS mods (Iris, required=true).
 *
 * Deteccion:
 * - Fabric: FabricLoader.isModLoaded("sodium") via reflexion suave (cargar la
 *   API del LOADER es seguro: no es target de mixin). El getResource simple
 *   no funciona bajo KnotClassLoader (0 lineas CTM en Fabric).
 * - NeoForge: getResource del .class objetivo (FML expone los jars al
 *   classloader y esta via funciona — verificado in-game).
 */
public class GlasscraftSodiumMixinPlugin implements IMixinConfigPlugin {

    private static Boolean cachedPresent;

    private static boolean sodiumPresent() {
        if (cachedPresent == null) {
            cachedPresent = detectViaFabricLoader() || detectViaResource();
        }
        return cachedPresent;
    }

    private static boolean detectViaFabricLoader() {
        try {
            Class<?> fl = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Method getInstance = fl.getMethod("getInstance");
            Method isModLoaded = fl.getMethod("isModLoaded", String.class);
            Object instance = getInstance.invoke(null);
            return (Boolean) isModLoaded.invoke(instance, "sodium");
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean detectViaResource() {
        try {
            return GlasscraftSodiumMixinPlugin.class.getClassLoader()
                .getResource("net/caffeinemc/mods/sodium/client/render/model/AbstractBlockRenderContext.class") != null;
        } catch (Throwable t) {
            return false;
        }
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
