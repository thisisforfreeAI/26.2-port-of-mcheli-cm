package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Where each part of a vehicle is drawn: upstream's {@code MCH_RenderHeli#drawModelBlade} and
 * {@code MCH_RenderAircraft#renderCommonPart}, ported call for call onto a {@link GlMatrixStack}.
 *
 * <p>Pure computation from a {@link Input} snapshot, so {@code PartTransformTest} can check it.
 * Parts that need later milestones are drawn at rest: weapon parts (M5: no weapon aims or recoils
 * yet, and missiles always show), crawler tracks and track rollers (M6), light hatches (search
 * lights, M6), wheels and steering wheels (ground vehicles, M6).
 */
public final class AircraftPartPoses {

    /** One draw: groups of a model and the matrix to draw them with, relative to the entity origin. */
    public record Draw(Matrix4f pose, List<McheliModel.Group> groups) {
    }

    /** What the poses depend on, taken from the entity at a partial tick. */
    public record Input(
            float tickTime,
            float yaw,
            float pitch,
            float roll,
            float canopyRotation,
            float prevCanopyRotation,
            float canopyRotationMax,
            boolean hasCanopy,
            float hatchRotation,
            float prevHatchRotation,
            float hatchRotationMax,
            boolean hasHatch,
            float landingGearRotation,
            float prevLandingGearRotation,
            boolean hasLandingGear,
            double throttle,
            double prevThrottle,
            float[] rotPartRotation,
            float[] prevRotPartRotation,
            float cameraYaw,
            float cameraPitch,
            boolean hideGunnerWeapons,
            float[][] bladeRotation,
            float[][] prevBladeRotation) {
    }

    private AircraftPartPoses() {
    }

    /**
     * Upstream {@code renderAircraft} up to the body rotation: offset by
     * {@link AircraftInfo#GLOBAL_Y_OFFSET}, then turned by yaw, pitch and roll.
     */
    public static GlMatrixStack bodyStack(Input in) {
        GlMatrixStack gl = new GlMatrixStack();
        gl.translate(0.0, AircraftInfo.GLOBAL_Y_OFFSET, 0.0);
        gl.rotate(in.yaw, 0.0F, -1.0F, 0.0F);
        gl.rotate(in.pitch, 1.0F, 0.0F, 0.0F);
        gl.rotate(in.roll, 0.0F, 0.0F, 1.0F);
        return gl;
    }

    /** Every draw of a helicopter: body, blades, then the common parts, in upstream's order. */
    public static void heli(HeliInfo info, AircraftModels.VehicleModels models, Input in, List<Draw> out) {
        GlMatrixStack gl = bodyStack(in);
        out.add(new Draw(gl.current(), models.body().body()));
        drawModelBlade(info, models, in, gl, out);
        commonParts(info, models, in, gl, out);
    }

    /** Upstream {@code MCH_RenderHeli#drawModelBlade}. */
    static void drawModelBlade(HeliInfo info, AircraftModels.VehicleModels models, Input in, GlMatrixStack gl,
            List<Draw> out) {
        for (int i = 0; i < in.bladeRotation.length && i < info.rotorList.size(); i++) {
            HeliInfo.Rotor rotorInfo = info.rotorList.get(i);
            List<McheliModel.Group> groups = models.part(rotorInfo.modelName);
            gl.pushMatrix();
            if (rotorInfo.oldRenderMethod) {
                gl.translate(rotorInfo.pos.x, rotorInfo.pos.y, rotorInfo.pos.z);
            }
            for (int b = 0; b < in.bladeRotation[i].length; b++) {
                gl.pushMatrix();
                float rot = in.bladeRotation[i][b];
                float prevRot = in.prevBladeRotation[i][b];
                if (rot - prevRot < -180.0F) {
                    prevRot -= 360.0F;
                } else if (prevRot - rot < -180.0F) {
                    prevRot += 360.0F;
                }
                if (!rotorInfo.oldRenderMethod) {
                    gl.translate(rotorInfo.pos.x, rotorInfo.pos.y, rotorInfo.pos.z);
                }
                gl.rotate(prevRot + (rot - prevRot) * in.tickTime, (float) rotorInfo.rot.x, (float) rotorInfo.rot.y,
                        (float) rotorInfo.rot.z);
                if (!rotorInfo.oldRenderMethod) {
                    gl.translate(-rotorInfo.pos.x, -rotorInfo.pos.y, -rotorInfo.pos.z);
                }
                add(out, gl, groups);
                gl.popMatrix();
            }
            gl.popMatrix();
        }
    }

    /** Upstream {@code renderCommonPart}, without ropes, ERA tiles and crawler tracks. */
    static void commonParts(AircraftInfo info, AircraftModels.VehicleModels models, Input in, GlMatrixStack gl,
            List<Draw> out) {
        weapons(info, models, in, gl, out);
        rotParts(info, models, in, gl, out);
        hatches(info, models, in, gl, out);
        restParts(info.partTrackRoller, models, gl, out);
        restParts(info.partSteeringWheel, models, gl, out);
        lightHatches(info, models, gl, out);
        restParts(info.partWheel, models, gl, out);
        throttles(info, models, in, gl, out);
        cameras(info, models, in, gl, out);
        landingGear(info, models, in, gl, out);
        restParts(info.partWeaponBay, models, gl, out);
        canopies(info, models, in, gl, out);
    }

    private static void add(List<Draw> out, GlMatrixStack gl, List<McheliModel.Group> groups) {
        if (!groups.isEmpty()) {
            out.add(new Draw(gl.current(), groups));
        }
    }

    private static void restParts(List<? extends AircraftInfo.DrawnPart> parts, AircraftModels.VehicleModels models,
            GlMatrixStack gl, List<Draw> out) {
        for (AircraftInfo.DrawnPart p : parts) {
            add(out, gl, models.part(p.modelName));
        }
    }

    private static void lightHatches(AircraftInfo info, AircraftModels.VehicleModels models, GlMatrixStack gl,
            List<Draw> out) {
        // Upstream draws light hatches from the body only (their part files are never loaded).
        for (AircraftInfo.Hatch h : info.lightHatchList) {
            add(out, gl, models.body().part("$" + h.modelName));
        }
    }

    /**
     * Upstream {@code renderWeapon} with no weapon set: the parts at rest, the children at rest,
     * and {@code HideGM} parts hidden from a rider in first person.
     */
    static void weapons(AircraftInfo info, AircraftModels.VehicleModels models, Input in, GlMatrixStack gl,
            List<Draw> out) {
        for (AircraftInfo.PartWeapon part : info.partWeapon) {
            if (part.hideGM && in.hideGunnerWeapons) {
                continue;
            }
            add(out, gl, models.part(part.modelName));
            for (AircraftInfo.PartWeaponChild child : part.child) {
                add(out, gl, models.part(child.modelName));
            }
        }
    }

    /** Upstream {@code renderRotPart}. */
    static void rotParts(AircraftInfo info, AircraftModels.VehicleModels models, Input in, GlMatrixStack gl,
            List<Draw> out) {
        if (in.rotPartRotation.length == 0 || in.rotPartRotation.length != info.partRotPart.size()) {
            return;
        }
        for (int i = 0; i < in.rotPartRotation.length; i++) {
            float rot = in.rotPartRotation[i];
            float prevRot = in.prevRotPartRotation[i];
            if (prevRot > rot) {
                rot += 360.0F;
            }
            rot = smooth(rot, prevRot, in.tickTime);
            AircraftInfo.RotPart h = info.partRotPart.get(i);
            gl.pushMatrix();
            gl.translate(h.pos.x, h.pos.y, h.pos.z);
            gl.rotate(rot, (float) h.rot.x, (float) h.rot.y, (float) h.rot.z);
            gl.translate(-h.pos.x, -h.pos.y, -h.pos.z);
            add(out, gl, models.part(h.modelName));
            gl.popMatrix();
        }
    }

    /** Upstream {@code renderHatch}. */
    static void hatches(AircraftInfo info, AircraftModels.VehicleModels models, Input in, GlMatrixStack gl,
            List<Draw> out) {
        if (info.hatchList.isEmpty() || !in.hasHatch) {
            return;
        }
        float rot = in.hatchRotation;
        float prevRot = in.prevHatchRotation;
        for (AircraftInfo.Hatch h : info.hatchList) {
            gl.pushMatrix();
            if (h.isSlide) {
                float r = in.hatchRotation / in.hatchRotationMax;
                float pr = in.prevHatchRotation / in.hatchRotationMax;
                float f = pr + (r - pr) * in.tickTime;
                gl.translate(h.pos.x * f, h.pos.y * f, h.pos.z * f);
            } else {
                gl.translate(h.pos.x, h.pos.y, h.pos.z);
                gl.rotate((prevRot + (rot - prevRot) * in.tickTime) * h.maxRotFactor, (float) h.rot.x,
                        (float) h.rot.y, (float) h.rot.z);
                gl.translate(-h.pos.x, -h.pos.y, -h.pos.z);
            }
            add(out, gl, models.part(h.modelName));
            gl.popMatrix();
        }
    }

    /** Upstream {@code renderThrottle}. */
    static void throttles(AircraftInfo info, AircraftModels.VehicleModels models, Input in, GlMatrixStack gl,
            List<Draw> out) {
        if (info.partThrottle.isEmpty()) {
            return;
        }
        float throttle = smooth((float) in.throttle, (float) in.prevThrottle, in.tickTime);
        for (AircraftInfo.Throttle h : info.partThrottle) {
            gl.pushMatrix();
            gl.translate(h.pos.x, h.pos.y, h.pos.z);
            gl.rotate(throttle * h.rot2, (float) h.rot.x, (float) h.rot.y, (float) h.rot.z);
            gl.translate(-h.pos.x, -h.pos.y, -h.pos.z);
            gl.translate(h.slide.x * throttle, h.slide.y * throttle, h.slide.z * throttle);
            add(out, gl, models.part(h.modelName));
            gl.popMatrix();
        }
    }

    /** Upstream {@code renderCamera}, with the camera turned towards the gunner's (or pilot's) view. */
    static void cameras(AircraftInfo info, AircraftModels.VehicleModels models, Input in, GlMatrixStack gl,
            List<Draw> out) {
        for (AircraftInfo.Camera c : info.cameraList) {
            gl.pushMatrix();
            gl.translate(c.pos.x, c.pos.y, c.pos.z);
            if (c.yawSync) {
                gl.rotate(in.cameraYaw, 0.0F, -1.0F, 0.0F);
            }
            if (c.pitchSync) {
                gl.rotate(in.cameraPitch, 1.0F, 0.0F, 0.0F);
            }
            gl.translate(-c.pos.x, -c.pos.y, -c.pos.z);
            add(out, gl, models.part(c.modelName));
            gl.popMatrix();
        }
    }

    /** Upstream {@code renderLandingGear}. */
    static void landingGear(AircraftInfo info, AircraftModels.VehicleModels models, Input in, GlMatrixStack gl,
            List<Draw> out) {
        if (info.landingGear.isEmpty() || !in.hasLandingGear) {
            return;
        }
        float rot = in.landingGearRotation;
        float prevRot = in.prevLandingGearRotation;
        float revR = 90.0F - rot;
        float revPr = 90.0F - prevRot;
        float rot1 = prevRot + (rot - prevRot) * in.tickTime;
        float rot1Rev = revPr + (revR - revPr) * in.tickTime;
        float rotHatch = 90.0F * Mth.sin(rot1 * 2.0F * (float) Math.PI / 180.0F) * 3.0F;
        if (rotHatch > 90.0F) {
            rotHatch = 90.0F;
        }
        for (AircraftInfo.LandingGear n : info.landingGear) {
            gl.pushMatrix();
            gl.translate(n.pos.x, n.pos.y, n.pos.z);
            if (!n.reverse) {
                if (!n.hatch) {
                    gl.rotate(rot1 * n.maxRotFactor, (float) n.rot.x, (float) n.rot.y, (float) n.rot.z);
                } else {
                    gl.rotate(rotHatch * n.maxRotFactor, (float) n.rot.x, (float) n.rot.y, (float) n.rot.z);
                }
            } else {
                gl.rotate(rot1Rev * n.maxRotFactor, (float) n.rot.x, (float) n.rot.y, (float) n.rot.z);
            }
            if (n.enableRot2) {
                Vec3 r2 = n.rot2;
                if (!n.reverse) {
                    gl.rotate(rot1 * n.maxRotFactor2, (float) r2.x, (float) r2.y, (float) r2.z);
                } else {
                    gl.rotate(rot1Rev * n.maxRotFactor2, (float) r2.x, (float) r2.y, (float) r2.z);
                }
            }
            gl.translate(-n.pos.x, -n.pos.y, -n.pos.z);
            if (n.slide != null) {
                float f = rot / 90.0F;
                if (n.reverse) {
                    f = 1.0F - f;
                }
                gl.translate(f * n.slide.x, f * n.slide.y, f * n.slide.z);
            }
            add(out, gl, models.part(n.modelName));
            gl.popMatrix();
        }
    }

    /** Upstream {@code renderCanopy}. */
    static void canopies(AircraftInfo info, AircraftModels.VehicleModels models, Input in, GlMatrixStack gl,
            List<Draw> out) {
        if (info.canopyList.isEmpty() || !in.hasCanopy) {
            return;
        }
        float rot = in.canopyRotation;
        float prevRot = in.prevCanopyRotation;
        for (AircraftInfo.Canopy c : info.canopyList) {
            gl.pushMatrix();
            if (c.isSlide) {
                float r = in.canopyRotation / in.canopyRotationMax;
                float pr = in.prevCanopyRotation / in.canopyRotationMax;
                float f = pr + (r - pr) * in.tickTime;
                gl.translate(c.pos.x * f, c.pos.y * f, c.pos.z * f);
            } else {
                gl.translate(c.pos.x, c.pos.y, c.pos.z);
                gl.rotate((prevRot + (rot - prevRot) * in.tickTime) * c.maxRotFactor, (float) c.rot.x,
                        (float) c.rot.y, (float) c.rot.z);
                gl.translate(-c.pos.x, -c.pos.y, -c.pos.z);
            }
            add(out, gl, models.part(c.modelName));
            gl.popMatrix();
        }
    }

    /** Upstream {@code MCH_Lib.smooth}. */
    static float smooth(float rot, float prevRot, float tickTime) {
        return prevRot + (rot - prevRot) * tickTime;
    }
}
