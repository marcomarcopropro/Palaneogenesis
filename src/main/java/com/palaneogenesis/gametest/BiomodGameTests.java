package com.palaneogenesis.gametest;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityState;
import com.palaneogenesis.biomod.ability.AbilityTimers;
import com.palaneogenesis.biomod.ability.CooldownManager;
import com.palaneogenesis.biomod.ability.charge.ChargeAbility;
import com.palaneogenesis.biomod.ability.charge.ChargeEffect;
import com.palaneogenesis.biomod.ability.charge.ChargeSession;
import com.palaneogenesis.biomod.ability.charge.ChargeSettings;
import com.palaneogenesis.biomod.ability.drones.DroneLoadout;
import com.palaneogenesis.biomod.ability.drones.DroneSettings;
import com.palaneogenesis.biomod.ability.drones.DroneTargetMemory;
import com.palaneogenesis.biomod.ability.megajump.CeilingJob;
import com.palaneogenesis.biomod.ability.megajump.FlightPhase;
import com.palaneogenesis.biomod.ability.megajump.MegaJumpEffect;
import com.palaneogenesis.biomod.ability.megajump.MegaJumpSettings;
import com.palaneogenesis.biomod.ability.pickaxe.BlockJob;
import com.palaneogenesis.biomod.ability.pickaxe.PickaxeVolume;
import com.palaneogenesis.biomod.ability.piston.PistonEffect;
import com.palaneogenesis.biomod.ability.piston.PistonSettings;
import com.palaneogenesis.biomod.ability.piston.PistonStrike;
import com.palaneogenesis.biomod.module.BiomodModule;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.biomod.module.ModuleCountsCache;
import com.palaneogenesis.biomod.module.PendingResync;
import com.palaneogenesis.biomod.target.TargetFilter;
import com.palaneogenesis.biomod.target.TargetList;
import com.palaneogenesis.biomod.target.TargetRules;
import com.palaneogenesis.capability.BiomodDataProvider;
import com.palaneogenesis.capability.Capabilities;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.network.DroneAppleSyncPacket;
import com.palaneogenesis.util.LevitationState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

	@GameTest(template = TEMPLATE)
	public static void pendingResyncSeConsumeUnaSolaVez(GameTestHelper helper) {
		PendingResync pendiente = new PendingResync();
		UUID jugador = UUID.randomUUID();
		UUID otro = UUID.randomUUID();

		helper.assertTrue(!pendiente.consume(jugador), "sin marcar no hay resync");
		pendiente.mark(jugador);
		pendiente.mark(jugador);
		helper.assertTrue(!pendiente.consume(otro), "otro jugador no se ve afectado");
		helper.assertTrue(pendiente.consume(jugador), "marcado: se consume");
		helper.assertTrue(!pendiente.consume(jugador), "marcar dos veces no da dos resync");

		pendiente.mark(jugador);
		pendiente.forget(jugador);
		helper.assertTrue(!pendiente.consume(jugador), "forget: se desconectó antes del primer tick");
		pendiente.mark(jugador);
		pendiente.clear();
		helper.assertTrue(!pendiente.consume(jugador), "clear: se apagó el servidor");
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

	// ---- Pistón de Presión: tiers (T3.1, sin Minecraft) ---------------------------------------

	@GameTest(template = TEMPLATE)
	public static void pistonEffectNivelesPorUnidades(GameTestHelper helper) {
		PistonSettings ajustes = PistonSettings.DEFAULTS;
		PistonEffect ninguno = PistonEffect.forUnits(0, ajustes);
		helper.assertTrue(ninguno == PistonEffect.NONE && !ninguno.launches() && !ninguno.slows(), "0 unidades: no hace nada");

		PistonEffect uno = PistonEffect.forUnits(1, ajustes);
		helper.assertTrue(uno.knockbackLevel() == 2, "1 unidad: Empuje de la config");
		helper.assertTrue(!uno.launches() && !uno.slows() && !uno.stuns() && !uno.isFist(), "1 unidad: solo Empuje");
		helper.assertTrue(uno.damage(1.0D) == 1.0D, "1 unidad: daño base sin cambios");

		PistonEffect dos = PistonEffect.forUnits(2, ajustes);
		helper.assertTrue(dos.launches() && dos.launchDistance() == 5.0D, "2 unidades: lanza 5 bloques");
		helper.assertTrue(dos.slows() && dos.slownessTicks() == 200 && dos.slownessAmplifier() == 0, "2 unidades: Lentitud I 10 s");
		helper.assertTrue(dos.damage(1.0D) == 7.0D, "2 unidades: base + 6 del brazo");
		helper.assertTrue(!dos.isFist() && !dos.stuns(), "2 unidades: todavía sin mano ni parálisis");

		PistonEffect tres = PistonEffect.forUnits(3, ajustes);
		helper.assertTrue(tres.isFist() && tres.stuns() && tres.stunTicks() == 15, "3 unidades: mano con parálisis de 15 ticks");
		helper.assertTrue(tres.damage(1.0D) == 7.0D && tres.damage(10.0D) == 7.0D, "3 unidades: daño absoluto de la mano");
		helper.assertTrue(!tres.launches() && tres.knockbackLevel() == 0 && tres.slows(), "3 unidades: sin lanzamiento ni Empuje, conserva la Lentitud");

		helper.assertTrue(PistonEffect.forUnits(9, ajustes).equals(tres), "más unidades que el máximo se recortan a 3");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void pistonLanzamientoRecorreLaDistanciaPedida(GameTestHelper helper) {
		for (double distancia : new double[] {1.0D, 5.0D, 12.5D}) {
			double velocidad = PistonEffect.launchSpeed(distancia);
			double recorrido = 0.0D;
			for (int tick = 0; tick < PistonEffect.LAUNCH_AIR_TICKS; tick++) {
				recorrido += velocidad;
				velocidad *= PistonEffect.AIR_DRAG;
			}
			helper.assertTrue(Math.abs(recorrido - distancia) < 1.0E-9D, "recorre " + distancia + " bloques (dio " + recorrido + ")");
		}
		helper.assertTrue(PistonEffect.launchSpeed(0.0D) == 0.0D, "distancia 0: sin velocidad");
		helper.succeed();
	}

	// ---- Pistón de Presión: el golpe del activo (T3.2) ----------------------------------------

	@GameTest(template = TEMPLATE)
	public static void pistonStrikeUnaUnidadSoloEmpuja(GameTestHelper helper) {
		Player jugador = helper.makeMockPlayer();
		Zombie zombi = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		float antes = zombi.getHealth();
		boolean golpe = PistonStrike.apply(jugador, zombi, PistonEffect.forUnits(1, PistonSettings.DEFAULTS), 1.0D);
		helper.assertTrue(golpe, "el golpe conecta");
		helper.assertTrue(zombi.getHealth() < antes, "el zombi pierde vida");
		helper.assertTrue(!zombi.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "1 unidad: sin Lentitud");
		helper.assertTrue(!PistonStrike.isStriking(), "la marca de golpe en curso se apaga al terminar");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void pistonStrikeDosUnidadesLanzaYRalentiza(GameTestHelper helper) {
		Player jugador = helper.makeMockPlayer();
		Zombie zombi = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		float antes = zombi.getHealth();
		boolean golpe = PistonStrike.apply(jugador, zombi, PistonEffect.forUnits(2, PistonSettings.DEFAULTS), 1.0D);
		helper.assertTrue(golpe, "el golpe conecta");
		helper.assertTrue(antes - zombi.getHealth() >= 6.0F, "2 unidades: base + 6 del brazo");
		helper.assertTrue(zombi.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "2 unidades: Lentitud");
		helper.assertTrue(zombi.getDeltaMovement().y > 0.0D, "queda lanzado hacia arriba");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void pistonStrikeTresUnidadesParaliza(GameTestHelper helper) {
		Player jugador = helper.makeMockPlayer();
		Zombie zombi = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		boolean golpe = PistonStrike.apply(jugador, zombi, PistonEffect.forUnits(3, PistonSettings.DEFAULTS), 1.0D);
		helper.assertTrue(golpe, "el golpe conecta");
		var efecto = zombi.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(efecto != null && efecto.getAmplifier() == PistonStrike.STUN_AMPLIFIER,
			"3 unidades: la Lentitud de parálisis es la más fuerte");
		helper.assertTrue(zombi.getDeltaMovement().lengthSqr() == 0.0D, "3 unidades: el objetivo queda quieto, sin empuje ni lanzamiento");
		helper.succeed();
	}

	// ---- Pico Gigante: volumen y cola de bloques (T3.4, sin Minecraft) ------------------------

	private static String clave(int[] posicion) {
		return posicion[0] + "," + posicion[1] + "," + posicion[2];
	}

	@GameTest(template = TEMPLATE)
	public static void pickaxeVolumeEsUnPrismaSinRepetidos(GameTestHelper helper) {
		int[] golpe = {10, 64, -5};
		int[][] direcciones = {{0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
		for (int[] d : direcciones) {
			List<int[]> bloques = PickaxeVolume.plan(golpe, d[0], d[1], d[2], PickaxeVolume.WIDTH, PickaxeVolume.DEPTH);
			helper.assertTrue(bloques.size() == 27, "3x3x3 = 27 bloques");
			Set<String> distintos = new HashSet<>();
			for (int[] b : bloques) {
				distintos.add(clave(b));
				int profundidad = (b[0] - golpe[0]) * d[0] + (b[1] - golpe[1]) * d[1] + (b[2] - golpe[2]) * d[2];
				helper.assertTrue(profundidad >= 0 && profundidad < PickaxeVolume.DEPTH, "dentro de la profundidad");
			}
			helper.assertTrue(distintos.size() == 27, "sin bloques repetidos");
			helper.assertTrue(distintos.contains(clave(golpe)), "incluye el bloque que se mira");
		}
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void pickaxeVolumeVaDeLoCercanoALoLejano(GameTestHelper helper) {
		int[] golpe = {0, 0, 0};
		List<int[]> bloques = PickaxeVolume.plan(golpe, 0, 0, 1, 3, 3);
		int anterior = 0;
		for (int[] b : bloques) {
			helper.assertTrue(b[2] >= anterior, "la profundidad nunca retrocede");
			anterior = b[2];
		}
		helper.assertTrue(bloques.get(0)[2] == 0 && bloques.get(26)[2] == 2, "de la capa 0 a la capa 2");
		// Una sección de 1 bloque y profundidad 4 es un túnel de un solo bloque.
		helper.assertTrue(PickaxeVolume.plan(golpe, 0, -1, 0, 1, 4).size() == 4, "1x1x4 = 4 bloques");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void pickaxeVolumeRechazaDireccionesInvalidas(GameTestHelper helper) {
		int[] golpe = {0, 0, 0};
		int[][] invalidas = {{0, 0, 0}, {1, 1, 0}, {2, 0, 0}};
		for (int[] d : invalidas) {
			boolean fallo = false;
			try {
				PickaxeVolume.plan(golpe, d[0], d[1], d[2], 3, 3);
			} catch (IllegalArgumentException e) {
				fallo = true;
			}
			helper.assertTrue(fallo, "dirección inválida rechazada");
		}
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void blockJobRespetaElPresupuestoPorTick(GameTestHelper helper) {
		List<int[]> bloques = PickaxeVolume.plan(new int[] {0, 0, 0}, 0, 0, 1, 3, 3);
		BlockJob trabajo = new BlockJob(bloques);
		int[] ticks = {0};
		int rotos = 0;
		while (!trabajo.isDone()) {
			int ahora = trabajo.run(10, b -> true);
			helper.assertTrue(ahora <= 10, "nunca más del presupuesto por tick");
			rotos += ahora;
			ticks[0]++;
		}
		helper.assertTrue(rotos == 27 && ticks[0] == 3, "27 bloques con presupuesto 10 = 3 ticks (10 + 10 + 7)");

		BlockJob entero = new BlockJob(bloques);
		helper.assertTrue(entero.run(27, b -> true) == 27 && entero.isDone(), "presupuesto 27: todo en un tick");

		// Los rechazados no gastan presupuesto: con 1 de cada 2 rechazado y presupuesto 5, se rompen 5.
		BlockJob mitad = new BlockJob(bloques);
		int[] llamada = {0};
		int hechos = mitad.run(5, b -> llamada[0]++ % 2 == 0);
		helper.assertTrue(hechos == 5, "los bloques rechazados no cuentan contra el presupuesto");
		helper.assertTrue(mitad.remaining() == 27 - 9, "se procesaron 9 posiciones para romper 5");
		helper.succeed();
	}

	// ---- Mega Jump: niveles, altura y hueco del techo (F4, sin Minecraft) ---------------------

	@GameTest(template = TEMPLATE)
	public static void megaJumpEffectNivelesPorUnidades(GameTestHelper helper) {
		MegaJumpSettings ajustes = MegaJumpSettings.DEFAULTS;
		helper.assertTrue(MegaJumpEffect.forUnits(0, ajustes) == MegaJumpEffect.NONE, "0 unidades: no hace nada");

		MegaJumpEffect uno = MegaJumpEffect.forUnits(1, ajustes);
		helper.assertTrue(uno.cooldownTicks() == 600, "1 unidad: cooldown de 30 s");
		helper.assertTrue(uno.impactDiameter() == 10.0D && uno.impactDamage() == 2.0D, "1 unidad: impacto de 10 bloques y 1 corazón");

		MegaJumpEffect dos = MegaJumpEffect.forUnits(2, ajustes);
		helper.assertTrue(dos.cooldownTicks() == 300, "2 unidades: cooldown de 15 s");
		helper.assertTrue(dos.impactDiameter() == 10.0D && dos.impactDamage() == 2.0D, "2 unidades: mismo impacto que con 1");

		MegaJumpEffect tres = MegaJumpEffect.forUnits(3, ajustes);
		helper.assertTrue(tres.cooldownTicks() == 300, "3 unidades: cooldown de 15 s");
		helper.assertTrue(tres.impactDiameter() == 25.0D && tres.impactDamage() == 6.0D, "3 unidades: impacto de 25 bloques y 3 corazones");
		helper.assertTrue(tres.ceilingWidth() == 3 && tres.maxCeilingBlocks() == 800, "hueco 3x3 con tope de 800 bloques");
		helper.assertTrue(MegaJumpEffect.forUnits(9, ajustes).equals(tres), "más unidades que el máximo se recortan a 3");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void megaJumpLanzamientoAlcanzaLaAltura(GameTestHelper helper) {
		helper.assertTrue(Math.abs(MegaJumpEffect.apex(0.42D) - 1.25D) < 0.01D, "un salto vanilla (0.42) llega a 1.25 bloques");
		for (double altura : new double[] {1.25D, 10.0D, 80.0D, 250.0D}) {
			double v0 = MegaJumpEffect.launchVelocity(altura);
			helper.assertTrue(Math.abs(MegaJumpEffect.apex(v0) - altura) < 0.01D, "el salto alcanza la altura pedida: " + altura);
		}
		helper.assertTrue(MegaJumpEffect.launchVelocity(0.0D) == 0.0D, "altura 0: sin velocidad");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void flightPhaseSoloAterrizaDespuesDeEstarEnElAire(GameTestHelper helper) {
		// El servidor ve "en el suelo" los primeros ticks (el cliente informa atrasado): eso NO es aterrizar.
		FlightPhase atrasado = new FlightPhase();
		boolean[] suelo = {true, true, false, false, false, true};
		FlightPhase.Result[] esperado = {FlightPhase.Result.FLYING, FlightPhase.Result.FLYING, FlightPhase.Result.FLYING,
			FlightPhase.Result.FLYING, FlightPhase.Result.FLYING, FlightPhase.Result.LANDED};
		for (int i = 0; i < suelo.length; i++) {
			helper.assertTrue(atrasado.tick(suelo[i]) == esperado[i], "tick " + (i + 1) + ": resultado esperado con el cliente atrasado");
		}
		helper.assertTrue(atrasado.hasBeenAirborne() && atrasado.ticks() == suelo.length, "cuenta ticks y recuerda que estuvo en el aire");

		// Sin atraso: en el aire desde el primer tick, y vuelve al suelo en el segundo (techo bajo): sí es un aterrizaje.
		FlightPhase rapido = new FlightPhase();
		helper.assertTrue(rapido.tick(false) == FlightPhase.Result.FLYING, "primer tick en el aire: sigue");
		helper.assertTrue(rapido.tick(true) == FlightPhase.Result.LANDED, "suelo tras haber estado en el aire: aterrizó");

		// Despega tarde pero a tiempo (en el último tick de espera): no se abandona.
		FlightPhase tarde = new FlightPhase();
		for (int i = 1; i < FlightPhase.AIRBORNE_TIMEOUT_TICKS; i++) {
			helper.assertTrue(tarde.tick(true) == FlightPhase.Result.FLYING, "esperando el despegue: tick " + i);
		}
		helper.assertTrue(tarde.tick(false) == FlightPhase.Result.FLYING, "despega justo en el último tick de espera");
		helper.assertTrue(tarde.tick(true) == FlightPhase.Result.LANDED, "y después aterriza normalmente");

		// Nunca despega (techo pegado, etc.): se abandona pasado el tiempo de espera, sin impacto.
		FlightPhase quieto = new FlightPhase();
		FlightPhase.Result ultimo = FlightPhase.Result.FLYING;
		for (int i = 0; i < FlightPhase.AIRBORNE_TIMEOUT_TICKS; i++) {
			ultimo = quieto.tick(true);
		}
		helper.assertTrue(ultimo == FlightPhase.Result.FLYING, "dentro del tiempo de espera todavía no se abandona");
		helper.assertTrue(quieto.tick(true) == FlightPhase.Result.ABORTED, "pasado el tiempo de espera sin despegar: abandonado");

		// Seguro de duración máxima: un vuelo eterno se cierra solo.
		FlightPhase eterno = new FlightPhase();
		FlightPhase.Result fin = FlightPhase.Result.FLYING;
		for (int i = 0; i <= FlightPhase.MAX_FLIGHT_TICKS; i++) {
			fin = eterno.tick(false);
		}
		helper.assertTrue(fin == FlightPhase.Result.ABORTED, "un vuelo de más de 1 minuto se cierra solo");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void flightPhaseElEspacioEnElAireSalvaElAterrizaje(GameTestHelper helper) {
		// Espacio apretado en el aire: el salto queda salvado y se mantiene hasta aterrizar.
		FlightPhase conEspacio = new FlightPhase();
		helper.assertTrue(!conEspacio.isRescued(), "al empezar no está salvado");
		conEspacio.tick(true);
		conEspacio.tick(false);
		helper.assertTrue(conEspacio.rescue() && conEspacio.isRescued(), "espacio en el aire: queda salvado");
		conEspacio.tick(false);
		helper.assertTrue(conEspacio.isRescued(), "sigue salvado mientras vuela");
		helper.assertTrue(conEspacio.tick(true) == FlightPhase.Result.LANDED && conEspacio.isRescued(),
			"aterriza salvado: el aterrizaje no hace daño de caída");

		// Sin espacio: nunca queda salvado, el aterrizaje hace su daño normal.
		FlightPhase sinEspacio = new FlightPhase();
		sinEspacio.tick(false);
		sinEspacio.tick(true);
		helper.assertTrue(!sinEspacio.isRescued(), "sin espacio: no está salvado");

		// La pulsación que acompaña al despegue (el servidor todavía ve el suelo) NO cuenta.
		FlightPhase alDespegar = new FlightPhase();
		alDespegar.tick(true);
		helper.assertTrue(!alDespegar.rescue() && !alDespegar.isRescued(), "espacio con el servidor viendo el suelo: no cuenta");
		alDespegar.tick(false);
		helper.assertTrue(!alDespegar.isRescued(), "y no queda anotada para después");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void levitationStateDescartaLaGraciaSiNoEstaTransformado(GameTestHelper helper) {
		UUID id = UUID.randomUUID();

		// Transformado: la altura de referencia y la gracia de caída se conservan.
		LevitationState.getOrStartTracking(id, 64.0D);
		LevitationState.markGrace(id);
		LevitationState.discardUnlessTransformed(id, true);
		helper.assertTrue(LevitationState.isTracking(id), "transformado: sigue la altura de referencia");
		helper.assertTrue(LevitationState.consumeGrace(id), "transformado: la gracia sigue pendiente");

		// Ya sin transformación (murió y reapareció, o revirtió): se descarta todo, la gracia incluida.
		LevitationState.getOrStartTracking(id, 64.0D);
		LevitationState.markGrace(id);
		LevitationState.discardUnlessTransformed(id, false);
		helper.assertTrue(!LevitationState.isTracking(id), "sin transformación: se descarta la altura de referencia");
		helper.assertTrue(!LevitationState.consumeGrace(id), "sin transformación: la gracia pendiente no deja pasar el aterrizaje");

		// Sin nada pendiente no pasa nada.
		LevitationState.discardUnlessTransformed(id, false);
		helper.assertTrue(!LevitationState.isTracking(id) && !LevitationState.consumeGrace(id), "sin estado previo: nada que descartar");
		LevitationState.clear(id);
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void ceilingJobAvanzaPorCapasYRespetaElTope(GameTestHelper helper) {
		// Columna 3x3 de y=10 a y=20, todo rompible: 9 bloques por capa.
		CeilingJob trabajo = new CeilingJob(10, 20, 3, 800);
		int[] maxY = {0};
		int primera = trabajo.advance(11, 5, 5, (x, y, z) -> {
			maxY[0] = Math.max(maxY[0], y);
			return x >= 4 && x <= 6 && z >= 4 && z <= 6;
		});
		helper.assertTrue(primera == 18 && maxY[0] == 11, "hasta la capa 11: 2 capas x 9 bloques, sin pasarse");
		helper.assertTrue(trabajo.advance(11, 5, 5, (x, y, z) -> true) == 0, "sin capas nuevas no rompe nada");
		trabajo.advance(99, 5, 5, (x, y, z) -> true);
		helper.assertTrue(trabajo.isDone() && trabajo.nextY() == 21 && trabajo.broken() == 99, "11 capas x 9 = 99 y se detiene en endY");

		// Tope: con 20 bloques como máximo se rompen exactamente 20.
		CeilingJob tope = new CeilingJob(0, 100, 3, 20);
		helper.assertTrue(tope.advance(100, 0, 0, (x, y, z) -> true) == 20 && tope.isDone(), "el tope corta a los 20 bloques");

		// Lo que el breaker rechaza (aire, protegido) no gasta el tope.
		CeilingJob rechazos = new CeilingJob(0, 100, 3, 10);
		int[] llamada = {0};
		helper.assertTrue(rechazos.advance(100, 0, 0, (x, y, z) -> llamada[0]++ % 2 == 0) == 10, "los rechazados no cuentan contra el tope");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void embestidaElDanoEsElBaseDeCaminarOCorrerPorLaVelocidad(GameTestHelper helper) {
		ChargeSettings config = ChargeSettings.DEFAULTS;
		// Caminando (4.3 bloques/s): daño base 1.0 x velocidad. Corriendo (5.6): daño base 5.0 x velocidad.
		helper.assertTrue(Math.abs(ChargeEffect.damage(false, 4.3D, config) - 4.3D) < 1.0E-9, "caminando: 1.0 x 4.3");
		helper.assertTrue(Math.abs(ChargeEffect.damage(true, 5.6D, config) - 28.0D) < 1.0E-9, "corriendo: 5.0 x 5.6");
		helper.assertTrue(ChargeEffect.damage(true, 0.0D, config) == 0.0D && ChargeEffect.damage(true, -3.0D, config) == 0.0D,
			"sin velocidad (o con velocidad negativa) no hay daño");
		// Solo golpea yendo a una velocidad mínima: caminar normal sí, arrastrarse (agachado, ~1.3) no.
		helper.assertTrue(ChargeEffect.canHit(4.3D) && !ChargeEffect.canHit(1.3D), "velocidad mínima para golpear");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void embestidaSuavizaLaVelocidadYFrenaTrasElImpacto(GameTestHelper helper) {
		// El servidor recibe las posiciones a ráfagas (0, el doble, 0, el doble): el promedio converge a la velocidad real.
		ChargeSession corriendo = new ChargeSession();
		for (int i = 0; i < 40; i++) {
			corriendo.update(i % 2 == 0 ? 0.0D : 0.56D, 0.0D);
		}
		double real = 0.28D * ChargeEffect.TICKS_PER_SECOND;
		helper.assertTrue(Math.abs(corriendo.speedBlocksPerSecond() - real) < 2.0D, "ráfagas de 0 y 0.56: velocidad cerca de 5.6 b/s, fue " + corriendo.speedBlocksPerSecond());
		helper.assertTrue(corriendo.hasDirection() && corriendo.dirX() == 1.0D && corriendo.dirZ() == 0.0D, "recuerda la dirección de la carrera");

		// Quieto no cambia la dirección ya conocida.
		corriendo.update(0.0D, 0.0D);
		helper.assertTrue(corriendo.dirX() == 1.0D, "quieto un tick: la dirección se conserva");

		// Frenada: dura exactamente los ticks pedidos y luego deja golpear.
		ChargeSession frenada = new ChargeSession();
		frenada.brake(3);
		helper.assertTrue(frenada.isBraking(), "frena tras el impacto");
		int ticksFrenando = 0;
		while (frenada.tickBrake()) {
			ticksFrenando++;
		}
		helper.assertTrue(ticksFrenando == 3 && !frenada.isBraking(), "frena exactamente 3 ticks, y después vuelve a poder golpear");

		// Sin frenada pedida, no frena.
		helper.assertTrue(!new ChargeSession().tickBrake(), "sin impacto no hay frenada");

		// Teletransporte: la velocidad medida se descarta.
		corriendo.resetSpeed();
		helper.assertTrue(corriendo.speedBlocksPerSecond() == 0.0D, "resetSpeed deja la velocidad en 0");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void embestidaNoRecibeDanoFisicoPeroSiElResto(GameTestHelper helper) {
		DamageSources fuentes = helper.getLevel().damageSources();
		Zombie zombi = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		// Físico = SOLO cuerpo a cuerpo: el golpe de un mob (el zombi que embestís) y el de un jugador.
		helper.assertTrue(ChargeAbility.isPhysicalDamage(fuentes.mobAttack(zombi)), "el golpe de un zombi es físico");
		// Ningún proyectil lo es: ni el de un mob ni una flecha (con o sin dueño).
		Arrow flecha = new Arrow(helper.getLevel(), 0.0D, 0.0D, 0.0D);
		helper.assertTrue(!ChargeAbility.isPhysicalDamage(fuentes.mobProjectile(zombi, zombi)), "un proyectil de mob NO es físico");
		helper.assertTrue(!ChargeAbility.isPhysicalDamage(fuentes.arrow(flecha, zombi)), "una flecha disparada por un mob NO es física");
		helper.assertTrue(!ChargeAbility.isPhysicalDamage(fuentes.arrow(flecha, null)), "una flecha sin dueño NO es física");
		// Un golpe cuyo agresor directo es un proyectil tampoco es cuerpo a cuerpo (mobs de otros mods).
		DamageSource golpeDeProyectil = new DamageSource(
			helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.MOB_ATTACK), flecha, zombi);
		helper.assertTrue(!ChargeAbility.isPhysicalDamage(golpeDeProyectil),
			"un 'golpe' dado por un proyectil NO es cuerpo a cuerpo");
		// No físico: sigue haciendo daño durante la Embestida.
		helper.assertTrue(!ChargeAbility.isPhysicalDamage(fuentes.magic()), "la magia no es física");
		helper.assertTrue(!ChargeAbility.isPhysicalDamage(fuentes.onFire()) && !ChargeAbility.isPhysicalDamage(fuentes.fall()),
			"el fuego y la caída no son físicos");
		// La velocidad base sube 40 % (Velocidad II) y no es un efecto de poción.
		helper.assertTrue(Math.abs(ChargeEffect.BASE_SPEED_BOOST - 0.4D) < 1.0E-9, "aumento de velocidad base: 40 %");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void dronesCadaCantidadTraeLoSuyo(GameTestHelper helper) {
		DroneLoadout cero = new DroneLoadout(0);
		helper.assertTrue(!cero.hasSupport() && cero.attackDrones() == 0 && !cero.hasEnchantedApple(), "sin drones no hay nada");
		// 1 dron: solo soporte.
		DroneLoadout uno = new DroneLoadout(1);
		helper.assertTrue(uno.hasSupport() && uno.attackDrones() == 0 && !uno.slowsTargets() && !uno.hasEnchantedApple(),
			"1 dron: solo soporte");
		// 2 drones: 2 de ataque y NADA de soporte (no se conserva lo del dron 1).
		DroneLoadout dos = new DroneLoadout(2);
		helper.assertTrue(!dos.hasSupport() && dos.attackDrones() == 2 && !dos.slowsTargets() && !dos.hasEnchantedApple(),
			"2 drones: 2 de ataque, sin soporte");
		// 3 drones: 2 de ataque + 1 de soporte.
		DroneLoadout tres = new DroneLoadout(3);
		helper.assertTrue(tres.hasSupport() && tres.attackDrones() == 2 && !tres.slowsTargets() && !tres.hasEnchantedApple(),
			"3 drones: 2 de ataque y 1 de soporte");
		// 4 drones: 4 de ataque, sin soporte, con Lentitud.
		DroneLoadout cuatro = new DroneLoadout(4);
		helper.assertTrue(!cuatro.hasSupport() && cuatro.attackDrones() == 4 && cuatro.slowsTargets() && !cuatro.hasEnchantedApple(),
			"4 drones: 4 de ataque, sin soporte, flecha con Lentitud");
		// 5 drones: 4 de ataque + la Manzana Dorada Encantada, sin soporte.
		DroneLoadout cinco = new DroneLoadout(5);
		helper.assertTrue(!cinco.hasSupport() && cinco.attackDrones() == 4 && cinco.slowsTargets() && cinco.hasEnchantedApple(),
			"5 drones: 4 de ataque y la Manzana, sin soporte");
		helper.assertTrue(new DroneLoadout(9).units() == 5 && new DroneLoadout(-2).units() == 0, "se recorta entre 0 y 5");
		// Posiciones de la órbita: el soporte primero, después los de ataque, la Manzana al final.
		helper.assertTrue(tres.attackSlot(0) == 1 && tres.attackSlot(1) == 2, "con soporte, el ataque empieza en la posición 1");
		helper.assertTrue(dos.attackSlot(0) == 0 && cinco.attackSlot(3) == 3 && cinco.appleSlot() == 4,
			"sin soporte el ataque empieza en la 0; la Manzana ocupa la última");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void dronesDisparanEscalonados(GameTestHelper helper) {
		// Con 2 drones de ataque y 60 ticks de intervalo: 1, 2, 1, 2... un disparo cada 30 ticks, alternados.
		int primero = -1;
		int anterior = -1;
		int disparos = 0;
		for (long t = 0; t < 240; t++) {
			for (int k = 0; k < 2; k++) {
				if (DroneLoadout.shouldFire(t, k, 2, 60)) {
					helper.assertTrue(k != anterior, "tick " + t + ": no dispara dos veces seguidas el mismo dron");
					anterior = k;
					if (primero < 0) {
						primero = k;
					}
					disparos++;
				}
			}
		}
		helper.assertTrue(disparos == 8, "en 240 ticks salen 8 disparos (uno cada 30), fueron " + disparos);

		// 4 drones: en 60 ticks sale una flecha de cada uno, nunca dos en el mismo tick.
		int[] porDron = new int[4];
		for (long t = 0; t < 60; t++) {
			int enEsteTick = 0;
			for (int k = 0; k < 4; k++) {
				if (DroneLoadout.shouldFire(t, k, 4, 60)) {
					porDron[k]++;
					enEsteTick++;
				}
			}
			helper.assertTrue(enEsteTick <= 1, "tick " + t + ": no disparan dos drones juntos");
		}
		for (int k = 0; k < 4; k++) {
			helper.assertTrue(porDron[k] == 1, "el dron " + k + " dispara exactamente una vez por intervalo");
		}
		helper.assertTrue(!DroneLoadout.shouldFire(0, 0, 0, 60) && !DroneLoadout.shouldFire(0, 4, 4, 60)
			&& !DroneLoadout.shouldFire(0, 0, 4, 0), "sin drones, índice inválido o intervalo 0: no dispara");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void dronesLaMarcaDuraMientrasLoVeanYSeReemplaza(GameTestHelper helper) {
		DroneTargetMemory memoria = new DroneTargetMemory();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		helper.assertTrue(memoria.current(0) == null, "al empezar no hay objetivo");
		memoria.mark(a, 10, 100);
		helper.assertTrue(a.equals(memoria.current(109)), "sigue marcado dentro de la memoria");
		helper.assertTrue(memoria.current(110) == null, "si no lo pueden alcanzar, pasada la memoria se olvida");

		// Mientras lo ven, la marca se renueva cada tick y NO vence aunque pase mucho más que la memoria.
		memoria.mark(a, 1000, 100);
		for (long t = 1000; t < 5000; t++) {
			memoria.keep(t, 100);
			helper.assertTrue(a.equals(memoria.current(t)), "tick " + t + ": a tiro, sigue marcado");
		}
		helper.assertTrue(a.equals(memoria.current(5098)) && memoria.current(5099) == null,
			"al perderlo de vista, vence a los 100 ticks de la última vez que lo vieron (tick 4999)");

		// Golpear a otro reemplaza la marca; keep sin objetivo no hace nada.
		memoria.mark(a, 6000, 100);
		memoria.mark(b, 6050, 100);
		helper.assertTrue(b.equals(memoria.current(6051)), "golpear a otro reemplaza la marca");
		memoria.clear();
		memoria.keep(6060, 100);
		helper.assertTrue(memoria.current(6061) == null, "clear() la borra y keep() no la resucita");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void dronesFlechaConLaMitadDelDanoYLentitudIV(GameTestHelper helper) {
		// Valores por defecto (DroneSettings.DEFAULTS, que repite los de BiomodConfig): Lentitud IV (amplificador 3, no 4) y flecha con la
		// mitad del daño de una de arco normal (base 1.0 en vez de 2.0).
		helper.assertTrue(DroneSettings.DEFAULTS.slownessAmplifier() == 3, "la Lentitud de la flecha es IV (amplificador 3)");
		helper.assertTrue(DroneSettings.DEFAULTS.arrowBaseDamage() == 1.0D, "el daño base de la flecha de los drones es 1.0");
		Arrow normal = new Arrow(helper.getLevel(), 0.0D, 0.0D, 0.0D);
		helper.assertTrue(Math.abs(DroneSettings.DEFAULTS.arrowBaseDamage() - normal.getBaseDamage() / 2.0D) < 1.0E-9,
			"es la mitad del daño base de una flecha normal");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void dronesElPaqueteDeLaManzanaViajaIntacto(GameTestHelper helper) {
		for (int ticks : new int[] {DroneAppleSyncPacket.NONE, 0, 1, 3600, 1728000}) {
			FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
			DroneAppleSyncPacket.encode(new DroneAppleSyncPacket(ticks), buffer);
			helper.assertTrue(DroneAppleSyncPacket.decode(buffer).remainingTicks() == ticks, "el paquete devuelve " + ticks);
		}
		helper.succeed();
	}
}
