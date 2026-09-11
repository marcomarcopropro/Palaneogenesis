package com.palaneogenesis.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Stage 4, Paso 1 - aura orbital pedida por el owner ("esas ondas alrededor de Steve"), visible
 * TODO el tiempo que el jugador está transformado (confirmado explícitamente, no sólo en el
 * instante de transformar/destransformar).
 *
 * Traduce a ticks de Minecraft (20/s) la física del prototipo de referencia en Three.js que mandó
 * el owner (ancient_particle_prototype.py): cada partícula orbita a radio fijo alrededor del
 * dueño, sube en línea recta mientras vive, y su opacidad sigue la misma envolvente sinusoidal
 * (fade-in/fade-out, no un corte brusco). No se traslada el código en sí (usa Three.js/Sprites,
 * ajeno a este motor) - sólo el comportamiento resultante, con las mismas constantes de diseño
 * (radio, velocidades, duración) convertidas de segundos a ticks.
 *
 * A propósito SIN tinte de color (rCol/gCol/bCol quedan en blanco, 1F/1F/1F): pedido explícito del
 * owner ("que se respete la gama de colores del PNG que te pasé") - ancient_particle.png ya es
 * violeta/índigo oscuro por sí solo (verificado leyendo el archivo: tonos como RGB(36,4,51) y
 * RGB(6,27,77), nada de cian pese a que el prototipo de referencia sí tintaba cian) - así que la
 * textura debe mostrarse tal cual viene, no reforzada con un multiply.
 *
 * hasPhysics=false a propósito: es un efecto puramente decorativo orbitando en el aire alrededor
 * del cuerpo, no tiene que chocar contra bloques ni el propio jugador.
 */
public class AncientAuraParticle extends TextureSheetParticle {

	/** DURACION_VIDA del prototipo (1.25s) en ticks, antes del jitter ±20% que ya tenía el
	 * prototipo (ver #AncientAuraParticle). */
	private static final int BASE_LIFETIME_TICKS = 25;

	/** VELOCIDAD_ORBITA del prototipo (2 rad/s) pasado a rad/tick (÷20). */
	private static final float BASE_ORBIT_SPEED_PER_TICK = 2.0F / 20.0F;

	/** VELOCIDAD_FLOTAR del prototipo (1 bloque/s) pasado a bloques/tick (÷20). */
	private static final float BASE_FLOAT_SPEED_PER_TICK = 1.0F / 20.0F;

	private static final float RADIUS_MIN = 0.4F;
	private static final float RADIUS_MAX = 0.7F;
	private static final float SPAWN_HEIGHT_MIN = 0.0F;
	private static final float SPAWN_HEIGHT_MAX = 1.0F;

	/** OPACIDAD_MAXIMA del prototipo (1.1 - un poco de "sobregiro" a propósito en su envolvente
	 * seno, que el clamp de abajo recorta a 1.0 en los frames más brillantes en vez de dejarlo
	 * en un pico más bajo). */
	private static final float OPACITY_PEAK = 1.1F;

	private final int ownerId;
	private final float radius;
	private final float orbitSpeedPerTick;
	private final float floatSpeedPerTick;
	private final float spawnHeightOffset;
	private float angle;
	private float risen;

	AncientAuraParticle(ClientLevel level, Entity owner, SpriteSet spriteSet) {
		super(level, owner.getX(), owner.getY(), owner.getZ());
		this.ownerId = owner.getId();

		this.radius = RADIUS_MIN + this.random.nextFloat() * (RADIUS_MAX - RADIUS_MIN);
		this.angle = this.random.nextFloat() * ((float) Math.PI * 2.0F);
		// Dirección de giro aleatoria por partícula en vez de alternar por índice de spawn como
		// hacía el prototipo (i % 2 == 0 ? 1 : -1) - mismo resultado visual (mitad y mitad en
		// promedio), más simple de generar desde un spawner que no lleva un índice.
		float direction = this.random.nextBoolean() ? 1.0F : -1.0F;
		this.orbitSpeedPerTick = BASE_ORBIT_SPEED_PER_TICK * (0.8F + this.random.nextFloat() * 0.4F) * direction;
		this.floatSpeedPerTick = BASE_FLOAT_SPEED_PER_TICK * (0.8F + this.random.nextFloat() * 0.4F);
		this.spawnHeightOffset = SPAWN_HEIGHT_MIN + this.random.nextFloat() * (SPAWN_HEIGHT_MAX - SPAWN_HEIGHT_MIN);
		this.risen = 0.0F;

		this.lifetime = Math.round(BASE_LIFETIME_TICKS * (0.8F + this.random.nextFloat() * 0.4F));
		// TAMANO_PARTICULA del prototipo (0.05 bloques) - a propósito chico, pedido explícito
		// "que no sobrecargue... que no se vea incómodo".
		this.quadSize = 0.05F;
		this.hasPhysics = false;
		this.rCol = 1.0F;
		this.gCol = 1.0F;
		this.bCol = 1.0F;

		this.setSpriteFromAge(spriteSet);
		this.updatePosition();
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;

		if (this.age++ >= this.lifetime) {
			this.remove();
			return;
		}

		Entity owner = this.level.getEntity(this.ownerId);
		if (!(owner instanceof LivingEntity living) || !living.isAlive()) {
			this.remove();
			return;
		}

		this.angle += this.orbitSpeedPerTick;
		this.risen += this.floatSpeedPerTick;

		float lifeRatio = 1.0F - (float) this.age / (float) this.lifetime;
		this.alpha = Mth.clamp(OPACITY_PEAK * Mth.sin(lifeRatio * (float) Math.PI), 0.0F, 1.0F);

		this.updatePosition();
	}

	private void updatePosition() {
		Entity owner = this.level.getEntity(this.ownerId);
		if (!(owner instanceof LivingEntity living)) {
			return;
		}
		double centerX = living.getX();
		double centerY = living.getY() + this.spawnHeightOffset + this.risen;
		double centerZ = living.getZ();
		double newX = centerX + Mth.cos(this.angle) * this.radius;
		double newZ = centerZ + Mth.sin(this.angle) * this.radius;
		this.setPos(newX, centerY, newZ);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	static final class Provider implements ParticleProvider<SimpleParticleType> {

		Provider(SpriteSet spriteSet) {
			// Se cachea acá porque AncientAuraSpawner necesita construir partículas pasando el
			// Entity dueño directamente - un dato que no entra en la firma fija de
			// ParticleProvider#createParticle (x/y/z/xSpeed/ySpeed/zSpeed, sin lugar para "a qué
			// entidad seguir"). Forge llama a este constructor una sola vez, en el registro (ver
			// client.ClientModEvents#registerParticleProviders), así que alcanza con guardarlo acá
			// en vez de en un campo de instancia que después nadie lee.
			CACHED_SPRITE_SET = spriteSet;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level,
				double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
			// No se usa en la práctica (ver AncientAuraSpawner, que llama a #spawn directo con la
			// Entity dueña ya resuelta) - level.addParticle(ANCIENT_AURA.get(), ...) no trae
			// ninguna entidad a la que seguir en su firma fija (x/y/z/xSpeed/ySpeed/zSpeed nada
			// más), así que no hay nada razonable que construir acá todavía. Se deja el método
			// implementado (devolviendo null, que ParticleEngine ya maneja de forma segura) en vez
			// de directamente no registrar el ParticleType, por si más adelante alguna otra parte
			// del código sí quiere spawnear vía el mecanismo estándar.
			return null;
		}
	}

	/** Ver el comentario en Provider#Provider sobre por qué se cachea acá en vez de pasarse por
	 * parámetro en cada spawn. */
	private static SpriteSet CACHED_SPRITE_SET;

	/** Punto de entrada real (ver client.AncientAuraSpawner): construye y agrega la partícula
	 * directo al particleEngine, evitando el viaje forzado por
	 * ParticleProvider#createParticle(x,y,z,xSpeed,ySpeed,zSpeed) - esa firma no tiene lugar para
	 * pasar "a qué entidad seguir" sin forzarlo adentro de xSpeed/ySpeed/zSpeed. */
	public static void spawn(ClientLevel level, Entity owner) {
		if (CACHED_SPRITE_SET == null) {
			return;
		}
		Minecraft.getInstance().particleEngine.add(
			new AncientAuraParticle(level, owner, CACHED_SPRITE_SET));
	}
}
