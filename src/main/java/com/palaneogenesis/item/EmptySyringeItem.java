package com.palaneogenesis.item;

import com.palaneogenesis.capability.HeartType;
import com.palaneogenesis.registry.ModItems;
import com.palaneogenesis.util.HeartArray;
import com.palaneogenesis.util.Transformation;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Empty Syringe - doble rol (doc de Fase 2, Sección 3.5). Sigue siendo el ingrediente de
 * crafteo de Fase 1 para la Ancient Extract Syringe (sin cambios ahí), pero ahora ADEMÁS es la
 * herramienta de reversión: usado mientras el jugador está transformado, revierte la
 * transformación y la jeringa se rompe en el proceso (remainder = Broken Syringe).
 *
 * Mientras el jugador NO está transformado, usar este ítem no hace nada especial (comportamiento
 * de Item por defecto, {@code use()} cae a {@code InteractionResultHolder.pass}) - sigue siendo
 * "solo" el componente de crafteo que ya era en Fase 1.
 *
 * Mismo patrón de animación que Ancient Extract Syringe (DRINK/32, ItemUtils.startUsingInstantly,
 * el trabajo real en finishUsingItem) por consistencia y porque la Sección 3.5 lo describe como
 * el mismo patrón de remainder que las pociones vanilla. Exactamente 1 unidad se consume del
 * stack sin importar su tamaño, igual que Ancient Extract Syringe.
 */
public class EmptySyringeItem extends Item {

	/** Vanilla default (doc Sección 3.5: "max health restored to 20"). */
	private static final double NORMAL_MAX_HEALTH = 20.0D;

	/** Piso de la penalización por abuso (Fase 3, util.Transformation#registerToggle): por más
	 * corazones rojos que se hayan perdido, la salud máxima nunca baja de 1 corazón entero (2.0).
	 * No estaba especificado qué hacer si la penalización vacía la barra entera, así que se avisa
	 * acá el criterio elegido por si se prefiere otro (ej. dejarlo caer hasta el mismo piso de
	 * medio corazón que usa la transformación, TRANSFORMED_MAX_HEALTH = 1.0). */
	private static final double MIN_MAX_HEALTH_AFTER_PENALTY = 2.0D;

	public EmptySyringeItem(Properties properties) {
		super(properties);
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.DRINK;
	}

	@Override
	public int getUseDuration(ItemStack stack) {
		return 32; // mismo tiempo que Ancient Extract Syringe / una poción vanilla
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		// No transformado: el Empty Syringe no tiene nada que hacer acá, es solo el componente de
		// crafteo de Fase 1. Se corta antes de arrancar la animación de uso.
		if (!Transformation.isTransformed(player)) {
			return InteractionResultHolder.pass(player.getItemInHand(hand));
		}
		return ItemUtils.startUsingInstantly(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
		Player player = livingEntity instanceof Player ? (Player) livingEntity : null;

		if (player != null && !level.isClientSide && Transformation.isTransformed(player)) {
			revert(player);
		}

		if (player != null) {
			player.awardStat(Stats.ITEM_USED.get(this));
		}

		if (player == null || !player.getAbilities().instabuild) {
			stack.shrink(1);
		}

		if (player == null || !player.getAbilities().instabuild) {
			ItemStack brokenSyringe = new ItemStack(ModItems.BROKEN_SYRINGE.get());
			if (stack.isEmpty()) {
				return brokenSyringe;
			}
			if (player != null && !player.getInventory().add(brokenSyringe)) {
				player.drop(brokenSyringe, false);
			}
		}

		return stack;
	}

	/**
	 * Sección 3.5: "reverting is assumed symmetric with the death case" - restaura MAX_HEALTH a
	 * 20 (o menos, si el jugador ya perdió corazones rojos por abuso de la mecánica - Fase 3, ver
	 * util.Transformation#registerToggle/getMaxHealthPenaltyHearts), saca toda la Temporary Life
	 * (tipo BLUE del array unificado, ver util.HeartArray, a 0),
	 * apaga el flag de transformación y remueve los efectos pasivos integrados de la Sección 3.3
	 * (mismo AttributeModifier, mismo UUID fijo, agregado en AncientExtractSyringeItem#transform()
	 * - Speed y Attack Damage nada más, no hay un tercer efecto de Resistance, se descartó por
	 * balance, ver util.Transformation). La vida actual se lleva al nuevo máximo (full heal) para
	 * que el jugador no quede con 1 HP reales sobre una barra de 20 corazones - no está escrito
	 * explícitamente en el doc, así que avisar si se prefiere otro comportamiento (p. ej. mantener
	 * la proporción de vida actual).
	 */
	private static void revert(Player player) {
		// Fase 3: cuenta como toggle para la penalización por abuso ANTES de calcular la salud
		// máxima efectiva de acá abajo, para que un revert() que justo complete la racha ya
		// aplique el nuevo corazón perdido en esta misma llamada (ver
		// util.Transformation#registerToggle).
		Transformation.registerToggle(player);

		double effectiveMaxHealth = Math.max(
			MIN_MAX_HEALTH_AFTER_PENALTY,
			NORMAL_MAX_HEALTH - Transformation.getMaxHealthPenaltyHearts(player) * 2.0D);

		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(effectiveMaxHealth);
		}

		HeartArray.setPointsOfType(player, HeartType.BLUE, 0);
		player.setHealth((float) effectiveMaxHealth);

		AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (movementSpeed != null) {
			movementSpeed.removeModifier(Transformation.SPEED_MODIFIER);
		}

		AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attackDamage != null) {
			attackDamage.removeModifier(Transformation.ATTACK_DAMAGE_MODIFIER);
		}

		Transformation.set(player, false);
	}
}