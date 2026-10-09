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
 * Dos reglas con garantia de seguridad (no pueden corromper texturas):
 * 1) CONTRACCION DIRECTA DEL LADO DEL SEAM: el vertice que esta en el borde
 *    con vecino conectado solo contrae si SU PROPIO u/v esta EXACTAMENTE en
 *    un borde del tile del sprite (u0/u1/v0/v1, tolerancia 1 texel). Contraer
 *    siempre mueve el muestreo HACIA DENTRO del tile: jamas muestrea fuera.
 *    (El emparejamiento anterior por direccion podia contraer el lado
 *    equivocado y desplazar la textura = "se ven mal".)
 * 2) EXCLUSION DE TAPAS SOLO PARA PANELES VANILLA REALES (isVanillaPane).
 *    OJO: el vidrio tiene canOcclude==false y NO es un panel — usar
 *    !canOcclude() como clasificador mataba las caras UP/DOWN de los
 *    bloques de vidrio ("no conectan desde arriba").
 *
 * Eje U del quad DERIVADO (max-|dot| contra ambos tangentes). Posiciones
 * 0..1 (FaceBakery div(16), verificado bytecode).
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
        // Tapas up/down: solo excluir PANELES REALES (el vidrio en bloque
        // tiene canOcclude=false — la exclusion anterior le mataba su cara
        // superior y nunca conectaba visto desde arriba).
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

        // Eje U del quad: derivar probando AMBOS ejes tangentes.
        Direction.Axis normalAxis = face.getAxis();
        Direction t1 = (normalAxis == Direction.Axis.X) ? Direction.SOUTH : Direction.EAST;
        Direction t2 = (normalAxis == Direction.Axis.Y) ? Direction.SOUTH : Direction.UP;
        int iMaxU = 0, iMinU = 0;
        for (int i = 1; i < 4; i++) {
            if (u[i] > u[iMaxU]) iMaxU = i;
            if (u[i] < u[iMinU]) iMinU = i;
        }
        Vector3f du = new Vector3f(p[iMaxU]).sub(p[iMinU]);
        float dU1 = du.dot(new Vector3f(t1.step()));
        float dU2 = du.dot(new Vector3f(t2.step()));
        Direction uAxis = (Math.abs(dU1) >= Math.abs(dU2)) ? t1 : t2;

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

        // DUMP con posiciones y decisiones (casos problematicos prioritarios).
        boolean doDump = false;
        if (mask != 0 && CtmDebug.dumpsRemaining.get() > 0
                && (realPane || face == Direction.UP || face == Direction.DOWN)) {
            CtmDebug.dumpsRemaining.decrementAndGet();
            doDump = true;
            CtmDebug.LOGGER.info("[DUMP2] face={} pane={} sprite={} mask={}",
                face, realPane, sprite.contents().name(), Integer.toBinaryString(mask));
            for (int i = 0; i < 4; i++) {
                CtmDebug.LOGGER.info("[DUMP2]  v{} p=({},{},{}) uv=({},{})",
                    i,
                    String.format("%.3f", p[i].x), String.format("%.3f", p[i].y), String.format("%.3f", p[i].z),
                    String.format("%.5f", u[i]), String.format("%.5f", v[i]));
            }
            CtmDebug.LOGGER.info("[DUMP2]  tile u=[{}/{}] v=[{}/{}] quad u=[{}/{}] v=[{}/{}]",
                String.format("%.5f", u0), String.format("%.5f", u1),
                String.format("%.5f", v0), String.format("%.5f", v1),
                String.format("%.5f", quMin), String.format("%.5f", quMax),
                String.format("%.5f", qvMin), String.format("%.5f", qvMax));
        }

        float[] nu = new float[4], nv = new float[4];
        System.arraycopy(u, 0, nu, 0, 4);
        System.arraycopy(v, 0, nv, 0, 4);
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            Vector3f rel = new Vector3f(p[i]).sub(center);
            for (Direction d : Direction.values()) {
                if (d.getAxis() == normalAxis) continue;
                if ((mask & (1 << d.ordinal())) == 0) continue;
                if (rel.dot(new Vector3f(d.step())) <= 0) continue;
                // Vertice en el borde que mira al vecino d: contraer su UV
                // SOLO si su u/v esta en un borde del tile (mueve hacia dentro).
                if (d.getAxis() == uAxis.getAxis()) {
                    if (Math.abs(u[i] - quMax) < 1e-6f && quMax > u1 - su) {
                        nu[i] = u[i] - su;
                    } else if (Math.abs(u[i] - quMin) < 1e-6f && quMin < u0 + su) {
                        nu[i] = u[i] + su;
                    }
                } else {
                    if (Math.abs(v[i] - qvMax) < 1e-6f && qvMax > v1 - sv) {
                        nv[i] = v[i] - sv;
                    } else if (Math.abs(v[i] - qvMin) < 1e-6f && qvMin < v0 + sv) {
                        nv[i] = v[i] + sv;
                    }
                }
            }
            if (nu[i] != u[i] || nv[i] != v[i]) changed = true;
        }
        if (!changed) {
            if (doDump) CtmDebug.LOGGER.info("[DUMP2]  -> SIN cambio (vertices del seam no estan en borde de tile)");
            return;
        }
        for (int i = 0; i < 4; i++) {
            quad.setUV(i, nu[i], nv[i]);
        }
        quad.setAmbientOcclusion(TriState.FALSE);
        CtmDebug.quadsModified.incrementAndGet();
        if (doDump) {
            CtmDebug.LOGGER.info("[DUMP2]  -> CONTRAIDO a uv=({},{}) ({},{}) ({},{}) ({},{})",
                String.format("%.5f", nu[0]), String.format("%.5f", nv[0]),
                String.format("%.5f", nu[1]), String.format("%.5f", nv[1]),
                String.format("%.5f", nu[2]), String.format("%.5f", nv[2]),
                String.format("%.5f", nu[3]), String.format("%.5f", nv[3]));
        }
        CtmDebug.sample(realPane ? "pane" : "cube");
    }
}
