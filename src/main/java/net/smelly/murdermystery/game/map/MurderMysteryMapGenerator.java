package net.smelly.murdermystery.game.map;

import net.minecraft.world.biome.BuiltinBiomes;
import xyz.nucleoid.plasmid.game.map.template.MapTemplateSerializer;

import java.util.concurrent.CompletableFuture;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurderMysteryMapGenerator {
	private final MurderMysteryMapConfig config;

	public MurderMysteryMapGenerator(MurderMysteryMapConfig config) {
		this.config = config;
	}

	public CompletableFuture<MurderMysteryMap> create() {
		return MapTemplateSerializer.INSTANCE.load(this.config.map).thenApply(template -> {
			MurderMysteryMap map = new MurderMysteryMap(template, this.config);
			template.setBiome(BuiltinBiomes.THE_VOID);
			return map;
		});
	}
}