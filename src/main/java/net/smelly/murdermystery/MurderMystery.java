package net.smelly.murdermystery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.ModInitializer;
import xyz.nucleoid.plasmid.game.GameType;
import net.minecraft.util.Identifier;
import net.smelly.murdermystery.game.MurderMysteryConfig;
import net.smelly.murdermystery.game.MurderMysteryWaiting;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurderMystery implements ModInitializer {
	public static final String MOD_ID = "murder_mystery";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
	public static final GameType<MurderMysteryConfig> TYPE = GameType.register(new Identifier(MOD_ID, "murder_mystery"), MurderMysteryWaiting::open, MurderMysteryConfig.CODEC);
	
	@Override
	public void onInitialize() {}
}
