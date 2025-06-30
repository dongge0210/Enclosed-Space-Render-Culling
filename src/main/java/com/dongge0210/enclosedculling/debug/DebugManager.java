package com.dongge0210.enclosedculling.debug;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import com.dongge0210.enclosedculling.config.ModConfig;
import com.dongge0210.enclosedculling.room.RoomManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    
    /**
     * 记录详细的剔除信息
     */
    public static void logCullingDetails(BlockPos pos, boolean culled, String reason) {
        if (!shouldLogDebugInfo("culling_details")) {
            return;
        }
        
        try {
            // 使用简洁的日志格式，只记录关键信息，使用DEBUG级别
            EnclosedSpaceRenderCulling.LOGGER.debug("[Culling] {}: {} ({})", 
                pos.toShortString(), culled ? "CULLED" : "VISIBLE", reason);
        } catch (Exception e) {
            // 静默处理异常
        }
    }
    
    /**
     * 记录调试日志
     */
    public static void logDebug(String message, Object... args) {
        if (!shouldLogDebugInfo("debug_log")) {
            return;
        }
        
        try {
            // 清理日志前缀，使用简洁的格式，只在DEBUG级别输出
            if (args.length > 0) {
                EnclosedSpaceRenderCulling.LOGGER.debug("[Debug] " + message, args);
            } else {
                EnclosedSpaceRenderCulling.LOGGER.debug("[Debug] " + message);
            }
        } catch (Exception e) {
            // 静默处理异常，避免调试功能影响正常运行
        }
    }
    
    /**
     * 检查是否应该记录调试信息（统一的实现）
     */
    private static boolean shouldLogDebugInfo(String logType) {
        try {
            if (!ModConfig.COMMON.enableDebug.get()) {
                return false;
            }
            
            long currentTime = System.currentTimeMillis();
            Long lastTime = lastLogTime.get(logType);
            
            if (lastTime != null && (currentTime - lastTime) < LOG_THROTTLE_INTERVAL) {
                return false;
            }
            
            lastLogTime.put(logType, currentTime);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 记录实体剔除信息
     */
    public static void trackEntityCulling(String entityId, String entityType, BlockPos entityPos, 
                                        BlockPos playerPos, boolean culled, String reason) {
        if (!ModConfig.COMMON.enableDebug.get()) {
            return;
        }
        
        totalEntityChecks++;
        if (culled) {
            culledEntities++;
        }
        
        String key = entityId + "_" + entityType;
        String info = String.format("%s@%s %s: %s", 
            entityType, entityPos.toShortString(), 
            culled ? "被剔除" : "可见", reason);
        
        entityCullingReasons.put(key, info);
        lastEntityCheck.put(key, System.currentTimeMillis());
        
        // 跟踪房间信息
        try {
            Integer playerRoomId = RoomManager.getRoomIdAt(Minecraft.getInstance().level, playerPos);
            Integer entityRoomId = RoomManager.getRoomIdAt(Minecraft.getInstance().level, entityPos);
            
            lastPlayerRoom = playerRoomId != null ? "房间" + playerRoomId : "未识别";
            lastTargetRoom = entityRoomId != null ? "房间" + entityRoomId : "未识别";
            
            String connectivityKey = "连通性_" + playerRoomId + "_to_" + entityRoomId;
            String connectivityInfo = String.format("玩家:%s -> 实体:%s", lastPlayerRoom, lastTargetRoom);
            roomConnectivity.put(connectivityKey, connectivityInfo);
            
        } catch (Exception e) {
            roomConnectivity.put("error", "房间检测错误: " + e.getMessage());
        }
        
        // 清理过期记录（保留最近1分钟的记录）
        long now = System.currentTimeMillis();
        lastEntityCheck.entrySet().removeIf(entry -> now - entry.getValue() > 60000);
        entityCullingReasons.entrySet().removeIf(entry -> 
            !lastEntityCheck.containsKey(entry.getKey()));
    }
    
    /**
     * 强制触发房间检测（用于调试）
     */
    public static void triggerRoomDetection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        BlockPos playerPos = mc.player.blockPosition();
        try {
            // 强制检测玩家周围的房间
            RoomManager.getRoomIdAt(mc.level, playerPos);
            
            // 检测玩家周围16个方块范围内的房间
            for (int x = -8; x <= 8; x += 4) {
                for (int z = -8; z <= 8; z += 4) {
                    for (int y = -4; y <= 4; y += 2) {
                        BlockPos checkPos = playerPos.offset(x, y, z);
                        RoomManager.getRoomIdAt(mc.level, checkPos);
                    }
                }
            }
            
            setDebugInfo("room_detection", "已触发");
        } catch (Exception e) {
            setDebugInfo("room_detection_error", e.getMessage());
        }
    }
    
    /**
     * 获取性能报告
     */
    public static String getPerformanceReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== 封闭空间剔除性能报告 ===\n");
        report.append("总检查次数: ").append(totalCullingChecks).append("\n");
        report.append("成功剔除次数: ").append(successfulCulls).append("\n");
        
        if (totalCullingChecks > 0) {
            report.append("剔除成功率: ").append(String.format("%.2f%%", 
                (double) successfulCulls / totalCullingChecks * 100)).append("\n");
        }
        
        if (averageCheckTime > 0) {
            report.append("平均检查耗时: ").append(String.format("%.3fms", averageCheckTime)).append("\n");
        }
        
        if (totalEntityChecks > 0) {
            report.append("实体检查次数: ").append(totalEntityChecks).append("\n");
            report.append("被剔除实体: ").append(culledEntities).append("\n");
            report.append("实体剔除率: ").append(String.format("%.2f%%", 
                (double) culledEntities / totalEntityChecks * 100)).append("\n");
        }
        
        report.append("调试信息条目: ").append(debugInfo.size()).append("\n");
        report.append("活跃计时器: ").append(performanceTimers.size()).append("\n");
        
        // 添加内存使用情况
        report.append("\n").append(getMemoryUsage()).append("\n");
        
        return report.toString();
    }

    /**
     * 渲染调试信息到屏幕
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        
        // 检查是否启用调试模式
        try {
            if (!ModConfig.COMMON.enableDebug.get()) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        
        // 检查GUI是否被关闭
        if (mc.options.hideGui) {
            return;
        }
        
        // 简单的调试信息显示
        renderSimpleDebugInfo(event.getGuiGraphics());
    }
    
    /**
     * 渲染简单的调试信息
     */
    private static void renderSimpleDebugInfo(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        int x = 10;
        int y = 10;
        int lineHeight = 12;
        int currentY = y;
        
        // 标题
        graphics.drawString(mc.font, Component.literal("§e[封闭空间剔除调试]"), x, currentY, 0xFFFFFF);
        currentY += lineHeight + 2;
        
        // 基本统计
        String stats = String.format("检查: %d | 剔除: %d | 成功率: %.1f%%", 
            totalCullingChecks, successfulCulls, 
            totalCullingChecks > 0 ? (double) successfulCulls / totalCullingChecks * 100 : 0);
        graphics.drawString(mc.font, Component.literal(stats), x, currentY, 0xFFFFFF);
        currentY += lineHeight;
        
        // 性能信息
        if (averageCheckTime > 0) {
            String performance = String.format("平均耗时: %.3fms", averageCheckTime);
            graphics.drawString(mc.font, Component.literal(performance), x, currentY, 0xFFFFFF);
            currentY += lineHeight;
        }
        
        // 房间信息
        try {
            BlockPos playerPos = mc.player.blockPosition();
            Integer roomId = RoomManager.getRoomIdAt(mc.level, playerPos);
            Integer groupId = RoomManager.getGroupIdAt(mc.level, playerPos);
            
            String roomInfo = String.format("房间ID: %s | 组ID: %s", 
                roomId != null ? roomId.toString() : "未知",
                groupId != null ? groupId.toString() : "未知");
            graphics.drawString(mc.font, Component.literal(roomInfo), x, currentY, 0xFFFFFF);
            currentY += lineHeight;
        } catch (Exception e) {
            graphics.drawString(mc.font, Component.literal("房间信息获取失败"), x, currentY, 0xFFFFFF);
            currentY += lineHeight;
        }
        
        // 实体剔除统计
        if (totalEntityChecks > 0) {
            String entityStats = String.format("实体检查: %d | 剔除: %d | 剔除率: %.1f%%",
                totalEntityChecks, culledEntities,
                (double) culledEntities / totalEntityChecks * 100);
            graphics.drawString(mc.font, Component.literal(entityStats), x, currentY, 0xFFFFFF);
            currentY += lineHeight;
        }
        
        // 缓存信息
        String cacheInfo = String.format("区块缓存: %d | 调试条目: %d",
            RoomManager.getChunkCacheSize(), debugInfo.size());
        graphics.drawString(mc.font, Component.literal(cacheInfo), x, currentY, 0xFFFFFF);
    }

    /**
     * 更新环境信息
     */
    public static void updateEnvironmentInfo(Level level, BlockPos playerPos) {
        if (!ModConfig.COMMON.enableDebug.get()) return;
        
        try {
            // 更新光照等级
            currentLightLevel = level.getMaxLocalRawBrightness(playerPos);
            
            // 判断环境类型
            if (currentLightLevel < MIN_LIGHT_LEVEL) {
                environmentType = "极暗";
            } else if (currentLightLevel < DARK_ENVIRONMENT_THRESHOLD) {
                environmentType = "暗";
            } else {
                environmentType = "亮";
            }
            
            // 判断是否在洞穴中
            isInCave = playerPos.getY() < 60 && !level.canSeeSky(playerPos);
            
            // 更新玩家渲染距离
            Minecraft mc = Minecraft.getInstance();
            if (mc.options != null) {
                playerRenderDistance = mc.options.renderDistance().get() * 16.0f;
            }
            
        } catch (Exception e) {
            environmentType = "错误";
        }
    }
}
