package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.A10Entity;
import io.github.mcheliceport.util.McheliMath;
import io.github.mcheliceport.util.McheliSounds;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponWorld;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Close air support: calls in three A-10 strafing runs, 45 ticks apart, on the block the user
 * looks at (at least 20 blocks away), flying across the user's view from one side. Ported from
 * upstream {@code MCH_WeaponCAS}.
 *
 * <p>Upstream never set the shooting vehicle ({@code this.shooter = shooter} assigned the field to
 * itself), so the A-10 belonged to no vehicle; here it belongs to the vehicle that called it (D20).
 * The ground laser designator calls it with no vehicle, as upstream.
 */
public class CasWeapon extends WeaponBase {

    private double targetPosX;
    private double targetPosY;
    private double targetPosZ;
    private int direction;
    private int startTick;
    private int attackCount;
    private @Nullable Entity user;
    private @Nullable Entity shooter;

    public CasWeapon(Level world, Vec3 pos, float yaw, float pitch, String name, WeaponInfo info) {
        super(world, pos, yaw, pitch, name, info);
        this.acceleration = 4.0F;
        this.explosionPower = 2;
        this.power = 32;
        this.interval = world.isClientSide() ? 65226 : 65236;
        this.direction = 0;
        this.startTick = 0;
        this.attackCount = 3;
    }

    @Override
    public void update(int countWait) {
        super.update(countWait);
        if (!this.world.isClientSide() && this.attackCount < 3 && countWait != 0 && this.tick == this.startTick) {
            double offsetX = 0;
            double offsetZ = 0;
            if (this.attackCount >= 1) {
                double sign = this.attackCount == 1 ? 1.0 : -1.0;
                if (this.direction == 0 || this.direction == 2) {
                    offsetX = rand.nextDouble() * 10.0 * sign;
                    offsetZ = (rand.nextDouble() - 0.5) * 10.0;
                } else {
                    offsetZ = rand.nextDouble() * 10.0 * sign;
                    offsetX = (rand.nextDouble() - 0.5) * 10.0;
                }
            }
            this.spawnA10(this.targetPosX + offsetX, this.targetPosY + 20.0, this.targetPosZ + offsetZ);
            this.startTick = this.tick + 45;
            this.attackCount++;
        }
    }

    @Override
    public void modifyParameters() {
        if (this.interval > 65286) {
            this.interval = 65286;
        }
    }

    public void spawnA10(double x, double y, double z) {
        double motionX = 0;
        double motionZ = 0;
        double spawnX = x;
        double spawnZ = z;
        switch (this.direction) {
            case 0 -> {
                motionZ = 3.0;
                spawnZ -= 90.0;
            }
            case 1 -> {
                motionX = -3.0;
                spawnX += 90.0;
            }
            case 2 -> {
                motionZ = -3.0;
                spawnZ += 90.0;
            }
            case 3 -> {
                motionX = 3.0;
                spawnX -= 90.0;
            }
            default -> {
            }
        }
        A10Entity a10 = new A10Entity(this.world, spawnX, y, spawnZ);
        a10.setWeaponName(this.name);
        a10.setYRot(90.0F * this.direction);
        a10.yRotO = a10.getYRot();
        a10.setDeltaMovement(motionX, 0.0, motionZ);
        a10.direction = this.direction;
        a10.shootingEntity = this.user;
        a10.shootingAircraft = this.shooter;
        a10.explosionPower = this.explosionPower;
        a10.power = this.power;
        a10.acceleration = this.acceleration;
        this.world.addFreshEntity(a10);
        McheliSounds.play(this.world, spawnX, y, spawnZ, "a-10_snd", 150.0F, 1.0F);
    }

    @Override
    public boolean shot(WeaponParam prm) {
        if (prm.user == null || prm.entity == null) {
            return false;
        }
        return this.performStrikeInquiry(prm.user, prm.entity, prm.entity.getX(), prm.entity.getY() + 2.0,
                prm.entity.getZ());
    }

    public boolean performStrikeInquiry(Entity user, @Nullable Entity shooter, double px, double py, double pz) {
        float yaw = user.getYRot();
        float pitch = user.getXRot();
        Vec3 lookVec = WeaponWorld.fromPitchYaw(pitch, yaw);
        double range = this.world.isClientSide() ? 80.0 : 150.0;
        Vec3 src = new Vec3(px, py, pz);
        Vec3 dst = src.add(lookVec.scale(range));
        BlockHitResult hit = WeaponWorld.rayTrace(this.world, src, dst, false, true);
        if (hit == null) {
            return false;
        }
        if (this.world.isClientSide() && hit.getLocation().distanceToSqr(px, py, pz) < 400.0) {
            return false;
        }
        this.targetPosX = hit.getLocation().x;
        this.targetPosY = hit.getLocation().y;
        this.targetPosZ = hit.getLocation().z;
        int baseDir = (int) McheliMath.getRotate360(yaw + 45.0F) / 90;
        this.direction = (baseDir + (rand.nextBoolean() ? -1 : 1)) % 4;
        if (this.direction < 0) {
            this.direction += 4;
        }
        this.user = user;
        this.shooter = shooter;
        if (shooter != null) {
            this.playSoundClient(shooter, 1.0F, 1.0F);
        }
        this.tick = 0;
        this.startTick = 50;
        this.attackCount = 0;
        return true;
    }
}
