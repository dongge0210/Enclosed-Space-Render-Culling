package com.dongge0210.enclosedculling.mixin;

import com.dongge0210.enclosedculling.client.CullingRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "shouldShowChunk", at = @At("HEAD"), cancellable = true)
    private void onShouldShowChunk(ChunkRenderDispatcher.RenderChunk renderChunk, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            try {
                // 检查配置是否启用剔除
                if (!com.dongge0210.enclosedculling.config.ModConfig.COMMON.enableCulling.get()) {
                    return;
                }
                
                BlockPos chunkPos = renderChunk.getOrigin();
                if (CullingRenderer.isPositionOccluded(mc.level, chunkPos, mc.player.position())) {
                    cir.setReturnValue(false);
                    // 调试信息：成功剔除了一个区块
                    com.dongge0210.enclosedculling.debug.DebugManager.setDebugInfo("last_chunk_culled", chunkPos.toShortString());
                }
            } catch (Exception e) {
                // 配置读取失败，跳过剔除
                com.dongge0210.enclosedculling.debug.DebugManager.setDebugInfo("chunk_cull_error", e.getMessage());
            }
        }
    }
}