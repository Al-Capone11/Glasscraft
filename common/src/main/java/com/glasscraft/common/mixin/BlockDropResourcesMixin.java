package com.glasscraft.common.mixin;

import com.glasscraft.common.init.GlasscraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops garantizados: romper un bloque del tag glasscutter_effective con el
 * Glasscutter SIEMPRE suelta el bloque mismo (glowstone suelta glowstone, el
 * vidrio suelta vidrio), sin importar su loot table (que sin silk touch daria
 * polvo/nada).
 */
@Mixin(Block.class)
abstract class BlockDropResourcesMixin {

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void glasscutter$dropSelf(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemStack tool, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (tool.getItem() != GlasscraftItems.glasscutter()) return;
        if (!state.is(GlasscraftItems.GLASSCUTTER_EFFECTIVE)) return;

        Item self = state.getBlock().asItem();
        if (self == null) return;

        // Dropear 1x el propio bloque en vez de su loot table.
        Block.popResource(level, pos, new ItemStack(self));
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            state.spawnAfterBreak(serverLevel, pos, tool, true);
        }
        ci.cancel();
    }
}
