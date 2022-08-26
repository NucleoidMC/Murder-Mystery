package net.smelly.murdermystery.game;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.smelly.murdermystery.MurderMystery;
import net.smelly.murdermystery.game.map.MMMap;
import net.smelly.murdermystery.game.map.MMMapGenerator;
import net.smelly.murdermystery.spawning.ConfiguredSpawnBoundPredicate;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.game.GameResult;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.player.PlayerOffer;
import xyz.nucleoid.plasmid.game.player.PlayerOfferResult;
import xyz.nucleoid.plasmid.game.player.PlayerSet;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MMWaiting {
	private static final String SEPARATOR = " - ";
	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0");

	private final GameSpace gameSpace;
	private final ServerWorld world;
	private final MMMap map;
	private final MMConfig config;
	private final MMSpawnLogic spawnLogic;
	private final BiPredicate<ServerWorld, BlockPos.Mutable> spawnPredicate;

	private MMWaiting(GameSpace gameSpace, ServerWorld world, MMMap map, MMConfig config) {
		this.gameSpace = gameSpace;
		this.world = world;
		this.map = map;
		this.spawnLogic = new MMSpawnLogic(world, map.config);
		this.config = config;
		this.spawnPredicate = loadPredicates(config.mapConfig().predicates());
	}

	public static GameOpenProcedure open(GameOpenContext<MMConfig> context) {
		MMConfig config = context.config();
		MMMapGenerator generator = new MMMapGenerator(config.mapConfig());
		MMMap map = generator.create(context.server());

		RuntimeWorldConfig worldConfig = new RuntimeWorldConfig().setGenerator(map.asGenerator(context.server()));

		return context.openWithWorld(worldConfig, (activity, world) -> {
			MMWaiting waiting = new MMWaiting(activity.getGameSpace(), world, map, config);

			GameWaitingLobby.addTo(activity, config.players());

			activity.deny(GameRuleType.CRAFTING);
			activity.deny(GameRuleType.PORTALS);
			activity.deny(GameRuleType.PVP);
			activity.deny(GameRuleType.FALL_DAMAGE);
			activity.deny(GameRuleType.HUNGER);

			activity.listen(GameActivityEvents.REQUEST_START, waiting::requestStart);
			activity.listen(GamePlayerEvents.OFFER, waiting::offerPlayer);
			activity.listen(PlayerDeathEvent.EVENT, waiting::onPlayerDeath);

			activity.listen(GameActivityEvents.TICK, waiting::tick);
		});
	}

	private BiPredicate<ServerWorld, BlockPos.Mutable> loadPredicates(List<ConfiguredSpawnBoundPredicate<?>> predicates) {
		BiPredicate<ServerWorld, BlockPos.Mutable> basePredicate = (world, pos) -> true;
		for(ConfiguredSpawnBoundPredicate<?> configuredPredicate : predicates) {
			configuredPredicate.loadConfig();
			basePredicate = basePredicate.and(configuredPredicate.getPredicate());
		}
		return basePredicate;
	}

	private void tick() {
		PlayerSet players = gameSpace.getPlayers();
		if(this.world.getTime() % 5 == 0) {
			Pair<Integer, Integer> totalWeights = this.getTotalWeight(players);
			for(ServerPlayerEntity player : players) {
				player.networkHandler.sendPacket(
						new SubtitleS2CPacket(
								Text.translatable("text.murder_mystery.murderer_chance", Text.literal(this.getFormattedChance(player, totalWeights.getLeft(), true)).formatted(Formatting.WHITE)).formatted(Formatting.RED)
										.append(Text.literal(SEPARATOR).formatted(Formatting.GRAY))
										.append(Text.translatable("text.murder_mystery.detective_chance", Text.literal(this.getFormattedChance(player, totalWeights.getRight(), false)).formatted(Formatting.WHITE)).formatted(Formatting.BLUE))
						)
				);
			}
		}
	}

	private GameResult requestStart() {
		MMActive.open(this.gameSpace, this.world, this.map, this.config, this.spawnPredicate);
		return GameResult.ok();
	}

	private PlayerOfferResult offerPlayer(PlayerOffer offer) {
		return offer.accept(this.world, Vec3d.ofCenter(this.config.mapConfig().platformPos().add(0.5F, 0.0F, 0.5F))).and(() -> this.spawnPlayer(offer.player()));
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
		for(ServerPlayerEntity player : players) {
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
