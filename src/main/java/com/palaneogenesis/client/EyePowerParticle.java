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
import net.minecraft.world.phys.Vec3;

/**
 * Stage 4, Paso 1 - la "capa de poder" acordada en esta sesión ("separar el efecto en dos capas en
 * vez de intentar que una sola técnica haga las dos cosas"): un emisor de partículas continuo desde
 * cada ojo mientras la transformación está activa, mismo mecanismo que ya usa
 * client.AncientAuraParticle (partículas reales con vida/fade propios, no un objeto rígido) - eso
 * es lo que da la sensación de energía fluyendo/estela de poder en vez del "tironcito" de un solo
 * segmento por frame que tenía la EyeFlareRenderEvents vieja.
 *
 * A diferencia del aura orbital (que rodea el cuerpo, no tiene dirección propia y no necesita
 * tinte porque ancient_particle.png ya viene coloreado), esto SÍ tiñe los vértices
 * (rCol/gCol/bCol) porque reutiliza el sprite existente eye_glow.png (gradiente radial genérico,
 * pensado para tintarse - ver la paleta debajo) en vez de generar un PNG nuevo: no había un asset
 * de textura para la estela en la paleta pedida (Sección 0.1 - no se inventa arte nuevo), pero SÍ
 * había 4 colores HEX/RGB concretos dados explícitamente por el owner como especificación numérica,
 * así que aplicarlos vía tinte de vértice sobre una textura ya existente y ya aprobada (misma forma
 * de caída gaussiana que el ancla en client.EyeGlowLayer) es ejecutar la especificación dada, no
 * inventar una.
 *
 * Paleta pedida (única referencia de color dada para esta capa, no hereda el violeta/índigo del
 * aura del cuerpo a propósito):
 *   BASE   #3CA6D7 (60,166,215)  - celeste brillante predominante
 *   SHADOW #0067A1 (0,103,161)   - azul rey intermedio, da volumen
 *   DEEP   #013361 (1,51,97)     - azul oscuro, zonas de máxima oscuridad
 *   GLOW   #EBF7FD (235,247,253) - destello celeste casi blanco
 * Se usan como una rampa TEMPORAL (no espacial, que es para lo que originalmente se pensó una
 * paleta de shading) - cada partícula nace en GLOW (el destello) y se enfría hacia BASE y después
 * DEEP a medida que envejece y se apaga, como una chispa de energía perdiendo intensidad; SHADOW
 * queda como punto medio implícito de esa misma curva. Es una interpretación razonable de la
 * especificación dada, no una confirmada contra referencia visual - avisar si no lee como se
 * esperaba para ajustar los stops.
 *
 * Sin seguimiento al dueño después de nacer (a diferencia de AncientAuraParticle, que sí relee la
 * Entity cada tick): una chispa que se desprende de la cara y sigue flotando sola, aunque el dueño
 * se mueva, es justamente lo que se lee como "estela" - más simple y sin ningún null-check de
 * entidad por tick.
 */
public class EyePowerParticle extends TextureSheetParticle {

	private static final int BASE_LIFETIME_TICKS = 14;
	private static final int FADE_IN_TICKS = 2;

	/** Ver el javadoc de la clase para la paleta completa. */
	private static final float[] GLOW = rgb(235, 247, 253);
	private static final float[] BASE = rgb(60, 166, 215);
	private static final float[] DEEP = rgb(1, 51, 97);

	/** Fracción de vida en la que termina el tramo GLOW->BASE y arranca BASE->DEEP. */
	private static final float COOLDOWN_SPLIT = 0.3F;

	/** Velocidad de salida inicial (bloques/tick) y qué tan rápido decae (por tick) - una chispa
	 * que se frena en vez de volar en línea recta para siempre, mismo criterio de "que no se vea
	 * incómodo" que ya aplicó AncientAuraParticle#quadSize. */
	private static final float INITIAL_SPEED = 0.028F;
	private static final float SPEED_DECAY_PER_TICK = 0.90F;

	/** Envolvente espiral alrededor de la dirección de salida - traduce el "velocidad en espiral"
	 * del pseudocódigo GenerateParticleStream de la imagen de referencia del owner a esta técnica
	 * basada en partículas reales en vez de una curva paramétrica calculada a mano. */
	private static final float SPIRAL_RADIUS = 0.02F;
	private static final float SPIRAL_SPEED_PER_TICK = 0.9F;

	private final double dirX, dirY, dirZ;
	private final double perpAx, perpAy, perpAz;
	private final double perpBx, perpBy, perpBz;
	private float speed;
	private float spiralAngle;
	private final float baseQuadSize;

	EyePowerParticle(ClientLevel level, double x, double y, double z, Vec3 outwardDir, SpriteSet spriteSet) {
		super(level, x, y, z);

		Vec3 dir = outwardDir.lengthSqr() > 1.0E-6D ? outwardDir.normalize() : new Vec3(0.0D, 1.0D, 0.0D);
		this.dirX = dir.x;
		this.dirY = dir.y;
		this.dirZ = dir.z;

		// Base perpendicular a `dir` para el plano de la espiral - mismo truco de "fallback a un
		// eje fijo si el cross degenera" que ya usa client.EyeFlareRenderEvents/PlayerBeamRenderEvents,
		// acá sólo se resuelve UNA vez al nacer en vez de una vez por frame por entidad visible.
		Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
		Vec3 perpA = dir.cross(worldUp);
		perpA = perpA.lengthSqr() > 1.0E-6D ? perpA.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
		Vec3 perpB = dir.cross(perpA).normalize();
		this.perpAx = perpA.x; this.perpAy = perpA.y; this.perpAz = perpA.z;
		this.perpBx = perpB.x; this.perpBy = perpB.y; this.perpBz = perpB.z;

		this.speed = INITIAL_SPEED * (0.75F + this.random.nextFloat() * 0.5F);
		this.spiralAngle = this.random.nextFloat() * ((float) Math.PI * 2.0F);

		this.lifetime = Math.round(BASE_LIFETIME_TICKS * (0.8F + this.random.nextFloat() * 0.4F));
		this.baseQuadSize = 0.05F + this.random.nextFloat() * 0.025F;
		this.quadSize = this.baseQuadSize;
		this.hasPhysics = false;

		this.rCol = GLOW[0];
		this.gCol = GLOW[1];
		this.bCol = GLOW[2];

		this.setSpriteFromAge(spriteSet);
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

		this.speed *= SPEED_DECAY_PER_TICK;
		this.spiralAngle += SPIRAL_SPEED_PER_TICK;

		double straightX = this.x + this.dirX * this.speed;
		double straightY = this.y + this.dirY * this.speed;
		double straightZ = this.z + this.dirZ * this.speed;

		float spiralA = Mth.cos(this.spiralAngle) * SPIRAL_RADIUS;
		float spiralB = Mth.sin(this.spiralAngle) * SPIRAL_RADIUS;

		double newX = straightX + this.perpAx * spiralA + this.perpBx * spiralB;
		double newY = straightY + this.perpAy * spiralA + this.perpBy * spiralB;
		double newZ = straightZ + this.perpAz * spiralA + this.perpBz * spiralB;
		this.setPos(newX, newY, newZ);

		float t = Mth.clamp((float) this.age / (float) this.lifetime, 0.0F, 1.0F);
		updateColor(t);
		updateAlpha();
		this.quadSize = this.baseQuadSize * (1.0F - 0.3F * t);
	}

	/** Ver el javadoc de la clase: rampa temporal GLOW -> BASE -> DEEP a medida que la chispa
	 * envejece, no un tinte fijo (a diferencia de AncientAuraParticle, que a propósito no tiñe). */
	private void updateColor(float t) {
		if (t < COOLDOWN_SPLIT) {
			float segT = t / COOLDOWN_SPLIT;
			this.rCol = Mth.lerp(segT, GLOW[0], BASE[0]);
			this.gCol = Mth.lerp(segT, GLOW[1], BASE[1]);
			this.bCol = Mth.lerp(segT, GLOW[2], BASE[2]);
		} else {
			float segT = (t - COOLDOWN_SPLIT) / (1.0F - COOLDOWN_SPLIT);
			this.rCol = Mth.lerp(segT, BASE[0], DEEP[0]);
			this.gCol = Mth.lerp(segT, BASE[1], DEEP[1]);
			this.bCol = Mth.lerp(segT, BASE[2], DEEP[2]);
		}
	}

	/** Fade-in rápido (evita el "pop" de aparecer ya a alfa completo) + fade-out por el resto de
	 * la vida - a diferencia de la envolvente seno de AncientAuraParticle (pensada para un aura
	 * sostenida), acá se quiere leer como una chispa que se apaga, no como un ciclo simétrico. */
	private void updateAlpha() {
		float envelope;
		if (this.age < FADE_IN_TICKS) {
			envelope = (float) this.age / (float) FADE_IN_TICKS;
		} else {
			envelope = 1.0F - (float) (this.age - FADE_IN_TICKS) / (float) (this.lifetime - FADE_IN_TICKS);
		}
		this.alpha = Mth.clamp(envelope, 0.0F, 1.0F);
	}

	private static float[] rgb(int r, int g, int b) {
		return new float[] { r / 255.0F, g / 255.0F, b / 255.0F };
	}

	@Override
	public ParticleRenderType getRenderType() {
		// Mismo criterio que AncientAuraParticle: translúcido, sigue la luz del mundo (no
		// full-bright) - el ancla fija (client.EyeGlowLayer) es la que se fuerza a full-bright
		// para leerse como fuente de luz propia; esta capa es la estela que emana de ella.
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	static final class Provider implements ParticleProvider<SimpleParticleType> {

		Provider(SpriteSet spriteSet) {
			// Mismo motivo que AncientAuraParticle.Provider: se cachea acá porque
			// client.EyePowerSpawner construye partículas directo, pasando una dirección de salida
			// que no entra en la firma fija de ParticleProvider#createParticle.
			CACHED_SPRITE_SET = spriteSet;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level,
				double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
			// No se usa en la práctica - ver client.EyePowerSpawner, que llama a #spawn directo.
			return null;
		}
	}

	private static SpriteSet CACHED_SPRITE_SET;

	/** Punto de entrada real (ver client.EyePowerSpawner). */
	public static void spawn(ClientLevel level, double x, double y, double z, Vec3 outwardDir) {
		if (CACHED_SPRITE_SET == null) {
			return;
		}
		Minecraft.getInstance().particleEngine.add(
			new EyePowerParticle(level, x, y, z, outwardDir, CACHED_SPRITE_SET));
	}
}
