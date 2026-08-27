package com.palaneogenesis.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Holder + serialización NBT de {@link IHeartArrayData} para un Player puntual. Colgado por
 * {@link com.palaneogenesis.event.HeartArrayEvents} en AttachCapabilitiesEvent - mismo patrón que
 * TransformationProvider, pero cada slot se persiste con su tipo y sus puntos por separado (no un
 * total por tipo), para no perder los límites entre slots que deciden cuántas veces dispara el
 * efecto "al romperse" de un tipo (ver event.HeartEvents) después de un guardado/carga.
 */
public class HeartArrayProvider implements ICapabilitySerializable<CompoundTag> {

	private final IHeartArrayData data = new HeartArrayData();
	private final LazyOptional<IHeartArrayData> holder = LazyOptional.of(() -> data);

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
		return Capabilities.HEART_ARRAY_DATA.orEmpty(cap, holder);
	}

	@Override
	public CompoundTag serializeNBT() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (IHeartArrayData.HeartSlot slot : data.snapshot()) {
			CompoundTag entry = new CompoundTag();
			entry.putString("Type", slot.type().name());
			entry.putInt("Points", slot.points());
			list.add(entry);
		}
		tag.put("Slots", list);
		return tag;
	}

	@Override
	public void deserializeNBT(CompoundTag tag) {
		List<IHeartArrayData.HeartSlot> slots = new ArrayList<>();
		ListTag list = tag.getList("Slots", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			try {
				HeartType type = HeartType.valueOf(entry.getString("Type"));
				slots.add(new IHeartArrayData.HeartSlot(type, entry.getInt("Points")));
			} catch (IllegalArgumentException ignored) {
				// Tipo desconocido (dato guardado por una versión vieja del mod, o corrupto): se
				// descarta ese slot puntual en vez de fallar la carga completa del jugador.
			}
		}
		data.restore(slots);
	}
}
