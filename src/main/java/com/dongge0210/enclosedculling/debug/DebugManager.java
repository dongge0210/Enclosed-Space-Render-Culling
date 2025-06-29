package com.dongge0210.enclosedculling.debug;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import com.dongge0210.enclosedculling.room.RoomManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
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
    
    private static boolean debugMode = false;
    private static final Map<String, Object> debugInfo = new ConcurrentHashMap<>();
    private static final Map<String, Long> performanceTimers = new ConcurrentHashMap<>();
    
    // 性能统计
    private static long totalCullingChecks = 0;
    private static long successfulCulls = 0;
    private static double averageCheckTime = 0.0;
    private static final int SAMPLE_SIZE = 1000;
    private static final List<Double> recentCheckTimes = new ArrayList<>();
    
    /**
     * 切换调试模式
     */
    public static boolean toggleDebugMode() {
        debugMode = !debugMode;
        EnclosedSpaceRenderCulling.LOGGER.info("Debug mode {}", debugMode ? "enabled" : "disabled");
        return debugMode;
    }
    
    /**
     * 获取调试模式状态
     */
    public static boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * 设置调试信息
     */
    public static void setDebugInfo(String key, Object value) {
        if (debugMode) {
            debugInfo.put(key, value);
        }
    }
    
    /**
     * 开始性能计时
     */
    public static void startTimer(String timerName) {
        if (debugMode) {
            performanceTimers.put(timerName, System.nanoTime());
        }
    }
    
    /**
     * 结束性能计时并记录
     */
    public static void endTimer(String timerName) {
        if (debugMode && performanceTimers.containsKey(timerName)) {
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
        if (debugMode) {
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
     * 渲染调试信息到屏幕
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!debugMode) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        GuiGraphics graphics = event.getGuiGraphics();
        int y = 10;
        int lineHeight = 12;
        
        // 渲染标题
        graphics.drawString(mc.font, 
            Component.literal("§e§lEnclosed Culling Debug"), 10, y, 0xFFFFFF);
        y += lineHeight + 5;
        
        // 渲染基本信息
        BlockPos playerPos = mc.player.blockPosition();
        Vec3 playerVec = mc.player.position();
        
        graphics.drawString(mc.font,
            Component.literal("§7Player: " + playerPos.toShortString()), 10, y, 0xFFFFFF);
        y += lineHeight;
        
        // 渲染性能统计
        if (totalCullingChecks > 0) {
            graphics.drawString(mc.font,
                Component.literal("§7Checks: " + totalCullingChecks), 10, y, 0xFFFFFF);
            y += lineHeight;
            
            graphics.drawString(mc.font,
                Component.literal("§7Culled: " + successfulCulls + " (" + 
                    String.format("%.1f%%", (double) successfulCulls / totalCullingChecks * 100) + ")"), 
                10, y, 0xFFFFFF);
            y += lineHeight;
            
            if (averageCheckTime > 0) {
                graphics.drawString(mc.font,
                    Component.literal("§7Avg Time: " + String.format("%.3fms", averageCheckTime)), 
                    10, y, 0xFFFFFF);
                y += lineHeight;
            }
        }
        
        // 渲染房间信息
        String roomStats = RoomManager.getRoomStats();
        if (!roomStats.isEmpty()) {
            String[] lines = roomStats.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                graphics.drawString(mc.font,
                    Component.literal("§7" + line), 10, y, 0xFFFFFF);
                y += lineHeight;
            }
        }
        
        // 渲染动态调试信息
        for (Map.Entry<String, Object> entry : debugInfo.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // 跳过已经显示的信息
            if (key.equals("total_checks") || key.equals("successful_culls") || 
                key.equals("cull_rate") || key.equals("avg_check_time")) {
                continue;
            }
            
            graphics.drawString(mc.font,
                Component.literal("§7" + key + ": " + value), 10, y, 0xFFFFFF);
            y += lineHeight;
            
            // 防止信息过多超出屏幕
            if (y > mc.getWindow().getGuiScaledHeight() - 50) {
                graphics.drawString(mc.font,
                    Component.literal("§8... (more info available)"), 10, y, 0xFFFFFF);
                break;
            }
        }
    }
    
    /**
     * 记录调试日志
     */
    public static void logDebug(String message, Object... args) {
        if (debugMode) {
            EnclosedSpaceRenderCulling.LOGGER.debug("[DEBUG] " + message, args);
        }
    }
    
    /**
     * 记录详细的剔除信息
     */
    public static void logCullingDetails(BlockPos pos, boolean culled, String reason) {
        if (debugMode) {
            setDebugInfo("last_check_pos", pos.toShortString());
            setDebugInfo("last_check_result", culled ? "CULLED" : "VISIBLE");
            setDebugInfo("last_check_reason", reason);
        }
    }
    
    /**
     * 获取性能报告
     */
    public static String getPerformanceReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Enclosed Culling Performance Report ===\n");
        report.append("Total Checks: ").append(totalCullingChecks).append("\n");
        report.append("Successful Culls: ").append(successfulCulls).append("\n");
        
        if (totalCullingChecks > 0) {
            report.append("Cull Rate: ").append(String.format("%.2f%%", 
                (double) successfulCulls / totalCullingChecks * 100)).append("\n");
        }
        
        if (averageCheckTime > 0) {
            report.append("Average Check Time: ").append(String.format("%.3fms", averageCheckTime)).append("\n");
        }
        
        report.append("Debug Info Entries: ").append(debugInfo.size()).append("\n");
        report.append("Active Timers: ").append(performanceTimers.size()).append("\n");
        
        return report.toString();
    }
}
