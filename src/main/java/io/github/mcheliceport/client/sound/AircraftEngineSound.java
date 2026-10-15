package io.github.mcheliceport.client.sound;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.util.McheliSounds;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * The looping engine and rotor sound of vehicles: upstream's {@code MCH_SoundUpdater}, run once a
 * tick for every vehicle the client sees. The volume follows the throttle, the pitch rises with
 * it and shifts while the vehicle passes a listener who is not aboard, and the sound is moved
 * towards the listener to give it the content's {@code SoundRange}.
 */
public final class AircraftEngineSound {

    private static final Map<AircraftEntity, AircraftEngineSound> UPDATERS = new WeakHashMap<>();

    private final AircraftEntity aircraft;
    private final String soundName;
    private Instance sound;
    private boolean isMoving;
    private boolean silent;
    private float aircraftPitch;
    private float aircraftVolume;
    private float addPitch;
    private double prevDist;
    private int soundDelay;

    private AircraftEngineSound(AircraftEntity aircraft, String soundName) {
        this.aircraft = aircraft;
        this.soundName = soundName;
        this.sound = new Instance(soundName);
    }

    /** The sound instance: its volume, pitch and position are set by the updater. */
    static final class Instance extends AbstractTickableSoundInstance {
        Instance(String name) {
            super(McheliSounds.event(name).value(), SoundSource.MASTER, RandomSource.create());
            this.looping = true;
        }

        void set(double x, double y, double z, float volume, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.volume = volume;
            this.pitch = pitch;
        }

        void setVolume(float volume) {
            this.volume = volume;
        }

        void setPitch(float pitch) {
            this.pitch = pitch;
        }

        void setPosition(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        void stopSound() {
            this.stop();
        }

        @Override
        public void tick() {
        }
    }

    /** Updates every vehicle's sound; called at the end of each client tick. */
    public static void tickAll() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            UPDATERS.values().forEach(u -> u.stop(mc));
            UPDATERS.clear();
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof AircraftEntity aircraft) {
                AircraftEngineSound updater = UPDATERS.get(aircraft);
                if (updater == null) {
                    String name = soundName(aircraft);
                    if (name.isEmpty()) {
                        continue;
                    }
                    updater = new AircraftEngineSound(aircraft, name);
                    UPDATERS.put(aircraft, updater);
                }
                updater.update(mc, player);
            }
        }
        UPDATERS.entrySet().removeIf(e -> {
            if (e.getKey().isRemoved() || e.getKey().level() != level) {
                e.getValue().stop(mc);
                return true;
            }
            return false;
        });
    }

    /** Upstream {@code getSoundName}: the content's {@code SoundMove}, else {@code heli} for helicopters. */
    static String soundName(AircraftEntity aircraft) {
        AircraftInfo info = aircraft.getAcInfo();
        if (info == null) {
            return "";
        }
        if (info.soundMove != null) {
            String move = info.soundMove;
            int colon = move.indexOf(':');
            return colon >= 0 ? move.substring(colon + 1) : move;
        }
        return aircraft instanceof io.github.mcheliceport.entity.aircraft.WingedAircraftEntity ? "plane"
                : aircraft instanceof HeliEntity ? "heli"
                : aircraft instanceof io.github.mcheliceport.entity.tank.TankEntity ? "prop" : "";
    }

    /** Upstream {@code MCH_EntityHeli#getSoundVolume}. */
    static float soundVolume(AircraftEntity aircraft) {
        AircraftInfo info = aircraft.getAcInfo();
        if (info != null && info.throttleUpDown <= 0.0F) {
            return 0.0F;
        }
        return aircraft instanceof io.github.mcheliceport.entity.tank.TankEntity tank ? tank.soundVolume * 0.7F
                : aircraft instanceof io.github.mcheliceport.entity.aircraft.WingedAircraftEntity plane ? plane.soundVolume * 0.7F
                : (float) aircraft.getCurrentThrottle() * 2.0F;
    }

    /** Upstream {@code MCH_EntityHeli#getSoundPitch}. */
    static float soundPitch(AircraftEntity aircraft) {
        return aircraft instanceof io.github.mcheliceport.entity.tank.TankEntity tank
                ? (float) Math.max(0.5 + tank.getCurrentThrottle() * 0.5, 0.5 + tank.soundTarget * 0.5)
                : aircraft instanceof io.github.mcheliceport.entity.vehicle.VehicleEntity
                ? (float) aircraft.getCurrentThrottle() * 0.5F
                : aircraft instanceof io.github.mcheliceport.entity.aircraft.WingedAircraftEntity
                ? (float) (0.6 + aircraft.getCurrentThrottle() * 0.4)
                : (float) (0.2 + aircraft.getCurrentThrottle() * 0.2);
    }

    private void stop(Minecraft mc) {
        this.sound.stopSound();
        mc.getSoundManager().stop(this.sound);
    }

    /** Upstream {@code MCH_SoundUpdater#update}. */
    private void update(Minecraft mc, LocalPlayer player) {
        AircraftInfo info = this.aircraft.getAcInfo();
        if (info == null) {
            return;
        }
        boolean isBeforeMoving = this.isMoving;
        if (!this.silent && this.aircraftVolume == 0.0F) {
            this.silent = true;
        }
        boolean isRide = this.aircraft.getSeatIdByEntity(player) >= 0;
        boolean isPlaying = mc.getSoundManager().isActive(this.sound);
        if (!isPlaying && this.aircraftVolume > 0.0F) {
            if (this.soundDelay > 0) {
                this.soundDelay--;
            } else {
                this.soundDelay = 20;
                if (this.sound.isStopped()) {
                    this.sound = new Instance(this.soundName);
                }
                this.sound.set(this.aircraft.getX(), this.aircraft.getY(), this.aircraft.getZ(), this.aircraftVolume,
                        this.aircraftPitch);
                mc.getSoundManager().play(this.sound);
            }
            this.silent = false;
        }
        float prevVolume = this.aircraftVolume;
        float prevPitch = this.aircraftPitch;
        this.isMoving = info.soundVolume * soundVolume(this.aircraft) >= 0.01;
        if (this.isMoving) {
            this.aircraftVolume = info.soundVolume * soundVolume(this.aircraft);
            this.aircraftPitch = info.soundPitch * soundPitch(this.aircraft);
            if (!isRide) {
                double dx = player.getX() - this.aircraft.getX();
                double dz = player.getZ() - this.aircraft.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                double pitch = this.prevDist - dist;
                if (Math.abs(pitch) > 0.3) {
                    this.addPitch = (float) (this.addPitch + pitch / 40.0);
                    float maxAddPitch = 0.2F;
                    if (this.addPitch < -maxAddPitch) {
                        this.addPitch = -maxAddPitch;
                    }
                    if (this.addPitch > maxAddPitch) {
                        this.addPitch = maxAddPitch;
                    }
                }
                this.addPitch = (float) (this.addPitch * 0.9);
                this.aircraftPitch = this.aircraftPitch + this.addPitch;
                this.prevDist = dist;
            }
            if (this.aircraftPitch < 0.0F) {
                this.aircraftPitch = 0.0F;
            }
        } else if (isBeforeMoving) {
            this.aircraftVolume = 0.0F;
            this.aircraftPitch = 0.0F;
        }
        if (!this.silent) {
            if (this.aircraftPitch != prevPitch) {
                this.sound.setPitch(this.aircraftPitch);
            }
            if (this.aircraftVolume != prevVolume) {
                this.sound.setVolume(this.aircraftVolume);
            }
        }
        if (this.aircraftVolume > 0.0F) {
            if (isRide) {
                this.sound.setPosition(this.aircraft.getX(), this.aircraft.getY(), this.aircraft.getZ());
            } else {
                double px = player.getX();
                double py = player.getY();
                double pz = player.getZ();
                double scale = info.soundRange / 16.0;
                this.sound.setPosition(px + (this.aircraft.getX() - px) / scale,
                        py + (this.aircraft.getY() - py) / scale, pz + (this.aircraft.getZ() - pz) / scale);
            }
        } else if (mc.getSoundManager().isActive(this.sound)) {
            this.stop(mc);
        }
    }
}
