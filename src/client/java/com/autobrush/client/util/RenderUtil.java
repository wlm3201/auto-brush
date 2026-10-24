package com.autobrush.client.util;

import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;

public final class RenderUtil {
    private static final float CUBOID_PADDING = 0.002F;
    private static final float LINE_WIDTH = 2.0F;

    public static void renderOutline(BlockPos pos, int lineColor) {
        Gizmos.cuboid(pos, CUBOID_PADDING, GizmoStyle.stroke(lineColor, LINE_WIDTH))
                .setAlwaysOnTop();
    }

    private RenderUtil() {}
}
