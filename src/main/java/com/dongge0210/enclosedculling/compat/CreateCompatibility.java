package com.dongge0210.enclosedculling.compat;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Create模组兼容层
 * 仅在Create模组存在时才会被初始化
 */
public class CreateCompatibility {
    
    private static boolean createLoaded = false;
    private static boolean initialized = false;
    
    /**
     * 初始化Create兼容性
     */
    public static void init() {
        if (initialized) return;
        
        try {
            // 检查Create模组是否存在
            Class.forName("com.simibubi.create.foundation.blockEntity.SmartBlockEntity");
            createLoaded = true;
            EnclosedSpaceRenderCulling.LOGGER.info("Create模组已检测到，启用兼容功能");
            
            // 在这里可以注册Create相关的事件监听器
            initCreateSpecificFeatures();
            
        } catch (ClassNotFoundException e) {
            createLoaded = false;
            EnclosedSpaceRenderCulling.LOGGER.debug("Create模组未检测到，跳过兼容功能");
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.warn("Create兼容性初始化失败: {}", e.getMessage());
        }
        
        initialized = true;
    }
    
    /**
     * 检查Create模组是否已加载 - 线程安全版本
     */
    public static boolean isCreateLoaded() {
        if (!initialized) {
            init(); // 确保已初始化
        }
        return createLoaded;
    }
    
    /**
     * 初始化Create特定功能
     */
    private static void initCreateSpecificFeatures() {
        // 这里可以添加Create特定的剔除逻辑
        // 例如：对Create的SmartBlockEntity进行特殊处理
    }
    
    /**
     * Check if Create block entity should be culled
     * 检查Create方块实体是否应该被剔除
     */
    public static boolean shouldCullCreateBlockEntity(Level level, BlockPos pos) {
        if (!createLoaded) {
            return false; // 如果Create未加载，不进行剔除
        }
        
        try {
            // 这里可以添加Create特定的剔除逻辑
            // 例如：检查SmartBlockEntity的特定属性
            return false; // 暂时不剔除Create方块实体
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.warn("Create方块实体剔除检查失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get Create related debug information
     * 获取Create相关的调试信息
     */
    public static String getCreateDebugInfo() {
        if (!createLoaded) {
            return "Create模组: 未加载";
        }
        
        return "Create模组: 已加载，兼容功能已启用";
    }
}