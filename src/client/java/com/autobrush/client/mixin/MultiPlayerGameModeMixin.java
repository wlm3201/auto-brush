package com.autobrush.client.mixin;

import com.autobrush.client.feature.Archaeology;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void autoBrush$startDestroyBlock(
            BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (Archaeology.INSTANCE.isPreventingBreakage(pos)) cir.cancel();
    }
}
