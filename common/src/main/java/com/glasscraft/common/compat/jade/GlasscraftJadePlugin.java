package com.glasscraft.common.compat.jade;

import com.glasscraft.common.Glasscraft;
import com.glasscraft.common.init.GlasscraftItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.JadeIds;
import snownee.jade.api.harvest.SimpleToolTier;
import snownee.jade.api.harvest.ToolType;
import snownee.jade.api.harvest.ToolTypeRegistry;

/**
 * Registro del Glasscutter como "correct tool" de Jade: al mirar un bloque
 * del tag glasscutter_effective, el overlay muestra el Glasscutter igual que
 * muestra un pico al mirar piedra.
 *
 * Patron copiado del VanillaPlugin de Jade (bytecode verificado): un tipo de
 * herramienta propio + un tier cuyo predicado es el Tool component del item
 * (via SimpleToolTier.isEffectiveTool, que respeta la config de materiales:
 * los bloques fuera del tag no matchean).
 */
public final class GlasscraftJadePlugin {

    private GlasscraftJadePlugin() {}

    public static void registerClient(IWailaClientRegistration registration) {
        registration.addHarvestPlugin(GlasscraftJadePlugin::registerHarvest);
    }

    private static void registerHarvest(ToolTypeRegistry registry) {
        ToolType type = registry.type(Identifier.fromNamespaceAndPath(Glasscraft.MOD_ID, "glasscutter"));
        ItemStack stack = new ItemStack(GlasscraftItems.glasscutter());
        // isCorrectTool usa el componente Tool del item (regla minesAndDrops
        // sobre glasscutter_effective): matchea exactamente lo que corta.
        type.addTier(new SimpleToolTier(
            Identifier.fromNamespaceAndPath(Glasscraft.MOD_ID, "glasscutter"),
            stack,
            SimpleToolTier.isEffectiveTool(stack)
        ));
    }
}
