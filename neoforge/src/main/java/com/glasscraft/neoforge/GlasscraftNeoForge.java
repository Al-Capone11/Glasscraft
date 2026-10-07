package com.glasscraft.neoforge;

import com.glasscraft.common.Glasscraft;
import com.glasscraft.common.init.GlasscraftItems;
import com.glasscraft.core.ConfigIo;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

@Mod(Glasscraft.MOD_ID)
public class GlasscraftNeoForge {

    public GlasscraftNeoForge() {
        Path configFile = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("glasscraft.json");
        Glasscraft.setConfig(ConfigIo.load(configFile));
        try {
            ConfigIo.save(configFile, Glasscraft.config());
        } catch (Exception ignored) {}

        GlasscraftItems.register();
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("glasscraft")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("reload").executes(ctx -> {
                Path configFile = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("glasscraft.json");
                Glasscraft.setConfig(ConfigIo.load(configFile));
                ctx.getSource().sendSuccess(() -> Component.literal("Glasscraft config reloaded"), false);
                return 1;
            })));
    }
}
