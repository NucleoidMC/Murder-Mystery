package net.smelly.murdermystery;

import com.google.common.reflect.Reflection;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.smelly.murdermystery.game.MMConfig;
import net.smelly.murdermystery.game.MMWaiting;
import net.smelly.murdermystery.game.custom.MMCustomItems;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.plasmid.game.GameType;
import xyz.nucleoid.plasmid.storage.ServerStorage;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurderMystery implements ModInitializer {
	public static final String MOD_ID = "murder_mystery";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
	public static final GameType<MMConfig> TYPE = GameType.register(new Identifier(MOD_ID, "murder_mystery"), MMConfig.CODEC, MMWaiting::open);
	public static final MMPlayerWeightStorage WEIGHT_STORAGE = ServerStorage.createStorage(new Identifier(MOD_ID, "weights"), new MMPlayerWeightStorage());

	static final TagKey<Block> MANSION_SPAWN_ON = TagKey.of(RegistryKeys.BLOCK, new Identifier(MOD_ID, "mansion_spawn_on"));
	static final TagKey<Block> METRO_SPAWN_ON = TagKey.of(RegistryKeys.BLOCK, new Identifier(MOD_ID, "metro_spawn_on"));

	@Override
	public void onInitialize() {
		Reflection.initialize(MMCustomItems.class);
	}
}
