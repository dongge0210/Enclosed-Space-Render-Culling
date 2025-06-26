package com.dongge0210.enclosedculling.client;

import com.dongge0210.enclosedculling.room.RoomManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CullingRenderer {
    private static final Map<BlockPos, Boolean> occlusionCache = new ConcurrentHashMap<>();
    private static final int CACHE_SIZE = 1000;
    private static final int CHECK_RADIUS = 3;

    public static boolean isPositionOccluded(Level world, BlockPos pos, Vec3 playerPos) {
        // 房间-门系统剔除：玩家不可见房间直接不渲染
        if (!RoomManager.isPositionVisible(world, pos, BlockPos.containing(playerPos))) {
            cacheResult(pos, true);
            return true;
        }

        if (occlusionCache.containsKey(pos)) {
            return occlusionCache.get(pos);
        }
        if (pos.distSqr(BlockPos.containing(playerPos)) < 16) {
            cacheResult(pos, false);
            return false;
        }
        boolean isOccluded = checkEnclosure(world, pos) && !hasLineOfSight(world, pos, playerPos);
        cacheResult(pos, isOccluded);
        return isOccluded;
    }

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

    private static boolean hasLineOfSight(Level world, BlockPos target, Vec3 playerPos) {
        Vec3 targetPos = Vec3.atCenterOf(target);
        Vec3 direction = targetPos.subtract(playerPos).normalize();
        double distance = playerPos.distanceTo(targetPos);
        int steps = (int) Math.min(distance, 20);
        for (int i = 1; i < steps; i++) {
            Vec3 checkPos = playerPos.add(direction.scale(i));
            BlockPos blockPos = BlockPos.containing(checkPos);
            if (isSolidBlock(world, blockPos)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSolidBlock(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.isAir() && state.isSolidRender(world, pos);
    }

    private static void cacheResult(BlockPos pos, boolean result) {
        if (occlusionCache.size() > CACHE_SIZE) {
            occlusionCache.clear();
        }
        occlusionCache.put(pos.immutable(), result);
    }

    public static void cleanCache() {
        if (Minecraft.getInstance().level != null) {
            occlusionCache.clear();
        }
    }
}