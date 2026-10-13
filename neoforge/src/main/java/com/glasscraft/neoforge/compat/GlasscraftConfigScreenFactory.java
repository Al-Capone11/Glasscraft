package com.glasscraft.neoforge.compat;

import com.glasscraft.common.client.GlasscraftConfigScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Config screen para el port NeoForge de ModMenu (y cualquier mod que hable
 * IConfigScreenFactory, p.ej. el boton de config de NeoForge). Se registra
 * como extension point del ModContainer en GlasscraftNeoForge.
 */
public class GlasscraftConfigScreenFactory implements IConfigScreenFactory {

    @Override
    public Screen createScreen(ModContainer modContainer, Screen parent) {
        return new GlasscraftConfigScreen(parent);
    }
}
