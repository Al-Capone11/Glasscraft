package com.glasscraft.common;

/**
 * Estado compartido del mod (thread-safe). Vive en common porque ambos loaders
 * necesitan leerlo: config, flags de mods compatibles.
 */
public final class Glasscraft {

    public static final String MOD_ID = "glasscraft";

    private static volatile com.glasscraft.core.GlasscraftConfig config = new com.glasscraft.core.GlasscraftConfig();

    private Glasscraft() {}

    public static com.glasscraft.core.GlasscraftConfig config() {
        return config;
    }

    public static void setConfig(com.glasscraft.core.GlasscraftConfig newConfig) {
        config = newConfig;
    }

    /** Id del mod al que pertenece un namespace (namespace == modId en ambos loaders). */
    public static String modIdOfNamespace(String namespace) {
        return namespace;
    }
}
