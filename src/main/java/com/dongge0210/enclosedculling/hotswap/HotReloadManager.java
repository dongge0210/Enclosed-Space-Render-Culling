package com.dongge0210.enclosedculling.hotswap;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import com.dongge0210.enclosedculling.config.ModConfig;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 热更新管理器 - 支持配置文件和脚本的热重载
 * Hot Reload Manager - Supports hot reloading of configuration files and scripts
 */
@Mod.EventBusSubscriber(modid = EnclosedSpaceRenderCulling.MODID)
public class HotReloadManager {
    
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static final Map<Path, WatchService> watchServices = new ConcurrentHashMap<>();
    private static final Map<Path, Long> lastModified = new ConcurrentHashMap<>();
    private static final Map<String, HotReloadCallback> callbacks = new ConcurrentHashMap<>();
    
    private static boolean hotReloadEnabled = false;
    private static volatile boolean initialized = false;
    
    /**
     * 热重载回调接口
     */
    public interface HotReloadCallback {
        void onReload(Path file) throws Exception;
    }
    
    /**
     * 初始化热更新系统
     */
    public static void initialize() {
        if (initialized) return;
        
        try {
            hotReloadEnabled = ModConfig.COMMON.enableCulling.get(); // 基于配置启用
            
            if (hotReloadEnabled) {
                setupConfigWatcher();
                setupScriptWatcher();
                startFileMonitoring();
                
                EnclosedSpaceRenderCulling.LOGGER.info("Hot reload system initialized");
            }
            
            initialized = true;
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Failed to initialize hot reload system", e);
        }
    }
    
    /**
     * 关闭热更新系统
     */
    public static void shutdown() {
        if (!initialized) return;
        
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            for (WatchService watchService : watchServices.values()) {
                watchService.close();
            }
            watchServices.clear();
            
            EnclosedSpaceRenderCulling.LOGGER.info("Hot reload system shutdown");
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Error shutting down hot reload system", e);
        }
    }
    
    /**
     * 注册文件监听
     */
    public static void registerFileWatcher(Path file, HotReloadCallback callback) {
        if (!hotReloadEnabled) return;
        
        try {
            Path parent = file.getParent();
            if (parent != null && Files.exists(parent)) {
                watchServices.computeIfAbsent(parent, path -> {
                    try {
                        WatchService watchService = FileSystems.getDefault().newWatchService();
                        path.register(watchService, 
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_CREATE);
                        return watchService;
                    } catch (IOException e) {
                        EnclosedSpaceRenderCulling.LOGGER.error("Failed to create watch service for " + path, e);
                        return null;
                    }
                });
                
                callbacks.put(file.toString(), callback);
                lastModified.put(file, getLastModified(file));
                
                EnclosedSpaceRenderCulling.LOGGER.debug("Registered file watcher for: " + file);
            }
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Failed to register file watcher for " + file, e);
        }
    }
    
    /**
     * 设置配置文件监听
     */
    private static void setupConfigWatcher() {
        // 监听Forge配置文件
        Path configDir = Paths.get("config");
        if (Files.exists(configDir)) {
            try {
                Files.walk(configDir, 2)
                    .filter(path -> path.toString().contains("enclosed_culling"))
                    .filter(path -> path.toString().endsWith(".toml"))
                    .forEach(configFile -> {
                        registerFileWatcher(configFile, file -> {
                            EnclosedSpaceRenderCulling.LOGGER.info("Config file changed, reloading: " + file);
                            ModConfig.reload();
                        });
                    });
            } catch (IOException e) {
                EnclosedSpaceRenderCulling.LOGGER.error("Failed to setup config watcher", e);
            }
        }
    }
    
    /**
     * 设置脚本文件监听
     */
    private static void setupScriptWatcher() {
        // 创建脚本目录（如果不存在）
        Path scriptDir = Paths.get("scripts", "enclosed_culling");
        try {
            Files.createDirectories(scriptDir);
            
            // 监听脚本文件变化
            registerFileWatcher(scriptDir.resolve("culling_rules.js"), file -> {
                EnclosedSpaceRenderCulling.LOGGER.info("Culling rules script changed, reloading: " + file);
                ScriptManager.reloadCullingRules();
            });
            
            registerFileWatcher(scriptDir.resolve("debug_hooks.js"), file -> {
                EnclosedSpaceRenderCulling.LOGGER.info("Debug hooks script changed, reloading: " + file);
                ScriptManager.reloadDebugHooks();
            });
            
        } catch (IOException e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Failed to setup script watcher", e);
        }
    }
    
    /**
     * 开始文件监控
     */
    private static void startFileMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            for (Map.Entry<Path, WatchService> entry : watchServices.entrySet()) {
                Path directory = entry.getKey();
                WatchService watchService = entry.getValue();
                
                if (watchService == null) continue;
                
                try {
                    WatchKey key = watchService.poll();
                    if (key != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            
                            if (kind == StandardWatchEventKinds.OVERFLOW) {
                                continue;
                            }
                            
                            Path fileName = (Path) event.context();
                            Path fullPath = directory.resolve(fileName);
                            
                            if (kind == StandardWatchEventKinds.ENTRY_MODIFY || 
                                kind == StandardWatchEventKinds.ENTRY_CREATE) {
                                handleFileChange(fullPath);
                            }
                        }
                        
                        boolean valid = key.reset();
                        if (!valid) {
                            watchServices.remove(directory);
                            break;
                        }
                    }
                } catch (Exception e) {
                    EnclosedSpaceRenderCulling.LOGGER.error("Error monitoring directory: " + directory, e);
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    /**
     * 处理文件变化
     */
    private static void handleFileChange(Path file) {
        try {
            if (!Files.exists(file)) return;
            
            long currentModified = getLastModified(file);
            Long previousModified = lastModified.get(file);
            
            // 检查文件是否确实修改了
            if (previousModified == null || currentModified > previousModified) {
                lastModified.put(file, currentModified);
                
                // 查找并执行回调
                HotReloadCallback callback = callbacks.get(file.toString());
                if (callback != null) {
                    // 延迟一点执行，确保文件写入完成
                    scheduler.schedule(() -> {
                        try {
                            callback.onReload(file);
                        } catch (Exception e) {
                            EnclosedSpaceRenderCulling.LOGGER.error("Error reloading file: " + file, e);
                        }
                    }, 100, TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Error handling file change: " + file, e);
        }
    }
    
    /**
     * 获取文件最后修改时间
     */
    private static long getLastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
    
    /**
     * Forge配置重载事件
     */
    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(EnclosedSpaceRenderCulling.MODID)) {
            EnclosedSpaceRenderCulling.LOGGER.info("Forge config reloaded");
            
            // 重新初始化相关系统
            scheduler.execute(() -> {
                try {
                    // 这里可以添加配置重载后需要执行的逻辑
                    EnclosedSpaceRenderCulling.LOGGER.debug("Post-config reload tasks completed");
                } catch (Exception e) {
                    EnclosedSpaceRenderCulling.LOGGER.error("Error in post-config reload tasks", e);
                }
            });
        }
    }
    
    /**
     * 创建示例脚本文件
     */
    public static void createExampleScripts() {
        Path scriptDir = Paths.get("scripts", "enclosed_culling");
        
        try {
            Files.createDirectories(scriptDir);
            
            // 创建剔除规则示例脚本
            Path cullingRulesFile = scriptDir.resolve("culling_rules.js");
            if (!Files.exists(cullingRulesFile)) {
                String cullingRulesScript = """
                    // 自定义剔除规则脚本
                    // Custom Culling Rules Script
                    
                    // 示例：根据方块类型定制剔除逻辑
                    function shouldCullBlock(blockPos, blockState, playerPos) {
                        // 获取方块类型
                        var blockType = blockState.getBlock().getDescriptionId();
                        
                        // 示例：永远不剔除重要方块
                        if (blockType.includes("chest") || blockType.includes("furnace")) {
                            return false;
                        }
                        
                        // 示例：距离玩家很远的装饰性方块可以被剔除
                        var distance = blockPos.distSqr(playerPos);
                        if (distance > 1024 && blockType.includes("flower")) {
                            return true;
                        }
                        
                        // 使用默认逻辑
                        return null;
                    }
                    
                    // 示例：自定义房间连通性判断
                    function isConnectedSpace(pos1, pos2, level) {
                        // 自定义连通性逻辑
                        return null; // 使用默认逻辑
                    }
                    """;
                Files.write(cullingRulesFile, cullingRulesScript.getBytes());
                EnclosedSpaceRenderCulling.LOGGER.info("Created example culling rules script");
            }
            
            // 创建调试钩子示例脚本
            Path debugHooksFile = scriptDir.resolve("debug_hooks.js");
            if (!Files.exists(debugHooksFile)) {
                String debugHooksScript = """
                    // 调试钩子脚本
                    // Debug Hooks Script
                    
                    // 在剔除检查前调用
                    function onBeforeCullingCheck(blockPos, playerPos) {
                        // 记录调试信息
                        console.log("Checking culling for block at: " + blockPos);
                    }
                    
                    // 在剔除检查后调用
                    function onAfterCullingCheck(blockPos, result, reason) {
                        // 记录结果
                        if (result) {
                            console.log("Block culled: " + blockPos + " (" + reason + ")");
                        }
                    }
                    
                    // 自定义性能监控
                    function onPerformanceMetric(metricName, value) {
                        // 处理性能指标
                        if (metricName === "culling_check_time" && value > 5.0) {
                            console.warn("Slow culling check: " + value + "ms");
                        }
                    }
                    """;
                Files.write(debugHooksFile, debugHooksScript.getBytes());
                EnclosedSpaceRenderCulling.LOGGER.info("Created example debug hooks script");
            }
            
        } catch (IOException e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Failed to create example scripts", e);
        }
    }
    
    /**
     * 检查热重载是否启用
     */
    public static boolean isHotReloadEnabled() {
        return hotReloadEnabled;
    }
    
    /**
     * 强制重载所有监听的文件
     */
    public static void forceReloadAll() {
        if (!hotReloadEnabled) return;
        
        for (Map.Entry<String, HotReloadCallback> entry : callbacks.entrySet()) {
            Path file = Paths.get(entry.getKey());
            if (Files.exists(file)) {
                try {
                    entry.getValue().onReload(file);
                    EnclosedSpaceRenderCulling.LOGGER.info("Force reloaded: " + file);
                } catch (Exception e) {
                    EnclosedSpaceRenderCulling.LOGGER.error("Error force reloading: " + file, e);
                }
            }
        }
    }
}
