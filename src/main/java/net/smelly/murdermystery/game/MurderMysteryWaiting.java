package net.smelly.murdermystery.game;

import java.util.concurrent.CompletableFuture;

import net.gegy1000.plasmid.game.GameWorld;
import net.gegy1000.plasmid.game.GameWorldState;
import net.gegy1000.plasmid.game.StartResult;
import net.gegy1000.plasmid.game.event.OfferPlayerListener;
import net.gegy1000.plasmid.game.event.PlayerAddListener;
import net.gegy1000.plasmid.game.event.PlayerDeathListener;
import net.gegy1000.plasmid.game.event.RequestStartListener;
import net.gegy1000.plasmid.game.player.JoinResult;
import net.gegy1000.plasmid.game.rule.GameRule;
import net.gegy1000.plasmid.game.rule.RuleResult;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.smelly.murdermystery.game.map.MurderMysteryMap;
import net.smelly.murdermystery.game.map.MurderMysteryMapGenerator;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurderMysteryWaiting {
	private final GameWorld gameWorld;
	private final MurderMysteryMap map;
	private final MurderMysteryConfig config;
	private final MurderMysterySpawnLogic spawnLogic;
	
	private MurderMysteryWaiting(GameWorld gameWorld, MurderMysteryMap map, MurderMysteryConfig config) {
		this.gameWorld = gameWorld;
		this.map = map;
		this.spawnLogic = new MurderMysterySpawnLogic(gameWorld, map.config, true);
		this.config = config;
	}
	
	public static CompletableFuture<Void> open(GameWorldState worldState, MurderMysteryConfig config) {
		MurderMysteryMapGenerator generator = new MurderMysteryMapGenerator(config.mapConfig);
		return generator.create().thenAccept(map -> {
			GameWorld gameWorld = worldState.openWorld(map.asGenerator());
			MurderMysteryWaiting waiting = new MurderMysteryWaiting(gameWorld, map, config);
			gameWorld.newGame(game -> {
				game.setRule(GameRule.ALLOW_CRAFTING, RuleResult.DENY);
				game.setRule(GameRule.ALLOW_PORTALS, RuleResult.DENY);
				game.setRule(GameRule.ALLOW_PVP, RuleResult.DENY);
				game.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);
				game.setRule(GameRule.ENABLE_HUNGER, RuleResult.DENY);
				
				game.on(RequestStartListener.EVENT, waiting::requestStart);
				game.on(OfferPlayerListener.EVENT, waiting::offerPlayer);
				game.on(PlayerAddListener.EVENT, waiting::addPlayer);
				game.on(PlayerDeathListener.EVENT, waiting::onPlayerDeath);
			});
		});
	}
	
	private StartResult requestStart() {
		if (this.gameWorld.getPlayerCount() < this.config.players.getMinPlayers()) return StartResult.notEnoughPlayers();
		MurderMysteryActive.open(this.gameWorld, this.map, this.config);
		return StartResult.ok();
	}
	
	private JoinResult offerPlayer(ServerPlayerEntity player) {
		return this.gameWorld.getPlayerCount() >= this.config.players.getMaxPlayers() ? JoinResult.gameFull() : JoinResult.ok();
	}
	
	private void addPlayer(ServerPlayerEntity player) {
		BlockPos platformPos = this.config.mapConfig.platformPos;
		player.teleport(this.gameWorld.getWorld(), platformPos.getX() + 0.5F, platformPos.getY(), platformPos.getZ() + 0.5F, 0.0F, 0.0F);
		this.spawnPlayer(player);
	}
	
	private boolean onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		this.spawnPlayer(player);
		return true;
	}
	
	private void spawnPlayer(ServerPlayerEntity player) {
		this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
		this.spawnLogic.spawnPlayer(player);
	}
}