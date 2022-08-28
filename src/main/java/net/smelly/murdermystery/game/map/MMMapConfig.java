package net.smelly.murdermystery.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.smelly.murdermystery.spawning.ConfiguredSpawnBoundPredicate;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.List;

/**
 * @author SmellyModder (Luke Tonon)
 */
public record MMMapConfig(String name, Identifier map, Identifier biome, BlockBounds bounds, BlockPos platformPos, List<ConfiguredSpawnBoundPredicate<?>> predicates) {
	private static final Codec<BlockBounds> BLOCK_BOUNDS_CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
				BlockPos.CODEC.fieldOf("min").forGetter(BlockBounds::min),
				BlockPos.CODEC.fieldOf("max").forGetter(BlockBounds::max)
		).apply(instance, BlockBounds::new);
	});

	public static final Codec<MMMapConfig> CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
				Codec.STRING.fieldOf("name").forGetter(config -> config.name),
				Identifier.CODEC.fieldOf("map").forGetter(config -> config.map),
				Identifier.CODEC.optionalFieldOf("biome", new Identifier("the_void")).forGetter(config -> config.biome),
				BLOCK_BOUNDS_CODEC.fieldOf("spawn_bounds").forGetter(config -> config.bounds),
				BlockPos.CODEC.fieldOf("waiting_position").forGetter(config -> config.platformPos),
				ConfiguredSpawnBoundPredicate.CODEC.listOf().fieldOf("spawn_predicates").forGetter(config -> config.predicates)
		).apply(instance, MMMapConfig::new);
	});

}
