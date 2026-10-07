package com.glasscraft.common.mixin;

import com.glasscraft.common.init.GlasscraftItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Patron elytra: el Glasscutter nunca desaparece. Al llegar al ultimo punto de
 * durabilidad queda damage == maxDamage (isBroken() == true, inutilizable,
 * barra vacia) pero NO se hace shrink(1) ni se dispara onDestroyed.
 * Se repara en yunque con hierro (componente repairable).
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {

    @Inject(method = "applyDamage", at = @At("HEAD"), cancellable = true)
    private void glasscraft$preventBreak(int damageAmount, ServerPlayer player, Consumer<Item> onBroken, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.getItem() != GlasscraftItems.glasscutter()) return;

        if (self.isDamageableItem() && self.getDamageValue() + damageAmount >= self.getMaxDamage()) {
            // Deja el item al maximo de dano: roto/inutilizable pero presente.
            self.setDamageValue(self.getMaxDamage());
            ci.cancel();
        }
    }
}
