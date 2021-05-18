package net.smelly.murdermystery.game.map;

import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import xyz.nucleoid.plasmid.game.GameOpenException;
import xyz.nucleoid.plasmid.map.template.MapTemplate;
import xyz.nucleoid.plasmid.map.template.MapTemplateSerializer;

import java.io.IOException;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MMMapGenerator {
	private final MMMapConfig config;

	public MMMapGenerator(MMMapConfig config) {
		this.config = config;
	}

	@SuppressWarnings("unchecked")
	public MMMap create() {
		MapTemplate template;
		try {
			template = MapTemplateSerializer.INSTANCE.loadFromResource(this.config.map);
		} catch (IOException e) {
			throw new GameOpenException(new TranslatableText("text.murder_mystery.load_map_error"), e);
		}

		MMMap map = new MMMap(template, this.config);
		template.setBiome((RegistryKey<Biome>) RegistryKey.INSTANCES.get((Registry.BIOME_KEY.getValue() + ":" + this.config.biome).intern()));
		return map;
	}
}
