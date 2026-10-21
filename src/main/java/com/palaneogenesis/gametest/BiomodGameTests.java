package com.palaneogenesis.gametest;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityState;
import com.palaneogenesis.biomod.ability.AbilityTimers;
import com.palaneogenesis.biomod.ability.CooldownManager;
import com.palaneogenesis.biomod.module.BiomodModule;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.biomod.module.ModuleCountsCache;
import com.palaneogenesis.biomod.target.TargetFilter;
import com.palaneogenesis.biomod.target.TargetList;
import com.palaneogenesis.biomod.target.TargetRules;
import com.palaneogenesis.capability.BiomodDataProvider;
import com.palaneogenesis.capability.Capabilities;
import com.palaneogenesis.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * GameTests de la Fase 2 (T2.8): ModuleCounts (con su caché), CooldownManager (con AbilityTimers y
 * la persistencia NBT de la capability) y TargetRules (las 7 pruebas de aceptación del documento).
 * Se corren con: gradlew runGameTestServer.
 *
 * Todos usan la estructura vacía data/palaneogenesis/structures/empty.nbt. Lo que depende de Curios
 * (ModuleScanner, canEquip) NO está acá: necesita un jugador real con el slot cargado y se prueba
 * a mano (ver commit de T2.1).
 */
@GameTestHolder(Palaneogenesis.MOD_ID)
@PrefixGameTestTemplate(false)
public class BiomodGameTests {

	private static final String TEMPLATE = "empty";

	// ---- ModuleCounts ----------------------------------------------------------------------

	@GameTest(template = TEMPLATE)
	public static void moduleCountsCuentaYRecorta(GameTestHelper helper) {
		ModuleCounts counts = ModuleCounts.fromModules(
			List.of(BiomodModule.PISTON, BiomodModule.PISTON, BiomodModule.MEGA_JUMP));
		helper.assertTrue(counts.get(BiomodModule.PISTON) == 2, "2 pistones");
		helper.assertTrue(counts.get(BiomodModule.MEGA_JUMP) == 1, "1 mega jump");
		helper.assertTrue(!counts.has(BiomodModule.DRONES), "0 drones");
		helper.assertTrue(counts.total() == 3, "total 3");

		ModuleCounts demasiadas = ModuleCounts.fromModules(
			List.of(BiomodModule.CHARGE, BiomodModule.CHARGE, BiomodModule.CHARGE));
		helper.assertTrue(demasiadas.get(BiomodModule.CHARGE) == 1, "Embestida se recorta a su máximo de 1");
		helper.assertTrue(counts.with(BiomodModule.DRONES, 9).get(BiomodModule.DRONES) == 5, "drones se recorta a 5");
		helper.assertTrue(counts.get(BiomodModule.PISTON) == 2, "ModuleCounts es inmutable");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void moduleCountsCacheEscaneaUnaVezHastaInvalidar(GameTestHelper helper) {
		ModuleCountsCache cache = new ModuleCountsCache();
		UUID jugador = UUID.randomUUID();
		int[] escaneos = {0};
		ModuleCounts real = ModuleCounts.EMPTY.with(BiomodModule.PISTON, 2);

		cache.get(jugador, () -> {
			escaneos[0]++;
			return real;
		});
		cache.get(jugador, () -> {
			escaneos[0]++;
			return real;
		});
		helper.assertTrue(escaneos[0] == 1, "la segunda lectura sale de la caché");

		cache.invalidate(jugador);
		cache.get(jugador, () -> {
			escaneos[0]++;
			return real;
		});
		helper.assertTrue(escaneos[0] == 2, "tras invalidar se vuelve a escanear");

		cache.setOverride(jugador, ModuleCounts.EMPTY.with(BiomodModule.PISTON, 3));
		helper.assertTrue(cache.get(jugador, () -> real).get(BiomodModule.PISTON) == 3, "el override gana");
		cache.clearOverride(jugador);
		helper.assertTrue(cache.get(jugador, () -> real).get(BiomodModule.PISTON) == 2, "sin override vuelve el real");
		helper.succeed();
	}

	// ---- Cooldowns -------------------------------------------------------------------------

	@GameTest(template = TEMPLATE)
	public static void cooldownEnfriaYVuelveALista(GameTestHelper helper) {
		AbilityTimers timers = new AbilityTimers();
		timers.startCooldown(AbilityId.PISTON, 3);
		helper.assertTrue(timers.state(AbilityId.PISTON) == AbilityState.COOLDOWN, "enfriando");
		timers.tick();
		timers.tick();
		helper.assertTrue(timers.remaining(AbilityId.PISTON) == 1, "falta 1 tick");
		timers.tick();
		helper.assertTrue(timers.isReady(AbilityId.PISTON), "vuelve a LISTA a los 3 ticks");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void embestidaEmpiezaElCooldownAlTerminarOCancelar(GameTestHelper helper) {
		// Termina por tiempo: activa 5 ticks, después 4 de cooldown (P-7).
		AbilityTimers timers = new AbilityTimers();
		timers.startActive(AbilityId.CHARGE, 5, 4);
		int mascara = 0;
		for (int i = 0; i < 5; i++) {
			mascara |= timers.tick();
		}
		helper.assertTrue(mascara == (1 << AbilityId.CHARGE.ordinal()), "avisa que la ACTIVA venció");
		helper.assertTrue(timers.state(AbilityId.CHARGE) == AbilityState.COOLDOWN
			&& timers.remaining(AbilityId.CHARGE) == 4, "el cooldown arranca al terminar");

		// Cancelada a mitad: el cooldown cuenta desde la cancelación.
		AbilityTimers cancelada = new AbilityTimers();
		cancelada.startActive(AbilityId.CHARGE, 100, 7);
		for (int i = 0; i < 10; i++) {
			cancelada.tick();
		}
		helper.assertTrue(cancelada.finishActive(AbilityId.CHARGE), "cancelar devuelve true");
		helper.assertTrue(cancelada.remaining(AbilityId.CHARGE) == 7, "cooldown de 7 desde la cancelación");

		// Sin duración (Mega Jump): no vence sola, termina al aterrizar.
		AbilityTimers salto = new AbilityTimers();
		salto.startActive(AbilityId.MEGA_JUMP, 0, 600);
		for (int i = 0; i < 2000; i++) {
			salto.tick();
		}
		helper.assertTrue(salto.isActive(AbilityId.MEGA_JUMP), "sigue activa hasta aterrizar");
		salto.finishActive(AbilityId.MEGA_JUMP);
		helper.assertTrue(salto.remaining(AbilityId.MEGA_JUMP) == 600, "cooldown de 600 al aterrizar");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void cooldownSobreviveARelogYSeReiniciaAlMorir(GameTestHelper helper) {
		AbilityTimers original = new AbilityTimers();
		original.startCooldown(AbilityId.PISTON, 80);
		original.startActive(AbilityId.CHARGE, 1200, 400);

		// Relog: lo guardado, cargado en unos timers nuevos (P-22).
		AbilityTimers relog = new AbilityTimers();
		relog.fromPersistent(original.toPersistent());
		helper.assertTrue(relog.remaining(AbilityId.PISTON) == 80, "el cooldown sigue tras el relog");
		helper.assertTrue(relog.state(AbilityId.CHARGE) == AbilityState.COOLDOWN
			&& relog.remaining(AbilityId.CHARGE) == 400, "relog en plena Embestida: no se salta el cooldown");

		// Muerte: todo a 0, incluido un Overdrive de 180 s (P-20).
		original.startCooldown(AbilityId.PICKAXE, 3600);
		original.reset();
		helper.assertTrue(!original.anyRunning(), "morir deja todas las habilidades en LISTA");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void cooldownManagerLeeLaCapabilityDelJugador(GameTestHelper helper) {
		Player jugador = helper.makeMockPlayer();
		jugador.getCapability(Capabilities.BIOMOD_DATA).ifPresent(d -> d.getTimers().startCooldown(AbilityId.PISTON, 100));
		helper.assertTrue(CooldownManager.state(jugador, AbilityId.PISTON) == AbilityState.COOLDOWN, "CooldownManager ve el estado");
		helper.assertTrue(CooldownManager.remainingTicks(jugador, AbilityId.PISTON) == 100, "y los ticks");
		helper.assertTrue(!CooldownManager.isReady(jugador, AbilityId.PISTON), "no está lista");
		helper.assertTrue(CooldownManager.isReady(jugador, AbilityId.PICKAXE), "otra habilidad sigue lista");
		helper.succeed();
	}

	// ---- Persistencia NBT de la capability ---------------------------------------------------

	@GameTest(template = TEMPLATE)
	public static void capabilityGuardaYCargaInyectorCooldownsYLista(GameTestHelper helper) {
		BiomodDataProvider guardado = new BiomodDataProvider();
		guardado.getCapability(Capabilities.BIOMOD_DATA, null).ifPresent(d -> {
			d.setInjected(true);
			d.getTimers().startCooldown(AbilityId.MEGA_JUMP, 250);
			d.getTargets().add("minecraft:zombie");
			d.getTargets().remove(TargetList.DEFAULT_ENTRY);
		});

		BiomodDataProvider cargado = new BiomodDataProvider();
		cargado.deserializeNBT(guardado.serializeNBT());
		cargado.getCapability(Capabilities.BIOMOD_DATA, null).ifPresent(d -> {
			helper.assertTrue(d.isInjected(), "el Inyector se conserva");
			helper.assertTrue(d.getTimers().remaining(AbilityId.MEGA_JUMP) == 250, "el cooldown se conserva");
			helper.assertTrue(d.getTargets().isProtected("minecraft:zombie"), "el zombi sigue en la lista");
			helper.assertTrue(!d.getTargets().isProtected("minecraft:villager"),
				"un aldeano quitado de la lista NO vuelve al recargar");
			helper.assertTrue(!d.isSessionOpen(), "la sesión de modificación no se guarda");
		});

		// Datos de antes de T2.7 (sin la clave de objetivos): queda la lista por defecto.
		var viejo = guardado.serializeNBT();
		viejo.remove("TargetsProtected");
		BiomodDataProvider migrado = new BiomodDataProvider();
		migrado.deserializeNBT(viejo);
		migrado.getCapability(Capabilities.BIOMOD_DATA, null).ifPresent(d ->
			helper.assertTrue(d.getTargets().isProtected("minecraft:villager"), "sin la clave: lista por defecto con el aldeano"));
		helper.succeed();
	}

	// ---- TargetRules: pruebas de aceptación del documento (Sección T2.7) ----------------------

	@GameTest(template = TEMPLATE)
	public static void targetRulesZombiSiAldeanoNo(GameTestHelper helper) {
		Player jugador = helper.makeMockPlayer();
		Zombie zombi = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		Villager aldeano = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 2));
		boolean aldeanoForzado = Config.COMMON.biomod.targetsForcedProtected.get().contains("minecraft:villager");

		helper.assertTrue(TargetRules.evaluate(jugador, zombi) == TargetFilter.Verdict.VALID, "1: el zombi es objetivo");
		helper.assertTrue(!TargetRules.isValidTarget(jugador, aldeano), "2: el aldeano no es objetivo");
		helper.assertTrue(TargetRules.evaluate(jugador, aldeano) == (aldeanoForzado
			? TargetFilter.Verdict.SERVER_PROTECTED : TargetFilter.Verdict.PLAYER_PROTECTED), "2: protegido por la config o por la lista");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void targetRulesMascotasNuncaSeAfectan(GameTestHelper helper) {
		Player duenio = helper.makeMockPlayer();
		Player otro = helper.makeMockPlayer();
		Wolf propio = helper.spawn(EntityType.WOLF, new BlockPos(1, 1, 1));
		propio.tame(duenio);
		Wolf ajeno = helper.spawn(EntityType.WOLF, new BlockPos(2, 1, 1));
		ajeno.tame(otro);

		helper.assertTrue(TargetRules.evaluate(duenio, propio) == TargetFilter.Verdict.PET, "3: lobo domado propio");
		helper.assertTrue(TargetRules.evaluate(duenio, ajeno) == TargetFilter.Verdict.PET, "4: lobo domado de otro jugador");
		// Aun sin ninguna lista, una mascota sigue protegida.
		duenio.getCapability(Capabilities.BIOMOD_DATA).ifPresent(d -> d.getTargets().replaceWith(List.of()));
		helper.assertTrue(TargetRules.evaluate(duenio, propio) == TargetFilter.Verdict.PET, "la lista vacía no desprotege mascotas");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void targetRulesListaPersonal(GameTestHelper helper) {
		Player jugador = helper.makeMockPlayer();
		Zombie zombi = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		Villager aldeano = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 2));
		boolean aldeanoForzado = Config.COMMON.biomod.targetsForcedProtected.get().contains("minecraft:villager");

		jugador.getCapability(Capabilities.BIOMOD_DATA).ifPresent(d -> d.getTargets().add("minecraft:zombie"));
		helper.assertTrue(TargetRules.evaluate(jugador, zombi) == TargetFilter.Verdict.PLAYER_PROTECTED, "5: zombi en la lista personal");

		jugador.getCapability(Capabilities.BIOMOD_DATA).ifPresent(d -> d.getTargets().remove("minecraft:villager"));
		// 6: quitado de la lista personal => sí, salvo que el servidor lo tenga forzado.
		helper.assertTrue(TargetRules.isValidTarget(jugador, aldeano) == !aldeanoForzado,
			"6: aldeano quitado de la lista: objetivo solo si el servidor no lo fuerza");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void targetRulesOtrosJugadoresYUnoMismo(GameTestHelper helper) {
		Player jugador = helper.makeMockPlayer();
		Player otro = helper.makeMockPlayer();
		helper.assertTrue(TargetRules.evaluate(jugador, jugador) == TargetFilter.Verdict.SELF, "nunca a uno mismo");
		if (!Config.COMMON.biomod.targetsAffectPlayers.get()) {
			helper.assertTrue(TargetRules.evaluate(jugador, otro) == TargetFilter.Verdict.PLAYER_NOT_ALLOWED,
				"7: otro jugador con affectPlayers = false");
		}
		helper.succeed();
	}

	/** La lista forzada del servidor gana sobre la personal y no se puede desproteger (sin Minecraft). */
	@GameTest(template = TEMPLATE)
	public static void targetFilterListaForzadaGanaSobreLaPersonal(GameTestHelper helper) {
		TargetList vacia = TargetList.empty();
		helper.assertTrue(TargetFilter.evaluate(false, false, false, "minecraft:villager",
			List.of("minecraft:villager"), vacia, false) == TargetFilter.Verdict.SERVER_PROTECTED, "forzado, lista personal vacía");
		helper.assertTrue(TargetFilter.evaluate(false, false, false, "minecraft:villager",
			List.of(), vacia, false) == TargetFilter.Verdict.VALID, "6: sin forzado y sin lista, es objetivo");
		helper.succeed();
	}
}
