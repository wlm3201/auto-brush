package com.autobrush.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SET_TYPE = new TypeToken<Set<String>>() {}.getType();

    public static final ModConfig INSTANCE = new ModConfig();

    public boolean enabled = false;

    public boolean breakBad = true;
    public boolean preventBreak = true;
    public int revealTimeoutTicks = 20;

    public boolean render = true;
    public int sandLineColor = 0xFFFFFF00;
    public int gravelLineColor = 0xFFFFFFFF;

    public boolean predict = true;
    public int predictedGoodColor = 0xFF55FF55;
    public int predictedBadColor = 0xFFFF5555;
    public Set<String> targetItems = new LinkedHashSet<>(ArchaeologyLoot.defaultTargetIds());

    public boolean isTargetItem(Item item) {
        return targetItems.contains(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    public void copyFrom(ModConfig other) {
        this.enabled = other.enabled;
        this.breakBad = other.breakBad;
        this.preventBreak = other.preventBreak;
        this.revealTimeoutTicks = other.revealTimeoutTicks;

        this.render = other.render;
        this.sandLineColor = other.sandLineColor;
        this.gravelLineColor = other.gravelLineColor;

        this.predict = other.predict;
        this.predictedGoodColor = other.predictedGoodColor;
        this.predictedBadColor = other.predictedBadColor;

        this.targetItems = new LinkedHashSet<>(other.targetItems);
    }

    public void load() {
        Path path = path();
        if (!Files.isRegularFile(path)) return;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement json = GSON.fromJson(reader, JsonElement.class);
            if (json == null) return;

            ModConfig loaded = GSON.fromJson(json, ModConfig.class);
            if (loaded == null) return;

            copyFrom(loaded);

            JsonElement targets = json.getAsJsonObject().get("targetItems");
            if (targets != null && targets.isJsonArray()) {
                Set<String> ids = GSON.fromJson(targets, SET_TYPE);
                if (ids != null) {
                    LinkedHashSet<String> normalised = new LinkedHashSet<>();
                    ids.forEach(id -> normalised.add(ArchaeologyLoot.fullId(id)));
                    this.targetItems = normalised;
                }
            }
        } catch (IOException e) {
            com.autobrush.client.AutoBrushClient.LOGGER.error("Failed to load autobrush config", e);
        }
    }

    public void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            com.autobrush.client.AutoBrushClient.LOGGER.error("Failed to save autobrush config", e);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("autobrush.json");
    }
}
