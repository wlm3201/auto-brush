package com.autobrush.client.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ArchaeologyLoot {
    public enum Kind {
        SHERDS,
        RARE,
        COMMON
    }

    public record Group(Kind kind, String name, boolean collapsed, List<String> ids) {}

    private static final String PREFIX = "minecraft:";

    private static final List<String> SHERDS =
            List.of(
                    "sheaf_pottery_sherd",
                    "shelter_pottery_sherd",
                    "angler_pottery_sherd",
                    "archer_pottery_sherd",
                    "arms_up_pottery_sherd",
                    "blade_pottery_sherd",
                    "brewer_pottery_sherd",
                    "burn_pottery_sherd",
                    "danger_pottery_sherd",
                    "explorer_pottery_sherd",
                    "friend_pottery_sherd",
                    "heart_pottery_sherd",
                    "heartbreak_pottery_sherd",
                    "howl_pottery_sherd",
                    "miner_pottery_sherd",
                    "mourner_pottery_sherd",
                    "plenty_pottery_sherd",
                    "prize_pottery_sherd",
                    "skull_pottery_sherd",
                    "snort_pottery_sherd");

    private static final List<String> VALUABLES =
            List.of(
                    "sniffer_egg",
                    "music_disc_relic",
                    "raiser_armor_trim_smithing_template",
                    "wayfinder_armor_trim_smithing_template",
                    "shaper_armor_trim_smithing_template",
                    "host_armor_trim_smithing_template");

    private static final List<String> COMMON =
            List.of(
                    "emerald",
                    "coal",
                    "tnt",
                    "wheat",
                    "brick",
                    "stick",
                    "suspicious_stew",
                    "diamond",
                    "gunpowder",
                    "wooden_hoe",
                    "iron_axe",
                    "gold_nugget",
                    "blue_dye",
                    "white_dye",
                    "yellow_dye",
                    "dead_bush",
                    "flower_pot",
                    "lead",
                    "wheat_seeds",
                    "string",
                    "spruce_hanging_sign",
                    "clay",
                    "light_blue_dye",
                    "orange_dye",
                    "brown_candle",
                    "green_candle",
                    "red_candle",
                    "purple_candle",
                    "beetroot_seeds",
                    "blue_stained_glass_pane",
                    "light_blue_stained_glass_pane",
                    "magenta_stained_glass_pane",
                    "yellow_stained_glass_pane",
                    "oak_hanging_sign",
                    "pink_stained_glass_pane",
                    "purple_stained_glass_pane",
                    "red_stained_glass_pane");

    public static final List<Group> GROUPS =
            List.of(
                    new Group(Kind.SHERDS, "Pottery Sherds", true, qualify(SHERDS)),
                    new Group(Kind.RARE, "Rare & Templates", true, qualify(VALUABLES)),
                    new Group(Kind.COMMON, "Common Junk", true, qualify(COMMON)));

    public static Set<String> defaultTargetIds() {
        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(qualify(SHERDS));
        ids.addAll(qualify(VALUABLES));
        return ids;
    }

    public static String fullId(String id) {
        if (id.startsWith(PREFIX)) return id;
        return id.contains(":") ? id : PREFIX + id;
    }

    public static Optional<Item> item(String id) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(fullId(id)));
    }

    public static Component name(String id) {
        return item(id).map(i -> new ItemStack(i).getHoverName())
                .orElseGet(() -> Component.literal(id));
    }

    private static List<String> qualify(List<String> ids) {
        return ids.stream().map(ArchaeologyLoot::fullId).toList();
    }

    private ArchaeologyLoot() {}
}
