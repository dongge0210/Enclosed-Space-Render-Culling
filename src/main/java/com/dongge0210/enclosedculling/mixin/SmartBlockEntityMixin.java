package com.dongge0210.enclosedculling.mixin;

import com.dongge0210.enclosedculling.room.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(SmartBlockEntity.class)
public class SmartBlockEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onSmartTick(Level level, BlockPos pos, CallbackInfo ci) {
        // 获取最近玩家
        var player = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 128, false);
        if (player == null) return; // 没玩家直接不剔除
        UUID playerId = player.getUUID();
        // 判断是否在玩家可见空间,不可见则跳过tick
        if (!RoomManager.isPositionVisible(
                level,
                pos,
                player.blockPosition(),
                playerId
        )) {
            ci.cancel();
        }
    }
}