package net.smelly.murdermystery.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.smelly.murdermystery.game.map.MMMapConfig;
import xyz.nucleoid.plasmid.game.common.config.PlayerConfig;

/**
 * @author SmellyModder (Luke Tonon)
 */
public record MMConfig(MMMapConfig mapConfig, PlayerConfig players, int startDuration, int gameDuration) {
	public static final Codec<MMConfig> CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
				MMMapConfig.CODEC.fieldOf("map").forGetter(config -> config.mapConfig),
				PlayerConfig.CODEC.fieldOf("players").forGetter(config -> config.players),
				Codec.INT.optionalFieldOf("start_duration", 200).forGetter(config -> config.startDuration),
				Codec.INT.optionalFieldOf("game_duration", 6600).forGetter(config -> config.gameDuration)
		).apply(instance, MMConfig::new);
	});

}