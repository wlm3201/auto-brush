package com.autobrush.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;

public final class PlatformImpl {
    public static KeyMapping toggleKey(KeyMapping.Category category) {
        return new KeyMapping("key.autobrush.toggle", InputConstants.KEY_B, category);
    }

    public static KeyMapping settingsKey(KeyMapping.Category category) {
        return new KeyMapping(
                "key.autobrush.settings", InputConstants.UNKNOWN.getValue(), category);
    }

    public static void swing(LocalPlayer player, InteractionHand hand) {
        player.swing(hand, SwingAnimation.DEFAULT, true);
    }

    public static Screen currentScreen(Minecraft mc) {
        return mc.gui.screen();
    }

    private PlatformImpl() {}
}
