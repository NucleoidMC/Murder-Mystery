package net.smelly.murdermystery.game;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.smelly.murdermystery.game.map.MurderMysteryMap;
import net.smelly.murdermystery.game.map.MurderMysteryMapGenerator;
import net.smelly.murdermystery.spawning.ConfiguredSpawnBoundPredicate;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.StartResult;
import xyz.nucleoid.plasmid.game.event.OfferPlayerListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.RequestStartListener;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.world.bubble.BubbleWorldConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurderMysteryWaiting {
	private final GameWorld gameWorld;
	private final MurderMysteryMap map;
	private final MurderMysteryConfig config;
	private final MurderMysterySpawnLogic spawnLogic;
	private final BiPredicate<ServerWorld, BlockPos.Mutable> spawnPredicate;
	
	private MurderMysteryWaiting(GameWorld gameWorld, MurderMysteryMap map, MurderMysteryConfig config) {
		this.gameWorld = gameWorld;
		this.map = map;
		this.spawnLogic = new MurderMysterySpawnLogic(gameWorld, map.config);
		this.config = config;
		this.spawnPredicate = loadPredicates(config.mapConfig.predicates);
	}
	
	public static CompletableFuture<GameWorld> open(GameOpenContext<MurderMysteryConfig> context) {
		MurderMysteryConfig config = context.getConfig();
		MurderMysteryMapGenerator generator = new MurderMysteryMapGenerator(config.mapConfig);
		
		return generator.create().thenCompose(map -> {
			BubbleWorldConfig worldConfig = new BubbleWorldConfig().setGenerator(map.asGenerator(context.getServer())).setDefaultGameMode(GameMode.SPECTATOR);
			return context.openWorld(worldConfig).thenApply(gameWorld -> {
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
				
				return gameWorld;
			});
		});
	}
	
	private BiPredicate<ServerWorld, BlockPos.Mutable> loadPredicates(List<ConfiguredSpawnBoundPredicate<?>> predicates) {
		BiPredicate<ServerWorld, BlockPos.Mutable> basePredicate = (world, pos) -> true;
		for (ConfiguredSpawnBoundPredicate<?> configuredPredicate : predicates) {
			configuredPredicate.loadConfig();
			basePredicate = basePredicate.and(configuredPredicate.getPredicate());
		}
		return basePredicate;
	}
	
	private StartResult requestStart() {
		if (this.gameWorld.getPlayerCount() < this.config.players.getMinPlayers()) return StartResult.NOT_ENOUGH_PLAYERS;
		MurderMysteryActive.open(this.gameWorld, this.map, this.config, this.spawnPredicate);
		return StartResult.OK;
	}
	
	private JoinResult offerPlayer(ServerPlayerEntity player) {
		return this.gameWorld.getPlayerCount() >= this.config.players.getMaxPlayers() ? JoinResult.gameFull() : JoinResult.ok();
	}
	
	private void addPlayer(ServerPlayerEntity player) {
		BlockPos platformPos = this.config.mapConfig.platformPos;
		player.teleport(this.gameWorld.getWorld(), platformPos.getX() + 0.5F, platformPos.getY(), platformPos.getZ() + 0.5F, 0.0F, 0.0F);
		this.spawnPlayer(player);
	}
	
	private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		this.spawnPlayer(player);
		return ActionResult.FAIL;
	}
	
	private void spawnPlayer(ServerPlayerEntity player) {
		this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
		this.spawnLogic.spawnPlayer(player);
	}
}