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

    /** ¿El vecino en esa direccion conecta con este bloque? */
    public static boolean connects(BlockState self, Direction dir, BlockPos pos,
                                   net.minecraft.world.level.BlockGetter level) {
        BlockState neighbor = level.getBlockState(pos.relative(dir));
        return neighbor.getBlock() == self.getBlock() && isEnabled(neighbor);
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
