package com.palaneogenesis.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BiomodDataProvider implements ICapabilitySerializable<CompoundTag> {

	private final IBiomodData data = new BiomodData();
	private final LazyOptional<IBiomodData> holder = LazyOptional.of(() -> data);

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
		return Capabilities.BIOMOD_DATA.orEmpty(cap, holder);
	}

	/** Solo se persiste "injected". La sesión de modificación es transitoria. */
	@Override
	public CompoundTag serializeNBT() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("Injected", data.isInjected());
		return tag;
	}

	@Override
	public void deserializeNBT(CompoundTag tag) {
		data.setInjected(tag.getBoolean("Injected"));
	}
}
