package com.glasscraft.common.client.ctm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/**
 * Sprite "cristal limpio" (glasscraft:block/clean_glass): solo el marco de
 * 1px del tile, interior transparente. Se resuelve del atlas de bloques con
 * cache invalidable — tras un reload de recursos (F3+T) hay que volver a
 * resolverlo porque el atlas se re-stitching.
 */
public final class CleanGlassSprite {

    private static volatile TextureAtlasSprite cached;
    private static volatile boolean resolved;

    private CleanGlassSprite() {}

    public static TextureAtlasSprite get() {
        if (resolved) return cached;
        synchronized (CleanGlassSprite.class) {
            if (resolved) return cached;
            try {
                Minecraft mc = Minecraft.getInstance();
                TextureAtlas atlas = mc.getAtlasManager()
                    .getAtlasOrThrow(TextureAtlas.LOCATION_BLOCKS);
                cached = atlas.getSprite(Identifier.fromNamespaceAndPath(
                    "glasscraft", "block/clean_glass"));
                resolved = cached != null;
            } catch (Throwable t) {
                // atlas aun no listo (inicio) — reintentar en la siguiente llamada
                resolved = false;
                cached = null;
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
