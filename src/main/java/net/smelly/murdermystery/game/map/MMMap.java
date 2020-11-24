package net.smelly.murdermystery.game.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import xyz.nucleoid.plasmid.map.template.MapTemplate;
import xyz.nucleoid.plasmid.map.template.TemplateChunkGenerator;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MMMap {
	public final MapTemplate template;
	public final MMMapConfig config;
	
	public MMMap(MapTemplate map, MMMapConfig config) {
		this.template = map;
		this.config = config;
	}
	
	public ChunkGenerator asGenerator(MinecraftServer server) {
		return new TemplateChunkGenerator(server, this.template);
	}
}
