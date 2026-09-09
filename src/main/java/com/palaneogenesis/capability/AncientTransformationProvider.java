package com.palaneogenesis.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Holder + serialización NBT de {@link IAncientTransformationData} para un Player puntual. Colgado por
 * {@link com.palaneogenesis.event.AncientTransformationEvents} en AttachCapabilitiesEvent.
 */
public class AncientTransformationProvider implements ICapabilitySerializable<CompoundTag> {

	private final IAncientTransformationData data = new AncientTransformationData();
	private final LazyOptional<IAncientTransformationData> holder = LazyOptional.of(() -> data);

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
		return Capabilities.ANCIENT_TRANSFORMATION_DATA.orEmpty(cap, holder);
	}

	@Override
	public CompoundTag serializeNBT() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("Transformed", data.isTransformed());
		// Sólo el castigo permanente de Fase 3 se persiste - recentToggleCount/lastToggleTick son
		// un timer de corto plazo (ver IAncientTransformationData) y se quedan siempre en su default.
		tag.putInt("MaxHealthPenaltyHearts", data.getMaxHealthPenaltyHearts());
		return tag;
	}

	@Override
	public void deserializeNBT(CompoundTag tag) {
		data.setTransformed(tag.getBoolean("Transformed"));
		data.setMaxHealthPenaltyHearts(tag.getInt("MaxHealthPenaltyHearts"));
	}
}