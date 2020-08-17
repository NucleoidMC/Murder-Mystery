package net.smelly.murdermystery.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.smelly.murdermystery.game.map.MurderMysteryMapConfig;
import xyz.nucleoid.plasmid.game.config.PlayerConfig;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurderMysteryConfig {
	public static final Codec<MurderMysteryConfig> CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
			MurderMysteryMapConfig.CODEC.fieldOf("map").forGetter(config -> config.mapConfig),
			PlayerConfig.CODEC.fieldOf("players").forGetter(config -> config.players),
			Codec.INT.optionalFieldOf("game_duration", 6000).forGetter(config -> config.gameDuration)
		).apply(instance, MurderMysteryConfig::new);
	});
	
	public final MurderMysteryMapConfig mapConfig;
	public final PlayerConfig players;
	public final int gameDuration;
	
	public MurderMysteryConfig(MurderMysteryMapConfig mapConfig, PlayerConfig players, int gameDuration) {
		this.mapConfig = mapConfig;
		this.players = players;
		this.gameDuration = gameDuration;
	}
}