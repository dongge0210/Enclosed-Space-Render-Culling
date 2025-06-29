package com.dongge0210.enclosedculling.mixin;

import com.dongge0210.enclosedculling.room.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 以机械动力等mod的BlockEntity为目标,可以按需扩展target
@Mixin(BlockEntity.class)
public class BlockEntityTickerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTileEntityTick(Level level, BlockPos pos, CallbackInfo ci) {
        // 只优化机械动力等高负载mod的BE（可根据需要扩展判断）
        BlockEntity self = (BlockEntity) (Object) this;
        String id = self.getType().toString();
        if (!id.contains("create") && !id.contains("mekanism") && !id.contains("thermal")) return;

        // 判断是否在玩家可见房间,不可见直接跳过tick
        var nearestPlayer = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 128, false);
        if (nearestPlayer == null) return; // 如果没有附近的玩家,跳过优化
        
        if (!RoomManager.isPositionVisible(level, pos, nearestPlayer.blockPosition())) {
            ci.cancel();
        }
    }
}