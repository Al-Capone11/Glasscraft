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

    /**
     * Categoria de config de un bloque vanilla por su path (verificado contra
     * los blockstates del jar 26.2). Los bloques de mods entran en byMod.
     */
    public static String categoryOfVanillaPath(String path) {
        if (path.endsWith("_stained_glass_pane") || path.equals("glass_pane")) return "glassPanes";
        if (path.equals("tinted_glass")) return "tintedGlass";
        if (path.endsWith("_stained_glass") || path.equals("glass")) return "vanillaGlass";
        if (path.equals("glowstone")) return "glowstone";
        if (path.equals("sea_lantern")) return "seaLantern";
        return null; // fuera de toda categoria
    }
}
