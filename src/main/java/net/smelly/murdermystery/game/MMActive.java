package net.smelly.murdermystery.game;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.text.Texts;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
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
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.GameCloseListener;
import xyz.nucleoid.plasmid.game.event.GameOpenListener;
import xyz.nucleoid.plasmid.game.event.GameTickListener;
import xyz.nucleoid.plasmid.game.event.OfferPlayerListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerChatListener;
import xyz.nucleoid.plasmid.game.event.PlayerDamageListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.PlayerRemoveListener;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.player.PlayerSet;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;
import xyz.nucleoid.plasmid.widget.GlobalWidgets;

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
	
	public final GameSpace gameSpace;
	public final MMConfig config;
	private final MMSpawnLogic spawnLogic;
	private final MMScoreboard scoreboard;
	
	private final PlayerRoleMap roleMap = new PlayerRoleMap();
	private final Set<TimerTask<?>> tasks = Sets.newHashSet();
	public final Set<ArmorStandEntity> bows = Sets.newHashSet();
	
	private final ServerWorld world;
	private final PlayerSet participants;
	private final Set<ServerPlayerEntity> aliveParticipants, deadParticipants;
	
	public int ticksTillStart = 200;
	private int ticksTillClose = -1;
	private long ticks = 0;
	
	private MMActive(GameSpace gameSpace, MMMap map, MMConfig config, BiPredicate<ServerWorld, BlockPos.Mutable> spawnPredicate, GlobalWidgets widgets) {
		this.gameSpace = gameSpace;
		this.config = config;
		this.spawnLogic = new MMSpawnLogic(gameSpace, map.config, spawnPredicate, false);
		this.scoreboard = gameSpace.addResource(new MMScoreboard(this, widgets));
		this.world = gameSpace.getWorld();
		this.participants = gameSpace.getPlayers();
		this.aliveParticipants = this.getPlayersPlaying();
		this.deadParticipants = Sets.newHashSet();
	}
	
	public static void open(GameSpace gameSpace, MMMap map, MMConfig config, BiPredicate<ServerWorld, BlockPos.Mutable> spawnPredicate) {
		gameSpace.openGame(game -> {
			GlobalWidgets widgets = new GlobalWidgets(game);
			MMActive active = new MMActive(gameSpace, map, config, spawnPredicate, widgets);

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
			game.on(PlayerChatListener.EVENT, active::onPlayerChat);
			
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
			if (player != null) {
				this.scoreboard.addPlayerToRole(player, role);
				this.roleMap.updatePlayerWeight(player, role);
				role.onApplied.accept(player);
			}
		});
		
		this.scoreboard.updateRendering();
		this.spawnLogic.populateCoinGenerators();
		this.participants.sendMessage(new TranslatableText("text.murder_mystery.game_begin_in", 10).formatted(Formatting.GREEN, Formatting.BOLD));
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
						player.networkHandler.sendPacket(new TitleS2CPacket(TitleS2CPacket.Action.ACTIONBAR, playerRole.getName().formatted(playerRole.getDisplayColor(), Formatting.ITALIC)));
						
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
				this.participants.sendMessage(new TranslatableText("text.murder_mystery.detective_bow_picked_up").formatted(Formatting.GOLD, Formatting.BOLD));
				bow.kill();
			}
		});
		
		this.bows.removeIf(bow -> !bow.isAlive());
		
		if (this.isGameClosing()) {
			this.ticksTillClose--;
			if (!this.isGameClosing()) this.gameSpace.close(GameCloseReason.FINISHED);
		}
		
		this.scoreboard.tick();
	}
	
	private void addPlayer(ServerPlayerEntity player) {
		this.spawnSpectator(player, true);
		if (this.aliveParticipants.contains(player)) {
			player.setGameMode(GameMode.ADVENTURE);
			this.aliveParticipants.remove(player);
		} else {
			this.deadParticipants.add(player);
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
	
	private ActionResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		if (this.isGameStarting() || player.isCreative() || player.isSpectator()) return ActionResult.FAIL;
		Entity attacker = source.getAttacker();
		if (attacker instanceof ServerPlayerEntity) {
			ServerPlayerEntity attackingPlayer = (ServerPlayerEntity) attacker;
			Role attackingRole = this.getPlayerRole(attackingPlayer);
			if (!attacker.equals(player) && attackingRole != null && attackingRole.canHurtPlayer.test(attackingPlayer, source)) {
				this.eliminatePlayer(attackingPlayer, player);
			}
			return ActionResult.FAIL;
		} else if (source != DamageSource.FALL) {
			this.eliminatePlayer(player, player);
		}
		return ActionResult.PASS;
	}
	
	private ActionResult onPlayerChat(Text message, ServerPlayerEntity sender) {
		if (!this.isGameClosing() && this.deadParticipants.contains(sender)) {
			UUID senderUUID = sender.getUuid();
			Text deadMessage = Texts.bracketed(new TranslatableText("text.murder_mystery.dead")).formatted(Formatting.GRAY).append(message.shallowCopy().formatted(Formatting.RESET));
			this.deadParticipants.forEach((deadParticipant) -> deadParticipant.sendSystemMessage(deadMessage, senderUUID));
			return ActionResult.FAIL;
		}
		return ActionResult.PASS;
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
		this.deadParticipants.add(player);
		this.participants.sendSound(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT);
		this.spawnSpectator(player, false);
	}
	
	private void applyRole(ServerPlayerEntity player, Role role) {
		this.roleMap.removePlayer(player);
		this.roleMap.putPlayerRole(player, role);
		player.networkHandler.sendPacket(new TitleS2CPacket(TitleS2CPacket.Action.TITLE, role.getName().formatted(role.getDisplayColor(), Formatting.BOLD)));
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
			player.networkHandler.sendPacket(new TitleS2CPacket(TitleS2CPacket.Action.ACTIONBAR, LiteralText.EMPTY));
			player.networkHandler.sendPacket(new TitleS2CPacket(TitleS2CPacket.Action.TITLE, role.getWinMessage().formatted(role.getDisplayColor(), Formatting.BOLD)));
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
		return ItemStackBuilder.of(MMCustomItems.DETECTIVE_BOW).addEnchantment(Enchantments.INFINITY, 1).setUnbreakable().setName(new TranslatableText("item.murder_mystery.detective_bow").formatted(Formatting.BLUE, Formatting.ITALIC)).build();
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
			stand.setCustomName(new TranslatableText("item.murder_mystery.detective_bow").formatted(Formatting.BLUE, Formatting.BOLD));
			this.bows.add(stand);
		} else {
			double lowestY = this.getLowestY(new BlockPos(x, y, z));
			stand.setPos(x, lowestY, z);
			
			ItemStack headItem = new ItemStack(Items.PLAYER_HEAD);
			CompoundTag tag = headItem.getOrCreateTag();
			tag.putString("SkullOwner", player.getEntityName());
			headItem.getItem().postProcessTag(tag);
			stand.equipStack(EquipmentSlot.HEAD, headItem);
			stand.setCustomName(new TranslatableText("text.murder_mystery.head", player.getName().shallowCopy()).formatted(Formatting.YELLOW));
			
			FloatingText.spawn(this.world, new Vec3d(x, lowestY + 2.35F, z), new TranslatableText("text.murder_mystery.death_quote", new TranslatableText("text.murder_mystery.death_quote." + (RANDOM.nextInt(22) + 1))).formatted(Formatting.ITALIC));
		}
		this.world.spawnEntity(stand);
	}
	
	private void spawnDetectiveBow(ServerPlayerEntity player) {
		this.spawnSpecialArmorStand(player, true);
		this.participants.sendMessage(new TranslatableText("text.murder_mystery.detective_bow_dropped").formatted(Formatting.GOLD, Formatting.BOLD));
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

	//TODO: Convert to a class-based Role System once TTT Game Mode is added.
	enum Role {
		INNOCENT("innocent", Formatting.GREEN, (player) -> {}, (attacker, damageSource) -> damageSource.isProjectile(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, "text.murder_mystery.innocents_win", (byte) 4, 65280, 41728, 16777215),
		DETECTIVE("detective", Formatting.BLUE, (player) -> {
			player.inventory.insertStack(1, getDetectiveBow());
			player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.inventory.insertStack(2, new ItemStack(Items.ARROW, 1));
		}, (attacker, damageSource) -> damageSource.isProjectile(), null, null, (byte) 4),
		MURDERER("murderer", Formatting. RED, (player) -> {
			player.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.inventory.insertStack(1, ItemStackBuilder.of(MMCustomItems.MURDERER_BLADE).setUnbreakable().setName(new TranslatableText("item.murder_mystery.murderer_blade").formatted(Formatting.RED, Formatting.ITALIC)).build());
		}, (attacker, damageSource) -> damageSource.isProjectile() || attacker.isHolding(MMCustomItems.MURDERER_BLADE), SoundEvents.ENTITY_WITHER_SPAWN, "text.murder_mystery.murderer_win", (byte) 3, 16711680, 11534336, 0);
		
		private final String id;
		private final TranslatableText name;
		private final Formatting displayColor;
		private final Consumer<ServerPlayerEntity> onApplied;
		private final BiPredicate<ServerPlayerEntity, DamageSource> canHurtPlayer;

		private final SoundEvent winSound;
		private final TranslatableText winMessage;
		private final byte fireworkShape;
		private final int[] fireworkColors;
		
		Role(String id, Formatting displayColor, Consumer<ServerPlayerEntity> onApplied, BiPredicate<ServerPlayerEntity, DamageSource> canHurtPlayer, SoundEvent winSound, String winMessage, byte fireworkShape, int... fireworkColors) {
			this.id = id;
			this.name = new TranslatableText("role.murder_mystery." + id);
			this.displayColor = displayColor;
			this.onApplied = onApplied;
			this.canHurtPlayer = canHurtPlayer;
			this.winSound = winSound;
			this.winMessage = new TranslatableText(winMessage);
			this.fireworkShape = fireworkShape;
			this.fireworkColors = fireworkColors;
		}

		@Override
		public String toString() {
			return id;
		}

		public TranslatableText getName() {
			return name;
		}

		public TranslatableText getWinMessage() {
			return winMessage;
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
			Set<ServerPlayerEntity> alivePlayers = players.stream().filter(LivingEntity::isAlive).collect(Collectors.toSet());
			WeightedPlayerList murdererList = new WeightedPlayerList();
			WeightedPlayerList detectiveList = new WeightedPlayerList();
			for (ServerPlayerEntity player : alivePlayers) {
				UUID playerUUID = player.getUuid();
				int murdererWeight = MurderMystery.WEIGHT_STORAGE.getPlayerWeight(playerUUID, true);
				int detectiveWeight = MurderMystery.WEIGHT_STORAGE.getPlayerWeight(playerUUID, false);
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
					MurderMystery.WEIGHT_STORAGE.incrementPlayerWeight(playerUUID, RANDOM.nextBoolean());
					break;
				case DETECTIVE:
					MurderMystery.WEIGHT_STORAGE.putPlayerWeight(playerUUID, 1, false);
					break;
				case MURDERER:
					MurderMystery.WEIGHT_STORAGE.putPlayerWeight(playerUUID, 1, true);
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
