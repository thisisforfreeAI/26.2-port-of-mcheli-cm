package io.github.mcheliceport.compat.hbm;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

/** Exercises the Java 25 reflection boundary without putting fake com.hbm classes on the classpath. */
class HbmCallsTest {
    public static class Api {
        public float radius;
        public Api() {}
        public Api(float radius) { this.radius = radius; }
        public float scale(float amount) { return radius * amount; }
        public int integral(int value) { return value; }
        public static String choose(int value) { return "int"; }
        public static String choose(double value) { return "double"; }
        public void reference(String value) {}
        public void primitive(boolean value) {}
        public void fail() { throw new IllegalStateException("API changed"); }
    }

    @Test
    void yamlDoublesBecomeFloatConstructorAndMethodArguments() throws Exception {
        Object value = HbmCalls.create(Api.class.getName(), 2.5D);
        assertEquals(7.5F, HbmCalls.call(value, "scale", 3.0D));
        HbmCalls.field(value, "radius", 4.0D);
        assertEquals(4.0F, ((Api) value).radius);
    }

    @Test
    void overloadsPreferTheExactNumberType() throws Exception {
        assertEquals("int", HbmCalls.callStatic(Api.class.getName(), "choose", 3));
        assertEquals("double", HbmCalls.callStatic(Api.class.getName(), "choose", 3.0D));
        assertThrows(NoSuchMethodException.class,
                () -> HbmCalls.callStatic(Api.class.getName(), "choose", 3.0F));
    }

    @Test
    void fractionalOrOverflowingYamlNumbersCannotBecomeIntegers() throws Exception {
        Api api = new Api();
        assertEquals(3, HbmCalls.call(api, "integral", 3.0D));
        assertThrows(NoSuchMethodException.class, () -> HbmCalls.call(api, "integral", 3.5D));
        assertThrows(NoSuchMethodException.class, () -> HbmCalls.call(api, "integral", 3_000_000_000L));
        assertThrows(NoSuchMethodException.class, () -> HbmCalls.call(api, "scale", Double.NaN));
        assertThrows(NoSuchMethodException.class, () -> HbmCalls.call(api, "scale", Float.POSITIVE_INFINITY));
    }

    @Test
    void nullIsOnlyPassedToReferenceParameters() throws Exception {
        Api api = new Api();
        assertNull(HbmCalls.call(api, "reference", new Object[] {null}));
        assertThrows(NoSuchMethodException.class, () -> HbmCalls.call(api, "primitive", new Object[] {null}));
    }

    @Test
    void missingApisAndInvocationFailuresAreReportedToTheFallbackBoundary() {
        assertThrows(ClassNotFoundException.class, () -> HbmCalls.create("com.hbm.missing.Missing"));
        assertThrows(NoSuchMethodException.class, () -> HbmCalls.call(new Api(), "removedMethod"));
        var error = assertThrows(InvocationTargetException.class, () -> HbmCalls.call(new Api(), "fail"));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    @Test
    void processorConfigurationCannotResolveClassesOutsideTheStandardPackage() {
        assertThrows(IllegalArgumentException.class,
                () -> HbmCompat.processor(java.util.Map.of("Type", "java.lang.Runtime")));
        assertThrows(IllegalArgumentException.class,
                () -> HbmCompat.processor(java.util.Map.of("Type", "../Other")));
    }
}
