package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exit criterion E4: the port's part transforms against upstream's GL call sequences. The oracle
 * transcribes {@code MCH_RenderHeli#renderAircraft}/{@code drawModelBlade} and the
 * {@code MCH_RenderAircraft#render*} methods with each {@code GlStateManager} call replaced by the
 * OpenGL 1.x matrix it multiplies by (in doubles), and records the matrix each part is drawn with.
 */
class PartTransformTest {

    private static ContentRegistries.Snapshot stock;

    @BeforeAll
    static void load() {
        stock = ContentTestSupport.load(ContentTestSupport.stockPack());
    }

    /** Every part resolves to a one-group model named after it, so every draw is recorded. */
    private static AircraftModels.VehicleModels everyPart() {
        Map<String, McheliModel> parts = new HashMap<>() {
            @Override
            public McheliModel get(Object key) {
                String name = (String) key;
                return new McheliModel(name, List.of(new McheliModel.Group(name, new float[0])), 0, 0, 0, 0, 0, 0);
            }
        };
        return new AircraftModels.VehicleModels(new McheliModel("body", List.of(), 0, 0, 0, 0, 0, 0), parts);
    }

    @Test
    void everyStockVehicleMatchesUpstream() {
        Map<String, Integer> covered = new TreeMap<>();
        int compared = 0;
        for (AircraftInfo info : stock.aircraft()) {
            Random random = new Random(info.name.hashCode());
            for (int sample = 0; sample < 8; sample++) {
                AircraftPartPoses.Input in = randomInput(info, random);
                List<Recorded> port = port(info, in);
                List<Recorded> upstream = new Upstream(in).render(info);
                compare(info.name + " sample " + sample, upstream, port);
                compared += port.size();
            }
            cover(info, covered);
        }
        // Each render path the test is meant to prove is exercised by some stock content.
        for (String feature : List.of("rotor new method", "canopy rotating", "canopy sliding",
                "hatch rotating", "hatch sliding", "landing gear", "landing gear reverse", "landing gear hatch",
                "landing gear rot2", "landing gear slide", "throttle", "rot part", "camera yaw", "camera pitch")) {
            assertTrue(covered.getOrDefault(feature, 0) > 0, "no stock content has a " + feature + ": " + covered);
        }
        assertTrue(compared > 1000, "compared " + compared + " draws");
    }

    @Test
    void bladeAnglesWrapAsUpstream() {
        HeliInfo info = stock.heli().get("ah-64");
        assertTrue(info != null && !info.rotorList.isEmpty(), "the AH-64 has rotors");
        AircraftPartPoses.Input base = randomInput(info, new Random(1));
        // A blade passing 360 -> 0 between ticks, and the other way round.
        float[][] rot = copy(base.bladeRotation());
        float[][] prev = copy(base.prevBladeRotation());
        rot[0][0] = 5.0F;
        prev[0][0] = 355.0F;
        rot[0][1] = 350.0F;
        prev[0][1] = 10.0F;
        AircraftPartPoses.Input in = withBlades(base, rot, prev);
        compare("ah-64 wrapping blades", new Upstream(in).render(info), port(info, in));
    }

    /** No stock helicopter uses {@code OldRenderMethod}; the AH-64's rotors are switched to it. */
    @Test
    void oldRenderMethodRotorsMatchUpstream() {
        HeliInfo info = ContentTestSupport.load(ContentTestSupport.stockPack()).heli().get("ah-64");
        assertTrue(info != null && !info.rotorList.isEmpty(), "the AH-64 has rotors");
        List<HeliInfo.Rotor> rotors = new ArrayList<>(info.rotorList);
        info.rotorList.clear();
        for (HeliInfo.Rotor r : rotors) {
            info.rotorList.add(new HeliInfo.Rotor(r, r.bladeNum, r.bladeRot, r.haveFoldFunc, true));
        }
        Random random = new Random(2);
        for (int sample = 0; sample < 16; sample++) {
            AircraftPartPoses.Input in = randomInput(info, random);
            compare("ah-64 old render method, sample " + sample, new Upstream(in).render(info), port(info, in));
        }
    }

    private static float[][] copy(float[][] a) {
        float[][] c = new float[a.length][];
        for (int i = 0; i < a.length; i++) {
            c[i] = a[i].clone();
        }
        return c;
    }

    private static AircraftPartPoses.Input withBlades(AircraftPartPoses.Input b, float[][] rot, float[][] prev) {
        return new AircraftPartPoses.Input(b.tickTime(), b.yaw(), b.pitch(), b.roll(), b.canopyRotation(),
                b.prevCanopyRotation(), b.canopyRotationMax(), b.hasCanopy(), b.hatchRotation(), b.prevHatchRotation(),
                b.hatchRotationMax(), b.hasHatch(), b.landingGearRotation(), b.prevLandingGearRotation(),
                b.hasLandingGear(), b.throttle(), b.prevThrottle(), b.rotPartRotation(), b.prevRotPartRotation(),
                b.cameraYaw(), b.cameraPitch(), false, rot, prev, b.weaponParts(), b.weaponBayRotation());
    }

    private static void cover(AircraftInfo info, Map<String, Integer> covered) {
        if (info instanceof HeliInfo heli) {
            for (HeliInfo.Rotor r : heli.rotorList) {
                covered.merge(r.oldRenderMethod ? "rotor old method" : "rotor new method", 1, Integer::sum);
            }
        }
        for (AircraftInfo.Canopy c : info.canopyList) {
            covered.merge(c.isSlide ? "canopy sliding" : "canopy rotating", 1, Integer::sum);
        }
        for (AircraftInfo.Hatch h : info.hatchList) {
            covered.merge(h.isSlide ? "hatch sliding" : "hatch rotating", 1, Integer::sum);
        }
        for (AircraftInfo.LandingGear n : info.landingGear) {
            covered.merge("landing gear", 1, Integer::sum);
            if (n.reverse) {
                covered.merge("landing gear reverse", 1, Integer::sum);
            }
            if (n.hatch) {
                covered.merge("landing gear hatch", 1, Integer::sum);
            }
            if (n.enableRot2) {
                covered.merge("landing gear rot2", 1, Integer::sum);
            }
            if (n.slide != null) {
                covered.merge("landing gear slide", 1, Integer::sum);
            }
        }
        if (!info.partThrottle.isEmpty()) {
            covered.merge("throttle", 1, Integer::sum);
        }
        if (!info.partRotPart.isEmpty()) {
            covered.merge("rot part", 1, Integer::sum);
        }
        for (AircraftInfo.Camera c : info.cameraList) {
            if (c.yawSync) {
                covered.merge("camera yaw", 1, Integer::sum);
            }
            if (c.pitchSync) {
                covered.merge("camera pitch", 1, Integer::sum);
            }
        }
    }

    private static AircraftPartPoses.Input randomInput(AircraftInfo info, Random r) {
        int rotors = info instanceof HeliInfo heli ? heli.rotorList.size() : 0;
        float[][] blades = new float[rotors][];
        float[][] prevBlades = new float[rotors][];
        for (int i = 0; i < rotors; i++) {
            int n = ((HeliInfo) info).rotorList.get(i).bladeNum;
            blades[i] = new float[n];
            prevBlades[i] = new float[n];
            for (int b = 0; b < n; b++) {
                blades[i][b] = r.nextFloat() * 360.0F;
                prevBlades[i][b] = blades[i][b] - r.nextFloat() * 40.0F;
                if (prevBlades[i][b] < 0.0F) {
                    prevBlades[i][b] += 360.0F;
                }
            }
        }
        int rotParts = info.partRotPart.size();
        float[] rotPart = new float[rotParts];
        float[] prevRotPart = new float[rotParts];
        for (int i = 0; i < rotParts; i++) {
            rotPart[i] = r.nextFloat() * 360.0F;
            prevRotPart[i] = r.nextFloat() * 360.0F;
        }
        float canopyMax = 1.0F + r.nextFloat() * 89.0F;
        float hatchMax = 1.0F + r.nextFloat() * 89.0F;
        return new AircraftPartPoses.Input(r.nextFloat(), r.nextFloat() * 360.0F - 180.0F,
                r.nextFloat() * 120.0F - 60.0F, r.nextFloat() * 120.0F - 60.0F,
                r.nextFloat() * canopyMax, r.nextFloat() * canopyMax, canopyMax, true,
                r.nextFloat() * hatchMax, r.nextFloat() * hatchMax, hatchMax, true,
                r.nextFloat() * 90.0F, r.nextFloat() * 90.0F, true,
                r.nextDouble(), r.nextDouble(), rotPart, prevRotPart,
                r.nextFloat() * 180.0F - 90.0F, r.nextFloat() * 60.0F - 30.0F, false, blades, prevBlades,
                new AircraftPartPoses.WeaponPartPose[0], new float[0]);
    }

    /** The port: the body stack, the blades, then the common parts this test checks. */
    private static List<Recorded> port(AircraftInfo info, AircraftPartPoses.Input in) {
        AircraftModels.VehicleModels models = everyPart();
        List<AircraftPartPoses.Draw> out = new ArrayList<>();
        GlMatrixStack gl = AircraftPartPoses.bodyStack(in);
        if (info instanceof HeliInfo heli) {
            AircraftPartPoses.drawModelBlade(heli, models, in, gl, out);
        }
        AircraftPartPoses.rotParts(info, models, in, gl, out);
        AircraftPartPoses.hatches(info, models, in, gl, out);
        AircraftPartPoses.throttles(info, models, in, gl, out);
        AircraftPartPoses.cameras(info, models, in, gl, out);
        AircraftPartPoses.landingGear(info, models, in, gl, out);
        AircraftPartPoses.canopies(info, models, in, gl, out);
        List<Recorded> recorded = new ArrayList<>();
        for (AircraftPartPoses.Draw d : out) {
            double[] m = new double[16];
            float[] f = new float[16];
            d.pose().get(f);
            for (int i = 0; i < 16; i++) {
                m[i] = f[i];
            }
            recorded.add(new Recorded(d.groups().getFirst().name(), m));
        }
        return recorded;
    }

    private static void compare(String what, List<Recorded> expected, List<Recorded> actual) {
        assertEquals(expected.size(), actual.size(), what + ": number of draws");
        for (int i = 0; i < expected.size(); i++) {
            Recorded e = expected.get(i);
            Recorded a = actual.get(i);
            assertEquals(e.part, a.part, what + ": draw " + i);
            for (int k = 0; k < 16; k++) {
                double tolerance = 2.0E-4 * Math.max(1.0, Math.abs(e.matrix[k]));
                if (Math.abs(e.matrix[k] - a.matrix[k]) > tolerance) {
                    fail(what + ": " + e.part + " element " + k + " expected " + Arrays.toString(e.matrix) + " got "
                            + Arrays.toString(a.matrix));
                }
            }
        }
    }

    record Recorded(String part, double[] matrix) {
    }

    /**
     * Upstream's render methods with {@code GlStateManager} replaced by a column-major double
     * matrix stack, multiplied on the right as OpenGL's fixed-function matrix calls did.
     */
    static final class Upstream {
        private final AircraftPartPoses.Input in;
        private final Deque<double[]> stack = new ArrayDeque<>();
        private double[] top = identity();
        private final List<Recorded> drawn = new ArrayList<>();

        Upstream(AircraftPartPoses.Input in) {
            this.in = in;
        }

        List<Recorded> render(AircraftInfo info) {
            // MCH_RenderHeli#renderAircraft (posX, posZ = 0; posY = GLOBAL_Y_OFFSET).
            this.translate(0.0, 0.35F, 0.0);
            this.rotate(this.in.yaw(), 0.0F, -1.0F, 0.0F);
            this.rotate(this.in.pitch(), 1.0F, 0.0F, 0.0F);
            this.rotate(this.in.roll(), 0.0F, 0.0F, 1.0F);
            if (info instanceof HeliInfo heli) {
                this.drawModelBlade(heli);
            }
            this.renderRotPart(info);
            this.renderHatch(info);
            this.renderThrottle(info);
            this.renderCamera(info);
            this.renderLandingGear(info);
            this.renderCanopy(info);
            return this.drawn;
        }

        private void drawModelBlade(HeliInfo info) {
            float tickTime = this.in.tickTime();
            for (int i = 0; i < this.in.bladeRotation().length && i < info.rotorList.size(); i++) {
                HeliInfo.Rotor rotorInfo = info.rotorList.get(i);
                this.pushMatrix();
                if (rotorInfo.oldRenderMethod) {
                    this.translate(rotorInfo.pos.x, rotorInfo.pos.y, rotorInfo.pos.z);
                }
                for (int b = 0; b < this.in.bladeRotation()[i].length; b++) {
                    this.pushMatrix();
                    float rot = this.in.bladeRotation()[i][b];
                    float prevRot = this.in.prevBladeRotation()[i][b];
                    if (rot - prevRot < -180.0F) {
                        prevRot -= 360.0F;
                    } else if (prevRot - rot < -180.0F) {
                        prevRot += 360.0F;
                    }
                    if (!rotorInfo.oldRenderMethod) {
                        this.translate(rotorInfo.pos.x, rotorInfo.pos.y, rotorInfo.pos.z);
                    }
                    this.rotate(prevRot + (rot - prevRot) * tickTime, (float) rotorInfo.rot.x,
                            (float) rotorInfo.rot.y, (float) rotorInfo.rot.z);
                    if (!rotorInfo.oldRenderMethod) {
                        this.translate(-rotorInfo.pos.x, -rotorInfo.pos.y, -rotorInfo.pos.z);
                    }
                    this.renderPart(rotorInfo.modelName);
                    this.popMatrix();
                }
                this.popMatrix();
            }
        }

        private void renderRotPart(AircraftInfo info) {
            float[] rotations = this.in.rotPartRotation();
            if (rotations.length == 0 || rotations.length != info.partRotPart.size()) {
                return;
            }
            for (int i = 0; i < rotations.length; i++) {
                float rot = rotations[i];
                float prevRot = this.in.prevRotPartRotation()[i];
                if (prevRot > rot) {
                    rot += 360.0F;
                }
                rot = prevRot + (rot - prevRot) * this.in.tickTime();
                AircraftInfo.RotPart h = info.partRotPart.get(i);
                this.pushMatrix();
                this.translate(h.pos.x, h.pos.y, h.pos.z);
                this.rotate(rot, (float) h.rot.x, (float) h.rot.y, (float) h.rot.z);
                this.translate(-h.pos.x, -h.pos.y, -h.pos.z);
                this.renderPart(h.modelName);
                this.popMatrix();
            }
        }

        private void renderHatch(AircraftInfo info) {
            if (info.hatchList.isEmpty() || !this.in.hasHatch()) {
                return;
            }
            float tickTime = this.in.tickTime();
            float rot = this.in.hatchRotation();
            float prevRot = this.in.prevHatchRotation();
            for (AircraftInfo.Hatch h : info.hatchList) {
                this.pushMatrix();
                if (h.isSlide) {
                    float r = this.in.hatchRotation() / this.in.hatchRotationMax();
                    float pr = this.in.prevHatchRotation() / this.in.hatchRotationMax();
                    float f = pr + (r - pr) * tickTime;
                    this.translate(h.pos.x * f, h.pos.y * f, h.pos.z * f);
                } else {
                    this.translate(h.pos.x, h.pos.y, h.pos.z);
                    this.rotate((prevRot + (rot - prevRot) * tickTime) * h.maxRotFactor, (float) h.rot.x,
                            (float) h.rot.y, (float) h.rot.z);
                    this.translate(-h.pos.x, -h.pos.y, -h.pos.z);
                }
                this.renderPart(h.modelName);
                this.popMatrix();
            }
        }

        private void renderThrottle(AircraftInfo info) {
            if (info.partThrottle.isEmpty()) {
                return;
            }
            float throttle = (float) this.in.prevThrottle()
                    + ((float) this.in.throttle() - (float) this.in.prevThrottle()) * this.in.tickTime();
            for (AircraftInfo.Throttle h : info.partThrottle) {
                this.pushMatrix();
                this.translate(h.pos.x, h.pos.y, h.pos.z);
                this.rotate(throttle * h.rot2, (float) h.rot.x, (float) h.rot.y, (float) h.rot.z);
                this.translate(-h.pos.x, -h.pos.y, -h.pos.z);
                this.translate(h.slide.x * throttle, h.slide.y * throttle, h.slide.z * throttle);
                this.renderPart(h.modelName);
                this.popMatrix();
            }
        }

        private void renderCamera(AircraftInfo info) {
            if (info.cameraList.isEmpty()) {
                return;
            }
            // Upstream subtracts the vehicle's yaw and pitch from the camera part's; the port's input
            // already holds the difference.
            float yaw = this.in.cameraYaw();
            float pitch = this.in.cameraPitch();
            for (AircraftInfo.Camera c : info.cameraList) {
                this.pushMatrix();
                this.translate(c.pos.x, c.pos.y, c.pos.z);
                if (c.yawSync) {
                    this.rotate(yaw, 0.0F, -1.0F, 0.0F);
                }
                if (c.pitchSync) {
                    this.rotate(pitch, 1.0F, 0.0F, 0.0F);
                }
                this.translate(-c.pos.x, -c.pos.y, -c.pos.z);
                this.renderPart(c.modelName);
                this.popMatrix();
            }
        }

        private void renderLandingGear(AircraftInfo info) {
            if (info.landingGear.isEmpty() || !this.in.hasLandingGear()) {
                return;
            }
            float tickTime = this.in.tickTime();
            float rot = this.in.landingGearRotation();
            float prevRot = this.in.prevLandingGearRotation();
            float revR = 90.0F - rot;
            float revPr = 90.0F - prevRot;
            float rot1 = prevRot + (rot - prevRot) * tickTime;
            float rot1Rev = revPr + (revR - revPr) * tickTime;
            float rotHatch = 90.0F * Mth.sin(rot1 * 2.0F * (float) Math.PI / 180.0F) * 3.0F;
            if (rotHatch > 90.0F) {
                rotHatch = 90.0F;
            }
            for (AircraftInfo.LandingGear n : info.landingGear) {
                this.pushMatrix();
                this.translate(n.pos.x, n.pos.y, n.pos.z);
                if (!n.reverse) {
                    if (!n.hatch) {
                        this.rotate(rot1 * n.maxRotFactor, (float) n.rot.x, (float) n.rot.y, (float) n.rot.z);
                    } else {
                        this.rotate(rotHatch * n.maxRotFactor, (float) n.rot.x, (float) n.rot.y, (float) n.rot.z);
                    }
                } else {
                    this.rotate(rot1Rev * n.maxRotFactor, (float) n.rot.x, (float) n.rot.y, (float) n.rot.z);
                }
                if (n.enableRot2) {
                    if (!n.reverse) {
                        this.rotate(rot1 * n.maxRotFactor2, (float) n.rot2.x, (float) n.rot2.y, (float) n.rot2.z);
                    } else {
                        this.rotate(rot1Rev * n.maxRotFactor2, (float) n.rot2.x, (float) n.rot2.y,
                                (float) n.rot2.z);
                    }
                }
                this.translate(-n.pos.x, -n.pos.y, -n.pos.z);
                if (n.slide != null) {
                    float f = rot / 90.0F;
                    if (n.reverse) {
                        f = 1.0F - f;
                    }
                    this.translate(f * n.slide.x, f * n.slide.y, f * n.slide.z);
                }
                this.renderPart(n.modelName);
                this.popMatrix();
            }
        }

        private void renderCanopy(AircraftInfo info) {
            if (info.canopyList.isEmpty() || !this.in.hasCanopy()) {
                return;
            }
            float tickTime = this.in.tickTime();
            float rot = this.in.canopyRotation();
            float prevRot = this.in.prevCanopyRotation();
            for (AircraftInfo.Canopy c : info.canopyList) {
                this.pushMatrix();
                if (c.isSlide) {
                    float r = this.in.canopyRotation() / this.in.canopyRotationMax();
                    float pr = this.in.prevCanopyRotation() / this.in.canopyRotationMax();
                    float f = pr + (r - pr) * tickTime;
                    this.translate(c.pos.x * f, c.pos.y * f, c.pos.z * f);
                } else {
                    this.translate(c.pos.x, c.pos.y, c.pos.z);
                    this.rotate((prevRot + (rot - prevRot) * tickTime) * c.maxRotFactor, (float) c.rot.x,
                            (float) c.rot.y, (float) c.rot.z);
                    this.translate(-c.pos.x, -c.pos.y, -c.pos.z);
                }
                this.renderPart(c.modelName);
                this.popMatrix();
            }
        }

        private void renderPart(String name) {
            this.drawn.add(new Recorded(name.toLowerCase(Locale.ROOT), this.top.clone()));
        }

        private void pushMatrix() {
            this.stack.push(this.top.clone());
        }

        private void popMatrix() {
            this.top = this.stack.pop();
        }

        /** {@code glTranslated}. */
        private void translate(double x, double y, double z) {
            double[] t = identity();
            t[12] = x;
            t[13] = y;
            t[14] = z;
            this.top = multiply(this.top, t);
        }

        /** {@code glRotatef}: the rotation matrix of the OpenGL 1.x specification; a zero axis is skipped. */
        private void rotate(float angle, float ax, float ay, float az) {
            double len = Math.sqrt((double) ax * ax + (double) ay * ay + (double) az * az);
            if (len == 0.0) {
                return;
            }
            double x = ax / len;
            double y = ay / len;
            double z = az / len;
            double rad = Math.toRadians(angle);
            double c = Math.cos(rad);
            double s = Math.sin(rad);
            double[] r = identity();
            r[0] = x * x * (1 - c) + c;
            r[1] = y * x * (1 - c) + z * s;
            r[2] = x * z * (1 - c) - y * s;
            r[4] = x * y * (1 - c) - z * s;
            r[5] = y * y * (1 - c) + c;
            r[6] = y * z * (1 - c) + x * s;
            r[8] = x * z * (1 - c) + y * s;
            r[9] = y * z * (1 - c) - x * s;
            r[10] = z * z * (1 - c) + c;
            this.top = multiply(this.top, r);
        }

        private static double[] identity() {
            double[] m = new double[16];
            m[0] = m[5] = m[10] = m[15] = 1.0;
            return m;
        }

        /** Column-major {@code a × b}. */
        private static double[] multiply(double[] a, double[] b) {
            double[] m = new double[16];
            for (int col = 0; col < 4; col++) {
                for (int row = 0; row < 4; row++) {
                    double sum = 0.0;
                    for (int k = 0; k < 4; k++) {
                        sum += a[k * 4 + row] * b[col * 4 + k];
                    }
                    m[col * 4 + row] = sum;
                }
            }
            return m;
        }
    }

    /** The oracle's matrix maths agrees with JOML on a plain rotation, so failures point at the port. */
    @Test
    void oracleMatchesJoml() {
        Upstream u = new Upstream(null);
        u.translate(1.0, 2.0, 3.0);
        u.rotate(30.0F, 1.0F, 2.0F, 0.5F);
        Matrix4f j = new Matrix4f().translate(1.0F, 2.0F, 3.0F)
                .rotate((float) Math.toRadians(30.0), new org.joml.Vector3f(1.0F, 2.0F, 0.5F).normalize());
        float[] f = new float[16];
        j.get(f);
        for (int i = 0; i < 16; i++) {
            assertEquals(f[i], u.top[i], 1.0E-6, "element " + i);
        }
    }
}
