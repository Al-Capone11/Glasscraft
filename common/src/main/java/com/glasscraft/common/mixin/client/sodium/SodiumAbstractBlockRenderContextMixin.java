package com.glasscraft.common.mixin.client.sodium;

import com.glasscraft.common.client.ctm.CtmContext;
import com.glasscraft.common.client.ctm.CtmDebug;
import com.glasscraft.common.client.ctm.CtmResolver;
import com.glasscraft.common.client.ctm.CtmUvShrink;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.state.BlockState;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CTM para el pipeline de terreno de Sodium 0.9.x: renderQuad es el cuello de
 * botella de todos los quads de terreno, con state/pos/level en el contexto.
 *
 * Shrink PROPORCIONAL AL PROPIO QUAD (bounding box de sus UVs): funciona
 * igual para cubos (vidrio) y paneles (UVs sub-tile). Al conectar, se
 * desactiva el AO del quad (setAmbientOcclusion(FALSE)): el AO de vanilla
 * oscurece las esquinas y dibuja lineas en las juntas (lampara marina).
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
        String path = null;
        if (CtmDebug.seenStates.size() < 300 || CtmDebug.activeStates.size() < 50) {
            path = state.getBlock().builtInRegistryHolder().key().identifier().getPath();
            CtmDebug.seeState(path);
        }
        if (!CtmResolver.isEnabled(state)) return;
        CtmDebug.activeBlocks.incrementAndGet();
        if (path != null) CtmDebug.seeActive(path);

        CtmContext ctx = CtmContext.get();
        ctx.level = level;
        ctx.pos = pos;
        ctx.state = state;
        ctx.connectionMask = -1;

        int mask = ctx.mask();
        CtmDebug.quadsTotal.incrementAndGet();
        if (mask == 0) return;

        net.minecraft.core.Direction face = quad.getNominalFace();
        if (face == null) return;

        boolean up = CtmUvShrink.connects(mask, upOf(face));
        boolean down = CtmUvShrink.connects(mask, downOf(face));
        boolean left = CtmUvShrink.connects(mask, leftOf(face));
        boolean right = CtmUvShrink.connects(mask, rightOf(face));
        if (!up && !down && !left && !right) return;
        CtmDebug.maskNonZero.incrementAndGet();
        if (path != null) CtmDebug.seeConnected(path);

        // Bounding box de las UVs DEL QUAD (sub-tile en paneles, tile entero
        // en cubos): contraer 1/16 del lado conectado dentro de esa caja.
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
        CtmDebug.sample("sodium");
    }

    private static net.minecraft.core.Direction upOf(net.minecraft.core.Direction face) {
        return switch (face) {
            case UP -> net.minecraft.core.Direction.NORTH;
            case DOWN -> net.minecraft.core.Direction.SOUTH;
            default -> net.minecraft.core.Direction.UP;
        };
    }

    private static net.minecraft.core.Direction downOf(net.minecraft.core.Direction face) {
        return upOf(face).getOpposite();
    }

    private static net.minecraft.core.Direction leftOf(net.minecraft.core.Direction face) {
        return switch (face) {
            case UP -> net.minecraft.core.Direction.WEST;
            case DOWN -> net.minecraft.core.Direction.EAST;
            default -> face.getClockWise();
        };
    }

    private static net.minecraft.core.Direction rightOf(net.minecraft.core.Direction face) {
        return leftOf(face).getOpposite();
    }
}
