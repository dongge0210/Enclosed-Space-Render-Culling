package com.dongge0210.enclosedculling.room;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class RoomManager {
    // --- 房间与连通群 ---
    // 每个方块属于哪个房间
    private static final Map<BlockPos, Integer> posToRoomID = new HashMap<>();
    // 每个房间属于哪个连通群（groupId=最小roomId）
    private static final Map<Integer, Integer> roomIdToGroupId = new HashMap<>();
    // 每个连通群包含哪些房间
    private static final Map<Integer, Set<Integer>> groupIdToRoomIds = new HashMap<>();
    // 玩家属于哪个连通群（可见区域）
    private static final Map<UUID, Integer> playerGroupCache = new HashMap<>();
    // 反查，哪个连通群有哪些玩家
    private static final Map<Integer, Set<UUID>> groupIdToPlayers = new HashMap<>();

    // --- 区块缓存（区块为单位缓存可见性） ---
    // <区块long坐标, <玩家UUID, 可见性>>
    private static final Map<Long, Map<UUID, Boolean>> chunkVisibilityCache = new HashMap<>();
    // <区块long坐标, 上次刷新tick>
    private static final Map<Long, Long> chunkCacheTick = new HashMap<>();
    // 缓存有效tick数
    private static final int CACHE_VALID_TICKS = 60;

    // --- 房间判定与可见性主入口 ---
    /**
     * 玩家每次移动时调用，更新玩家所在房间和连通群
     */
    public static void updatePlayerRoom(Level level, UUID playerId, BlockPos playerPos) {
        int roomId = findOrCreateRoom(level, playerPos);
        int groupId = roomIdToGroupId.getOrDefault(roomId, roomId);
        playerGroupCache.put(playerId, groupId);
        groupIdToPlayers.computeIfAbsent(groupId, k -> new HashSet<>()).add(playerId);
    }

    /**
     * 判断目标位置是否在玩家可见区域（带区块缓存和视线判定）
     */
    public static boolean isPositionVisible(Level level, BlockPos target, BlockPos playerPos, UUID playerId) {
        int roomIdTarget = findOrCreateRoom(level, target);
        int groupIdTarget = roomIdToGroupId.getOrDefault(roomIdTarget, roomIdTarget);
        int groupIdPlayer = playerGroupCache.getOrDefault(playerId, -1);

        // 不同连通群直接不可见
        if (groupIdTarget != groupIdPlayer) return false;

        // 区块缓存
        long chunkKey = chunkPosLong(target);
        long nowTick = level.getGameTime();
        Map<UUID, Boolean> chunkCache = chunkVisibilityCache.computeIfAbsent(chunkKey, k -> new HashMap<>());
        long lastTick = chunkCacheTick.getOrDefault(chunkKey, 0L);
        Boolean cached = chunkCache.get(playerId);

        if (cached != null && (nowTick - lastTick) < CACHE_VALID_TICKS) {
            return cached;
        }

        // 需要重新判定：带视线算法
        boolean visible = hasLineOfSight(level, playerPos, target);
        chunkCache.put(playerId, visible);
        chunkCacheTick.put(chunkKey, nowTick);
        return visible;
    }

    /**
     * 重载方法：简化版可见性判断（无UUID参数）
     * 直接使用视线算法，不进行房间分组检查
     */
    public static boolean isPositionVisible(Level level, BlockPos target, BlockPos playerPos) {
        // 简化版本，直接进行视线检查
        return hasLineOfSight(level, playerPos, target);
    }

    // --- 房间分层与连通性算法 ---
    /**
     * 找或新建目标位置的房间并分配连通群
     */
    private static int findOrCreateRoom(Level level, BlockPos pos) {
        if (posToRoomID.containsKey(pos)) {
            return posToRoomID.get(pos);
        }
        // flood fill找到所有连通方块，分配roomId
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
                if (isRoomTransparent(state)) {
                    queue.add(next);
                    roomBlocks.add(next);
                }
            }
        }
        // 标记roomId
        for (BlockPos p : roomBlocks) {
            posToRoomID.put(p, roomId);
        }
        // 检查附近是否有其他房间可以合并为连通群（通过门/玻璃等）
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
        // 合并连通群
        int groupId = roomId;
        for (int near : neighbourRooms) {
            int nearGroup = roomIdToGroupId.getOrDefault(near, near);
            groupId = Math.min(groupId, nearGroup);
        }
        roomIdToGroupId.put(roomId, groupId);
        groupIdToRoomIds.computeIfAbsent(groupId, k -> new HashSet<>()).add(roomId);
        // 合并邻居房间到同一群
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

    /**
     * 判断是否为“房间内部”——空气与非遮挡为连通（你可以加玻璃、门等）
     */
    private static boolean isRoomTransparent(BlockState state) {
        return state.isAir() || !state.canOcclude();
        // 你可以加更多白名单，比如玻璃、门、trapdoor等
    }

    /**
     * 六方向遍历
     */
    private static List<BlockPos> getCardinalDirections() {
        return List.of(
                new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
                new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
                new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
        );
    }

    // --- 视线判定相关 ---
    /**
     * 视线判定，from到to，中间有不透明方块就不可见
     */
    public static boolean hasLineOfSight(Level level, BlockPos from, BlockPos to) {
        for (BlockPos pos : bresenham3D(from, to)) {
            if (!isTransparent(level.getBlockState(pos))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 3D Bresenham直线算法
     */
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

    /**
     * 判定透明（空气、非遮挡、你可以扩展玻璃、trapdoor等）
     */
    public static boolean isTransparent(BlockState state) {
        return state.isAir() || !state.canOcclude();
    }

    // --- 区块相关 ---
    private static long chunkPosLong(BlockPos pos) {
        return (((long)pos.getX() >> 4) & 0xFFFFFFFFL) | ((((long)pos.getZ() >> 4) & 0xFFFFFFFFL) << 32);
    }

    // --- 缓存清理 ---
    /**
     * 清理所有缓存（比如世界重载、区块卸载时用）
     */
    public static void clearAll() {
        posToRoomID.clear();
        roomIdToGroupId.clear();
        groupIdToRoomIds.clear();
        playerGroupCache.clear();
        groupIdToPlayers.clear();
        chunkVisibilityCache.clear();
        chunkCacheTick.clear();
    }
    /**
     * 只清理区块可见性缓存
     */
    public static void clearChunkCache() {
        chunkVisibilityCache.clear();
        chunkCacheTick.clear();
    }
}