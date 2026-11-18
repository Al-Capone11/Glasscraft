package com.glasscraft.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigIoTest {

    @TempDir
    Path tempDir;

    @Test
    void legacyConfigWithoutModRulesDefaultsToNoCtmForOtherMods() throws Exception {
        Path file = tempDir.resolve("glasscraft.json");
        Files.writeString(file, """
            {
              "continuity": {
                "enabled": true,
                "vanillaGlass": true,
                "byMod": {},
                "blockAllowlist": [],
                "blockDenylist": []
              }
            }
            """);

        GlasscraftConfig config = ConfigIo.load(file);

        assertFalse(config.continuity.resolvesCtm("hivemind", "hive_chitin"));
        assertTrue(config.continuity.resolves("hivemind", "hive_chitin"));
        assertTrue(config.continuity.vanillaGlass);
    }

    @Test
    void explicitModRulesAndBlockListsSurviveSaveLoad() throws Exception {
        Path file = tempDir.resolve("glasscraft.json");
        GlasscraftConfig original = new GlasscraftConfig();
        original.continuity.byMod.put("chisel", true);
        original.continuity.byMod.put("hivemind", false);
        original.continuity.blockAllowlist.add("example:compatible_glass");
        original.continuity.blockDenylist.add("chisel:forbidden_glass");

        ConfigIo.save(file, original);
        GlasscraftConfig loaded = ConfigIo.load(file);

        assertTrue(loaded.continuity.resolvesCtm("chisel", "glass"));
        assertFalse(loaded.continuity.resolvesCtm("hivemind", "hive_chitin"));
        assertTrue(loaded.continuity.resolvesCtm("example", "compatible_glass"));
        assertFalse(loaded.continuity.resolvesCtm("chisel", "forbidden_glass"));
    }
}
