package net.smelly.murdermystery.spawning;

import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.block.Block;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.Tag;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos.Mutable;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class StandingOnTagPredicate extends SpawnBoundPredicate<StandingOnTagPredicate> {
	private Identifier id;
	private Tag<Block> tag;
	
	public StandingOnTagPredicate() {
		this(null);
	}
	
	private StandingOnTagPredicate(Identifier id) {
		super(
			RecordCodecBuilder.create(instance -> {
				return instance.group(
					Identifier.CODEC.fieldOf("tag").forGetter(predicate -> predicate.id)
				).apply(instance, StandingOnTagPredicate::new);
			})
		);
		this.id = id;
	}
	
	@Override
	public void loadConfig(StandingOnTagPredicate config) {
		this.id = config.id;
		this.tag = BlockTags.getTagGroup().getTag(this.id);
		if (this.tag == null) throw new NullPointerException(String.format("The tag with id %s could not be found", this.id));
	}

	@Override
	public boolean test(ServerWorld world, Mutable mutable) {
		return world.getBlockState(mutable.toImmutable().down()).isIn(this.tag);
	}
}