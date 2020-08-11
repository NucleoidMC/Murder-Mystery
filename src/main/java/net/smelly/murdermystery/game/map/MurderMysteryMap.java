package net.smelly.murdermystery.game.map;

import xyz.nucleoid.plasmid.game.map.template.MapTemplate;
import xyz.nucleoid.plasmid.game.map.template.TemplateChunkGenerator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurderMysteryMap {
	public final MapTemplate map;
	public final MurderMysteryMapConfig config;
	
	public MurderMysteryMap(MapTemplate map, MurderMysteryMapConfig config) {
		this.map = map;
		this.config = config;
	}
	
	public ChunkGenerator asGenerator() {
		return new TemplateChunkGenerator(this.map, BlockPos.ORIGIN);
	}
}
