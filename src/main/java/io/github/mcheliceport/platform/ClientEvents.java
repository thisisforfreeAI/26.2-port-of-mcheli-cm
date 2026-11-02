package io.github.mcheliceport.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.platform.GameEvents.Listeners;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Loader-neutral client events, the client side of {@link GameEvents}: the shared client code
 * listens here, and each loader's client entry point fires them (NeoForge: {@code NeoForgeClientEvents},
 * Fabric: {@code FabricClientEvents}). Registration events hand the listener a registrar that the
 * loader backs at its registration time.
 */
public final class ClientEvents {

    /** After the client's tick. */
    public static final Listeners<Runnable> TICK_POST = new Listeners<>();
    /** Before and after the client renders a frame. */
    public static final Listeners<Runnable> RENDER_FRAME_PRE = new Listeners<>();
    public static final Listeners<Runnable> RENDER_FRAME_POST = new Listeners<>();
    /** The local player joined or left a world (server). */
    public static final Listeners<Runnable> LOGGING_IN = new Listeners<>();
    public static final Listeners<Runnable> LOGGING_OUT = new Listeners<>();
    /** After an entity of the client level ticked. */
    public static final Listeners<Consumer<Entity>> ENTITY_TICK_POST = new Listeners<>();
    /** While the level renders: submit custom geometry for this frame. */
    public static final Listeners<Consumer<Geometry>> SUBMIT_GEOMETRY = new Listeners<>();
    /** The camera's angles for this frame; listeners may change them. */
    public static final Listeners<Consumer<CameraAngles>> CAMERA_ANGLES = new Listeners<>();
    /** The field of view for this frame; listeners may change it. */
    public static final Listeners<Consumer<Fov>> COMPUTE_FOV = new Listeners<>();
    /** The third-person camera's distance; listeners may change it. */
    public static final Listeners<Consumer<DetachedDistance>> DETACHED_CAMERA_DISTANCE = new Listeners<>();
    /** The first-person hand is about to render; a listener may cancel it. */
    public static final Listeners<Consumer<Cancelable>> RENDER_HAND = new Listeners<>();
    /** A vanilla HUD layer is about to render; a listener may cancel it. */
    public static final Listeners<Consumer<GuiLayerRender>> RENDER_GUI_LAYER = new Listeners<>();
    /** Whether an entity's name tag renders; a listener may hide it. */
    public static final Listeners<Consumer<NameTag>> NAME_TAG = new Listeners<>();
    /** A key was pressed or released in the world (no screen open). */
    public static final Listeners<Consumer<KeyInput>> KEY = new Listeners<>();
    /** The attack or use key fired; a listener may cancel it. */
    public static final Listeners<Consumer<InteractionKey>> INTERACTION_KEY = new Listeners<>();
    /** The mouse wheel turned in the world; a listener may cancel it. */
    public static final Listeners<Consumer<MouseScroll>> MOUSE_SCROLL = new Listeners<>();
    /** A screen finished its init; listeners may add widgets. */
    public static final Listeners<Consumer<ScreenInit>> SCREEN_INIT_POST = new Listeners<>();
    /** The level's render state was extracted for this frame. */
    public static final Listeners<Consumer<net.minecraft.client.multiplayer.ClientLevel>> LEVEL_EXTRACTED = new Listeners<>();
    /** After the level rendered (before the hand and the HUD). */
    public static final Listeners<Consumer<AfterLevel>> AFTER_LEVEL = new Listeners<>();
    /** Registers HUD layers. */
    public static final Listeners<Consumer<HudLayers>> REGISTER_HUD_LAYERS = new Listeners<>();
    /** After the frame was presented (NeoForge's {@code FlipFrameEvent}). */
    public static final Listeners<Runnable> FLIP_FRAME = new Listeners<>();
    /** A living entity's render state was extracted; listeners may attach {@code RenderData} to it. */
    public static final Listeners<LivingState> LIVING_RENDER_STATE = new Listeners<>();
    /** Before and after a living entity submits; a listener may cancel the submit (and its post). */
    public static final Listeners<Consumer<RenderLiving>> RENDER_LIVING_PRE = new Listeners<>();
    public static final Listeners<Consumer<RenderLiving>> RENDER_LIVING_POST = new Listeners<>();

    // Registration: each loader fires these once, at its registration time.
    public static final Listeners<Consumer<ClientRegistrars.Pipelines>> REGISTER_PIPELINES = new Listeners<>();
    public static final Listeners<Consumer<ClientRegistrars.ParticleGroups>> REGISTER_PARTICLE_GROUPS = new Listeners<>();
    public static final Listeners<Consumer<ClientRegistrars.KeyMappings>> REGISTER_KEY_MAPPINGS = new Listeners<>();
    public static final Listeners<Consumer<ClientRegistrars.Renderers>> REGISTER_RENDERERS = new Listeners<>();
    public static final Listeners<Consumer<ClientRegistrars.LayerDefinitions>> REGISTER_LAYER_DEFINITIONS = new Listeners<>();
    public static final Listeners<Consumer<ClientRegistrars.MenuScreenRegistrar>> REGISTER_MENU_SCREENS = new Listeners<>();
    public static final Listeners<Consumer<ClientRegistrars.ItemModels>> REGISTER_ITEM_MODELS = new Listeners<>();
    public static final Listeners<Consumer<ClientRegistrars.PictureInPicture>> REGISTER_PICTURE_IN_PICTURE = new Listeners<>();
    public static final Listeners<Consumer<ClientRegistrars.ReloadListeners>> ADD_RELOAD_LISTENERS = new Listeners<>();
    public static final Listeners<Consumer<ClientRegistrars.FeatureRenderers>> REGISTER_FEATURE_RENDERERS = new Listeners<>();
    public static final Listeners<Consumer<ClientRegistrars.FluidModels>> REGISTER_FLUID_MODELS = new Listeners<>();

    private ClientEvents() {
    }

    /** The level being rendered, and where to submit geometry for it (NeoForge's {@code SubmitCustomGeometryEvent}). */
    public record Geometry(LevelRenderState levelRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        public LevelRenderState getLevelRenderState() {
            return levelRenderState;
        }

        public PoseStack getPoseStack() {
            return poseStack;
        }

        public SubmitNodeCollector getSubmitNodeCollector() {
            return submitNodeCollector;
        }
    }

    /** NeoForge's {@code ViewportEvent.ComputeCameraAngles}. */
    public static final class CameraAngles {
        private final net.minecraft.client.Camera camera;
        private final double partialTick;
        private float yaw;
        private float pitch;
        private float roll;

        public CameraAngles(net.minecraft.client.Camera camera, double partialTick, float yaw, float pitch, float roll) {
            this.camera = camera;
            this.partialTick = partialTick;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        public net.minecraft.client.Camera getCamera() { return camera; }
        public double getPartialTick() { return partialTick; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public float getRoll() { return roll; }
        public void setYaw(float yaw) { this.yaw = yaw; }
        public void setPitch(float pitch) { this.pitch = pitch; }
        public void setRoll(float roll) { this.roll = roll; }
    }

    /** NeoForge's {@code ViewportEvent.ComputeFov}. */
    public static final class Fov {
        private final net.minecraft.client.Camera camera;
        private final double partialTick;
        private double fov;

        public Fov(net.minecraft.client.Camera camera, double partialTick, double fov) {
            this.camera = camera;
            this.partialTick = partialTick;
            this.fov = fov;
        }

        public net.minecraft.client.Camera getCamera() { return camera; }
        public double getPartialTick() { return partialTick; }
        public double getFOV() { return fov; }
        public void setFOV(double fov) { this.fov = fov; }
    }

    /** NeoForge's {@code CalculateDetachedCameraDistanceEvent}. */
    public static final class DetachedDistance {
        private float distance;

        public DetachedDistance(float distance) { this.distance = distance; }
        public float getDistance() { return distance; }
        public void setDistance(float distance) { this.distance = distance; }
    }

    /** An event a listener may cancel. */
    public static class Cancelable {
        private boolean canceled;

        public boolean isCanceled() { return canceled; }
        public void setCanceled(boolean canceled) { this.canceled = canceled; }
    }

    /** NeoForge's {@code RenderGuiLayerEvent.Pre}. */
    public static final class GuiLayerRender extends Cancelable {
        private final net.minecraft.resources.Identifier name;

        public GuiLayerRender(net.minecraft.resources.Identifier name) { this.name = name; }
        /** The layer, by NeoForge's {@code VanillaGuiLayers} name. */
        public net.minecraft.resources.Identifier getName() { return name; }
    }

    /** NeoForge's {@code RenderNameTagEvent.CanRender}. */
    public static final class NameTag {
        private final Entity entity;
        private boolean hidden;

        public NameTag(Entity entity) { this.entity = entity; }
        public Entity getEntity() { return entity; }
        public void hide() { hidden = true; }
        public boolean hidden() { return hidden; }
    }

    /** NeoForge's {@code InputEvent.Key}. */
    public record KeyInput(net.minecraft.client.input.KeyEvent keyEvent, int action) {
        public net.minecraft.client.input.KeyEvent getKeyEvent() { return keyEvent; }
        public int getAction() { return action; }
    }

    /** NeoForge's {@code InputEvent.InteractionKeyMappingTriggered}. */
    public static final class InteractionKey extends Cancelable {
        private final boolean attack;
        private final boolean useItem;
        private boolean swingHand = true;

        public InteractionKey(boolean attack, boolean useItem) {
            this.attack = attack;
            this.useItem = useItem;
        }

        public boolean isAttack() { return attack; }
        public boolean isUseItem() { return useItem; }
        public boolean shouldSwingHand() { return swingHand; }
        public void setSwingHand(boolean swingHand) { this.swingHand = swingHand; }
    }

    /** NeoForge's {@code InputEvent.MouseScrollingEvent}. */
    public static final class MouseScroll extends Cancelable {
        private final double scrollDeltaY;

        public MouseScroll(double scrollDeltaY) { this.scrollDeltaY = scrollDeltaY; }
        public double getScrollDeltaY() { return scrollDeltaY; }
    }

    /** NeoForge's {@code ScreenEvent.Init.Post}. */
    public record ScreenInit(net.minecraft.client.gui.screens.Screen screen,
            java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> listeners,
            Consumer<net.minecraft.client.gui.components.events.GuiEventListener> adder) {
        public net.minecraft.client.gui.screens.Screen getScreen() { return screen; }
        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> getListenersList() {
            return listeners;
        }
        public void addListener(net.minecraft.client.gui.components.events.GuiEventListener listener) {
            adder.accept(listener);
        }
    }

    /** NeoForge's {@code RenderLevelStageEvent.AfterLevel}. */
    public record AfterLevel(LevelRenderState levelRenderState, org.joml.Matrix4fc modelViewMatrix) {
        public LevelRenderState getLevelRenderState() { return levelRenderState; }
        public org.joml.Matrix4fc getModelViewMatrix() { return modelViewMatrix; }
    }

    /** A listener on {@link #LIVING_RENDER_STATE}. */
    @FunctionalInterface
    public interface LivingState {
        void accept(net.minecraft.world.entity.LivingEntity entity,
                net.minecraft.client.renderer.entity.state.LivingEntityRenderState state, float partialTick);
    }

    /** NeoForge's {@code RenderLivingEvent.Pre} and {@code Post}. */
    public static final class RenderLiving extends Cancelable {
        private final net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> renderer;
        private final net.minecraft.client.renderer.entity.state.LivingEntityRenderState state;
        private final PoseStack poseStack;
        private final SubmitNodeCollector collector;

        public RenderLiving(net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> renderer,
                net.minecraft.client.renderer.entity.state.LivingEntityRenderState state, PoseStack poseStack,
                SubmitNodeCollector collector) {
            this.renderer = renderer;
            this.state = state;
            this.poseStack = poseStack;
            this.collector = collector;
        }

        public net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> getRenderer() { return renderer; }
        public net.minecraft.client.renderer.entity.state.LivingEntityRenderState getRenderState() { return state; }
        public PoseStack getPoseStack() { return poseStack; }
        public SubmitNodeCollector getSubmitNodeCollector() { return collector; }
    }

    // ---- firing, for the loaders ----

    public static void run(Listeners<Runnable> event) {
        event.forEach(Runnable::run);
    }

    public static <T> void fire(Listeners<Consumer<T>> event, T value) {
        event.forEach(listener -> listener.accept(value));
    }
}
