package com.glasscraft.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modelo de configuracion de Glasscraft. Java puro (sin Minecraft) para poder
 * testear el parser con JUnit y compartirlo entre loaders.
 *
 * Estructura JSON (config/glasscraft.json):
 * {
 *   "continuity": {
 *     "enabled": true,
 *     "vanillaGlass": true, "glassPanes": true, "tintedGlass": true,
 *     "glowstone": true, "seaLantern": true,
 *     "byMod": { "chisel": true, "rechiseled": true },
 *     "blockAllowlist": [], "blockDenylist": []
 *   },
 *   }, "item": { "durability": 250, "repairWith": "c:iron_ingots" }
 * }
 * miningSpeed NO es configurable (peticion del usuario): constante 6.0 en
 * GlasscraftItems.MINING_SPEED, igual a un pico de hierro.
 */
public final class GlasscraftConfig {

    public static final int DEFAULT_DURABILITY = 250;    // pico de hierro (FIJO)
    public static final String DEFAULT_REPAIR_ITEM = "c:iron_ingots";

    public final Continuity continuity = new Continuity();
    public final Item item = new Item();

    public static final class Continuity {
        public boolean enabled = true;
        public boolean vanillaGlass = true;
        public boolean glassPanes = true;
        public boolean tintedGlass = true;
        public boolean glowstone = true;
        public boolean seaLantern = true;
        /** Continuidad por mod (id de mod -> activada). Mods no listados usan 'enabled' global. */
        public final Map<String, Boolean> byMod = new LinkedHashMap<>();
        /** Bloques concretos que SIEMPRE reciben continuidad (namespace:path). */
        public final java.util.List<String> blockAllowlist = new java.util.ArrayList<>();
        /** Bloques concretos que NUNCA reciben continuidad (namespace:path). */
        public final java.util.List<String> blockDenylist = new java.util.ArrayList<>();

        /** Resuelve si un bloque (namespace:path) recibe continuidad. */
        public boolean resolves(String namespace, String path) {
            String id = namespace + ":" + path;
            if (blockDenylist.contains(id)) return false;
            if (blockAllowlist.contains(id)) return true;
            Boolean byModFlag = byMod.get(namespace);
            if (byModFlag != null) return byModFlag;
            return enabled;
        }

        /** Resuelve para un bloque vanilla (solo path, namespace minecraft). */
        public boolean resolvesVanilla(String path) {
            if (blockDenylist.contains("minecraft:" + path)) return false;
            if (blockAllowlist.contains("minecraft:" + path)) return true;
            return enabled;
        }
    }

    public static final class Item {
        /** Tag de item (namespace:path) aceptado como material de reparacion. */
        public String repairWith = DEFAULT_REPAIR_ITEM;
    }
}
