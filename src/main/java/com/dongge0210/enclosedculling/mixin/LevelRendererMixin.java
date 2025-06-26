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
            BlockPos chunkPos = renderChunk.getOrigin();
            if (CullingRenderer.isPositionOccluded(mc.level, chunkPos, mc.player.position())) {
                cir.setReturnValue(false);
            }
        }
    }
}