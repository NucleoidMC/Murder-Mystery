package net.smelly.murdermystery.game;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.EulerAngle;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap.Type;
import net.smelly.murdermystery.game.custom.MurderMysteryCustomItems;
import net.smelly.murdermystery.game.map.MurderMysteryMap;

import xyz.nucleoid.plasmid.entity.FloatingText;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.event.GameCloseListener;
import xyz.nucleoid.plasmid.game.event.GameOpenListener;
import xyz.nucleoid.plasmid.game.event.GameTickListener;
import xyz.nucleoid.plasmid.game.event.OfferPlayerListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDamageListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author SmellyModder (Luke Tonon)
 * Much of the code in here supports more than one of something.
 * This is done to make support for multiple detectives and murderers easier later.
 */
public final class MurderMysteryActive {
	private static final Random RANDOM = new Random();
	private static final String[] DEATH_QUOTES = new String[] {
		"Skibbity bop mm dada!",
		"Oof",
		"Hey fellas!",
		"I did not get my Spaghetti-O's; I got spaghetti",
		"And now for a final word from our sponsor-",
		"Bring me a bullet-proof vest",
		"Thank god. I'm tired of being the funniest person in the room",
		"Surprise me",
		"I'm looking for loopholes",
		"Gun's not loaded... see?",
		"No",
		"Now why did I do that?",
		"Don't let it end like this. Tell them I said something important",
		"Haha... fool",
		"The tables seem to have turned...",
		"Hey! I saw this one!",
		"What are ya gonna do? stab me?",
		"Hey... you guys wanna see a dead body?",
		"10/10 would live again.",
		"Wow.",
		"It's beautiful, it's perfect, oh wow... just kidding"
	};
	
	public final GameWorld gameWorld;
	private final MurderMysteryConfig config;
	private final MurderMysterySpawnLogic spawnLogic;
	private final MurderMysteryScoreboard scoreboard;
	
	private final PlayerRoleMap roleMap = new PlayerRoleMap();
	private final Set<TimerTask<?>> tasks = Sets.newHashSet();
	public final Set<ArmorStandEntity> bows = Sets.newHashSet();
	
	private final ServerWorld world;
	private final Set<ServerPlayerEntity> participants;
	
	private int ticksTillStart = 200;
	public int ticksTillClose = -1;
	private long ticks = 0;
	
	private MurderMysteryActive(GameWorld gameWorld, MurderMysteryMap map, MurderMysteryConfig config, BiPredicate<ServerWorld, BlockPos.Mutable> spawnPredicate, Set<ServerPlayerEntity> participants) {
		this.gameWorld = gameWorld;
		this.config = config;
		this.spawnLogic = new MurderMysterySpawnLogic(gameWorld, map.config, spawnPredicate, false);
		this.scoreboard = gameWorld.addResource(new MurderMysteryScoreboard(this));
		this.world = gameWorld.getWorld();
		this.participants = new HashSet<>(participants);
	}
	
	public static void open(GameWorld gameWorld, MurderMysteryMap map, MurderMysteryConfig config, BiPredicate<ServerWorld, BlockPos.Mutable> spawnPredicate) {
		MurderMysteryActive active = new MurderMysteryActive(gameWorld, map, config, spawnPredicate, gameWorld.getPlayers());
		gameWorld.openGame(game -> {
			game.setRule(GameRule.CRAFTING, RuleResult.DENY);
			game.setRule(GameRule.PORTALS, RuleResult.DENY);
			game.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
			game.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);
			game.setRule(GameRule.HUNGER, RuleResult.DENY);
			game.setRule(GameRule.THROW_ITEMS, RuleResult.DENY);

			game.on(GameOpenListener.EVENT, active::onOpen);
			game.on(GameCloseListener.EVENT, active::onClose);

			game.on(OfferPlayerListener.EVENT, player -> JoinResult.ok());
			game.on(PlayerAddListener.EVENT, active::addPlayer);
			game.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
			game.on(PlayerDamageListener.EVENT, active::onPlayerDamage);
			
			game.on(GameTickListener.EVENT, active::tick);
		});
	}
	
	private void onOpen() {
		for (ServerPlayerEntity player : this.participants) {
			this.spawnParticipant(player);
		}
		
		this.spawnLogic.populateCoinGenerators();
		
		this.broadcastMessage(new LiteralText("Players will receive their roles in 10 seconds!").formatted(Formatting.GREEN, Formatting.BOLD));
		
		this.tasks.add(new TimerTask<>(this, (game) -> {
			for (ServerPlayerEntity player : game.participants) {
				boolean noMurderersAlive = this.areNoPlayersWithRoleLeft(Role.MURDERER);
				boolean noDetectivesAlive = this.areNoPlayersWithRoleLeft(Role.DETECTIVE);
				float chance = RANDOM.nextFloat();
				
				if (chance > 0.95F && noMurderersAlive) {
					this.applyRole(player, Role.MURDERER);
				} else if (chance > 0.9F && noDetectivesAlive) {
					this.applyRole(player, Role.DETECTIVE);
				} else {
					this.applyRole(player, Role.INNOCENT);
				}
			}
			this.applyOpenRole(this.participants, Role.DETECTIVE);
			this.applyOpenRole(this.participants, Role.MURDERER);
			this.roleMap.forEach((uuid, role) -> {
				ServerPlayerEntity player = (ServerPlayerEntity) this.world.getPlayerByUuid(uuid);
				this.scoreboard.addPlayerToRole(player, role);
				role.onApplied.accept(player);
			});
			this.scoreboard.updateRendering();
		}, 200));
	}
	
	private void onClose() {
		this.bows.forEach(Entity::kill);
		this.scoreboard.close();
	}
	
	private void tick() {
		this.tasks.forEach(TimerTask::tick);
		this.tasks.removeIf(TimerTask::isFinished);
		
		this.spawnLogic.tick();
		
		if (this.ticksTillStart > 0) {
			this.ticksTillStart--;
		} else {
			this.ticks++;
			if (this.ticks >= this.config.gameDuration && this.ticksTillClose < 0) {
				this.doWin(Role.INNOCENT);
			}
		}
		
		for (ServerPlayerEntity player : this.participants) {
			player.setExperienceLevel(this.ticksTillStart / 20);
			
			if (this.world.getTime() % 5 == 0 && this.getPlayerRole(player) != Role.DETECTIVE && !this.hasDetectiveBow(player) && player.inventory.contains(new ItemStack(Items.SUNFLOWER))) {
				int coins = this.getCoinCount(player);
				if (coins >= 10) {
					this.takeCoins(player, 10);
					if (!player.inventory.contains(new ItemStack(Items.BOW))) player.inventory.insertStack(ItemStackBuilder.of(Items.BOW).setUnbreakable().build());
					player.inventory.insertStack(new ItemStack(Items.ARROW));
				}
			}
		}
		
		this.world.getEntitiesByType(EntityType.ARROW, (entity) -> ((PersistentProjectileEntity) entity).inGround).forEach(projectile -> projectile.kill());
		
		this.bows.forEach(bow -> {
			bow.yaw += 10.0F;
			List<PlayerEntity> collidingInnocents = this.world.getEntitiesByType(EntityType.PLAYER, bow.getBoundingBox(), (player) -> player.isAlive() && !player.isSpectator() && this.getPlayerRole((ServerPlayerEntity) player) != Role.MURDERER);
			if (!collidingInnocents.isEmpty()) {
				ServerPlayerEntity player = (ServerPlayerEntity) collidingInnocents.get(0);
				player.inventory.insertStack(getDetectiveBow());
				player.inventory.insertStack(new ItemStack(Items.ARROW));
				this.broadcastMessage(new LiteralText("Detective Bow Picked Up!").formatted(Formatting.GOLD, Formatting.BOLD));
				bow.kill();
			}
		});
		
		this.bows.removeIf(bow -> !bow.isAlive());
		
		if (this.ticksTillClose > 0) {
			this.ticksTillClose--;
			if (this.ticksTillClose <= 0) this.gameWorld.close();
		}
		
		this.scoreboard.tick();
	}
	
	private void addPlayer(ServerPlayerEntity player) {
		if (!this.participants.contains(player)) this.spawnSpectator(player, true);
	}
	
	private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		this.eliminatePlayer(player, player);
		return ActionResult.FAIL;
	}
	
	private boolean onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		Entity attacker = source.getAttacker();
		if (attacker instanceof ServerPlayerEntity) {
			ServerPlayerEntity attackingPlayer = (ServerPlayerEntity) attacker;
			Role role = this.getPlayerRole(attackingPlayer);
			boolean isNotProjectile = !source.isProjectile();
			if ((attacker == player || this.ticksTillStart > 0) || role != Role.MURDERER && isNotProjectile || role == Role.MURDERER && isNotProjectile && attackingPlayer.getStackInHand(attackingPlayer.getActiveHand()).getItem() != MurderMysteryCustomItems.MURDERER_BLADE) return true;
			this.eliminatePlayer(attackingPlayer, player);
		} else if (source != DamageSource.FALL && !player.isCreative() && !player.isSpectator()) {
			this.eliminatePlayer(player, player);
		}
		return false;
	}

	private void spawnParticipant(ServerPlayerEntity player) {
		this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
		this.spawnLogic.spawnPlayer(player);
	}
	
	private void spawnSpectator(ServerPlayerEntity player, boolean joined) {
		this.spawnLogic.resetPlayer(player, GameMode.SPECTATOR);
		if (joined) this.spawnLogic.spawnPlayer(player);
	}
	
	private void eliminatePlayer(ServerPlayerEntity attacker, ServerPlayerEntity player) {
		Role yourRole = this.getPlayerRole(player);
		if (this.hasDetectiveBow(player)) {
			this.spawnSpecialArmorStand(player, true);
			this.broadcastMessage(new LiteralText("Detective Bow Dropped!").formatted(Formatting.GOLD, Formatting.BOLD));
		}
		
		if (attacker != player && this.getPlayerRole(attacker) != Role.MURDERER && yourRole != Role.MURDERER) {
			this.eliminatePlayer(attacker, attacker);
		}
		
		this.spawnSpecialArmorStand(player, false);
		
		this.roleMap.removePlayer(player);
		
		if (this.ticksTillClose < 0) {
			if (this.areNoPlayersWithRoleLeft(Role.MURDERER)) {
				this.doWin(Role.INNOCENT);
			} else if (this.areNoPlayersWithRoleLeft(Role.DETECTIVE) && this.areNoPlayersWithRoleLeft(Role.INNOCENT)) {
				this.doWin(Role.MURDERER);
			}
		}
		
		this.scoreboard.updateRendering();
		this.broadcastMessage(player.getDisplayName().shallowCopy().append(" has been eliminated!").formatted(Formatting.RED));
		this.broadcastSound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG);
		this.spawnSpectator(player, false);
		this.participants.remove(player);
	}
	
	private void applyOpenRole(Set<ServerPlayerEntity> players, Role role) {
		if (this.areNoPlayersWithRoleLeft(role) && !this.areNoPlayersWithRoleLeft(Role.INNOCENT)) {
			List<ServerPlayerEntity> innocents = players.stream().filter(player -> this.getPlayerRole(player) == Role.INNOCENT).collect(Collectors.toList());
			this.applyRole(innocents.get(RANDOM.nextInt(innocents.size())), role);
		}
	}
	
	private void applyRole(ServerPlayerEntity player, Role role) {
		this.roleMap.removePlayer(player);
		this.roleMap.putPlayerRole(player, role);
		player.networkHandler.sendPacket(new TitleS2CPacket(TitleS2CPacket.Action.TITLE, new LiteralText(role.toString()).formatted(role.displayColor, Formatting.BOLD)));
	}
	
	private Role getPlayerRole(ServerPlayerEntity player) {
		return this.roleMap.getPlayerRole(player);
	}
	
	private boolean areNoPlayersWithRoleLeft(Role role) {
		return this.roleMap.isRoleEmpty(role);
	}
	
	private void broadcastMessage(Text message) {
		for (ServerPlayerEntity player : this.gameWorld.getPlayers()) player.sendMessage(message, false);
	}

	private void broadcastSound(SoundEvent sound) {
		for (ServerPlayerEntity player : this.gameWorld.getPlayers()) player.playSound(sound, SoundCategory.PLAYERS, 1.0F, 1.0F);
	}
	
	private void doWin(Role role) {
		SoundEvent winSound = role.winSound;
		TitleS2CPacket winMessage = new TitleS2CPacket(TitleS2CPacket.Action.TITLE, new LiteralText(role.winMessage).formatted(role.displayColor, Formatting.BOLD));
		
		for (ServerPlayerEntity player : this.gameWorld.getPlayers()) {
			player.playSound(winSound, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.networkHandler.sendPacket(winMessage);
			player.inventory.clear();
		}
		
		BlockPos center = this.world.getTopPosition(Type.WORLD_SURFACE, new BlockPos(this.config.mapConfig.bounds.getCenter()));
		int x = center.getX();
		int y = center.getY();
		int z = center.getZ();
		
		for (int i = 0; i < 16; i++) {
			this.createRocketForRole(role, x + (RANDOM.nextInt(25) - RANDOM.nextInt(25)), y + RANDOM.nextInt(5), z + (RANDOM.nextInt(25) - RANDOM.nextInt(25)));
		}
		
		this.ticksTillClose = 100;
	}
	
	private void createRocketForRole(Role role, int x, int y, int z) {
		this.tasks.add(new TimerTask<>(this.world, (world) -> {
			ItemStack fireworkItem = new ItemStack(Items.FIREWORK_ROCKET);
			
			CompoundTag fireworks = new CompoundTag();
			ListTag explosions = new ListTag();
			
			CompoundTag explosionTag = new CompoundTag();
			explosionTag.putBoolean("Flicker", RANDOM.nextBoolean());
			explosionTag.putBoolean("Trail", RANDOM.nextBoolean());
			explosionTag.putByte("Type", RANDOM.nextBoolean() ? role.fireworkShape : 2);
			explosionTag.putIntArray("Colors", role.fireworkColors);
			explosions.add(explosionTag);
			
			fireworks.put("Explosions", explosions);
			fireworkItem.getOrCreateTag().put("Fireworks", fireworks);
			
			FireworkRocketEntity firework = new FireworkRocketEntity(world, x, y, z, fireworkItem);
			world.spawnEntity(firework);
		}, RANDOM.nextInt(10) + 5));
	}
	
	private static ItemStack getDetectiveBow() {
		return ItemStackBuilder.of(MurderMysteryCustomItems.DETECTIVE_BOW).addEnchantment(Enchantments.INFINITY, 1).setUnbreakable().setName(new LiteralText("Detective's Bow").formatted(Formatting.BLUE, Formatting.ITALIC)).build();
	}
	
	private boolean hasDetectiveBow(ServerPlayerEntity player) {
		for (int i = 0; i < player.inventory.size(); i++) {
			if (player.inventory.getStack(i).getItem() == MurderMysteryCustomItems.DETECTIVE_BOW) return true;
		}
		return false;
	}
	
	private int getCoinCount(ServerPlayerEntity player) {
		int available = 0;
		for (int i = 0; i < player.inventory.size(); i++) {
			ItemStack stack = player.inventory.getStack(i);
			if (!stack.isEmpty() && stack.getItem().equals(Items.SUNFLOWER)) {
				available += stack.getCount();
			}
		}
		return available;
	}
	
	private void takeCoins(ServerPlayerEntity player, int count) {
		for (int slot = 0; slot < player.inventory.size(); slot++) {
			ItemStack stack = player.inventory.getStack(slot);
			if (!stack.isEmpty() && stack.getItem().equals(Items.SUNFLOWER)) {
				int remove = Math.min(count, stack.getCount());
				player.inventory.removeStack(slot, remove);
				count -= remove;
				if (count <= 0) return;
			}
		}
	}
	
	private void spawnSpecialArmorStand(PlayerEntity player, boolean isBow) {
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		ArmorStandEntity stand = new ArmorStandEntity(this.world, x, y, z);
		DataTracker tracker = stand.getDataTracker();
		tracker.set(ArmorStandEntity.ARMOR_STAND_FLAGS, (byte) (tracker.get(ArmorStandEntity.ARMOR_STAND_FLAGS) | 4));
		stand.setNoGravity(!isBow);
		stand.setInvisible(true);
		stand.setCustomNameVisible(true);
		stand.setInvulnerable(true);
		stand.disabledSlots = 65793;
		stand.yaw = RANDOM.nextFloat() * 360.0F;
		
		if (isBow) {
			stand.setRightArmRotation(new EulerAngle(180.0F, 0.0F, 32.0F));
			stand.equipStack(EquipmentSlot.MAINHAND, getDetectiveBow());
			stand.setCustomName(new LiteralText("Detective's Bow").formatted(Formatting.BLUE, Formatting.BOLD));
			this.bows.add(stand);
		} else {
			double lowestY = this.getLowestY(new BlockPos(x, y, z));
			stand.setPos(x, lowestY, z);
			
			ItemStack headItem = new ItemStack(Items.PLAYER_HEAD);
			CompoundTag tag = headItem.getOrCreateTag();
			tag.putString("SkullOwner", player.getEntityName());
			headItem.getItem().postProcessTag(tag);
			stand.equipStack(EquipmentSlot.HEAD, headItem);
			stand.setCustomName(player.getName().shallowCopy().append("'s head").formatted(Formatting.YELLOW));
			
			FloatingText.spawn(this.world, new Vec3d(x, lowestY + 2.35F, z), new LiteralText("\"" + DEATH_QUOTES[RANDOM.nextInt(DEATH_QUOTES.length)] + "\"").formatted(Formatting.ITALIC));
		}
		this.world.spawnEntity(stand);
	}
	
	private double getLowestY(BlockPos playerPos) {
		BlockPos.Mutable mutable = new BlockPos.Mutable();
		int x = playerPos.getX();
		int y = playerPos.getY() + 1;
		int z = playerPos.getZ();
		for (int i = 0; i < 128; i++) {
			mutable.set(x, y - i, z);
			VoxelShape shape = this.world.getBlockState(mutable).getCollisionShape(this.world, mutable);
			if (!shape.isEmpty()) {
				return mutable.getY() + 1 + shape.getMax(Axis.Y) - 2.5F;
			}
		}
		return playerPos.getY();
	}
	
	public long getTimeRemaining() {
		return this.config.gameDuration - this.ticks;
	}
	
	public String getInnocentsRemaining() {
		return String.valueOf(this.roleMap.getInnocentsLeft());
	}
	
	enum Role {
		INNOCENT(Formatting.GREEN, (player) -> {}, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, "Innocents Win!", (byte) 4, 65280, 41728, 16777215),
		DETECTIVE(Formatting.BLUE, (player) -> {
			player.inventory.insertStack(1, getDetectiveBow());
			player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.inventory.insertStack(2, new ItemStack(Items.ARROW, 1));
		}, null, null, (byte) 4),
		MURDERER(Formatting. RED, (player) -> {
			player.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.inventory.insertStack(1, ItemStackBuilder.of(MurderMysteryCustomItems.MURDERER_BLADE).setUnbreakable().setName(new LiteralText("Murderer's Blade").formatted(Formatting.RED, Formatting.ITALIC)).build());
		}, SoundEvents.ENTITY_WITHER_SPAWN, "Murderer Wins!", (byte) 3, 16711680, 11534336, 0);
		
		public static final String[] CACHED_DISPLAYS = Util.make(new String[3], (array) -> {
			for (Role role : values()) {
				String roleName = role.toString();
				array[role.ordinal()] = role.getDisplayColor().toString() + roleName.charAt(0) + roleName.substring(1).toLowerCase();
			}
		});
		
		private final Formatting displayColor;
		private final Consumer<ServerPlayerEntity> onApplied;
		
		private final SoundEvent winSound;
		private final String winMessage;
		private final byte fireworkShape;
		private final int[] fireworkColors;
		
		private Role(Formatting displayColor, Consumer<ServerPlayerEntity> onApplied, SoundEvent winSound, String winMessage, byte fireworkShape, int... fireworkColors) {
			this.displayColor = displayColor;
			this.onApplied = onApplied;
			this.winSound = winSound;
			this.winMessage = winMessage;
			this.fireworkShape = fireworkShape;
			this.fireworkColors = fireworkColors;
		}
		
		public Formatting getDisplayColor() {
			return this.displayColor;
		}
	}
	
	static class PlayerRoleMap extends HashMap<UUID, Role> {
		private static final long serialVersionUID = -5696930182002870464L;
		private final EnumMap<Role, Integer> roleCountMap = Maps.newEnumMap(Role.class);
		
		public PlayerRoleMap() {}
		
		public void putPlayerRole(ServerPlayerEntity player, Role role) {
			UUID playerUUID = player.getUuid();
			this.put(playerUUID, role);
			this.roleCountMap.put(role, this.roleCountMap.getOrDefault(role, 0) + 1);
		}
		
		public void removePlayer(ServerPlayerEntity player) {
			UUID uuid = player.getUuid();
			if (!this.containsKey(uuid)) return;
			Role role = super.remove(uuid);
			this.roleCountMap.put(role, this.roleCountMap.get(role) - 1);
		}
		
		public Role getPlayerRole(ServerPlayerEntity player) {
			return this.get(player.getUuid());
		}
		
		public int getInnocentsLeft() {
			return this.roleCountMap.getOrDefault(Role.INNOCENT, 0) + this.roleCountMap.getOrDefault(Role.DETECTIVE, 0);
		}
		
		public boolean isRoleEmpty(Role role) {
			return this.roleCountMap.getOrDefault(role, 0) == 0;
		}
	}
	
	static class TimerTask<T> {
		private final T type;
		private final Consumer<T> consumer;
		private final int tickLength;
		private int ticks;
		private boolean finished;
		
		public TimerTask(T type, Consumer<T> consumer, int tickLength) {
			this.type = type;
			this.consumer = consumer;
			this.tickLength = tickLength;
		}
		
		public void tick() {
			if (this.ticks++ >= this.tickLength) this.onFinished();
		}
		
		public void onFinished() {
			this.consumer.accept(this.type);
			this.finished = true;
		}
		
		public boolean isFinished() {
			return this.finished;
		}
	}
}