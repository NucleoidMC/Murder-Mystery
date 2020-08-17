package net.smelly.murdermystery.spawning;

import net.minecraft.util.Identifier;
import net.smelly.murdermystery.MurderMystery;
import xyz.nucleoid.plasmid.registry.TinyRegistry;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class SpawnBoundPredicates {
	public static final TinyRegistry<SpawnBoundPredicate<?>> REGISTRY = TinyRegistry.newStable();
	
	static {
		register("standing_on", new StandingOnTagPredicate());
	}
	
	private static <P extends SpawnBoundPredicate<P>> P register(String name, P predicate) {
		REGISTRY.register(new Identifier(MurderMystery.MOD_ID, name), predicate);
		return predicate;
	}
}