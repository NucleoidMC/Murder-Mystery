package net.smelly.murdermystery.game.custom;

import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.EulerAngle;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MurdererBladeEntity extends ArmorStandEntity {
	private final PlayerEntity murderer;
	private final Vec3d velocity;
	
	private MurdererBladeEntity(World world, PlayerEntity murderer, Vec3d velocity, double x, double y, double z) {
		super(world, x, y, z);
		this.setInvisible(true);
		this.equipStack(EquipmentSlot.MAINHAND, this.getMurdererBlade());
		this.noClip = true;
		this.disabledSlots = 65793;
		this.dataTracker.set(ARMOR_STAND_FLAGS, (byte) (this.dataTracker.get(ArmorStandEntity.ARMOR_STAND_FLAGS) | 4));
		this.murderer = murderer;
		this.velocity = velocity;
	}
	
	public static void throwBlade(PlayerEntity player) {
		float yaw = player.yaw;
		float pitch = player.pitch;
		
		Vec3d offset = new Vec3d(0.0F, 0.0F, 0.2F).rotateY(yaw);
		Vec3d playerVelocity = player.getVelocity();
		Vec3d bladeVelocity = new Vec3d(playerVelocity.getX(), player.isOnGround() ? 0.0F : playerVelocity.getY(), playerVelocity.getZ()).add(new Vec3d(-MathHelper.sin(yaw * ((float) Math.PI / 180F)) * MathHelper.cos(pitch * ((float) Math.PI / 180F)), -MathHelper.sin((pitch) * ((float) Math.PI / 180F)), MathHelper.cos(yaw * ((float) Math.PI / 180F)) * MathHelper.cos(pitch * ((float) Math.PI / 180F))));
		MurdererBladeEntity blade = new MurdererBladeEntity(player.world, player, bladeVelocity.multiply(0.5F), player.getX() + offset.getX(), player.getY(), player.getZ() + offset.getZ());
		
		blade.setRightArmRotation(new EulerAngle(352.0F, 0.0F, 270.0F));
		blade.yaw = yaw;
		player.world.spawnEntity(blade);
	}
	
	@Override
	public void tick() {
		super.tick();
		boolean collided = !(this.world.getBlockCollisions(this, this.getBladeBoundingBox().expand(-0.75F)).count() == 0);
		if (this.murderer.isDead() || this.age >= 100 || collided) {
			if (collided) this.world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_NETHERITE_BLOCK_BREAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
			this.kill();
		}
		this.setVelocity(this.velocity);
	}
	
	@Override
	protected void tickCramming() {
		List<Entity> entities = this.world.getOtherEntities(this, this.getBladeBoundingBox(), entity -> entity instanceof PlayerEntity && !entity.isSpectator() && !((PlayerEntity) entity).isCreative() && entity != this.murderer);
		if (!entities.isEmpty()) {
			entities.get(0).damage(DamageSource.thrownProjectile(this, this.murderer), Float.MAX_VALUE);
			this.kill();
		}
	}
	
	private ItemStack getMurdererBlade() {
		return ItemStackBuilder.of(MMCustomItems.MURDERER_BLADE).setUnbreakable().setName(new LiteralText("Murderer's Blade").formatted(Formatting.RED, Formatting.ITALIC)).build();
	}
	
	private Box getBladeBoundingBox() {
		Box boundingBox = this.getBoundingBox();
		return new Box(boundingBox.minX, boundingBox.minY + 0.5F, boundingBox.minZ, boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ).expand(0.25F);
	}

	@Override
	public void remove() {
		if (!this.world.isClient && this.murderer.isAlive() && !this.murderer.isSpectator() && this.murderer.world.getDimension() == this.world.getDimension()) {
			this.murderer.inventory.insertStack(1, this.getMurdererBlade());
		}
		super.remove();
	}
}