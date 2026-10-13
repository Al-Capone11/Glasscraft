package com.glasscraft.common.init;

import com.glasscraft.common.Glasscraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Pestaña creativa de Glasscraft. En 26.2 las tabs se registran como entradas
 * del registro CREATIVE_MODE_TAB (como items). Fabric: registro directo;
 * NeoForge: DeferredRegister + bind (mismo patron que los items).
 *
 * DisplayItemsGenerator se implementa con clase anonima (no lambda) para
 * referenciar Output sin problemas de acceso del compilador.
 */
public final class GlasscraftCreativeTab {

    public static final ResourceKey<CreativeModeTab> TAB_KEY =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(Glasscraft.MOD_ID, "glasscraft"));

    private static volatile Supplier<CreativeModeTab> holder = () -> null;

    private GlasscraftCreativeTab() {}

    public static CreativeModeTab build() {
        CreativeModeTab.Builder builder = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.glasscraft"))
            .icon(() -> new ItemStack(GlasscraftItems.glasscutter()));

        builder.displayItems(new CreativeModeTab.DisplayItemsGenerator() {
            @Override
            public void accept(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
                output.accept(new ItemStack(GlasscraftItems.glasscutter()));
                output.accept(new ItemStack(GlasscraftItems.glasscutterHead()));
            }
        });

        return builder.build();
    }

    /** Fabric: registro directo durante onInitialize (despues de los items). */
    public static void registerDirect() {
        if (holder.get() != null) return;
        CreativeModeTab tab = build();
        net.minecraft.core.Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, tab);
        holder = () -> tab;
    }

    public static void bind(Supplier<CreativeModeTab> supplier) {
        holder = supplier;
    }
}
