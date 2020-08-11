package net.smelly.murdermystery.game;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.mutable.MutableInt;

import com.google.common.collect.Sets;

import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.event.GameCloseListener;
import xyz.nucleoid.plasmid.game.event.GameOpenListener;
import xyz.nucleoid.plasmid.game.event.GameTickListener;
import xyz.nucleoid.plasmid.game.event.OfferPlayerListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDamageListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.UseItemListener;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.item.CustomItem;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.EulerAngle;
import net.minecraft.world.GameMode;
import net.smelly.murdermystery.game.custom.MurderMysteryCustomItems;
import net.smelly.murdermystery.game.map.MurderMysteryMap;

/**
 * @author SmellyModder (Luke Tonon)
 * Much of the code in here supports more than one of something.
 * This is done to make support for multiple detectives and murderers easier later.
 */
public final class MurderMysteryActive {
	private static final Random RANDOM = new Random();
	
	public final GameWorld gameWorld;
	@SuppressWarnings("unused") //Only used for saving the map.
	private final MurderMysteryMap map;
	private final MurderMysteryConfig config;
	private final MurderMysterySpawnLogic spawnLogic;
	private final MurderMysteryScoreboard scoreboard;
	
	private final PlayerRoleMap roleMap = new PlayerRoleMap();
	private final Set<TimerTask<?>> tasks = Sets.newHashSet();
	public final Set<ArmorStandEntity> bows = Sets.newHashSet();
	
	private final ServerWorld world;
	private final Set<ServerPlayerEntity> participants;
	private Team team;
	
	private int ticksTillStart = 200;
	public int ticksTillClose = -1;
	private long ticks = 0;
	
	private MurderMysteryActive(GameWorld gameWorld, MurderMysteryMap map, MurderMysteryConfig config, Set<ServerPlayerEntity> participants) {
		this.gameWorld = gameWorld;
		this.map = map;
		this.config = config;
		this.spawnLogic = new MurderMysterySpawnLogic(gameWorld, map.config, false);
		this.scoreboard = new MurderMysteryScoreboard(this);
		this.world = gameWorld.getWorld();
		this.participants = new HashSet<>(participants);
	}
	
	public static void open(GameWorld gameWorld, MurderMysteryMap map, MurderMysteryConfig config) {
		MurderMysteryActive active = new MurderMysteryActive(gameWorld, map, config, gameWorld.getPlayers());
		gameWorld.newGame(game -> {
			game.setRule(GameRule.ALLOW_CRAFTING, RuleResult.DENY);
			game.setRule(GameRule.ALLOW_PORTALS, RuleResult.DENY);
			game.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
			game.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);
			game.setRule(GameRule.ENABLE_HUNGER, RuleResult.DENY);

			game.on(GameOpenListener.EVENT, active::onOpen);
			game.on(GameCloseListener.EVENT, active::onClose);

			game.on(OfferPlayerListener.EVENT, player -> JoinResult.ok());
			game.on(PlayerAddListener.EVENT, active::addPlayer);
			game.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
			game.on(PlayerDamageListener.EVENT, active::onPlayerDamage);
			
			game.on(UseItemListener.EVENT, active::onUseItem);
			
			game.on(GameTickListener.EVENT, active::tick);
		});
	}
	
	private void onOpen() {
		Team presentTeam = this.world.getScoreboard().getTeam("Murder Mystery");
		this.team = presentTeam != null ? presentTeam : this.world.getScoreboard().addTeam("Murder Mystery");
		for (ServerPlayerEntity player : this.participants) {
			this.world.getScoreboard().addPlayerToTeam(player.getEntityName(), this.team);
			this.spawnParticipant(player);
		}
		
		this.broadcastMessage(new LiteralText("Players will receive their roles in 10 seconds!").formatted(Formatting.GREEN, Formatting.BOLD));
		
		MutableInt players = new MutableInt(0);
		this.tasks.add(new TimerTask<>(this, (game) -> {
			for (ServerPlayerEntity player : game.participants) {
				players.add(1);
				
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
				
				if (players.getValue() >= this.participants.size() - 1) {
					if (noDetectivesAlive) {
						this.applyRole(player, Role.DETECTIVE);
					} else if (noMurderersAlive) {
						this.applyRole(player, Role.MURDERER);
					}
				}
			}
			this.roleMap.forEach((uuid, role) -> role.onApplied.accept((ServerPlayerEntity) this.world.getPlayerByUuid(uuid)));
		}, 200));
	}
	
	private void onClose() {
		//Current used to save the mansion.
		/*try {
			BlockBounds bounds = new BlockBounds(new BlockPos(-100, 100, -100), new BlockPos(100, 140, 100));
			for (BlockPos pos : bounds.iterate()) {
				this.map.map.setBlockState(pos, this.world.getBlockState(pos));
			}
			MapTemplateSerializer.save(this.map.map, new Identifier(MurderMystery.MOD_ID, "maps/saved"));
		} catch (IOException e) {
			e.printStackTrace();
		}*/
		this.bows.forEach(Entity::kill);
		this.world.getScoreboard().removeTeam(this.team);
	}
	
	private void tick() {
		this.tasks.forEach(TimerTask::tick);
		this.tasks.removeIf(TimerTask::isFinished);
		
		if (this.ticksTillStart > 0) {
			this.ticksTillStart--;
		} else {
			this.ticks++;
			if (this.ticks >= this.config.gameDuration && this.ticksTillClose < 0) {
				this.broadcastWin(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, new TitleS2CPacket(TitleS2CPacket.Action.TITLE, new LiteralText("Innocents Win!").formatted(Formatting.GREEN, Formatting.BOLD)));
			}
		}
		
		for (ServerPlayerEntity player : this.participants) {
			player.setExperienceLevel(this.ticksTillStart / 20);
			
			if (this.ticksTillStart <= 0 && this.world.getTime() % 20 == 0 && RANDOM.nextFloat() < 0.025F) {
				player.inventory.insertStack(ItemStackBuilder.of(Items.SUNFLOWER).setName(new LiteralText("Coins")).build());
			}
			
			if (this.world.getTime() % 5 == 0 && this.getPlayerRole(player) != Role.DETECTIVE && !this.hasDetectiveBow(player) && player.inventory.contains(new ItemStack(Items.SUNFLOWER))) {
				int coins = this.getCoinCount(player);
				if (coins >= 10) {
					this.takeCoins(player, 10);
					if (!player.inventory.contains(new ItemStack(Items.BOW))) player.inventory.insertStack(ItemStackBuilder.of(Items.BOW).setUnbreakable().build());
					player.inventory.insertStack(new ItemStack(Items.ARROW));
				}
			}
		}
		
		this.world.getEntities(EntityType.ARROW, (entity) -> ((PersistentProjectileEntity) entity).inGround).forEach(projectile -> projectile.kill());
		
		this.bows.forEach(bow -> {
			bow.yaw += 10.0F;
			List<PlayerEntity> collidingInnocents = this.world.getEntities(EntityType.PLAYER, bow.getBoundingBox(), (player) -> player.isAlive() && !player.isSpectator() && this.getPlayerRole((ServerPlayerEntity) player) != Role.MURDERER);
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
			if (this.ticksTillClose <= 0) this.gameWorld.closeWorld();
		}
		
		this.scoreboard.tick();
	}
	
	private void addPlayer(ServerPlayerEntity player) {
		if (!this.participants.contains(player)) this.spawnSpectator(player);
	}
	
	private boolean onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		this.eliminatePlayer(player, player);
		return true;
	}
	
	private boolean onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		Entity attacker = source.getAttacker();
		boolean isPlayer = attacker instanceof ServerPlayerEntity;
		if ((attacker == player || this.ticksTillStart > 0) || isPlayer && this.getPlayerRole((ServerPlayerEntity) attacker) != Role.MURDERER && !source.isProjectile()) return true;
		if (isPlayer) this.eliminatePlayer((ServerPlayerEntity) attacker, player);
		return false;
	}
	
	private TypedActionResult<ItemStack> onUseItem(ServerPlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (CustomItem.match(stack) == MurderMysteryCustomItems.MURDERER_BLADE) {
			//TODO: Add ability for Murderer to throw their sword.
			/*ItemCooldownManager manager = player.getItemCooldownManager();
			Item item = stack.getItem();
			if (!manager.isCoolingDown(item)) {
				manager.set(item, 100);
				player.swingHand(hand);
			}*/
		}
		return TypedActionResult.pass(stack);
	}

	private void spawnParticipant(ServerPlayerEntity player) {
		this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
		this.spawnLogic.spawnPlayer(player);
	}
	
	private void spawnSpectator(ServerPlayerEntity player) {
		this.spawnLogic.resetPlayer(player, GameMode.SPECTATOR);
		this.spawnLogic.spawnPlayer(player);
	}
	
	private void eliminatePlayer(ServerPlayerEntity attacker, ServerPlayerEntity player) {
		Role yourRole = this.getPlayerRole(player);
		if (yourRole == Role.DETECTIVE && this.hasDetectiveBow(player)) {
			ArmorStandEntity stand = new ArmorStandEntity(this.world, player.getX(), player.getY(), player.getZ());
			stand.setInvisible(true);
			
			DataTracker tracker = stand.getDataTracker();
			tracker.set(ArmorStandEntity.ARMOR_STAND_FLAGS, (byte) (tracker.get(ArmorStandEntity.ARMOR_STAND_FLAGS) | 4));
			
			stand.setRightArmRotation(new EulerAngle(180.0F, 0.0F, 32.0F));
			stand.equipStack(EquipmentSlot.MAINHAND, getDetectiveBow());
			stand.setCustomName(new LiteralText("Detective's Bow").formatted(Formatting.BLUE, Formatting.BOLD));
			stand.setCustomNameVisible(true);
			stand.setInvulnerable(true);
			stand.disabledSlots = 65793;
			
			this.world.spawnEntity(stand);
			this.bows.add(stand);
			
			this.broadcastMessage(new LiteralText("Detective Bow Dropped!").formatted(Formatting.GOLD, Formatting.BOLD));
		}
		
		if (attacker != player && this.getPlayerRole(attacker) != Role.MURDERER && yourRole != Role.MURDERER) {
			this.eliminatePlayer(attacker, attacker);
		}
		
		this.roleMap.remove(player.getUuid());
		
		if (this.ticksTillClose < 0) {
			if (this.areNoPlayersWithRoleLeft(Role.MURDERER)) {
				this.broadcastWin(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, new TitleS2CPacket(TitleS2CPacket.Action.TITLE, new LiteralText("Innocents Win!").formatted(Formatting.GREEN, Formatting.BOLD)));
			} else if (this.areNoPlayersWithRoleLeft(Role.DETECTIVE) && this.areNoPlayersWithRoleLeft(Role.INNOCENT)) {
				this.broadcastWin(SoundEvents.ENTITY_WITHER_SPAWN, new TitleS2CPacket(TitleS2CPacket.Action.TITLE, new LiteralText("Murderer Wins!").formatted(Formatting.RED, Formatting.BOLD)));
			}
		}
		
		this.broadcastMessage(player.getDisplayName().shallowCopy().append(" has been eliminated!").formatted(Formatting.RED));
		this.broadcastSound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG);
		this.spawnSpectator(player);
		this.participants.remove(player);
	}
	
	private void applyRole(ServerPlayerEntity player, Role role) {
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
	
	private void broadcastWin(SoundEvent sound, TitleS2CPacket packet) {
		for (ServerPlayerEntity player : this.gameWorld.getPlayers()) {
			player.playSound(sound, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.networkHandler.sendPacket(packet);
			player.inventory.clear();
		}
		this.ticksTillClose = 100;
	}
	
	private static ItemStack getDetectiveBow() {
		return MurderMysteryCustomItems.DETECTIVE_BOW.applyTo(ItemStackBuilder.of(Items.BOW).addEnchantment(Enchantments.INFINITY, 1).setUnbreakable().setName(new LiteralText("Detective's Bow").formatted(Formatting.BLUE, Formatting.ITALIC)).build());
	}
	
	private boolean hasDetectiveBow(ServerPlayerEntity player) {
		for (int i = 0; i < player.inventory.size(); i++) {
			if (CustomItem.match(player.inventory.getStack(i)) == MurderMysteryCustomItems.DETECTIVE_BOW) return true;
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
	
	public long getTimeRemaining() {
		return this.config.gameDuration - this.ticks;
	}
	
	enum Role {
		INNOCENT(Formatting.GREEN, (player) -> {}),
		DETECTIVE(Formatting.BLUE, (player) -> {
			player.inventory.insertStack(1, getDetectiveBow());
			player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.inventory.insertStack(2, new ItemStack(Items.ARROW, 1));
		}),
		MURDERER(Formatting.RED, (player) -> {
			player.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.inventory.insertStack(1, MurderMysteryCustomItems.MURDERER_BLADE.applyTo(ItemStackBuilder.of(Items.NETHERITE_SWORD).setUnbreakable().setName(new LiteralText("Murderer's Blade").formatted(Formatting.RED, Formatting.ITALIC)).build()));
		});
		
		private final Formatting displayColor;
		private final Consumer<ServerPlayerEntity> onApplied;
		
		private Role(Formatting displayColor, Consumer<ServerPlayerEntity> onApplied) {
			this.displayColor = displayColor;
			this.onApplied = onApplied;
		}
	}
	
	static class PlayerRoleMap extends HashMap<UUID, Role> {
		private static final long serialVersionUID = -5696930182002870464L;
		
		public PlayerRoleMap() {}
		
		public void putPlayerRole(ServerPlayerEntity player, Role role) {
			this.put(player.getUuid(), role);
		}
		
		public Role getPlayerRole(ServerPlayerEntity player) {
			return this.get(player.getUuid());
		}
		
		public boolean isRoleEmpty(Role role) {
			return this.values().stream().filter(roleIn -> roleIn == role).collect(Collectors.toList()).size() <= 0;
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
