# Fase 3 — Pistón de Presión y Pico Gigante: avances

Documento de diseño: Rev. 4. Cada tarea es un commit con su propia documentación (qué hace, archivos,
cómo probarla, qué se verificó y qué no). Esta página es el resumen. Parte de `F2_avances.md`: con F3
las habilidades del Pistón (tecla J) y del Pico (tecla K) dejan de ser un "NO_HANDLER" en el log y
hacen algo. Mega Jump (F4) y Embestida (F5) siguen sin handler.

## Estado de verificación

**Nada de esto se compiló con Gradle ni se ejecutó en el juego.** La sesión donde se escribió no tiene
acceso a `services.gradle.org`. Lo que sí se hizo:

- Comprobar la sintaxis de cada archivo con `javac` (sin las librerías de Minecraft: se ignoraron solo
  los errores de símbolos y paquetes de Minecraft y Forge).
- Probar fuera del juego la lógica pura nueva (`PistonEffect`, `PickaxeVolume`, `BlockJob`) con un
  runner de consola: **403 comprobaciones, 0 fallas** (18 del Pistón y 385 del Pico).

Lo que **no** se probó fuera del juego, porque depende de Minecraft: `AbilityTargeting`,
`PistonStrike`, `PistonAbility`, `PistonEvents`, `PickaxeAbility`, `PickaxeEvents` y los 3 GameTests de
`PistonStrike`. Los GameTests nuevos tampoco corrieron: son lo primero que conviene ejecutar al
compilar.

**Primer paso al compilar:** `gradlew build`, después `gradlew runGameTestServer` (21 GameTests: los 12
de F2 más 9 nuevos). Si algo no compila, probablemente sea un nombre de la API de abajo.

## Tareas

| Tarea | Qué quedó | Cómo verlo |
|---|---|---|
| T3.1 | `PistonSettings` y `PistonEffect`: qué trae cada nivel (1, 2 y 3 unidades) | GameTests (2) |
| T3.2 | Pistón activo (J): golpe a la entidad en la mira, Empuje, lanzamiento, Lentitud, parálisis | Zombi en la mira + J |
| T3.3 | Pistón pasivo: Empuje en los golpes cuerpo a cuerpo | Pegarle a un zombi con 1 Pistón |
| T3.4 | `PickaxeVolume` y `BlockJob`: prisma 3x3x3 y cola con presupuesto por tick | GameTests (4) |
| T3.5 | Pico Gigante activo (K): rompe el prisma hacia adentro del bloque mirado | Pared de piedra + K |

## Cómo probarlo todo rápido (sin equipar módulos)

```
/biomod injected true
/biomod units piston 3        (o 1, o 2)
/biomod units pickaxe 1
```

- **J** con un zombi a menos de 4,5 bloques en la mira. Con 1 unidad lo empuja; con 2 lo lanza ~5
  bloques y lo ralentiza 10 s; con 3 hace 7 de daño y lo deja quieto 0,75 s.
- **K** mirando una pared a menos de 6 bloques: se rompe un cubo 3x3x3 y los bloques sueltan sus drops.
- Sin objetivo (J) o sin bloque (K): no pasa nada y **no se gasta el cooldown**.
- `/biomod target <entidad>` explica por qué una entidad es o no objetivo.
- El HUD (T2.6) muestra la cuenta atrás: 4 s el Pistón, 5 s el Pico.

## Interpretaciones que conviene revisar (la config no dice todo)

`BiomodConfig` trae los números pero no el comportamiento exacto. Lo que sigue es lo que se asumió; el
documento de diseño (Sección 5) manda. Cada punto está concentrado en un solo lugar para cambiarlo
fácil.

1. **Niveles del Pistón** (`PistonEffect#forUnits`). 1 unidad: Empuje. 2: lanzamiento + Lentitud + el
   daño extra del brazo. 3: golpe de mano con daño absoluto y parálisis. Sale de las descripciones
   ("con 2 o más unidades", "con 3 unidades") de las claves de la config.
2. **El activo del Pistón es un golpe instantáneo** al objetivo en la mira, no un toggle ni un
   modificador del próximo golpe (`PistonAbility`).
3. **"Cada golpe de mano"**: se implementó UN golpe por pulsación con `fistDamage` absoluto
   (reemplaza al daño base + brazo). Si la intención son varios golpes por pulsación, se cambia en
   `PistonStrike#apply`.
4. **El lanzamiento reemplaza al Empuje** en el golpe activo (no se suman). La distancia de 5 bloques
   es una aproximación (ver Conocido).
5. **La parálisis** es Lentitud VII durante `fistStunTicks`: deja la velocidad en 0, pero no impide
   saltar ni atacar a un mob.
6. **Alcances**: 4,5 bloques el Pistón y 6 el Pico. No están en la Sección 9: son constantes
   (`PistonAbility.REACH`, `PickaxeAbility.REACH`).
7. **Forma del Pico**: prisma 3x3x3 hacia adentro del bloque mirado. La config solo trae cooldown y
   bloques por tick; el 27 por defecto sugiere 3x3x3. Constantes `PickaxeVolume.WIDTH` y `DEPTH`.
8. **Cómo rompe el Pico**: como un pico de netherita virtual (drops completos, sin durabilidad, sin
   Toque de seda ni Fortuna). La piedra suelta adoquín.
9. **El Pico no rompe bloques con block entity** (cofres, hornos, spawners), ni líquidos, ni
   indestructibles, ni fuera del borde del mundo. Es una restricción de seguridad: si el diseño quiere
   otra cosa, ver `PickaxeAbility#isBreakable`.
10. **Sinergia Pico + Pistón** (`synergy.*` en la config): NO está hecha. Cada habilidad funciona por
    separado. Tampoco el Overdrive.

## Nombres de la API (escritos de memoria; sin compilar)

- Minecraft 1.20.1: `ProjectileUtil.getEntityHitResult(Level, Entity, Vec3, Vec3, AABB, Predicate)`,
  `LivingEntity#knockback(double, double, double)`, `LivingEntity#addEffect(MobEffectInstance, Entity)`,
  `Entity#damageSources()`, `ServerPlayer#swing(InteractionHand, boolean)`.
- Bloques: `Block.dropResources(BlockState, Level, BlockPos, BlockEntity, Entity, ItemStack)`,
  `Level#destroyBlock(BlockPos, boolean, Entity)`, `Player#blockActionRestricted(Level, BlockPos,
  GameType)`, `WorldBorder#isWithinBounds(BlockPos)`.
- Forge 47.4.10: `BlockEvent.BreakEvent(Level, BlockPos, BlockState, Player)` en
  `net.minecraftforge.event.level`, `LivingHurtEvent`, `DamageTypes.PLAYER_ATTACK`.

## Conocido

- **Lanzamiento aproximado**: `PistonEffect#launchSpeed` calcula la velocidad para recorrer la distancia
  pedida en el aire (12 ticks, fricción 0,91). Ignora la fricción del suelo al aterrizar y el
  frenado por colisiones: en el juego la distancia real va a ser algo distinta de 5. Se ajusta ahí.
- **Empuje del pasivo**: se aplica en `LivingHurtEvent`, antes de que el daño se resuelva del todo. Si
  otro mod cancela el daño después, el empuje queda igual. Se suma al Empuje vanilla.
- **Pico y mundos grandes**: con `maxBlocksPerTick` por defecto (27) todo el prisma se rompe en un
  tick. Esto genera drops, partículas y actualizaciones de bloques juntos; no se midió el costo.
- **Sin textos de aviso**: ninguna habilidad avisa en pantalla por qué no hizo nada (sin objetivo, sin
  bloque). Solo el log de debug del `AbilityManager`.
- Lo de F2 que sigue abierto: texturas provisionales, spike de Curios (T0.4), tecla L en conflicto con
  Avances. La línea de `F2_avances.md` que dice "Ninguna habilidad hace nada todavía" ya no vale para el
  Pistón ni el Pico.
