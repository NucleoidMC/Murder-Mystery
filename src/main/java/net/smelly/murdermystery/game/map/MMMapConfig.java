package net.smelly.murdermystery.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.smelly.murdermystery.spawning.ConfiguredSpawnBoundPredicate;
import xyz.nucleoid.plasmid.util.BlockBounds;

import java.util.List;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MMMapConfig {
	private static final Codec<BlockBounds> BLOCK_BOUNDS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.fieldOf("min").forGetter(BlockBounds::getMin),
		BlockPos.CODEC.fieldOf("max").forGetter(BlockBounds::getMax)
	).apply(instance, BlockBounds::new));
	
	public static final Codec<MMMapConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("translation").forGetter(config -> config.translation),
		Identifier.CODEC.fieldOf("map").forGetter(config -> config.map),
		Identifier.CODEC.optionalFieldOf("biome", new Identifier("the_void")).forGetter(config -> config.biome),
		BLOCK_BOUNDS_CODEC.fieldOf("spawn_bounds").forGetter(config -> config.bounds),
		BlockPos.CODEC.fieldOf("waiting_position").forGetter(config -> config.platformPos),
		ConfiguredSpawnBoundPredicate.CODEC.listOf().fieldOf("spawn_predicates").forGetter(config -> config.predicates)
	).apply(instance, MMMapConfig::new));
	
	public final String translation;
	public final Identifier map;
	public final Identifier biome;
	public final BlockBounds bounds;
	public final BlockPos platformPos;
	public final List<ConfiguredSpawnBoundPredicate<?>> predicates;
	
	public MMMapConfig(String translation, Identifier map, Identifier biome, BlockBounds bounds, BlockPos platformPos, List<ConfiguredSpawnBoundPredicate<?>> predicates) {
		this.translation = translation;
		this.map = map;
		this.biome = biome;
		this.bounds = bounds;
		this.platformPos = platformPos;
		this.predicates = predicates;
	}
}
