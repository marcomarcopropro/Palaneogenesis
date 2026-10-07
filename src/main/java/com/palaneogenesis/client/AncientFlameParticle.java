package com.palaneogenesis.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
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
import net.minecraft.world.phys.Vec3;

/**
 * "Llama de energía" del aura: una lengua vertical suave (textura aura_wisp.png, gradiente
 * blanco que se tiñe con {@link AncientPalette}) que nace pegada al cuerpo, acelera hacia arriba
 * y se apaga. Es lo que hace que el aura se lea como el aura de un súper saiyajin / anime y no
 * como el remolino de partículas de una poción (el aura orbital anterior).
 *
 * Diferencias clave con una partícula vanilla:
 * - Billboard CILÍNDRICO (render propio): el quad siempre mira a la cámara en horizontal pero
 *   su eje es el vertical del mundo. Un billboard normal gira con la cámara y una lengua
 *   estirada se vería torcida al mirar desde arriba/abajo.
 * - Full-bright (0xF000F0): se lee como luz propia, también de noche/en cuevas.
 * - ANCLADA al dueño (FIX "las líneas quedan atrás cuando el personaje se mueve"): antes la
 *   llama guardaba una posición de MUNDO y copiaba sólo el 85 % del desplazamiento del dueño por
 *   tick, así que al correr se despegaba y quedaba rezagada. Ahora guarda un OFFSET relativo al
 *   dueño (offX/offY/offZ) y en cada frame se dibuja en lerp(dueño.xo, dueño.x, partialTick) +
 *   lerp(offsetPrevio, offset, partialTick): exactamente la misma interpolación que usa el modelo
 *   del jugador, así que la llama acompaña al cuerpo cuadro a cuadro, sin retraso ni temblor.
 * - Se atenúa cerca de la cámara: en primera persona las llamas que pasan a centímetros de
 *   los ojos no tapan la pantalla.
 */
public class AncientFlameParticle extends TextureSheetParticle {

	private static final int BASE_LIFETIME_TICKS = 22;
	/** Cuánto se acerca por tick al eje del cuerpo (las llamas convergen hacia arriba). */
	private static final double CONVERGE_PER_TICK = 0.02D;
	private static final float NEAR_FADE_START = 0.45F;
	private static final float NEAR_FADE_END = 1.40F;
	private static final int FULL_BRIGHT = 0xF000F0;

	private final int ownerId;
	private final float baseHalfWidth;
	private final float baseHalfHeight;
	private final float peakAlpha;
	private final float riseAccel;
	private final float maxRise;
	private final float swayPhase;
	private final float swayAmp;
	private final float swayDirX;
	private final float swayDirZ;
	private final float[] tmpColor = new float[3];

	/** Posición relativa al dueño (bloques) en este tick y en el anterior (para interpolar). */
	private double offX;
	private double offY;
	private double offZ;
	private double prevOffX;
	private double prevOffY;
	private double prevOffZ;
	/** Dueño resuelto en el último tick (se renueva cada tick por id). */
	private LivingEntity owner;

	private float riseSpeed = 0.012F;
	private float halfWidth;
	private float halfHeight;

	/**
	 * @param intensity 0..1, qué tan fuerte es el pulso actual (ver AncientAuraSpawner): más
	 *                  intensidad = llamas más altas, anchas, rápidas y opacas.
	 * @param burst     true para las llamas del "estallido" al inicio de cada pulso: nacen en un
	 *                  anillo bajo, a ras del piso, y suben más rápido y más alto.
	 */
	AncientFlameParticle(ClientLevel level, LivingEntity owner, float intensity, boolean burst, SpriteSet spriteSet) {
		super(level, owner.getX(), owner.getY(), owner.getZ());
		this.ownerId = owner.getId();

		float angle = this.random.nextFloat() * ((float) Math.PI * 2.0F);
		float radius = burst ? 0.45F + this.random.nextFloat() * 0.25F : 0.28F + this.random.nextFloat() * 0.20F;
		float height = burst
			? this.random.nextFloat() * 0.15F
			: (float) Math.pow(this.random.nextFloat(), 1.4D) * owner.getBbHeight() * 0.85F;
		this.owner = owner;
		this.offX = Mth.cos(angle) * radius;
		this.offY = height;
		this.offZ = Mth.sin(angle) * radius;
		// Sin esto el primer frame interpola desde (0,0,0) hasta el offset de spawn.
		this.prevOffX = this.offX;
		this.prevOffY = this.offY;
		this.prevOffZ = this.offZ;
		this.setPos(owner.getX() + this.offX, owner.getY() + this.offY, owner.getZ() + this.offZ);
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;

		float burstScale = burst ? 1.25F : 1.0F;
		this.lifetime = Math.round(BASE_LIFETIME_TICKS * (0.8F + this.random.nextFloat() * 0.4F) * (burst ? 1.3F : 1.0F));
		this.baseHalfWidth = (0.05F + 0.035F * this.random.nextFloat()) * (0.85F + 0.4F * intensity);
		this.baseHalfHeight = (0.26F + 0.22F * this.random.nextFloat()) * (0.75F + 0.6F * intensity) * burstScale;
		this.peakAlpha = (0.30F + 0.35F * intensity) * (burst ? 1.15F : 1.0F);
		this.riseAccel = 0.0035F + 0.003F * intensity;
		this.maxRise = (0.045F + 0.04F * intensity) * (burst ? 1.4F : 1.0F);

		this.swayPhase = this.random.nextFloat() * 6.2831855F;
		this.swayAmp = 0.004F + this.random.nextFloat() * 0.004F;
		float swayAngle = this.random.nextFloat() * 6.2831855F;
		this.swayDirX = Mth.cos(swayAngle);
		this.swayDirZ = Mth.sin(swayAngle);

		this.halfWidth = this.baseHalfWidth;
		this.halfHeight = this.baseHalfHeight;
		this.hasPhysics = false;
		this.alpha = 0.0F;
		this.rCol = AncientPalette.CORE[0];
		this.gCol = AncientPalette.CORE[1];
		this.bCol = AncientPalette.CORE[2];
		// Caja de colisión/culling acorde a una lengua alta, no al 0.2x0.2 por defecto.
		this.setSize(0.3F, 0.9F);
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
		Entity entity = this.level.getEntity(this.ownerId);
		if (!(entity instanceof LivingEntity owner) || !owner.isAlive()) {
			this.remove();
			return;
		}

		this.owner = owner;

		float t = Mth.clamp((float) this.age / (float) this.lifetime, 0.0F, 1.0F);

		// Todo el movimiento propio de la llama (subir, converger al eje, ondular) ocurre en
		// coordenadas RELATIVAS al dueño; el desplazamiento del dueño se suma al dibujar.
		this.prevOffX = this.offX;
		this.prevOffY = this.offY;
		this.prevOffZ = this.offZ;

		this.riseSpeed = Math.min(this.maxRise, this.riseSpeed + this.riseAccel);
		this.offY += this.riseSpeed;

		double keep = 1.0D - CONVERGE_PER_TICK;
		this.offX *= keep;
		this.offZ *= keep;

		float sway = Mth.cos((this.age + this.swayPhase) * 0.3F) * this.swayAmp;
		this.offX += this.swayDirX * sway;
		this.offZ += this.swayDirZ * sway;

		// La posición de mundo sólo se usa para la caja de culling del motor de partículas.
		this.setPos(owner.getX() + this.offX, owner.getY() + this.offY, owner.getZ() + this.offZ);

		float bell = Mth.sin(t * (float) Math.PI);
		this.alpha = this.peakAlpha * (float) Math.pow(bell, 0.75D);
		this.halfHeight = this.baseHalfHeight * (0.55F + 0.45F * bell);
		this.halfWidth = this.baseHalfWidth * (0.7F + 0.3F * bell);
		AncientPalette.energy(t, this.tmpColor);
		this.rCol = this.tmpColor[0];
		this.gCol = this.tmpColor[1];
		this.bCol = this.tmpColor[2];
	}

	@Override
	public void render(VertexConsumer consumer, Camera camera, float partialTick) {
		Vec3 cam = camera.getPosition();
		// Base = posición interpolada del dueño (la misma que usa su modelo y la cámara) + offset
		// interpolado de la llama: queda pegada al cuerpo sin importar la velocidad.
		double baseX = this.x;
		double baseY = this.y;
		double baseZ = this.z;
		double offsetX = 0.0D;
		double offsetY = 0.0D;
		double offsetZ = 0.0D;
		if (this.owner != null) {
			baseX = Mth.lerp((double) partialTick, this.owner.xo, this.owner.getX());
			baseY = Mth.lerp((double) partialTick, this.owner.yo, this.owner.getY());
			baseZ = Mth.lerp((double) partialTick, this.owner.zo, this.owner.getZ());
			offsetX = Mth.lerp((double) partialTick, this.prevOffX, this.offX);
			offsetY = Mth.lerp((double) partialTick, this.prevOffY, this.offY);
			offsetZ = Mth.lerp((double) partialTick, this.prevOffZ, this.offZ);
		}
		float px = (float) (baseX + offsetX - cam.x());
		float py = (float) (baseY + offsetY - cam.y());
		float pz = (float) (baseZ + offsetZ - cam.z());

		float dist = Mth.sqrt(px * px + py * py + pz * pz);
		float nearFade = Mth.clamp((dist - NEAR_FADE_START) / (NEAR_FADE_END - NEAR_FADE_START), 0.0F, 1.0F);
		float a = this.alpha * nearFade;
		if (a <= 0.01F) {
			return;
		}

		// Eje "izquierda" del billboard cilíndrico: perpendicular (en horizontal) a la línea
		// cámara->partícula. Es el mismo sentido que el vector izquierda de la cámara que usa
		// vanilla, así que el orden de vértices de abajo conserva su orientación de caras.
		float lx = pz;
		float lz = -px;
		float len = Mth.sqrt(lx * lx + lz * lz);
		if (len < 1.0E-4F) {
			lx = 1.0F;
			lz = 0.0F;
		} else {
			lx /= len;
			lz /= len;
		}

		float w = this.halfWidth;
		float h = this.halfHeight;
		float u0 = this.getU0();
		float u1 = this.getU1();
		float v0 = this.getV0();
		float v1 = this.getV1();

		consumer.vertex(px - lx * w, py - h, pz - lz * w).uv(u1, v1).color(this.rCol, this.gCol, this.bCol, a).uv2(FULL_BRIGHT).endVertex();
		consumer.vertex(px - lx * w, py + h, pz - lz * w).uv(u1, v0).color(this.rCol, this.gCol, this.bCol, a).uv2(FULL_BRIGHT).endVertex();
		consumer.vertex(px + lx * w, py + h, pz + lz * w).uv(u0, v0).color(this.rCol, this.gCol, this.bCol, a).uv2(FULL_BRIGHT).endVertex();
		consumer.vertex(px + lx * w, py - h, pz + lz * w).uv(u0, v1).color(this.rCol, this.gCol, this.bCol, a).uv2(FULL_BRIGHT).endVertex();
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	static final class Provider implements ParticleProvider<SimpleParticleType> {

		Provider(SpriteSet spriteSet) {
			// Mismo patrón que AncientAuraParticle/EyePowerParticle: el spawner construye la
			// partícula directo (necesita pasar dueño + intensidad, que no entran en la firma de
			// createParticle), así que el SpriteSet se cachea acá.
			CACHED_SPRITE_SET = spriteSet;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level,
				double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
			return null;
		}
	}

	private static SpriteSet CACHED_SPRITE_SET;

	public static void spawn(ClientLevel level, LivingEntity owner, float intensity, boolean burst) {
		if (CACHED_SPRITE_SET == null) {
			return;
		}
		Minecraft.getInstance().particleEngine.add(
			new AncientFlameParticle(level, owner, intensity, burst, CACHED_SPRITE_SET));
	}
}
