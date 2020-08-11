package net.smelly.murdermystery.game.custom;

import xyz.nucleoid.plasmid.item.CustomItem;
import net.minecraft.util.Identifier;
import net.smelly.murdermystery.MurderMystery;

public final class MurderMysteryCustomItems {
	public static final CustomItem DETECTIVE_BOW = CustomItem.builder().id(new Identifier(MurderMystery.MOD_ID, "detective_bow")).register();
	public static final CustomItem MURDERER_BLADE = CustomItem.builder().id(new Identifier(MurderMystery.MOD_ID, "murderer_blade")).register();
}
