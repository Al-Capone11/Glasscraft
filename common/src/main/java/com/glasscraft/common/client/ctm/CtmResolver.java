package com.glasscraft.common.client.ctm;

import com.glasscraft.common.Glasscraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import com.glasscraft.core.GlasscraftConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Motor de continuidad (CTM estilo OptiFine "glass"): dos bloques conectan
 * si son del MISMO bloque y el material esta activo en la config. Resolucion
 * por tipo (vanilla) o por mod (byMod). Cache por (stateId+vecinos) la
 * gestionan los llamadores; aqui solo se decide.
 */
public final class CtmResolver {

    private CtmResolver() {}

    /** ¿Este bloque participa de continuidad segun la config? */
    public static boolean isEnabled(BlockState state) {
        GlasscraftConfig.Continuity c = Glasscraft.config().continuity;
        if (!c.enabled) return false;

        Identifier id = state.getBlock().builtInRegistryHolder().key().identifier();
        String namespace = id.getNamespace();
        String path = id.getPath();

        if (namespace.equals("minecraft")) {
            String category = Glasscraft.categoryOfVanillaPath(path);
            if (category == null) return false;
            return switch (category) {
                case "vanillaGlass" -> c.vanillaGlass;
                case "glassPanes" -> c.glassPanes;
                case "tintedGlass" -> c.tintedGlass;
                case "glowstone" -> c.glowstone;
                case "seaLantern" -> c.seaLantern;
                default -> false;
            };
        }
        // Bloques de mods (Chisel Reborn / Rechiseled / etc.): por namespace.
        return c.resolves(namespace, path);
    }

    /**
     * "Cristal limpio": bloques cuyo sprite base se sustituye por
     * glasscraft:block/clean_glass al conectar (solo vidrios vanilla — el
     * vidrio tintado tiene textura propia de vidrio, los paneles usan el
     * mismo sprite de vidrio que los bloques).
     */
    public static boolean cleanGlassActive(BlockState state) {
        if (!Glasscraft.config().continuity.cleanGlass) return false;
        Identifier id = state.getBlock().builtInRegistryHolder().key().identifier();
        if (!"minecraft".equals(id.getNamespace())) return false;
        String path = id.getPath();
        return "glass".equals(path) || "tinted_glass".equals(path)
            || path.endsWith("_stained_glass") || path.endsWith("_stained_glass_pane")
            || path.equals("glass_pane");
    }

    /**
     * Override de sprite para eliminar bordes sombreados en bloques conectados.
     * Vidrios: cuando cleanGlass esta activo. Linterna marina: cuando su
     * continuidad esta activa (el borde oscuro de 2px de la textura aparece
     * como linea en cada junta — el shrink de 1px no alcanza a taparlo).
     */
    public static boolean spriteOverrideActive(BlockState state) {
        Identifier id = state.getBlock().builtInRegistryHolder().key().identifier();
        if ("minecraft".equals(id.getNamespace()) && "sea_lantern".equals(id.getPath())) {
            return Glasscraft.config().continuity.enabled
                && Glasscraft.config().continuity.seaLantern;
        }
        return Glasscraft.config().continuity.cleanGlass
            && isVanillaGlassLike(state, id);
    }

    private static boolean isVanillaGlassLike(BlockState state, Identifier id) {
        if (!"minecraft".equals(id.getNamespace())) return false;
        String path = id.getPath();
        return "glass".equals(path) || "tinted_glass".equals(path)
            || path.endsWith("_stained_glass") || path.endsWith("_stained_glass_pane")
            || path.equals("glass_pane");
    }

    /**
     * Paneles vanilla: sus UVs son tiras sub-tile del atlas; la contraccion
     * full-tile los distorsionaria. Quedan fuera del CTM por ahora.
     */
    public static boolean isVanillaPane(BlockState state) {
        Identifier id = state.getBlock().builtInRegistryHolder().key().identifier();
        if (!"minecraft".equals(id.getNamespace())) return false;
        String path = id.getPath();
        return path.equals("glass_pane") || path.endsWith("_stained_glass_pane");
    }

    /** ¿El vecino en esa direccion conecta con este bloque? */
    public static boolean connects(BlockState self, Direction dir, BlockPos pos,
                                   net.minecraft.world.level.BlockGetter level) {
        BlockState neighbor = level.getBlockState(pos.relative(dir));
        return neighbor.getBlock() == self.getBlock() && isEnabled(neighbor);
    }

    /** ¿El vecino DIAGONAL (en el plano de una cara) conecta? Para esquinas CTM. */
    public static boolean diagonalConnects(BlockState self, BlockPos diagPos,
                                           net.minecraft.world.level.BlockGetter level) {
        BlockState n = level.getBlockState(diagPos);
        return n.getBlock() == self.getBlock() && isEnabled(n);
    }

    /**
     * Mascara de esquinas de la cara (bits NW=1, NE=2, SW=4, SE=8 en el marco
     * del tile). nDir = direccion "arriba" de la cara en el mundo, wDir = "izquierda".
     * Diagonal libre -> el sprite dibuja el arco redondeado vanilla (como el
     * bloque suelto); diagonal conectada -> continuacion plana del interior.
     * Es la pieza que el CTM de 4 lados no tenia: sin ella, la cruz pierde sus
     * esquinas y el contorno del grupo queda rectangular.
     */
    public static int cornerMask(BlockState state, BlockPos pos,
                                 net.minecraft.world.level.BlockGetter level,
                                 Direction nDir, Direction wDir) {
        int m = 0;
        if (diagonalConnects(state, pos.relative(nDir).relative(wDir), level)) m |= 1;              // NW
        if (diagonalConnects(state, pos.relative(nDir).relative(wDir.getOpposite()), level)) m |= 2;   // NE
        if (diagonalConnects(state, pos.relative(nDir.getOpposite()).relative(wDir), level)) m |= 4;    // SW
        if (diagonalConnects(state, pos.relative(nDir.getOpposite()).relative(wDir.getOpposite()), level)) m |= 8; // SE
        return m;
    }

    /**
     * Mascara de 6 bits de vecinos que conectan (bit i = Direction.values()[i]).
     */
    public static int connectionMask(BlockState state, BlockPos pos,
                                     net.minecraft.world.level.BlockGetter level) {
        int mask = 0;
        Direction[] dirs = Direction.values();
        for (int i = 0; i < dirs.length; i++) {
            if (connects(state, dirs[i], pos, level)) {
                mask |= 1 << i;
            }
        }
        return mask;
    }
}
