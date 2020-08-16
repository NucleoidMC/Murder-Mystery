package net.smelly.murdermystery.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.plasmid.util.BlockBounds;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurderMysteryMapConfig {
	private static final Codec<BlockBounds> BLOCK_BOUNDS_CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
			BlockPos.field_25064.fieldOf("min").forGetter(bounds -> bounds.getMin()),
			BlockPos.field_25064.fieldOf("max").forGetter(bounds -> bounds.getMax())
		).apply(instance, BlockBounds::new);
	});
	
	public static final Codec<MurderMysteryMapConfig> CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
			Identifier.CODEC.fieldOf("map").forGetter(config -> config.map),
			BLOCK_BOUNDS_CODEC.fieldOf("spawn_bounds").forGetter(config -> config.bounds),
			BlockPos.field_25064.fieldOf("platform_position").forGetter(config -> config.platformPos)
		).apply(instance, MurderMysteryMapConfig::new);
	});
	
	public final Identifier map;
	public final BlockBounds bounds;
	public final BlockPos platformPos;
	
	public MurderMysteryMapConfig(Identifier map, BlockBounds bounds, BlockPos platformPos) {
		this.map = map;
		this.bounds = bounds;
		this.platformPos = platformPos;
	}
}