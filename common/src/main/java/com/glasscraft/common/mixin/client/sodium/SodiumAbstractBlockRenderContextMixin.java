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
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CTM para el pipeline de terreno de Sodium 0.9.x.
 *
 * EJES UV<->mundo por COVARIANZA least-squares (ronda 12): el eje y signo de
 * U/V se derivan de los 4 vertices a la vez (cov(u, coordenada mundial)).
 * La ronda anterior usaba el par max/min de UV — que es una DIAGONAL del
 * quad — y en tiras de panel (7x16 mundo) la diagonal domina en Y: uAxis
 * salia Y (mal) -> contracciones en el lado equivocado -> texturas sesgadas
 * en Fabric y sin efecto visible en NeoForge. La covarianza es inmune al
 * orden de vertices y a la diagonal: verificada contra dumps reales de
 * brazo de panel (uAxis=Z, u crece hacia el norte) y cara up de cubo
 * (uAxis=EAST, igual que la ronda que ya funcionaba).
 *
 * Garantias que se mantienen:
 * - Un vertice solo contrae si su u/v esta EXACTAMENTE en un borde del tile
 *   del sprite, y siempre hacia DENTRO del tile (imposible corromper).
 * - Paneles vanilla reales (isVanillaPane): tapas up/down excluidas.
 *   OJO: vidrio NO es panel (translucido, canOcclude=false).
 *
 * Modelo de panel vanilla verificado en el jar: brazo = elemento [7..9]x[0..16]
 * con cara larga u=[9..16] del tile de VIDRIO (u=16 en la punta exterior);
 * la linea de marco en la junta entre paneles conectados ES ese borde u=16
 * del tile. Contraerlo cuando el vecino en esa direccion conecta = union.
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

        boolean realPane = CtmResolver.isVanillaPane(state);
        if (realPane && (face == Direction.UP || face == Direction.DOWN)) return;

        glasscraft$shrinkQuad(quad, face, mask, realPane);
    }

    private void glasscraft$shrinkQuad(MutableQuadViewImpl quad, Direction face, int mask, boolean realPane) {
        float[] u = new float[4], v = new float[4];
        Vector3f[] p = new Vector3f[4];
        Vector3f center = new Vector3f();
        for (int i = 0; i < 4; i++) {
            u[i] = quad.getTexU(i);
            v[i] = quad.getTexV(i);
            p[i] = new Vector3f(quad.getX(i), quad.getY(i), quad.getZ(i));
            center.add(p[i]);
        }
        center.mul(0.25f);

        // ---- Ejes UV<->mundo por covarianza (least-squares, 4 vertices) ----
        Direction.Axis nAx = face.getAxis();
        Direction.Axis[] tan = new Direction.Axis[2];
        int ti = 0;
        for (Direction.Axis ax : Direction.Axis.values()) {
            if (ax != nAx) tan[ti++] = ax;
        }
        float uMean = (u[0] + u[1] + u[2] + u[3]) * 0.25f;
        float vMean = (v[0] + v[1] + v[2] + v[3]) * 0.25f;
        float m0 = (coord(p[0], tan[0]) + coord(p[1], tan[0]) + coord(p[2], tan[0]) + coord(p[3], tan[0])) * 0.25f;
        float m1 = (coord(p[0], tan[1]) + coord(p[1], tan[1]) + coord(p[2], tan[1]) + coord(p[3], tan[1])) * 0.25f;
        float covU0 = 0, covU1 = 0, covV0 = 0, covV1 = 0;
        for (int i = 0; i < 4; i++) {
            float du = u[i] - uMean;
            float dv = v[i] - vMean;
            float d0 = coord(p[i], tan[0]) - m0;
            float d1 = coord(p[i], tan[1]) - m1;
            covU0 += du * d0;
            covU1 += du * d1;
            covV0 += dv * d0;
            covV1 += dv * d1;
        }
        Direction uDir = null, vDir = null;
        if (Math.abs(covU0) > 1e-12f || Math.abs(covU1) > 1e-12f) {
            if (Math.abs(covU0) >= Math.abs(covU1)) {
                uDir = covU0 >= 0 ? posDir(tan[0]) : posDir(tan[0]).getOpposite();
            } else {
                uDir = covU1 >= 0 ? posDir(tan[1]) : posDir(tan[1]).getOpposite();
            }
        }
        if (Math.abs(covV0) > 1e-12f || Math.abs(covV1) > 1e-12f) {
            if (Math.abs(covV0) >= Math.abs(covV1)) {
                vDir = covV0 >= 0 ? posDir(tan[0]) : posDir(tan[0]).getOpposite();
            } else {
                vDir = covV1 >= 0 ? posDir(tan[1]) : posDir(tan[1]).getOpposite();
            }
        }

        TextureAtlasSprite sprite = quad.cachedSprite();
        if (sprite == null) return;
        float su = (sprite.getU1() - sprite.getU0()) / 16.0f;
        float sv = (sprite.getV1() - sprite.getV0()) / 16.0f;
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();

        float quMin = Math.min(Math.min(u[0], u[1]), Math.min(u[2], u[3]));
        float quMax = Math.max(Math.max(u[0], u[1]), Math.max(u[2], u[3]));
        float qvMin = Math.min(Math.min(v[0], v[1]), Math.min(v[2], v[3]));
        float qvMax = Math.max(Math.max(v[0], v[1]), Math.max(v[2], v[3]));

        // DUMP dedicado a paneles (presupuesto propio: los cubos no se lo comen).
        boolean doDump = false;
        if (realPane && CtmDebug.paneDumps.get() > 0) {
            CtmDebug.paneDumps.decrementAndGet();
            doDump = true;
            CtmDebug.LOGGER.info("[PDUMP] face={} pane={} sprite={} uAx={} vAx={} mask={}",
                face, realPane, sprite.contents().name(),
                uDir, vDir, Integer.toBinaryString(mask));
            CtmDebug.LOGGER.info("[PDUMP]  tile u=[{}/{}] v=[{}/{}] quad u=[{}/{}] v=[{}/{}]",
                f(u0), f(u1), f(v0), f(v1), f(quMin), f(quMax), f(qvMin), f(qvMax));
            for (int i = 0; i < 4; i++) {
                CtmDebug.LOGGER.info("[PDUMP]  v{} p=({},{},{}) uv=({},{})",
                    i, f(p[i].x), f(p[i].y), f(p[i].z), f(u[i]), f(v[i]));
            }
        }

        float[] nu = new float[4], nv = new float[4];
        System.arraycopy(u, 0, nu, 0, 4);
        System.arraycopy(v, 0, nv, 0, 4);
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            Vector3f rel = new Vector3f(p[i]).sub(center);
            boolean ch = false;
            for (Direction d : Direction.values()) {
                if (d.getAxis() == nAx) continue;
                if ((mask & (1 << d.ordinal())) == 0) continue;
                if (rel.dot(new Vector3f(d.step())) <= 0) continue;
                if (uDir != null && d.getAxis() == uDir.getAxis()) {
                    if (d == uDir) {
                        if (u[i] == quMax && quMax > u1 - su) { nu[i] = u[i] - su; ch = true; }
                    } else {
                        if (u[i] == quMin && quMin < u0 + su) { nu[i] = u[i] + su; ch = true; }
                    }
                } else if (vDir != null) {
                    if (d == vDir) {
                        if (v[i] == qvMax && qvMax > v1 - sv) { nv[i] = v[i] - sv; ch = true; }
                    } else {
                        if (v[i] == qvMin && qvMin < v0 + sv) { nv[i] = v[i] + sv; ch = true; }
                    }
                }
            }
            if (ch) changed = true;
        }
        if (!changed) {
            if (doDump) CtmDebug.LOGGER.info("[PDUMP]  -> SIN cambio");
            return;
        }
        for (int i = 0; i < 4; i++) {
            quad.setUV(i, nu[i], nv[i]);
        }
        quad.setAmbientOcclusion(TriState.FALSE);
        CtmDebug.quadsModified.incrementAndGet();
        if (doDump) {
            CtmDebug.LOGGER.info("[PDUMP]  -> CONTRAIDO uv=({},{}) ({},{}) ({},{}) ({},{})",
                f(nu[0]), f(nv[0]), f(nu[1]), f(nv[1]), f(nu[2]), f(nv[2]), f(nu[3]), f(nv[3]));
        }
        CtmDebug.sample(realPane ? "pane" : "cube");
    }

    private static float coord(Vector3f p, Direction.Axis ax) {
        return switch (ax) {
            case X -> p.x;
            case Y -> p.y;
            case Z -> p.z;
        };
    }

    private static Direction posDir(Direction.Axis ax) {
        return switch (ax) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
    }

    private static String f(float x) {
        return String.format("%.5f", x);
    }
}
