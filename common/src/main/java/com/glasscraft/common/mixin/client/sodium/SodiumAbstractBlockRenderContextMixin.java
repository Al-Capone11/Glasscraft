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
 * CUBOS (vidrio/glowstone/linterna): contrae 1/16 del tile del sprite en cada
 * borde PERPENDICULAR conectado — el borde oscuro de la textura queda absorbido.
 *
 * PANELES: vanilla ya extiende la geometria al bloque vecino cuando conecta
 * (pane_side llega hasta el borde). La junta visible es el marco de 1px de la
 * textura glass_pane en el borde del sprite que apunta al vecino. Fix:
 * contraer 1/16 del TILE (no del quad: el quad es una franja de 7px donde
 * 1/16 de quad seria sub-pixel) del borde que apunta al vecino, derivando
 * QUE borde UV es por la POSICION GEOMETRICA de los vertices (de los 4
 * vertices, 2 estan pegados al borde del bloque en el eje de la cara).
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

        boolean paneLike = !state.canOcclude();
        if (paneLike) {
            glasscraft$paneShrink(quad, face, mask);
        } else {
            glasscraft$cubeShrink(quad, face, mask);
        }
    }

    /**
     * PANELES: contrae 1/16 del TILE del sprite en el borde que apunta al
     * vecino. Los paneles conectan en el EJE de la cara (el quad-poste mira
     * E/W y el vecino esta al E/W). Se identifica que par de vertices UV
     * corresponden al borde geometrico hacia el vecino usando las posiciones
     * XYZ locales (0..1) del quad: los vertices pegados al borde del bloque
     * en el eje de la cara son los que hay que contraer.
     */
    private void glasscraft$paneShrink(MutableQuadViewImpl quad, Direction face, int mask) {
        // El vecino en la direccion de la cara o su opuesta.
        boolean front = (mask & (1 << face.getOpposite().ordinal())) != 0;
        boolean back = (mask & (1 << face.ordinal())) != 0;
        boolean up = (mask & (1 << Direction.UP.ordinal())) != 0;
        boolean down = (mask & (1 << Direction.DOWN.ordinal())) != 0;
        if (!front && !back && !up && !down) return;

        TextureAtlasSprite sprite = quad.cachedSprite();
        if (sprite == null) return;
        float su = (sprite.getU1() - sprite.getU0()) / 16.0f;
        float sv = (sprite.getV1() - sprite.getV0()) / 16.0f;

        // Eje de la cara: para E/W el eje transversal del quad es X; para N/S es Z.
        // El quad del poste tiene 4 vertices: 2 pegados a un borde del bloque
        // (el que apunta al vecino), 2 al otro. Contraer los UVs de los vertices
        // del borde QUE CONECTA hacia el interior del tile (1/16).
        // Distinguir par: por coordenada local del vertice en el eje de la cara.
        // posiciones locales: getX/getY/getZ devuelven 0..16 relativos al bloque.
        Direction axis = face; // eje de la cara
        float minEdge, maxEdge;
        // coordenadas de los 4 vertices en el eje de la cara
        float[] c = new float[4];
        for (int i = 0; i < 4; i++) {
            c[i] = switch (axis.getAxis()) {
                case X -> quad.getX(i);
                case Y -> quad.getY(i);
                case Z -> quad.getZ(i);
            };
        }
        minEdge = Math.min(Math.min(c[0], c[1]), Math.min(c[2], c[3]));
        maxEdge = Math.max(Math.max(c[0], c[1]), Math.max(c[2], c[3]));

        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            float u = quad.getTexU(i);
            float v = quad.getTexV(i);
            float nu = u, nv = v;
            // vertice en el borde minimo (apunta a la direccion negativa del eje)
            if (front && Math.abs(c[i] - minEdge) < 0.01f) {
                // contraer el UV de este vertice 1/16 hacia el interior del tile
                // en el eje UV que corresponda segun la cara (para E/W: eje U del tile
                // mapea el eje X? No — las UV del quad-poste de pane_side usan franjas
                // del sprite glass; el borde del bloque en X corresponde a U del sprite.
                if (axis.getAxis() == Direction.Axis.X) {
                    nu = u + (face == Direction.WEST ? su : -su);
                } else {
                    nv = v + (face == Direction.SOUTH ? sv : -sv);
                }
                changed = true;
            }
            if (back && Math.abs(c[i] - maxEdge) < 0.01f) {
                if (axis.getAxis() == Direction.Axis.X) {
                    nu = u + (face == Direction.EAST ? su : -su);
                } else {
                    nv = v + (face == Direction.NORTH ? sv : -sv);
                }
                changed = true;
            }
            // estiramiento vertical con vecinos arriba/abajo (pane apilado)
            float y = quad.getY(i);
            if (up && Math.abs(y - 16.0f) < 0.01f) { nv = v + sv; changed = true; }
            if (down && Math.abs(y - 0.0f) < 0.01f) { nv = v - sv; changed = true; }
            if (changed) quad.setUV(i, nu, nv);
        }
        if (!changed) return;
        quad.setAmbientOcclusion(TriState.FALSE);
        CtmDebug.quadsModified.incrementAndGet();
        CtmDebug.sample("pane");
    }

    /**
     * CUBO: contrae 1/16 del tile en cada borde perpendicular conectado.
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

        // contraer los UVs en funcion de la posicion geometrica del vertice
        // dentro de la cara (0..16): los vertices del borde conectado retroceden
        // 1/16 dentro del tile.
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            float u = quad.getTexU(i);
            float v = quad.getTexV(i);
            float nu = u, nv = v;
            // eje U de la cara = eje horizontal del quad; eje V = vertical.
            // Para caras N/S: X es U; para E/W: Z es U; para UP/DOWN: X es U.
            float hCoord, vCoord;
            switch (face) {
                case NORTH, SOUTH -> { hCoord = quad.getX(i); vCoord = quad.getY(i); }
                case EAST, WEST -> { hCoord = quad.getZ(i); vCoord = quad.getY(i); }
                default -> { hCoord = quad.getX(i); vCoord = quad.getZ(i); }
            }
            // hCoord/vCoord en 0..16: contraer bordes conectados.
            // Mapa hCoord->U, vCoord->V (convencion vanilla de FaceBakery).
            if (left && hCoord < 0.01f) { nu = u + su; changed = true; }
            if (right && hCoord > 15.99f) { nu = u - su; changed = true; }
            if (up && vCoord > 15.99f) { nv = v + sv; changed = true; }
            if (down && vCoord < 0.01f) { nv = v - sv; changed = true; }
            if (changed) quad.setUV(i, nu, nv);
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
