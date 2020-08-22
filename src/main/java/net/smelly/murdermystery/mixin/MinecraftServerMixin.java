package net.smelly.murdermystery.mixin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.smelly.murdermystery.MurderMystery;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
	private static final File WEIGHT_SAVE_DIRECTORY = new File(".", "murder_mystery");
	
	@Inject(at = @At("RETURN"), method = "save")
	private void save(boolean bl, boolean b2, boolean b3, CallbackInfoReturnable<Boolean> info) {
		if (!WEIGHT_SAVE_DIRECTORY.exists()) {
			WEIGHT_SAVE_DIRECTORY.mkdirs();
		}
		File savedWeights = new File(WEIGHT_SAVE_DIRECTORY, "weight_saves.nbt");
		
		CompoundTag compoundTag = new CompoundTag();
		ListTag murdererWeights = new ListTag();
		ListTag detectiveWeights = new ListTag();
		
		MurderMystery.MURDERER_WEIGHT_MAP.entrySet().forEach((entry) -> murdererWeights.add(this.createEntryTag(entry)));
		MurderMystery.DETECTIVE_WEIGHT_MAP.entrySet().forEach((entry) -> detectiveWeights.add(this.createEntryTag(entry)));
		
		compoundTag.put("MurdererWeights", murdererWeights);
		compoundTag.put("DetectiveWeights", detectiveWeights);
		
		try {
			NbtIo.method_30614(compoundTag, savedWeights);
		} catch (IOException e) {
			MurderMystery.LOGGER.error("Could not save role weight data {}", compoundTag, e);
		}
	}
	
	@Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;setupServer()Z"), method = "runServer")
	private void setupServer(CallbackInfo info) {
		if (WEIGHT_SAVE_DIRECTORY.exists()) {
			File savedWeights = new File(WEIGHT_SAVE_DIRECTORY, "weight_saves.nbt");
			if (savedWeights.exists()) {
				try {
					CompoundTag compoundTag = NbtIo.method_30613(savedWeights);
					compoundTag.getList("MurdererWeights", 10).forEach(entry -> {
						CompoundTag entryTag = (CompoundTag) entry;
						MurderMystery.MURDERER_WEIGHT_MAP.put(entryTag.getUuid("UUID"), entryTag.getInt("Weight"));
					});
					compoundTag.getList("DetectiveWeights", 10).forEach(entry -> {
						CompoundTag entryTag = (CompoundTag) entry;
						MurderMystery.DETECTIVE_WEIGHT_MAP.put(entryTag.getUuid("UUID"), entryTag.getInt("Weight"));
					});
				} catch (IOException e) {
					MurderMystery.LOGGER.error("Could not read role weight data", e);
				}
			}
		}
	}
	
	private CompoundTag createEntryTag(Map.Entry<UUID, Integer> entry) {
		CompoundTag entryTag = new CompoundTag();
		entryTag.putUuid("UUID", entry.getKey());
		entryTag.putInt("Weight", entry.getValue());
		return entryTag;
	}
}