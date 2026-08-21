package com.palaneogenesis.entity;

import com.palaneogenesis.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Design doc Section 2 ("Káak Tun - Entidad").
 *
 * Pacifismo hacia el jugador: deliberately NO HurtByTargetGoal is registered at all. Per the
 * design doc, a mob only retaliates against whoever hit it if a HurtByTargetGoal is added; not
 * adding one at all is the simplest way to satisfy "never attacks the player, not even if
 * attacked first" without needing any defensive override in hurt(). The target selector below
 * only ever targets Enemy (hostiles), never Player.
 */
public class KaakTunEntity extends AbstractGolem {

	private static final String TAG_LAPIS_FEED_COUNT = "LapisFeedCount";
	private static final int LAPIS_FEEDS_FOR_BLUE_HEART = 4;

	/** Persisted via NBT (addAdditionalSaveData/readAdditionalSaveData) - design doc Section 2,
	 * "Ruta pacifica (alimentar Lapislazuli)". No fragment item needed; this is the counter. */
	private int lapisFeedCount = 0;

	/** Client-side only (see #setupAnimationStates / #tick): drives KaakTunModel's
	 * AnimationDefinition playback. Never touched server-side, but declared unconditionally
	 * like vanilla mobs do (e.g. Warden#roarAnimationState) since the entity class itself is
	 * shared between logical sides. */
	public final AnimationState chargeAnimationState = new AnimationState();
	/** Drives ESPECIAL_ATTACK (the melee punch) - see KaakTunMeleeAttackGoal, which is the only
	 * thing that ever calls #triggerMeleeAttackAnimation. Previously named beamFireAnimationState
	 * and driven by the beam's own fire flag, which was the root cause of the "golpe" animation
	 * playing every time the beam hit instead of on a real, distance-gated melee attack. */
	public final AnimationState meleeAttackAnimationState = new AnimationState();
	/** Short ease-back that takes over the instant chargeAnimationState's hold ends, so the arm
	 * eases down to rest instead of popping straight to it - see KaakTunAnimations.RELEASE. */
	public final AnimationState releaseAnimationState = new AnimationState();
	private int lastSeenMeleeAttackTick = -1;
	/** Tracked ourselves (rather than read back from AnimationState, whose internal accessors
	 * aren't part of the stable public API to rely on) so the auto-stop check below doesn't
	 * depend on anything beyond AnimationState#start/#stop/#isStarted, which are certain. */
	private int chargeAnimationStartTick = -1;
	private int meleeAttackAnimationStartTick = -1;
	private int releaseAnimationStartTick = -1;

	/** Duplicated from KaakTunAnimations (client package) on purpose rather than imported:
	 * KaakTunEntity is loaded on dedicated servers too, and client-package classes reference
	 * net.minecraft.client.* types that don't exist there. Keep these values in sync with
	 * KaakTunAnimations.ATTACK / .ESPECIAL_ATTACK / .RELEASE's withLength(...) (in ticks, 20/sec)
	 * if any changes. */
	private static final int ATTACK_LENGTH_TICKS = 176;
	// Resynced with KaakTunAnimations.ESPECIAL_ATTACK_LENGTH_TICKS (20 ticks now, was 38) after
	// the ESPECIAL_ATTACK re-export: the new right_arm keyframes return to rest by 1.0s/20 ticks.
	private static final int ESPECIAL_ATTACK_LENGTH_TICKS = 20;
	private static final int RELEASE_LENGTH_TICKS = 6;

	public KaakTunEntity(EntityType<? extends AbstractGolem> type, Level level) {
		super(type, level);
	}

	/** Real Iron Golem base values (vanilla): MAX_HEALTH 100.0, MOVEMENT_SPEED 0.25,
	 * KNOCKBACK_RESISTANCE 1.0, ATTACK_DAMAGE 15.0, ARMOR 0.0 (not set by vanilla).
	 * Per latest request: MAX_HEALTH 300, ARMOR/ARMOR_TOUGHNESS maxed out to a full diamond
	 * armor set's totals (20.0 armor, 8.0 toughness - helmet+chestplate+leggings+boots combined). */
	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 300.0D)
			.add(Attributes.MOVEMENT_SPEED, 0.20D)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.4D)
			.add(Attributes.ARMOR, 20.0D)
			.add(Attributes.ARMOR_TOUGHNESS, 8.0D)
			.add(Attributes.ATTACK_DAMAGE, 45.0D)
			.add(Attributes.FOLLOW_RANGE, 24.0D)
			.add(Attributes.ATTACK_KNOCKBACK, 0.0D);
	}

	@Override
	protected void registerGoals() {
		// Priority 1: melee (real, proximity-gated hit - see KaakTunMeleeAttackGoal). Registered
		// above the beam so the golem prefers landing a melee hit once a target has actually
		// closed to meleeRange, instead of continuing to charge the beam. Both goals share the
		// MOVE/LOOK flags, so only one of the two ever runs at a time.
		this.goalSelector.addGoal(1, new KaakTunMeleeAttackGoal(this));
		// Priority 2: beam attack (also handles closing distance to a valid target).
		this.goalSelector.addGoal(2, new KaakTunBeamAttackGoal(this));
		// Idle behavior, lowest to highest priority number = lowest priority.
		// KaakTunWanderGoal (not the vanilla goal directly): re-rolls a new stroll much more
		// often than vanilla's ~6s-average default, so WALK is actually visible while merodeando
		// and not just while chasing - see that class's javadoc for the full report/diagnosis.
		this.goalSelector.addGoal(3, new KaakTunWanderGoal(this, 0.6D));
		this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

		// Only ever targets hostiles (Enemy) - never Player. "equivalente al pedido 'targets all
		// standard hostile entities'" (design doc).
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(
			this, Mob.class, 10, true, false, entity -> entity instanceof Enemy));
	}

	// ------------------------------------------------------------------
	// Beam attack: synced state (design doc: "Campos de estado sincronizados en la entidad
	// (SynchedEntityData / EntityDataAccessor<Integer>)... necesarios porque el renderizado del
	// beam es client-side"). Server (KaakTunBeamAttackGoal) writes these; the client renderer
	// reads them to decide whether/how to draw the beam.
	// ------------------------------------------------------------------

	private static final EntityDataAccessor<Integer> DATA_BEAM_TARGET_ID =
		SynchedEntityData.defineId(KaakTunEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_BEAM_CHARGE =
		SynchedEntityData.defineId(KaakTunEntity.class, EntityDataSerializers.INT);
	/** Set to this.tickCount (server-side) every time KaakTunMeleeAttackGoal actually lands a
	 * melee hit. The client just watches for this value changing (see #setupAnimationStates) to
	 * (re)trigger the short meleeAttackAnimationState flash - simpler than adding a one-tick
	 * synced boolean, which can be missed if a client render frame lands between two ticks. */
	private static final EntityDataAccessor<Integer> DATA_MELEE_ATTACK_TICK =
		SynchedEntityData.defineId(KaakTunEntity.class, EntityDataSerializers.INT);

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		this.entityData.define(DATA_BEAM_TARGET_ID, 0);
		this.entityData.define(DATA_BEAM_CHARGE, 0);
		this.entityData.define(DATA_MELEE_ATTACK_TICK, 0);
	}

	/** 0 = no active beam target. */
	public void setBeamTargetId(int entityId) {
		this.entityData.set(DATA_BEAM_TARGET_ID, entityId);
	}

	public int getBeamTargetId() {
		return this.entityData.get(DATA_BEAM_TARGET_ID);
	}

	public void setBeamCharge(int ticks) {
		this.entityData.set(DATA_BEAM_CHARGE, ticks);
	}

	public int getBeamCharge() {
		return this.entityData.get(DATA_BEAM_CHARGE);
	}

	/** Called by KaakTunMeleeAttackGoal exactly on the tick a melee hit lands. */
	public void triggerMeleeAttackAnimation() {
		this.entityData.set(DATA_MELEE_ATTACK_TICK, this.tickCount);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide) {
			this.setupAnimationStates();
		}
	}

	/** Client-side only: translates the server-synced beam and melee state into the
	 * AnimationStates KaakTunModel actually reads.
	 *
	 * Bug fix (meleeAttackAnimationState never stopping): it was only ever started, never
	 * stopped, so once a hit landed the "arm raised" ESPECIAL_ATTACK pose held forever on every
	 * later frame - the animation only ever played back correctly the first time. It's now
	 * force-stopped once its own animation's real length has elapsed (using the same tick count
	 * as KaakTunAnimations.ESPECIAL_ATTACK_LENGTH_TICKS), as a safety net that self-heals
	 * regardless of whether the synced-data-driven start path above already handled it.
	 *
	 * Bug fix (instant arm snap): chargeAnimationState used to be stopped directly the moment
	 * getBeamCharge() dropped to 0 (fired, interrupted, or the timeout below). Since
	 * KaakTunModel#setupAnim resets every part to identity every frame and only re-applies a
	 * pose for AnimationStates that are currently "started", that direct stop() made the arm pop
	 * from ATTACK's held -90 deg straight to 0 deg in a single tick - happening on every charge
	 * cycle since beamChargeTicks (default 40, ~2s) is well under ATTACK's own length. Every path
	 * that used to call chargeAnimationState.stop() now also starts releaseAnimationState, which
	 * plays KaakTunAnimations.RELEASE to ease the arm back down instead. */
	private void setupAnimationStates() {
		boolean charging = this.getBeamCharge() > 0;
		if (charging && !this.chargeAnimationState.isStarted()) {
			this.chargeAnimationState.start(this.tickCount);
			this.chargeAnimationStartTick = this.tickCount;
		}

		boolean chargeTimedOut = this.chargeAnimationState.isStarted()
				&& this.tickCount - this.chargeAnimationStartTick > ATTACK_LENGTH_TICKS;

		if (this.chargeAnimationState.isStarted() && (!charging || chargeTimedOut)) {
			this.chargeAnimationState.stop();
			this.releaseAnimationState.start(this.tickCount);
			this.releaseAnimationStartTick = this.tickCount;
		}
		if (this.releaseAnimationState.isStarted()
				&& this.tickCount - this.releaseAnimationStartTick > RELEASE_LENGTH_TICKS) {
			this.releaseAnimationState.stop();
		}

		int meleeTick = this.entityData.get(DATA_MELEE_ATTACK_TICK);
		if (meleeTick != this.lastSeenMeleeAttackTick) {
			this.lastSeenMeleeAttackTick = meleeTick;
			this.meleeAttackAnimationState.start(this.tickCount);
			this.meleeAttackAnimationStartTick = this.tickCount;
		}
		if (this.meleeAttackAnimationState.isStarted()
				&& this.tickCount - this.meleeAttackAnimationStartTick > ESPECIAL_ATTACK_LENGTH_TICKS) {
			this.meleeAttackAnimationState.stop();
		}
	}

	// ------------------------------------------------------------------
	// Ruta pacifica: feed Lapis Lazuli, 4 feeds -> 1 Blue Heart.
	// ------------------------------------------------------------------

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (stack.is(Items.LAPIS_LAZULI) && this.isAlive()) {
			if (!this.level().isClientSide) {
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}

				this.lapisFeedCount++;

				if (this.lapisFeedCount >= LAPIS_FEEDS_FOR_BLUE_HEART) {
					this.lapisFeedCount = 0;
					this.spawnAtLocation(ModItems.BLUE_HEART.get());
					this.level().playSound(null, this.blockPosition(), SoundEvents.PLAYER_LEVELUP,
						SoundSource.NEUTRAL, 0.6F, 1.4F);
				} else {
					this.level().playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
						SoundSource.NEUTRAL, 0.6F, 1.2F);
				}
			}

			return InteractionResult.sidedSuccess(this.level().isClientSide);
		}

		return super.mobInteract(player, hand);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt(TAG_LAPIS_FEED_COUNT, this.lapisFeedCount);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.lapisFeedCount = tag.getInt(TAG_LAPIS_FEED_COUNT);
	}
}
