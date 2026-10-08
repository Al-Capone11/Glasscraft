package com.glasscraft.common.client;

import com.glasscraft.common.Glasscraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Pantalla de configuracion de Glasscraft (GUI vanilla 26.2, sin deps de
 * Cloth/Configured: sistema ligero). La comparten ambos loaders; Fabric la
 * registra via entrypoint "modmenu" y NeoForge via IConfigScreenFactory (lo
 * que lee el port NeoForge de ModMenu).
 *
 * 26.2: el render de screens es extractRenderState(GuiGraphicsExtractor,..)
 * y el fondo extractBackground (verificado por javap).
 */
public class GlasscraftConfigScreen extends Screen {

    private static final int BUTTON_W = 250;
    private static final int BUTTON_H = 20;
    private static final int ROW = 24;

    private final Screen parent;

    public GlasscraftConfigScreen(Screen parent) {
        super(Component.translatable("glasscraft.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = (this.width - BUTTON_W) / 2;
        int y = 56;

        for (ToggleDef def : TOGGLES) {
            this.addRenderableWidget(Button.builder(def.label(), b -> {
                    def.toggle();
                    b.setMessage(def.label());
                })
                .bounds(x, y, BUTTON_W, BUTTON_H)
                .build());
            y += ROW;
        }

        // Durabilidad (slider 1..1000)
        this.addRenderableWidget(new AbstractSliderButton(x, y, BUTTON_W, AbstractSliderButton.DEFAULT_HEIGHT,
                durabilityLabel(), durabilitySlider()) {
            @Override
            protected void updateMessage() {
                setMessage(durabilityLabel());
            }

            @Override
            protected void applyValue() {
                Glasscraft.config().item.durability = Math.max(1, (int) Math.round(this.value * 1000));
                this.setMessage(durabilityLabel());
            }
        });
        y += ROW + 8;

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(x, y, BUTTON_W, BUTTON_H)
            .build());
    }

    private double durabilitySlider() {
        return Math.min(1.0, Glasscraft.config().item.durability / 1000.0);
    }

    private Component durabilityLabel() {
        return Component.translatable("glasscraft.config.durability",
            Glasscraft.config().item.durability);
    }

    @Override
    public void onClose() {
        GlasscraftConfigScreenHost.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.centeredText(this.font, this.title, this.width / 2, 24, 0xFFFFFF);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    /** Definiciones de toggle. */
    private interface ToggleDef {
        Component label();
        void toggle();
    }

    private static final ToggleDef[] TOGGLES = {
        def("glasscraft.config.continuity_enabled",
            () -> Glasscraft.config().continuity.enabled,
            v -> Glasscraft.config().continuity.enabled = v),
        def("glasscraft.config.vanilla_glass",
            () -> Glasscraft.config().continuity.vanillaGlass,
            v -> Glasscraft.config().continuity.vanillaGlass = v),
        def("glasscraft.config.glass_panes",
            () -> Glasscraft.config().continuity.glassPanes,
            v -> Glasscraft.config().continuity.glassPanes = v),
        def("glasscraft.config.tinted_glass",
            () -> Glasscraft.config().continuity.tintedGlass,
            v -> Glasscraft.config().continuity.tintedGlass = v),
        def("glasscraft.config.glowstone",
            () -> Glasscraft.config().continuity.glowstone,
            v -> Glasscraft.config().continuity.glowstone = v),
        def("glasscraft.config.sea_lantern",
            () -> Glasscraft.config().continuity.seaLantern,
            v -> Glasscraft.config().continuity.seaLantern = v),
        def("glasscraft.config.prismarine",
            () -> Glasscraft.config().continuity.prismarine,
            v -> Glasscraft.config().continuity.prismarine = v),
    };

    private static ToggleDef def(String key, java.util.function.Supplier<Boolean> getter,
                                java.util.function.Consumer<Boolean> setter) {
        return new ToggleDef() {
            @Override
            public Component label() {
                return Component.translatable(key).append(": ")
                    .append(getter.get() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
            }

            @Override
            public void toggle() {
                setter.accept(!getter.get());
            }
        };
    }
}
