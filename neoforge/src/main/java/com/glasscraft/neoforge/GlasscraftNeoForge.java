package com.glasscraft.neoforge;

import com.glasscraft.common.Glasscraft;
import com.glasscraft.common.init.GlasscraftItems;
import com.glasscraft.core.ConfigIo;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.nio.file.Path;

/**
 * Entrypoint NeoForge. El registro de items va por DeferredRegister (patron
 * MDK 26.2): el registro directo en el constructor @Mod crashea con
 * "Registry is already frozen" porque el constructor corre tras el registro.
 */
@Mod(Glasscraft.MOD_ID)
public class GlasscraftNeoForge {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Glasscraft");

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Glasscraft.MOD_ID);
    private static final DeferredItem<Item> GLASSCUTTER =
        ITEMS.registerItem("glasscutter", props -> GlasscraftItems.buildForDeferred());
    private static final DeferredItem<Item> GLASSCUTTER_HEAD =
        ITEMS.registerItem("glasscutter_head", props -> GlasscraftItems.buildHeadForDeferred());
    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Glasscraft.MOD_ID);
    private static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
        TABS.register("glasscraft", () -> com.glasscraft.common.init.GlasscraftCreativeTab.build());

    public GlasscraftNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        // Config antes de crear el item (durabilidad/velocidad vienen de ahi).
        Path configFile = FMLPaths.CONFIGDIR.get().resolve("glasscraft.json");
        Glasscraft.setConfig(ConfigIo.load(configFile));
        try {
            ConfigIo.save(configFile, Glasscraft.config()); // materializa defaults
        } catch (Exception ignored) {}

        // Config screen para el port NeoForge de ModMenu (lee IConfigScreenFactory).
        modContainer.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
            new com.glasscraft.neoforge.compat.GlasscraftConfigScreenFactory());

        // Puente de guardado para la pantalla de config.
        com.glasscraft.common.client.GlasscraftConfigScreenHost.init(configFile, path -> {
            try {
                ConfigIo.save(path, Glasscraft.config());
            } catch (Exception e) {
                LOGGER.error("No se pudo guardar la config de Glasscraft", e);
            }
        });

        // DeferredRegister hace el registro en el momento correcto del ciclo FML.
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);

        // Puentes para que el codigo comun (mixins, Jade) encuentre los items.
        GlasscraftItems.bind(GLASSCUTTER::get);
        GlasscraftItems.bindHead(GLASSCUTTER_HEAD::get);

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
