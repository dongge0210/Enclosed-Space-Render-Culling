package com.dongge0210.enclosedculling.client;

import com.dongge0210.enclosedculling.room.RoomManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class CullingRenderer {
    private static final int CACHE_SIZE = 4096;
    // LRU缓存
    private static final LinkedHashMap<BlockPos, Boolean> occlusionCache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private static final int CHECK_RADIUS = 3;

    public static boolean isPositionOccluded(Level world, BlockPos pos, Vec3 playerPos) {
        if (!RoomManager.isPositionVisible(world, pos, BlockPos.containing(playerPos))) {
            cacheResult(pos, true);
            return true;
        }

        Boolean cached = occlusionCache.get(pos);
        if (cached != null) return cached;

        if (pos.distSqr(BlockPos.containing(playerPos)) < 16) {
            cacheResult(pos, false);
            return false;
        }
        // 更精细的遮挡判定
        boolean isOccluded = checkEnclosure(world, pos) && !hasLineOfSight(world, pos, playerPos);
        cacheResult(pos, isOccluded);
        return isOccluded;
    }

    // 检查是否被包围
    private static boolean checkEnclosure(Level world, BlockPos center) {
        BlockPos[] directions = {
                center.above(CHECK_RADIUS),
                center.below(CHECK_RADIUS),
                center.north(CHECK_RADIUS),
                center.south(CHECK_RADIUS),
                center.east(CHECK_RADIUS),
                center.west(CHECK_RADIUS)
        };
        int solidSides = 0;
        for (BlockPos checkPos : directions) {
            if (isSolidBlock(world, checkPos)) {
                solidSides++;
            }
        }
        return solidSides >= 5;
    }

    // 高精度视线判定（Bresenham线段算法）
    private static boolean hasLineOfSight(Level world, BlockPos target, Vec3 playerPos) {
        BlockPos playerBlock = BlockPos.containing(playerPos);
        int x1 = playerBlock.getX(), y1 = playerBlock.getY(), z1 = playerBlock.getZ();
        int x2 = target.getX(), y2 = target.getY(), z2 = target.getZ();
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);
        int xs = x1 < x2 ? 1 : -1;
        int ys = y1 < y2 ? 1 : -1;
        int zs = z1 < z2 ? 1 : -1;

        int n = 1 + dx + dy + dz;
        int x = x1, y = y1, z = z1;
        int err_1 = dx - dy, err_2 = dx - dz;

        for (int i = 0; i < n; ++i) {
            if (isSolidBlock(world, new BlockPos(x, y, z))) return false;
            if (2 * err_1 > -dy) {
                err_1 -= dy;
                x += xs;
            }
            if (2 * err_2 > -dz) {
                err_2 -= dz;
                x += xs;
            }
            if (2 * err_1 < dx) {
                err_1 += dx;
                y += ys;
            }
            if (2 * err_2 < dx) {
                err_2 += dx;
                z += zs;
            }
        }
        return true;
    }

    private static boolean isSolidBlock(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.isAir() && state.isSolidRender(world, pos);
    }

    private static void cacheResult(BlockPos pos, boolean result) {
        occlusionCache.put(pos.immutable(), result);
    }

    public static void cleanCache() {
        occlusionCache.clear();
    }
}