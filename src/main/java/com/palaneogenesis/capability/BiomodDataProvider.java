package com.palaneogenesis.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BiomodDataProvider implements ICapabilitySerializable<CompoundTag> {

	private final IBiomodData data = new BiomodData();
	private final LazyOptional<IBiomodData> holder = LazyOptional.of(() -> data);

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
		return Capabilities.BIOMOD_DATA.orEmpty(cap, holder);
	}

	/**
	 * Se persiste "injected" y los tiempos de enfriamiento (P-22: desconectarse no saltea un
	 * cooldown). La sesión de modificación y el estado ACTIVA son transitorios; una ACTIVA se guarda
	 * como el cooldown que le tocaría al terminar (ver AbilityTimers#toPersistent).
	 */
	@Override
	public CompoundTag serializeNBT() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("Injected", data.isInjected());
		tag.putIntArray("Cooldowns", data.getTimers().toPersistent());
		ListTag targets = new ListTag();
		for (String typeId : data.getTargets().view()) {
			targets.add(StringTag.valueOf(typeId));
		}
		tag.put("TargetsProtected", targets);
		return tag;
	}

	@Override
	public void deserializeNBT(CompoundTag tag) {
		data.setInjected(tag.getBoolean("Injected"));
		if (tag.contains("Cooldowns", Tag.TAG_INT_ARRAY)) {
			data.getTimers().fromPersistent(tag.getIntArray("Cooldowns"));
		}
		// Sin la clave (jugador de antes de T2.7) queda la lista por defecto; con la clave, aunque
		// esté vacía, se respeta tal cual (el jugador pudo quitar hasta el aldeano).
		if (tag.contains("TargetsProtected", Tag.TAG_LIST)) {
			ListTag targets = tag.getList("TargetsProtected", Tag.TAG_STRING);
			List<String> ids = new ArrayList<>();
			for (int i = 0; i < targets.size(); i++) {
				ids.add(targets.getString(i));
			}
			data.getTargets().replaceWith(ids);
		}
	}
}
