package com.palaneogenesis.item.biomod;

import com.palaneogenesis.biomod.module.BiomodModule;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.biomod.module.ModuleScanner;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio.DropRule;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Módulo de biomodificación (documento de diseño, Sección 4): ítem de stack 1 que va en el slot
 * "biomod" de Curios. Hay un solo ítem por tipo (Pistón, Mega Jump, Embestida, Pico, Drones) y
 * cada copia equipada es una unidad.
 *
 * Reglas de equipamiento que se resuelven acá (T2.1):
 * - límite de unidades por tipo (BiomodModule#maxUnits).
 * Las demás reglas (inyectado y sesión con las Pinzas) las aplica event.BiomodEvents sobre los
 * eventos de Curios; el bloqueo anti hot-swap llega en la Fase 8.
 *
 * Regla de drop (T1.7, P-20): los módulos nunca se sueltan al morir.
 */
public class BiomodModuleItem extends Item implements ICurioItem {

	private final BiomodModule module;

	public BiomodModuleItem(Properties properties, BiomodModule module) {
		super(properties);
		this.module = module;
	}

	public BiomodModule module() {
		return module;
	}

	/**
	 * Se puede equipar si, sin contar el slot que se está llenando, todavía queda lugar en el
	 * límite del tipo. Se cuenta en vivo (no con la caché) porque Curios avisa del cambio recién
	 * después de que ya pasó.
	 */
	@Override
	public boolean canEquip(SlotContext slotContext, ItemStack stack) {
		ModuleCounts others = ModuleScanner.scan(slotContext.entity(), slotContext);
		return others.get(module) < module.maxUnits();
	}

	@Override
	public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
		return true;
	}

	/** Los módulos nunca se sueltan al morir (P-20): son modificaciones biológicas. */
	@Override
	public DropRule getDropRule(SlotContext slotContext, DamageSource source, int lootingLevel, boolean recentlyHit, ItemStack stack) {
		return DropRule.ALWAYS_KEEP;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.palaneogenesis.biomod.module", module.maxUnits())
			.withStyle(ChatFormatting.GRAY));
	}
}
