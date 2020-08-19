package net.smelly.murdermystery.game.map;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.smelly.murdermystery.spawning.ConfiguredSpawnBoundPredicate;
import xyz.nucleoid.plasmid.util.BlockBounds;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MMMapConfig {
	private static final Codec<BlockBounds> BLOCK_BOUNDS_CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
			BlockPos.field_25064.fieldOf("min").forGetter(bounds -> bounds.getMin()),
			BlockPos.field_25064.fieldOf("max").forGetter(bounds -> bounds.getMax())
		).apply(instance, BlockBounds::new);
	});
	
	public static final Codec<MMMapConfig> CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
			Identifier.CODEC.fieldOf("map").forGetter(config -> config.map),
			Codec.STRING.fieldOf("name").forGetter(config -> config.name),
			BLOCK_BOUNDS_CODEC.fieldOf("spawn_bounds").forGetter(config -> config.bounds),
			BlockPos.field_25064.fieldOf("waiting_position").forGetter(config -> config.platformPos),
			ConfiguredSpawnBoundPredicate.CODEC.listOf().fieldOf("spawn_predicates").forGetter(config -> config.predicates)
		).apply(instance, MMMapConfig::new);
	});
	
	public final Identifier map;
	public final String name;
	public final BlockBounds bounds;
	public final BlockPos platformPos;
	public final List<ConfiguredSpawnBoundPredicate<?>> predicates;
	
	public MMMapConfig(Identifier map, String name, BlockBounds bounds, BlockPos platformPos, List<ConfiguredSpawnBoundPredicate<?>> predicates) {
		this.map = map;
		this.name = name;
		this.bounds = bounds;
		this.platformPos = platformPos;
		this.predicates = predicates;
	}
}