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
 * Ancient Extract Syringe - dispara la transformación (doc de Fase 2, Sección 3.2).
 *
 * FIX (origen SYRINGE/PLAYER - Temporary Life se comía Blue Hearts crafteados en cada
 * retransformación): {@code transform()} usaba {@code HeartArray.setPointsOfType(BLUE, ...)},
 * que reemplaza TODOS los slots de un tipo por uno solo, sin distinguir de dónde venía cada uno.
 * Como el Blue Heart crafteado (ver item.BlueHeartItem) agrega puntos con ese mismo
 * HeartType.BLUE, cualquier Blue Heart que el jugador hubiera juntado mientras transformado
 * desaparecía cada vez que se volvía a inyectar la jeringa - y además, si a la Temporary Life le
 * quedaban puntos sin gastar de la transformación anterior (sobrevive un revert(), ver
 * item.EmptySyringeItem), este método los tiraba a la basura y volvía a poner el total entero de
 * nuevo en vez de sólo reponer lo que faltaba. Ahora HeartArray.topUpSyringe (ver
 * capability.IHeartArrayData#topUpSyringe) sólo toca slots de origen SYRINGE del mismo tipo:
 * agrega nada más que la diferencia hasta llegar a TEMPORARY_LIFE_POINTS, nunca resetea a cero y
 * nunca toca los slots PLAYER (Blue Heart crafteado incluido).
 *
 * CAMBIO (pedido explícito, aislado - "eliminar el gesto de tomar una jeringa"): antes seguía el
 * mismo patrón que las pociones vanilla (getUseAnimation/getUseDuration = DRINK/32, use() vía
 * ItemUtils.startUsingInstantly, el trabajo real en finishUsingItem, Sección 3.2 "mirrors
 * vanilla's own potion → glass bottle behavior") - eso hacía que el jugador levantara el brazo en
 * un gesto de "beber/inyectarse" (UseAnim.DRINK) durante esos 32 ticks de carga, que es el único
 * gesto de transformación que existe en todo el mod (TransformationEvents sólo cuelga la
 * capability, no anima nada). No hay ambigüedad sobre cuál era: es este.
 *
 * Ahora sigue el mismo patrón de un solo click instantáneo que ya usaba BlueHeartItem: sin
 * getUseAnimation/getUseDuration (el default de Item ya es UseAnim.NONE / duración 0, no hace
 * falta declararlo), todo el trabajo pasa a use() y finishUsingItem desaparece porque ya no hay
 * ítem "en uso" que completar. El resto de la lógica es exactamente la misma que tenía
 * finishUsingItem antes de este cambio - transform(), el manejo del Empty Syringe remanente
 * (mismo comportamiento: si el stack queda vacío el Empty Syringe pasa a ocupar la mano, si no se
 * intenta agregar al inventario y si no entra se dropea, sigue siendo, a propósito - Sección 3.5 -
 * el ítem que se necesita para revertir la transformación más adelante, ver EmptySyringeItem),
 * awardStat y shrink - sólo se movió de lugar y de disparador, no se tocó.
 */
public class AncientExtractSyringeItem extends Item {

	/** Dos filas completas del Blue Heart Pool ya existente de Fase 1 (10 íconos/fila × 2 puntos × 2 filas).
	 * Decisión Sección 3.2 (consultada, opción B): Temporary Life reusa el tipo BLUE del array
	 * unificado de corazones (capability.IHeartArrayData, ver util.HeartArray) en vez de
	 * absorción vanilla - moneda de Blue Heart y Temporary Life pasan a ser el mismo número. */
	private static final int TEMPORARY_LIFE_POINTS = 40;

	/** Piso del engine para MAX_HEALTH (doc Sección 2): 0 literal no es alcanzable, 1.0 sí. */
	private static final double TRANSFORMED_MAX_HEALTH = 1.0D;

	public AncientExtractSyringeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		// Ya transformado: no tiene sentido re-inyectarse (mismo corte que tenía use() antes de
		// este cambio, ahora es el único lugar que lo necesita).
		if (Transformation.isTransformed(player)) {
			return InteractionResultHolder.pass(stack);
		}

		if (!level.isClientSide) {
			transform(player);
		}

		player.awardStat(Stats.ITEM_USED.get(this));

		if (player.getAbilities().instabuild) {
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}

		stack.shrink(1);
		ItemStack emptySyringe = new ItemStack(ModItems.EMPTY_SYRINGE.get());

		// FIX (mismo bug reportado en item.EmptySyringeItem#use para la Broken Syringe, ver el
		// comentario completo ahí): si el shrink deja el stack de Ancient Extract Syringe en 0
		// (última unidad en mano), Inventory#add podía insertar el Empty Syringe recién creado en
		// ese mismo slot que se acaba de vaciar - y el setItemInHand que hace el motor vanilla
		// justo después de que use() retorna (porque el count cambió) pisaba ese slot con el
		// `stack` viejo ya vacío, borrando el Empty Syringe que se acababa de otorgar. Igual que
		// ahí: si el stack quedó vacío, el remainder se devuelve DIRECTO como resultado de use()
		// en vez de pasar por Inventory#add.
		if (stack.isEmpty()) {
			return InteractionResultHolder.sidedSuccess(emptySyringe, level.isClientSide());
		}
		if (!player.getInventory().add(emptySyringe)) {
			player.drop(emptySyringe, false);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	/**
	 * Sección 3.2, los 3 pasos, todos en el mismo método/tick para que nunca haya un frame donde
	 * los números no coincidan (doc Sección 2): baja MAX_HEALTH al piso del engine, otorga/repone
	 * la Temporary Life vía HeartArray (tipo BLUE, origen SYRINGE, topeada en TEMPORARY_LIFE_POINTS
	 * - ver el FIX documentado arriba de la clase), y recién ahí clampea la vida actual - en ese
	 * orden, porque setHealth clampea contra el getMaxHealth() vigente en el momento de la llamada.
	 *
	 * Después de eso, Sección 3.3: aplica los efectos pasivos integrados (Speed y Attack Damage
	 * como AttributeModifier permanente, con UUID fijo en Transformation para que
	 * EmptySyringeItem#revert() pueda sacar exactamente ese mismo modifier más adelante). No hay
	 * un tercer efecto de Resistance: se descartó por balance (ver util.Transformation).
	 */
	private static void transform(Player player) {
		// Fase 3: cuenta como un toggle para la penalización por abuso (ver
		// util.Transformation#registerToggle) - tiene que ir antes de tocar MAX_HEALTH nada más
		// por consistencia con EmptySyringeItem#revert(), aunque acá no cambia el resultado
		// inmediato: TRANSFORMED_MAX_HEALTH es un piso fijo, no depende de la penalización.
		Transformation.registerToggle(player);

		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(TRANSFORMED_MAX_HEALTH);
		}

		HeartArray.topUpSyringe(player, HeartType.BLUE, TEMPORARY_LIFE_POINTS);
		player.setHealth((float) TRANSFORMED_MAX_HEALTH);

		AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (movementSpeed != null && !movementSpeed.hasModifier(Transformation.SPEED_MODIFIER)) {
			movementSpeed.addPermanentModifier(Transformation.SPEED_MODIFIER);
		}

		AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attackDamage != null && !attackDamage.hasModifier(Transformation.ATTACK_DAMAGE_MODIFIER)) {
			attackDamage.addPermanentModifier(Transformation.ATTACK_DAMAGE_MODIFIER);
		}

		Transformation.set(player, true);
	}
}