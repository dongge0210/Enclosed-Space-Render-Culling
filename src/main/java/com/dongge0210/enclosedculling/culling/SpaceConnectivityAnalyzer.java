package com.dongge0210.enclosedculling.culling;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.*;

public class SpaceConnectivityAnalyzer {
    public final Level world;
    private final Set<BlockPos> visibleSpaces = new HashSet<>();
    private final int maxStep;

    public SpaceConnectivityAnalyzer(Level world, int maxStep) {
        this.world = world;
        this.maxStep = maxStep;
    }

    // 泛洪填充,标记所有可见空间
    public void floodFrom(BlockPos start) {
        visibleSpaces.clear();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visibleSpaces.add(start);

        int[] dx = {1, -1, 0, 0, 0, 0};
        int[] dy = {0, 0, 1, -1, 0, 0};
        int[] dz = {0, 0, 0, 0, 1, -1};

        int steps = 0;
        while (!queue.isEmpty() && steps++ < maxStep) {
            BlockPos pos = queue.poll();
            for (int d = 0; d < 6; d++) {
                BlockPos next = pos.offset(dx[d], dy[d], dz[d]);
                if (!visibleSpaces.contains(next) && isAirOrTransparent(next)) {
                    visibleSpaces.add(next);
                    queue.add(next);
                }
            }
        }
        
        // 记录分析结果
        EnclosedSpaceRenderCulling.LOGGER.debug("Space connectivity analysis completed: {} visible positions analyzed in {} steps", 
            visibleSpaces.size(), steps);
    }

    private boolean isAirOrTransparent(BlockPos pos) {
        var state = world.getBlockState(pos);
        
        // 空气方块
        if (state.isAir()) {
            return true;
        }
        
        // 可替换方块（如火把、花、草等）
        if (state.canBeReplaced()) {
            return true;
        }
        
        // 透明方块（如玻璃）
        if (!state.canOcclude()) {
            return true;
        }
        
        // 检查特定的透明方块类型
        String blockId = state.getBlock().getDescriptionId();
        if (blockId.contains("glass") || 
            blockId.contains("fence") || 
            blockId.contains("door") ||
            blockId.contains("gate") ||
            blockId.contains("trapdoor") ||
            blockId.contains("bars") ||
            blockId.contains("pane")) {
            return true;
        }
        
        // 液体也应该视为可通过
        if (state.getFluidState().isEmpty() == false) {
            return true;
        }
        
        return false;
    }

    public boolean isVisible(BlockPos pos) {
        return visibleSpaces.contains(pos);
    }
    
    // 调试方法
    public int getVisibleSpaceCount() {
        return visibleSpaces.size();
    }
    
    public boolean hasAnalyzedAnySpaces() {
        return !visibleSpaces.isEmpty();
    }
}