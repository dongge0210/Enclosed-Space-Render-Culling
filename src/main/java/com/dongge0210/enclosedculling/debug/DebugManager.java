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
    
    // 缓存调试信息以减少闪烁
    private static final Map<String, String> cachedDebugStrings = new ConcurrentHashMap<>();
    private static long lastDebugUpdate = 0;
    private static final long DEBUG_UPDATE_INTERVAL = 100; // 100ms更新一次，提高刷新率
    
    /**
     * 渲染调试信息到屏幕
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        // 使用配置中的调试开关，而不是内部的debugMode
        if (!ModConfig.COMMON.enableDebug.get()) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        // 只在需要时更新调试信息，减少闪烁
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDebugUpdate > DEBUG_UPDATE_INTERVAL) {
            updateCachedDebugInfo();
            lastDebugUpdate = currentTime;
        }
        
        GuiGraphics graphics = event.getGuiGraphics();
        
        // 使用缓存的信息渲染，避免频繁计算
        renderCachedDebugInfo(graphics);
    }
    
    /**
     * 更新缓存的调试信息
     */
    private static void updateCachedDebugInfo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        try {
            BlockPos playerPos = mc.player.blockPosition();
            
            // 更新环境信息
            updateEnvironmentInfo();
            
            // 缓存基本信息
            cachedDebugStrings.put("title", "§e§l封闭空间剔除 - 调试");
            cachedDebugStrings.put("player_pos", "§7玩家位置: " + playerPos.toShortString());
            cachedDebugStrings.put("environment", String.format("§7环境: %s (光照:%d, 渲染距离:%.0f)", 
                environmentType, currentLightLevel, playerRenderDistance));
            
            // 缓存房间信息
            try {
                Integer currentRoomId = RoomManager.getRoomIdAt(mc.level, playerPos);
                Integer currentGroupId = RoomManager.getGroupIdAt(mc.level, playerPos);
                
                if (currentRoomId != null) {
                    cachedDebugStrings.put("room_id", "§7当前房间ID: " + currentRoomId);
                } else {
                    cachedDebugStrings.put("room_id", "§7当前房间: 未识别");
                }
                
                if (currentGroupId != null) {
                    cachedDebugStrings.put("group_id", "§7房间组ID: " + currentGroupId);
                } else {
                    cachedDebugStrings.put("group_id", "§7房间组: 未识别");
                }
                
                // 添加房间连通性状态
                String connectivityStatus = "未知";
                try {
                    // 检查周围房间的连通性
                    BlockPos testPos = playerPos.offset(5, 0, 0);
                    connectivityStatus = RoomManager.getRoomConnectivityStatus(mc.level, playerPos, testPos);
                } catch (Exception e) {
                    connectivityStatus = "检测失败";
                }
                cachedDebugStrings.put("room_connectivity", "§7房间连通性: " + connectivityStatus);
                
            } catch (Exception e) {
                cachedDebugStrings.put("room_id", "§c房间检测错误: " + e.getMessage());
                cachedDebugStrings.put("group_id", "§c房间组检测错误");
            }
            
            // 缓存剔除系统状态 - 分别显示空间剔除和实体剔除
            cachedDebugStrings.put("space_culling_enabled", "§7空间剔除功能: " + 
                (ModConfig.COMMON.enableCulling.get() ? "§a已启用" : "§c已禁用"));
            
            // 实体剔除状态需要考虑冲突检测
            boolean entityCullingDetected = false;
            try {
                // 检查Entity Culling mod是否存在
                Class.forName("net.mehvahdjukaar.entityculling.EntityCullingMod");
                entityCullingDetected = true;
            } catch (ClassNotFoundException ignored) {
                // Entity Culling不存在
            }
            
            if (entityCullingDetected && !ModConfig.COMMON.forceEntityCulling.get()) {
                cachedDebugStrings.put("entity_culling_enabled", "§7实体剔除功能: §c已禁用（Entity Culling冲突）");
            } else if (!ModConfig.COMMON.enableEntityRendering.get()) {
                cachedDebugStrings.put("entity_culling_enabled", "§7实体剔除功能: §c已禁用（配置关闭）");
            } else {
                cachedDebugStrings.put("entity_culling_enabled", "§7实体剔除功能: §a已启用");
            }
            
            // 从debugInfo中获取剔除相关信息
            Object visibleSpaces = debugInfo.get("visible_spaces_count");
            if (visibleSpaces != null) {
                cachedDebugStrings.put("visible_spaces", "§7可见空间数: " + visibleSpaces);
            }
            
            Object cullingRange = debugInfo.get("culling_range");
            if (cullingRange != null) {
                cachedDebugStrings.put("culling_range", "§7剔除范围: " + cullingRange);
            }
            
            Object totalEntitiesObj = debugInfo.get("total_entities_in_range");
            Object culledEntitiesObj = debugInfo.get("culled_entities_this_frame");
            if (totalEntitiesObj != null && culledEntitiesObj != null) {
                cachedDebugStrings.put("entity_stats", 
                    String.format("§7实体统计: %s/%s", culledEntitiesObj, totalEntitiesObj));
            }
            
            // 缓存mod检测信息
            if (neatModDetected) {
                cachedDebugStrings.put("neat_mod", String.format("§e极简血量显示: 已检测 (距离阈值:%.1f)", neatMaxDistance));
            } else {
                cachedDebugStrings.remove("neat_mod");
            }
            
            // 缓存房间信息
            try {
                Integer currentRoomId = RoomManager.getRoomIdAt(mc.level, playerPos);
                if (currentRoomId != null) {
                    cachedDebugStrings.put("room_id", "§7当前房间ID: " + currentRoomId);
                } else {
                    cachedDebugStrings.put("room_id", "§7当前房间: 未识别");
                }
            } catch (Exception e) {
                cachedDebugStrings.put("room_id", "§c房间检测错误");
            }
            
            // 缓存统计信息
            if (totalEntityChecks > 0) {
                cachedDebugStrings.put("entity_header", "§6=== 实体剔除跟踪 ===");
                cachedDebugStrings.put("entity_checks", "§7实体检查: " + totalEntityChecks);
                cachedDebugStrings.put("entity_culled", "§7被剔除实体: " + culledEntities + " (" + 
                    String.format("%.1f%%", (double) culledEntities / totalEntityChecks * 100) + ")");
                cachedDebugStrings.put("current_room", "§7当前房间: " + lastPlayerRoom);
            }
            
            if (totalCullingChecks > 0) {
                cachedDebugStrings.put("block_header", "§6=== 方块剔除统计 ===");
                cachedDebugStrings.put("block_checks", "§7方块检查: " + totalCullingChecks);
                cachedDebugStrings.put("block_culled", "§7成功剔除: " + successfulCulls + " (" + 
                    String.format("%.1f%%", (double) successfulCulls / totalCullingChecks * 100) + ")");
                
                if (averageCheckTime > 0) {
                    cachedDebugStrings.put("avg_time", "§7平均耗时: " + String.format("%.3fms", averageCheckTime));
                }
            }
            
            // 缓存房间统计
            try {
                String roomStats = RoomManager.getRoomStats();
                if (!roomStats.isEmpty()) {
                    String[] lines = roomStats.split("\n");
                    for (int i = 0; i < lines.length && i < 4; i++) {
                        if (!lines[i].trim().isEmpty()) {
                            cachedDebugStrings.put("room_stat_" + i, "§7" + lines[i]);
                        }
                    }
                }
            } catch (Exception e) {
                cachedDebugStrings.put("room_stats", "§c房间统计错误: " + e.getMessage());
            }
            
        } catch (Exception e) {
            cachedDebugStrings.put("error", "§c调试信息更新错误: " + e.getMessage());
        }
    }
    
    /**
     * 渲染缓存的调试信息
     */
    private static void renderCachedDebugInfo(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        int y = 10;
        int lineHeight = 12;
        
        // 渲染标题
        String title = cachedDebugStrings.get("title");
        if (title != null) {
            graphics.drawString(mc.font, Component.literal(title), 10, y, 0xFFFFFF);
            y += lineHeight + 5;
        }
        
        // 按顺序渲染基本信息
        String[] basicKeys = {"player_pos", "environment", "room_id", "group_id", "room_connectivity", 
                              "space_culling_enabled", "entity_culling_enabled", "visible_spaces", "culling_range", "entity_stats", "entity_rendering_status", "neat_mod"};
        for (String key : basicKeys) {
            String value = cachedDebugStrings.get(key);
            if (value != null) {
                graphics.drawString(mc.font, Component.literal(value), 10, y, 0xFFFFFF);
                y += lineHeight;
            }
        }
        
        // 渲染实体剔除信息
        if (cachedDebugStrings.containsKey("entity_header")) {
            y += 5; // 增加间距
            String[] entityKeys = {"entity_header", "entity_checks", "entity_culled", "current_room"};
            for (String key : entityKeys) {
                String value = cachedDebugStrings.get(key);
                if (value != null) {
                    graphics.drawString(mc.font, Component.literal(value), 10, y, 0xFFFFFF);
                    y += lineHeight;
                }
            }
            
            // 渲染最近的实体剔除记录（限制数量以减少闪烁）
            int entityCount = 0;
            for (Map.Entry<String, String> entry : entityCullingReasons.entrySet()) {
                if (entityCount >= 3) break; // 只显示最近3条
                
                String color = entry.getValue().contains("被剔除") ? "§c" : "§a";
                graphics.drawString(mc.font, 
                    Component.literal(color + entry.getValue()), 10, y, 0xFFFFFF);
                y += lineHeight;
                entityCount++;
            }
        }
        
        // 渲染方块剔除信息
        if (cachedDebugStrings.containsKey("block_header")) {
            y += 5; // 增加间距
            String[] blockKeys = {"block_header", "block_checks", "block_culled", "avg_time"};
            for (String key : blockKeys) {
                String value = cachedDebugStrings.get(key);
                if (value != null) {
                    graphics.drawString(mc.font, Component.literal(value), 10, y, 0xFFFFFF);
                    y += lineHeight;
                }
            }
        }
        
        // 渲染房间统计信息
        for (int i = 0; i < 4; i++) {
            String value = cachedDebugStrings.get("room_stat_" + i);
            if (value != null) {
                graphics.drawString(mc.font, Component.literal(value), 10, y, 0xFFFFFF);
                y += lineHeight;
            }
        }
        
        // 渲染错误信息（如果有）
        String error = cachedDebugStrings.get("error");
        if (error != null) {
            graphics.drawString(mc.font, Component.literal(error), 10, y, 0xFFFFFF);
            y += lineHeight;
        }
        
        // 渲染简化的距离剔除信息（避免过多信息导致闪烁）
        if (!distanceCullingReasons.isEmpty() && distanceCullingReasons.size() <= 3) {
            y += 5;
            graphics.drawString(mc.font, Component.literal("§d最近距离剔除:"), 10, y, 0xFFFFFF);
            y += lineHeight;
            
            int distanceCount = 0;
            for (Map.Entry<String, String> entry : distanceCullingReasons.entrySet()) {
                if (distanceCount >= 2) break; // 最多显示2条
                
                String color = entry.getKey().contains("cull") ? "§c" : "§a";
                String truncated = entry.getValue().length() > 50 ? 
                    entry.getValue().substring(0, 47) + "..." : entry.getValue();
                graphics.drawString(mc.font, Component.literal(color + truncated), 10, y, 0xFFFFFF);
                y += lineHeight;
                distanceCount++;
            }
        }
    }
    
    /**
     * 记录调试日志
     */
    public static void logDebug(String message, Object... args) {
        if (ModConfig.COMMON.enableDebug.get()) {
            EnclosedSpaceRenderCulling.LOGGER.debug("[DEBUG] " + message, args);
        }
    }
    
    /**
     * 记录详细的剔除信息
     */
    public static void logCullingDetails(BlockPos pos, boolean culled, String reason) {
        if (ModConfig.COMMON.enableDebug.get()) {
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
        
        return report.toString();
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
     * 记录实体剔除信息
     */
    public static void trackEntityCulling(String entityId, String entityType, BlockPos entityPos, 
                                        BlockPos playerPos, boolean culled, String reason) {
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
     * 记录EntityCulling模组的剔除情况
     */
    public static void trackEntityCullingMod(String entityId, String entityType, 
                                           boolean culledByEntityCulling, String reason) {
        String key = "EC_" + entityId;
        String info = String.format("EntityCulling: %s %s - %s", 
            entityType, culledByEntityCulling ? "剔除" : "保留", reason);
        
        entityCullingReasons.put(key, info);
        lastEntityCheck.put(key, System.currentTimeMillis());
    }
    
    /**
     * 获取实体剔除信息
     */
    public static String getEntityCullingInfo(String entityId, String entityType) {
        String key = entityId + "_" + entityType;
        return entityCullingReasons.getOrDefault(key, "无记录");
    }
    
    /**
     * 获取最近的实体剔除时间
     */
    public static long getLastEntityCullingTime(String entityId, String entityType) {
        String key = entityId + "_" + entityType;
        return lastEntityCheck.getOrDefault(key, 0L);
    }
    
    /**
     * 获取房间连通性信息
     */
    public static String getRoomConnectivityInfo(Integer roomId1, Integer roomId2) {
        String key = "连通性_" + roomId1 + "_to_" + roomId2;
        return roomConnectivity.getOrDefault(key, "无记录");
    }
    
    /**
     * 更新环境信息 - 增强版
     */
    public static void updateEnvironmentInfo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        BlockPos playerPos = mc.player.blockPosition();
        Level level = mc.level;
        
        try {
            // 更新渲染距离
            playerRenderDistance = mc.options.renderDistance().get() * 16.0f;
            
            // 更新光照等级（获取更准确的光照）
            currentLightLevel = Math.max(
                level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, playerPos),
                level.getBrightness(net.minecraft.world.level.LightLayer.SKY, playerPos)
            );
            
            // 详细的环境类型判断
            determineDetailedEnvironmentType(level, playerPos);
            
            // 检测各种mod的兼容性
            detectModCompatibility();
            
            // 更新调试信息
            setDebugInfo("render_distance", String.format("%.1f格", playerRenderDistance));
            setDebugInfo("light_level", currentLightLevel);
            setDebugInfo("environment", environmentType);
            setDebugInfo("is_cave", isInCave ? "是" : "否");
            
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.warn("更新环境信息时出错: {}", e.getMessage());
        }
    }
    
    /**
     * 详细判断环境类型
     */
    private static void determineDetailedEnvironmentType(Level level, BlockPos playerPos) {
        // 维度检查
        if (level.dimension() == Level.NETHER) {
            environmentType = "下界";
            isInCave = false;
            return;
        } else if (level.dimension() == Level.END) {
            environmentType = "末地";
            isInCave = false;
            return;
        }
        
        // 主世界详细环境判断
        int y = playerPos.getY();
        boolean canSeeSky = level.canSeeSky(playerPos);
        boolean isDay = level.isDay();
        
        // 高度因素
        if (y < 0) {
            environmentType = "深层地下";
            isInCave = true;
        } else if (y < 40) {
            if (canSeeSky) {
                environmentType = isDay ? "低海拔地表(白天)" : "低海拔地表(夜晚)";
                isInCave = false;
            } else {
                environmentType = determineUndergroundType(level, playerPos);
                isInCave = true;
            }
        } else if (y < 64) {
            if (canSeeSky) {
                environmentType = isDay ? "地表(白天)" : "地表(夜晚)";
                isInCave = false;
            } else {
                environmentType = "室内/建筑内";
                isInCave = false;
            }
        } else if (y < 120) {
            environmentType = isDay ? "地表高处(白天)" : "地表高处(夜晚)";
            isInCave = false;
        } else {
            environmentType = "高空";
            isInCave = false;
        }
    }
    
    /**
     * 判断地下环境的具体类型
     */
    private static String determineUndergroundType(Level level, BlockPos playerPos) {
        // 检查周围是否有大量空气方块（可能是洞穴）
        int airBlocks = 0;
        int totalBlocks = 0;
        
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos checkPos = playerPos.offset(x, y, z);
                    if (level.getBlockState(checkPos).isAir()) {
                        airBlocks++;
                    }
                    totalBlocks++;
                }
            }
        }
        
        float airRatio = (float) airBlocks / totalBlocks;
        
        if (airRatio > 0.6f) {
            return "大型洞穴";
        } else if (airRatio > 0.3f) {
            return "地下洞穴";
        } else {
            return "地下隧道";
        }
    }
    
    /**
     * 检测mod兼容性
     */
    private static void detectModCompatibility() {
        // 检测极简血量显示mod (Neat)
        if (!neatModDetected) {
            try {
                Class.forName("vazkii.neat.NeatMod");
                neatModDetected = true;
                neatMaxDistance = 24.0f; // Neat默认显示距离
                setDebugInfo("neat_mod", "已检测");
            } catch (ClassNotFoundException e) {
                try {
                    // 尝试其他可能的Neat类
                    Class.forName("vazkii.neat.NeatConfig");
                    neatModDetected = true;
                    neatMaxDistance = 24.0f;
                    setDebugInfo("neat_mod", "已检测(配置类)");
                } catch (ClassNotFoundException e2) {
                    neatModDetected = false;
                    setDebugInfo("neat_mod", "未检测到");
                }
            }
        }
        
        // TODO: 检测其他相关mod
        // 比如：
        // - Waila/HWYLA (What Am I Looking At)
        // - JEI (Just Enough Items)
        // - OptiFine/Sodium
        // - Iron Chests
        // - Applied Energistics
        
        detectPerformanceMods();
        detectUtilityMods();
    }
    
    /**
     * 检测性能优化mod
     */
    private static void detectPerformanceMods() {
        try {
            // OptiFine检测
            Class.forName("optifine.OptiFineClassTransformer");
            setDebugInfo("optifine", "已检测");
        } catch (ClassNotFoundException e) {
            // Sodium检测
            try {
                Class.forName("me.jellysquid.mods.sodium.client.SodiumClientMod");
                setDebugInfo("sodium", "已检测");
            } catch (ClassNotFoundException e2) {
                setDebugInfo("performance_mods", "未检测到");
            }
        }
    }
    
    /**
     * 检测实用工具mod
     */
    private static void detectUtilityMods() {
        // JEI检测
        try {
            Class.forName("mezz.jei.api.JeiPlugin");
            setDebugInfo("jei", "已检测");
        } catch (ClassNotFoundException e) {
            // REI检测
            try {
                Class.forName("me.shedaniel.rei.RoughlyEnoughItemsCore");
                setDebugInfo("rei", "已检测");
            } catch (ClassNotFoundException e2) {
                setDebugInfo("recipe_mods", "未检测到");
            }
        }
        
        // WAILA/HWYLA检测
        try {
            Class.forName("mcp.mobius.waila.Waila");
            setDebugInfo("waila", "已检测");
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("mcp.mobius.waila.api.IWailaPlugin");
                setDebugInfo("hwyla", "已检测");
            } catch (ClassNotFoundException e2) {
                setDebugInfo("info_mods", "未检测到");
            }
        }
    }
    
    /**
     * 智能距离剔除判断 - 增强版
     */
    public static boolean shouldCullByDistance(BlockPos entityPos, BlockPos playerPos, String entityType) {
        updateEnvironmentInfo();
        
        double distance = Math.sqrt(entityPos.distSqr(playerPos));
        
        // 1. 强制渲染距离内必须渲染（最高优先级）
        if (distance <= FORCE_RENDER_DISTANCE) {
            distanceCullingReasons.put("force_render", 
                String.format("距离%.1f格，强制渲染(阈值:%.1f)", distance, FORCE_RENDER_DISTANCE));
            return false;
        }
        
        // 2. 超过绝对最大距离，直接剔除
        if (distance > MAX_CULL_DISTANCE) {
            distanceCullingReasons.put("max_distance", 
                String.format("距离%.1f格，超过绝对最大距离(%.1f)", distance, MAX_CULL_DISTANCE));
            return true;
        }
        
        // 3. 计算动态距离阈值
        float dynamicThreshold = calculateDynamicDistanceThreshold(entityType);
        
        // 4. 实体类型特殊处理
        float entitySpecificThreshold = applyEntitySpecificModifier(entityType, dynamicThreshold);
        
        // 5. 环境因素调整
        float environmentAdjustedThreshold = applyEnvironmentModifier(entitySpecificThreshold);
        
        // 6. Mod兼容性调整
        float finalThreshold = applyModCompatibilityModifier(entityType, environmentAdjustedThreshold);
        
        // 7. 最终判断
        boolean shouldCull = distance > finalThreshold;
        
        // 8. 记录详细信息
        String reason = String.format("%s - 距离:%.1f 阈值:%.1f 环境:%s 光照:%d %s", 
            shouldCull ? "§c被剔除" : "§a保持渲染",
            distance, finalThreshold, environmentType, currentLightLevel,
            neatModDetected ? "Neat模组:是" : "");
        distanceCullingReasons.put(entityType, reason);
        
        return shouldCull;
    }
    
    /**
     * 计算基础动态距离阈值
     */
    private static float calculateDynamicDistanceThreshold(String entityType) {
        // 基于玩家渲染距离计算基础阈值
        float baseThreshold = playerRenderDistance > 0 ? playerRenderDistance * 0.4f : 24.0f;
        
        // 确保在合理范围内
        baseThreshold = Math.max(baseThreshold, 16.0f);
        baseThreshold = Math.min(baseThreshold, 48.0f);
        
        return baseThreshold;
    }
    
    /**
     * 应用实体类型特定的修正因子
     */
    private static float applyEntitySpecificModifier(String entityType, float baseThreshold) {
        String lowerType = entityType.toLowerCase();
        
        // 玩家和重要NPC - 优先级最高
        if (lowerType.contains("player") || lowerType.contains("villager") || 
            lowerType.contains("iron_golem")) {
            return baseThreshold * 1.5f;
        }
        
        // 敌对生物 - 需要较远距离察觉
        if (lowerType.contains("zombie") || lowerType.contains("skeleton") || 
            lowerType.contains("creeper") || lowerType.contains("spider") ||
            lowerType.contains("enderman") || lowerType.contains("witch")) {
            return baseThreshold * 1.3f;
        }
        
        // 蜘蛛特殊处理 - 可能在墙上或天花板
        if (lowerType.contains("spider")) {
            return baseThreshold * 1.1f; // 稍微增加但不过多
        }
        
        // 被动生物 - 中等优先级
        if (lowerType.contains("cow") || lowerType.contains("pig") || 
            lowerType.contains("sheep") || lowerType.contains("chicken")) {
            return baseThreshold * 1.0f;
        }
        
        // 物品实体和经验球 - 较短距离
        if (lowerType.contains("item") || lowerType.contains("experience") ||
            lowerType.contains("arrow") || lowerType.contains("snowball")) {
            return baseThreshold * 0.7f;
        }
        
        // 装饰性实体 - 最短距离
        if (lowerType.contains("painting") || lowerType.contains("armor_stand") ||
            lowerType.contains("item_frame")) {
            return baseThreshold * 0.8f;
        }
        
        return baseThreshold; // 默认不修改
    }
    
    /**
     * 应用环境因素修正
     */
    private static float applyEnvironmentModifier(float threshold) {
        float modifier = 1.0f;
        
        // 光照因素
        if (currentLightLevel < MIN_LIGHT_LEVEL) {
            // 环境过暗，大幅缩短距离
            modifier *= 0.6f;
        } else if (currentLightLevel < 10) {
            // 光线较暗，适度缩短距离
            modifier *= 0.8f;
        }
        
        // 维度和环境类型
        switch (environmentType) {
            case "下界":
                modifier *= 0.7f; // 下界能见度较差
                break;
            case "末地":
                modifier *= 1.1f; // 末地较为开阔
                break;
            case "地下":
            case "洞穴":
                modifier *= 0.65f; // 洞穴环境大幅缩短
                break;
            case "室内":
                modifier *= 0.85f; // 室内适度缩短
                break;
            case "地表(夜晚)":
                modifier *= 0.75f; // 夜晚缩短距离
                break;
            case "地表(白天)":
                modifier *= 1.0f; // 白天保持正常
                break;
        }
        
        return threshold * modifier;
    }
    
    /**
     * 应用Mod兼容性修正
     */
    private static float applyModCompatibilityModifier(String entityType, float threshold) {
        // 极简血量显示mod的影响
        if (neatModDetected) {
            String lowerType = entityType.toLowerCase();
            
            // 对于生物实体，需要考虑血量显示距离
            if (lowerType.contains("player") || lowerType.contains("zombie") || 
                lowerType.contains("skeleton") || lowerType.contains("creeper") ||
                lowerType.contains("villager") || lowerType.contains("cow") ||
                lowerType.contains("pig") || lowerType.contains("sheep")) {
                
                // 确保不超过Neat的显示距离，但也不能太近
                float neatAwareThreshold = Math.min(threshold, neatMaxDistance * 1.1f);
                
                // 在暗环境下进一步缩短
                if (currentLightLevel < MIN_LIGHT_LEVEL) {
                    neatAwareThreshold *= 0.8f;
                }
                
                return Math.max(neatAwareThreshold, FORCE_RENDER_DISTANCE + 2.0f);
            }
        }
        
        // TODO: 可以在这里添加其他mod的兼容性处理
        // 比如：
        // - 极限渲染mod (Extreme Render)
        // - 远景优化mod (Distant Horizons)
        // - 性能优化mod (OptiFine, Sodium)
        
        return threshold;
    }
    
    /**
     * 检查实体是否被墙体遮挡
     */
    public static boolean isBlockedByWalls(BlockPos entityPos, BlockPos playerPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        
        // 简单的射线检测
        Vec3 start = playerPos.getCenter();
        Vec3 end = entityPos.getCenter();
        Vec3 direction = end.subtract(start).normalize();
        double distance = start.distanceTo(end);
        
        int solidBlocks = 0;
        for (double d = 1.0; d < distance; d += 0.5) {
            Vec3 checkPos = start.add(direction.scale(d));
            BlockPos blockPos = BlockPos.containing(checkPos);
            
            if (!mc.level.getBlockState(blockPos).isAir() && 
                !mc.level.getBlockState(blockPos).getBlock().getDescriptionId().contains("glass") &&
                !mc.level.getBlockState(blockPos).getBlock().getDescriptionId().contains("door")) {
                solidBlocks++;
            }
        }
        
        // 如果有2个或以上的实体方块，认为被遮挡
        return solidBlocks >= 2;
    }
    
    /**
     * 综合实体可见性判断
     */
    public static boolean shouldRenderEntity(BlockPos entityPos, BlockPos playerPos, String entityType) {
        // 首先检查是否被墙体遮挡
        if (isBlockedByWalls(entityPos, playerPos)) {
            trackEntityCulling("wall_check", entityType, entityPos, playerPos, true, "被墙体遮挡");
            return false;
        }
        
        // 然后检查距离剔除
        if (shouldCullByDistance(entityPos, playerPos, entityType)) {
            String reason = distanceCullingReasons.getOrDefault("last_reason", "距离剔除");
            trackEntityCulling("distance_check", entityType, entityPos, playerPos, true, reason);
            return false;
        }
        
        // 检查房间连通性
        if (!RoomManager.areRoomsConnectedByDoor(Minecraft.getInstance().level, playerPos, entityPos)) {
            trackEntityCulling("room_check", entityType, entityPos, playerPos, true, "房间不连通");
            return false;
        }
        
        // 都通过，实体应该渲染
        trackEntityCulling("final_check", entityType, entityPos, playerPos, false, "应该渲染");
        return true;
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
                if (roomIdHistory.size() > MAX_ROOM_HISTORY) {
                    roomIdHistory.remove(0);
                }
                
                lastRoomId = currentRoomId;
                
                // 更新debug信息
                setDebugInfo("room_id_changes", roomIdChangeCount);
                setDebugInfo("room_id_last_change", new java.util.Date(lastRoomIdChangeTime).toString());
                setDebugInfo("room_id_history", String.join(",", roomIdHistory));
            }
        }
    }
}
