package com.glasscraft.common.client.ctm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/**
 * Sprite "cristal limpio" (glasscraft:block/clean_glass): solo el marco de
 * 1px del tile, interior transparente. Resolucion cacheada del atlas de
 * bloques; el meshing de chunks corre en hilos de compilacion, asi que la
 * primera resolucion se hace en el hilo render y se cachea (volatile).
 */
public final class CleanGlassSprite {

    private static final org.slf4j.Logger LOGGER =
        org.slf4j.LoggerFactory.getLogger("GlasscraftCTM");

    private static volatile TextureAtlasSprite cached;
    private static volatile boolean resolved;
    private static int loggedErrors;

    private CleanGlassSprite() {}

    public static TextureAtlasSprite get() {
        if (resolved) return cached;
        synchronized (CleanGlassSprite.class) {
            if (resolved) return cached;
            try {
                Minecraft mc = Minecraft.getInstance();
                TextureAtlas atlas = mc.getAtlasManager()
                    .getAtlasOrThrow(TextureAtlas.LOCATION_BLOCKS);
                TextureAtlasSprite s = atlas.getSprite(Identifier.fromNamespaceAndPath(
                    "glasscraft", "block/clean_glass"));
                // Sprite "missing" de vanilla: ficha magenta/blanca con u0==v0==0 y
                // tamano igual al del atlas entero en algunos casos; detectarlo por
                // nombre (missingno) para no usarlo por error.
                if (s != null && !s.contents().name().getPath().contains("missingno")) {
                    cached = s;
                    resolved = true;
                    LOGGER.info("[CLEAN] sprite resuelto: {}", s.contents().name());
                } else if (s != null && loggedErrors < 3) {
                    loggedErrors++;
                    LOGGER.warn("[CLEAN] getSprite devolvio el sprite missing — clean_glass NO esta stitches en el atlas");
                }
            } catch (Throwable t) {
                if (loggedErrors < 3) {
                    loggedErrors++;
                    LOGGER.warn("[CLEAN] no se pudo resolver el sprite: {}", t.toString());
                }
            }
            return cached;
        }
    }

    /** Invalidar tras un reload de recursos. */
    public static void invalidate() {
        synchronized (CleanGlassSprite.class) {
            resolved = false;
            cached = null;
        }
    }
}
