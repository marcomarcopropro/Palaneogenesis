# Fase 2 — Motor de habilidades: avances

Documento de diseño: Rev. 4. Cada tarea es un commit con su propia documentación (qué hace, archivos,
cómo probarla, qué se verificó y qué no). Esta página es el resumen.

## Estado de verificación

**Primer build real (`gradlew clean build`, en tu máquina):** `compileJava` dio **1 error de tipos** y
ningún otro, ya corregido en el commit "F2 fix: compila con la lista de la config". Como `javac`
también revisa los demás archivos, el resto del código de F2 pasó la comprobación de tipos contra
Minecraft, Forge y Curios (con eso quedan descartados la mayoría de los nombres de API que estaban
marcados "a confirmar"). Queda sin comprobar:

- Que el build termine entero: se cortó en `compileJava`, así que no corrieron los recursos
  (`processResources`) ni el empaquetado.
- Los 12 GameTests (`gradlew runGameTestServer`).
- Todo el comportamiento en el juego: teclas, HUD, comandos, paquete de red y persistencia.

Lo que se hizo antes, sin Gradle (la sesión no llega a `services.gradle.org`):

- Comprobar la sintaxis de cada archivo con `javac` (sin las librerías de Minecraft).
- Probar fuera del juego la lógica pura (`ModuleCounts`, `ModuleCountsCache`, `AbilityTimers`,
  `TargetFilter`, `TargetList`) con un runner de consola: 70 comprobaciones, 0 fallas.
- Validar los JSON de lang, modelos y tag.

**Siguiente paso:** `gradlew build`, después `gradlew runGameTestServer`.

## Tareas

| Tarea | Qué quedó | Cómo verlo |
|---|---|---|
| T2.1 + T1.7 | 5 ítems de módulo, límite por tipo en `canEquip`, drop "siempre conservar" | Equipar 3 pistones: el 4.º no entra. Morir con módulos: siguen puestos |
| T2.2 | `ModuleCounts` con caché invalidada por `CurioChangeEvent` | GameTests |
| T2.4 | `CooldownManager` (LISTA / ACTIVA / ENFRIANDO), persistido, reinicio al morir | `/biomod cooldown`, relog |
| T2.3 | Teclas J/K/L (Mega Jump sin tecla), `AbilityKeyPacket`, `AbilityManager` | Log con nivel debug al apretar |
| T2.5 | `BiomodSyncPacket` con estados, tiempos y conteos | HUD |
| T2.6 | HUD numérico a la derecha | `/biomod units piston 1` |
| T2.7 | `TargetRules`, lista personal persistente, `/palaneogenesis biomod targets` | `/biomod target <entidad>` |
| T0.5 | `/biomod injected, give, units, cooldown, target` | ver commit |
| T2.8 | 12 GameTests + estructura vacía | `gradlew runGameTestServer` |

## Decisiones que conviene revisar

1. **El aldeano vive dentro de la lista personal**, no como regla fija aparte. Es la única forma de
   cumplir a la vez "por defecto solo se evitan los aldeanos" y la prueba 6 de `TargetRules`. El paso
   (4) del orden de la Sección 8 queda absorbido por el (3).
2. **`canEquip` cuenta en vivo**, no con la caché: Curios avisa del cambio después de que ya pasó.
3. **El cooldown de una habilidad ACTIVA se guarda como ENFRIANDO** al desconectarse (P-22).
4. **`TargetRules` acepta cualquier `Player`**, no solo `ServerPlayer`, para poder probarlo con
   jugadores simulados.
5. **`PROTOCOL_VERSION` pasa a "3"**: se agregó `AbilityKeyPacket` y cambió el formato de
   `BiomodSyncPacket`.
6. **La L está en conflicto con Avances** (tecla vanilla). Se resuelve en Controles.

## Nombres de la API (escritos de memoria; el compilador ya los aceptó, falta verlos funcionar)

- Curios 5.14.1: `CuriosApi.getCuriosInventory`, `getStacksHandler`, `getStacks`, `CurioChangeEvent`.
- Minecraft 1.20.1: `OwnableEntity#getOwnerUUID`, `MinecraftServer#isPvpAllowed`,
  `SharedSuggestionProvider.suggestResource`.
- GameTest: `@GameTestHolder`, `@PrefixGameTestTemplate(false)`, `makeMockPlayer`, `spawn`.

## Conocido

- Al iniciar sesión, si Curios todavía no cargó el slot cuando se sincroniza, el HUD puede mostrar 0
  módulos hasta el primer `CurioChangeEvent`.
- Ninguna habilidad hace nada todavía: `AbilityManager` valida y registra la pulsación, pero no hay
  handlers hasta F3 (Pico y Pistón), F4 (Mega Jump) y F5 (Embestida).
- Las texturas de los 5 módulos son provisionales (texturas vanilla).
- El spike de Curios (T0.4) sigue abierto: `biomod_test` y `/biomod spike` no se borraron.
