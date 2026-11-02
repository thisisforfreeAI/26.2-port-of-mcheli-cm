package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.flare.FlareEntity;
import io.github.mcheliceport.weapon.WeaponClientHooks;
import java.util.Random;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A vehicle's flare dispenser: each pattern (types 1 to 5 and 10) releases a number of flares at
 * an interval for a while, then waits before it can fire again. While the first part of a pattern
 * runs the vehicle counts as "using flares", which breaks heat-seeker locks. Ported from upstream
 * {@code MCH_Flare}.
 *
 * <p>Upstream kept the pattern table in a static field built by whichever side created a dispenser
 * first, so in single player both sides got the same (client or server) wait times. Each side
 * gets its own here, as intended: clients wait 50 ticks longer. Upstream's "FlareUsing" flag
 * lived in the entity's custom data and was saved with it; here it is
 * {@link AircraftEntity#isFlareDecoying()}, not saved.
 */
public final class AircraftFlares {

    /** One pattern: flares per release, release interval, total wait, part counting as in use, releases. */
    record FlareParam(int num, int interval, int tickWait, int tickEnable, int numFlareMax) {
    }

    private static final FlareParam[] SERVER_DATA = table(0);
    private static final FlareParam[] CLIENT_DATA = table(50);

    private final Level worldObj;
    private final AircraftEntity aircraft;
    private final FlareParam[] data;
    private final Random rand = new Random();
    public int numFlare;
    public int tick;
    private int flareType;
    private boolean flareUsing;

    public AircraftFlares(Level w, AircraftEntity ac) {
        this.worldObj = w;
        this.aircraft = ac;
        this.data = w.isClientSide() ? CLIENT_DATA : SERVER_DATA;
        this.tick = 0;
        this.numFlare = 0;
        this.flareType = 0;
    }

    private static FlareParam[] table(int delay) {
        FlareParam[] t = new FlareParam[11];
        t[1] = new FlareParam(1, 3, 200 + delay, 100, 16);
        t[2] = new FlareParam(3, 5, 300 + delay, 200, 16);
        t[3] = new FlareParam(2, 3, 200 + delay, 100, 16);
        t[4] = new FlareParam(1, 3, 200 + delay, 100, 16);
        t[5] = new FlareParam(2, 3, 200 + delay, 100, 16);
        t[10] = new FlareParam(8, 1, 250 + delay, 60, 1);
        t[0] = t[1];
        t[6] = t[1];
        t[7] = t[1];
        t[8] = t[1];
        t[9] = t[1];
        return t;
    }

    public boolean isInPreparation() {
        return this.tick != 0;
    }

    public boolean isUsing() {
        int type = this.getFlareType();
        return this.tick != 0 && type >= 0 && type < this.data.length
                && this.tick > this.data[type].tickWait - this.data[type].tickEnable;
    }

    /** Whether heat seekers see this vehicle dispensing flares (upstream's "FlareUsing" flag). */
    public boolean isFlareUsing() {
        return this.flareUsing;
    }

    public int getFlareType() {
        return this.flareType;
    }

    /** Starts a pattern; on the client only when the last one is over (with a click). */
    public boolean use(int type) {
        if (type <= 0 || type >= this.data.length) {
            return false;
        }
        this.flareType = type;
        if (this.worldObj.isClientSide()) {
            if (this.tick == 0) {
                this.tick = this.data[type].tickWait;
                this.numFlare = 0;
                WeaponClientHooks.playSoundClick(1.0F, 1.0F);
                return true;
            }
            return false;
        }
        this.numFlare = 0;
        this.tick = this.data[type].tickWait;
        this.flareUsing = true;
        return true;
    }

    public void update() {
        int type = this.getFlareType();
        if (!this.aircraft.isAlive() || type <= 0 || type >= this.data.length) {
            return;
        }
        if (this.tick > 0) {
            this.tick--;
        }
        FlareParam p = this.data[type];
        AircraftInfo info = this.aircraft.getAcInfo();
        if (!this.worldObj.isClientSide() && info != null && this.tick > 0 && this.tick % p.interval() == 0
                && this.numFlare < p.numFlareMax()) {
            Vec3 v = info.flare.pos;
            v = this.aircraft.getTransformedPosition(v.x, v.y, v.z, this.aircraft.xo, this.aircraft.yo,
                    this.aircraft.zo);
            this.spawnFlare(v);
        }
        if (!this.isUsing() && this.flareUsing) {
            this.flareUsing = false;
        }
    }

    private void spawnFlare(Vec3 v) {
        this.numFlare++;
        int type = this.getFlareType();
        int num = this.data[type].num();
        Vec3 am = this.aircraft.getDeltaMovement();
        this.worldObj.playSound(null, v.x - am.x * 2.0, v.y - am.y * 2.0 - 1.0, v.z - am.z * 2.0,
                SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                2.6F + (this.rand.nextFloat() - this.rand.nextFloat()) * 0.8F);
        for (int i = 0; i < num; i++) {
            double x = v.x - am.x * 2.0;
            double y = v.y - am.y * 2.0 - 1.0;
            double z = v.z - am.z * 2.0;
            double tx = 0.0;
            double ty = am.y;
            double tz = 0.0;
            int fuseCount = 0;
            double r = this.aircraft.getYRot();
            if (type == 1) {
                tx = Mth.sin(this.rand.nextFloat() * 360.0F);
                tz = Mth.cos(this.rand.nextFloat() * 360.0F);
            } else if (type != 2 && type != 3) {
                if (type == 4) {
                    r *= Math.PI / 180.0;
                    tx = -Math.sin(r) + (this.rand.nextFloat() - 0.5) * 1.3;
                    tz = Math.cos(r) + (this.rand.nextFloat() - 0.5) * 1.3;
                } else if (type == 5) {
                    r *= Math.PI / 180.0;
                    tx = (-Math.sin(r) + (this.rand.nextFloat() - 0.5) * 0.9) * 0.3;
                    tz = (Math.cos(r) + (this.rand.nextFloat() - 0.5) * 0.9) * 0.3;
                }
            } else {
                if (i == 0) {
                    r += 90.0;
                }
                if (i == 1) {
                    r -= 90.0;
                }
                if (i == 2) {
                    r += 180.0;
                }
                r *= Math.PI / 180.0;
                tx = -Math.sin(r) + (this.rand.nextFloat() - 0.5) * 0.6;
                tz = Math.cos(r) + (this.rand.nextFloat() - 0.5) * 0.6;
            }
            tx += am.x;
            ty += am.y / 2.0;
            tz += am.z;
            if (type == 10) {
                r += (double) 360 / num / 2 + i * ((double) 360 / num);
                r *= Math.PI / 180.0;
                tx = -Math.sin(r) * 2.0;
                tz = Math.cos(r) * 2.0;
                ty = 0.7;
                y += 2.0;
                fuseCount = 10;
            }
            FlareEntity e = new FlareEntity(this.worldObj, x, y, z, tx * 0.5, ty * 0.5, tz * 0.5, 6.0F, fuseCount);
            e.setXRot(this.rand.nextFloat() * 360.0F);
            e.setYRot(this.rand.nextFloat() * 360.0F);
            e.xRotO = this.rand.nextFloat() * 360.0F;
            e.yRotO = this.rand.nextFloat() * 360.0F;
            if (type == 4) {
                e.gravity *= 0.6;
                e.airResistance = 0.995;
            }
            this.worldObj.addFreshEntity(e);
        }
    }
}
