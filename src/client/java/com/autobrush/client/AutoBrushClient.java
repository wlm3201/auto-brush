package com.autobrush.client;

import com.autobrush.client.compat.PlatformImpl;
import com.autobrush.client.config.ModConfig;
import com.autobrush.client.config.ModConfigScreen;
import com.autobrush.client.feature.Archaeology;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoBrushClient implements ClientModInitializer {
    public static final String MOD_ID = "autobrush";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "autobrush"));

    public static final KeyMapping TOGGLE = PlatformImpl.toggleKey(CATEGORY);
    public static final KeyMapping SETTINGS = PlatformImpl.settingsKey(CATEGORY);

    public static KeyMapping[] keyMappings() {
        return new KeyMapping[] {TOGGLE, SETTINGS};
    }

    @Override
    public void onInitializeClient() {
        ModConfig.INSTANCE.load();

        ClientTickEvents.START_CLIENT_TICK.register(
                mc -> {
                    while (TOGGLE.consumeClick())
                        Archaeology.INSTANCE.setEnabled(!Archaeology.INSTANCE.isEnabled());
                    while (SETTINGS.consumeClick()) openSettings(mc);

                    Archaeology.INSTANCE.tickStart(mc);
                });

        ClientTickEvents.END_CLIENT_TICK.register(Archaeology.INSTANCE::tickEnd);
    }

    private static void openSettings(Minecraft mc) {
        mc.setScreenAndShow(ModConfigScreen.create(PlatformImpl.currentScreen(mc)));
    }
}
