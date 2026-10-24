package com.autobrush.client.util;

import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class InvUtils {
    public static int findHotbarSlot(LocalPlayer player, Predicate<ItemStack> filter) {
        Inventory inv = player.getInventory();
        int selected = inv.getSelectedSlot();
        if (filter.test(inv.getItem(selected))) return selected;

        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (filter.test(inv.getItem(i))) return i;
        }

        return -1;
    }

    public static void swap(Minecraft mc, LocalPlayer player, int slot) {
        Inventory inv = player.getInventory();
        if (slot < 0 || slot >= Inventory.getSelectionSize()) return;
        if (inv.getSelectedSlot() == slot) return;

        inv.setSelectedSlot(slot);
        ClientPacketListener connection = mc.getConnection();
        if (connection != null) connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private InvUtils() {}
}
