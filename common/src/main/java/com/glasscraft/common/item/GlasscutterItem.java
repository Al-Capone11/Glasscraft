package com.glasscraft.common.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
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

    /**
     * Compatibilidad de primera persona con Locomotion.
     *
     * Locomotion elige la pose de la mano evaluando predicados sobre el
     * ItemStack (FirstPersonHandPoses.testForNextHandPose, ordenados por
     * prioridad). Los predicados de herramienta son tags vanilla
     * (#minecraft:pickaxes y compañia), que NO podemos usar: meter el
     * Glasscutter ahi le daria comportamientos vanilla que no son suyos
     * (romper macetas decoradas, encantamientos de mineria, Silk Touch).
     *
     * Pero hay un predicado que SI controlamos desde nuestro propio item: la
     * animacion de uso. Locomotion mapea getUseAnimation()==SPEAR a su pose
     * SPEAR (prioridad 100 > 0 del GENERIC_ITEM por defecto), y esa pose
     * renderiza el item con ItemRenderType.THIRD_PERSON_ITEM, es decir
     * ItemDisplayContext.THIRD_PERSON_RIGHT_HAND — el contexto "en la mano",
     * que si controlamos con thirdperson_righthand. Sin el, caia en
     * FIXED_ITEM -> ON_SHELF, un contexto pensado para exponer el item en
     * una estanteria, y por eso la herramienta se veia flotando sobre la mano.
     *
     * Sin Locomotion esta animacion nunca se usa (el item no tiene duracion
     * de uso, asi que nunca entra en estado "usando"), de modo que el render
     * vanilla no cambia: el override solo actua cuando el mod esta presente.
     */
    private static final boolean LOCOMOTION_PRESENT = detectLocomotion();

    private static boolean detectLocomotion() {
        try {
            Class.forName("com.trainguy9512.locomotion.LocomotionMain", false,
                GlasscutterItem.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        // Ver LOCOMOTION_PRESENT arriba: solo para la pose de mano de Locomotion.
        return LOCOMOTION_PRESENT ? ItemUseAnimation.SPEAR : super.getUseAnimation(stack);
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
