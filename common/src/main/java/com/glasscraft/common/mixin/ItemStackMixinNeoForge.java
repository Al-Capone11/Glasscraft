package com.glasscraft.common.mixin;

import com.glasscraft.common.item.GlasscutterItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Variante NeoForge del patron elytra. Solo se referencia desde
 * glasscraft-neoforge.mixins.json: el jar parcheado de NeoForge 26.2.0.88
 * desvia TODA la ruta de dano de herramientas por el overload
 * applyDamage(int, LivingEntity, Consumer) (el de ServerPlayer queda como
 * delegador muerto). En vanilla/Fabric este metodo NO existe y este mixin no
 * se carga. Misma semantica: newDamage es el TOTAL nuevo; al llegar a
 * maxDamage se fija ahi y se cancela antes del shrink(1).
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixinNeoForge {

    @Inject(method = "applyDamage(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private void glasscraft$preventBreak(int newDamage, LivingEntity entity, Consumer<Item> onBroken, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (!(self.getItem() instanceof GlasscutterItem)) return;

        if (newDamage >= self.getMaxDamage()) {
            self.setDamageValue(self.getMaxDamage());
            ci.cancel();
        }
    }
}
