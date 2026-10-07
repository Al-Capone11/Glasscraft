package com.glasscraft.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Parser de config basado en Gson (ya incluido en Minecraft, cero deps nuevas).
 * Lee/Escribe config/glasscraft.json; ante cualquier error devuelve defaults
 * (fail-soft: nunca crashea el juego por una config mala).
 */
public final class ConfigIo {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ConfigIo() {}

    public static GlasscraftConfig load(Path file) {
        GlasscraftConfig config = new GlasscraftConfig();
        if (!Files.exists(file)) {
            return config;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            // Merge manual campo a campo para tolerar configs parciales viejas.
            if (root.has("continuity")) {
                JsonObject cont = root.getAsJsonObject("continuity");
                GlasscraftConfig.Continuity c = config.continuity;
                c.enabled = bool(cont, "enabled", c.enabled);
                c.vanillaGlass = bool(cont, "vanillaGlass", c.vanillaGlass);
                c.glassPanes = bool(cont, "glassPanes", c.glassPanes);
                c.tintedGlass = bool(cont, "tintedGlass", c.tintedGlass);
                c.glowstone = bool(cont, "glowstone", c.glowstone);
                c.seaLantern = bool(cont, "seaLantern", c.seaLantern);
                c.prismarine = bool(cont, "prismarine", c.prismarine);
                strings(cont, "blockAllowlist").forEach(c.blockAllowlist::add);
                strings(cont, "blockDenylist").forEach(c.blockDenylist::add);
                if (cont.has("byMod") && cont.get("byMod").isJsonObject()) {
                    JsonObject byMod = cont.getAsJsonObject("byMod");
                    byMod.entrySet().forEach(e -> c.byMod.put(e.getKey(), e.getValue().getAsBoolean()));
                }
            }
            if (root.has("item")) {
                JsonObject item = root.getAsJsonObject("item");
                GlasscraftConfig.Item i = config.item;
                i.durability = Math.max(1, intOf(item, "durability", i.durability));
                i.miningSpeed = Math.max(0.5f, floatOf(item, "miningSpeed", i.miningSpeed));
                if (item.has("repairWith") && item.get("repairWith").isJsonPrimitive()) {
                    String repairWith = item.get("repairWith").getAsString();
                    if (!repairWith.isBlank()) i.repairWith = repairWith;
                }
            }
        } catch (Exception e) {
            // fail-soft: config corrupta -> defaults (log lo hace el caller)
        }
        return config;
    }

    public static void save(Path file, GlasscraftConfig config) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        }
    }

    private static boolean bool(JsonObject obj, String key, boolean def) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) return obj.get(key).getAsBoolean();
        return def;
    }

    private static int intOf(JsonObject obj, String key, int def) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) return obj.get(key).getAsInt();
        return def;
    }

    private static float floatOf(JsonObject obj, String key, float def) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) return obj.get(key).getAsFloat();
        return def;
    }

    private static java.util.List<String> strings(JsonObject obj, String key) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            obj.getAsJsonArray(key).forEach(e -> {
                if (e.isJsonPrimitive()) out.add(e.getAsString());
            });
        }
        return out;
    }
}
