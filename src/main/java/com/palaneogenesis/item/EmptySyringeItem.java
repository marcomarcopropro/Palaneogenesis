package com.palaneogenesis.item;

import com.palaneogenesis.registry.ModItems;
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

		ItemStack brokenSyringe = new ItemStack(ModItems.BROKEN_SYRINGE.get());

		// FIX (Mini-Patch pedido: "Broken Syringe glitchea en vez de apilarse normalmente" -
		// confirmado en video, cada uso deja una Broken Syringe nueva en un slot propio en vez de
		// juntarse en una sola pila). La causa era el orden de operaciones: el fix anterior de
		// este método (ver historial) shrinkeaba `stack` PRIMERO y recién ahí intentaba
		// Inventory#add - cuando ese shrink dejaba el stack de la mano en 0, ese slot quedaba
		// libre ANTES del add(), así que el propio Inventory#add podía terminar poniendo la
		// Broken Syringe recién creada en ese mismo slot para que, un instante después, el
		// motor vanilla lo pisara con el `stack` viejo (ya vacío) - de ahí la rama especial que
		// devolvía brokenSyringe DIRECTO como resultado de use(), sin pasar por Inventory#add en
		// absoluto: cada Broken Syringe terminaba siendo su propia pila nueva en la mano en vez
		// de mergearse con las que ya hubiera en el inventario.
		//
		// Ahora el add() corre ANTES del shrink, mientras `stack` todavía tiene al menos 1 unidad
		// (el slot de la mano nunca está libre durante Inventory#add), así que ya no hace falta
		// la rama especial de "devolver directo": Inventory#add siempre encuentra y mergea con
		// cualquier Broken Syringe existente en el inventario (o abre un slot nuevo si no hay
		// ninguna todavía), igual que cualquier otro ítem stackeable.
		if (!player.getInventory().add(brokenSyringe)) {
			player.drop(brokenSyringe, false);
		}

		stack.shrink(1);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	/**
	 * Sección 3.5: "reverting is assumed symmetric with the death case" - restaura MAX_HEALTH a
	 * 20 (o menos, si el jugador ya perdió corazones rojos por abuso de la mecánica - Fase 3, ver
	 * util.Transformation#registerToggle/getMaxHealthPenaltyHearts), apaga el flag de
	 * transformación y remueve los efectos pasivos integrados de la Sección 3.3 (mismo
	 * AttributeModifier, mismo UUID fijo, agregado en AncientExtractSyringeItem#transform() -
	 * Speed y Attack Damage nada más, no hay un tercer efecto de Resistance, se descartó por
	 * balance, ver util.Transformation). La vida actual se lleva al nuevo máximo (full heal) para
	 * que el jugador no quede con 1 HP reales sobre una barra de 20 corazones - no está escrito
	 * explícitamente en el doc, así que avisar si se prefiere otro comportamiento (p. ej. mantener
	 * la proporción de vida actual).
	 *
	 * FIX (destransformarte ya no borra nada): antes esto vaciaba a 0 TODOS los slots BLUE del
	 * array (Temporary Life de la jeringa Y Blue Heart crafteado, indistinguibles - ver el FIX
	 * documentado en item.AncientExtractSyringeItem). Ahora revert() no toca el array de corazones
	 * en absoluto: tanto lo que queda de la Temporary Life (origen SYRINGE) como los corazones
	 * crafteados (origen PLAYER) sobreviven intactos, guardados, y quedan inertes mientras el
	 * jugador está en forma vanilla - ni se dibujan en el HUD (client.HeartHudOverlay) ni absorben
	 * daño (event.HeartEvents#onLivingDamage), ambos gateados por Transformation.isTransformed. Lo
	 * que haya sobrevivido de la reserva de la jeringa se repone sólo hasta el tope la próxima vez
	 * que el jugador se transforme (ver AncientExtractSyringeItem#transform /
	 * capability.IHeartArrayData#topUpSyringe), sin resetearse a cero acá.
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