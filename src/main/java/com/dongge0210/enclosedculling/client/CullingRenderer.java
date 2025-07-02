package com.dongge0210.enclosedculling.client;

import com.dongge0210.enclosedculling.room.RoomManager;
import com.dongge0210.enclosedculling.compat.EntityCullingCompatibility;
import com.dongge0210.enclosedculling.client.gpu.FrustumCuller;
import com.dongge0210.enclosedculling.client.gpu.LODManager;
import com.dongge0210.enclosedculling.client.gpu.BatchRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;

import java.util.*;

/**
 * 渲染剔除器 - 负责判断方块位置是否被遮挡
 * 通过房间系统和视线检测来优化渲染性能
 * 
 * EntityCulling兼容性说明：
 * - 当检测到EntityCulling MOD时，自动跳过实体剔除功能
 * - 专注于方块和空间剔除优化
 * - 避免与EntityCulling功能重叠造成冲突
 */
public class CullingRenderer {
    
    // === 配置常量 ===
    private static final int CACHE_SIZE = 4096;
    private static final int CHECK_RADIUS = 3;
    private static final double CLOSE_DISTANCE_THRESHOLD = 32.0; // 从16.0扩大到32.0
    
    // === LRU缓存 ===
    private static final LinkedHashMap<BlockPos, Boolean> occlusionCache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    
    // === 主要方法 ===
    
    /**
     * 判断位置是否被遮挡（增强版 - 包含GPU优化）
     * @param world 世界实例
     * @param pos 要检查的位置
     * @param playerPos 玩家位置
     * @return true 如果位置被遮挡
     */
    public static boolean isPositionOccluded(Level world, BlockPos pos, Vec3 playerPos) {
        // 1. 视锥剔除检查
        if (!FrustumCuller.isBlockInFrustum(pos)) {
            cacheResult(pos, true);
            // 记录方块剔除统计
            com.dongge0210.enclosedculling.debug.DebugManager.recordBlockCheck(false);
            return true;
        }
        
        // 2. LOD检查
        LODManager.LODLevel lod = LODManager.calculateBlockLOD(pos, playerPos);
        if (lod == LODManager.LODLevel.CULLED) {
            cacheResult(pos, true);
            // 记录方块剔除统计
            com.dongge0210.enclosedculling.debug.DebugManager.recordBlockCheck(false);
            return true;
        }
        
        // 3. 房间系统快速判断
        if (!RoomManager.isPositionVisible(world, pos, BlockPos.containing(playerPos))) {
            cacheResult(pos, true);
            // 记录方块剔除统计
            com.dongge0210.enclosedculling.debug.DebugManager.recordBlockCheck(false);
            return true;
        }
        
        // 4. 检查缓存
        Boolean cached = occlusionCache.get(pos);
        if (cached != null) {
            // 记录方块剔除统计（缓存命中）
            com.dongge0210.enclosedculling.debug.DebugManager.recordBlockCheck(!cached);
            return cached;
        }
        
        // 5. 近距离不剔除，确保玩家附近的物体始终可见
        if (pos.distSqr(BlockPos.containing(playerPos)) < CLOSE_DISTANCE_THRESHOLD) {
            cacheResult(pos, false);
            // 记录方块剔除统计
            com.dongge0210.enclosedculling.debug.DebugManager.recordBlockCheck(true);
            return false;
        }
        
        // 6. 执行详细的遮挡检测
        boolean isOccluded = checkEnclosure(world, pos) && !hasLineOfSight(world, pos, playerPos);
        cacheResult(pos, isOccluded);
        
        // 记录方块剔除统计
        com.dongge0210.enclosedculling.debug.DebugManager.recordBlockCheck(!isOccluded);
        
        // 7. 添加到批渲染（如果未被遮挡）
        if (!isOccluded) {
            BlockState state = world.getBlockState(pos);
            BatchRenderer.addBlockToBatch(pos, state, playerPos);
        }
        
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
    
    /**
     * 判断实体是否应该被剔除（兼容EntityCulling）
     * @param entity 要检查的实体
     * @param playerPos 玩家位置
     * @return true 如果实体应该被剔除
     */
    public static boolean shouldCullEntity(Entity entity, Vec3 playerPos) {
        // EntityCulling兼容性检查：如果检测到EntityCulling MOD，跳过实体剔除
        if (EntityCullingCompatibility.shouldSkipEntityCulling()) {
            // 让EntityCulling MOD处理实体剔除
            return false;
        }
        
        // 我们的实体剔除逻辑
        return isEntityOccluded(entity, playerPos);
    }
    
    /**
     * 内部实体遮挡检查逻辑（增强版）
     */
    private static boolean isEntityOccluded(Entity entity, Vec3 playerPos) {
        BlockPos entityPos = entity.blockPosition();
        Level world = entity.level();
        
        // 1. 视锥剔除检查
        if (!FrustumCuller.isSphereInFrustum(entity.position(), entity.getBbWidth() * 0.5f)) {
            return true;
        }
        
        // 2. LOD检查
        LODManager.LODLevel lod = LODManager.calculateEntityLOD(entity, playerPos);
        if (lod == LODManager.LODLevel.CULLED) {
            return true;
        }
        
        // 3. 使用房间系统检查实体是否在可见房间内
        if (!RoomManager.isPositionVisible(world, entityPos, BlockPos.containing(playerPos))) {
            return true;
        }
        
        // 4. 距离检查
        double distance = playerPos.distanceToSqr(entity.position());
        if (distance > 1024.0) { // 从256.0(16格)增加到1024.0(32格)的实体进行更详细检查
            return isPositionOccluded(world, entityPos, playerPos);
        }
        
        return false;
    }
    
    /**
     * 获取兼容性状态信息（增强版）
     */
    public static String getCompatibilityInfo() {
        StringBuilder info = new StringBuilder();
        info.append("CullingRenderer Enhanced Status:\n");
        info.append("  EntityCulling Detected: ").append(EntityCullingCompatibility.isEntityCullingDetected()).append("\n");
        info.append("  Entity Culling: ").append(EntityCullingCompatibility.shouldSkipEntityCulling() ? "DELEGATED" : "ACTIVE").append("\n");
        info.append("  Block Culling: ACTIVE\n");
        info.append("  Cache Size: ").append(occlusionCache.size()).append("/").append(CACHE_SIZE).append("\n");
        info.append("  ").append(FrustumCuller.getFrustumInfo()).append("\n");
        info.append("  ").append(LODManager.getLODInfo()).append("\n");
        info.append("  ").append(BatchRenderer.getBatchInfo());
        return info.toString();
    }
    
    // === GPU优化管理方法 ===
    
    /**
     * 更新GPU优化系统
     */
    public static void updateGPUOptimizations(Vec3 viewerPos, float frameTime) {
        // 更新自适应LOD
        LODManager.AdaptiveLOD.updateAdaptiveLOD(frameTime);
        
        // 更新批渲染
        BatchRenderer.updateBatches(viewerPos);
        
        // 定期清理过期数据
        if (System.currentTimeMillis() % 10000 == 0) { // 每10秒清理一次
            BatchRenderer.cleanupBatches();
        }
    }
    
    /**
     * 重置GPU优化系统
     */
    public static void resetGPUOptimizations() {
        FrustumCuller.reset();
        LODManager.AdaptiveLOD.reset();
        BatchRenderer.clearBatches();
        cleanCache();
    }
    
    /**
     * 检查方块是否应该渲染（综合判断）
     */
    public static boolean shouldRenderBlock(Level world, BlockPos pos, Vec3 playerPos) {
        // 基础遮挡检查
        if (isPositionOccluded(world, pos, playerPos)) {
            return false;
        }
        
        // LOD检查
        LODManager.LODLevel lod = LODManager.calculateBlockLOD(pos, playerPos);
        return LODManager.shouldRender(lod);
    }
    
    /**
     * 获取方块的渲染质量
     */
    public static float getBlockRenderQuality(BlockPos pos, Vec3 playerPos) {
        LODManager.LODLevel lod = LODManager.calculateBlockLOD(pos, playerPos);
        return LODManager.getQualityMultiplier(lod);
    }
    
    /**
     * 检查是否应该使用批渲染
     */
    public static boolean shouldUseBatchRendering(BlockPos pos, Vec3 playerPos) {
        LODManager.LODLevel lod = LODManager.calculateBlockLOD(pos, playerPos);
        return LODManager.shouldUseBatchRendering(lod);
    }
}