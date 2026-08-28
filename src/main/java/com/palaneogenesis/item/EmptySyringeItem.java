package com.palaneogenesis.item;

import com.palaneogenesis.capability.HeartType;
import com.palaneogenesis.registry.ModItems;
import com.palaneogenesis.util.HeartArray;
import com.palaneogenesis.util.Transformation;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 * FIX (bug reportado en video: la racha de Fase 3 nunca gatillaba). Este ítem había quedado con
 * el viejo patrón DRINK/32 (ItemUtils.startUsingInstantly + finishUsingItem) que Ancient Extract
 * Syringe tenía ANTES de que se le sacara el gesto de "tomar la jeringa" (ver el CAMBIO
 * documentado en AncientExtractSyringeItem) - ese cambio nunca se replicó acá. Con esa animación,
 * cada revert() real necesitaba 32 ticks (1.6s) sostenidos de click; sólo 5 reverts ya suman 160
 * ticks, más que transformationAbuseWindowTicks entero (100 ticks / 5s por default) - así que la
 * racha de util.Transformation#registerToggle nunca llegaba a juntar 5 toggles dentro de la
 * ventana sin importar qué tan rápido clickeara el jugador (confirmado en el video: el contador
 * de Broken Syringe sube varias veces seguidas y la vida máxima nunca baja). Ahora sigue
 * exactamente el mismo patrón instantáneo de un solo click que ya usa AncientExtractSyringeItem:
 * sin getUseAnimation/getUseDuration (el default de Item ya es UseAnim.NONE / duración 0), todo
 * el trabajo pasa a use() y finishUsingItem desaparece. El resto de la lógica (revert(), el
 * manejo del remainder Broken Syringe, awardStat y shrink) es exactamente la misma que tenía
 * finishUsingItem antes de este cambio - sólo se movió de lugar y de disparador. Exactamente 1
 * unidad se consume del stack sin importar su tamaño, igual que Ancient Extract Syringe.
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
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		// No transformado: el Empty Syringe no tiene nada que hacer acá, es solo el componente de
		// crafteo de Fase 1 (mismo corte que tenía use() antes de este cambio).
		if (!Transformation.isTransformed(player)) {
			return InteractionResultHolder.pass(stack);
		}

		if (!level.isClientSide) {
			revert(player);
		}

		player.awardStat(Stats.ITEM_USED.get(this));

		if (player.getAbilities().instabuild) {
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}

		stack.shrink(1);
		ItemStack brokenSyringe = new ItemStack(ModItems.BROKEN_SYRINGE.get());

		// FIX (bug reportado: "al destransformarse con la ÚLTIMA Empty Syringe del stack,
		// desaparecen tanto la Empty Syringe como la Broken Syringe"). La rama de abajo (mergear
		// vía Inventory#add) fue en su momento el fix correcto para el bug VIEJO ("se crea una
		// jeringa rota de forma individual y no como las pociones que se van acumulando"), pero
		// sólo es segura cuando el stack en mano SIGUE teniendo algo después del shrink. Cuando
		// el shrink lo deja en 0 (stack.isEmpty()), ese slot de la mano queda libre justo ANTES
		// de llamar a Inventory#add(-1, ...) - y ese método busca el primer slot libre/mergeable
		// SIN excluir el que el jugador tiene seleccionado, así que la Broken Syringe recién
		// creada puede terminar cayendo exactamente en ese mismo slot. El problema es el paso
		// siguiente: el motor vanilla, apenas use() retorna, compara el count devuelto contra el
		// que tenía antes de entrar acá y, si cambió (shrink SIEMPRE lo cambia), hace
		// player.setItemInHand(hand, <lo que devolvimos>) - pisando ese slot con el `stack` viejo
		// (ya vacío) sin importar qué haya puesto Inventory#add ahí un instante antes. Resultado
		// visible: ni la Empty Syringe ni la Broken Syringe quedan en ese slot.
		//
		// Mismo criterio que ItemUtils.createFilledResult de vanilla (poción -> frasco vacío):
		// si el stack original quedó vacío, el remainder se devuelve DIRECTO como resultado de
		// use() - así el motor lo coloca él mismo en la mano, sin competir con Inventory#add por
		// el mismo slot. Sólo cuando sobra Empty Syringe en el stack (rama de abajo, el slot de
		// la mano NO queda libre) hace falta intentar acumularlo en el inventario o dropearlo.
		if (stack.isEmpty()) {
			return InteractionResultHolder.sidedSuccess(brokenSyringe, level.isClientSide());
		}
		if (!player.getInventory().add(brokenSyringe)) {
			player.drop(brokenSyringe, false);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
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