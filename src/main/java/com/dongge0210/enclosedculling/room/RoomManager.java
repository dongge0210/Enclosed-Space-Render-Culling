package com.dongge0210.enclosedculling.room;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.*;

public class RoomManager {
    private static final int MAX_ROOM_SIZE = 2048; // 支持更大空间
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_BITS = CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE;
    // 空间ID到房间对象
    private static final Map<Integer, Room> roomIdMap = new ConcurrentHashMap<>();
    // 方块坐标到空间ID
    private static final Map<BlockPos, Integer> posToRoomId = new ConcurrentHashMap<>();
    private static final ExecutorService roomThreadPool = Executors.newFixedThreadPool(2);

    // 提供异步接口，提升大空间首次遍历速度
    public static Future<Room> findRoomAsync(Level world, BlockPos start) {
        return roomThreadPool.submit(() -> findRoom(world, start));
    }

    // 推荐用异步，老接口同步
    public static Room findRoom(Level world, BlockPos start) {
        Integer cachedId = posToRoomId.get(start);
        if (cachedId != null) {
            return roomIdMap.get(cachedId);
        }
        Room room = floodFillRoom(world, start);
        int roomId = room.hashCode();
        for (BlockPos pos : room.blocks) {
            posToRoomId.put(pos.immutable(), roomId);
        }
        roomIdMap.put(roomId, room);
        return room;
    }

    // 优化后的flood fill with BitSet
    private static Room floodFillRoom(Level world, BlockPos start) {
        Set<BlockPos> blocks = new HashSet<>();
        Set<BlockPos> doors = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        int roomSize = 0;
        boolean isClosed = true;

        while (!queue.isEmpty() && roomSize < MAX_ROOM_SIZE) {
            BlockPos pos = queue.poll();
            if (!blocks.add(pos)) continue;
            roomSize++;
            // 6方向遍历
            for (BlockPos dir : get6Directions(pos)) {
                if (!blocks.contains(dir)) {
                    BlockState state = world.getBlockState(dir);
                    if (isSolid(state, world, dir)) continue;
                    if (isDoor(world, dir)) {
                        doors.add(dir.immutable());
                    } else {
                        queue.add(dir);
                    }
                }
            }
        }
        if (roomSize >= MAX_ROOM_SIZE) isClosed = false;
        return new Room(blocks, doors, isClosed);
    }

    private static List<BlockPos> get6Directions(BlockPos pos) {
        return Arrays.asList(
                pos.above(), pos.below(),
                pos.north(), pos.south(),
                pos.east(), pos.west()
        );
    }

    // 判断门洞（支持自定义白名单）
    private static final Set<String> DOOR_BLOCKS = Set.of("minecraft:air", "minecraft:glass", "minecraft:open_door", "create:window");
    private static boolean isDoor(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) return false;
        String idStr = id.toString();
        if (!DOOR_BLOCKS.contains(idStr)) return false;
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
        roomIdMap.clear();
        posToRoomId.clear();
    }

    // 递归��可见，优先短路径
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