package com.glasscraft.common.client.ctm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Contexto del bloque en tesselacion: ModelBlockRenderer tesselateBlock
 * conoce pos+level+state, pero putQuadWithTint (donde modificamos el quad)
 * no recibe el BlockPos en un parametro usable — se pasa via thread-local.
 * El hilo del mesher es el unico escritor durante el tesselado del bloque.
 */
public final class CtmContext {

    private static final ThreadLocal<CtmContext> INSTANCE = ThreadLocal.withInitial(CtmContext::new);

    public net.minecraft.world.level.BlockGetter level;
    public BlockPos pos;
    public BlockState state;
    public int connectionMask = -1; // -1 = sin calcular

    public static CtmContext get() {
        return INSTANCE.get();
    }

    public int mask() {
        if (connectionMask == -1) {
            connectionMask = CtmResolver.connectionMask(state, pos, level);
        }
        return connectionMask;
    }

    public void reset() {
        level = null;
        pos = null;
        state = null;
        connectionMask = -1;
    }
}
