package com.glasscraft.common.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Glasscutter. Un Item normal de 26.2 (todo va por data components), con una
 * unica pieza de logica propia: cuando isBroken() (durabilidad agotada,
 * gracias al mixin applyDamage que evita el shrink) NO rompe nada —
 * velocidad 0 y sin drops correctos. Se repara en yunque con hierro.
 *
 * La pose en mano con Locomotion NO se resuelve aca: este item esta en el tag
 * vanilla #minecraft:pickaxes (ver data/minecraft/tags/item/pickaxes.json), y
 * por eso Locomotion le da la misma pose de herramienta que a un pico (agarre
 * + animacion de minado, mano a escala normal). Ese tag es tambien la razon
 * por la que NO debe volver a activarse el override de getUseAnimation: la
 * pose SPEAR tiene prioridad 100 y le ganaria a la de pico (60).
 *
 * GlasscraftHeldItem (el override de getUseAnimation) queda para los items
 * que NO pueden estar en ese tag, como la cabeza.
 */
public class GlasscutterItem extends Item {

    public GlasscutterItem(Properties properties) {
        super(properties);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, net.minecraft.world.level.block.state.BlockState state) {
        // Roto = inutilizable: velocidad 0 -> el bloque nunca progresa.
        if (stack.isBroken()) {
            return 0.0f;
        }
        return super.getDestroySpeed(stack, state);
    }

    /**
     * La barra vanilla (round(13 - damage*13/max), verificado en bytecode)
     * llega a 0 px con ~4% de durabilidad restante: con 250 son ~9 usos en
     * los que la barra ya se ve vacia pero el item aun funciona. Alineamos
     * lo visual con lo funcional: minimo 1 px mientras sea usable, 0 solo
     * cuando isBroken().
     */
    @Override
    public int getBarWidth(ItemStack stack) {
        if (stack.isBroken()) {
            return 0;
        }
        return Math.max(1, super.getBarWidth(stack));
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, net.minecraft.world.level.block.state.BlockState state) {
        if (stack.isBroken()) {
            return false;
        }
        return super.isCorrectToolForDrops(stack, state);
    }

    @SuppressWarnings("deprecation") // appendHoverText: deprecado en 26.2 pero funcional
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (stack.isBroken()) {
            tooltip.accept(Component.translatable("item.glasscraft.glasscutter.broken")
                .withStyle(net.minecraft.ChatFormatting.RED));
        }
        tooltip.accept(Component.translatable("item.glasscraft.glasscutter.repair_hint")
            .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
