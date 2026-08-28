package com.palaneogenesis.util;

import com.palaneogenesis.capability.Capabilities;
import com.palaneogenesis.capability.ITransformationData;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.network.NetworkHandler;
import com.palaneogenesis.network.TransformationSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Acceso estático al flag de transformación (ver {@link com.palaneogenesis.capability.ITransformationData}),
 * mismo rol que {@link BlueHeartPool} para el pool de corazones: el resto del mod no debería
 * llamar a {@code player.getCapability(...)} directamente, sino pasar por acá.
 *
 * También centraliza las constantes de los efectos pasivos integrados (doc Fase 2, Sección 3.3):
 * Speed y Attack Damage necesitan el mismo AttributeModifier (mismo UUID) tanto para agregarlo
 * en AncientExtractSyringeItem#transform() como para sacarlo en EmptySyringeItem#revert() - de
 * ahí que vivan acá, en un solo lugar, en vez de duplicados en cada item.
 *
 * Un tercer efecto pasivo (Resistance, -20% daño entrante) estuvo documentado acá en su momento
 * pero nunca se implementó (el multiplicador quedó definido sin que nada lo aplicara); se
 * descarta la idea por completo por balance, no por bug - la transformación se queda en 2 efectos
 * pasivos integrados (Speed + Attack Damage).
 *
 * FIX: el flag de transformación SÍ necesita llegar al cliente (a diferencia de lo que decía este
 * comentario antes) - se lee del lado cliente en item.AncientExtractSyringeItem#use,
 * item.EmptySyringeItem#use y client.HeartHudOverlay, y esos tres necesitan ver el mismo valor
 * que el servidor o el cliente predice mal (ver network.TransformationSyncPacket para el detalle
 * del bug que esto causaba). Por eso, igual que HeartArray#sync para el array de corazones,
 * #set() ahora empuja el nuevo valor al dueño cada vez que cambia.
 */
public final class Transformation {

	private Transformation() {
	}

	public static boolean isTransformed(Player player) {
		return player.getCapability(Capabilities.TRANSFORMATION_DATA)
			.map(com.palaneogenesis.capability.ITransformationData::isTransformed)
			.orElse(false);
	}

	public static void set(Player player, boolean transformed) {
		player.getCapability(Capabilities.TRANSFORMATION_DATA).ifPresent(data -> {
			data.setTransformed(transformed);
			sync(player);
		});
	}

	/** Empuja el estado actual al dueño únicamente (mismo rol que HeartArray#sync). No-op si
	 * {@code player} no es un ServerPlayer real (ej. si algo lo llama por error del lado
	 * cliente, o antes de que la capability exista todavía).
	 *
	 * FIX (bug reportado: "la mecánica visual de broken hearts no funciona"). Este paquete sólo
	 * mandaba isTransformed() - getMaxHealthPenaltyHearts() nunca llegaba al cliente, así que
	 * client.BrokenHeartHudOverlay (que lee ese valor de la copia CLIENTE de esta misma
	 * capability, ver #getMaxHealthPenaltyHearts) siempre veía 0 y nunca dibujaba nada, sin
	 * importar cuántos corazones hubiera perdido el jugador en el servidor. Ahora se manda
	 * también ese valor, y se resincroniza cada vez que cambia porque registerToggle() (el único
	 * lugar que lo modifica) siempre corre justo antes de un set() que ya dispara este mismo
	 * sync() (ver EmptySyringeItem#revert / AncientExtractSyringeItem#transform). */
	public static void sync(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		player.getCapability(Capabilities.TRANSFORMATION_DATA).ifPresent(data ->
			NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
				new TransformationSyncPacket(data.isTransformed(), data.getMaxHealthPenaltyHearts()))
		);
	}

	// --- Fase 3: penalización por abuso de la mecánica ---

	/**
	 * Registra un toggle (una llamada a transform() o a revert(), da igual cuál de los dos: el
	 * pedido es "se transforma Y destransforma muchas veces seguidas", así que ambas acciones
	 * cuentan para el mismo contador). Tiene que llamarse ANTES de que transform()/revert() fijen
	 * MAX_HEALTH, para que un revert() que dispare la penalización en esta misma llamada ya vea
	 * el nuevo valor de getMaxHealthPenaltyHearts() al calcular la salud máxima efectiva.
	 *
	 * Usa player.tickCount (mismo campo que ya usa event.HeartEvents para su propio chequeo
	 * periódico) en vez de level().getGameTime(): sólo hace falta medir una ventana corta relativa
	 * al propio jugador, no un reloj global del mundo.
	 */
	public static void registerToggle(Player player) {
		player.getCapability(Capabilities.TRANSFORMATION_DATA).ifPresent(data -> {
			int now = player.tickCount;
			int windowTicks = Config.COMMON.transformationAbuseWindowTicks.get();

			boolean withinWindow = data.getLastToggleTick() != Integer.MIN_VALUE
				&& now - data.getLastToggleTick() <= windowTicks;
			int count = withinWindow ? data.getRecentToggleCount() + 1 : 1;

			data.setRecentToggleCount(count);
			data.setLastToggleTick(now);

			int threshold = Config.COMMON.transformationAbuseToggleThreshold.get();
			if (count >= threshold) {
				data.setMaxHealthPenaltyHearts(data.getMaxHealthPenaltyHearts() + 1);
				// Reinicia el contador para que la penalización no se repita en cada toggle
				// subsiguiente, sólo cada vez que se vuelve a juntar otra racha completa.
				data.setRecentToggleCount(0);
			}
		});
	}

	/** Corazones rojos de salud máxima perdidos permanentemente por abuso (ver registerToggle()).
	 * 0 si el jugador nunca gatilló la penalización. */
	public static int getMaxHealthPenaltyHearts(Player player) {
		return player.getCapability(Capabilities.TRANSFORMATION_DATA)
			.map(ITransformationData::getMaxHealthPenaltyHearts)
			.orElse(0);
	}

	// --- Efectos pasivos integrados (Sección 3.3) ---

	private static final UUID SPEED_MODIFIER_ID = UUID.fromString("6b3e4a70-27f0-4c1a-9d3b-1a29a3d0e2a1");
	private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("a02e7c4d-df41-4b8e-8a02-4f2e8c1d9b73");

	/**
	 * +30% velocidad de movimiento. MULTIPLY_TOTAL: misma operación que usa el efecto Speed
	 * vanilla (Sección 3.3, nota técnica), aplicada acá como AttributeModifier permanente en vez
	 * de MobEffectInstance - no aparece en la lista de efectos, no hay ícono ni partículas.
	 */
	public static final AttributeModifier SPEED_MODIFIER = new AttributeModifier(
		SPEED_MODIFIER_ID, "palaneogenesis:transformation_speed", 0.30D, AttributeModifier.Operation.MULTIPLY_TOTAL);

	/**
	 * +5 daño de ataque plano.
	 *
	 * RECALIBRADO (pedido explícito, reemplaza el valor anterior de +10 de la Sección 3.3): el
	 * valor viejo dejaba el puño limpio transformado en 1.0 (ATTACK_DAMAGE base del jugador
	 * vanilla, sin herramienta) + 10.0 = 11.0, suficiente para matar a un cerdo (10 HP vanilla)
	 * de un solo golpe - exactamente el one-shot que se pidió evitar. El pedido puntual es que un
	 * golpe reste el 60% de la vida de un cerdo: 10 HP × 0.6 = 6.0 de daño, así que el modificador
	 * queda en 6.0 - 1.0 (base) = 5.0, y hace falta un segundo golpe (6.0 + 6.0 = 12.0 > 10.0)
	 * para matarlo. Sigue siendo un AttributeModifier sobre ATTACK_DAMAGE en general (no sólo
	 * puño limpio) - mismo alcance que ya tenía este modificador, sólo se ajustó la magnitud.
	 */
	public static final AttributeModifier ATTACK_DAMAGE_MODIFIER = new AttributeModifier(
		ATTACK_DAMAGE_MODIFIER_ID, "palaneogenesis:transformation_attack_damage", 5.0D, AttributeModifier.Operation.ADDITION);
}