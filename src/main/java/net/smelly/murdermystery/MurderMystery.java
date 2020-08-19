package net.smelly.murdermystery;

import com.google.common.reflect.Reflection;
import net.smelly.murdermystery.game.custom.MMCustomItems;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.tag.TagRegistry;
import xyz.nucleoid.plasmid.game.GameType;
import net.minecraft.block.Block;
import net.minecraft.tag.Tag;
import net.minecraft.util.Identifier;
import net.smelly.murdermystery.game.MMConfig;
import net.smelly.murdermystery.game.MMWaiting;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurderMystery implements ModInitializer {
	public static final String MOD_ID = "murder_mystery";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
	public static final GameType<MMConfig> TYPE = GameType.register(new Identifier(MOD_ID, "murder_mystery"), MMWaiting::open, MMConfig.CODEC);
	
	static final Tag<Block> DEFAULT_SPAWN_ON = TagRegistry.block(new Identifier(MOD_ID, "default_spawn_on"));
	
	@Override
	public void onInitialize() {
		Reflection.initialize(MMCustomItems.class);
	}
}