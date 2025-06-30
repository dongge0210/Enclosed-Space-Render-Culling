package com.dongge0210.enclosedculling.mixin;

import com.dongge0210.enclosedculling.room.RoomManager;
import com.dongge0210.enclosedculling.debug.DebugManager;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 增强的实体渲染Mixin - 处理特殊场景
 * Enhanced Entity Render Mixin - Handles special scenarios
 */
@Mixin(EntityRenderDispatcher.class)
public class EnhancedEntityRenderMixin {

    /**
     * 增强的实体渲染判断 - 处理特殊实体场景
     */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void onEnhancedShouldRender(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        
        Player player = mc.player;
        Level level = mc.level;
        BlockPos playerPos = player.blockPosition();
        BlockPos entityPos = entity.blockPosition();
        String entityType = entity.getType().getDescription().getString();
        
        try {
            // 1. 处理小白射出的箭（不可拾起）- 自动清理
            if (entity instanceof Arrow) {
                Arrow arrow = (Arrow) entity;
                // 检查是否是不可拾起的箭
                if (arrow.pickup == AbstractArrow.Pickup.DISALLOWED) {
                    // 如果箭存在超过30秒且距离玩家较远，标记为应该被清理
                    if (arrow.tickCount > 600 && player.distanceTo(arrow) > 16.0f) {
                        DebugManager.trackEntityCulling(
                            arrow.getUUID().toString(),
                            "不可拾起的箭",
                            entityPos, playerPos, true,
                            "存在时间过长且距离较远，应该清理"
                        );
                        cir.setReturnValue(false);
                        return;
                    }
                }
            }
            
            // 2. 蜘蛛特殊处理 - 考虑其眼睛渲染
            if (entity instanceof Spider) {
                boolean shouldRender = handleSpiderRendering((Spider) entity, player, level, entityPos, playerPos);
                if (!shouldRender) {
                    DebugManager.trackEntityCulling(
                        entity.getUUID().toString(),
                        "蜘蛛",
                        entityPos, playerPos, true,
                        "蜘蛛被剔除但需特殊处理眼睛渲染"
                    );
                    cir.setReturnValue(false);
                    return;
                }
            }
            
            // 3. 幻翼特殊处理 - 考虑声音与视觉的一致性
            if (entity instanceof Phantom) {
                boolean shouldRender = handlePhantomRendering((Phantom) entity, player, level, entityPos, playerPos);
                if (!shouldRender) {
                    DebugManager.trackEntityCulling(
                        entity.getUUID().toString(),
                        "幻翼",
                        entityPos, playerPos, true,
                        "幻翼被剔除但声音可能仍然播放"
                    );
                    cir.setReturnValue(false);
                    return;
                }
            }
            
            // 4. 门开启时的房间内实体处理
            if (isDoorOpenScenario(level, playerPos, entityPos)) {
                boolean shouldRender = handleDoorOpenScenario(entity, player, level, entityPos, playerPos);
                if (shouldRender) {
                    DebugManager.trackEntityCulling(
                        entity.getUUID().toString(),
                        entityType,
                        entityPos, playerPos, false,
                        "门开启场景，强制渲染房间内实体"
                    );
                    cir.setReturnValue(true);
                    return;
                }
            }
            
            // 5. 着火实体的特殊处理 - 火焰渲染
            if (entity.isOnFire()) {
                boolean shouldRender = handleFireEntityRendering(entity, player, level, entityPos, playerPos);
                if (shouldRender) {
                    DebugManager.trackEntityCulling(
                        entity.getUUID().toString(),
                        entityType + "(着火)",
                        entityPos, playerPos, false,
                        "实体着火，需要渲染火焰效果"
                    );
                    cir.setReturnValue(true);
                    return;
                }
            }
            
            // 6. 常规的房间连通性检查
            if (!RoomManager.isPositionVisible(level, entityPos, playerPos)) {
                // 进行额外的特殊场景检查
                if (!performSpecialScenarioCheck(entity, player, level, entityPos, playerPos)) {
                    DebugManager.trackEntityCulling(
                        entity.getUUID().toString(),
                        entityType,
                        entityPos, playerPos, true,
                        "房间不连通且无特殊场景"
                    );
                    cir.setReturnValue(false);
                    return;
                }
            }
            
        } catch (Exception e) {
            // 出错时保守处理，不剔除
            DebugManager.setDebugInfo("entity_render_error", e.getMessage());
        }
    }
    
    /**
     * 处理蜘蛛渲染 - 特别考虑眼睛效果
     */
    private boolean handleSpiderRendering(Spider spider, Player player, Level level, 
                                        BlockPos entityPos, BlockPos playerPos) {
        double distance = player.distanceTo(spider);
        
        // 近距离必须渲染（包括眼睛）
        if (distance <= 8.0) {
            return true;
        }
        
        // 检查房间连通性
        if (!RoomManager.areRoomsConnectedByDoor(level, playerPos, entityPos)) {
            // 即使被剔除，也需要考虑眼睛的特殊渲染
            // 这里可以添加特殊的眼睛渲染逻辑
            return false;
        }
        
        // 考虑光照 - 蜘蛛在暗处更危险
        int lightLevel = level.getMaxLocalRawBrightness(entityPos);
        if (lightLevel < 7 && distance > 16.0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 处理幻翼渲染 - 考虑声音与视觉一致性
     */
    private boolean handlePhantomRendering(Phantom phantom, Player player, Level level,
                                         BlockPos entityPos, BlockPos playerPos) {
        double distance = player.distanceTo(phantom);
        
        // 幻翼通常在高空，需要特殊处理
        int heightDiff = Math.abs(entityPos.getY() - playerPos.getY());
        
        // 如果幻翼在很高的地方，但距离不太远，应该渲染
        if (heightDiff > 20 && distance < 32.0) {
            return true;
        }
        
        // 检查是否能看到天空（幻翼通常在天空中）
        if (level.canSeeSky(playerPos) && distance < 48.0) {
            return true;
        }
        
        // 夜晚时幻翼更活跃，渲染距离增加
        if (!level.isDay() && distance < 40.0) {
            return true;
        }
        
        return distance <= 24.0;
    }
    
    /**
     * 检查是否是门开启的场景
     */
    private boolean isDoorOpenScenario(Level level, BlockPos playerPos, BlockPos entityPos) {
        // 检查玩家和实体之间是否有开启的门
        return RoomManager.areRoomsConnectedByDoor(level, playerPos, entityPos);
    }
    
    /**
     * 处理门开启场景的实体渲染
     */
    private boolean handleDoorOpenScenario(Entity entity, Player player, Level level,
                                         BlockPos entityPos, BlockPos playerPos) {
        double distance = player.distanceTo(entity);
        
        // 如果门是开着的，且距离不太远，应该渲染房间内的实体
        if (distance <= 24.0) {
            return true;
        }
        
        // 对于重要实体（如村民、敌对生物），距离可以更远
        String entityType = entity.getType().getDescription().getString().toLowerCase();
        if (entityType.contains("villager") || entityType.contains("zombie") || 
            entityType.contains("skeleton") || entityType.contains("creeper")) {
            return distance <= 32.0;
        }
        
        return false;
    }
    
    /**
     * 处理着火实体的渲染 - 确保火焰效果正确显示
     */
    private boolean handleFireEntityRendering(Entity entity, Player player, Level level,
                                            BlockPos entityPos, BlockPos playerPos) {
        double distance = player.distanceTo(entity);
        
        // 着火的实体在一定距离内必须渲染，以显示火焰效果
        if (distance <= 16.0) {
            return true;
        }
        
        // 检查是否在同一个房间或连通的房间
        if (RoomManager.areRoomsConnectedByDoor(level, playerPos, entityPos)) {
            return distance <= 24.0;
        }
        
        return false;
    }
    
    /**
     * 执行特殊场景检查
     */
    private boolean performSpecialScenarioCheck(Entity entity, Player player, Level level,
                                               BlockPos entityPos, BlockPos playerPos) {
        double distance = player.distanceTo(entity);
        String entityType = entity.getType().getDescription().getString().toLowerCase();
        
        // 1. 极近距离强制渲染
        if (distance <= 4.0) {
            return true;
        }
        
        // 2. 重要实体类型的特殊处理
        if (entityType.contains("player") || entityType.contains("villager")) {
            return distance <= 32.0;
        }
        
        // 3. 敌对生物的特殊处理
        if (entityType.contains("zombie") || entityType.contains("skeleton") || 
            entityType.contains("creeper") || entityType.contains("enderman")) {
            return distance <= 24.0;
        }
        
        // 4. 物品实体的特殊处理
        if (entity instanceof ItemEntity) {
            return distance <= 12.0;
        }
        
        // 5. 环境光照因素
        int lightLevel = level.getMaxLocalRawBrightness(entityPos);
        if (lightLevel < 3 && distance > 8.0) {
            return false;
        }
        
        return false;
    }
}
