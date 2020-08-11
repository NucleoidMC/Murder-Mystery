package net.smelly.murdermystery.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import xyz.nucleoid.plasmid.item.CustomItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.smelly.murdermystery.game.custom.MurderMysteryCustomItems;

@Mixin(BowItem.class)
public class BowItemMixin {

	@Inject(method = "onStoppedUsing", at = @At("HEAD"))
	private void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks, CallbackInfo info) {
		if (!world.isClient && user instanceof ServerPlayerEntity && CustomItem.match(stack) == MurderMysteryCustomItems.DETECTIVE_BOW) {
			((ServerPlayerEntity) user).getItemCooldownManager().set(stack.getItem(), 100);
		}
	}
	
}
