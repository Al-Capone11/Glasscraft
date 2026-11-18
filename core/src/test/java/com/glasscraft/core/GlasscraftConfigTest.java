package com.glasscraft.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlasscraftConfigTest {

    @Test
    void unlistedModDoesNotGetCtmByDefault() {
        GlasscraftConfig.Continuity c = new GlasscraftConfig().continuity;

        assertFalse(c.resolvesCtm("hivemind", "hive_chitin"));
    }

    @Test
    void unlistedModKeepsLegacyNonCtmResolution() {
        GlasscraftConfig.Continuity c = new GlasscraftConfig().continuity;

        // La proteccion de drops y otras mecanicas conservan su comportamiento.
        assertTrue(c.resolves("hivemind", "hive_chitin"));
    }

    @Test
    void byModTrueEnablesCtm() {
        GlasscraftConfig.Continuity c = new GlasscraftConfig().continuity;
        c.byMod.put("chisel", true);

        assertTrue(c.resolvesCtm("chisel", "glass"));
    }

    @Test
    void byModFalseDisablesCtm() {
        GlasscraftConfig.Continuity c = new GlasscraftConfig().continuity;
        c.byMod.put("hivemind", false);

        assertFalse(c.resolvesCtm("hivemind", "hive_chitin"));
    }

    @Test
    void blockAllowlistOverridesByModFalse() {
        GlasscraftConfig.Continuity c = new GlasscraftConfig().continuity;
        c.byMod.put("example", false);
        c.blockAllowlist.add("example:compatible_glass");

        assertTrue(c.resolvesCtm("example", "compatible_glass"));
        assertFalse(c.resolvesCtm("example", "other_block"));
    }

    @Test
    void blockDenylistOverridesAllowlistAndByModTrue() {
        GlasscraftConfig.Continuity c = new GlasscraftConfig().continuity;
        c.byMod.put("example", true);
        c.blockAllowlist.add("example:glass");
        c.blockDenylist.add("example:glass");

        assertFalse(c.resolvesCtm("example", "glass"));
    }

    @Test
    void vanillaResolutionIsUnchanged() {
        GlasscraftConfig.Continuity c = new GlasscraftConfig().continuity;

        assertTrue(c.resolvesVanilla("glass"));
        c.enabled = false;
        assertFalse(c.resolvesVanilla("glass"));
    }
}
