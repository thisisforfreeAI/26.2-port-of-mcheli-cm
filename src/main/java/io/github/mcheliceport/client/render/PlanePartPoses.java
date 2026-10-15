package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.content.aircraft.AircraftInfo.DrawnPart;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import java.util.List;

/** MCP_RenderPlane's nozzle, wing/pylon and rotor/blade matrix sequences. */
public final class PlanePartPoses {
    private PlanePartPoses() {
    }

    public static void draw(PlaneInfo info, AircraftModels.VehicleModels models, AircraftPartPoses.Input input,
            float nozzle, float wing, float rotor, List<AircraftPartPoses.Draw> out) {
        GlMatrixStack gl = AircraftPartPoses.bodyStack(input);
        for (DrawnPart part : info.nozzles) {
            gl.pushMatrix();
            pivot(gl, part, nozzle);
            out.add(new AircraftPartPoses.Draw(gl.current(), models.part(part.modelName)));
            gl.popMatrix();
        }
        for (PlaneInfo.Wing part : info.wingList) {
            gl.pushMatrix();
            pivot(gl, part, wing * part.maxRotFactor);
            out.add(new AircraftPartPoses.Draw(gl.current(), models.part(part.modelName)));
            if (part.pylonList != null) {
                for (PlaneInfo.Pylon pylon : part.pylonList) {
                    gl.pushMatrix();
                    pivot(gl, pylon, wing * pylon.maxRotFactor);
                    out.add(new AircraftPartPoses.Draw(gl.current(), models.part(pylon.modelName)));
                    gl.popMatrix();
                }
            }
            gl.popMatrix();
        }
        for (PlaneInfo.Rotor part : info.rotorList) {
            gl.pushMatrix();
            pivot(gl, part, nozzle * part.maxRotFactor);
            out.add(new AircraftPartPoses.Draw(gl.current(), models.part(part.modelName)));
            for (PlaneInfo.Blade blade : part.blades) {
                gl.pushMatrix();
                pivot(gl, blade, rotor);
                for (int i = 0; i < blade.numBlade; i++) {
                    pivot(gl, blade, blade.rotBlade);
                    out.add(new AircraftPartPoses.Draw(gl.current(), models.part(blade.modelName)));
                }
                gl.popMatrix();
            }
            gl.popMatrix();
        }
        out.add(new AircraftPartPoses.Draw(gl.current(), models.body().body()));
        AircraftPartPoses.commonParts(info, models, input, gl, out);
    }

    private static void pivot(GlMatrixStack gl, DrawnPart part, float rotation) {
        gl.translate(part.pos.x, part.pos.y, part.pos.z);
        gl.rotate(rotation, (float) part.rot.x, (float) part.rot.y, (float) part.rot.z);
        gl.translate(-part.pos.x, -part.pos.y, -part.pos.z);
    }
}
