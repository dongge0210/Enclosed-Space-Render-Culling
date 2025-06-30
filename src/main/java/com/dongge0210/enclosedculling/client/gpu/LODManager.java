package com.dongge0210.enclosedculling.client.gpu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import com.dongge0210.enclosedculling.config.ModConfig;

/**
 * LOD (细节层次) 系统 - 根据距离动态调整渲染质量
 * GPU优化: 减少远距离对象的渲染复杂度
 */
public class LODManager {
    
    // LOD距离阈值
    private static final float LOD_DISTANCE_1 = 32.0f;   // 高质量
    private static final float LOD_DISTANCE_2 = 64.0f;   // 中等质量
    private static final float LOD_DISTANCE_3 = 128.0f;  // 低质量
    
    // LOD级别枚举
    public enum LODLevel {
        HIGH(0, "High Detail"),      // 完整渲染
        MEDIUM(1, "Medium Detail"),  // 简化渲染
        LOW(2, "Low Detail"),        // 最简渲染
        CULLED(3, "Culled");         // 完全剔除
        
        private final int level;
        private final String description;
        
        LODLevel(int level, String description) {
            this.level = level;
            this.description = description;
        }
        
        public int getLevel() { return level; }
        public String getDescription() { return description; }
    }
    
    /**
     * 根据距离计算LOD级别
     */
    public static LODLevel calculateLOD(Vec3 objectPos, Vec3 viewerPos) {
        double distanceSq = objectPos.distanceToSqr(viewerPos);
        
        try {
            // 使用配置的剔除距离作为最大距离
            int cullDistance = ModConfig.COMMON.cullDistance.get();
            float maxDistanceSq = cullDistance * cullDistance;
            
            if (distanceSq > maxDistanceSq) {
                return LODLevel.CULLED;
            } else if (distanceSq > LOD_DISTANCE_3 * LOD_DISTANCE_3) {
                return LODLevel.LOW;
            } else if (distanceSq > LOD_DISTANCE_2 * LOD_DISTANCE_2) {
                return LODLevel.MEDIUM;
            } else {
                return LODLevel.HIGH;
            }
        } catch (Exception e) {
            // 配置未加载时使用默认值
            if (distanceSq > 128 * 128) {
                return LODLevel.CULLED;
            } else if (distanceSq > LOD_DISTANCE_3 * LOD_DISTANCE_3) {
                return LODLevel.LOW;
            } else if (distanceSq > LOD_DISTANCE_2 * LOD_DISTANCE_2) {
                return LODLevel.MEDIUM;
            } else {
                return LODLevel.HIGH;
            }
        }
    }
    
    /**
     * 计算方块的LOD级别
     */
    public static LODLevel calculateBlockLOD(BlockPos blockPos, Vec3 viewerPos) {
        Vec3 blockCenter = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
        return calculateLOD(blockCenter, viewerPos);
    }
    
    /**
     * 计算实体的LOD级别
     */
    public static LODLevel calculateEntityLOD(Entity entity, Vec3 viewerPos) {
        return calculateLOD(entity.position(), viewerPos);
    }
    
    /**
     * 检查是否应该渲染
     */
    public static boolean shouldRender(LODLevel lod) {
        return lod != LODLevel.CULLED;
    }
    
    /**
     * 检查是否应该使用简化渲染
     */
    public static boolean shouldUseSimplifiedRendering(LODLevel lod) {
        return lod == LODLevel.LOW || lod == LODLevel.MEDIUM;
    }
    
    /**
     * 检查是否应该跳过细节渲染（如阴影、光照等）
     */
    public static boolean shouldSkipDetails(LODLevel lod) {
        return lod == LODLevel.LOW;
    }
    
    /**
     * 获取渲染质量倍数（用于纹理LOD等）
     */
    public static float getQualityMultiplier(LODLevel lod) {
        switch (lod) {
            case HIGH: return 1.0f;
            case MEDIUM: return 0.75f;
            case LOW: return 0.5f;
            case CULLED: return 0.0f;
            default: return 1.0f;
        }
    }
    
    /**
     * 获取建议的渲染距离倍数
     */
    public static float getRenderDistanceMultiplier(LODLevel lod) {
        switch (lod) {
            case HIGH: return 1.0f;
            case MEDIUM: return 0.8f;
            case LOW: return 0.6f;
            case CULLED: return 0.0f;
            default: return 1.0f;
        }
    }
    
    /**
     * 检查是否应该使用批渲染
     */
    public static boolean shouldUseBatchRendering(LODLevel lod) {
        return lod == LODLevel.LOW || lod == LODLevel.MEDIUM;
    }
    
    /**
     * 自适应LOD：根据性能动态调整
     */
    public static class AdaptiveLOD {
        private static float lastFrameTime = 16.67f; // 目标60FPS
        private static float lodBias = 0.0f;
        private static long lastUpdateTime = 0;
        
        /**
         * 更新自适应LOD参数
         */
        public static void updateAdaptiveLOD(float currentFrameTime) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateTime < 1000) return; // 每秒更新一次
            
            lastUpdateTime = currentTime;
            lastFrameTime = currentFrameTime;
            
            // 目标帧时间 (60FPS = 16.67ms)
            float targetFrameTime = 16.67f;
            
            if (currentFrameTime > targetFrameTime * 1.2f) {
                // 性能不足，增加LOD偏差（降低质量）
                lodBias = Math.min(lodBias + 0.1f, 2.0f);
            } else if (currentFrameTime < targetFrameTime * 0.8f) {
                // 性能富余，减少LOD偏差（提高质量）
                lodBias = Math.max(lodBias - 0.05f, 0.0f);
            }
        }
        
        /**
         * 获取自适应LOD级别
         */
        public static LODLevel getAdaptiveLOD(Vec3 objectPos, Vec3 viewerPos) {
            LODLevel baseLOD = calculateLOD(objectPos, viewerPos);
            
            // 应用自适应偏差
            int adjustedLevel = Math.min(baseLOD.getLevel() + (int) lodBias, LODLevel.CULLED.getLevel());
            
            switch (adjustedLevel) {
                case 0: return LODLevel.HIGH;
                case 1: return LODLevel.MEDIUM;
                case 2: return LODLevel.LOW;
                case 3: return LODLevel.CULLED;
                default: return LODLevel.HIGH;
            }
        }
        
        /**
         * 获取当前LOD偏差
         */
        public static float getCurrentLODBias() {
            return lodBias;
        }
        
        /**
         * 重置自适应LOD
         */
        public static void reset() {
            lodBias = 0.0f;
            lastFrameTime = 16.67f;
        }
    }
    
    /**
     * 获取LOD系统状态信息
     */
    public static String getLODInfo() {
        return String.format("LOD System: ACTIVE | Bias: %.2f | Frame: %.2fms", 
            AdaptiveLOD.getCurrentLODBias(), AdaptiveLOD.lastFrameTime);
    }
    
    /**
     * 获取距离阈值配置信息
     */
    public static String getThresholdInfo() {
        return String.format("LOD Thresholds: High<%.0f | Medium<%.0f | Low<%.0f", 
            LOD_DISTANCE_1, LOD_DISTANCE_2, LOD_DISTANCE_3);
    }
}
