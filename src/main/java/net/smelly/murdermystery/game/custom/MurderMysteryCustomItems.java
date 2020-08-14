package net.smelly.murdermystery.game.custom;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.smelly.murdermystery.MurderMystery;

public final class MurderMysteryCustomItems {
	public static final Item DETECTIVE_BOW = register("detective_bow", new DetectiveBowItem());
	public static final Item MURDERER_BLADE = register("murderer_blade", new SimpleFakeItem(Items.NETHERITE_SWORD));

	private static <T extends Item> T register(String identifier, T item) {
		return Registry.register(Registry.ITEM, new Identifier(MurderMystery.MOD_ID, identifier), item);
	}
}
