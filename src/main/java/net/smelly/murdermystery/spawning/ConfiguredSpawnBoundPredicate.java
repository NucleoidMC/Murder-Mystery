package net.smelly.murdermystery.spawning;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * @author SmellyModder (Luke Tonon)
 */
public record ConfiguredSpawnBoundPredicate<C>(SpawnBoundPredicate<C> predicate, C config) {
	public static final Codec<ConfiguredSpawnBoundPredicate<?>> CODEC = SpawnBoundPredicates.REGISTRY.dispatchStable(c -> c.predicate, ConfiguredSpawnBoundPredicate::codecFor);

	public SpawnBoundPredicate<C> getPredicate() {
		return this.predicate;
	}

	public C getConfig() {
		return this.config;
	}

	public void loadConfig() {
		this.predicate.loadConfig(this.config);
	}

	private static <C> Codec<? extends ConfiguredSpawnBoundPredicate<C>> codecFor(SpawnBoundPredicate<C> predicate) {
		Codec<C> configCodec = predicate.getCodec();
		if (configCodec instanceof MapCodec.MapCodecCodec) {
			MapCodec<C> codec = ((MapCodec.MapCodecCodec<C>) configCodec).codec();
			return xmapMapCodec(predicate, codec).codec();
		} else {
			return xmapCodec(predicate, configCodec);
		}
	}

	private static <C> MapCodec<? extends ConfiguredSpawnBoundPredicate<C>> xmapMapCodec(SpawnBoundPredicate<C> predicate, MapCodec<C> codec) {
		return codec.xmap(
				config -> new ConfiguredSpawnBoundPredicate<>(predicate, config),
				configured -> configured.config
		);
	}

	private static <C> Codec<? extends ConfiguredSpawnBoundPredicate<C>> xmapCodec(SpawnBoundPredicate<C> predicate, Codec<C> codec) {
		return codec.xmap(
				config -> new ConfiguredSpawnBoundPredicate<>(predicate, config),
				configured -> configured.config
		);
	}
}