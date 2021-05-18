package net.smelly.murdermystery.game.custom;

import eu.pb4.polymer.item.VirtualItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import xyz.nucleoid.plasmid.fake.FakeItem;

public final class DetectiveBowItem extends BowItem implements VirtualItem {
    public DetectiveBowItem() {
        super(new Item.Settings().maxCount(1));
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClient && user instanceof ServerPlayerEntity) {
            ((ServerPlayerEntity) user).getItemCooldownManager().set(stack.getItem(), 100);
        }
        super.onStoppedUsing(stack, world, user, remainingUseTicks);
    }

    @Override
    public Item getVirtualItem() {
        return Items.BOW;
    }
}
