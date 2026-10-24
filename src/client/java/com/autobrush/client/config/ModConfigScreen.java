package com.autobrush.client.config;

import com.autobrush.client.feature.Archaeology;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ModConfigScreen {
    private static final ModConfig CFG = ModConfig.INSTANCE;

    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("autobrush.title"))
                .category(generalCategory())
                .category(targetItemsCategory())
                .save(ModConfig.INSTANCE::save)
                .build()
                .generateScreen(parent);
    }

    private static ConfigCategory generalCategory() {
        return ConfigCategory.createBuilder()
                .name(Component.translatable("autobrush.category.general"))
                .tooltip(Component.translatable("autobrush.category.general.tooltip"))
                .option(
                        bool(
                                "enabled",
                                Archaeology.INSTANCE::isEnabled,
                                value -> Archaeology.INSTANCE.setEnabled(value)))
                .option(bool("break-bad", () -> CFG.breakBad, value -> CFG.breakBad = value))
                .option(
                        bool(
                                "prevent-accidental-breakage",
                                () -> CFG.preventBreak,
                                value -> CFG.preventBreak = value))
                .option(
                        intOption(
                                "reveal-timeout",
                                20,
                                () -> CFG.revealTimeoutTicks,
                                value -> CFG.revealTimeoutTicks = value))
                .option(bool("predict", () -> CFG.predict, value -> CFG.predict = value))
                .option(
                        bool(
                                "render-suspicious-blocks",
                                () -> CFG.render,
                                value -> CFG.render = value))
                .option(
                        colorOption(
                                "sand-line-color",
                                0xFFFFFF00,
                                () -> CFG.sandLineColor,
                                value -> CFG.sandLineColor = value))
                .option(
                        colorOption(
                                "gravel-line-color",
                                0xFFFFFFFF,
                                () -> CFG.gravelLineColor,
                                value -> CFG.gravelLineColor = value))
                .option(
                        colorOption(
                                "predicted-good-color",
                                0xFF55FF55,
                                () -> CFG.predictedGoodColor,
                                value -> CFG.predictedGoodColor = value))
                .option(
                        colorOption(
                                "predicted-bad-color",
                                0xFFFF5555,
                                () -> CFG.predictedBadColor,
                                value -> CFG.predictedBadColor = value))
                .build();
    }

    private static Option<String> stringOption(
            String key, Supplier<String> getter, Consumer<String> setter) {
        return Option.<String>createBuilder()
                .name(Component.translatable("autobrush.option." + key))
                .description(
                        OptionDescription.of(
                                Component.translatable("autobrush.option." + key + ".desc")))
                .binding("", getter, setter)
                .controller(StringControllerBuilder::create)
                .build();
    }

    private static ConfigCategory targetItemsCategory() {
        ConfigCategory.Builder builder =
                ConfigCategory.createBuilder()
                        .name(Component.translatable("autobrush.category.targets"))
                        .tooltip(
                                Component.translatable("autobrush.category.targets.tooltip.1"),
                                Component.translatable("autobrush.category.targets.tooltip.2"));

        List<Option<Boolean>> sherds = new ArrayList<>();
        List<Option<Boolean>> rare = new ArrayList<>();
        List<Option<Boolean>> common = new ArrayList<>();

        for (ArchaeologyLoot.Group group : ArchaeologyLoot.GROUPS) {
            List<Option<Boolean>> target =
                    switch (group.kind()) {
                        case SHERDS -> sherds;
                        case RARE -> rare;
                        case COMMON -> common;
                    };

            for (String fullId : group.ids()) {
                target.add(
                        Option.<Boolean>createBuilder()
                                .name(ArchaeologyLoot.name(fullId))
                                .description(OptionDescription.of(Component.literal(fullId)))
                                .binding(
                                        ArchaeologyLoot.defaultTargetIds().contains(fullId),
                                        () -> CFG.targetItems.contains(fullId),
                                        value -> {
                                            if (value) CFG.targetItems.add(fullId);
                                            else CFG.targetItems.remove(fullId);
                                        })
                                .controller(
                                        option ->
                                                BooleanControllerBuilder.create(option)
                                                        .coloured(true))
                                .build());
            }
        }

        builder.group(group("autobrush.group.sherds", sherds));
        builder.group(group("autobrush.group.rare", rare));
        builder.group(group("autobrush.group.common", common));

        return builder.build();
    }

    private static OptionGroup group(String translationKey, List<Option<Boolean>> options) {
        return OptionGroup.createBuilder()
                .name(Component.translatable(translationKey))
                .options(options)
                .collapsed(true)
                .build();
    }

    private static Option<Boolean> bool(
            String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable("autobrush.option." + key))
                .description(
                        OptionDescription.of(
                                Component.translatable("autobrush.option." + key + ".desc")))
                .binding(defaultBoolean(key), getter, setter)
                .controller(option -> BooleanControllerBuilder.create(option).coloured(true))
                .build();
    }

    private static boolean defaultBoolean(String key) {
        return switch (key) {
            case "enabled" -> true;
            case "break-bad" -> true;
            case "prevent-accidental-breakage" -> true;
            case "render-suspicious-blocks" -> true;
            case "predict" -> true;
            default -> false;
        };
    }

    private static Option<Integer> intOption(
            String key, int def, Supplier<Integer> getter, Consumer<Integer> setter) {
        return Option.<Integer>createBuilder()
                .name(Component.translatable("autobrush.option." + key))
                .description(
                        OptionDescription.of(
                                Component.translatable("autobrush.option." + key + ".desc")))
                .binding(def, getter, setter)
                .controller(
                        option ->
                                IntegerSliderControllerBuilder.create(option).range(5, 60).step(1))
                .build();
    }

    private static Option<Color> colorOption(
            String key, int defArgb, Supplier<Integer> getter, Consumer<Integer> setter) {
        return Option.<Color>createBuilder()
                .name(Component.translatable("autobrush.option." + key))
                .description(
                        OptionDescription.of(
                                Component.translatable("autobrush.option." + key + ".desc")))
                .binding(
                        new Color(defArgb, true),
                        () -> new Color(getter.get(), true),
                        value -> setter.accept(value.getRGB()))
                .controller(option -> ColorControllerBuilder.create(option).allowAlpha(true))
                .build();
    }

    private ModConfigScreen() {}
}
