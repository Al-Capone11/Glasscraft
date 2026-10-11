package com.glasscraft.common.client.ctm;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import com.glasscraft.common.Glasscraft;

/**
 * Contraccion de UVs estilo OptiFine "glass.ctm": cuando la cara de un bloque
 * conecta con un vecino identico, contraemos las UV de esa cara hacia el
 * centro del tile (1/16 = 1 pixel de margen). Vecinos opuestos desplazan el
 * centro del muestreo para que el patron de borde quede alineado.
 *
 * BakedQuad 26.2 (verificado): record inmutable con 4x packedUV (long) via
 * UVPair.pack(u,v); se reconstruye el quad con UVs modificadas.
 */
public final class CtmUvShrink {

    /** Contraccion en fraccion de tile (1 pixel de 16). */
    private static final float SHRINK = 1.0f / 16.0f;

    private CtmUvShrink() {}

    /**
     * Reconstruye el quad con UVs contraidas si la cara 'dir' conecta.
     * Devuelve el quad original si no hay que tocar nada.
     */
    public static BakedQuad shrinkIfConnected(BakedQuad quad, Direction face, int connectionMask,
                                              boolean unused, BlockState state) {
        if (connectionMask == 0) return quad;

        TextureAtlasSprite sprite = quad.materialInfo().sprite();

        // Override de sprite: cristal limpio (vidrios) y linterna marina.
        // Cara totalmente rodeada -> variante _flat (sin marco, inmune a mips).
        BakedQuad.MaterialInfo info = quad.materialInfo();
        if (CtmResolver.spriteOverrideActive(state)) {
            String blockPath = state.getBlock().builtInRegistryHolder().key()
                .identifier().getPath();
            boolean up = connects(connectionMask, upOf(face));
            boolean down = connects(connectionMask, downOf(face));
            boolean left = connects(connectionMask, leftOf(face));
            boolean right = connects(connectionMask, rightOf(face));
            boolean flat = up && down && left && right;
            TextureAtlasSprite clean = CleanGlassSprite.getFor(blockPath, flat);
            if (clean != null && clean != sprite) {
                info = new BakedQuad.MaterialInfo(clean, info.layer(), info.itemRenderType(),
                    info.tintIndex(), info.shade(), info.lightEmission());
                // remapear UVs del atlas al nuevo tile (mismo tamano)
                float[] uArr = new float[4], vArr = new float[4];
                long[] packed = {quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3()};
                for (int i = 0; i < 4; i++) {
                    uArr[i] = clean.getU0() + (UVPair.unpackU(packed[i]) - sprite.getU0());
                    vArr[i] = clean.getV0() + (UVPair.unpackV(packed[i]) - sprite.getV0());
                    packed[i] = UVPair.pack(uArr[i], vArr[i]);
                }
                quad = new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                    packed[0], packed[1], packed[2], packed[3], quad.direction(), info);
                sprite = clean;
            }
        }

        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();

        float su = (u1 - u0) * SHRINK;
        float sv = (v1 - v0) * SHRINK;

        // Desplazar el centro de muestreo si conecta por los lados
        // perpendiculares a la cara (compress vertical/horizontal).
        boolean up = connectsDir(connectionMask, upOf(face));
        boolean down = connectsDir(connectionMask, downOf(face));
        boolean left = connectsDir(connectionMask, leftOf(face));
        boolean right = connectsDir(connectionMask, rightOf(face));

        // Ejes UV dependen de la cara (mapeo vanilla estandar de FaceBakery).
        // Contraer el lado que conecta: si conecta 'down', la V superior baja.
        float nu0 = u0 + (left ? su : 0);
        float nu1 = u1 - (right ? su : 0);
        // V0 = borde SUPERIOR del tile: contraer arriba cuando conecta 'up'.
        float nv0 = v0 + (up ? sv : 0);
        float nv1 = v1 - (down ? sv : 0);

        return new BakedQuad(
            quad.position0(), quad.position1(), quad.position2(), quad.position3(),
            UVPair.pack(remapU(quad.packedUV0(), u0, u1, nu0, nu1), remapV(quad.packedUV0(), v0, v1, nv0, nv1)),
            UVPair.pack(remapU(quad.packedUV1(), u0, u1, nu0, nu1), remapV(quad.packedUV1(), v0, v1, nv0, nv1)),
            UVPair.pack(remapU(quad.packedUV2(), u0, u1, nu0, nu1), remapV(quad.packedUV2(), v0, v1, nv0, nv1)),
            UVPair.pack(remapU(quad.packedUV3(), u0, u1, nu0, nu1), remapV(quad.packedUV3(), v0, v1, nv0, nv1)),
            quad.direction(), info);
    }

    /** Reempaqueta U del rango original del tile al rango contraido. */
    private static long remapU(long packedUV, float u0, float u1, float nu0, float nu1) {
        float u = UVPair.unpackU(packedUV);
        float f = (u - u0) / (u1 - u0);
        return UVPair.pack(nu0 + f * (nu1 - nu0), UVPair.unpackV(packedUV));
    }

    private static long remapV(long packedUV, float v0, float v1, float nv0, float nv1) {
        float v = UVPair.unpackV(packedUV);
        float f = (v - v0) / (v1 - v0);
        return UVPair.pack(UVPair.unpackU(packedUV), nv0 + f * (nv1 - nv0));
    }

    private static boolean connectsDir(int mask, Direction d) {
        return d != null && (mask & (1 << d.ordinal())) != 0;
    }

    /** Publico para el hook de Sodium: ¿la mascara conecta en esa direccion? */
    public static boolean connects(int mask, Direction d) {
        return connectsDir(mask, d);
    }

    /** Vecino 'arriba' en el plano de la cara (para contraer V-top). */
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
