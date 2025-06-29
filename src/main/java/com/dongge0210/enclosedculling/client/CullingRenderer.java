package com.dongge0210.enclosedculling.client;

import com.dongge0210.enclosedculling.room.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 渲染剔除器 - 负责判断方块位置是否被遮挡
 * 通过房间系统和视线检测来优化渲染性能
 */
public class CullingRenderer {
    
    // === 配置常量 ===
    private static final int CACHE_SIZE = 4096;
    private static final int CHECK_RADIUS = 3;
    private static final double CLOSE_DISTANCE_THRESHOLD = 16.0;
    
    // === LRU缓存 ===
    private static final LinkedHashMap<BlockPos, Boolean> occlusionCache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    
    // === 主要方法 ===
    
    /**
     * 判断位置是否被遮挡
     * @param world 世界实例
     * @param pos 要检查的位置
     * @param playerPos 玩家位置
     * @return true 如果位置被遮挡
     */
    public static boolean isPositionOccluded(Level world, BlockPos pos, Vec3 playerPos) {
        // 首先使用房间管理器快速判断
        if (!RoomManager.isPositionVisible(world, pos, BlockPos.containing(playerPos))) {
            cacheResult(pos, true);
            return true;
        }
        
        // 检查缓存
        Boolean cached = occlusionCache.get(pos);
        if (cached != null) {
            return cached;
        }
        
        // 近距离不剔除，确保玩家附近的物体始终可见
        if (pos.distSqr(BlockPos.containing(playerPos)) < CLOSE_DISTANCE_THRESHOLD) {
            cacheResult(pos, false);
            return false;
        }
        
        // 执行详细的遮挡检测
        boolean isOccluded = checkEnclosure(world, pos) && !hasLineOfSight(world, pos, playerPos);
        cacheResult(pos, isOccluded);
        return isOccluded;
    }

    
    // === 私有辅助方法 ===
    
    /**
     * 检查方块是否被周围方块包围
     * @param world 世界实例
     * @param center 中心位置
     * @return true 如果被包围
     */
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
        
        // 至少5个方向被阻挡才认为是被包围的
        return solidSides >= 5;
    }

    
    /**
     * 使用 Bresenham 3D 算法进行高精度视线检测
     * @param world 世界实例
     * @param target 目标位置
     * @param playerPos 玩家位置
     * @return true 如果有视线
     */
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
            // 如果路径上有实体方块，则视线被阻挡
            if (isSolidBlock(world, new BlockPos(x, y, z))) {
                return false;
            }
            
            // Bresenham 3D 算法步进
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

    
    /**
     * 判断方块是否为实体方块
     * @param world 世界实例
     * @param pos 方块位置
     * @return true 如果是实体方块
     */
    private static boolean isSolidBlock(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.isAir() && state.isSolidRender(world, pos);
    }
    
    /**
     * 缓存遮挡检测结果
     * @param pos 位置
     * @param result 检测结果
     */
    private static void cacheResult(BlockPos pos, boolean result) {
        occlusionCache.put(pos.immutable(), result);
    }
    
    // === 公共工具方法 ===
    
    /**
     * 清理遮挡缓存
     */
    public static void cleanCache() {
        occlusionCache.clear();
    }
}