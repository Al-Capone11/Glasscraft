package com.glasscraft.fabric;

import com.glasscraft.common.Glasscraft;
import com.glasscraft.common.init.GlasscraftItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.glasscraft.core.ConfigIo;

import java.nio.file.Path;

public class GlasscraftFabric implements ModInitializer {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Glasscraft");

    @Override
    public void onInitialize() {
        // Cargar config ANTES de registrar (durabilidad/velocidad vienen de ahi).
        Path configFile = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getConfigDir().resolve("glasscraft.json");
        Glasscraft.setConfig(ConfigIo.load(configFile));
        try {
            ConfigIo.save(configFile, Glasscraft.config()); // materializa defaults
        } catch (Exception ignored) {}

        GlasscraftItems.registerDirect();
        com.glasscraft.common.init.GlasscraftCreativeTab.registerDirect();

        // Puente de guardado para la pantalla de config (ModMenu).
        com.glasscraft.common.client.GlasscraftConfigScreenHost.init(configFile, path -> {
            try {
                ConfigIo.save(path, Glasscraft.config());
            } catch (Exception e) {
                LOGGER.error("No se pudo guardar la config de Glasscraft", e);
            }
        });

        // /glasscraft reload
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("glasscraft")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("reload").executes(ctx -> {
                    Glasscraft.setConfig(ConfigIo.load(configFile));
                    ctx.getSource().sendSuccess(() -> Component.literal("Glasscraft config reloaded"), false);
                    return 1;
                }))));
    }
}
