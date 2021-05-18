package net.smelly.murdermystery.game;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.smelly.murdermystery.MurderMystery;
import net.smelly.murdermystery.game.map.MMMap;
import net.smelly.murdermystery.game.map.MMMapGenerator;
import net.smelly.murdermystery.spawning.ConfiguredSpawnBoundPredicate;
import xyz.nucleoid.fantasy.BubbleWorldConfig;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.GameWaitingLobby;
import xyz.nucleoid.plasmid.game.StartResult;
import xyz.nucleoid.plasmid.game.event.GameTickListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.RequestStartListener;
import xyz.nucleoid.plasmid.game.player.PlayerSet;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;

import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MMWaiting {
	private static final String SEPARATOR = " - ";
	private static final String DETECTIVE_CHANCE = "Detective Chance: ";
	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0");
	
	private final GameSpace gameSpace;
	private final MMMap map;
	private final MMConfig config;
	private final MMSpawnLogic spawnLogic;
	private final BiPredicate<ServerWorld, BlockPos.Mutable> spawnPredicate;
	
	private MMWaiting(GameSpace gameSpace, MMMap map, MMConfig config) {
		this.gameSpace = gameSpace;
		this.map = map;
		this.spawnLogic = new MMSpawnLogic(gameSpace, map.config);
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
	
	public static GameOpenProcedure open(GameOpenContext<MMConfig> context) {
		MMConfig config = context.getConfig();
		MMMapGenerator generator = new MMMapGenerator(config.mapConfig);
		MMMap map = generator.create();

		BubbleWorldConfig worldConfig = new BubbleWorldConfig().setGenerator(map.asGenerator(context.getServer())).setDefaultGameMode(GameMode.SPECTATOR);

		return context.createOpenProcedure(worldConfig, game -> {
			MMWaiting waiting = new MMWaiting(game.getSpace(), map, config);

			GameWaitingLobby.applyTo(game, config.players);

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
	}
	
	private void tick() {
		PlayerSet players = gameSpace.getPlayers();
		if (this.gameSpace.getWorld().getTime() % 5 == 0) {
			Pair<Integer, Integer> totalWeights = this.getTotalWeight(players);
			for (ServerPlayerEntity player : players) {
				player.networkHandler.sendPacket(
					new TitleS2CPacket(
						TitleS2CPacket.Action.ACTIONBAR,
						new TranslatableText("text.murder_mystery.murderer_chance", new LiteralText(this.getFormattedChance(player, totalWeights.getLeft(), true)).formatted(Formatting.WHITE)).formatted(Formatting.RED)
							.append(new LiteralText(SEPARATOR).formatted(Formatting.GRAY))
							.append(new TranslatableText("text.murder_mystery.detective_chance", new LiteralText(this.getFormattedChance(player, totalWeights.getRight(), false)).formatted(Formatting.WHITE)).formatted(Formatting.BLUE))
					)
				);
			}
		}
	}
	
	private StartResult requestStart() {
		MMActive.open(this.gameSpace, this.map, this.config, this.spawnPredicate);
		return StartResult.OK;
	}
	
	private void addPlayer(ServerPlayerEntity player) {
		BlockPos platformPos = this.config.mapConfig.platformPos;
		player.teleport(this.gameSpace.getWorld(), platformPos.getX() + 0.5F, platformPos.getY(), platformPos.getZ() + 0.5F, 0.0F, 0.0F);
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
	
	private Pair<Integer, Integer> getTotalWeight(Iterable<ServerPlayerEntity> players) {
		int totalMurdererWeight = 0;
		int totalDetectiveWeight = 0;
		for (ServerPlayerEntity player : players) {
			UUID playerUUID = player.getUuid();
			totalMurdererWeight += MurderMystery.WEIGHT_STORAGE.getPlayerWeight(playerUUID, true);
			totalDetectiveWeight += MurderMystery.WEIGHT_STORAGE.getPlayerWeight(playerUUID, false);
		}
		return new Pair<>(totalMurdererWeight, totalDetectiveWeight);
	}
	
	private String getFormattedChance(ServerPlayerEntity player, int totalWeight, boolean murderer) {
		return DECIMAL_FORMAT.format(100.0F * ((float) MurderMystery.WEIGHT_STORAGE.getPlayerWeight(player.getUuid(), murderer) / (float) totalWeight)) + "%";
	}
}
