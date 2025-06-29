package com.dongge0210.enclosedculling.culling;

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
    }

    private boolean isAirOrTransparent(BlockPos pos) {
    // 只穿透空气和可替换方块（比如火把、花等）
    return world.getBlockState(pos).isAir() || world.getBlockState(pos).canBeReplaced();
    }

    public boolean isVisible(BlockPos pos) {
        return visibleSpaces.contains(pos);
    }
}