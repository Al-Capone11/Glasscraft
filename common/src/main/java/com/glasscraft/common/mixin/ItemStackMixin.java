package com.glasscraft.common.mixin;

import com.glasscraft.common.item.GlasscutterItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
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
 * Semantica 26.2 (bytecode verificado): applyDamage recibe el NUEVO TOTAL de
 * dano, no un delta. Si ese total alcanza maxDamage, fijamos damage=maxDamage
 * (isBroken=true, barra vacia, inutilizable via GlasscutterItem) y cancelamos
 * ANTES del shrink(1).
 *
 * Multi-loader: vanilla/Fabric 26.2 solo tiene applyDamage(int, ServerPlayer,
 * Consumer) — este mixin va en la config compartida. El jar de NeoForge
 * 26.2.0.88 añade el overload applyDamage(int, LivingEntity, Consumer) por el
 * que pasa toda la ruta real de dano; lo cubre ItemStackMixinNeoForge, que
 * solo referencia la config glasscraft-neoforge.mixins.json. Cada config
 * referencia unicamente metodos que existen en su jar.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {

    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private void glasscraft$preventBreak(int newDamage, ServerPlayer player, Consumer<Item> onBroken, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (!(self.getItem() instanceof GlasscutterItem)) return;

        if (newDamage >= self.getMaxDamage()) {
            self.setDamageValue(self.getMaxDamage());
            ci.cancel();
        }
    }
}
