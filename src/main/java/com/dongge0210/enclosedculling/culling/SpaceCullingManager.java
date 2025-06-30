package com.dongge0210.enclosedculling.culling;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import com.dongge0210.enclosedculling.config.ModConfig;
import com.dongge0210.enclosedculling.debug.DebugManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;

public class SpaceCullingManager {
    private SpaceConnectivityAnalyzer analyzer = null;
    private BlockPos lastPlayerPos = null;
    private Level lastWorld = null;

    // 剔除范围和泛洪步数（建议做成config）
    private static final int CULL_RANGE = 64;
    private static final int MAX_STEPS = 8000;
    
    // Entity Culling冲突检测
    private static Boolean hasEntityCulling = null;
    private static boolean entityCullingChecked = false;

    public static void register() {
        SpaceCullingManager instance = new SpaceCullingManager();
        MinecraftForge.EVENT_BUS.register(instance);
        EnclosedSpaceRenderCulling.LOGGER.info("SpaceCullingManager 已注册到事件总线。");
        
        // 早期检测Entity Culling冲突
        if (instance.detectEntityCulling()) {
            EnclosedSpaceRenderCulling.LOGGER.warn("==================================================");
            EnclosedSpaceRenderCulling.LOGGER.warn("检测到Entity Culling模组！");
            EnclosedSpaceRenderCulling.LOGGER.warn("为避免冲突，Enclosed-Space-Render-Culling的实体剔除功能已被禁用。");
            EnclosedSpaceRenderCulling.LOGGER.warn("空间连通性分析和房间检测功能仍然正常工作。");
            EnclosedSpaceRenderCulling.LOGGER.warn("==================================================");
        }
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;
        
        // 检查剔除功能是否启用
        if (!ModConfig.COMMON.enableCulling.get()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Level world = mc.level;
        net.minecraft.world.entity.player.Player player = mc.player;
        BlockPos playerPos = player.blockPosition();

        // 只有玩家移动或世界切换时才刷新泛洪（大幅优化性能）
        if (analyzer == null || lastWorld != world || lastPlayerPos == null || !lastPlayerPos.equals(playerPos)) {
            analyzer = new SpaceConnectivityAnalyzer(world, MAX_STEPS);
            analyzer.floodFrom(playerPos);
            lastPlayerPos = playerPos;
            lastWorld = world;
            
            // 获取玩家房间信息
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
                
                // 添加新的智能渲染信息
                SmartRenderingContext debugContext = getSmartRenderingContext(mc, world, playerPos);
                DebugManager.setDebugInfo("night_vision_active", debugContext.hasNightVision ? "是" : "否");
                DebugManager.setDebugInfo("raid_active", debugContext.isRaidActive ? "村庄袭击中" : "无袭击");
                if (debugContext.hasNightVision) {
                    DebugManager.setDebugInfo("lighting_factor", "夜视: 忽略环境光照");
                } else {
                    DebugManager.setDebugInfo("lighting_factor", "环境光照: " + debugContext.lightLevel);
                }
                
                // 统一显示实体渲染功能状态（Beta标签），包含冲突检测信息
                if (detectEntityCulling() && !ModConfig.COMMON.forceEntityCulling.get()) {
                    DebugManager.setDebugInfo("entity_rendering_status", "§c[Beta] 智能实体渲染已禁用 - Entity Culling冲突");
                } else if (!ModConfig.COMMON.enableEntityRendering.get()) {
                    DebugManager.setDebugInfo("entity_rendering_status", "§c[Beta] 智能实体渲染已禁用 - 配置关闭");
                } else {
                    DebugManager.setDebugInfo("entity_rendering_status", "§e[Beta] 智能实体渲染已启用");
                }
                
                DebugManager.setDebugInfo("culling_analyzer_updated", "已更新");
                DebugManager.setDebugInfo("visible_spaces_count", analyzer.getVisibleSpaceCount());
                DebugManager.setDebugInfo("culling_range", CULL_RANGE + "格");
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

        // 剔除不可见空间的实体 - 但如果检测到Entity Culling模组则跳过
        if (analyzer != null && player != null) {
            String disableReason = null;
            
            // 检测Entity Culling模组冲突
            if (detectEntityCulling() && !ModConfig.COMMON.forceEntityCulling.get()) {
                disableReason = "Entity Culling冲突";
                DebugManager.setDebugInfo("entity_culling_conflict", "检测到Entity Culling模组，已禁用实体剔除功能以避免冲突");
                DebugManager.setDebugInfo("entity_culling_reason", "为避免与Entity Culling模组冲突而禁用");
            }
            // 检查实体渲染Beta功能是否启用
            else if (!ModConfig.COMMON.enableEntityRendering.get()) {
                disableReason = "配置关闭";
                DebugManager.setDebugInfo("entity_rendering_disabled", "智能实体渲染功能已在配置中禁用");
            }
            
            // 如果有任何禁用原因，统一处理
            if (disableReason != null) {
                // 统一显示禁用状态，避免重复信息
                DebugManager.setDebugInfo("entity_rendering_status", "§c[Beta] 智能实体渲染已禁用 - " + disableReason);
                // 清除可能的重复信息
                DebugManager.setDebugInfo("total_entities_in_range", null);
                DebugManager.setDebugInfo("culled_entities_this_frame", null);
                return; // 直接返回，不进行实体剔除
            }
            
            int totalEntities = 0;
            int culledEntities = 0;
            
            // 获取智能渲染参数
            SmartRenderingContext renderContext = getSmartRenderingContext(mc, world, playerPos);
            
            for (Entity entity : world.getEntities(null, player.getBoundingBox().inflate(CULL_RANGE))) {
                if (entity == player) continue; // 不剔除玩家自己
                
                totalEntities++;
                BlockPos pos = entity.blockPosition();
                double distance = player.distanceTo(entity);
                
                // 综合判断是否应该渲染此实体
                boolean shouldRender = shouldRenderEntity(entity, pos, distance, renderContext, analyzer);
                
                if (!shouldRender) {
                    // 隐藏实体及其相关物品（解决掠夺者弩显示问题）
                    hideEntityCompletely(entity);
                    culledEntities++;
                    
                    // 记录剔除信息
                    String entityType = entity.getType().getDescription().getString();
                    String reason = getDetailedCullingReason(entity, pos, distance, renderContext, analyzer);
                    DebugManager.trackEntityCulling(
                        entity.getUUID().toString(), 
                        entityType, 
                        pos, 
                        playerPos, 
                        true, 
                        reason
                    );
                } else {
                    // 恢复可见性
                    showEntityCompletely(entity);
                    
                    String entityType = entity.getType().getDescription().getString();
                    DebugManager.trackEntityCulling(
                        entity.getUUID().toString(), 
                        entityType, 
                        pos, 
                        playerPos, 
                        false, 
                        "智能渲染:可见"
                    );
                }
            }
            
            // 更新调试统计
            DebugManager.setDebugInfo("total_entities_in_range", totalEntities);
            DebugManager.setDebugInfo("culled_entities_this_frame", culledEntities);
            
            if (culledEntities > 0) {
                EnclosedSpaceRenderCulling.LOGGER.debug("Culled {} out of {} entities", culledEntities, totalEntities);
            }
        }
        // 方块剔除需要事件或Mixin配合（如需直接跳过渲染,告诉我）
    }
    
    // === 智能渲染系统 ===
    
    /**
     * 智能渲染上下文 - 包含环境和设置信息
     */
    private static class SmartRenderingContext {
        public final float renderDistance;
        public final float gamma; // 游戏亮度
        public final int lightLevel;
        public final boolean isOutdoor;
        public final boolean isDay;
        public final float fov;
        public final boolean hasOptiFine;
        public final boolean hasEmbeddium;
        public final String dimension;
        public final boolean hasNightVision; // 玩家是否有夜视效果
        public final boolean isRaidActive; // 是否有活跃的村庄袭击
        
        public SmartRenderingContext(float renderDistance, float gamma, int lightLevel, 
                                   boolean isOutdoor, boolean isDay, float fov,
                                   boolean hasOptiFine, boolean hasEmbeddium, String dimension,
                                   boolean hasNightVision, boolean isRaidActive) {
            this.renderDistance = renderDistance;
            this.gamma = gamma;
            this.lightLevel = lightLevel;
            this.isOutdoor = isOutdoor;
            this.isDay = isDay;
            this.fov = fov;
            this.hasOptiFine = hasOptiFine;
            this.hasEmbeddium = hasEmbeddium;
            this.dimension = dimension;
            this.hasNightVision = hasNightVision;
            this.isRaidActive = isRaidActive;
        }
    }
    
    /**
     * 获取智能渲染上下文
     */
    private SmartRenderingContext getSmartRenderingContext(Minecraft mc, Level world, BlockPos playerPos) {
        try {
            // 获取玩家设置的渲染距离
            float renderDistance = mc.options.renderDistance().get().floatValue() * 16.0f;
            
            // 获取游戏亮度设置
            float gamma = mc.options.gamma().get().floatValue();
            
            // 获取环境光照
            int lightLevel = Math.max(
                world.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, playerPos),
                world.getBrightness(net.minecraft.world.level.LightLayer.SKY, playerPos)
            );
            
            // 判断是否在室外
            boolean isOutdoor = world.canSeeSky(playerPos);
            boolean isDay = world.isDay();
            
            // 获取视野角度
            float fov = mc.options.fov().get().floatValue();
            
            // 检测优化模组
            boolean hasOptiFine = detectOptiFine();
            boolean hasEmbeddium = detectEmbeddium();
            
            String dimension = world.dimension().toString();
            
            // 检测玩家是否有夜视效果
            boolean hasNightVision = false;
            if (mc.player != null && ModConfig.COMMON.enableNightVisionSupport.get()) {
                hasNightVision = mc.player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
            }
            
            // 检测是否有活跃的村庄袭击
            boolean isRaidActive = ModConfig.COMMON.enableRaidDetection.get() && detectActiveRaid(world, playerPos);
            
            return new SmartRenderingContext(renderDistance, gamma, lightLevel, isOutdoor, isDay, fov, 
                                           hasOptiFine, hasEmbeddium, dimension, hasNightVision, isRaidActive);
            
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.warn("Failed to get smart rendering context: {}", e.getMessage());
            // 返回默认值
            return new SmartRenderingContext(256.0f, 1.0f, 15, true, true, 70.0f, false, false, "minecraft:overworld", false, false);
        }
    }
    
    /**
     * 检测OptiFine
     */
    private boolean detectOptiFine() {
        try {
            Class.forName("optifine.OptiFineClassTransformer");
            return true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("net.optifine.Config");
                return true;
            } catch (ClassNotFoundException e2) {
                return false;
            }
        }
    }
    
    /**
     * 检测Embeddium
     */
    private boolean detectEmbeddium() {
        try {
            Class.forName("me.jellysquid.mods.sodium.client.SodiumClientMod");
            return true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("org.embeddedt.embeddium.impl.Embeddium");
                return true;
            } catch (ClassNotFoundException e2) {
                return false;
            }
        }
    }
    
    /**
     * 检测Entity Culling模组
     */
    private boolean detectEntityCulling() {
        if (entityCullingChecked && hasEntityCulling != null) {
            return hasEntityCulling;
        }
        
        try {
            // 检测Entity Culling的主要类
            Class.forName("net.tr7zw.entityculling.EntityCulling");
            hasEntityCulling = true;
            EnclosedSpaceRenderCulling.LOGGER.info("检测到Entity Culling模组，禁用实体剔除功能以避免冲突");
        } catch (ClassNotFoundException e) {
            try {
                // 检测其他可能的Entity Culling类
                Class.forName("net.tr7zw.entityculling.CullTask");
                hasEntityCulling = true;
                EnclosedSpaceRenderCulling.LOGGER.info("检测到Entity Culling模组，禁用实体剔除功能以避免冲突");
            } catch (ClassNotFoundException e2) {
                try {
                    // 检测通过模组ID
                    if (net.minecraftforge.fml.ModList.get().isLoaded("entityculling")) {
                        hasEntityCulling = true;
                        EnclosedSpaceRenderCulling.LOGGER.info("检测到Entity Culling模组（通过模组ID），禁用实体剔除功能以避免冲突");
                    } else {
                        hasEntityCulling = false;
                    }
                } catch (Exception e3) {
                    hasEntityCulling = false;
                }
            }
        }
        
        entityCullingChecked = true;
        return hasEntityCulling != null && hasEntityCulling;
    }
    
    /**
     * 检测是否有活跃的村庄袭击
     */
    private boolean detectActiveRaid(Level world, BlockPos playerPos) {
        try {
            if (world.isClientSide) {
                // 在客户端，检查附近是否有袭击相关的实体
                return !world.getEntitiesOfClass(net.minecraft.world.entity.monster.Pillager.class, 
                    new net.minecraft.world.phys.AABB(playerPos).inflate(64)).isEmpty() ||
                       !world.getEntitiesOfClass(net.minecraft.world.entity.monster.Vindicator.class, 
                    new net.minecraft.world.phys.AABB(playerPos).inflate(64)).isEmpty() ||
                       !world.getEntitiesOfClass(net.minecraft.world.entity.monster.Evoker.class, 
                    new net.minecraft.world.phys.AABB(playerPos).inflate(64)).isEmpty();
            } else {
                // 在服务端，简单检查附近是否有袭击相关的实体
                return !world.getEntitiesOfClass(net.minecraft.world.entity.monster.Pillager.class, 
                    new net.minecraft.world.phys.AABB(playerPos).inflate(64)).isEmpty() ||
                       !world.getEntitiesOfClass(net.minecraft.world.entity.monster.Vindicator.class, 
                    new net.minecraft.world.phys.AABB(playerPos).inflate(64)).isEmpty();
            }
        } catch (Exception e) {
            // 如果检测失败，默认返回false
            return false;
        }
    }
    
    /**
     * 综合判断实体是否应该渲染
     */
    private boolean shouldRenderEntity(Entity entity, BlockPos pos, double distance, SmartRenderingContext context, SpaceConnectivityAnalyzer analyzer) {
        // 1. 特殊实体类型检查（优先级最高）
        if (isAlwaysImportantEntity(entity, context)) {
            return true; // 重要实体总是渲染
        }
        
        // 2. 基础空间连通性检查
        if (!analyzer.isVisible(pos)) {
            return false;
        }
        
        // 3. 距离检查 - 基于环境和设置调整
        float maxDistance = calculateSmartRenderDistance(entity, context);
        if (distance > maxDistance) {
            return false;
        }
        
        // 4. 基于环境的额外检查（但如果有夜视效果则跳过）
        if (!context.hasNightVision && !context.isOutdoor && context.lightLevel < 7 && distance > maxDistance * 0.6f) {
            return false; // 室内暗环境下缩短距离
        }
        
        return true;
    }
    
    /**
     * 计算智能渲染距离
     */
    private float calculateSmartRenderDistance(Entity entity, SmartRenderingContext context) {
        float baseDistance = context.renderDistance * 0.5f; // 基础距离为渲染距离的一半
        
        // 根据亮度调整（但如果玩家有夜视效果则忽略光照因素）
        float brightnessMultiplier = 1.0f;
        if (!context.hasNightVision) {
            if (context.isOutdoor && context.isDay && context.lightLevel > 12) {
                brightnessMultiplier = 1.5f; // 明亮室外环境增加渲染距离
            } else if (!context.isOutdoor || context.lightLevel < 7) {
                brightnessMultiplier = 0.6f; // 室内或暗环境减少渲染距离
            }
        } else {
            // 有夜视效果时，视为明亮环境
            brightnessMultiplier = 1.3f;
            EnclosedSpaceRenderCulling.LOGGER.debug("玩家有夜视效果，忽略环境光照因素，使用明亮环境倍数: {}", brightnessMultiplier);
        }
        
        // 根据游戏亮度设置调整
        float gammaMultiplier = Math.max(0.5f, Math.min(1.5f, context.gamma));
        
        // 根据FOV调整
        float fovMultiplier = context.fov / 70.0f; // 以70度为基准
        
        // 优化模组加成
        float modMultiplier = 1.0f;
        if (context.hasOptiFine || context.hasEmbeddium) {
            modMultiplier = 1.2f; // 有优化模组时可以渲染更远
        }
        
        // 实体类型调整
        float entityMultiplier = getEntityRenderMultiplier(entity);
        
        // 袭击期间加成
        float raidMultiplier = 1.0f;
        if (context.isRaidActive && isRaidEntity(entity)) {
            raidMultiplier = 2.0f; // 袭击期间，袭击生物渲染距离加倍
        }
        
        float finalDistance = baseDistance * brightnessMultiplier * gammaMultiplier * fovMultiplier * modMultiplier * entityMultiplier * raidMultiplier;
        
        // 确保在合理范围内
        return Math.max(8.0f, Math.min(finalDistance, context.renderDistance));
    }
    
    /**
     * 获取实体渲染距离倍数
     */
    private float getEntityRenderMultiplier(Entity entity) {
        String entityType = entity.getType().getDescription().getString().toLowerCase();
        
        // 玩家和重要NPC
        if (entityType.contains("player") || entityType.contains("villager")) {
            return 1.5f;
        }
        
        // 敌对生物
        if (entityType.contains("zombie") || entityType.contains("skeleton") || 
            entityType.contains("creeper") || entityType.contains("spider")) {
            return 1.2f;
        }
        
        // 物品和经验球
        if (entityType.contains("item") || entityType.contains("experience")) {
            return 0.7f;
        }
        
        return 1.0f;
    }
    
    /**
     * 检查是否是重要实体（总是渲染）
     */
    private boolean isAlwaysImportantEntity(Entity entity, SmartRenderingContext context) {
        String entityType = entity.getType().getDescription().getString().toLowerCase();
        
        // 玩家总是重要
        if (entityType.contains("player")) {
            return true;
        }
        
        // 如果有活跃的村庄袭击，袭击相关生物总是重要
        if (context.isRaidActive && isRaidEntity(entity)) {
            EnclosedSpaceRenderCulling.LOGGER.debug("村庄袭击中，强制渲染袭击生物: {}", entityType);
            return true;
        }
        
        // 附近的敌对生物
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && entity.distanceTo(mc.player) < 8.0f && 
            (entityType.contains("zombie") || entityType.contains("skeleton") || 
             entityType.contains("creeper"))) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查是否是袭击相关实体
     */
    private boolean isRaidEntity(Entity entity) {
        // 检查是否是掠夺者
        if (entity instanceof net.minecraft.world.entity.monster.Pillager) {
            return true;
        }
        
        // 检查是否是卫道士
        if (entity instanceof net.minecraft.world.entity.monster.Vindicator) {
            return true;
        }
        
        // 检查是否是唤魔者
        if (entity instanceof net.minecraft.world.entity.monster.Evoker) {
            return true;
        }
        
        // 检查是否是劫掠兽
        if (entity instanceof net.minecraft.world.entity.monster.Ravager) {
            return true;
        }
        
        // 检查是否是女巫（袭击随从）
        if (entity instanceof net.minecraft.world.entity.monster.Witch) {
            return true;
        }
        
        // 也可以通过字符串检查
        String entityType = entity.getType().getDescription().getString().toLowerCase();
        return entityType.contains("pillager") || entityType.contains("vindicator") || 
               entityType.contains("evoker") || entityType.contains("ravager") || 
               entityType.contains("witch");
    }
    
    /**
     * 完全隐藏实体（包括装备等）
     */
    private void hideEntityCompletely(Entity entity) {
        if (!entity.isInvisible()) {
            entity.setInvisible(true);
            
            // 特殊处理掠夺者等有装备的实体
            if (entity instanceof net.minecraft.world.entity.monster.Pillager) {
                // 可以在这里添加特殊的装备隐藏逻辑
                // 或者使用更底层的渲染控制
            }
        }
    }
    
    /**
     * 完全显示实体
     */
    private void showEntityCompletely(Entity entity) {
        if (entity.isInvisible()) {
            entity.setInvisible(false);
        }
    }
    
    /**
     * 获取详细的剔除原因
     */
    private String getDetailedCullingReason(Entity entity, BlockPos pos, double distance, SmartRenderingContext context, SpaceConnectivityAnalyzer analyzer) {
        // 优先检查是否是重要实体
        if (isAlwaysImportantEntity(entity, context)) {
            if (context.isRaidActive && isRaidEntity(entity)) {
                return "袭击生物:强制显示";
            }
            return "重要实体:强制显示";
        }
        
        if (!analyzer.isVisible(pos)) {
            return "空间不连通";
        }
        
        float maxDistance = calculateSmartRenderDistance(entity, context);
        if (distance > maxDistance) {
            String lightInfo = context.hasNightVision ? "夜视" : "光照" + context.lightLevel;
            String raidInfo = context.isRaidActive && isRaidEntity(entity) ? "+袭击加成" : "";
            
            // 计算区块距离
            BlockPos playerPos = Minecraft.getInstance().player.blockPosition();
            int chunkDistance = Math.max(
                Math.abs(pos.getX() >> 4) - Math.abs(playerPos.getX() >> 4),
                Math.abs(pos.getZ() >> 4) - Math.abs(playerPos.getZ() >> 4)
            );
            
            return String.format("距离%.1f>%.1f格(%d区块,%s%s)", distance, maxDistance, chunkDistance, lightInfo, raidInfo);
        }
        
        if (!context.hasNightVision && !context.isOutdoor && context.lightLevel < 7 && distance > maxDistance * 0.6f) {
            return "室内暗环境距离限制";
        }
        
        return "综合剔除判断";
    }
}