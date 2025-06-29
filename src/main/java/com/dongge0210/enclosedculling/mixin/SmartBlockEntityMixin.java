package com.dongge0210.enclosedculling.mixin;

import com.dongge0210.enclosedculling.room.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmartBlockEntity.class)
public class SmartBlockEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onSmartTick(Level level, BlockPos pos, CallbackInfo ci) {
        // 判断是否在玩家可见空间,不可见则跳过tick
        if (!RoomManager.isPositionVisible(
                level,
                pos,
                level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 128, false).blockPosition()
        )) {
            ci.cancel();
        }
    }
}