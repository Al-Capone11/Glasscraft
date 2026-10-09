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
 * Regla central (arregla triángulos y caras equivocadas): SOLO se contrae el
 * UV de un borde si el quad ACTUALMENTE MUESTRA ese borde del tile del sprite.
 * Los cantos superiores de los brazos del panel y las puntas 'noside'
 * muestrean zonas interiores del tile (p.ej. [7..9]x[0..7] de
 * glass_pane_top): contraerlos saca el muestreo del area pintada ->
 * triangulos transparentes. La superficie de los brazos ([9..16]x[0..16])
 * SI toca el borde del tile: es la junta real entre paneles.
 *
 * Convencion UV<->mundo DERIVADA del propio quad (no asumida): el vertice
 * de max-U/max-V define el signo de los ejes tangentes.
 * Posiciones 0..1 (FaceBakery.bakeVertex div(16), verificado).
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

        // PANELES: las tapas up/down (glass_pane_top) nunca participan en la
        // continuidad — su textura es un anillo distinto del vidrio y
        // contraerlas muestra textura del vecino (bug paneles apilados).
        // La union apilada ya es geometrica en vanilla. Solo caras laterales.
        boolean paneLike = !state.canOcclude();
        if (paneLike && (face == Direction.UP || face == Direction.DOWN)) return;

        glasscraft$shrinkQuad(quad, face, mask, paneLike);
    }

    private void glasscraft$shrinkQuad(MutableQuadViewImpl quad, Direction face, int mask, boolean paneLike) {
        // ejes tangentes canonicos, signos corregidos con los datos del quad
        Direction uDir, vDir;
        switch (face.getAxis()) {
            case X -> { uDir = Direction.SOUTH; vDir = Direction.UP; }
            case Y -> { uDir = Direction.EAST; vDir = Direction.SOUTH; }
            default -> { uDir = Direction.EAST; vDir = Direction.UP; }
        }
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

        int iMaxU = 0, iMinU = 0, iMaxV = 0, iMinV = 0;
        for (int i = 1; i < 4; i++) {
            if (u[i] > u[iMaxU]) iMaxU = i;
            if (u[i] < u[iMinU]) iMinU = i;
            if (v[i] > v[iMaxV]) iMaxV = i;
            if (v[i] < v[iMinV]) iMinV = i;
        }
        Vector3f du = new Vector3f(p[iMaxU]).sub(p[iMinU]);
        if (du.dot(new Vector3f(uDir.step())) < 0) uDir = uDir.getOpposite();
        Vector3f dv = new Vector3f(p[iMaxV]).sub(p[iMinV]);
        if (dv.dot(new Vector3f(vDir.step())) < 0) vDir = vDir.getOpposite();

        TextureAtlasSprite sprite = quad.cachedSprite();
        if (sprite == null) return;
        float su = (sprite.getU1() - sprite.getU0()) / 16.0f;
        float sv = (sprite.getV1() - sprite.getV0()) / 16.0f;

        // REGLA TILE-BORDE: el quad solo puede contraer un lado si su UV
        // bbox toca ese borde del tile. ej. brazo de panel: [9..16]x[0..16]
        // toca u1 y v0/v1 -> solo esos lados son contraibles.
        float quMin = Math.min(Math.min(u[0], u[1]), Math.min(u[2], u[3]));
        float quMax = Math.max(Math.max(u[0], u[1]), Math.max(u[2], u[3]));
        float qvMin = Math.min(Math.min(v[0], v[1]), Math.min(v[2], v[3]));
        float qvMax = Math.max(Math.max(v[0], v[1]), Math.max(v[2], v[3]));
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();
        boolean quadAtU0 = quMin < u0 + su;   // toca borde izquierdo del tile
        boolean quadAtU1 = quMax > u1 - su;  // borde derecho
        boolean quadAtV0 = qvMin < v0 + sv;  // borde superior
        boolean quadAtV1 = qvMax > v1 - sv;  // borde inferior

        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            Vector3f rel = new Vector3f(p[i]).sub(center);
            float nu = u[i], nv = v[i];
            boolean thisChanged = false;
            for (Direction d : Direction.values()) {
                if (d.getAxis() == face.getAxis()) continue; // no vecinos de la normal
                if ((mask & (1 << d.ordinal())) == 0) continue;
                if (rel.dot(new Vector3f(d.step())) <= 0) continue; // vertice no esta en ese borde
                if (d.getAxis() == uDir.getAxis()) {
                    // contraer U: solo si el quad muestra ese borde del tile
                    if (d == uDir && quadAtU1) { nu = u[i] - su; thisChanged = true; }
                    else if (d != uDir && quadAtU0) { nu = u[i] + su; thisChanged = true; }
                } else {
                    if (d == vDir && quadAtV1) { nv = v[i] - sv; thisChanged = true; }
                    else if (d != vDir && quadAtV0) { nv = v[i] + sv; thisChanged = true; }
                }
            }
            if (thisChanged) {
                quad.setUV(i, nu, nv);
                changed = true;
            }
        }
        if (!changed) return;
        quad.setAmbientOcclusion(TriState.FALSE);
        CtmDebug.quadsModified.incrementAndGet();
        CtmDebug.sample(paneLike ? "pane" : "cube");
    }
}
