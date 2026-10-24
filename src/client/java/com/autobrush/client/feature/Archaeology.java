package com.autobrush.client.feature;

import com.autobrush.client.AutoBrushClient;
import com.autobrush.client.config.ModConfig;
import com.autobrush.client.util.BlockUtil;
import com.autobrush.client.util.InvUtils;
import com.autobrush.client.util.MsgUtil;
import com.autobrush.client.util.RenderUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class Archaeology {
    public static final Archaeology INSTANCE = new Archaeology();

    private static final int REFRESH_INTERVAL_TICKS = 5;
    private static final int TRACK_RANGE = 256;
    private static final int SCAN_CHUNK_RANGE = TRACK_RANGE >> 4;
    private static final long NO_BRUSH_INTERVAL_MS = 7777L;

    private static final int GOOD = 0x55FF55;
    private static final int BAD = 0xFF5555;

    private static final int YELLOW_LINE = 0xFF89A904;

    private int timer;
    private int ticksBrushing;
    private long lastNoBrushNotice;
    private ClientLevel lastLevel;

    private final Set<Long> toIgnore = new HashSet<>();
    private final Set<BlockPos> goodBlocks = new HashSet<>();
    private final Set<BlockPos> safeBlocksToBrush = new HashSet<>();
    private final Set<BlockPos> safeBlocksToBreak = new HashSet<>();
    private final Set<BlockPos> preventingBreakageBlocks = new HashSet<>();
    private final List<BlockPos> suspiciousSandBlocks = new ArrayList<>();
    private final List<BlockPos> suspiciousGravelBlocks = new ArrayList<>();
    private final BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos();

    private final Map<BlockPos, Optional<Item>> predictions = new HashMap<>();

    private Optional<Item> prediction(ClientLevel level, BlockPos pos) {
        return predictions.computeIfAbsent(
                pos,
                p -> {
                    ModConfig cfg = ModConfig.INSTANCE;
                    if (!cfg.predict) return Optional.empty();
                    return ArchaeologyPredictor.predict(level, p);
                });
    }

    public boolean isEnabled() {
        return ModConfig.INSTANCE.enabled;
    }

    public void setEnabled(boolean enabled) {
        ModConfig.INSTANCE.enabled = enabled;

        if (!enabled) {
            reset();
            releaseUse(Minecraft.getInstance());
        }

        ModConfig.INSTANCE.save();
        MsgUtil.send(MsgUtil.message(enabled ? "enabled" : "disabled"));
    }

    private void reset() {
        timer = 0;
        ticksBrushing = 0;
        lastNoBrushNotice = 0L;

        toIgnore.clear();
        goodBlocks.clear();
        safeBlocksToBrush.clear();
        safeBlocksToBreak.clear();
        preventingBreakageBlocks.clear();
        suspiciousSandBlocks.clear();
        suspiciousGravelBlocks.clear();
        predictions.clear();
    }

    private void clearIfWorldChanged(Minecraft mc) {
        if (mc.level == lastLevel) return;

        lastLevel = mc.level;
        reset();
        releaseUse(mc);
    }

    public void tickStart(Minecraft mc) {
        clearIfWorldChanged(mc);

        ModConfig cfg = ModConfig.INSTANCE;
        if (!cfg.enabled || mc.level == null || mc.player == null || mc.gameMode == null) return;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        ++timer;
        if (timer >= REFRESH_INTERVAL_TICKS) {
            timer = 0;
            refreshKnownBlocks(level, player);
        }

        HitResult target = mc.hitResult;
        if (!(target instanceof BlockHitResult blockHit)) {
            releaseUse(mc);
            return;
        }

        BlockPos hitPos = blockHit.getBlockPos();
        if (!isBrushable(level, hitPos)) {
            if (holdsBrush(player)) releaseUse(mc);
            return;
        }

        if (toIgnore.contains(hitPos.asLong())) {
            releaseUse(mc);

            if (cfg.breakBad) {
                boolean offhandShovel = isShovel(player.getOffhandItem());
                int shovelSlot = InvUtils.findHotbarSlot(player, Archaeology::isShovel);
                if (!offhandShovel && shovelSlot >= 0) InvUtils.swap(mc, player, shovelSlot);

                if (isSafeToBreak(level, hitPos)) {
                    BlockUtil.breakBlock(mc, hitPos, blockHit.getDirection());
                } else {
                    MsgUtil.overlay("prevent-break");
                }
            }
            return;
        }

        boolean offhandBrush = player.getOffhandItem().is(Items.BRUSH);
        int brushSlot = InvUtils.findHotbarSlot(player, stack -> stack.is(Items.BRUSH));

        if (!offhandBrush && brushSlot < 0) {
            long now = System.currentTimeMillis();
            if (now - lastNoBrushNotice >= NO_BRUSH_INTERVAL_MS) {
                lastNoBrushNotice = now;
                MsgUtil.send(MsgUtil.message("no-brush").withColor(BAD));
            }
            return;
        }

        InteractionHand hand = offhandBrush ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!offhandBrush) InvUtils.swap(mc, player, brushSlot);

        if (isSafeToBrush(level, hitPos)) {
            if (!player.isUsingItem()) mc.gameMode.useItem(player, hand);
            mc.options.keyUse.setDown(true);
        }
    }

    public void tickEnd(Minecraft mc) {
        ModConfig cfg = ModConfig.INSTANCE;
        if (!cfg.enabled || mc.level == null || mc.player == null) return;

        inspectCrosshair(mc);

        if (cfg.render) render(mc);
    }

    private void inspectCrosshair(Minecraft mc) {
        ModConfig cfg = ModConfig.INSTANCE;
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;

        HitResult target = mc.hitResult;
        if (!(target instanceof BlockHitResult blockHit)) {
            ticksBrushing = 0;
            return;
        }

        BlockPos hitPos = blockHit.getBlockPos();
        if (!(level.getBlockEntity(hitPos) instanceof BrushableBlockEntity brushable)) {
            ticksBrushing = 0;
            return;
        }

        if (toIgnore.contains(hitPos.asLong())) return;

        ItemStack stack = brushable.getItem();
        if (stack.isEmpty()) {
            preventingBreakageBlocks.add(hitPos);

            if (player.isUsingItem()) ++ticksBrushing;
            if (ticksBrushing > cfg.revealTimeoutTicks) {
                ticksBrushing = 0;
                player.stopUsingItem();
                mc.options.keyUse.setDown(false);
            }
            return;
        }

        ticksBrushing = 0;
        boolean wanted = cfg.isTargetItem(stack.getItem());

        if (wanted) {
            if (goodBlocks.add(hitPos)) {
                MsgUtil.send(MsgUtil.message("good", stack.getHoverName()).withColor(GOOD));
            }
        } else {
            toIgnore.add(hitPos.asLong());
            MsgUtil.send(MsgUtil.message("bad", stack.getHoverName()).withColor(BAD));
        }

        preventingBreakageBlocks.add(hitPos);
    }

    private void refreshKnownBlocks(ClientLevel level, LocalPlayer player) {
        BlockPos origin = player.blockPosition();

        suspiciousSandBlocks.removeIf(
                pos -> isOutOfRange(pos, origin, TRACK_RANGE) || !isBrushable(level, pos));
        suspiciousGravelBlocks.removeIf(
                pos -> isOutOfRange(pos, origin, TRACK_RANGE) || !isBrushable(level, pos));
        preventingBreakageBlocks.removeIf(pos -> !isBrushable(level, pos));
        goodBlocks.removeIf(pos -> !isBrushable(level, pos));
        predictions
                .keySet()
                .removeIf(
                        pos -> isOutOfRange(pos, origin, TRACK_RANGE) || !isBrushable(level, pos));
        safeBlocksToBrush.removeIf(
                pos -> !isBrushable(level, pos) || isOutOfRange(pos, origin, TRACK_RANGE));
        safeBlocksToBreak.removeIf(
                pos -> !isBrushable(level, pos) || isOutOfRange(pos, origin, TRACK_RANGE));

        ChunkSource chunks = level.getChunkSource();
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;

        for (int cx = chunkX - SCAN_CHUNK_RANGE; cx <= chunkX + SCAN_CHUNK_RANGE; cx++) {
            for (int cz = chunkZ - SCAN_CHUNK_RANGE; cz <= chunkZ + SCAN_CHUNK_RANGE; cz++) {
                LevelChunk chunk = chunks.getChunkNow(cx, cz);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof BrushableBlockEntity)) continue;

                    BlockPos pos = blockEntity.getBlockPos();
                    if (toIgnore.contains(pos.asLong())) continue;
                    if (suspiciousSandBlocks.contains(pos) || suspiciousGravelBlocks.contains(pos))
                        continue;

                    if (level.getBlockState(pos).is(Blocks.SUSPICIOUS_GRAVEL))
                        suspiciousGravelBlocks.add(pos);
                    else suspiciousSandBlocks.add(pos);
                }
            }
        }
    }

    public boolean isPreventingBreakage(BlockPos pos) {
        ModConfig cfg = ModConfig.INSTANCE;
        if (!cfg.enabled || !cfg.preventBreak) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;

        return !isSafeToBreak(mc.level, pos);
    }

    private boolean isSafeToBrush(ClientLevel level, BlockPos pos) {
        if (safeBlocksToBrush.contains(pos)) return true;

        if (isFloating(level, pos)) {
            preventingBreakageBlocks.add(pos.below());
            MsgUtil.overlay("brush-floating");
            return false;
        }

        for (Direction dir : Direction.values()) {
            BlockPos neighbour = pos.relative(dir);
            if (toIgnore.contains(neighbour.asLong()) || !isSuspicious(level, neighbour)) continue;

            if (isFloating(level, neighbour)) {
                preventingBreakageBlocks.add(neighbour);
                MsgUtil.overlay("brush-neighbour");
                return false;
            }
        }

        safeBlocksToBrush.add(pos);
        return true;
    }

    private boolean isSafeToBreak(ClientLevel level, BlockPos pos) {
        if (safeBlocksToBreak.contains(pos)) return true;
        if (!toIgnore.contains(pos.asLong()) && isSuspicious(level, pos)) return false;

        for (Direction dir : Direction.values()) {
            BlockPos neighbour = pos.relative(dir);
            if (toIgnore.contains(neighbour.asLong()) || !isSuspicious(level, neighbour)) continue;

            if (isFloating(level, neighbour)) {
                preventingBreakageBlocks.add(neighbour);
                MsgUtil.overlay("break-neighbour");
                return false;
            }
        }

        BlockPos.MutableBlockPos checkPos =
                new BlockPos.MutableBlockPos(pos.getX(), pos.getY() + 1, pos.getZ());
        while (checkPos.getY() < level.getHeight()) {
            BlockState checkState = level.getBlockState(checkPos);

            if (!toIgnore.contains(checkPos.asLong()) && isSuspicious(level, checkPos)) {
                preventingBreakageBlocks.add(
                        new BlockPos(checkPos.getX(), checkPos.getY(), checkPos.getZ()));
                MsgUtil.overlay("break-indirect");
                return false;
            }

            if (!(checkState.getBlock() instanceof FallingBlock)) break;

            for (Direction dir : Direction.values()) {
                if (dir == Direction.UP || dir == Direction.DOWN) continue;

                BlockPos indirect = checkPos.relative(dir);
                if (toIgnore.contains(indirect.asLong()) || !isSuspicious(level, indirect))
                    continue;

                if (isFloating(level, indirect)) {
                    preventingBreakageBlocks.add(indirect);
                    MsgUtil.overlay("break-indirect");
                    return false;
                }
            }

            checkPos.set(checkPos.getX(), checkPos.getY() + 1, checkPos.getZ());
        }

        safeBlocksToBreak.add(pos);
        return true;
    }

    private void render(Minecraft mc) {
        ModConfig cfg = ModConfig.INSTANCE;

        for (BlockPos pos : preventingBreakageBlocks) {
            if (toIgnore.contains(pos.asLong()))
                RenderUtil.renderOutline(pos, cfg.predictedBadColor);
            else if (goodBlocks.contains(pos))
                RenderUtil.renderOutline(pos, cfg.predictedGoodColor);
            else RenderUtil.renderOutline(pos, YELLOW_LINE);
        }

        ClientLevel level = mc.level;

        for (BlockPos pos : suspiciousSandBlocks) {
            if (preventingBreakageBlocks.contains(pos)) continue;

            boolean known = toIgnore.contains(pos.asLong()) || goodBlocks.contains(pos);

            if (cfg.predict && !known) {
                Optional<Item> guess = prediction(level, pos);
                if (guess.isPresent()) {
                    boolean wanted = cfg.isTargetItem(guess.get());
                    RenderUtil.renderOutline(
                            pos, wanted ? cfg.predictedGoodColor : cfg.predictedBadColor);
                    continue;
                }
            }

            RenderUtil.renderOutline(pos, cfg.sandLineColor);
        }

        for (BlockPos pos : suspiciousGravelBlocks) {
            if (preventingBreakageBlocks.contains(pos)) continue;
            RenderUtil.renderOutline(pos, cfg.gravelLineColor);
        }
    }

    private static void releaseUse(Minecraft mc) {
        mc.options.keyUse.setDown(false);
    }

    private static boolean isShovel(ItemStack stack) {
        return stack.is(ItemTags.SHOVELS);
    }

    private static boolean holdsBrush(LocalPlayer player) {
        return player.getMainHandItem().is(Items.BRUSH) || player.getOffhandItem().is(Items.BRUSH);
    }

    private static boolean isBrushable(ClientLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BrushableBlockEntity;
    }

    private boolean isSuspicious(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.SUSPICIOUS_SAND) || state.is(Blocks.SUSPICIOUS_GRAVEL);
    }

    private static boolean isFloating(ClientLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.isAir() || below.canBeReplaced();
    }

    private boolean isOutOfRange(BlockPos pos, BlockPos reference, int range) {
        if (pos == null || reference == null) return true;

        testPos.set(reference.getX(), pos.getY(), reference.getZ());
        return !pos.closerThan(testPos, range);
    }

    static {
        AutoBrushClient.LOGGER.debug("Archaeology feature initialised");
    }
}
