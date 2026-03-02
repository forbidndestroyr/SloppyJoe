package com.sloppyjoe.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.passive.HorseEntity;

public class HorsePlaneEntity extends HorseEntity {

    private static final float FLY_ACCEL   = 0.10f;
    private static final float FLY_DRAG    = 0.95f;
    private static final float BRAKE_DRAG  = 0.55f;
    /** Ground-roll friction — decelerates the plane after landing. */
    private static final float LAND_DRAG   = 0.72f;
    /** Horizontal speed (blocks/tick) needed before vertical thrust is allowed. */
    private static final float TAKEOFF_SPEED = 0.12f;

    /** Minimum horizontal speed (blocks/tick) before a collision counts as a crash. */
    private static final float CRASH_SPEED_THRESHOLD = 0.30f;

    /** Pitch (degrees, positive = looking down) above which a ground-first landing is a nosedive. */
    private static final float NOSEDIVE_PITCH_THRESHOLD = 60.0f;

    public HorsePlaneEntity(EntityType<? extends HorsePlaneEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createHorsePlaneAttributes() {
        return AnimalEntity.createAnimalAttributes()
                .add(EntityAttributes.MAX_HEALTH, 30.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.225)
                .add(EntityAttributes.JUMP_STRENGTH, 0.7);
    }

    public void makeTame() {
        this.setTame(true);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier,
                                    DamageSource source) {
        return false;
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!hasPassengers() && !player.shouldCancelInteraction()) {
            player.startRiding(this);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public void travel(Vec3d movementInput) {
        if (isAlive() && hasPassengers() && getFirstPassenger() instanceof LivingEntity rider) {
            setYaw(rider.getYaw());
            setBodyYaw(rider.getYaw());
            setHeadYaw(rider.getYaw());
            setPitch(rider.getPitch() * 0.5f);

            setNoGravity(true);
            this.fallDistance = 0f;

            // Capture horizontal speed BEFORE the move so we know how fast we were going.
            float speed = (float) getVelocity().horizontalLength();
            boolean wasInAir = !isOnGround();

            boolean braking = rider.isSneaking();

            if (isOnGround()) {
                // ---- ground roll / takeoff run ----
                if (!braking) {
                    // Only push horizontally while on the ground — look direction's Y is ignored.
                    Vec3d look = rider.getRotationVec(1.0f);
                    Vec3d horiz = new Vec3d(look.x, 0, look.z);
                    if (horiz.lengthSquared() > 0.001) {
                        addVelocity(horiz.normalize().multiply(FLY_ACCEL * 0.8));
                    }
                }
                // Stronger ground friction — decelerates to a stop after landing.
                float groundDrag = braking ? BRAKE_DRAG : LAND_DRAG;
                setVelocity(getVelocity().x * groundDrag, 0.0, getVelocity().z * groundDrag);
            } else {
                // ---- airborne flight ----
                if (!braking) {
                    Vec3d thrust = rider.getRotationVec(1.0f).multiply(FLY_ACCEL);
                    // Gate upward climb until enough horizontal speed is built.
                    if (speed < TAKEOFF_SPEED) {
                        thrust = new Vec3d(thrust.x, Math.min(thrust.y, 0), thrust.z);
                    }
                    addVelocity(thrust);
                }
                float drag = braking ? BRAKE_DRAG : FLY_DRAG;
                setVelocity(getVelocity().multiply(drag));
            }

            move(MovementType.SELF, getVelocity());

            // ---- crash detection (server-side, flying-only) ----
            if (!getWorld().isClient() && wasInAir && speed > CRASH_SPEED_THRESHOLD) {
                if (horizontalCollision) {
                    // Head hit a wall
                    crashExplode();
                    return;
                } else if (isOnGround() && rider.getPitch() > NOSEDIVE_PITCH_THRESHOLD) {
                    // Nose-dived into the ground
                    crashExplode();
                    return;
                }
            }

            if (horizontalCollision && !isOnGround()) {
                setVelocity(getVelocity().x * 0.2, getVelocity().y * 0.5, getVelocity().z * 0.2);
            }

        } else {
            setNoGravity(false);
            this.fallDistance = 0f;
            super.travel(movementInput);
        }
    }

    /**
     * Creates an explosion centred on the horse and removes it.
     * Only call from the server side.
     */
    private void crashExplode() {
        getWorld().createExplosion(
                this,
                getX(), getY() + 0.5, getZ(),
                12.0f,
                true,
                World.ExplosionSourceType.MOB
        );
        this.discard();
    }
}
