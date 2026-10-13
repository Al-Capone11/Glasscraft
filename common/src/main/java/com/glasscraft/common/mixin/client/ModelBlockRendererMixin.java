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
 * F3 CTM — anclaje VANILLA (sin Sodium): tesselateBlock publica pos+level+state
 * via thread-local; putQuadWithTint reempaqueta el BakedQuad con UVs contraidas.
 * Con Sodium presente, el terreno va por el hook de Sodium; este cubre items,
 * bloques especiales y cualquier uso del ModelBlockRenderer vanilla.
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
        // Paneles vanilla fuera del path vanilla (UVs sub-tile; el hook de
        // Sodium con shrink proporcional si los cubre).
        if (CtmResolver.isVanillaPane(state)) return;
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
        BakedQuad shrunk = CtmUvShrink.shrinkIfConnected(quad, quad.direction(), mask,
            false, ctx.state);
        return shrunk;
    }
}
