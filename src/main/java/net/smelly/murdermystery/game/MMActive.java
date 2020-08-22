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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.util.Util;
import net.minecraft.util.collection.WeightedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.EulerAngle;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap.Type;
import net.smelly.murdermystery.MurderMystery;
import net.smelly.murdermystery.game.custom.MMCustomItems;
import net.smelly.murdermystery.game.map.MMMap;

import xyz.nucleoid.plasmid.entity.FloatingText;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.event.GameCloseListener;
import xyz.nucleoid.plasmid.game.event.GameOpenListener;
import xyz.nucleoid.plasmid.game.event.GameTickListener;
import xyz.nucleoid.plasmid.game.event.OfferPlayerListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDamageListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.PlayerRemoveListener;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.player.PlayerSet;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;

import java.util.EnumMap;
import java.util.HashMap;
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
public final class MMActive {
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
	public final MMConfig config;
	private final MMSpawnLogic spawnLogic;
	private final MMScoreboard scoreboard;
	
	private final PlayerRoleMap roleMap = new PlayerRoleMap();
	private final Set<TimerTask<?>> tasks = Sets.newHashSet();
	public final Set<ArmorStandEntity> bows = Sets.newHashSet();
	
	private final ServerWorld world;
	private final PlayerSet participants;
	private final Set<ServerPlayerEntity> nonSpectators;
	
	public int ticksTillStart = 200;
	private int ticksTillClose = -1;
	private long ticks = 0;
	
	private MMActive(GameWorld gameWorld, MMMap map, MMConfig config, BiPredicate<ServerWorld, BlockPos.Mutable> spawnPredicate) {
		this.gameWorld = gameWorld;
		this.config = config;
		this.spawnLogic = new MMSpawnLogic(gameWorld, map.config, spawnPredicate, false);
		this.scoreboard = gameWorld.addResource(new MMScoreboard(this));
		this.world = gameWorld.getWorld();
		this.participants = gameWorld.getPlayerSet();
		this.nonSpectators = this.getPlayersPlaying();
	}
	
	public static void open(GameWorld gameWorld, MMMap map, MMConfig config, BiPredicate<ServerWorld, BlockPos.Mutable> spawnPredicate) {
		MMActive active = new MMActive(gameWorld, map, config, spawnPredicate);
		gameWorld.openGame(game -> {
			game.setRule(GameRule.CRAFTING, RuleResult.DENY);
			game.setRule(GameRule.PORTALS, RuleResult.DENY);
			game.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
			game.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);
			game.setRule(GameRule.HUNGER, RuleResult.DENY);
			game.setRule(GameRule.THROW_ITEMS, RuleResult.DENY);
			game.setRule(GameRule.PVP, RuleResult.ALLOW);

			game.on(GameOpenListener.EVENT, active::onOpen);
			game.on(GameCloseListener.EVENT, active::onClose);

			game.on(OfferPlayerListener.EVENT, player -> JoinResult.ok());
			game.on(PlayerAddListener.EVENT, active::addPlayer);
			game.on(PlayerRemoveListener.EVENT, active::removePlayer);
			game.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
			game.on(PlayerDamageListener.EVENT, active::onPlayerDamage);
			
			game.on(GameTickListener.EVENT, active::tick);
		});
	}
	
	private void onOpen() {
		for (ServerPlayerEntity player : this.participants) {
			this.spawnParticipant(player);
		}
		
		Set<ServerPlayerEntity> playersToAssign = this.getPlayersPlaying();
		Pair<WeightedPlayerList, WeightedPlayerList> weightLists = this.roleMap.createWeightedPlayerRoleLists(playersToAssign);
		
		ServerPlayerEntity pickedMurderer = weightLists.getLeft().pickRandom(RANDOM);
		this.applyRole(pickedMurderer, Role.MURDERER);
		playersToAssign.remove(pickedMurderer);
			
		WeightedPlayerList possibleDetectives = weightLists.getRight();
		possibleDetectives.remove(pickedMurderer);
		if (!possibleDetectives.isEmpty()) {
			ServerPlayerEntity pickedDetective = weightLists.getRight().pickRandom(RANDOM);
			this.applyRole(pickedDetective, Role.DETECTIVE);
			playersToAssign.remove(pickedDetective);
		}
		
		for (ServerPlayerEntity player : playersToAssign) {
			this.applyRole(player, Role.INNOCENT);
		}
				
		this.roleMap.forEach((uuid, role) -> {
			ServerPlayerEntity player = (ServerPlayerEntity) this.world.getPlayerByUuid(uuid);
			this.scoreboard.addPlayerToRole(player, role);
			this.roleMap.updatePlayerWeight(player, role);
			role.onApplied.accept(player);
		});
		
		this.scoreboard.updateRendering();
		this.spawnLogic.populateCoinGenerators();
		this.participants.sendMessage(new LiteralText("Game will begin in 10 seconds!").formatted(Formatting.GREEN, Formatting.BOLD));
	}
	
	private void onClose() {
		this.bows.forEach(Entity::kill);
		this.scoreboard.close();
	}
	
	private void tick() {
		this.tasks.forEach(TimerTask::tick);
		this.tasks.removeIf(TimerTask::isFinished);
		
		this.spawnLogic.tick();
		
		if (this.isGameStarting()) {
			this.ticksTillStart--;
		} else {
			this.ticks++;
			if (this.ticks >= this.config.gameDuration && !this.isGameClosing()) {
				this.doWin(Role.INNOCENT);
			}
		}
		
		if (!this.isGameClosing()) {
			for (ServerPlayerEntity player : this.participants) {
				player.setExperienceLevel(this.ticksTillStart / 20);
				
				if (this.world.getTime() % 5 == 0) {
					Role playerRole = this.getPlayerRole(player);
					if (playerRole != null) {
						player.networkHandler.sendPacket(new TitleS2CPacket(TitleS2CPacket.Action.ACTIONBAR, new LiteralText(Role.CACHED_DISPLAYS[playerRole.ordinal()]).formatted(playerRole.displayColor, Formatting.ITALIC)));
						
						if (playerRole != Role.DETECTIVE && !this.hasDetectiveBow(player) && player.inventory.contains(new ItemStack(Items.SUNFLOWER))) {
							int coins = this.getCoinCount(player);
							if (coins >= 10) {
								this.takeCoins(player, 10);
								if (!player.inventory.contains(new ItemStack(Items.BOW))) player.inventory.insertStack(ItemStackBuilder.of(Items.BOW).setUnbreakable().build());
								player.inventory.insertStack(new ItemStack(Items.ARROW));
							}
						}
					}
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
				this.participants.sendMessage(new LiteralText("Detective Bow Picked Up!").formatted(Formatting.GOLD, Formatting.BOLD));
				bow.kill();
			}
		});
		
		this.bows.removeIf(bow -> !bow.isAlive());
		
		if (this.isGameClosing()) {
			this.ticksTillClose--;
			if (!this.isGameClosing()) this.gameWorld.close();
		}
		
		this.scoreboard.tick();
	}
	
	private void addPlayer(ServerPlayerEntity player) {
		this.spawnSpectator(player, true);
		if (this.nonSpectators.contains(player)) {
			player.setGameMode(GameMode.ADVENTURE);
			this.nonSpectators.remove(player);
		}
	}
	
	private void removePlayer(ServerPlayerEntity player) {
		if (this.roleMap.removePlayer(player)) this.scoreboard.updateRendering();
		if (!this.isGameClosing()) {
			if (!player.isSpectator() && this.hasDetectiveBow(player)) this.spawnDetectiveBow(player);
			this.testWin();
		}
	}
	
	private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		if (!player.isSpectator() && !player.isCreative()) this.eliminatePlayer(player, player);
		return ActionResult.FAIL;
	}
	
	private boolean onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		Entity attacker = source.getAttacker();
		boolean unEliminatable = player.isCreative() || player.isSpectator();
		if (attacker instanceof ServerPlayerEntity) {
			ServerPlayerEntity attackingPlayer = (ServerPlayerEntity) attacker;
			Role role = this.getPlayerRole(attackingPlayer);
			boolean isNotProjectile = !source.isProjectile();
			if ((attacker == player || this.isGameStarting()) || role != Role.MURDERER && isNotProjectile || role == Role.MURDERER && isNotProjectile && attackingPlayer.getStackInHand(attackingPlayer.getActiveHand()).getItem() != MMCustomItems.MURDERER_BLADE || unEliminatable) return true;
			this.eliminatePlayer(attackingPlayer, player);
		} else if (source != DamageSource.FALL && !unEliminatable) {
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
			this.spawnDetectiveBow(player);
		}
		
		if (attacker != player && this.getPlayerRole(attacker) != Role.MURDERER && yourRole != Role.MURDERER) {
			this.eliminatePlayer(attacker, attacker);
		}
		
		this.spawnSpecialArmorStand(player, false);
		
		this.roleMap.removePlayer(player);
		
		if (!this.isGameClosing()) {
			this.testWin();
		}
		
		this.scoreboard.updateRendering();
		this.participants.sendMessage(player.getDisplayName().shallowCopy().append(" has been eliminated!").formatted(Formatting.RED));
		this.participants.sendSound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG);
		this.spawnSpectator(player, false);
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
	
	private Set<ServerPlayerEntity> getPlayersPlaying() {
		Set<ServerPlayerEntity> players = Sets.newHashSet();
		for (ServerPlayerEntity player : this.participants) {
			players.add(player);
		}
		return players;
	}
	
	private void testWin() {
		if (this.areNoPlayersWithRoleLeft(Role.MURDERER)) {
			this.doWin(Role.INNOCENT);
		} else if (this.areNoPlayersWithRoleLeft(Role.DETECTIVE) && this.areNoPlayersWithRoleLeft(Role.INNOCENT)) {
			this.doWin(Role.MURDERER);
		}
	}
	
	private void doWin(Role role) {
		for (ServerPlayerEntity player : this.world.getPlayers()) {
			player.playSound(role.winSound, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.networkHandler.sendPacket(new TitleS2CPacket(TitleS2CPacket.Action.ACTIONBAR, new LiteralText("")));
			player.networkHandler.sendPacket(new TitleS2CPacket(TitleS2CPacket.Action.TITLE, new LiteralText(role.winMessage).formatted(role.displayColor, Formatting.BOLD)));
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
		return ItemStackBuilder.of(MMCustomItems.DETECTIVE_BOW).addEnchantment(Enchantments.INFINITY, 1).setUnbreakable().setName(new LiteralText("Detective's Bow").formatted(Formatting.BLUE, Formatting.ITALIC)).build();
	}
	
	private boolean hasDetectiveBow(ServerPlayerEntity player) {
		for (int i = 0; i < player.inventory.size(); i++) {
			if (player.inventory.getStack(i).getItem() == MMCustomItems.DETECTIVE_BOW) return true;
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
	
	private void spawnSpecialArmorStand(ServerPlayerEntity player, boolean isBow) {
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
		stand.disabledSlots = 4144959;
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
	
	private void spawnDetectiveBow(ServerPlayerEntity player) {
		this.spawnSpecialArmorStand(player, true);
		this.participants.sendMessage(new LiteralText("Detective Bow Dropped!").formatted(Formatting.GOLD, Formatting.BOLD));
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
	
	private boolean isGameStarting() {
		return this.ticksTillStart > 0;
	}
	
	public boolean isGameClosing() {
		return this.ticksTillClose > 0;
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
			player.inventory.insertStack(1, ItemStackBuilder.of(MMCustomItems.MURDERER_BLADE).setUnbreakable().setName(new LiteralText("Murderer's Blade").formatted(Formatting.RED, Formatting.ITALIC)).build());
		}, SoundEvents.ENTITY_WITHER_SPAWN, "Murderer Wins!", (byte) 3, 16711680, 11534336, 0);
		
		public static final String[] CACHED_DISPLAYS = Util.make(new String[3], (array) -> {
			for (Role role : values()) {
				String roleName = role.toString();
				array[role.ordinal()] = roleName.charAt(0) + roleName.substring(1).toLowerCase();
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
		
		private PlayerRoleMap() {}
		
		private Pair<WeightedPlayerList, WeightedPlayerList> createWeightedPlayerRoleLists(Set<ServerPlayerEntity> players) {
			Set<ServerPlayerEntity> alivePlayers = players.stream().filter(player -> player.isAlive()).collect(Collectors.toSet());
			WeightedPlayerList murdererList = new WeightedPlayerList();
			WeightedPlayerList detectiveList = new WeightedPlayerList();
			for (ServerPlayerEntity player : alivePlayers) {
				UUID playerUUID = player.getUuid();
				int murdererWeight = MurderMystery.MURDERER_WEIGHT_MAP.getOrDefault(playerUUID, 1);
				int detectiveWeight = MurderMystery.DETECTIVE_WEIGHT_MAP.getOrDefault(playerUUID, 1);
				murdererList.add(player, murdererWeight);
				detectiveList.add(player, detectiveWeight);
			}
			return new Pair<>(murdererList, detectiveList);
		}
		
		private void putPlayerRole(ServerPlayerEntity player, Role role) {
			UUID playerUUID = player.getUuid();
			this.put(playerUUID, role);
			this.roleCountMap.put(role, this.roleCountMap.getOrDefault(role, 0) + 1);
		}
		
		private boolean removePlayer(ServerPlayerEntity player) {
			UUID playerUUID = player.getUuid();
			if (!this.containsKey(playerUUID)) return false;
			Role role = super.remove(playerUUID);
			this.roleCountMap.put(role, this.roleCountMap.get(role) - 1);
			return true;
		}
		
		private void updatePlayerWeight(ServerPlayerEntity player, Role role) {
			UUID playerUUID = player.getUuid();
			switch (role) {
				case INNOCENT:
					if (RANDOM.nextBoolean()) {
						MurderMystery.DETECTIVE_WEIGHT_MAP.put(playerUUID, MurderMystery.DETECTIVE_WEIGHT_MAP.getOrDefault(playerUUID, 1) + 1);
					} else {
						MurderMystery.MURDERER_WEIGHT_MAP.put(playerUUID, MurderMystery.MURDERER_WEIGHT_MAP.getOrDefault(playerUUID, 1) + 1);
					}
					break;
				case DETECTIVE:
					MurderMystery.DETECTIVE_WEIGHT_MAP.put(playerUUID, 1);
					break;
				case MURDERER:
					MurderMystery.MURDERER_WEIGHT_MAP.put(playerUUID, 1);
					break;
			}
		}
		
		private Role getPlayerRole(ServerPlayerEntity player) {
			return this.get(player.getUuid());
		}
		
		private int getInnocentsLeft() {
			return this.roleCountMap.getOrDefault(Role.INNOCENT, 0) + this.roleCountMap.getOrDefault(Role.DETECTIVE, 0);
		}
		
		private boolean isRoleEmpty(Role role) {
			return this.roleCountMap.getOrDefault(role, 0) == 0;
		}
	}
	
	static class WeightedPlayerList extends WeightedList<ServerPlayerEntity> {
		
		public WeightedPlayerList() {}
		
		private void remove(ServerPlayerEntity player) {
			WeightedList.Entry<ServerPlayerEntity> matchingEntry = null;
			for (WeightedList.Entry<ServerPlayerEntity> entry : this.entries) {
				if (entry.getElement() == player) {
					matchingEntry = entry;
				}
			}
			this.entries.remove(matchingEntry);
		}
		
		private boolean isEmpty() {
			return this.entries.isEmpty();
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