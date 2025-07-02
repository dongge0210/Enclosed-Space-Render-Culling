
package com.dongge0210.enclosedculling.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import com.dongge0210.enclosedculling.config.ModConfig;
import com.dongge0210.enclosedculling.room.RoomManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
    
    // 简化的方块剔除计数器
    private static long totalBlockChecks = 0;
    private static long blocksVisible = 0;
    private static long blocksCulled = 0;
    private static long lastBlockStatsUpdate = 0;
    
    // 房间连通性跟踪
    private static String lastPlayerRoom = "未知";
    
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
    
    // 环境信息变量
    private static int currentLightLevel = 0;
    private static String environmentType = "未知";
    private static boolean isInCave = false;
    private static float playerRenderDistance = 0;
    private static final int MIN_LIGHT_LEVEL = 1;
    private static final int DARK_ENVIRONMENT_THRESHOLD = 7;
    
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
     * 简化的方块剔除统计 - 直接计数
     */
    public static void recordBlockCheck(boolean visible) {
        if (!ModConfig.COMMON.enableDebug.get()) {
            return;
        }
        
        totalBlockChecks++;
        if (visible) {
            blocksVisible++;
        } else {
            blocksCulled++;
        }
        
        // 每秒更新一次统计显示
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBlockStatsUpdate > 1000) {
            debugInfo.put("block_checks_total", totalBlockChecks);
            debugInfo.put("blocks_visible", blocksVisible);
            debugInfo.put("blocks_culled", blocksCulled);
            if (totalBlockChecks > 0) {
                debugInfo.put("block_cull_rate", String.format("%.1f%%", 
                    (double) blocksCulled / totalBlockChecks * 100));
            }
            lastBlockStatsUpdate = currentTime;
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
    
    // ...existing code...
    
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
        
        // 重置简化的方块统计
        totalBlockChecks = 0;
        blocksVisible = 0;
        blocksCulled = 0;
        lastBlockStatsUpdate = 0;
        
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
        
        // 清理过期的日志时间记录
        lastLogTime.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > 300000); // 5分钟过期
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
            
            // 检测玩家周围区块范围内的房间（基于玩家渲染距离，最小1个区块）
            int renderDistance = Math.max(1, mc.options.renderDistance().get());
            int chunkRange = Math.min(renderDistance, 4); // 最多检测4个区块范围
            
            for (int chunkX = -chunkRange; chunkX <= chunkRange; chunkX++) {
                for (int chunkZ = -chunkRange; chunkZ <= chunkRange; chunkZ++) {
                    for (int y = -1; y <= 1; y++) {
                        BlockPos checkPos = playerPos.offset(chunkX * 16, y, chunkZ * 16);
                        RoomManager.getRoomIdAt(mc.level, checkPos);
                    }
                }
            }
            
            setDebugInfo("room_detection", "已触发(" + chunkRange + "区块)");
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
        
        report.append("调试信息条目: ").append(debugInfo.size()).append("\n");
        report.append("活跃计时器: ").append(performanceTimers.size()).append("\n");
        
        // 添加内存使用情况
        report.append("\n").append(getMemoryUsage()).append("\n");
        
        return report.toString();
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
        if (mc.player == null || mc.level == null || mc.font == null) return;
        
        int x = 10;
        int y = 10;
        int lineHeight = 12;
        int currentY = y;
        
        // 标题
        graphics.drawString(mc.font, Component.literal("§e§l[封闭空间剔除 - 调试模式]"), x, currentY, 0xFFFFFF);
        currentY += lineHeight + 3;
        
        // 基本统计 - 使用简化的方块统计
        String blockStats;
        if (!ModConfig.COMMON.enableCulling.get()) {
            blockStats = "§7方块剔除: §c已关闭";
        } else {
            String totalBlockChecksStr = formatLargeNumber(totalBlockChecks);
            String blocksCulledStr = formatLargeNumber(blocksCulled);
            if (totalBlockChecks > 0) {
                blockStats = String.format("§7方块检查: §f%s §7| 剔除: §a%s §7| 成功率: §e%.1f%%", 
                    totalBlockChecksStr, blocksCulledStr, 
                    (double) blocksCulled / totalBlockChecks * 100);
            } else {
                blockStats = "§7方块检查: §f0 §7| 等待数据...";
            }
        }
        graphics.drawString(mc.font, Component.literal(blockStats), x, currentY, 0xFFFFFF);
        currentY += lineHeight;
        
        // 性能信息
        if (averageCheckTime > 0) {
            String performance = String.format("§7平均耗时: §f%.3fms", averageCheckTime);
            graphics.drawString(mc.font, Component.literal(performance), x, currentY, 0xFFFFFF);
            currentY += lineHeight;
        }
        
        // 房间信息 - 房间ID是标识符，不需要格式化
        try {
            BlockPos playerPos = mc.player.blockPosition();
            Integer roomId = RoomManager.getRoomIdAt(mc.level, playerPos);
            Integer groupId = RoomManager.getGroupIdAt(mc.level, playerPos);
            
            String roomIdStr = roomId != null ? roomId.toString() : "§c未知";
            String groupIdStr = groupId != null ? groupId.toString() : "§c未知";
            
            String roomInfo = String.format("§7房间ID: §f%s §7| 组ID: §f%s", roomIdStr, groupIdStr);
            graphics.drawString(mc.font, Component.literal(roomInfo), x, currentY, 0xFFFFFF);
            currentY += lineHeight;
            
            // 房间连通性状态
            String connectivity = RoomManager.getRoomConnectivityStatus(mc.level, playerPos, playerPos);
            graphics.drawString(mc.font, Component.literal("§7连通性: §f" + connectivity), x, currentY, 0xFFFFFF);
            currentY += lineHeight;
            
        } catch (Exception e) {
            graphics.drawString(mc.font, Component.literal("§c房间信息获取失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误")), x, currentY, 0xFFFFFF);
            currentY += lineHeight;
        }
        
        // 环境信息
        try {
            updateEnvironmentInfo(mc.level, mc.player.blockPosition());
            String envInfo;
            int color = 0xFFFFFF;
            if (currentLightLevel == 0) {
                envInfo = "§7环境: §9瞎了 (光照: 0)";
                color = 0x3399FF; // 蓝色
            } else if (currentLightLevel >= 15) {
                envInfo = "§7环境: §f亮 (光照: 15)";
                color = 0xFFFFFF; // 白色
            } else {
                envInfo = String.format("§7环境: §f%s §7(光照: §f%d§7)", environmentType, currentLightLevel);
            }
            graphics.drawString(mc.font, Component.literal(envInfo), x, currentY, color);
            currentY += lineHeight;
        } catch (Exception e) {
            graphics.drawString(mc.font, Component.literal("§c环境信息错误"), x, currentY, 0xFFFFFF);
            currentY += lineHeight;
        }
        
        // 缓存信息
        try {
            String cacheInfo = String.format("§7区块缓存: §f%d §7| 调试条目: §f%d",
                RoomManager.getChunkCacheSize(), debugInfo.size());
            graphics.drawString(mc.font, Component.literal(cacheInfo), x, currentY, 0xFFFFFF);
            currentY += lineHeight;
        } catch (Exception e) {
            graphics.drawString(mc.font, Component.literal("§c缓存信息错误"), x, currentY, 0xFFFFFF);
            currentY += lineHeight;
        }
        
        // 配置状态和剔除关闭原因
        String configStatus;
        int configColor = 0xFFFFFF;
        
        if (!ModConfig.COMMON.enableCulling.get()) {
            configStatus = "§7剔除功能: §c禁用 (配置关闭)";
            configColor = 0xFF6666;
        } else {
            configStatus = String.format("§7剔除功能: %s §7| 热重载: %s", 
                ModConfig.COMMON.enableCulling.get() ? "§a启用" : "§c禁用", 
                ModConfig.COMMON.enableHotReload.get() ? "§a启用" : "§c禁用");
        }
        graphics.drawString(mc.font, Component.literal(configStatus), x, currentY, configColor);
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

    /**
     * 获取调试信息映射
     */
    public static Map<String, Object> getDebugInfo() {
        return debugInfo;
    }
    
    /**
     * 安全地获取配置值，避免在配置未加载时崩溃
     */
    public static boolean safeGetConfigBool(java.util.function.Supplier<Boolean> configGetter, boolean defaultValue) {
        try {
            return configGetter.get();
        } catch (Exception e) {
            // 配置还没有加载完成，返回默认值
            return defaultValue;
        }
    }
    
    /**
     * 安全地获取配置值，避免在配置未加载时崩溃
     */
    public static int safeGetConfigInt(java.util.function.Supplier<Integer> configGetter, int defaultValue) {
        try {
            return configGetter.get();
        } catch (Exception e) {
            // 配置还没有加载完成，返回默认值
            return defaultValue;
        }
    }
    
    /**
     * 格式化大数字，1000以上用k单位表示
     */
    private static String formatLargeNumber(long number) {
        if (number >= 1000) {
            return String.format("%.1fk", number / 1000.0);
        }
        return String.valueOf(number);
    }
}
