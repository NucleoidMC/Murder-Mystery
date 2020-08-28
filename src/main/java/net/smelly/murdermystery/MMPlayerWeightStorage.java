package net.smelly.murdermystery;

import com.google.common.collect.Maps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import xyz.nucleoid.plasmid.storage.ServerStorage;

import java.util.Map;
import java.util.UUID;

/**
 * @author SmellyModder (Luke Tonon)
 */
public final class MMPlayerWeightStorage implements ServerStorage {
    private final Map<UUID, Integer> murdererWeights;
    private final Map<UUID, Integer> detectiveWeights;

    public MMPlayerWeightStorage() {
        this.murdererWeights = Maps.newHashMap();
        this.detectiveWeights = Maps.newHashMap();
    }

    public void putPlayerWeight(UUID uuid, int weight, boolean murderer) {
        if (murderer) {
            this.murdererWeights.put(uuid, weight);
        } else {
            this.detectiveWeights.put(uuid, weight);
        }
    }

    public void incrementPlayerWeight(UUID uuid, boolean murderer) {
        this.putPlayerWeight(uuid, this.getPlayerWeight(uuid, murderer) + 1, murderer);
    }

    public int getPlayerWeight(UUID uuid, boolean murderer) {
        return murderer ? this.murdererWeights.getOrDefault(uuid, 1) : this.detectiveWeights.getOrDefault(uuid, 1);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag murdererWeights = new ListTag();
        ListTag detectiveWeights = new ListTag();
        this.murdererWeights.forEach((uuid, weight) -> murdererWeights.add(this.createWeightEntryTag(uuid, weight)));
        this.detectiveWeights.forEach((uuid, weight) -> detectiveWeights.add(this.createWeightEntryTag(uuid, weight)));
        tag.put("MurdererWeights", murdererWeights);
        tag.put("DetectiveWeights", detectiveWeights);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag compoundTag) {
        compoundTag.getList("MurdererWeights", 10).forEach(entry -> {
            CompoundTag entryTag = (CompoundTag) entry;
            this.murdererWeights.put(entryTag.getUuid("UUID"), entryTag.getInt("Weight"));
        });
        compoundTag.getList("DetectiveWeights", 10).forEach(entry -> {
            CompoundTag entryTag = (CompoundTag) entry;
            this.detectiveWeights.put(entryTag.getUuid("UUID"), entryTag.getInt("Weight"));
        });
    }

    private CompoundTag createWeightEntryTag(UUID uuid, int weight) {
        CompoundTag entryTag = new CompoundTag();
        entryTag.putUuid("UUID", uuid);
        entryTag.putInt("Weight", weight);
        return entryTag;
    }
}