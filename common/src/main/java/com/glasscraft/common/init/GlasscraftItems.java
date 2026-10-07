package com.glasscraft.common.init;

import com.glasscraft.common.Glasscraft;
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

/**
 * Registro del Glasscutter. Se llama desde el entrypoint de cada loader DURANTE
 * el bootstrap del registro de items (26.2 exige setId ANTES de super()).
 */
public final class GlasscraftItems {

    public static final ResourceKey<Item> GLASSCUTTER_KEY =
        ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Glasscraft.MOD_ID, "glasscutter"));

    /** Tag de bloques que el Glasscutter corta con drops garantizados. */
    public static final TagKey<net.minecraft.world.level.block.Block> GLASSCUTTER_EFFECTIVE =
        TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Glasscraft.MOD_ID, "glasscutter_effective"));

    private static Item glasscutter;

    private GlasscraftItems() {}

    public static void register() {
        if (glasscutter != null) return;

        // Patron vanilla 26.2 (ToolMaterial.applyToolProperties): resolver el HolderSet
        // del tag en el momento del bootstrap.
        HolderGetter<net.minecraft.world.level.block.Block> blocks =
            BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        HolderSet.Named<net.minecraft.world.level.block.Block> effective =
            blocks.getOrThrow(GLASSCUTTER_EFFECTIVE);

        float speed = Glasscraft.config().item.miningSpeed;   // 12.0 = pico de oro
        int durability = Glasscraft.config().item.durability;  // 250 = pico de hierro

        Tool tool = new Tool(
            List.of(Tool.Rule.minesAndDrops(effective, speed)),
            1.0f,   // defaultMiningSpeed fuera del tag
            1,      // damagePerBlock
            true    // canDestroyBlocksInCreative
        );

        Item.Properties properties = new Item.Properties()
            .setId(GLASSCUTTER_KEY)
            .durability(durability)
            .component(DataComponents.TOOL, tool)
            .repairable(net.minecraft.tags.ItemTags.IRON_TOOL_MATERIALS)
            .enchantable(14); // enchantability de hierro

        glasscutter = new Item(properties);
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, GLASSCUTTER_KEY, glasscutter);
    }

    public static Item glasscutter() {
        return glasscutter;
    }
}
