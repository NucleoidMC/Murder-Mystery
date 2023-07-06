package net.smelly.murdermystery.game.map;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.world.biome.Biome;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.plasmid.game.GameOpenException;

import java.io.IOException;

/**
 * @author SmellyModder (Luke Tonon)
 */
public record MMMapGenerator(MMMapConfig config) {

	@SuppressWarnings("unchecked")
	public MMMap create(MinecraftServer server) {
		MapTemplate template;
		try {
			template = MapTemplateSerializer.loadFromResource(server, this.config.map());
		} catch (IOException e) {
			throw new GameOpenException(Text.translatable("text.murder_mystery.load_map_error"), e);
		}

		MMMap map = new MMMap(this.config);
		template.setBiome((RegistryKey<Biome>) RegistryKey.INSTANCES.get((RegistryKeys.BIOME.getValue() + ":" + this.config.biome()).intern()));
		return map;
	}
}
