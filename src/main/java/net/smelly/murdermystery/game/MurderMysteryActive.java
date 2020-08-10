package net.smelly.murdermystery.game;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.mutable.MutableInt;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.ibm.icu.impl.Pair;

import net.gegy1000.plasmid.game.GameWorld;
import net.gegy1000.plasmid.game.event.GameCloseListener;
import net.gegy1000.plasmid.game.event.GameOpenListener;
import net.gegy1000.plasmid.game.event.GameTickListener;
import net.gegy1000.plasmid.game.event.OfferPlayerListener;
import net.gegy1000.plasmid.game.event.PlayerAddListener;
import net.gegy1000.plasmid.game.event.PlayerDamageListener;
import net.gegy1000.plasmid.game.event.PlayerDeathListener;
import net.gegy1000.plasmid.game.player.JoinResult;
import net.gegy1000.plasmid.game.rule.GameRule;
import net.gegy1000.plasmid.game.rule.RuleResult;
import net.gegy1000.plasmid.util.ItemStackBuilder;
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
import net.minecraft.util.math.EulerAngle;
import net.minecraft.world.GameMode;
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
	
	private final Map<ServerPlayerEntity, Role> roleMap = Maps.newHashMap();
	private final Set<TimerTask<?>> tasks = Sets.newHashSet();
	private final Set<ServerPlayerEntity> blacklistedPlayerTasks = Sets.newHashSet();
	public final Set<ArmorStandEntity> bows = Sets.newHashSet();
	
	private final ServerWorld world;
	private final Set<ServerPlayerEntity> participants;
	private Team team;
	
	private int ticksTillStart = 200;
	private int ticksTillClose = -1;
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
			
			game.on(GameTickListener.EVENT, active::tick);
		});
    }
	
	private void onOpen() {
		this.team = this.world.getScoreboard().getTeam("Murder Mystery");
		for (ServerPlayerEntity player : this.participants) {
			this.world.getScoreboard().addPlayerToTeam(player.getEntityName(), this.team);
			this.spawnParticipant(player);
		}
		
		this.broadcastMessage(new LiteralText("Players will receive their roles in 10 seconds!").formatted(Formatting.GREEN, Formatting.BOLD));
		
		MutableInt players = new MutableInt(0);
		this.tasks.add(new TimerTask<>(this, (game) -> {
			for (ServerPlayerEntity player : game.participants) {
				players.add(1);
				
				float chance = RANDOM.nextFloat();
				if (chance > 0.95F && this.getPlayersWithRoleCount(Role.MURDERER) == 0) {
					this.applyRole(player, Role.MURDERER);
				} else if (chance > 0.9F && this.getPlayersWithRoleCount(Role.DETECTIVE) == 0) {
					this.applyRole(player, Role.DETECTIVE);
				} else {
					this.applyRole(player, Role.INNOCENT);
				}
				
				if (players.getValue() >= this.participants.size() - 1) {
					if (this.getPlayersWithRoleCount(Role.DETECTIVE) == 0) {
						this.applyRole(player, Role.DETECTIVE);
					} else if (this.getPlayersWithRoleCount(Role.MURDERER) == 0) {
						this.applyRole(player, Role.MURDERER);
					}
				}
			}
			this.roleMap.forEach((player, role) -> role.onApplied.accept(player));
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
			
			if (!this.blacklistedPlayerTasks.contains(player) && this.getPlayerRole(player) != Role.MURDERER && this.hasDetectiveBow(player) && !player.inventory.contains(new ItemStack(Items.ARROW))) {
				this.tasks.add(new TimerTask<>(Pair.of(this, player), (pair) -> {
					pair.second.inventory.insertStack(new ItemStack(Items.ARROW));
					pair.first.blacklistedPlayerTasks.remove(player);
				}, 60));
				this.blacklistedPlayerTasks.add(player);
			}
			
			if (this.ticksTillStart <= 0 && this.world.getTime() % 20 == 0 && RANDOM.nextFloat() < 0.025F) {
				player.inventory.insertStack(ItemStackBuilder.of(Items.SUNFLOWER).setName(new LiteralText("Coins")).build());
			}
			
			if (this.getPlayerRole(player) != Role.DETECTIVE && player.inventory.contains(new ItemStack(Items.SUNFLOWER))) {
				int coins = this.getCoinCount(player);
				if (coins >= 10) {
					this.takeCoins(player, coins);
					if (!player.inventory.contains(new ItemStack(Items.BOW))) player.inventory.insertStack(ItemStackBuilder.of(Items.BOW).setUnbreakable().build());
					player.inventory.insertStack(new ItemStack(Items.ARROW));
				}
			}
		}
		
		this.world.getEntities(EntityType.ARROW, (entity) -> ((PersistentProjectileEntity) entity).inGround).forEach(projectile -> projectile.kill());
		
		this.bows.forEach(bow -> {
			bow.yaw += 10.0F;
			for (PlayerEntity player : this.world.getEntities(EntityType.PLAYER, bow.getBoundingBox(), (player) -> player.isAlive() && !player.isSpectator() && this.getPlayerRole((ServerPlayerEntity) player) != Role.MURDERER)) {
				player.inventory.insertStack(getDetectiveBow());
				player.inventory.insertStack(new ItemStack(Items.ARROW));
				this.broadcastMessage(new LiteralText("Detective Bow Picked Up!").formatted(Formatting.GOLD, Formatting.BOLD));
				bow.kill();
				break;
			}
		});
		
		this.bows.removeIf(bow -> !bow.isAlive());
		
		if (this.ticksTillClose > 0) {
			this.ticksTillClose--;
			if (this.ticksTillClose <= 0) {
				this.gameWorld.closeWorld();
			}
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
		if ((attacker == player || this.ticksTillStart > 0) || attacker instanceof ServerPlayerEntity && this.getPlayerRole((ServerPlayerEntity) attacker) != Role.MURDERER && !source.isProjectile()) return true;
		if (attacker instanceof ServerPlayerEntity) {
			this.eliminatePlayer((ServerPlayerEntity) attacker, player);
		}
		return false;
    }

	private void spawnParticipant(ServerPlayerEntity player) {
		this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
		this.spawnLogic.spawnPlayer(player);
	}
	
	private void spawnSpectator(ServerPlayerEntity player) {
		this.spawnLogic.resetPlayer(player, GameMode.SPECTATOR);
		this.spawnLogic.spawnPlayer(player);
	}
	
	private void applyRole(ServerPlayerEntity player, Role role) {
		this.roleMap.put(player, role);
		player.networkHandler.sendPacket(new TitleS2CPacket(TitleS2CPacket.Action.TITLE, new LiteralText(role.toString()).formatted(role.displayColor, Formatting.BOLD)));
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
		
		this.roleMap.remove(player);
		
		if (this.ticksTillClose < 0) {
			if (this.getPlayersWithRoleCount(Role.MURDERER) == 0) {
				this.broadcastWin(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, new TitleS2CPacket(TitleS2CPacket.Action.TITLE, new LiteralText("Innocents Win!").formatted(Formatting.GREEN, Formatting.BOLD)));
			} else if (this.getPlayersWithRoleCount(Role.DETECTIVE) == 0 && this.getPlayersWithRoleCount(Role.INNOCENT) == 0) {
				this.broadcastWin(SoundEvents.ENTITY_WITHER_SPAWN, new TitleS2CPacket(TitleS2CPacket.Action.TITLE, new LiteralText("Murderer Wins!").formatted(Formatting.RED, Formatting.BOLD)));
			}
		}
		
		this.broadcastMessage(player.getDisplayName().shallowCopy().append(" has been eliminated!").formatted(Formatting.RED));
		this.broadcastSound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG);
		this.spawnSpectator(player);
		this.participants.remove(player);
    }
	
	private Role getPlayerRole(ServerPlayerEntity player) {
		return this.roleMap.get(player);
	}
	
	private int getPlayersWithRoleCount(Role role) {
		return this.roleMap.values().stream().filter(roleIn -> roleIn == role).collect(Collectors.toList()).size();
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
		return ItemStackBuilder.of(Items.BOW).setUnbreakable().setName(new LiteralText("Detective's Bow").formatted(Formatting.BLUE, Formatting.ITALIC)).build();
	}
	
	private boolean hasDetectiveBow(ServerPlayerEntity player) {
		for (int i = 0; i < player.inventory.size(); i++) {
			ItemStack stack = player.inventory.getStack(i);
			if (stack.getItem() == Items.BOW && stack.getName().asString().equals("Detective's Bow")) return true;
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
			player.inventory.insertStack(getDetectiveBow());
			player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.inventory.insertStack(new ItemStack(Items.ARROW, 1));
		}),
		MURDERER(Formatting.RED, (player) -> {
			ItemStack stack = ItemStackBuilder.of(Items.NETHERITE_SWORD).setUnbreakable().setName(new LiteralText("Murderer's Blade").formatted(Formatting.RED, Formatting.ITALIC)).build();
			player.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.inventory.insertStack(stack);
		});
		
		private final Formatting displayColor;
		private final Consumer<ServerPlayerEntity> onApplied;
		
		private Role(Formatting displayColor, Consumer<ServerPlayerEntity> onApplied) {
			this.displayColor = displayColor;
			this.onApplied = onApplied;
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