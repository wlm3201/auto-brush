package com.autobrush.client.util;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class MsgUtil {
    private static final String PREFIX = "autobrush.msg.";
    private static final Map<String, Long> LAST_OVERLAY = new HashMap<>();

    public static void send(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        player.sendSystemMessage(Component.literal("[AutoBrush] ").append(message));
    }

    public static void overlay(String key, Object... args) {
        overlay(key, Component.translatable(PREFIX + key, args), 500L);
    }

    public static void overlay(String key, Component message, long throttleMs) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        long now = System.currentTimeMillis();
        Long last = LAST_OVERLAY.get(key);
        if (last != null && now - last < throttleMs) return;
        LAST_OVERLAY.put(key, now);

        player.sendOverlayMessage(message);
    }

    public static MutableComponent message(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }
}
