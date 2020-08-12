package net.smelly.murdermystery.game;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.smelly.murdermystery.game.map.MurderMysteryMap;
import net.smelly.murdermystery.game.map.MurderMysteryMapGenerator;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.StartResult;
import xyz.nucleoid.plasmid.game.event.OfferPlayerListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.RequestStartListener;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.game.world.bubble.BubbleWorldConfig;

import java.util.concurrent.CompletableFuture;

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
	
	public static CompletableFuture<Void> open(MinecraftServer server, MurderMysteryConfig config) {
		MurderMysteryMapGenerator generator = new MurderMysteryMapGenerator(config.mapConfig);
		return generator.create().thenAccept(map -> {
			BubbleWorldConfig worldConfig = new BubbleWorldConfig()
					.setGenerator(map.asGenerator(server))
					.setDefaultGameMode(GameMode.SPECTATOR);
			GameWorld gameWorld = GameWorld.open(server, worldConfig);
			MurderMysteryWaiting waiting = new MurderMysteryWaiting(gameWorld, map, config);
			gameWorld.openGame(game -> {
				game.setRule(GameRule.CRAFTING, RuleResult.DENY);
				game.setRule(GameRule.PORTALS, RuleResult.DENY);
				game.setRule(GameRule.PVP, RuleResult.DENY);
				game.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);
				game.setRule(GameRule.HUNGER, RuleResult.DENY);
				
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
