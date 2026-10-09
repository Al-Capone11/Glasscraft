package com.glasscraft.common.client.ctm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagnostico F3: contador unico con muestreo para ver en el log del cliente
 * si el CTM esta siendo invocado y cuantos quads modifica.
 */
public final class CtmDebug {

    public static final Logger LOGGER = LoggerFactory.getLogger("GlasscraftCTM");

    public static long beginCalls;
    public static long activeBlocks;   // bloques que pasaron todos los guards
    public static long maskNonZero;    // bloques con al menos un vecino conectado
    public static long quadsModified;
    public static long quadsTotal;

    private static long lastLog;

    private CtmDebug() {}

    public static synchronized void sample(String where) {
        long now = System.currentTimeMillis();
        if (now - lastLog < 5000) return;
        lastLog = now;
        LOGGER.info("[{}] begin={} active={} mask>0={} quadsTotal={} quadsMod={}",
            where, beginCalls, activeBlocks, maskNonZero, quadsTotal, quadsModified);
    }
}
