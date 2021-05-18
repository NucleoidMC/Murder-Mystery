package net.smelly.murdermystery.game.custom;

import eu.pb4.polymer.item.VirtualItem;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import xyz.nucleoid.plasmid.fake.FakeItem;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurdererBladeItem extends Item implements VirtualItem {

	public MurdererBladeItem() {
		super(new Item.Settings().maxCount(1));
	}
	
	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		if (!world.isClient) {
			ItemCooldownManager manager = user.getItemCooldownManager();
			if (!manager.isCoolingDown(this)) {
				ItemStack stack = user.getStackInHand(hand);
				manager.set(this, 100);
				user.swingHand(hand);
				stack.decrement(1);
				MurdererBladeEntity.throwBlade(user);
				return TypedActionResult.success(stack);
			}
		}
		return super.use(world, user, hand);
	}

	@Override
	public Item getVirtualItem() {
		return Items.NETHERITE_SWORD;
	}
}