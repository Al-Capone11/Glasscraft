package com.glasscraft.common.mixin;

import com.glasscraft.common.Glasscraft;
import com.glasscraft.common.init.GlasscraftItems;
import com.glasscraft.core.GlasscraftConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops garantizados: romper un bloque del tag glasscutter_effective con el
 * Glasscutter suelta el bloque mismo (glowstone suelta glowstone, el vidrio
 * vidrio), sin importar su loot table (que sin silk touch daria polvo/nada).
 *
 * CONFIG: si el material esta en NO (por tipo o por mod), NO se protege —
 * el bloque se rompe igual de rapido (sigue en el tag del Tool) pero se
 * dropea lo que diga su loot table original (vidrio: nada, glowstone: polvos).
 */
@Mixin(Block.class)
abstract class BlockDropResourcesMixin {

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void glasscutter$dropSelf(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemStack tool, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (tool.getItem() != GlasscraftItems.glasscutter()) return;
        if (!state.is(GlasscraftItems.GLASSCUTTER_EFFECTIVE)) return;

        // Config por tipo de bloque / mod: si esta desactivado, drops vanilla.
        Identifier id = state.getBlock().builtInRegistryHolder().key().identifier();
        if (!isProtected(id)) return;

        Item self = state.getBlock().asItem();
        if (self == null) return;

        // Dropear 1x el propio bloque en vez de su loot table.
        Block.popResource(level, pos, new ItemStack(self));
        if (level instanceof ServerLevel serverLevel) {
            state.spawnAfterBreak(serverLevel, pos, tool, true);
        }
        ci.cancel();
    }

    private static boolean isProtected(Identifier id) {
        GlasscraftConfig.Continuity c = Glasscraft.config().continuity;
        String namespace = id.getNamespace();
        String path = id.getPath();
        if (namespace.equals("minecraft")) {
            String category = Glasscraft.categoryOfVanillaPath(path);
            if (category == null) return false; // fuera del tag no llega aqui, pero por si acaso
            return switch (category) {
                case "vanillaGlass" -> c.vanillaGlass;
                case "glassPanes" -> c.glassPanes;
                case "tintedGlass" -> c.tintedGlass;
                case "glowstone" -> c.glowstone;
                case "seaLantern" -> c.seaLantern;
                default -> false;
            };
        }
        // Bloques de mods: resolucion por mod de la config.
        return c.resolves(namespace, path);
    }
}
