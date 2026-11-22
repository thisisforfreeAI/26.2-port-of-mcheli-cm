package io.github.mcheliceport.compat.jade;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.compat.VehicleTooltip;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Optional Jade support: looking at a helicopter, plane, ship, tank or vehicle shows its hull,
 * fuel, pilot, crew, team and damaged components. Jade finds this class by its annotation and,
 * on NeoForge, loads it only when Jade is installed. All values are synced already, so the plugin
 * is client-only and adds no server data.
 */
@WailaPlugin("jade")
public final class McheliJadePlugin implements IWailaPlugin {
    static final Identifier VEHICLE = Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "vehicle");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(VehicleProvider.INSTANCE, AircraftEntity.class);
    }

    enum VehicleProvider implements IEntityComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (accessor.getEntity() instanceof AircraftEntity vehicle) {
                for (Component line : VehicleTooltip.lines(vehicle)) {
                    tooltip.add(line);
                }
            }
        }

        @Override
        public Identifier getUid() {
            return VEHICLE;
        }
    }
}
