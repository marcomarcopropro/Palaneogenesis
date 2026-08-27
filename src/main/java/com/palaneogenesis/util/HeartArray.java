package com.palaneogenesis.util;

import com.palaneogenesis.capability.Capabilities;
import com.palaneogenesis.capability.HeartType;
import com.palaneogenesis.capability.IHeartArrayData;
import com.palaneogenesis.network.HeartArraySyncPacket;
import com.palaneogenesis.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * Acceso estático al array unificado de corazones (ver capability.IHeartArrayData), mismo rol que
 * {@link Transformation} tiene para el flag de transformación: el resto del mod no debería llamar
 * a {@code player.getCapability(...)} directamente para esto, sino pasar por acá.
 *
 * A diferencia de {@link Transformation#isTransformed} (que sólo se usa server-side, ver
 * event.TransformationEvents), este array SÍ necesita llegar al cliente para el HUD
 * (client.HeartHudOverlay). El viejo diseño con 4 atributos (*HeartPool, ver ModAttributes ya
 * eliminado) se sincronizaba gratis porque Forge sincroniza atributos de entidad
 * automáticamente; una capability no tiene ese beneficio, así que cada método que MUTA el array
 * acá dispara un HeartArraySyncPacket server -> dueño (ver #sync). Los métodos de sólo lectura
 * (totalPointsOfType/isEmpty) no sincronizan nada - son válidos en ambos lados, cada uno leyendo
 * el estado que ya tiene.
 */
public final class HeartArray {

	private HeartArray() {
	}

	public static void addPoints(Player player, HeartType type, int points) {
		player.getCapability(Capabilities.HEART_ARRAY_DATA).ifPresent(data -> {
			data.addPoints(type, points);
			sync(player);
		});
	}

	/** Consume daño del array (más viejo primero, sin importar tipo) y devuelve qué tipos se
	 * rompieron en este golpe, en el orden en que se rompieron - el llamador (event.HeartEvents)
	 * es quien decide qué efecto de juego dispara cada tipo. Sincroniza al cliente si hubo
	 * cambios. Si el jugador no tiene la capability (no debería pasar nunca), devuelve el daño
	 * intacto sin nada roto. */
	public static IHeartArrayData.HeartAbsorbResult absorbDamage(Player player, float amount) {
		IHeartArrayData.HeartAbsorbResult[] result = new IHeartArrayData.HeartAbsorbResult[1];
		player.getCapability(Capabilities.HEART_ARRAY_DATA).ifPresent(data -> {
			result[0] = data.absorbDamage(amount);
			sync(player);
		});
		return result[0] != null ? result[0] : new IHeartArrayData.HeartAbsorbResult(amount, List.of());
	}

	public static int totalPointsOfType(Player player, HeartType type) {
		return player.getCapability(Capabilities.HEART_ARRAY_DATA)
			.map(data -> data.totalPointsOfType(type))
			.orElse(0);
	}

	/** Ver IHeartArrayData#setPointsOfType: fija el total de un tipo puntual en un valor
	 * absoluto sin tocar los demás tipos - usado por AncientExtractSyringeItem#transform y
	 * EmptySyringeItem#revert para la Temporary Life (BLUE). */
	public static void setPointsOfType(Player player, HeartType type, int points) {
		player.getCapability(Capabilities.HEART_ARRAY_DATA).ifPresent(data -> {
			data.setPointsOfType(type, points);
			sync(player);
		});
	}

	public static boolean isEmpty(Player player) {
		return player.getCapability(Capabilities.HEART_ARRAY_DATA)
			.map(IHeartArrayData::isEmpty)
			.orElse(true);
	}

	public static void clear(Player player) {
		player.getCapability(Capabilities.HEART_ARRAY_DATA).ifPresent(data -> {
			data.clear();
			sync(player);
		});
	}

	/** Empuja el estado actual del array al dueño únicamente (no a otros jugadores que lo estén
	 * trackeando) - el HUD sólo necesita ver el propio array del jugador local, igual que antes
	 * con los atributos. No-op si {@code player} no es un ServerPlayer real (ej. si algo lo llama
	 * por error del lado cliente). */
	public static void sync(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		player.getCapability(Capabilities.HEART_ARRAY_DATA).ifPresent(data ->
			NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
				new HeartArraySyncPacket(data.snapshot()))
		);
	}
}
