package net.smelly.murdermystery.game;

import net.gegy1000.plasmid.game.GameWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.smelly.murdermystery.game.map.MurderMysteryMapConfig;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurderMysterySpawnLogic {
	private final GameWorld gameWorld;
	private final MurderMysteryMapConfig config;
	private final boolean waiting;

	public MurderMysterySpawnLogic(GameWorld gameWorld, MurderMysteryMapConfig config, boolean waiting) {
		this.gameWorld = gameWorld;
		this.config = config;
		this.waiting = waiting;
	}

	public void resetPlayer(ServerPlayerEntity player, GameMode gameMode) {
		player.inventory.clear();
		player.getEnderChestInventory().clear();
		player.clearStatusEffects();
		player.setHealth(20.0F);
		player.getHungerManager().setFoodLevel(20);
		player.fallDistance = 0.0F;
		player.setGameMode(gameMode);
    }
	
	public void spawnPlayer(ServerPlayerEntity player) {
		ServerWorld world = this.gameWorld.getWorld();
		if (this.waiting) {
			BlockPos platformPos = this.config.platformPos;
			player.teleport(world, platformPos.getX() + 0.5F, platformPos.getY(), platformPos.getZ() + 0.5F, 0.0F, 0.0F);
		} else {
			BlockPos spawnPos = this.config.spawnPos;
			player.teleport(world, spawnPos.getX() + 0.5F, spawnPos.getY(), spawnPos.getZ() + 0.5F, 0.0F, 0.0F);
		}
	}
}