package com.autobrush.client.mixin;

import com.autobrush.client.AutoBrushClient;
import java.io.File;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public class OptionsMixin {
    @Mutable @Shadow public KeyMapping[] keyMappings;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void autoBrush$appendKeyMappings(
            Minecraft minecraft, File workingDirectory, CallbackInfo ci) {
        KeyMapping[] added = AutoBrushClient.keyMappings();
        KeyMapping[] merged = new KeyMapping[keyMappings.length + added.length];

        System.arraycopy(keyMappings, 0, merged, 0, keyMappings.length);
        System.arraycopy(added, 0, merged, keyMappings.length, added.length);

        keyMappings = merged;
    }
}
