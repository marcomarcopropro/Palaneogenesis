package com.palaneogenesis.item;

import com.palaneogenesis.registry.ModItems;
import com.palaneogenesis.util.BlueHeartPool;
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
 * Ancient Extract Syringe - dispara la transformación (doc de Fase 2, Sección 3.2).
 *
 * Sigue el mismo patrón que las pociones vanilla (getUseAnimation/getUseDuration = DRINK/32,
 * use() vía ItemUtils.startUsingInstantly, el trabajo real en finishUsingItem), tal como pide la
 * Sección 3.2 ("mirrors vanilla's own potion → glass bottle behavior"), en vez del patrón
 * instantáneo de un solo click que usa BlueHeartItem. El remainder (Empty Syringe) sigue
 * exactamente la lógica de PotionItem#finishUsingItem: si el stack queda vacío, el syringe vacío
 * pasa a ocupar la mano; si no, se intenta agregar a modo inventario y, si no entra, se dropea.
 */
public class AncientExtractSyringeItem extends Item {

	/** Dos filas completas del Blue Heart Pool ya existente de Fase 1 (10 íconos/fila × 2 puntos × 2 filas).
	 * Decisión Sección 3.2 (consultada, opción B): Temporary Life reusa BlueHeartPool en vez de
	 * absorción vanilla - moneda de Blue Heart y Temporary Life pasan a ser el mismo número. */
	private static final int TEMPORARY_LIFE_POINTS = 40;

	/** Piso del engine para MAX_HEALTH (doc Sección 2): 0 literal no es alcanzable, 1.0 sí. */
	private static final double TRANSFORMED_MAX_HEALTH = 1.0D;

	public AncientExtractSyringeItem(Properties properties) {
		super(properties);
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.DRINK;
	}

	@Override
	public int getUseDuration(ItemStack stack) {
		return 32; // mismo tiempo que una poción vanilla
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		// Ya transformado: no tiene sentido re-inyectarse. Se corta acá, antes de arrancar la
		// animación de uso, en vez de dejar que finishUsingItem lo descarte más tarde.
		if (Transformation.isTransformed(player)) {
			return InteractionResultHolder.pass(player.getItemInHand(hand));
		}
		return ItemUtils.startUsingInstantly(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
		Player player = livingEntity instanceof Player ? (Player) livingEntity : null;

		if (player != null && !level.isClientSide && !Transformation.isTransformed(player)) {
			transform(player);
		}

		if (player != null) {
			player.awardStat(Stats.ITEM_USED.get(this));
		}

		if (player == null || !player.getAbilities().instabuild) {
			stack.shrink(1);
		}

		if (player == null || !player.getAbilities().instabuild) {
			ItemStack emptySyringe = new ItemStack(ModItems.EMPTY_SYRINGE.get());
			if (stack.isEmpty()) {
				return emptySyringe;
			}
			if (player != null && !player.getInventory().add(emptySyringe)) {
				player.drop(emptySyringe, false);
			}
		}

		return stack;
	}

	/**
	 * Sección 3.2, los 3 pasos, todos en el mismo método/tick para que nunca haya un frame donde
	 * los números no coincidan (doc Sección 2): baja MAX_HEALTH al piso del engine, otorga
	 * Temporary Life vía BlueHeartPool, y recién ahí clampea la vida actual - en ese orden,
	 * porque setHealth clampea contra el getMaxHealth() vigente en el momento de la llamada.
	 */
	private static void transform(Player player) {
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(TRANSFORMED_MAX_HEALTH);
		}

		BlueHeartPool.set(player, TEMPORARY_LIFE_POINTS);
		player.setHealth((float) TRANSFORMED_MAX_HEALTH);

		Transformation.set(player, true);
	}
}
