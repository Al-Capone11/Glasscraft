package com.glasscraft.common.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Base de los items de Glasscraft que se sostienen en la mano (Glasscutter y
 * su cabeza). Existe por una sola razon: que Locomotion los sostenga EN la
 * mano en vez de exponerlos sobre la palma.
 *
 * Locomotion elige la pose de la mano evaluando predicados sobre el ItemStack
 * (FirstPersonHandPoses.testForNextHandPose, ordenados por prioridad). Los
 * predicados de herramienta son tags vanilla (#minecraft:pickaxes y compania),
 * que NO podemos usar: meter estos items ahi les daria comportamientos vanilla
 * que no son suyos (romper macetas decoradas, encantamientos de mineria,
 * Silk Touch).
 *
 * Pero hay un predicado que SI controlamos desde nuestro propio item: la
 * animacion de uso. Locomotion mapea getUseAnimation()==SPEAR a su pose SPEAR
 * (prioridad 100 > 0 del GENERIC_ITEM por defecto), y esa pose renderiza el
 * item con ItemRenderType.THIRD_PERSON_ITEM, es decir
 * ItemDisplayContext.THIRD_PERSON_RIGHT_HAND — el contexto "en la mano", que
 * controlamos con thirdperson_righthand. Sin esto caen en
 * FirstPersonGenericItems.FIXED_ITEM -> ON_SHELF, el contexto de las
 * ESTANTERIAS, y por eso se veian flotando sobre la mano.
 *
 * Sin Locomotion esta animacion nunca se usa (ninguno de estos items tiene
 * duracion de uso, asi que nunca entran en estado "usando"), de modo que el
 * render vanilla no cambia: el override solo actua cuando el mod esta
 * presente.
 */
public class GlasscraftHeldItem extends Item {

    public GlasscraftHeldItem(Properties properties) {
        super(properties);
    }

    private static final boolean LOCOMOTION_PRESENT = detectLocomotion();

    private static boolean detectLocomotion() {
        try {
            Class.forName("com.trainguy9512.locomotion.LocomotionMain", false,
                GlasscraftHeldItem.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        // Ver el javadoc de la clase: solo sirve como predicado de pose de Locomotion.
        return LOCOMOTION_PRESENT ? ItemUseAnimation.SPEAR : super.getUseAnimation(stack);
    }
}
