package io.github.mcheliceport.gametest;

import io.github.mcheliceport.McheliCePort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's GameTests (docs/M3_PLAN.md section 8.3), run by {@code ./gradlew runGameTestServer}
 * and in-game with {@code /test}. Every test runs in {@code mcheli_ce_port:flight_area}, a
 * 24 × 48 × 24 space with a stone floor at relative y 0 (written by
 * {@code scripts/make-test-structure.py}).
 *
 * <p>The test functions are registered always (an unused registry entry costs nothing); the test
 * instances only when the game asks for them, which it does only with GameTests enabled.
 */
public final class ModGameTests {

    static final Identifier STRUCTURE = id("flight_area");

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, McheliCePort.MOD_ID);

    /** {@code batch} names the environment, and so the batch, the test runs in. */
    private record Spec(String name, int maxTicks, ResourceKey<Consumer<GameTestHelper>> function, String batch) {
    }

    private static final List<Spec> SPECS = new ArrayList<>();

    private ModGameTests() {
    }

    public static void register(IEventBus modBus) {
        PlacementGameTests.register();
        RidingGameTests.register();
        CameraModeGameTests.register();
        FlightGameTests.register();
        FuelGameTests.register();
        SupportGameTests.register();
        CargoGameTests.register();
        RackGameTests.register();
        ChainGameTests.register();
        RopeGameTests.register();
        SearchLightGameTests.register();
        UavGameTests.register();
        UavPairingGameTests.register();
        UavRangeGameTests.register();
        PickUpGameTests.register();
        PersistenceGameTests.register();
        AllHelicoptersGameTests.register();
        PlaneGameTests.register();
        PlaneFlightGameTests.register();
        ShipGameTests.register();
        GroundGameTests.register();
        TankCollisionGameTests.register();
        PlaneCameraGameTests.register();
        ParachuteGameTests.register();
        WeaponGameTests.register();
        CraftingGameTests.register();
        ToolGameTests.register();
        GltdGameTests.register();
        LightWeaponGameTests.register();
        DraftingTableGameTests.register();
        ChargerGameTests.register();
        AircraftMenuGameTests.register();
        FuelFluidGameTests.register();
        MultiplayGameTests.register();
        MobSeatGameTests.register();
        GunnerGameTests.register();
        EcmGameTests.register();
        RadarGameTests.register();
        RadarFeedGameTests.register();
        CombatGameTests.register();
        WingmanMarkerGameTests.register();
        WingmanGameTests.register();
        MissionGameTests.register();
        PlannerGameTests.register();
        NpcBattleGameTests.register();
        McheliCommandGameTests.register();
        ProjectileChunkGameTests.register();
        AdvancementGameTests.register();
        LongDistanceGameTests.register();
        BlockListGameTests.register();
        NtmGameTests.register();
        LiveAirframeGameTests.register();
        LagCompensationGameTests.register();
        AntiCheatGameTests.register();
        ComponentDamageGameTests.register();
        CrewGameTests.register();
        GroundCrewGameTests.register();
        AiPilotGameTests.register();
        AiFireControlGameTests.register();
        WeatherGameTests.register();
        ComponentEffectsGameTests.register();
        VehicleBloodGameTests.register();
        if (System.getProperty("neoforge.enabledGameTestNamespaces") != null) {
            ChargerGameTests.registerTestBattery(modBus);
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(ModGameTests::onRegisterGameTests);
    }

    /** Adds a test that fails unless it succeeds within {@code maxTicks}. */
    static void test(String name, int maxTicks, Consumer<GameTestHelper> function) {
        FUNCTIONS.register(name, () -> function);
        SPECS.add(new Spec(name, maxTicks, ResourceKey.create(Registries.TEST_FUNCTION, id(name)), "default"));
    }

    /**
     * Adds a test that runs in a batch of its own, after the others, for what reaches every entity of
     * the level (and so the other tests' vehicles).
     */
    static void testAlone(String name, int maxTicks, Consumer<GameTestHelper> function) {
        FUNCTIONS.register(name, () -> function);
        SPECS.add(new Spec(name, maxTicks, ResourceKey.create(Registries.TEST_FUNCTION, id(name)), "alone"));
    }

    /**
     * Adds a test that runs in the batch named {@code batch}, apart from the others: for tests that
     * fire many projectiles or force chunks, which {@link #testAlone} tests would see.
     */
    static void testInBatch(String batch, String name, int maxTicks, Consumer<GameTestHelper> function) {
        FUNCTIONS.register(name, () -> function);
        SPECS.add(new Spec(name, maxTicks, ResourceKey.create(Registries.TEST_FUNCTION, id(name)), batch));
    }

    private static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new HashMap<>();
        for (Spec spec : SPECS) {
            Holder<TestEnvironmentDefinition<?>> environment = environments.computeIfAbsent(spec.batch(),
                    b -> event.registerEnvironment(id(b), new TestEnvironmentDefinition.AllOf(List.of())));
            event.registerTest(id(spec.name()), new FunctionGameTestInstance(spec.function(),
                    new TestData<>(environment, STRUCTURE, spec.maxTicks(), 0, true, Rotation.NONE, false, 1, 1, false,
                            0)));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, path);
    }
}
