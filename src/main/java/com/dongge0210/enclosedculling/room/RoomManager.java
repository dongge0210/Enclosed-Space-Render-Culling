package com.dongge0210.enclosedculling.room;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class RoomManager {
    private static final int MAX_ROOM_SIZE = 512;
    private static final int MAX_DOOR_SIZE = 8;
    // 已识别房间缓存，避免重复遍历
    private static final Map<BlockPos, Room> roomCache = new HashMap<>();

    public static Room findRoom(Level world, BlockPos start) {
        if (roomCache.containsKey(start)) {
            return roomCache.get(start);
        }
        Room room = floodFillRoom(world, start);
        for (BlockPos pos : room.blocks) {
            roomCache.put(pos.immutable(), room);
        }
        return room;
    }

    // 广度优先找封闭空间+门
    private static Room floodFillRoom(Level world, BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> doors = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        int roomSize = 0;
        boolean isClosed = true;

        while (!queue.isEmpty() && roomSize < MAX_ROOM_SIZE) {
            BlockPos pos = queue.poll();
            if (!visited.add(pos)) continue;
            roomSize++;
            for (BlockPos dir : get6Directions(pos)) {
                if (!visited.contains(dir)) {
                    BlockState state = world.getBlockState(dir);
                    if (isSolid(state, world, dir)) continue;
                    // 判断是不是门口
                    if (isDoor(world, dir)) {
                        doors.add(dir.immutable());
                    } else {
                        queue.add(dir);
                    }
                }
            }
        }
        // 超大空间不算完整房间
        if (roomSize >= MAX_ROOM_SIZE) isClosed = false;
        return new Room(visited, doors, isClosed);
    }

    private static List<BlockPos> get6Directions(BlockPos pos) {
        return Arrays.asList(
                pos.above(), pos.below(),
                pos.north(), pos.south(),
                pos.east(), pos.west()
        );
    }

    // 判断方块是否为“门”（简单：空气但周围有非空气）
    private static boolean isDoor(Level world, BlockPos pos) {
        if (!world.getBlockState(pos).isAir()) return false;
        int solidSides = 0;
        for (BlockPos dir : get6Directions(pos)) {
            if (!world.getBlockState(dir).isAir()) solidSides++;
        }
        return solidSides >= 2 && solidSides <= 4;
    }

    private static boolean isSolid(BlockState state, Level world, BlockPos pos) {
        return !state.isAir() && state.isSolidRender(world, pos);
    }

    public static void clearCache() {
        roomCache.clear();
    }

    // 判断玩家是否能看到指定房间（递归门）
    public static boolean isRoomVisible(Level world, Room room, BlockPos playerPos, int depth) {
        if (depth > 8) return false;
        if (room.blocks.contains(playerPos)) return true;
        for (BlockPos door : room.doors) {
            Room next = findRoom(world, door);
            if (next != null && isRoomVisible(world, next, playerPos, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    // 给剔除器用，判断一个坐标是不是玩家可见房间
    public static boolean isPositionVisible(Level world, BlockPos pos, BlockPos playerPos) {
        Room room = findRoom(world, pos);
        return isRoomVisible(world, room, playerPos, 0);
    }

    public static class Room {
        public final Set<BlockPos> blocks;
        public final Set<BlockPos> doors;
        public final boolean isClosed;
        public Room(Set<BlockPos> blocks, Set<BlockPos> doors, boolean isClosed) {
            this.blocks = blocks;
            this.doors = doors;
            this.isClosed = isClosed;
        }
    }
}