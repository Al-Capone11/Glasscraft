package com.glasscraft.common.mixin;

import com.glasscraft.common.item.GlasscutterItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Patron elytra: el Glasscutter nunca desaparece.
 *
 * OJO semantica 26.2 (bytecode verificado): applyDamage recibe el NUEVO TOTAL
 * de dano (hurtAndBreak llama applyDamage(getDamageValue() + delta, ...)), no
 * un delta. Si ese total alcanza maxDamage, fijamos damage=maxDamage
 * (isBroken=true, barra vacia, inutilizable via GlasscutterItem) y cancelamos
 * ANTES del shrink(1) de vanilla.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {

    @Inject(method = "applyDamage", at = @At("HEAD"), cancellable = true)
    private void glasscraft$preventBreak(int newDamage, ServerPlayer player, Consumer<Item> onBroken, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (!(self.getItem() instanceof GlasscutterItem)) return;

        if (newDamage >= self.getMaxDamage()) {
            self.setDamageValue(self.getMaxDamage());
            ci.cancel();
        }
    }
}
