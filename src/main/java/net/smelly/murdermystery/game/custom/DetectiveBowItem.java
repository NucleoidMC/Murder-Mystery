package net.smelly.murdermystery.game.custom;

import eu.pb4.polymer.api.item.PolymerItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class DetectiveBowItem extends BowItem implements PolymerItem {
	public DetectiveBowItem() {
		super(new Item.Settings().maxCount(1));
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if(!world.isClient && user instanceof ServerPlayerEntity) {
			((ServerPlayerEntity) user).getItemCooldownManager().set(stack.getItem(), 100);
		}
		super.onStoppedUsing(stack, world, user, remainingUseTicks);
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, @Nullable ServerPlayerEntity serverPlayerEntity) {
		return Items.BOW;
	}
}
