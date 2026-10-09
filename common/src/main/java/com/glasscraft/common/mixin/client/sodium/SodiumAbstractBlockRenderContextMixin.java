package com.glasscraft.common.mixin.client.sodium;

import com.glasscraft.common.client.ctm.CtmContext;
import com.glasscraft.common.client.ctm.CtmDebug;
import com.glasscraft.common.client.ctm.CtmResolver;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.state.BlockState;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CTM para el pipeline de terreno de Sodium 0.9.x.
 *
 * Dos modos segun la forma del bloque:
 * - CUBO (vidrio, glowstone, linterna): contrae el borde de la cara en cada
 *   direccion PERPENDICULAR conectada (up/down/left/right de la cara).
 * - PANEL (glass_pane): el quad-poste mira HACIA el vecino (E/W). Si conecta
 *   en el eje de la propia cara, se ESTIRA el quad hasta el borde del bloque
 *   (de 14px a 16px de ancho), cubriendo la junta; ademas su AO se apaga.
 *   La rama de paneles solo aplica a bloques no-occluders con la misma cara.
 */
@Mixin(net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext.class)
abstract class SodiumAbstractBlockRenderContextMixin {

    @Shadow(remap = false) protected BlockState state;
    @Shadow(remap = false) protected BlockPos pos;
    @Shadow(remap = false) protected BlockAndTintGetter level;

    @Inject(method = "renderQuad", at = @At("HEAD"), cancellable = true)
    private void glasscraft$shrinkSodiumQuad(MutableQuadViewImpl quad, CallbackInfo ci) {
        if (state == null || pos == null || level == null) return;
        CtmDebug.beginCalls.incrementAndGet();
        if (!CtmResolver.isEnabled(state)) return;
        CtmDebug.activeBlocks.incrementAndGet();

        CtmContext ctx = CtmContext.get();
        ctx.level = level;
        ctx.pos = pos;
        ctx.state = state;
        ctx.connectionMask = -1;

        int mask = ctx.mask();
        CtmDebug.quadsTotal.incrementAndGet();
        if (mask == 0) return;

        Direction face = quad.getNominalFace();
        if (face == null) return;

        boolean isPaneLike = !state.canOcclude();
        if (isPaneLike && CtmResolver.isVanillaPane(state)) {
            glasscraft$paneStretch(quad, face, mask);
            return;
        }
        glasscraft$cubeShrink(quad, face, mask);
    }

    /**
     * PANEL: si conecta en la direccion de la propia cara (el vecino esta
     * delante/detras del quad-poste), estirar el quad en ese eje hasta el
     * borde del bloque (de 2/16 a 4/16 de ancho de poste) para cubrir la
     * junta. Contrae tambien el lado opuesto si conecta alli.
     */
    private void glasscraft$paneStretch(MutableQuadViewImpl quad, Direction face, int mask) {
        // eje de la cara: NORTH/SOUTH -> eje Z; EAST/WEST -> eje X.
        boolean frontConnected = (mask & (1 << face.getOpposite().ordinal())) != 0;
        boolean backConnected = (mask & (1 << face.ordinal())) != 0;
        if (!frontConnected && !backConnected) return;

        // El quad-poste se estira en su eje transversal (el ancho del poste).
        // Para face EAST/WEST el poste es una tira en X; para NORTH/SOUTH en Z.
        // UVs: estirar el bounding box en el eje correspondiente.
        float qu0 = Float.MAX_VALUE, qu1 = -Float.MAX_VALUE;
        float qv0 = Float.MAX_VALUE, qv1 = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float u = quad.getTexU(i);
            float v = quad.getTexV(i);
            qu0 = Math.min(qu0, u); qu1 = Math.max(qu1, u);
            qv0 = Math.min(qv0, v); qv1 = Math.max(qv1, v);
        }

        // Eje de estiramiento: para caras N/S, la tira corre en U (textura horizontal).
        // Simplificacion robusta: estirar AMBOS ejes proporcionalmente al
        // bounding box, expandiendo 1/16 hacia cada lado conectado.
        float su = (qu1 - qu0);
        float sv = (qv1 - qv0);
        float nu0 = qu0, nu1 = qu1, nv0 = qv0, nv1 = qv1;
        if (face.getAxis() == Direction.Axis.X) {
            // cara E/W: el poste corre verticalmente; estirar V si conecta arriba/abajo
            if ((mask & (1 << Direction.UP.ordinal())) != 0) nv0 = qv0 - sv;
            if ((mask & (1 << Direction.DOWN.ordinal())) != 0) nv1 = qv1 + sv;
            // y estirar U hacia el vecino frontal/trasero (mismo plano de textura)
            if (frontConnected) nu0 = qu0 - su * 0.5f;
            if (backConnected) nu1 = qu1 + su * 0.5f;
        } else {
            if ((mask & (1 << Direction.UP.ordinal())) != 0) nv0 = qv0 - sv;
            if ((mask & (1 << Direction.DOWN.ordinal())) != 0) nv1 = qv1 + sv;
            if (frontConnected) nu1 = qu1 + su * 0.5f;
            if (backConnected) nu0 = qu0 - su * 0.5f;
        }

        for (int i = 0; i < 4; i++) {
            float u = quad.getTexU(i);
            float v = quad.getTexV(i);
            float fu = (u - qu0) / (qu1 - qu0);
            float fv = (v - qv0) / (qv1 - qv0);
            quad.setUV(i, nu0 + fu * (nu1 - nu0), nv0 + fv * (nv1 - nv0));
        }
        quad.setAmbientOcclusion(TriState.FALSE);
        CtmDebug.quadsModified.incrementAndGet();
        CtmDebug.sample("pane");
    }

    /**
     * CUBO: contrae 1/16 del lado conectado dentro del bounding box UV del quad.
     */
    private void glasscraft$cubeShrink(MutableQuadViewImpl quad, Direction face, int mask) {
        boolean up = (mask & (1 << upOf(face).ordinal())) != 0;
        boolean down = (mask & (1 << downOf(face).ordinal())) != 0;
        boolean left = (mask & (1 << leftOf(face).ordinal())) != 0;
        boolean right = (mask & (1 << rightOf(face).ordinal())) != 0;
        if (!up && !down && !left && !right) return;

        float qu0 = Float.MAX_VALUE, qu1 = -Float.MAX_VALUE;
        float qv0 = Float.MAX_VALUE, qv1 = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float u = quad.getTexU(i);
            float v = quad.getTexV(i);
            qu0 = Math.min(qu0, u); qu1 = Math.max(qu1, u);
            qv0 = Math.min(qv0, v); qv1 = Math.max(qv1, v);
        }
        float su = (qu1 - qu0) * (1.0f / 16.0f);
        float sv = (qv1 - qv0) * (1.0f / 16.0f);
        float nu0 = qu0 + (left ? su : 0);
        float nu1 = qu1 - (right ? su : 0);
        float nv0 = qv0 + (up ? sv : 0);
        float nv1 = qv1 - (down ? sv : 0);

        for (int i = 0; i < 4; i++) {
            float u = quad.getTexU(i);
            float v = quad.getTexV(i);
            float fu = (u - qu0) / (qu1 - qu0);
            float fv = (v - qv0) / (qv1 - qv0);
            quad.setUV(i, nu0 + fu * (nu1 - nu0), nv0 + fv * (nv1 - nv0));
        }
        quad.setAmbientOcclusion(TriState.FALSE);
        CtmDebug.quadsModified.incrementAndGet();
        CtmDebug.sample("cube");
    }

    private static Direction upOf(Direction face) {
        return switch (face) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    private static Direction downOf(Direction face) {
        return upOf(face).getOpposite();
    }

    private static Direction leftOf(Direction face) {
        return switch (face) {
            case UP -> Direction.WEST;
            case DOWN -> Direction.EAST;
            default -> face.getClockWise();
        };
    }

    private static Direction rightOf(Direction face) {
        return leftOf(face).getOpposite();
    }
}
