package io.github.mcheliceport.platform;

import java.util.ServiceLoader;

/** Looks up the loader-specific implementations of the platform interfaces. */
public final class Services {

    public static final Platform PLATFORM = load(Platform.class);
    public static final Registrar REGISTRAR = load(Registrar.class);
    public static final io.github.mcheliceport.platform.network.Network NETWORK =
            load(io.github.mcheliceport.platform.network.Network.class);
    public static final Configs CONFIGS = load(Configs.class);
    public static final Transfer TRANSFER = load(Transfer.class);

    private Services() {
    }

    public static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, Services.class.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + type.getName()
                        + " implementation found: is the right loader module on the classpath?"));
    }
}
