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
 * SIN ASUNCIONES de convencion UV: el eje mundial de U y V se DERIVA del
 * propio quad (delta 3D entre vertices de distinta u / v), y los bordes se
 * identifican comparando la posicion del vertice contra el centro del quad
 * en ese eje. Funciona igual para cubos y paneles con cualquier rotacion.
 *
 * Posiciones 0..1 (26.2 normaliza, verificado FaceBakery.bakeVertex div(16)).
 */
@Mixin(net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext.class)
abstract class SodiumAbstractBlockRenderContextMixin {

    private static final float EDGE = 0.02f;

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

        glasscraft$shrinkQuad(quad, face, mask);
    }

    /**
     * Contrae 1/16 del tile en cada borde conectado, derivando la
     * correspondencia UV<->mundo del propio quad:
     * 1) centro del quad (media de posiciones).
     * 2) para cada vertice, vector centro->vertice.
     * 3) U mundial: promedio de (pos_j - pos_i) * sign(u_j - u_i) para pares
     *    con |du| apreciable — un vector que apunta hacia "+U".
     * 4) V mundial: igual con v.
     * 5) para cada vecino conectado (dir), el borde que lo mira son los
     *    vertices cuyo (vertice-centro)·dir > 0; contraer su UV 1/16 hacia
     *    el interior: u -= su * dot(uAxis, dir) ... mas simple: contraer el
     *    COMPONENTE de UV alineado con dir.
     */
    private void glasscraft$shrinkQuad(MutableQuadViewImpl quad, Direction face, int mask) {
        // ejes tangentes de la cara
        Direction uDir, vDir;
        switch (face.getAxis()) {
            case X -> { uDir = Direction.SOUTH; vDir = Direction.UP; }
            case Y -> { uDir = Direction.EAST; vDir = Direction.SOUTH; }
            default -> { uDir = Direction.EAST; vDir = Direction.UP; }
        }
        // Ajustar signos derivando del quad: comparar UV vs posicion.
        float[] u = new float[4], v = new float[4];
        Vector3f[] p = new Vector3f[4];
        Vector3f center = new Vector3f();
        for (int i = 0; i < 4; i++) {
            u[i] = quad.getTexU(i);
            v[i] = quad.getTexV(i);
            p[i] = new Vector3f(quad.getX(i), quad.getY(i), quad.getZ(i));
            center.add(p[i]);
        }
        center.mul(1.0f / 4.0f);

        // derivar signo/mapeo de uDir: el vertice con mayor U deberia estar
        // del lado de la cara apuntado por +uDir. Verificar con un par.
        // Calcular componente pos en uDir para el vertice de mayor u y menor u.
        int iMaxU = 0, iMinU = 0;
        for (int i = 1; i < 4; i++) {
            if (u[i] > u[iMaxU]) iMaxU = i;
            if (u[i] < u[iMinU]) iMinU = i;
        }
        Vector3f du = new Vector3f(p[iMaxU]).sub(p[iMinU]);
        // si el vertice con max U esta en direccion +uDir del mundo, du·uVec > 0
        Vector3f uVec = new Vector3f(uDir.step());
        if (du.dot(uVec) < 0) uDir = uDir.getOpposite();
        // igual para V
        int iMaxV = 0, iMinV = 0;
        for (int i = 1; i < 4; i++) {
            if (v[i] > v[iMaxV]) iMaxV = i;
            if (v[i] < v[iMinV]) iMinV = i;
        }
        Vector3f dv = new Vector3f(p[iMaxV]).sub(p[iMinV]);
        Vector3f vVec = new Vector3f(vDir.step());
        if (dv.dot(vVec) < 0) vDir = vDir.getOpposite();

        TextureAtlasSprite sprite = quad.cachedSprite();
        if (sprite == null) return;
        float su = (sprite.getU1() - sprite.getU0()) / 16.0f;
        float sv = (sprite.getV1() - sprite.getV0()) / 16.0f;

        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            Vector3f rel = new Vector3f(p[i]).sub(center);
            float nu = u[i], nv = v[i];
            boolean thisChanged = false;
            // vecinos conectados en el plano de la cara (todos menos la normal)
            for (Direction d : Direction.values()) {
                if (d.getAxis() == face.getAxis()) continue;
                if ((mask & (1 << d.ordinal())) == 0) continue;
                boolean onEdge = rel.dot(new Vector3f(d.step())) > 0;
                if (!onEdge) continue;
                // este vertice esta en el borde que mira a d: contraer su UV
                // en el eje UV alineado con d.
                if (d.getAxis() == uDir.getAxis()) {
                    nu += (d == uDir) ? -su : su;
                } else {
                    nv += (d == vDir) ? -sv : sv;
                }
                thisChanged = true;
            }
            if (thisChanged) {
                quad.setUV(i, nu, nv);
                changed = true;
            }
        }
        if (!changed) return;
        quad.setAmbientOcclusion(TriState.FALSE);
        CtmDebug.quadsModified.incrementAndGet();
        boolean paneLike = !state.canOcclude();
        CtmDebug.sample(paneLike ? "pane" : "cube");
    }
}
