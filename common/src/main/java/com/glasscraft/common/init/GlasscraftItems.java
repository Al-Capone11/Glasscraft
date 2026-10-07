package com.glasscraft.common.init;

import com.glasscraft.common.Glasscraft;
import com.glasscraft.common.item.GlasscutterItem;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Tool;

import java.util.List;
import java.util.function.Supplier;

/**
 * Registro del Glasscutter. Dos rutas:
 * - Fabric: registerDirect() en el entrypoint (los registros siguen abiertos ahi).
 * - NeoForge: el loader crea un DeferredRegister y bind()ea el supplier; el
 *   registro directo en NeoForge crashea con "Registry is already frozen"
 *   porque el constructor @Mod corre DESPUES del registro de items.
 */
public final class GlasscraftItems {

    public static final ResourceKey<Item> GLASSCUTTER_KEY =
        ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Glasscraft.MOD_ID, "glasscutter"));

    /** Tag de bloques que el Glasscutter corta con drops garantizados. */
    public static final TagKey<net.minecraft.world.level.block.Block> GLASSCUTTER_EFFECTIVE =
        TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Glasscraft.MOD_ID, "glasscutter_effective"));

    private static volatile Supplier<Item> holder = () -> null;

    private GlasscraftItems() {}

    /** Fabric: registro directo durante onInitialize. */
    public static void registerDirect() {
        if (holder.get() != null) return;
        Item item = new GlasscutterItem(buildProperties());
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, GLASSCUTTER_KEY, item);
        bind(() -> item);
    }

    /** NeoForge: el DeferredRegister invoca este supplier durante el registro. */
    public static Item buildForDeferred() {
        return new GlasscutterItem(buildProperties());
    }

    /** Establece el supplier del item ya registrado (desde cualquier loader). */
    public static void bind(Supplier<Item> supplier) {
        holder = supplier;
    }

    public static Item glasscutter() {
        return holder.get();
    }

    public static boolean isGlasscutter(Item item) {
        return item != null && item == holder.get();
    }

    private static Item.Properties buildProperties() {
        // Patron vanilla 26.2 (ToolMaterial.applyToolProperties): resolver el
        // HolderSet del tag en el momento del bootstrap del registro.
        HolderGetter<net.minecraft.world.level.block.Block> blocks =
            BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        HolderSet.Named<net.minecraft.world.level.block.Block> effective =
            blocks.getOrThrow(GLASSCUTTER_EFFECTIVE);

        float speed = Glasscraft.config().item.miningSpeed;   // default 6.0 = pico de hierro
        int durability = Glasscraft.config().item.durability;  // 250 = pico de hierro

        Tool tool = new Tool(
            List.of(Tool.Rule.minesAndDrops(effective, speed)),
            1.0f,   // defaultMiningSpeed fuera del tag
            1,      // damagePerBlock
            true    // canDestroyBlocksInCreative
        );

        return new Item.Properties()
            .setId(GLASSCUTTER_KEY)
            .durability(durability)
            .component(DataComponents.TOOL, tool)
            .repairable(net.minecraft.tags.ItemTags.IRON_TOOL_MATERIALS)
            .enchantable(14); // enchantability de hierro
    }
}
