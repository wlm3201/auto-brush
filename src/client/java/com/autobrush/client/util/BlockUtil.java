package com.autobrush.client.util;

import com.autobrush.client.compat.PlatformImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

public final class BlockUtil {
    public static boolean canBreak(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return false;
        BlockState state = mc.level.getBlockState(pos);
        return state.getShape(mc.level, pos) != Shapes.empty();
    }

    public static boolean breakBlock(Minecraft mc, BlockPos pos, Direction direction) {
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (gameMode == null || mc.level == null || mc.player == null) return false;
        if (!canBreak(mc, pos)) return false;

        BlockPos target = new BlockPos(pos.getX(), pos.getY(), pos.getZ());

        if (gameMode.isDestroying()) gameMode.continueDestroyBlock(target, direction);
        else gameMode.startDestroyBlock(target, direction);

        PlatformImpl.swing(mc.player, InteractionHand.MAIN_HAND);
        return true;
    }

    private BlockUtil() {}
}
