package com.dongge0210.enclosedculling.debug;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import com.dongge0210.enclosedculling.config.ModConfig;
import com.dongge0210.enclosedculling.hotswap.HotReloadManager;
import com.dongge0210.enclosedculling.hotswap.ScriptManager;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

/**
 * 开发功能初始化器 - 确保所有开发和调试功能正确初始化
 * Development Features Initializer - Ensures all development and debugging features are properly initialized
 */
@Mod.EventBusSubscriber(modid = EnclosedSpaceRenderCulling.MODID)
public class DevelopmentInitializer {
    
    private static boolean initialized = false;
    
    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        if (initialized) return;
        
        try {
            // 确保所有系统按正确顺序初始化
            initializeAllSystems();
            initialized = true;
            
            EnclosedSpaceRenderCulling.LOGGER.info("All development and debugging systems initialized successfully");
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Failed to initialize development systems", e);
        }
    }
    
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        if (!initialized) {
            initializeAllSystems();
            initialized = true;
        }
        
        // 服务器启动时的特定初始化
        EnclosedSpaceRenderCulling.LOGGER.info("Development systems ready for server");
    }
    
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        try {
            // 清理资源
            cleanupAllSystems();
            EnclosedSpaceRenderCulling.LOGGER.info("Development systems cleaned up");
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Error cleaning up development systems", e);
        }
    }
    
    /**
     * 初始化所有系统
     */
    private static void initializeAllSystems() {
        try {
            // 1. 首先初始化脚本管理器
            if (ModConfig.COMMON.enableScriptSupport.get()) {
                ScriptManager.initialize();
                EnclosedSpaceRenderCulling.LOGGER.info("Script manager initialized");
            }
            
            // 2. 初始化热重载管理器
            if (ModConfig.COMMON.enableHotReload.get()) {
                HotReloadManager.initialize();
                EnclosedSpaceRenderCulling.LOGGER.info("Hot reload manager initialized");
            }
            
            // 3. 初始化调试管理器
            if (ModConfig.COMMON.enableDebugMode.get()) {
                DebugManager.resetStats();
                EnclosedSpaceRenderCulling.LOGGER.info("Debug manager initialized");
            }
            
            // 4. 创建示例文件
            HotReloadManager.createExampleScripts();
            
            // 5. 设置性能监控
            setupPerformanceMonitoring();
            
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Error during system initialization", e);
        }
    }
    
    /**
     * 清理所有系统
     */
    private static void cleanupAllSystems() {
        try {
            // 关闭热重载系统
            HotReloadManager.shutdown();
            
            // 清理脚本管理器
            ScriptManager.cleanup();
            
            // 重置调试管理器
            DebugManager.resetStats();
            
            initialized = false;
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Error during cleanup", e);
        }
    }
    
    /**
     * 设置性能监控
     */
    private static void setupPerformanceMonitoring() {
        if (!ModConfig.COMMON.enablePerformanceLogging.get()) return;
        
        // 注册性能监控任务
        Thread performanceMonitor = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(30000); // 每30秒检查一次
                    
                    String report = DebugManager.getPerformanceReport();
                    if (ModConfig.COMMON.enableDebugMode.get()) {
                        EnclosedSpaceRenderCulling.LOGGER.info("Performance Report:\n" + report);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    EnclosedSpaceRenderCulling.LOGGER.error("Error in performance monitoring", e);
                }
            }
        });
        
        performanceMonitor.setDaemon(true);
        performanceMonitor.setName("EnclosedCulling-PerformanceMonitor");
        performanceMonitor.start();
        
        EnclosedSpaceRenderCulling.LOGGER.info("Performance monitoring started");
    }
    
    /**
     * 检查所有系统是否正常运行
     */
    public static String getSystemStatus() {
        StringBuilder status = new StringBuilder();
        status.append("=== 封闭空间剔除系统状态 ===\n");
        
        status.append("初始化状态: ").append(initialized ? "✓ 已完成" : "✗ 未完成").append("\n");
        
        status.append("脚本管理器: ");
        if (ScriptManager.isInitialized()) {
            status.append("✓ 运行中\n");
            status.append("  ").append(ScriptManager.getEngineInfo().replace("\n", "\n  "));
        } else {
            status.append("✗ 未初始化\n");
        }
        
        status.append("热重载: ");
        if (HotReloadManager.isHotReloadEnabled()) {
            status.append("✓ 已启用\n");
        } else {
            status.append("✗ 已禁用\n");
        }
        
        status.append("调试模式: ");
        if (DebugManager.isDebugMode()) {
            status.append("✓ 已激活\n");
        } else {
            status.append("✗ 未激活\n");
        }
        
        status.append("\n配置信息:\n");
        status.append("  剔除功能: ").append(ModConfig.COMMON.enableCulling.get() ? "已启用" : "已禁用").append("\n");
        status.append("  调试模式: ").append(ModConfig.COMMON.enableDebugMode.get() ? "已启用" : "已禁用").append("\n");
        status.append("  热重载: ").append(ModConfig.COMMON.enableHotReload.get() ? "已启用" : "已禁用").append("\n");
        status.append("  脚本支持: ").append(ModConfig.COMMON.enableScriptSupport.get() ? "已启用" : "已禁用").append("\n");
        status.append("  性能日志: ").append(ModConfig.COMMON.enablePerformanceLogging.get() ? "已启用" : "已禁用").append("\n");
        
        return status.toString();
    }
    
    /**
     * 强制重新初始化所有系统
     */
    public static void forceReinitialize() {
        EnclosedSpaceRenderCulling.LOGGER.info("Force reinitializing all development systems...");
        
        cleanupAllSystems();
        initializeAllSystems();
        initialized = true;
        
        EnclosedSpaceRenderCulling.LOGGER.info("Force reinitialization completed");
    }
    
    /**
     * 验证系统健康状态
     */
    public static boolean validateSystemHealth() {
        try {
            boolean healthy = true;
            
            // 检查脚本管理器
            if (ModConfig.COMMON.enableScriptSupport.get() && !ScriptManager.isInitialized()) {
                EnclosedSpaceRenderCulling.LOGGER.warn("Script manager should be initialized but isn't");
                healthy = false;
            }
            
            // 检查热重载
            if (ModConfig.COMMON.enableHotReload.get() && !HotReloadManager.isHotReloadEnabled()) {
                EnclosedSpaceRenderCulling.LOGGER.warn("Hot reload should be enabled but isn't");
                healthy = false;
            }
            
            return healthy;
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Error validating system health", e);
            return false;
        }
    }
}
