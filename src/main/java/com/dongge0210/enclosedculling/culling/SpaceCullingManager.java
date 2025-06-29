package com.dongge0210.enclosedculling.culling;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
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

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new SpaceCullingManager());
        EnclosedSpaceRenderCulling.LOGGER.info("SpaceCullingManager 已注册到事件总线。");
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Level world = mc.level;
        BlockPos playerPos = mc.player.blockPosition();

        // 只有玩家移动或世界切换时才刷新泛洪（大幅优化性能）
        if (analyzer == null || lastWorld != world || lastPlayerPos == null || !lastPlayerPos.equals(playerPos)) {
            analyzer = new SpaceConnectivityAnalyzer(world, MAX_STEPS);
            analyzer.floodFrom(playerPos);
            lastPlayerPos = playerPos;
            lastWorld = world;
        }

        // 剔除不可见空间的实体
        if (analyzer != null && mc.player != null) {
            for (Entity entity : world.getEntities(null, mc.player.getBoundingBox().inflate(CULL_RANGE))) {
                BlockPos pos = entity.blockPosition();
                if (!analyzer.isVisible(pos)) {
                    entity.setInvisible(true); // 强制不可见
                } else {
                    entity.setInvisible(false);
                }
            }
        }
        // 方块剔除需要事件或Mixin配合（如需直接跳过渲染,告诉我）
    }
}