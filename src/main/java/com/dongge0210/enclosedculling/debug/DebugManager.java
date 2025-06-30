package com.dongge0210.enclosedculling.debug;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import com.dongge0210.enclosedculling.config.ModConfig;
import com.dongge0210.enclosedculling.room.RoomManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 调试管理器 - 管理调试模式和信息显示
 * Debug Manager - Manages debug mode and information display
 */
@Mod.EventBusSubscriber(modid = EnclosedSpaceRenderCulling.MODID, value = Dist.CLIENT)
public class DebugManager {
    
    private static final Map<String, Object> debugInfo = new ConcurrentHashMap<>();
    private static final Map<String, Long> performanceTimers = new ConcurrentHashMap<>();
    
    // 性能统计
    private static long totalCullingChecks = 0;
    private static long successfulCulls = 0;
    private static double averageCheckTime = 0.0;
    private static final int SAMPLE_SIZE = 1000;
    private static final List<Double> recentCheckTimes = new ArrayList<>();
    
    // 实体剔除跟踪
    private static final Map<String, String> entityCullingReasons = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastEntityCheck = new ConcurrentHashMap<>();
    private static long totalEntityChecks = 0;
    private static long culledEntities = 0;
    
    // 房间连通性跟踪
    private static final Map<String, String> roomConnectivity = new ConcurrentHashMap<>();
    private static String lastPlayerRoom = "未知";
    private static String lastTargetRoom = "未知";
    
    // 距离和环境因素跟踪
    private static final Map<String, String> distanceCullingReasons = new ConcurrentHashMap<>();
    private static float playerRenderDistance = 0.0f;
    private static int currentLightLevel = 0;
    private static String environmentType = "未知";
    private static boolean isInCave = false;
    
    // 距离剔除配置参数
    private static final float FORCE_RENDER_DISTANCE = 4.0f; // 强制渲染距离（近到这个距离必须渲染）
    private static final float EXTREME_RENDER_DISTANCE = 64.0f; // 极限显示距离
    private static final float MAX_CULL_DISTANCE = 96.0f; // 绝对最大剔除距离
    private static final int DARK_ENVIRONMENT_THRESHOLD = 7; // 环境过暗阈值（光照等级）
    private static final int MIN_LIGHT_LEVEL = 5; // 最低光照等级
    private static final float DARK_DISTANCE_PENALTY = 0.6f; // 暗环境下的距离惩罚系数
    
    // Mod兼容性检测
    private static boolean hasMinimalHealthMod = false;
    private static boolean hasExtremeRenderMod = false;
    private static long lastModCheck = 0;
    
    // 极简血量显示mod相关
    private static boolean neatModDetected = false;
    private static float neatMaxDistance = 24.0f; // 极简血量显示的默认最大距离
    
    // RoomID稳定性监控
    private static String lastRoomId = null;
    private static int roomIdChangeCount = 0;
    private static long lastRoomIdChangeTime = 0;
    private static final List<String> roomIdHistory = new ArrayList<>();
    private static final int MAX_ROOM_HISTORY = 10;
    
    // 降低更新频率，从100ms改为500ms，减少性能影响和刷屏
    private static final int DEBUG_UPDATE_INTERVAL = 500; // 500ms更新一次
    private static long lastDebugUpdate = 0;
    
    // 添加日志输出控制
    private static final int LOG_THROTTLE_INTERVAL = 1000; // 1秒内最多输出一次相同类型的日志
    private static final Map<String, Long> lastLogTime = new ConcurrentHashMap<>();
    
    // 界面尺寸适配相关
    private static float guiScale = 1.0f;
    private static int screenWidth = 1920;
    private static int screenHeight = 1080;
    private static final float MIN_GUI_SCALE = 0.5f;
    private static final float MAX_GUI_SCALE = 4.0f;

    // 缓存调试信息以减少闪烁
    private static final Map<String, String> cachedDebugStrings = new ConcurrentHashMap<>();
    
    /**
     * 切换调试模式
     */
    public static boolean toggleDebugMode() {
        // 切换配置中的调试开关
        boolean newValue = !ModConfig.COMMON.enableDebug.get();
        ModConfig.COMMON.enableDebug.set(newValue);
        
        if (newValue) {
            // 启用调试模式时，触发房间检测
            triggerRoomDetection();
        }
        EnclosedSpaceRenderCulling.LOGGER.info("Debug mode {}", newValue ? "enabled" : "disabled");
        return newValue;
    }
    
    /**
     * 获取调试模式状态
     */
    public static boolean isDebugMode() {
        return ModConfig.COMMON.enableDebug.get();
    }
    
    /**
     * 设置调试信息
     */
    public static void setDebugInfo(String key, Object value) {
        if (ModConfig.COMMON.enableDebug.get()) {
            if (value == null) {
                debugInfo.remove(key);
            } else {
                debugInfo.put(key, value);
            }
        }
    }
    
    /**
     * 开始性能计时
     */
    public static void startTimer(String timerName) {
        if (ModConfig.COMMON.enableDebug.get()) {
            performanceTimers.put(timerName, System.nanoTime());
        }
    }
    
    /**
     * 结束性能计时并记录
     */
    public static void endTimer(String timerName) {
        if (ModConfig.COMMON.enableDebug.get() && performanceTimers.containsKey(timerName)) {
            long startTime = performanceTimers.remove(timerName);
            double duration = (System.nanoTime() - startTime) / 1_000_000.0; // 转换为毫秒
            
            // 记录到调试信息
            debugInfo.put(timerName + "_time", String.format("%.3fms", duration));
            
            // 如果是剔除检查，更新统计信息
            if (timerName.equals("culling_check")) {
                updateCullingStats(duration);
            }
        }
    }
    
    /**
     * 记录剔除结果
     */
    public static void recordCullingResult(boolean culled) {
        if (ModConfig.COMMON.enableDebug.get()) {
            totalCullingChecks++;
            if (culled) {
                successfulCulls++;
            }
            
            debugInfo.put("total_checks", totalCullingChecks);
            debugInfo.put("successful_culls", successfulCulls);
            debugInfo.put("cull_rate", String.format("%.1f%%", 
                (double) successfulCulls / totalCullingChecks * 100));
        }
    }
    
    /**
     * 更新剔除统计信息
     */
    private static void updateCullingStats(double checkTime) {
        synchronized (recentCheckTimes) {
            recentCheckTimes.add(checkTime);
            
            // 保持样本大小
            if (recentCheckTimes.size() > SAMPLE_SIZE) {
                recentCheckTimes.remove(0);
            }
            
            // 计算平均值
            averageCheckTime = recentCheckTimes.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            
            debugInfo.put("avg_check_time", String.format("%.3fms", averageCheckTime));
        }
    }
    
    /**
     * 重置性能统计
     */
    public static void resetStats() {
        totalCullingChecks = 0;
        successfulCulls = 0;
        averageCheckTime = 0.0;
        recentCheckTimes.clear();
        debugInfo.clear();
        performanceTimers.clear();
        
        EnclosedSpaceRenderCulling.LOGGER.info("Debug statistics reset");
    }
    
    /**
     * 跟踪RoomID变化，监控稳定性
     */
    public static void trackRoomIdChange(String currentRoomId) {
        if (currentRoomId == null) return;
        
        synchronized (roomIdHistory) {
            if (!currentRoomId.equals(lastRoomId)) {
                roomIdChangeCount++;
                lastRoomIdChangeTime = System.currentTimeMillis();
                
                // 记录到历史中
                roomIdHistory.add(currentRoomId);
                
                // 保持历史记录大小
                while (roomIdHistory.size() > MAX_ROOM_HISTORY) {
                    roomIdHistory.remove(0);
                }
                
                lastRoomId = currentRoomId;
                
                // 更新调试信息
                setDebugInfo("room_id_changes", roomIdChangeCount);
                setDebugInfo("last_room_change", lastRoomIdChangeTime);
            }
        }
    }
    
    /**
     * 获取房间ID稳定性报告
     */
    public static String getRoomIdStabilityReport() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastChange = currentTime - lastRoomIdChangeTime;
        
        return String.format("房间ID变化次数: %d\n" +
                           "上次变化: %d毫秒前\n" +
                           "当前房间: %s\n" +
                           "历史记录: %s",
                roomIdChangeCount, timeSinceLastChange, 
                lastRoomId != null ? lastRoomId : "未知",
                String.join(" -> ", roomIdHistory));
    }
    
    /**
     * 清理过期的调试数据
     */
    public static void cleanupExpiredData() {
        long currentTime = System.currentTimeMillis();
        
        // 清理过期的实体检查记录
        lastEntityCheck.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > 60000); // 1分钟过期
        
        // 清理对应的实体剔除原因
        entityCullingReasons.entrySet().removeIf(entry -> 
            !lastEntityCheck.containsKey(entry.getKey()));
        
        // 清理过期的距离剔除记录
        if (distanceCullingReasons.size() > 5) {
            distanceCullingReasons.clear();
        }
        
        // 清理过期的房间连通性记录
        if (roomConnectivity.size() > 10) {
            roomConnectivity.clear();
        }
        
        // 清理过期的日志时间记录
        lastLogTime.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > 300000); // 5分钟过期
    }
    
    /**
     * 获取内存使用情况
     */
    public static String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        return String.format("内存使用: %.1fMB / %.1fMB (最大: %.1fMB)",
                usedMemory / 1024.0 / 1024.0,
                totalMemory / 1024.0 / 1024.0,
                maxMemory / 1024.0 / 1024.0);
    }
}