package net.smelly.murdermystery.game;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiPredicate;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.smelly.murdermystery.game.map.MMMapConfig;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MMSpawnLogic {
	private final MMMapConfig config;
	private final ServerWorld world;
	private final SpawnBounds bounds;
	private final Set<CoinSpawner> spawners = Sets.newHashSet();
	private final boolean waiting;

	public MMSpawnLogic(GameWorld gameWorld, MMMapConfig config, BiPredicate<ServerWorld, BlockPos.Mutable> spawnPredicate, boolean waiting) {
		this.config = config;
		this.waiting = waiting;
		this.world = gameWorld.getWorld();
		this.bounds = new SpawnBounds(config.bounds.getMin(), config.bounds.getMax(), this.world, spawnPredicate);
	}
	
	public MMSpawnLogic(GameWorld gameWorld, MMMapConfig config) {
		this(gameWorld, config, (world, pos) -> false, true);
	}
	
	public void tick() {
		if (this.waiting) return;
		this.spawners.forEach(CoinSpawner::tick);
	}
	
	public void populateCoinGenerators() {
		double averageBounds = this.bounds.getAverageSideLength();
		for (int i = 0; i < averageBounds / 2; i++) {
			this.spawners.add(new CoinSpawner(this.world, this.bounds.getRandomSpawnPos(this.world.random), averageBounds));
		}
	}

	public void resetPlayer(ServerPlayerEntity player, GameMode gameMode) {
		player.setHealth(20.0F);
		player.getHungerManager().setFoodLevel(20);
		player.fallDistance = 0.0F;
		player.setGameMode(gameMode);
	}
	
	public void spawnPlayer(ServerPlayerEntity player) {
		if (this.waiting) {
			BlockPos platformPos = this.config.platformPos;
			player.teleport(this.world, platformPos.getX() + 0.5F, platformPos.getY(), platformPos.getZ() + 0.5F, 0.0F, 0.0F);
		} else {
			BlockPos spawnPos = this.bounds.getRandomSpawnPos(new Random());
			player.teleport(this.world, spawnPos.getX() + 0.5F, spawnPos.getY(), spawnPos.getZ() + 0.5F, 0.0F, 0.0F);
		}
	}
	
	static class SpawnBounds {
		private final BlockPos min, max;
		private final List<BlockPos> positions = Lists.newArrayList();
		
		SpawnBounds(BlockPos min, BlockPos max, ServerWorld world, BiPredicate<ServerWorld, BlockPos.Mutable> spawnPredicate) {
			this.min = min;
			this.max = max;
			BlockPos.Mutable pos = new BlockPos.Mutable();
			for (int x = min.getX(); x < max.getX(); x++) {
				for (int y = min.getY(); y < max.getY(); y++) {
					for (int z = min.getZ(); z < max.getZ(); z++) {
						pos.set(x, y, z);
						if (spawnPredicate.test(world, pos)) this.positions.add(pos.toImmutable());
					}
				}
			}
		}
		
		public BlockPos getRandomSpawnPos(Random rand) {
			return this.positions.get(rand.nextInt(this.positions.size()));
		}
		
		public double getAverageSideLength() {
			return new Box(this.min, this.max).getAverageSideLength();
		}
	}
	
	static class CoinSpawner {
		private final ServerWorld world;
		private final BlockPos pos;
		private int spawnDelay = new Random().nextInt(300) + 200;
		private final int minSpawnDelay;
		private final int maxSpawnDelay;
		private final int spawnRange = 4;
		private final int maxNearbyCoins = 3;
		
		public CoinSpawner(ServerWorld world, BlockPos pos, double averageBounds) {
			this.world = world;
			this.pos = pos;
			this.minSpawnDelay = (int) (averageBounds * 20.0D);
			this.maxSpawnDelay = (int) (averageBounds * 30.0D);
		}
		
		public void tick() {
			if (this.spawnDelay == -1) this.resetTimer();

			if (this.spawnDelay > 0) {
				this.spawnDelay--;
				return;
			}

			boolean spawnedCoin = false;

			double x = (double) this.pos.getX() + (this.world.random.nextDouble() - this.world.random.nextDouble()) * (double) this.spawnRange + 0.5D;
			double y = (double) (this.pos.getY() + this.world.random.nextInt(3) - 1);
			double z = (double) this.pos.getZ() + (this.world.random.nextDouble() - this.world.random.nextDouble()) * (double) this.spawnRange + 0.5D;
			if (this.world.doesNotCollide(EntityType.ITEM.createSimpleBoundingBox(x, y, z))) {
				BlockPos underPos = new BlockPos(x, y - 1, z);
				if (this.world.getBlockState(underPos).isSolidBlock(this.world, underPos)) {
					int nearbyCoins = this.world.getEntitiesByType(EntityType.ITEM, new Box(this.pos.getX(), this.pos.getY(), this.pos.getZ(), this.pos.getX() + 1, this.pos.getY() + 1, this.pos.getZ() + 1).expand(this.spawnRange), (item) -> item.getStack().getItem() == Items.SUNFLOWER).size();
					if (nearbyCoins >= this.maxNearbyCoins) {
						this.resetTimer();
						return;
					}
						
					ItemEntity coin = new ItemEntity(this.world, x, y, z, ItemStackBuilder.of(Items.SUNFLOWER).setName(new LiteralText("Coins")).build());
					coin.refreshPositionAndAngles(x, y, z, this.world.random.nextFloat() * 360.0F, 0.0F);
					this.world.spawnEntity(coin);
					spawnedCoin = true;
				}
			}

			if (spawnedCoin) this.resetTimer();
		}
		
		private void resetTimer() {
			if (this.maxSpawnDelay <= this.minSpawnDelay) {
				this.spawnDelay = this.minSpawnDelay;
			} else {
				this.spawnDelay = this.minSpawnDelay + this.world.random.nextInt(this.maxSpawnDelay - this.minSpawnDelay);
			}
		}
	}
}