package com.dongge0210.enclosedculling.room;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class RoomManager {
    // --- 房间与连通群 ---
    private static final Map<BlockPos, Integer> posToRoomID = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> roomIdToGroupId = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<Integer>> groupIdToRoomIds = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> playerGroupCache = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<UUID>> groupIdToPlayers = new ConcurrentHashMap<>();

    // --- 区块缓存 ---
    private static final Map<Long, Map<UUID, Boolean>> chunkVisibilityCache = new ConcurrentHashMap<>();
    private static final Map<Long, Long> chunkCacheTick = new ConcurrentHashMap<>();
    private static final int CACHE_VALID_TICKS = 60;

    // --- 插件化策略（可插拔） ---
    public interface BlockTransparencyJudge {
        boolean isRoomTransparent(BlockState state);
    }
    private static BlockTransparencyJudge transparencyJudge = RoomManager::defaultJudge;

    // --- 配置热更新支持 ---
    private static Set<String> configDoorBlocks = new HashSet<>(List.of(
            "minecraft:glass", "minecraft:oak_door", "minecraft:air"
    ));
    public static void reloadConfig() throws IOException {
        Properties prop = new Properties();
        try (FileInputStream in = new FileInputStream("config/room_rules.properties")) {
            prop.load(in);
            configDoorBlocks = Set.of(prop.getProperty("door", "minecraft:glass,minecraft:oak_door,minecraft:air").split(","));
        }
    }
    public static void setTransparencyJudge(BlockTransparencyJudge judge) {
        transparencyJudge = judge;
    }

    // --- 主入口 ---
    public static void updatePlayerRoom(Level level, UUID playerId, BlockPos playerPos) {
        int roomId = findOrCreateRoom(level, playerPos);
        int groupId = roomIdToGroupId.getOrDefault(roomId, roomId);
        playerGroupCache.put(playerId, groupId);
        groupIdToPlayers.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(playerId);
    }
    public static boolean isPositionVisible(Level level, BlockPos target, BlockPos playerPos, UUID playerId) {
        // 开始性能计时
        com.dongge0210.enclosedculling.debug.DebugManager.startTimer("culling_check");
        
        // 调用脚本钩子
        com.dongge0210.enclosedculling.hotswap.ScriptManager.callBeforeCullingCheck(target, playerPos);
        
        boolean visible = true;
        String reason = "default_visible";
        
        try {
            // 首先检查脚本是否有自定义逻辑
            Boolean scriptResult = com.dongge0210.enclosedculling.hotswap.ScriptManager.callShouldCullBlock(
                target, level.getBlockState(target), playerPos);
            
            if (scriptResult != null) {
                visible = !scriptResult; // 脚本返回true表示应该剔除
                reason = scriptResult ? "script_culled" : "script_visible";
            } else {
                // 使用原有逻辑
                int roomIdTarget = findOrCreateRoom(level, target);
                int groupIdTarget = roomIdToGroupId.getOrDefault(roomIdTarget, roomIdTarget);
                int groupIdPlayer = playerGroupCache.getOrDefault(playerId, -1);
                
                if (groupIdTarget != groupIdPlayer) {
                    visible = false;
                    reason = "different_group";
                } else {
                    long chunkKey = chunkPosLong(target);
                    long nowTick = level.getGameTime();
                    Map<UUID, Boolean> chunkCache = chunkVisibilityCache.computeIfAbsent(chunkKey, k -> new ConcurrentHashMap<>());
                    long lastTick = chunkCacheTick.getOrDefault(chunkKey, 0L);
                    Boolean cached = chunkCache.get(playerId);
                    
                    if (cached != null && (nowTick - lastTick) < CACHE_VALID_TICKS) {
                        visible = cached;
                        reason = "cached_result";
                    } else {
                        visible = hasLineOfSight(level, playerPos, target);
                        chunkCache.put(playerId, visible);
                        chunkCacheTick.put(chunkKey, nowTick);
                        reason = visible ? "line_of_sight" : "no_line_of_sight";
                    }
                }
            }
            
            // 记录调试信息
            com.dongge0210.enclosedculling.debug.DebugManager.recordCullingResult(!visible);
            com.dongge0210.enclosedculling.debug.DebugManager.logCullingDetails(target, !visible, reason);
            
        } finally {
            // 结束性能计时
            com.dongge0210.enclosedculling.debug.DebugManager.endTimer("culling_check");
            
            // 调用脚本钩子
            com.dongge0210.enclosedculling.hotswap.ScriptManager.callAfterCullingCheck(target, !visible, reason);
        }
        
        return visible;
    }
    public static boolean isPositionVisible(Level level, BlockPos target, BlockPos playerPos) {
        return hasLineOfSight(level, playerPos, target);
    }

    // --- 房间算法 ---
    private static int findOrCreateRoom(Level level, BlockPos pos) {
        if (posToRoomID.containsKey(pos)) {
            return posToRoomID.get(pos);
        }
        int roomId = Objects.hash(pos.getX(), pos.getY(), pos.getZ(), System.nanoTime());
        Set<BlockPos> roomBlocks = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos);
        roomBlocks.add(pos);

        final int MAX_ROOM_SIZE = 4096;
        while (!queue.isEmpty() && roomBlocks.size() < MAX_ROOM_SIZE) {
            BlockPos curr = queue.poll();
            for (BlockPos dir : getCardinalDirections()) {
                BlockPos next = curr.offset(dir.getX(), dir.getY(), dir.getZ());
                if (roomBlocks.contains(next)) continue;
                BlockState state = level.getBlockState(next);
                if (transparencyJudge.isRoomTransparent(state)) {
                    queue.add(next);
                    roomBlocks.add(next);
                }
            }
        }
        for (BlockPos p : roomBlocks) {
            posToRoomID.put(p, roomId);
        }
        Set<Integer> neighbourRooms = new HashSet<>();
        for (BlockPos p : roomBlocks) {
            for (BlockPos dir : getCardinalDirections()) {
                BlockPos adj = p.offset(dir.getX(), dir.getY(), dir.getZ());
                if (posToRoomID.containsKey(adj)) {
                    int nearRoom = posToRoomID.get(adj);
                    if (nearRoom != roomId) neighbourRooms.add(nearRoom);
                }
            }
        }
        int groupId = roomId;
        for (int near : neighbourRooms) {
            int nearGroup = roomIdToGroupId.getOrDefault(near, near);
            groupId = Math.min(groupId, nearGroup);
        }
        roomIdToGroupId.put(roomId, groupId);
        groupIdToRoomIds.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(roomId);
        for (int near : neighbourRooms) {
            int oldGroup = roomIdToGroupId.getOrDefault(near, near);
            if (oldGroup != groupId) {
                if (groupIdToRoomIds.containsKey(oldGroup)) {
                    groupIdToRoomIds.get(groupId).addAll(groupIdToRoomIds.get(oldGroup));
                    groupIdToRoomIds.remove(oldGroup);
                }
                for (int r : groupIdToRoomIds.getOrDefault(groupId, new HashSet<>())) {
                    roomIdToGroupId.put(r, groupId);
                }
            }
        }
        return roomId;
    }

    private static boolean defaultJudge(BlockState state) {
        String id = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
        return configDoorBlocks.contains(id) || state.isAir() || !state.canOcclude();
    }

    private static List<BlockPos> getCardinalDirections() {
        return List.of(
                new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
                new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
                new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
        );
    }

    // --- 视线判定 ---
    public static boolean hasLineOfSight(Level level, BlockPos from, BlockPos to) {
        for (BlockPos pos : bresenham3D(from, to)) {
            if (!isTransparent(level.getBlockState(pos))) {
                return false;
            }
        }
        return true;
    }
    public static List<BlockPos> bresenham3D(BlockPos from, BlockPos to) {
        List<BlockPos> result = new ArrayList<>();
        int x1 = from.getX(), y1 = from.getY(), z1 = from.getZ();
        int x2 = to.getX(), y2 = to.getY(), z2 = to.getZ();

        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1), dz = Math.abs(z2 - z1);
        int xs = x2 > x1 ? 1 : -1;
        int ys = y2 > y1 ? 1 : -1;
        int zs = z2 > z1 ? 1 : -1;

        int px = x1, py = y1, pz = z1;
        int dx2 = dx << 1, dy2 = dy << 1, dz2 = dz << 1;
        int err1, err2;
        if (dx >= dy && dx >= dz) {
            err1 = dy2 - dx;
            err2 = dz2 - dx;
            for (int i = 0; i < dx; i++) {
                result.add(new BlockPos(px, py, pz));
                if (err1 > 0) { py += ys; err1 -= dx2; }
                if (err2 > 0) { pz += zs; err2 -= dx2; }
                err1 += dy2;
                err2 += dz2;
                px += xs;
            }
        } else if (dy >= dx && dy >= dz) {
            err1 = dx2 - dy;
            err2 = dz2 - dy;
            for (int i = 0; i < dy; i++) {
                result.add(new BlockPos(px, py, pz));
                if (err1 > 0) { px += xs; err1 -= dy2; }
                if (err2 > 0) { pz += zs; err2 -= dy2; }
                err1 += dx2;
                err2 += dz2;
                py += ys;
            }
        } else {
            err1 = dy2 - dz;
            err2 = dx2 - dz;
            for (int i = 0; i < dz; i++) {
                result.add(new BlockPos(px, py, pz));
                if (err1 > 0) { py += ys; err1 -= dz2; }
                if (err2 > 0) { px += xs; err2 -= dz2; }
                err1 += dy2;
                err2 += dx2;
                pz += zs;
            }
        }
        result.add(new BlockPos(x2, y2, z2));
        return result;
    }
    public static boolean isTransparent(BlockState state) {
        return transparencyJudge.isRoomTransparent(state);
    }

    private static long chunkPosLong(BlockPos pos) {
        return (((long)pos.getX() >> 4) & 0xFFFFFFFFL) | ((((long)pos.getZ() >> 4) & 0xFFFFFFFFL) << 32);
    }

    // --- 开发/调试辅助接口 ---
    public static int getRoomIdForPos(Level level, BlockPos pos) {
        return posToRoomID.getOrDefault(pos, -1);
    }
    public static int getGroupIdForRoom(int roomId) {
        return roomIdToGroupId.getOrDefault(roomId, -1);
    }
    public static int getRoomSize(int roomId) {
        int count = 0;
        for (int v : posToRoomID.values()) if (v == roomId) count++;
        return count;
    }
    // 在世界里给指定房间高亮（粒子特效）
    public static void debugHighlightRoom(Level level, int roomId, ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (Map.Entry<BlockPos, Integer> e : posToRoomID.entrySet()) {
            if (e.getValue() == roomId) {
                serverLevel.sendParticles(player, ParticleTypes.END_ROD, true,
                        e.getKey().getX() + 0.5, e.getKey().getY() + 0.5, e.getKey().getZ() + 0.5,
                        1, 0, 0, 0, 0.01);
            }
        }
    }
    // 简单统计接口
    public static String getRoomStats() {
        int roomCount = new HashSet<>(posToRoomID.values()).size();
        int groupCount = new HashSet<>(roomIdToGroupId.values()).size();
        return "总房间数:" + roomCount + " 总连通群数:" + groupCount;
    }

    // --- 缓存清理 ---
    public static void clearAll() {
        posToRoomID.clear();
        roomIdToGroupId.clear();
        groupIdToRoomIds.clear();
        playerGroupCache.clear();
        groupIdToPlayers.clear();
        chunkVisibilityCache.clear();
        chunkCacheTick.clear();
    }
    public static void clearChunkCache() {
        chunkVisibilityCache.clear();
        chunkCacheTick.clear();
    }
}