package net.smelly.murdermystery.game;

import com.google.common.collect.Maps;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.smelly.murdermystery.game.map.MMMap;
import net.smelly.murdermystery.game.map.MMMapGenerator;
import net.smelly.murdermystery.spawning.ConfiguredSpawnBoundPredicate;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameWaitingLobby;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.StartResult;
import xyz.nucleoid.plasmid.game.event.GameTickListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.RequestStartListener;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.world.bubble.BubbleWorldConfig;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MMWaiting {
	private static final String MURDERER_CHANCE = "Murderer Chance: ";
	private static final String SEPARATOR = " - ";
	private static final String DETECTIVE_CHANCE = "Detective Chance: ";
	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0");
	public static final HashMap<UUID, Integer> DETECTIVE_WEIGHT_MAP = Maps.newHashMap();
	public static final HashMap<UUID, Integer> MURDERER_WEIGHT_MAP = Maps.newHashMap();
	
	private final GameWorld gameWorld;
	private final MMMap map;
	private final MMConfig config;
	private final MMSpawnLogic spawnLogic;
	private final BiPredicate<ServerWorld, BlockPos.Mutable> spawnPredicate;
	
	private MMWaiting(GameWorld gameWorld, MMMap map, MMConfig config) {
		this.gameWorld = gameWorld;
		this.map = map;
		this.spawnLogic = new MMSpawnLogic(gameWorld, map.config);
		this.config = config;
		this.spawnPredicate = loadPredicates(config.mapConfig.predicates);
	}
	
	private BiPredicate<ServerWorld, BlockPos.Mutable> loadPredicates(List<ConfiguredSpawnBoundPredicate<?>> predicates) {
		BiPredicate<ServerWorld, BlockPos.Mutable> basePredicate = (world, pos) -> true;
		for (ConfiguredSpawnBoundPredicate<?> configuredPredicate : predicates) {
			configuredPredicate.loadConfig();
			basePredicate = basePredicate.and(configuredPredicate.getPredicate());
		}
		return basePredicate;
	}
	
	public static CompletableFuture<GameWorld> open(GameOpenContext<MMConfig> context) {
		MMConfig config = context.getConfig();
		MMMapGenerator generator = new MMMapGenerator(config.mapConfig);
		
		return generator.create().thenCompose(map -> {
			BubbleWorldConfig worldConfig = new BubbleWorldConfig().setGenerator(map.asGenerator(context.getServer())).setDefaultGameMode(GameMode.SPECTATOR);
			return context.openWorld(worldConfig).thenApply(gameWorld -> {
				MMWaiting waiting = new MMWaiting(gameWorld, map, config);
				
				return GameWaitingLobby.open(gameWorld, config.players, game -> {
					game.setRule(GameRule.CRAFTING, RuleResult.DENY);
					game.setRule(GameRule.PORTALS, RuleResult.DENY);
					game.setRule(GameRule.PVP, RuleResult.DENY);
					game.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);
					game.setRule(GameRule.HUNGER, RuleResult.DENY);
					
					game.on(RequestStartListener.EVENT, waiting::requestStart);
					game.on(PlayerAddListener.EVENT, waiting::addPlayer);
					game.on(PlayerDeathListener.EVENT, waiting::onPlayerDeath);
					
					game.on(GameTickListener.EVENT, waiting::tick);
				});
			});
		});
	}
	
	private void tick() {
		Set<ServerPlayerEntity> players = this.gameWorld.getPlayers();
		if (this.gameWorld.getWorld().getTime() % 5 == 0) {
			Pair<Integer, Integer> totalWeights = this.getTotalWeight(players);
			for (ServerPlayerEntity player : players) {
				player.networkHandler.sendPacket(
					new TitleS2CPacket(
						TitleS2CPacket.Action.ACTIONBAR,
						new LiteralText(Formatting.RED + MURDERER_CHANCE).formatted(Formatting.RESET).append(new LiteralText(this.getFormattedChance(player, totalWeights.getLeft(), true)))
							.append(new LiteralText(SEPARATOR).formatted(Formatting.GRAY))
							.append(new LiteralText(DETECTIVE_CHANCE).formatted(Formatting.BLUE)).formatted(Formatting.RESET).append(new LiteralText(this.getFormattedChance(player, totalWeights.getRight(), false)))
					)
				);
			}
		}
	}
	
	private StartResult requestStart() {
		MMActive.open(this.gameWorld, this.map, this.config, this.spawnPredicate);
		return StartResult.OK;
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
	
	private Pair<Integer, Integer> getTotalWeight(Set<ServerPlayerEntity> players) {
		int totalMurdererWeight = 0;
		int totalDetectiveWeight = 0;
		for (ServerPlayerEntity player : players) {
			UUID playerUUID = player.getUuid();
			totalMurdererWeight += MURDERER_WEIGHT_MAP.getOrDefault(playerUUID, 1);
			totalDetectiveWeight += DETECTIVE_WEIGHT_MAP.getOrDefault(playerUUID, 1);
		}
		return new Pair<>(totalMurdererWeight, totalDetectiveWeight);
	}
	
	private String getFormattedChance(ServerPlayerEntity player, int totalWeight, boolean murderer) {
		return DECIMAL_FORMAT.format(100.0F * ((float) (murderer ? MURDERER_WEIGHT_MAP.getOrDefault(player.getUuid(), 1) : DETECTIVE_WEIGHT_MAP.getOrDefault(player.getUuid(), 1)) / (float) totalWeight)) + "%";
	}
}