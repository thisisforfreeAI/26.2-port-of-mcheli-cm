package io.github.mcheliceport.client;

import io.github.mcheliceport.platform.ClientEvents;
import io.github.mcheliceport.platform.HudLayers;

/**
 * The client's listeners on {@link ClientEvents}, for both loaders' client entry points. (On
 * NeoForge these were {@code @EventBusSubscriber} classes, which Fabric has no equivalent of.)
 */
public final class ClientSetup {

    private ClientSetup() {
    }

    public static void registerEvents() {
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.effect.BlastMarks::tick);
        ClientEvents.SUBMIT_GEOMETRY.add(io.github.mcheliceport.client.effect.BlastMarks::submit);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.effect.BloodStains::tick);
        ClientEvents.SUBMIT_GEOMETRY.add(io.github.mcheliceport.client.effect.BloodStains::submit);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.effect.FluidStains::tick);
        ClientEvents.SUBMIT_GEOMETRY.add(io.github.mcheliceport.client.effect.FluidStains::submit);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.effect.TrackMarks::tick);
        ClientEvents.SUBMIT_GEOMETRY.add(io.github.mcheliceport.client.effect.TrackMarks::submit);
        ClientEvents.ENTITY_TICK_POST.add(io.github.mcheliceport.client.effect.VehicleTracks::entityTick);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.effect.WreckGround::tick);
        ClientEvents.SUBMIT_GEOMETRY.add(io.github.mcheliceport.client.effect.WreckGround::submit);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.hud.CockpitFeedbackLayer::tick);
        ClientEvents.RENDER_FRAME_PRE.add(io.github.mcheliceport.client.particle.EffectPerformance::frame);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.particle.SupplyRangeRings::tick);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.particle.VehicleAtmosphere::tick);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.particle.VehicleBloodEffects::tick);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.particle.VehicleShrapnel::tick);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.particle.VehicleSurfaceBuildup::tick);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.particle.VehicleWaterEffects::tick);
        ClientEvents.SUBMIT_GEOMETRY.add(io.github.mcheliceport.client.particle.VehicleWaterEffects::submit);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.particle.VehicleWeaponHeat::tick);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.particle.VehicleWeather::tick);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.particle.WeaponGroundEffects::tick);
        ClientEvents.SUBMIT_GEOMETRY.add(io.github.mcheliceport.client.particle.WeaponGroundEffects::submit);
        ClientEvents.RENDER_FRAME_PRE.add(io.github.mcheliceport.client.render.AircraftMarkings::frame);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.render.EngineHeat::tick);
        ClientEvents.RENDER_FRAME_PRE.add(io.github.mcheliceport.client.render.FlightLighting::frame);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.render.LandingLightGround::tick);
        ClientEvents.SUBMIT_GEOMETRY.add(io.github.mcheliceport.client.render.LandingLightGround::submit);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.render.LoosePanels::tick);
        ClientEvents.SUBMIT_GEOMETRY.add(io.github.mcheliceport.client.render.LoosePanels::submit);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.render.SceneLights::tick);
        ClientEvents.SUBMIT_GEOMETRY.add(io.github.mcheliceport.client.render.SceneLights::submit);
        ClientEvents.RENDER_FRAME_PRE.add(io.github.mcheliceport.client.render.VehicleDetailLayers::frame);
        ClientEvents.RENDER_FRAME_PRE.add(io.github.mcheliceport.client.render.VehicleImpactMarks::frame);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.render.WreckDebrisField::tick);
        ClientEvents.SUBMIT_GEOMETRY.add(io.github.mcheliceport.client.render.WreckDebrisField::submit);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.render.WreckScene::tick);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.sound.ExplosionAudio::tick);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.render.FarVehicles::onClientTick);
        ClientEvents.TICK_POST.add(io.github.mcheliceport.client.particle.ProjectileTrailEffects::lifecycle);
        ClientEvents.SCREEN_INIT_POST.add(io.github.mcheliceport.client.gui.EffectsPauseMenu::init);
        ClientEvents.LEVEL_EXTRACTED.add(io.github.mcheliceport.client.particle.EntityGlowAdmission::extracted);
        ClientEvents.AFTER_LEVEL.add(io.github.mcheliceport.client.render.EngineHeat::render);
        ClientEvents.REGISTER_HUD_LAYERS.add(ClientSetup::registerHudLayers);
    }

    /** The HUD layers; the ones placed against the aircraft HUD come after it. */
    private static void registerHudLayers(HudLayers layers) {
        io.github.mcheliceport.client.hud.AircraftHudLayer.register(layers);
        io.github.mcheliceport.client.hud.ToolHudLayer.register(layers);
        io.github.mcheliceport.client.hud.SpawnGunnerHudLayer.register(layers);
        io.github.mcheliceport.client.hud.InteractSeatHudLayer.register(layers);
        io.github.mcheliceport.client.hud.LockMarkerHudLayer.register(layers);
        io.github.mcheliceport.client.hud.RwrHudLayer.register(layers);
        io.github.mcheliceport.client.hud.BvrHudLayer.register(layers);
        io.github.mcheliceport.client.hud.MortarRadarHudLayer.register(layers);
        io.github.mcheliceport.client.hud.GpsHudLayer.register(layers);
        io.github.mcheliceport.client.hud.WingmanKeyHintLayer.register(layers);
        io.github.mcheliceport.client.hud.ComponentStatusLayer.register(layers);
        io.github.mcheliceport.client.hud.CrewHudLayer.register(layers);
        io.github.mcheliceport.client.hud.GroundCrewHudLayer.register(layers);
        io.github.mcheliceport.client.hud.WeatherHudLayer.register(layers);
        io.github.mcheliceport.client.effect.EffectsRangeClient.registerGui(layers);
        io.github.mcheliceport.client.hud.CockpitFeedbackLayer.register(layers);
        io.github.mcheliceport.client.hud.TeamListOverlay.register(layers);
        io.github.mcheliceport.client.hud.GltdHudLayer.register(layers);
        io.github.mcheliceport.client.hud.LightWeaponHudLayer.register(layers);
        io.github.mcheliceport.client.hud.TitleOverlay.register(layers);
    }
}
