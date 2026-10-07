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
