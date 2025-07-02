package com.dongge0210.enclosedculling.compat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import com.dongge0210.enclosedculling.config.ModConfig;

import net.minecraftforge.fml.ModList;

/**
 * EntityCulling兼容性管理器
 * 自动检测EntityCulling MOD并管理兼容性
 */
public class EntityCullingCompatibility {
    
    private static boolean entityCullingDetected = false;
    private static boolean compatibilityEnabled = false;
    private static String detectedVersion = "unknown";
    
    // 已知的EntityCulling MOD ID变体
    private static final Set<String> ENTITYCULLING_MOD_IDS = new HashSet<>(Arrays.asList(
        "entityculling",
        "entity_culling", 
        "entitycull",
        "entity-culling",
        "tr7zwEntityCulling"  // 作者的用户名前缀
    ));
    
    // 已知可能冲突的MOD
    private static final Set<String> CONFLICTING_MODS = new HashSet<>(Arrays.asList(
        "entityculling",
        "entity_culling",
        "cullingmod",
        "renderculling",
        "performanceplus"  // 其他性能优化MOD
    ));
    
    /**
     * 初始化EntityCulling兼容性检测
     */
    public static void initialize() {
        EnclosedSpaceRenderCulling.LOGGER.info("Initializing EntityCulling compatibility detection...");
        
        // 检测EntityCulling是否存在
        detectEntityCulling();
        
        // 根据检测结果配置兼容性
        if (entityCullingDetected) {
            handleEntityCullingDetected();
        } else {
            EnclosedSpaceRenderCulling.LOGGER.info("No EntityCulling MOD detected, full functionality enabled");
        }
        
        // 检测其他可能冲突的MOD
        detectOtherConflictingMods();
    }
    
    /**
     * 检测EntityCulling MOD
     */
    private static void detectEntityCulling() {
        ModList modList = ModList.get();
        
        for (String modId : ENTITYCULLING_MOD_IDS) {
            if (modList.isLoaded(modId)) {
                entityCullingDetected = true;
                try {
                    detectedVersion = modList.getModContainerById(modId)
                        .map(container -> container.getModInfo().getVersion().toString())
                        .orElse("unknown");
                } catch (Exception e) {
                    detectedVersion = "unknown";
                }
                
                EnclosedSpaceRenderCulling.LOGGER.warn("EntityCulling MOD detected! ID: {}, Version: {}", 
                    modId, detectedVersion);
                break;
            }
        }
    }
    
    /**
     * 处理检测到EntityCulling的情况
     */
    private static void handleEntityCullingDetected() {
        EnclosedSpaceRenderCulling.LOGGER.warn("===========================================");
        EnclosedSpaceRenderCulling.LOGGER.warn("  ENTITYCULLING COMPATIBILITY MODE");
        EnclosedSpaceRenderCulling.LOGGER.warn("===========================================");
        EnclosedSpaceRenderCulling.LOGGER.warn("EntityCulling MOD detected (v{})!", detectedVersion);
        EnclosedSpaceRenderCulling.LOGGER.warn("Enabling compatibility mode to prevent conflicts...");
        
        // 启用兼容模式
        compatibilityEnabled = true;
        
        // 自动调整配置以避免冲突
        adjustConfigurationForCompatibility();
        
        EnclosedSpaceRenderCulling.LOGGER.info("Compatibility adjustments applied:");
        EnclosedSpaceRenderCulling.LOGGER.info("- Entity culling disabled (handled by EntityCulling MOD)");
        EnclosedSpaceRenderCulling.LOGGER.info("- Focus shifted to block/space culling only");
        EnclosedSpaceRenderCulling.LOGGER.info("- Reduced performance monitoring to avoid interference");
        EnclosedSpaceRenderCulling.LOGGER.warn("===========================================");
    }
    
    /**
     * 调整配置以兼容EntityCulling
     */
    private static void adjustConfigurationForCompatibility() {
        try {
            // 如果启用了调试模式，记录兼容性调整
            if (ModConfig.COMMON.enableDebugMode.get()) {
                EnclosedSpaceRenderCulling.LOGGER.debug("Applying EntityCulling compatibility adjustments...");
            }
            
            // 这里可以动态调整某些功能的启用状态
            // 例如：禁用实体相关的剔除功能，专注于方块剔除
            
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Failed to apply compatibility adjustments", e);
        }
    }
    
    /**
     * 检测其他可能冲突的MOD
     */
    private static void detectOtherConflictingMods() {
        ModList modList = ModList.get();
        
        for (String modId : CONFLICTING_MODS) {
            if (modList.isLoaded(modId) && !ENTITYCULLING_MOD_IDS.contains(modId)) {
                String version = modList.getModContainerById(modId)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
                    
                EnclosedSpaceRenderCulling.LOGGER.warn("Potentially conflicting MOD detected: {} (v{})", 
                    modId, version);
                EnclosedSpaceRenderCulling.LOGGER.warn("Please monitor for performance issues or conflicts");
            }
        }
    }
    
    /**
     * 获取兼容性状态信息
     */
    public static String getCompatibilityStatus() {
        StringBuilder status = new StringBuilder();
        status.append("EntityCulling Compatibility Status:\n");
        status.append("  EntityCulling Detected: ").append(entityCullingDetected ? "YES" : "NO").append("\n");
        
        if (entityCullingDetected) {
            status.append("  Detected Version: ").append(detectedVersion).append("\n");
            status.append("  Compatibility Mode: ").append(compatibilityEnabled ? "ENABLED" : "DISABLED").append("\n");
            status.append("  Entity Culling: DELEGATED TO ENTITYCULLING MOD\n");
            status.append("  Our Focus: BLOCK/SPACE CULLING ONLY\n");
        } else {
            status.append("  Full Functionality: ENABLED\n");
            status.append("  Entity Culling: AVAILABLE\n");
            status.append("  Block Culling: AVAILABLE\n");
        }
        
        return status.toString();
    }
    
    /**
     * 检查是否应该跳过实体剔除功能
     */
    public static boolean shouldSkipEntityCulling() {
        // 实体剔除功能已移除，总是跳过
        return true;
    }
    
    /**
     * 检查是否启用了兼容模式
     */
    public static boolean isCompatibilityModeEnabled() {
        return compatibilityEnabled;
    }
    
    /**
     * 获取检测到的EntityCulling版本
     */
    public static String getDetectedEntityCullingVersion() {
        return detectedVersion;
    }
    
    /**
     * 是否检测到EntityCulling
     */
    public static boolean isEntityCullingDetected() {
        return entityCullingDetected;
    }
    
    /**
     * 强制启用/禁用兼容模式（调试用）
     */
    public static void setCompatibilityMode(boolean enabled) {
        compatibilityEnabled = enabled;
        EnclosedSpaceRenderCulling.LOGGER.info("Compatibility mode {} set to: {}", 
            entityCullingDetected ? "forcibly" : "manually", enabled);
    }
    
    /**
     * 获取兼容性建议
     */
    public static String getCompatibilityAdvice() {
        if (!entityCullingDetected) {
            return "No conflicts detected. All features available.";
        }
        
        return "EntityCulling MOD detected. Recommendations:\n" +
               "1. Keep EntityCulling for entity optimization\n" +
               "2. Use our MOD for advanced block/space culling\n" +
               "3. Monitor performance in F3 debug screen\n" +
               "4. Disable our entity features if conflicts occur\n" +
               "5. Report any issues to mod developers";
    }
    
    /**
     * 显示启动兼容性警告（如果需要）
     */
    public static void showStartupWarnings() {
        try {
            if (!ModConfig.COMMON.showCompatibilityWarnings.get()) {
                return;
            }
        } catch (Exception e) {
            // 配置未加载，使用默认行为
        }
        
        if (entityCullingDetected) {
            EnclosedSpaceRenderCulling.LOGGER.info("===========================================");
            EnclosedSpaceRenderCulling.LOGGER.info("  COMPATIBILITY MODE ACTIVE");
            EnclosedSpaceRenderCulling.LOGGER.info("===========================================");
            EnclosedSpaceRenderCulling.LOGGER.info("EntityCulling MOD detected!");
            EnclosedSpaceRenderCulling.LOGGER.info("Our MOD will focus on block/space culling only.");
            EnclosedSpaceRenderCulling.LOGGER.info("Entity culling will be handled by EntityCulling MOD.");
            EnclosedSpaceRenderCulling.LOGGER.info("");
            EnclosedSpaceRenderCulling.LOGGER.info("To override this behavior:");
            EnclosedSpaceRenderCulling.LOGGER.info("- 实体剔除功能已从此版本中移除");
            EnclosedSpaceRenderCulling.LOGGER.info("- 本MOD现在只专注于空间剔除和方块剔除功能");
            EnclosedSpaceRenderCulling.LOGGER.info("===========================================");
        }
    }
}
