package com.palaneogenesis.item;

import com.palaneogenesis.capability.HeartOrigin;
import com.palaneogenesis.capability.HeartType;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.util.HeartArray;
import com.palaneogenesis.util.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Inverted Heart (Blue_Hearts.md): ½ corazón de vida, sin efecto activo. Efecto al romperse (ver
 * event.HeartEvents#triggerLifeDrain; comportamiento revisado esta sesión - ya NO es una
 * explosión, eso quedó exclusivo de Explosive Heart): en vez de dar vida, la quita - inflige daño
 * real (por defecto 500, la vida del Warden; Config.COMMON.invertedHeartDamage) a las entidades
 * hostiles en un radio de 100 bloques por defecto (radio propio e independiente del de Explosive,
 * Config.COMMON.invertedHeartExplosionRadius), con la Ender Dragon como única excepción
 * explícita. Visual tipo "chasquido de Thanos" (partículas, sin onda expansiva ni sonido de
 * explosión) en vez de la vieja explosión compartida con Explosive Heart. Nombre conservado tal
 * como fue definido originalmente.
 */
public class InvertedHeartItem extends Item {

	public InvertedHeartItem(Properties properties) {
		super(properties);
	}

	/** Pulido pedido esta sesión: nombre en juego en "morado oscuro / casi negro" (mismo criterio
	 * que Explosive/Resistance Heart, ver sus getName). Se usa ChatFormatting.DARK_PURPLE, el
	 * único código de formato vanilla que coincide con "dark purple" tal cual se pidió - si en
	 * cambio se quería un tono más cercano a negro puro que a púrpura, avisar y se cambia esto por
	 * un TextColor a medida (Style.EMPTY.withColor(0x...)) en vez del ChatFormatting fijo. */
	@Override
	public Component getName(ItemStack stack) {
		return super.getName(stack).copy().withStyle(ChatFormatting.DARK_PURPLE);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		// Fase 3: misma restricción que ya tenía BlueHeartItem, extendida ahora a todos los
		// corazones especiales (pedido explícito) - bloqueado por completo en estado vanilla de
		// Steve, sin gastar el ítem ni otorgar puntos.
		if (!Transformation.isTransformed(player)) {
			return InteractionResultHolder.fail(stack);
		}

		if (!level.isClientSide) {
			HeartArray.addPoints(player, HeartType.INVERTED, HeartOrigin.PLAYER, Config.COMMON.invertedHeartPoints.get());
		}

		player.awardStat(Stats.ITEM_USED.get(this));
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}

		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}