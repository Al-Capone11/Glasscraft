package com.glasscraft.common.client.ctm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Diagnostico F3: contadores acumulados por path de bloque — determina
 * EXACTAMENTE que bloques llegan al hook y en que guard se detienen.
 */
public final class CtmDebug {

    public static final Logger LOGGER = LoggerFactory.getLogger("GlasscraftCTM");

    public static final AtomicLong beginCalls = new AtomicLong();
    public static final AtomicLong activeBlocks = new AtomicLong();
    public static final AtomicLong maskNonZero = new AtomicLong();
    public static final AtomicLong quadsModified = new AtomicLong();
    public static final AtomicLong quadsTotal = new AtomicLong();

    public static final java.util.concurrent.atomic.AtomicInteger dumpsRemaining = new java.util.concurrent.atomic.AtomicInteger(30);

    /** path de bloque -> veces que fue el estado del hook (solo primeros 200 paths). */
    public static final ConcurrentHashMap<String, AtomicLong> seenStates = new ConcurrentHashMap<>();
    /** path de bloque que paso isEnabled. */
    public static final ConcurrentHashMap<String, AtomicLong> activeStates = new ConcurrentHashMap<>();
    /** path con vecinos conectados. */
    public static final ConcurrentHashMap<String, AtomicLong> connectedStates = new ConcurrentHashMap<>();

    private static volatile long lastLog;

    private CtmDebug() {}

    public static void seeState(String path) {
        seenStates.computeIfAbsent(path, k -> new AtomicLong()).incrementAndGet();
        sample("tick");
    }

    public static void seeActive(String path) {
        activeStates.computeIfAbsent(path, k -> new AtomicLong()).incrementAndGet();
    }

    public static void seeConnected(String path) {
        connectedStates.computeIfAbsent(path, k -> new AtomicLong()).incrementAndGet();
    }

    public static synchronized void sample(String where) {
        long now = System.currentTimeMillis();
        if (now - lastLog < 5000) return;
        lastLog = now;
        LOGGER.info("[{}] begin={} active={} conn={} quadsMod={} estados={} activos={}",
            where, beginCalls.get(), activeBlocks.get(), maskNonZero.get(),
            quadsModified.get(), seenStates.size(), activeStates.size());
        // volcar top de paths activos
        if (!activeStates.isEmpty()) {
            StringBuilder sb = new StringBuilder("[activos] ");
            activeStates.forEach((k, v) -> sb.append(k).append('x').append(v.get()).append(' '));
            LOGGER.info("{}", sb);
        }
        if (!connectedStates.isEmpty()) {
            StringBuilder sb = new StringBuilder("[conectados] ");
            connectedStates.forEach((k, v) -> sb.append(k).append('x').append(v.get()).append(' '));
            LOGGER.info("{}", sb);
        }
    }
}
