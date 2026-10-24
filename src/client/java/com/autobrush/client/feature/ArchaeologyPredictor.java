package com.autobrush.client.feature;

import com.autobrush.client.AutoBrushClient;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class ArchaeologyPredictor {
    public record Entry(String id, int weight) {
        public static Entry of(String id) {
            return new Entry(id, 1);
        }

        public static Entry of(String id, int weight) {
            return new Entry(id, weight);
        }
    }

    public enum Table {
        DESERT_PYRAMID(
                "archaeology/desert_pyramid",
                List.of(
                        Entry.of("archer_pottery_sherd"),
                        Entry.of("miner_pottery_sherd"),
                        Entry.of("prize_pottery_sherd"),
                        Entry.of("skull_pottery_sherd"),
                        Entry.of("diamond"),
                        Entry.of("tnt"),
                        Entry.of("gunpowder"),
                        Entry.of("emerald"))),
        DESERT_WELL(
                "archaeology/desert_well",
                List.of(
                        Entry.of("arms_up_pottery_sherd", 2),
                        Entry.of("brewer_pottery_sherd", 2),
                        Entry.of("brick"),
                        Entry.of("emerald"),
                        Entry.of("stick"),
                        Entry.of("suspicious_stew")));

        public final String path;
        public final List<Entry> entries;

        Table(String path, List<Entry> entries) {
            this.path = path;
            this.entries = entries;
        }

        public int totalWeight() {
            int sum = 0;
            for (Entry e : entries) sum += e.weight;
            return sum;
        }
    }

    private ArchaeologyPredictor() {}

    public static Optional<Item> predict(ClientLevel level, BlockPos pos) {
        if (level == null) return Optional.empty();

        if (isInsideDesertWell(level, pos)) {
            return DesertWellSeeding.POSITION_SEEDED
                    ? pick(Table.DESERT_WELL, pos.asLong())
                    : Optional.empty();
        }

        Table table = tableFor(level, pos);
        if (table == null) return Optional.empty();

        long seed = seedFor(table, pos);
        return pick(table, seed);
    }

    private static Table tableFor(ClientLevel level, BlockPos pos) {
        if (isInsideDesertPyramid(level, pos)) return Table.DESERT_PYRAMID;
        return null;
    }

    private static long seedFor(Table table, BlockPos pos) {
        return switch (table) {
            case DESERT_PYRAMID, DESERT_WELL -> pos.asLong();
        };
    }

    public static Optional<Item> pick(Table table, long seed) {
        int total = table.totalWeight();
        if (total <= 0) return Optional.empty();

        RandomSource random = RandomSource.create(seed);
        int roll = random.nextInt(total);

        for (Entry entry : table.entries) {
            roll -= entry.weight;
            if (roll < 0) return item(entry.id());
        }
        return Optional.empty();
    }

    private static Optional<Item> item(String id) {
        try {
            Item item =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getOptional(Identifier.parse(id))
                            .orElse(null);
            return Optional.ofNullable(item);
        } catch (Exception e) {
            AutoBrushClient.LOGGER.warn("autobrush: unknown item id {}", id);
            return Optional.empty();
        }
    }

    private static final int[][] WELL_CROSS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static boolean isInsideDesertWell(ClientLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos centre = new BlockPos.MutableBlockPos();

        for (int dy = 1; dy <= 2; dy++) {
            for (int[] offset : WELL_CROSS) {
                centre.set(pos.getX() - offset[0], pos.getY() + dy, pos.getZ() - offset[1]);
                if (isWellCentre(level, centre)) return true;
            }
        }
        return false;
    }

    private static boolean isWellCentre(ClientLevel level, BlockPos centre) {
        if (!isWater(level, centre)) return false;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (!isWater(level, centre.relative(dir))) return false;
        }

        if (count(level, centre, -1, Cells.CROSS, ArchaeologyPredictor::isSandOrSusSand) < 5)
            return false;
        if (count(level, centre, -1, Cells.NOT_CROSS, ArchaeologyPredictor::isSandstone) < 18)
            return false;

        if (count(level, centre, -2, Cells.ALL, b -> isSandstone(b) || b == Blocks.SUSPICIOUS_SAND)
                < 23) return false;

        if (count(level, centre, 1, Cells.RING, ArchaeologyPredictor::isSandstone) < 12)
            return false;
        if (count(level, centre, 1, Cells.PILLARS, ArchaeologyPredictor::isSandstone) < 3)
            return false;

        return true;
    }

    private static boolean isWater(ClientLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.WATER);
    }

    private static boolean isSandOrSusSand(Block block) {
        return block == Blocks.SAND || block == Blocks.SUSPICIOUS_SAND;
    }

    private static boolean isSandstone(Block block) {
        return block == Blocks.SANDSTONE
                || block == Blocks.CUT_SANDSTONE
                || block == Blocks.CHISELED_SANDSTONE
                || block == Blocks.SANDSTONE_SLAB
                || block == Blocks.SANDSTONE_STAIRS
                || block == Blocks.SANDSTONE_WALL;
    }

    private enum Cells {
        ALL,
        CROSS,
        NOT_CROSS,
        RING,
        PILLARS;

        private boolean matches(int dx, int dz) {
            boolean cross = Math.abs(dx) + Math.abs(dz) <= 1;

            return switch (this) {
                case ALL -> true;
                case CROSS -> cross;
                case NOT_CROSS -> !cross;
                case RING -> Math.max(Math.abs(dx), Math.abs(dz)) == 2;
                case PILLARS -> Math.abs(dx) == 1 && Math.abs(dz) == 1;
            };
        }
    }

    private static int count(
            ClientLevel level, BlockPos centre, int dy, Cells cells, Predicate<Block> matcher) {
        int y = centre.getY() + dy;
        if (level.isOutsideBuildHeight(y)) return 0;

        int found = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!cells.matches(dx, dz)) continue;

                p.set(centre.getX() + dx, y, centre.getZ() + dz);
                if (matcher.test(level.getBlockState(p).getBlock())) found++;
            }
        }
        return found;
    }

    private static boolean isInsideDesertPyramid(ClientLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int dy = -5; dy <= 1; dy++) {
            int y = pos.getY() + dy;
            if (level.isOutsideBuildHeight(y)) continue;

            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    p.set(pos.getX() + dx, y, pos.getZ() + dz);
                    if (level.getBlockState(p).is(BlockTags.TERRACOTTA)) return true;
                }
            }
        }
        return false;
    }

    static {
        AutoBrushClient.LOGGER.debug(
                "ArchaeologyPredictor initialised (tables: {})", Table.values().length);
    }
}
