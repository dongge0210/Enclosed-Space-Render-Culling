package com.dongge0210.enclosedculling.culling;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import com.dongge0210.enclosedculling.config.ModConfig;
import com.dongge0210.enclosedculling.debug.DebugManager;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class SpaceCullingManager {
    private SpaceConnectivityAnalyzer analyzer = null;
    private BlockPos lastPlayerPos = null;
    private Level lastWorld = null;

    // 剔除范围和泛洪步数（建议做成config）
    private static final int MAX_STEPS = 8000;

    public static void register() {
        SpaceCullingManager instance = new SpaceCullingManager();
        MinecraftForge.EVENT_BUS.register(instance);
        EnclosedSpaceRenderCulling.LOGGER.info("SpaceCullingManager 已注册到事件总线。");
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Level world = mc.level;
        net.minecraft.world.entity.player.Player player = mc.player;
        BlockPos playerPos = player.blockPosition();

        // 空间连通性分析（用于方块剔除和房间检测）
        boolean spaceCullingEnabled = false;
        try {
            spaceCullingEnabled = ModConfig.COMMON.enableCulling.get();
        } catch (Exception e) {
            // 配置还没有加载完成，跳过空间剔除
        }

        // 只有空间剔除启用时才更新分析器
        if (spaceCullingEnabled) {
            if (analyzer == null || lastWorld != world || lastPlayerPos == null || !lastPlayerPos.equals(playerPos)) {
                analyzer = new SpaceConnectivityAnalyzer(world, MAX_STEPS);
                analyzer.floodFrom(playerPos);
                lastPlayerPos = playerPos;
                lastWorld = world;
                
                // 更新调试信息
                updateDebugInfo(mc, world, playerPos, player);
            }
        } else {
            // 空间剔除关闭时，清除分析器
            analyzer = null;
            lastPlayerPos = null;
            lastWorld = null;
        }
    }
    
    /**
     * 更新调试信息
     */
    private void updateDebugInfo(Minecraft mc, Level world, BlockPos playerPos, net.minecraft.world.entity.player.Player player) {
        try {
            String playerId = player.getUUID().toString();
            Integer playerRoomId = com.dongge0210.enclosedculling.room.RoomManager.getRoomIdAtStable(world, playerPos, playerId);
            Integer playerGroupId = null;
            
            if (playerRoomId != null) {
                playerGroupId = com.dongge0210.enclosedculling.room.RoomManager.getGroupIdForRoom(playerRoomId);
            }
            
            // 记录详细的调试信息
            DebugManager.setDebugInfo("player_position", playerPos.toShortString());
            
            // 检查是否在室外，如果是则显示"房间外"
            boolean isPlayerOutdoor = world.canSeeSky(playerPos);
            if (isPlayerOutdoor) {
                DebugManager.setDebugInfo("player_room_id", "房间外");
                DebugManager.setDebugInfo("player_group_id", "室外区域");
            } else {
                DebugManager.setDebugInfo("player_room_id", playerRoomId != null ? playerRoomId.toString() : "未识别");
                DebugManager.setDebugInfo("player_group_id", playerGroupId != null ? playerGroupId.toString() : "未识别");
            }
            
            // 跟踪roomId变化
            if (playerRoomId != null) {
                DebugManager.trackRoomIdChange(playerRoomId.toString());
            }
            
            // 添加roomId稳定性监控
            DebugManager.setDebugInfo("room_id_stable", "使用稳定算法");
            DebugManager.setDebugInfo("room_cache_status", "3秒冷却期");
            
            // 简化的房间信息 - 删除实体渲染状态
            DebugManager.setDebugInfo("culling_analyzer_updated", "已更新");
            DebugManager.setDebugInfo("visible_spaces_count", analyzer.getVisibleSpaceCount());
            DebugManager.setDebugInfo("culling_method", "仅空间连通性");
            DebugManager.setDebugInfo("max_flood_steps", MAX_STEPS);
            
            // 获取房间统计信息
            String roomStats = com.dongge0210.enclosedculling.room.RoomManager.getRoomStats();
            if (!roomStats.isEmpty()) {
                String[] lines = roomStats.split("\n");
                for (int i = 0; i < Math.min(lines.length, 3); i++) {
                    DebugManager.setDebugInfo("room_stat_" + i, lines[i].trim());
                }
            }
            
            EnclosedSpaceRenderCulling.LOGGER.debug("Space connectivity analyzer updated - Position: {}, Room: {}, Group: {}, Visible spaces: {}", 
                playerPos, playerRoomId, playerGroupId, analyzer.getVisibleSpaceCount());
                
        } catch (Exception e) {
            DebugManager.setDebugInfo("player_room_error", "房间检测错误: " + e.getMessage());
            EnclosedSpaceRenderCulling.LOGGER.warn("Failed to get player room info: {}", e.getMessage());
        }
    }

    /**
     * 获取空间连通性分析器
     */
    public SpaceConnectivityAnalyzer getAnalyzer() {
        return analyzer;
    }
}
