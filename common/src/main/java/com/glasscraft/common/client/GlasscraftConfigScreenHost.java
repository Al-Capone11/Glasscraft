package com.glasscraft.common.client;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Puente loader->pantalla: cada loader registra aqui el Path de la config y
 * el save se hace sin deps de loader en la pantalla compartida.
 */
public final class GlasscraftConfigScreenHost {

    private static volatile Supplier<Path> configPathSupplier = () -> null;
    private static volatile Runnable saver = () -> {};

    private GlasscraftConfigScreenHost() {}

    public static void init(Path configFile, java.util.function.Consumer<Path> saveFn) {
        configPathSupplier = () -> configFile;
        saver = () -> saveFn.accept(configFile);
    }

    public static void save() {
        saver.run();
    }
}
