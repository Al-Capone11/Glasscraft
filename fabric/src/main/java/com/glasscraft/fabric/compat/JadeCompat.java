package com.glasscraft.fabric.compat;

import com.glasscraft.common.compat.jade.GlasscraftJadePlugin;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;

/**
 * Entrypoint "jade" de Fabric (fabric.mod.json). Solo se instancia si Jade
 * esta presente; sin Jade, Fabric no llama al entrypoint y la clase ni se
 * carga (Jade es un suggests, no un depends).
 */
public class JadeCompat implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        GlasscraftJadePlugin.registerClient(registration);
    }
}
