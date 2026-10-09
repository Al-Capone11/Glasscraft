package com.glasscraft.common.mixin.client.sodium;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Plugin de config de mixins de Sodium: solo aplica si Sodium esta presente.
 *
 * OJO: la deteccion NO usa Class.forName — cargar la clase objetivo con
 * Class.forName dispara MixinTargetAlreadyLoadedException en los mixins de
 * OTROS mods (Iris mixinea AbstractBlockRenderContext): si la clase ya esta
 * cargada sin transformar, Mixin no puede aplicarse. Se detecta por la
 * existencia del RECURSO .class en el classpath (getResource no carga la
 * clase, solo la localiza).
 */
public class GlasscraftSodiumMixinPlugin implements IMixinConfigPlugin {

    private static Boolean cachedPresent;

    private static boolean sodiumPresent() {
        if (cachedPresent == null) {
            cachedPresent = GlasscraftSodiumMixinPlugin.class.getClassLoader()
                .getResource("net/caffeinemc/mods/sodium/client/render/model/AbstractBlockRenderContext.class") != null;
        }
        return cachedPresent;
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
