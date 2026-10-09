package com.glasscraft.common.mixin.client.sodium;

import com.glasscraft.common.client.ctm.CtmContext;
import com.glasscraft.common.client.ctm.CtmDebug;
import com.glasscraft.common.client.ctm.CtmResolver;
import com.glasscraft.common.client.ctm.CtmUvShrink;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Vector2f;

/**
 * CTM para el pipeline de terreno de Sodium 0.9.x (mc 26.2): su BlockRenderer
 * NO pasa por ModelBlockRenderer de vanilla — pide los quads via
 * PlatformModelAccess.getQuads y los procesa como MutableQuadViewImpl.
 * Este mixin cubre ese camino: renderQuad es el cuello de botella comun de
 * todos los quads de terreno, con state/pos/level en el contexto.
 */
@Mixin(net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext.class)
abstract class SodiumAbstractBlockRenderContextMixin {

    @Shadow(remap = false) protected BlockState state;
    @Shadow(remap = false) protected BlockPos pos;
    @Shadow(remap = false) protected BlockAndTintGetter level;

    @Inject(method = "renderQuad", at = @At("HEAD"), cancellable = true)
    private void glasscraft$shrinkSodiumQuad(MutableQuadViewImpl quad, CallbackInfo ci) {
        CtmDebug.beginCalls++;
        if (state == null || pos == null || level == null) return;
        if (CtmResolver.isVanillaPane(state)) return;
        if (!CtmResolver.isEnabled(state)) return;
        CtmDebug.activeBlocks++;

        CtmContext ctx = CtmContext.get();
        ctx.level = level;
        ctx.pos = pos;
        ctx.state = state;
        ctx.connectionMask = -1;

        int mask = ctx.mask();
        CtmDebug.quadsTotal++;
        if (mask == 0) return;

        net.minecraft.core.Direction face = quad.getNominalFace();
        if (face == null) return;

        // Contraer UVs del quad mutable directamente.
        var sprite = quad.cachedSprite();
        if (sprite == null) return;
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();
        float su = (u1 - u0) * (1.0f / 16.0f);
        float sv = (v1 - v0) * (1.0f / 16.0f);

        boolean up = CtmUvShrink.connects(mask, upOf(face));
        boolean down = CtmUvShrink.connects(mask, downOf(face));
        boolean left = CtmUvShrink.connects(mask, leftOf(face));
        boolean right = CtmUvShrink.connects(mask, rightOf(face));
        if (!up && !down && !left && !right) return;

        float nu0 = u0 + (left ? su : 0);
        float nu1 = u1 - (right ? su : 0);
        float nv0 = v0 + (up ? sv : 0);
        float nv1 = v1 - (down ? sv : 0);

        for (int i = 0; i < 4; i++) {
            float u = quad.getTexU(i);
            float v = quad.getTexV(i);
            float fu = (u - u0) / (u1 - u0);
            float fv = (v - v0) / (v1 - v0);
            quad.setUV(i, nu0 + fu * (nu1 - nu0), nv0 + fv * (nv1 - nv0));
        }
        CtmDebug.quadsModified++;
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
