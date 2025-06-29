package com.dongge0210.enclosedculling.mixin;

import com.dongge0210.enclosedculling.room.RoomManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityTickMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onEntityTick(CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        Level level = self.level();
        if (level.isClientSide) return; // 只在服务端优化tick
        // 这里可以加白名单,比如只针对怪物,或者排除玩家
        if (!RoomManager.isPositionVisible(level, self.blockPosition(),
                level.getNearestPlayer(self, 128).blockPosition())) {
            ci.cancel();
        }
    }
}