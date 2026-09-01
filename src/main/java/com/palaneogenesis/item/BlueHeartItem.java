package com.palaneogenesis.item;

import com.palaneogenesis.capability.HeartOrigin;
import com.palaneogenesis.capability.HeartType;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.util.HeartArray;
import com.palaneogenesis.util.Transformation;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Blue Heart - universal currency/crafting item (design doc Section 1). Grants
 * {@link com.palaneogenesis.config.Config.Common#blueHeartPoints} temporary hit points
 * (default 1, medio corazón), sin otro efecto secundario: sin duración, sin partículas
 * de poción, no toca hambre/saturación. Deliberately NOT built on FoodProperties - this isn't
 * food.
 *
 * Usa {@link HeartArray} (array unificado de corazones, ver capability.IHeartArrayData) en vez de
 * LivingEntity#setAbsorptionAmount directamente: ese campo es el mismo que usa la manzana dorada
 * vanilla, así que compartirlo era lo que hacía que el HUD mostrara corazones amarillos (o los
 * dos superpuestos) en vez de los azules del mod. El array del mod es independiente: la manzana
 * dorada sigue dando corazones amarillos vanilla sin que el mod los toque. Blue Heart es la
 * salvaguarda básica del array - sin efecto al romperse, a propósito (ver event.HeartEvents).
 */
public class BlueHeartItem extends Item {

	public BlueHeartItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		// Restricción pedida explícitamente: el array de corazones del mod (ver
		// client.HeartHudOverlay) sólo tiene sentido mientras el jugador está transformado -
		// bloqueado por completo en estado vanilla de Steve, sin gastar el ítem ni otorgar
		// puntos. Mismo chequeo, sin diferenciar cliente/servidor, que ya usa
		// item.AncientExtractSyringeItem#use: Transformation.isTransformed lee de una capability
		// propia del jugador, disponible igual en ambos lados para su propia instancia.
		if (!Transformation.isTransformed(player)) {
			return InteractionResultHolder.fail(stack);
		}

		if (!level.isClientSide) {
			// FIX (origen SYRINGE/PLAYER): este ítem comparte HeartType.BLUE con la Temporary
			// Life que otorga la jeringa (ver item.AncientExtractSyringeItem), pero NO es esa
			// reserva - se agrega como PLAYER, igual que Explosive/Resistance/Inverted, para que
			// compita con ellos por orden de llegada en vez de mezclarse con el fondo reservado
			// de la jeringa (ver capability.HeartArrayData#absorbDamage).
			HeartArray.addPoints(player, HeartType.BLUE, HeartOrigin.PLAYER, Config.COMMON.blueHeartPoints.get());
			// TODO: optional feedback here, e.g.
			// level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.5F);
		}

		player.awardStat(Stats.ITEM_USED.get(this));
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}

		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}