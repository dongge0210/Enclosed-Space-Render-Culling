package com.dongge0210.enclosedculling.mixin;

import com.dongge0210.enclosedculling.room.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

// 使用字符串目标，完全避免编译时加载Create类
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.SmartBlockEntity", remap = false)
public class SmartBlockEntityMixin {
    
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSmartTick(Level level, BlockPos pos, CallbackInfo ci) {
        // 运行时检查Create是否真的存在
        if (!com.dongge0210.enclosedculling.compat.CreateCompatibility.isCreateLoaded()) {
            return; // Create未加载，跳过处理
        }
        
        try {
            // 获取最近玩家
            var player = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 128, false);
            if (player == null) return; // 没玩家直接不剔除
            
            UUID playerId = player.getUUID();
            // 判断是否在玩家可见空间,不可见则跳过tick
            if (!RoomManager.isPositionVisible(level, pos, player.blockPosition(), playerId)) {
                ci.cancel();
            }
        } catch (Exception e) {
            // 静默处理异常，避免影响游戏运行
            com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling.LOGGER.debug(
                "SmartBlockEntity tick处理异常: {}", e.getMessage());
        }
    }
}