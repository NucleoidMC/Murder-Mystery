package net.smelly.murdermystery.game.map;

import java.util.concurrent.CompletableFuture;

import net.gegy1000.plasmid.game.map.template.MapTemplateSerializer;
import net.minecraft.world.biome.Biomes;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurderMysteryMapGenerator {
	private final MurderMysteryMapConfig config;

	public MurderMysteryMapGenerator(MurderMysteryMapConfig config) {
		this.config = config;
	}

	public CompletableFuture<MurderMysteryMap> create() {
		return MapTemplateSerializer.load(config.map).thenApply(template -> {
			MurderMysteryMap map = new MurderMysteryMap(template, this.config);
			template.setBiome(Biomes.THE_VOID);
			return map;
		});
	}
}