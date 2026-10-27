package com.palaneogenesis.client;

import com.palaneogenesis.biomod.ability.drones.DroneLoadout;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Un dron para GeckoLib: el estado de animación de UN dron de la flotilla de un jugador (los drones no son
 * entidades; ver client.ClientDroneVisuals, que guarda uno por dron, y client.DroneRenderer, que lo dibuja).
 * Solo existe en el cliente.
 *
 * Animaciones (guiaFuncionamientoAnimaciones.md; los nombres son los de animations/drone.animation.json, tal
 * cual, incluido que "move" no lleva el prefijo "animation."):
 * - move: siempre, en bucle (controlador "move"). Gira los dientes de los engranajes.
 * - heal: la reproduce el dron de SOPORTE al aparecer y se queda en su último cuadro mientras exista; el lente
 *   va verde todo ese tiempo. El soporte renueva su efecto cada segundo, así que está curando siempre. El
 *   dueño confirmó que heal no vuelve a blanco.
 * - attack: un disparo (DroneVisualPacket con shotSlot); el lente va rojo mientras dura y después vuelve a blanco.
 * - El dron de la Manzana solo tiene "move" y el lente blanco (decisión mía: el diseño no le dio animación).
 * heal y attack van en un segundo controlador ("action"), para que "move" siga corriendo a la vez.
 */
public final class DroneAnimatable implements GeoAnimatable {

	static final String MOVE_ANIMATION = "palaneogenesis.drone.move";
	static final String ATTACK_ANIMATION = "animation.palaneogenesis.drone.attack";
	static final String HEAL_ANIMATION = "animation.palaneogenesis.drone.heal";

	private static final String MOVE_CONTROLLER = "move";
	private static final String ACTION_CONTROLLER = "action";
	private static final String TRIGGER_ATTACK = "attack";
	private static final String TRIGGER_HEAL = "heal";

	private static final RawAnimation MOVE = RawAnimation.begin().thenLoop(MOVE_ANIMATION);
	private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay(ATTACK_ANIMATION);
	/** El archivo ya declara hold_on_last_frame para heal; acá se pide igual para no depender de eso. */
	private static final RawAnimation HEAL = RawAnimation.begin().thenPlayAndHold(HEAL_ANIMATION);

	/** Cuánto va rojo el lente tras un disparo, en ticks: lo que dura la animación attack (0,6 s). */
	private static final int ATTACK_LENS_TICKS = 12;
	/** Color del lente (multiplica al blanco #FFFFFF de la textura, así que sale exactamente este color). */
	private static final float[] LENS_GREEN = {0.1F, 1.0F, 0.2F};
	private static final float[] LENS_RED = {1.0F, 0.1F, 0.1F};

	/**
	 * Un caché por instancia (false), no el de "objeto único" que GeckoLib elige solo para lo que no es entidad
	 * ni bloque: ese separa el estado por id de instancia y las animaciones disparadas con trigger() no llegarían
	 * al dron que se dibuja. Cada DroneAnimatable es un solo dron.
	 */
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this, false);
	private final DroneLoadout.Role role;
	private boolean hasShot;
	private long lastShotGameTime;

	public DroneAnimatable(DroneLoadout.Role role) {
		this.role = role;
	}

	public DroneLoadout.Role role() {
		return role;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(
			new AnimationController<DroneAnimatable>(this, MOVE_CONTROLLER, 0, state -> state.setAndContinue(MOVE)),
			// Sin animación propia: solo reproduce las que se le disparan con trigger().
			new AnimationController<DroneAnimatable>(this, ACTION_CONTROLLER, 0, state -> PlayState.STOP)
				.triggerableAnim(TRIGGER_ATTACK, ATTACK)
				.triggerableAnim(TRIGGER_HEAL, HEAL));
	}

	/** El dron de soporte empieza a curar: animación heal (queda en su último cuadro). */
	public void startHealing() {
		trigger(TRIGGER_HEAL);
	}

	/** Este dron de ataque acaba de disparar (en el tick de juego {@code gameTime}). */
	public void shoot(long gameTime) {
		hasShot = true;
		lastShotGameTime = gameTime;
		trigger(TRIGGER_ATTACK);
	}

	private void trigger(String animation) {
		AnimatableManager<DroneAnimatable> manager = cache.getManagerForId(0L);
		manager.tryTriggerAnimation(ACTION_CONTROLLER, animation);
	}

	/**
	 * Color por el que se multiplica el lente, o null si va en blanco (sin teñir y sin brillo). Verde siempre en
	 * el de soporte; rojo en el de ataque durante {@value #ATTACK_LENS_TICKS} ticks tras cada disparo.
	 */
	public float[] lensColor(long gameTime) {
		return switch (role) {
			case SUPPORT -> LENS_GREEN;
			case ATTACK -> hasShot && gameTime - lastShotGameTime < ATTACK_LENS_TICKS ? LENS_RED : null;
			case APPLE -> null;
		};
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public double getTick(Object object) {
		return RenderUtils.getCurrentTick();
	}
}
