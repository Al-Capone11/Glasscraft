package com.glasscraft.common.mixin.client.sodium;

import com.glasscraft.common.client.ctm.CtmContext;
import com.glasscraft.common.client.ctm.CtmDebug;
import com.glasscraft.common.client.ctm.CtmResolver;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
 * IMPORTANTE (bytecode verificado, FaceBakery.bakeVertex): las posiciones de
 * los quads en 26.2 estan NORMALIZADAS 0..1 (Vector3f.div(16)), NO 0..16
 * como en 1.21. Todas las comparaciones de borde usan 0..1.
 *
 * CUBOS: contrae 1/16 del tile del sprite en cada borde PERPENDICULAR
 * conectado (los vertices de ese borde retroceden 1/16 dentro del tile).
 *
 * PANELES: vanilla ya extiende la geometria al vecino al conectar; la junta
 * es el marco de 1px de la textura en el borde del sprite que apunta al
 * vecino. Se contrae ese borde (derivado por posicion geometrica) del tile.
 */
@Mixin(net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext.class)
abstract class SodiumAbstractBlockRenderContextMixin {

    private static final float EDGE = 0.001f;

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

        boolean paneLike = !state.canOcclude();
        if (paneLike) {
            glasscraft$paneShrink(quad, face, mask);
        } else {
            glasscraft$cubeShrink(quad, face, mask);
        }
    }

    /**
     * PANELES: contrae 1/16 del tile en el borde geometrico que apunta al
     * vecino conectado (la conexion de un panel esta en el EJE de la cara
     * del poste). Vertices en 0..1.
     */
    private void glasscraft$paneShrink(MutableQuadViewImpl quad, Direction face, int mask) {
        boolean front = (mask & (1 << face.getOpposite().ordinal())) != 0;
        boolean back = (mask & (1 << face.ordinal())) != 0;
        boolean up = (mask & (1 << Direction.UP.ordinal())) != 0;
        boolean down = (mask & (1 << Direction.DOWN.ordinal())) != 0;
        if (!front && !back && !up && !down) return;

        TextureAtlasSprite sprite = quad.cachedSprite();
        if (sprite == null) return;
        float su = (sprite.getU1() - sprite.getU0()) / 16.0f;
        float sv = (sprite.getV1() - sprite.getV0()) / 16.0f;

        Direction.Axis axis = face.getAxis();
        float[] c = new float[4];
        for (int i = 0; i < 4; i++) {
            c[i] = switch (axis) {
                case X -> quad.getX(i);
                case Y -> quad.getY(i);
                case Z -> quad.getZ(i);
            };
        }
        float minEdge = Math.min(Math.min(c[0], c[1]), Math.min(c[2], c[3]));
        float maxEdge = Math.max(Math.max(c[0], c[1]), Math.max(c[2], c[3]));

        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            float u = quad.getTexU(i);
            float v = quad.getTexV(i);
            float nu = u, nv = v;
            boolean thisChanged = false;
            if (front && Math.abs(c[i] - minEdge) < EDGE) {
                if (axis == Direction.Axis.X) {
                    nu = u + (face == Direction.WEST ? -su : su);
                } else {
                    nv = v + (face == Direction.SOUTH ? sv : -sv);
                }
                thisChanged = true;
            }
            if (back && Math.abs(c[i] - maxEdge) < EDGE) {
                if (axis == Direction.Axis.X) {
                    nu = u + (face == Direction.EAST ? su : -su);
                } else {
                    nv = v + (face == Direction.NORTH ? -sv : sv);
                }
                thisChanged = true;
            }
            // apilado vertical de paneles: contrae los bordes superior/inferior
            float y = quad.getY(i);
            if (up && Math.abs(y - 1.0f) < EDGE) { nv = v + (face.getAxis() == Direction.Axis.Y ? 0 : sv); thisChanged = true; }
            if (down && Math.abs(y - 0.0f) < EDGE) { nv = v - (face.getAxis() == Direction.Axis.Y ? 0 : sv); thisChanged = true; }
            if (thisChanged) {
                quad.setUV(i, nu, nv);
                changed = true;
            }
        }
        if (!changed) return;
        quad.setAmbientOcclusion(TriState.FALSE);
        CtmDebug.quadsModified.incrementAndGet();
        CtmDebug.sample("pane");
    }

    /**
     * CUBO: contrae 1/16 del tile en cada borde perpendicular conectado.
     * Coordenadas de vertice 0..1.
     */
    private void glasscraft$cubeShrink(MutableQuadViewImpl quad, Direction face, int mask) {
        boolean up = (mask & (1 << upOf(face).ordinal())) != 0;
        boolean down = (mask & (1 << downOf(face).ordinal())) != 0;
        boolean left = (mask & (1 << leftOf(face).ordinal())) != 0;
        boolean right = (mask & (1 << rightOf(face).ordinal())) != 0;
        if (!up && !down && !left && !right) return;

        TextureAtlasSprite sprite = quad.cachedSprite();
        if (sprite == null) return;
        float su = (sprite.getU1() - sprite.getU0()) / 16.0f;
        float sv = (sprite.getV1() - sprite.getV0()) / 16.0f;

        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            float u = quad.getTexU(i);
            float v = quad.getTexV(i);
            float nu = u, nv = v;
            boolean thisChanged = false;
            float hCoord, vCoord;
            switch (face) {
                case NORTH, SOUTH -> { hCoord = quad.getX(i); vCoord = quad.getY(i); }
                case EAST, WEST -> { hCoord = quad.getZ(i); vCoord = quad.getY(i); }
                default -> { hCoord = quad.getX(i); vCoord = quad.getZ(i); }
            }
            if (left && hCoord < EDGE) { nu = u + su; thisChanged = true; }
            if (right && hCoord > 1.0f - EDGE) { nu = u - su; thisChanged = true; }
            if (up && vCoord > 1.0f - EDGE) { nv = v + sv; thisChanged = true; }
            if (down && vCoord < EDGE) { nv = v - sv; thisChanged = true; }
            if (thisChanged) {
                quad.setUV(i, nu, nv);
                changed = true;
            }
        }
        if (!changed) return;
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
