package io.github.mcheliceport.gametest;

import io.github.mcheliceport.McheliCePort;
import java.util.ArrayList;
import java.util.List;
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

    private record Spec(String name, int maxTicks, ResourceKey<Consumer<GameTestHelper>> function) {
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
        FUNCTIONS.register(modBus);
        modBus.addListener(ModGameTests::onRegisterGameTests);
    }

    /** Adds a test that fails unless it succeeds within {@code maxTicks}. */
    static void test(String name, int maxTicks, Consumer<GameTestHelper> function) {
        FUNCTIONS.register(name, () -> function);
        SPECS.add(new Spec(name, maxTicks, ResourceKey.create(Registries.TEST_FUNCTION, id(name))));
    }

    private static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment =
                event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        for (Spec spec : SPECS) {
            event.registerTest(id(spec.name()), new FunctionGameTestInstance(spec.function(),
                    new TestData<>(environment, STRUCTURE, spec.maxTicks(), 0, true, Rotation.NONE, false, 1, 1, false,
                            0)));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, path);
    }
}
