package io.github.mcheliceport.entity.ground;

import io.github.mcheliceport.content.aircraft.AircraftInfo;

/** Client wheel and left/right crawler state from MCH_EntityAircraft#updatePartWheel/CrawlerTrack. */
public final class GroundAnimation {
    public float wheel, previousWheel;
    public final float[] roller = new float[2], previousRoller = new float[2];
    public final float[] track = new float[2], previousTrack = new float[2], throttle = new float[2];

    public void tick(AircraftInfo info, double power, float reverse, boolean left, boolean right) {
        this.previousWheel = this.wheel;
        double wheelPower = info.enableBack && reverse > 0.01 && power <= 0 ? -reverse * 15 : power;
        this.wheel += (float) (wheelPower * info.partWheelRot);
        if (this.wheel >= 360) { this.wheel -= 360; this.previousWheel -= 360; }
        else if (this.wheel < 0) { this.wheel += 360; this.previousWheel += 360; }
        int dir = 1;
        if (info.enableBack && reverse > 0 && power <= 0) {
            power = -reverse * 5;
            if (left != right) { boolean tmp = left; left = right; right = tmp; dir = -1; }
        }
        double pivot = info.pivotTurnThrottle <= 0 ? 1 : info.pivotTurnThrottle * 0.1;
        if (left != right) {
            this.throttle[left ? 0 : 1] += (float) (0.2 * dir);
            this.throttle[left ? 1 : 0] -= (float) (pivot * 0.2 * dir);
        } else {
            this.throttle[0] += (float) Math.clamp(power, -0.2, 0.2);
            this.throttle[1] += (float) Math.clamp(power, -0.2, 0.2);
        }
        for (int i = 0; i < 2; i++) {
            this.previousRoller[i] = this.roller[i]; this.previousTrack[i] = this.track[i];
            float t = Math.clamp(this.throttle[i], -0.72F, 0.72F);
            this.roller[i] += t * info.trackRollerRot;
            if (this.roller[i] >= 360) { this.roller[i] -= 360; this.previousRoller[i] -= 360; }
            else if (this.roller[i] < 0) { this.roller[i] += 360; this.previousRoller[i] += 360; }
            this.track[i] -= t;
            while (this.track[i] >= 1) { this.track[i]--; this.previousTrack[i]--; }
            while (this.track[i] < 0) { this.track[i]++; }
            while (this.previousTrack[i] < 0) { this.previousTrack[i]++; }
            this.throttle[i] = t * 0.75F;
        }
    }
}
