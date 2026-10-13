package com.glasscraft.fabric.compat;

import com.glasscraft.common.client.GlasscraftConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;

/**
 * Entrypoint "modmenu" de Fabric (fabric.mod.json). Solo se instancia si
 * ModMenu esta presente; sin ModMenu, Fabric no llama al entrypoint y esta
 * clase ni se carga.
 */
public class ModMenuCompat implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (ConfigScreenFactory<Screen>) GlasscraftConfigScreen::new;
    }
}
