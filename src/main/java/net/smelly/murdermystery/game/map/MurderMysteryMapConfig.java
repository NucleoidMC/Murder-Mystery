package net.smelly.murdermystery.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurderMysteryMapConfig {
	public static final Codec<MurderMysteryMapConfig> CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
			Identifier.CODEC.fieldOf("map").forGetter(config -> config.map),
			BlockPos.field_25064.fieldOf("platform_position").forGetter(config -> config.platformPos),
			BlockPos.field_25064.fieldOf("spawn_position").forGetter(config -> config.spawnPos)
		).apply(instance, MurderMysteryMapConfig::new);
    });
	
	public final Identifier map;
	public final BlockPos platformPos;
	public final BlockPos spawnPos;
	
	public MurderMysteryMapConfig(Identifier map, BlockPos platformPos, BlockPos spawnPos) {
		this.map = map;
		this.platformPos = platformPos;
		this.spawnPos = spawnPos;
	}
}