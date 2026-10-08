package com.glasscraft.neoforge.compat;

import com.glasscraft.common.compat.jade.GlasscraftJadePlugin;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Plugin Jade para NeoForge: la anotacion @WailaPlugin (RUNTIME) la descubre
 * Jade via ModFileScanData — no necesita entrypoint ni registro manual
 * (mecanismo verificado en el bytecode de CommonProxy$Entrypoint de Jade).
 */
@WailaPlugin
public class JadeCompat implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        GlasscraftJadePlugin.registerClient(registration);
    }
}
