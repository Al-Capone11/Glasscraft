package com.glasscraft.common.client.ctm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprites "cristal limpio" (glasscraft:block/clean_*): el marco de 1px del tile
 * con SU color original, interior 100% transparente. Un sprite por tipo de
 * vidrio (clean_glass, clean_blue_stained_glass, ...) — los teñidos tienen el
 * color PRE-COREADO en su textura, un sprite unico les robaba el color.
 *
 * Resolucion cacheada por path; el meshing de chunks corre en hilos de
 * compilacion, la primera resolucion cachea (volatile + mapa por path).
 */
public final class CleanGlassSprite {

    private static final org.slf4j.Logger LOGGER =
        org.slf4j.LoggerFactory.getLogger("GlasscraftCTM");

    /** path del bloque vanilla -> path del sprite limpio glasscraft. */
    private static final Map<String, String> PATH_MAP = Map.ofEntries(
        Map.entry("glass", "clean_glass"),
        Map.entry("tinted_glass", "clean_tinted_glass"),
        Map.entry("glass_pane", "clean_glass"),
        Map.entry("white_stained_glass", "clean_white_stained_glass"),
        Map.entry("orange_stained_glass", "clean_orange_stained_glass"),
        Map.entry("magenta_stained_glass", "clean_magenta_stained_glass"),
        Map.entry("light_blue_stained_glass", "clean_light_blue_stained_glass"),
        Map.entry("yellow_stained_glass", "clean_yellow_stained_glass"),
        Map.entry("lime_stained_glass", "clean_lime_stained_glass"),
        Map.entry("pink_stained_glass", "clean_pink_stained_glass"),
        Map.entry("gray_stained_glass", "clean_gray_stained_glass"),
        Map.entry("light_gray_stained_glass", "clean_light_gray_stained_glass"),
        Map.entry("cyan_stained_glass", "clean_cyan_stained_glass"),
        Map.entry("purple_stained_glass", "clean_purple_stained_glass"),
        Map.entry("blue_stained_glass", "clean_blue_stained_glass"),
        Map.entry("brown_stained_glass", "clean_brown_stained_glass"),
        Map.entry("green_stained_glass", "clean_green_stained_glass"),
        Map.entry("red_stained_glass", "clean_red_stained_glass"),
        Map.entry("black_stained_glass", "clean_black_stained_glass"),
        Map.entry("white_stained_glass_pane", "clean_white_stained_glass"),
        Map.entry("orange_stained_glass_pane", "clean_orange_stained_glass"),
        Map.entry("magenta_stained_glass_pane", "clean_magenta_stained_glass"),
        Map.entry("light_blue_stained_glass_pane", "clean_light_blue_stained_glass"),
        Map.entry("yellow_stained_glass_pane", "clean_yellow_stained_glass"),
        Map.entry("lime_stained_glass_pane", "clean_lime_stained_glass"),
        Map.entry("pink_stained_glass_pane", "clean_pink_stained_glass"),
        Map.entry("gray_stained_glass_pane", "clean_gray_stained_glass"),
        Map.entry("light_gray_stained_glass_pane", "clean_light_gray_stained_glass"),
        Map.entry("cyan_stained_glass_pane", "clean_cyan_stained_glass"),
        Map.entry("purple_stained_glass_pane", "clean_purple_stained_glass"),
        Map.entry("blue_stained_glass_pane", "clean_blue_stained_glass"),
        Map.entry("brown_stained_glass_pane", "clean_brown_stained_glass"),
        Map.entry("green_stained_glass_pane", "clean_green_stained_glass"),
        Map.entry("red_stained_glass_pane", "clean_red_stained_glass"),
        Map.entry("black_stained_glass_pane", "clean_black_stained_glass")
    );

    private static final Map<String, TextureAtlasSprite> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> FAILED = new ConcurrentHashMap<>();
    private static int loggedErrors;

    private CleanGlassSprite() {}

    /**
     * Sprite limpio para el path de un bloque de vidrio vanilla.
     * Devuelve null si el path no tiene variante o la resolucion fallo.
     */
    public static TextureAtlasSprite getFor(String blockPath) {
        String spritePath = PATH_MAP.get(blockPath);
        if (spritePath == null) return null;
        TextureAtlasSprite hit = CACHE.get(spritePath);
        if (hit != null) return hit;
        if (Boolean.TRUE.equals(FAILED.get(spritePath))) return null;
        try {
            Minecraft mc = Minecraft.getInstance();
            // Clave del AtlasManager = ID de DEFINICION del atlas (AtlasIds.BLOCKS),
            // no la textura (LOCATION_BLOCKS) — esta ultima lanza IllegalArgumentException.
            TextureAtlas atlas = mc.getAtlasManager()
                .getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
            TextureAtlasSprite s = atlas.getSprite(Identifier.fromNamespaceAndPath(
                "glasscraft", "block/" + spritePath));
            if (s != null && !s.contents().name().getPath().contains("missingno")) {
                CACHE.put(spritePath, s);
                return s;
            }
            if (loggedErrors < 3) {
                loggedErrors++;
                LOGGER.warn("[CLEAN] sprite missing para {}: {}", blockPath, spritePath);
            }
            FAILED.put(spritePath, Boolean.TRUE);
        } catch (Throwable t) {
            if (loggedErrors < 3) {
                loggedErrors++;
                LOGGER.warn("[CLEAN] error resolviendo {}: {}", blockPath, t.toString());
            }
        }
        return null;
    }

    /** Invalidar tras un reload de recursos. */
    public static void invalidate() {
        CACHE.clear();
        FAILED.clear();
    }
}
