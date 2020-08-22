package net.smelly.murdermystery.game.map;

import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
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

	@SuppressWarnings("unchecked")
	public CompletableFuture<MMMap> create() {
		return MapTemplateSerializer.INSTANCE.load(this.config.map).thenApply(template -> {
			MMMap map = new MMMap(template, this.config);
			template.setBiome((RegistryKey<Biome>) RegistryKey.INSTANCES.get((Registry.BIOME_KEY.getValue() + ":" + this.config.biome).intern()));
			return map;
		});
	}
}