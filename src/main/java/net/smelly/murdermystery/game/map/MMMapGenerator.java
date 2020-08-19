package net.smelly.murdermystery.game.map;

import net.minecraft.world.biome.BuiltinBiomes;
import xyz.nucleoid.plasmid.game.map.template.MapTemplateSerializer;

import java.util.concurrent.CompletableFuture;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MMMapGenerator {
	private final MMMapConfig config;

	public MMMapGenerator(MMMapConfig config) {
		this.config = config;
	}

	public CompletableFuture<MMMap> create() {
		return MapTemplateSerializer.INSTANCE.load(this.config.map).thenApply(template -> {
			MMMap map = new MMMap(template, this.config);
			//TODO: Make it use Dark Forest when mob spawning is fixed
			template.setBiome(BuiltinBiomes.THE_VOID);
			return map;
		});
	}
}