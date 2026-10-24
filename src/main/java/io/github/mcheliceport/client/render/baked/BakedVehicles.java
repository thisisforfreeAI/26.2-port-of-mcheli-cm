package io.github.mcheliceport.client.render.baked;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.client.render.AircraftPartPoses;
import io.github.mcheliceport.config.ClientConfig;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.FlipFrameEvent;
import net.neoforged.neoforge.client.event.RegisterFeatureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.submit.RenderPhaseKeys;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Render path B: vehicle models drawn from vertex buffers uploaded once per model group, instead
 * of writing every triangle again each frame (path A, {@code ModelVertices}). Each part is one draw
 * with its transform, colour and light in a small uniform block; the image is path A's, drawn with
 * vanilla's entity fragment shader and lighting. Vehicles whose look path A changes per frame
 * (thermal vision, the far-vehicle pass, a glowing outline) and the emissive layers stay on path A,
 * as does everything when the client config turns it off or the pipeline fails.
 *
 * <p>Bodies are drawn with the solid entities, before anything translucent, so exhaust flames,
 * search light beams and other see-through geometry, which write no depth, land on top of them.
 * Upstream drew its models from display lists in the entity pass; quads with see-through texels are
 * therefore drawn unsorted and over what is solid, as upstream did, where path A sorts them among
 * the translucent geometry.
 */
public final class BakedVehicles {

    private static final Logger LOGGER = LoggerFactory.getLogger(BakedVehicles.class);

    static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
            .addAttribute("FlatNormal", GpuFormat.RGBA8_SNORM)
            .build();

    static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
            .withLocation(id("pipeline/baked_vehicle"))
            .withVertexShader(id("core/baked_vehicle"))
            .withFragmentShader("core/entity")
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("McheliPart", UniformType.UNIFORM_BUFFER).build())
            .withVertexBinding(0, FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();

    static final FeatureRendererType<Submit> TYPE = FeatureRendererType.create("mcheli_ce_port:baked_vehicle");

    static final int PART_UBO_SIZE = new Std140SizeCalculator().putMat4f().putMat4f().putVec4().putIVec2().putInt().get();

    static final BakedModelCache CACHE = new BakedModelCache();
    static DynamicUniformStorage<PartUniform> parts;
    private static boolean failed;

    private BakedVehicles() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterRenderPipelinesEvent event) -> event.registerPipeline(PIPELINE));
        NeoForge.EVENT_BUS.addListener((RegisterFeatureRenderersEvent event) ->
                event.register(TYPE, new BakedVehicleRenderer()));
        NeoForge.EVENT_BUS.addListener((FlipFrameEvent event) -> endFrame());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, path);
    }

    /** One part of a vehicle: where it is and which model groups it draws. */
    record Part(Matrix4f partMat, Matrix4f normalMat, List<McheliModel.Group> groups) {
    }

    /** A vehicle's body, drawn by {@link BakedVehicleRenderer}. */
    record Submit(Identifier texture, List<Part> parts, int color, int light, boolean flat, boolean surfaceShade)
            implements SubmitNode {
        @Override
        public FeatureRendererType<Submit> featureType() {
            return TYPE;
        }
    }

    /** Bit of {@code Flags}: light with the flat normal. */
    static final int FLAG_FLAT = 1;
    /** Bit of {@code Flags}: use the vertices' baked surface shade colours where they have one. */
    static final int FLAG_SURFACE_SHADE = 2;

    record PartUniform(Matrix4f partMat, Matrix4f normalMat, int color, int light, int flags)
            implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putMat4f(this.partMat)
                    .putMat4f(this.normalMat)
                    .putVec4((this.color >> 16 & 0xFF) / 255.0F, (this.color >> 8 & 0xFF) / 255.0F,
                            (this.color & 0xFF) / 255.0F, (this.color >>> 24) / 255.0F)
                    .putIVec2(this.light & 0xFFFF, this.light >>> 16 & 0xFFFF)
                    .putInt(this.flags);
        }
    }

    /** Whether path B is on and has not failed. */
    public static boolean enabled() {
        return !failed && ClientConfig.get(ClientConfig.GPU_MODEL_BUFFERS);
    }

    /**
     * Submits a vehicle's body to path B: each draw's groups with the draw's pose on top of the
     * entity's, with {@code ModelVertices#emit}'s flat and surface shade choices. Returns false,
     * submitting nothing, when path A must draw it.
     */
    public static boolean submit(SubmitNodeCollector collector, PoseStack poseStack, Identifier texture,
            List<AircraftPartPoses.Draw> draws, int color, int light, boolean flat, boolean surfaceShade) {
        // NeoForge's default submitSpecial drops the submit: only the vanilla collectors keep it.
        if (!enabled() || !(collector instanceof SubmitNodeStorage || collector instanceof SubmitNodeCollection)) {
            return false;
        }
        List<Part> list = new ArrayList<>(draws.size());
        for (AircraftPartPoses.Draw draw : draws) {
            PoseStack.Pose pose = poseStack.last().copy();
            pose.mulPose(draw.pose());
            list.add(new Part(new Matrix4f(pose.pose()), new Matrix4f().set(pose.normal()), draw.groups()));
        }
        collector.submitSpecial(RenderPhaseKeys.SOLID, new Submit(texture, list, color, light, flat, surfaceShade));
        return true;
    }

    static DynamicUniformStorage<PartUniform> parts() {
        if (parts == null) {
            parts = new DynamicUniformStorage<>("McHeli part UBO", PART_UBO_SIZE, 64);
        }
        return parts;
    }

    /** Turns path B off for the rest of the session after an error, which path A then covers. */
    static void fail(Throwable e) {
        if (!failed) {
            failed = true;
            LOGGER.error("Drawing vehicles from GPU buffers failed; drawing them as before from now on", e);
        }
    }

    private static void endFrame() {
        if (parts != null) {
            parts.endFrame();
        }
        if (failed || !ClientConfig.get(ClientConfig.GPU_MODEL_BUFFERS)) {
            CACHE.clear();
        } else {
            CACHE.endFrame();
        }
    }
}
