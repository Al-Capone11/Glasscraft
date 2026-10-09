package com.glasscraft.common.mixin.client;

import com.glasscraft.common.client.ctm.CtmContext;
import com.glasscraft.common.client.ctm.CtmResolver;
import com.glasscraft.common.client.ctm.CtmUvShrink;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F3 CTM: dos inyecciones en ModelBlockRenderer (firmas verificadas javap).
 *
 * 1) tesselateBlock HEAD: si el estado participa (config + tipo/mod),
 *    publica pos+level+state en CtmContext (thread-local) para los quads
 *    de este bloque; TAIL siempre limpia.
 * 2) putQuadWithTint: sustituye el BakedQuad por la version con UVs
 *    contraidas si la cara conecta con un vecino identico.
 *
 * Coste: cuando la config desactiva la continuidad o el bloque no
 * participa, solo un isEnabled() por bloque (cache de registry holder).
 */
@Mixin(ModelBlockRenderer.class)
abstract class ModelBlockRendererMixin {

    @Inject(method = "tesselateBlock", at = @At("HEAD"))
    private void glasscraft$beginBlock(BlockQuadOutput output, float x, float y, float z,
                                        net.minecraft.client.renderer.block.BlockAndTintGetter level,
                                        net.minecraft.core.BlockPos pos,
                                        net.minecraft.world.level.block.state.BlockState state,
                                        net.minecraft.client.renderer.block.dispatch.BlockStateModel model,
                                        long seed, CallbackInfo ci) {
        if (state.isAir()) return;
        // Solo cubos completos: paneles y bloques con formas raras quedan
        // fuera (sus UVs son sub-tile y la contraccion full-tile los
        // distorsionaria; ademas sus quads internos van con direction=null).
        if (!state.canOcclude()) return;
        if (!CtmResolver.isEnabled(state)) return;
        CtmContext ctx = CtmContext.get();
        ctx.level = level;
        ctx.pos = pos;
        ctx.state = state;
        ctx.connectionMask = -1;
    }

    @Inject(method = "tesselateBlock", at = @At("TAIL"))
    private void glasscraft$endBlock(BlockQuadOutput output, float x, float y, float z,
                                      net.minecraft.client.renderer.block.BlockAndTintGetter level,
                                      net.minecraft.core.BlockPos pos,
                                      net.minecraft.world.level.block.state.BlockState state,
                                      net.minecraft.client.renderer.block.dispatch.BlockStateModel model,
                                      long seed, CallbackInfo ci) {
        CtmContext.get().reset();
    }

    @ModifyVariable(method = "putQuadWithTint", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private BakedQuad glasscraft$shrinkQuad(BakedQuad quad) {
        CtmContext ctx = CtmContext.get();
        if (ctx.state == null) return quad;
        if (quad.direction() == null) return quad; // quads internos/postes
        int mask = ctx.mask();
        if (mask == 0) return quad;
        return CtmUvShrink.shrinkIfConnected(quad, quad.direction(), mask);
    }
}
