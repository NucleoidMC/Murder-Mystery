package net.smelly.murdermystery.game.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.plasmid.game.world.generator.TemplateChunkGenerator;

import java.io.IOException;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MMMap {
	public final MMMapConfig config;

	public MMMap(MMMapConfig config) {;
		this.config = config;
	}

	public ChunkGenerator createGenerator(MinecraftServer server) throws IOException {
		MapTemplate template = MapTemplateSerializer.loadFromResource(server, this.config.map());
		return new TemplateChunkGenerator(server, template);
	}
}
