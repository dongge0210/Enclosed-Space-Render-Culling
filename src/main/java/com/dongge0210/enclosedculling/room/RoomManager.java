package com.dongge0210.enclosedculling.room;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    // 添加日志频率控制
    private static final Map<String, Long> lastLogTime = new ConcurrentHashMap<>();
    private static final int LOG_THROTTLE_INTERVAL = 5000; // 5秒内最多输出一次相同类型的日志

    public static boolean isPositionVisible(Level level, BlockPos target, BlockPos playerPos, UUID playerId) {
        // 性能优化：减少重复计算
        com.dongge0210.enclosedculling.debug.DebugManager.startTimer("culling_check");
        
        boolean visible = true;
        String reason = "default_visible";
        
        try {
            // 脚本钩子检查
            Boolean scriptResult = com.dongge0210.enclosedculling.hotswap.ScriptManager.callShouldCullBlock(
                target, level.getBlockState(target), playerPos);
            
            if (scriptResult != null) {
                visible = !scriptResult;
                reason = scriptResult ? "script_culled" : "script_visible";
            } else {
                // 改进的房间连通性检测
                int roomIdTarget = findOrCreateRoom(level, target);
                int groupIdTarget = roomIdToGroupId.getOrDefault(roomIdTarget, roomIdTarget);
                int groupIdPlayer = playerGroupCache.getOrDefault(playerId, -1);
                
                if (groupIdTarget != groupIdPlayer) {
                    // 优化门连接检查
                    boolean connectedByDoor = areRoomsConnectedByDoor(level, playerPos, target);
                    visible = connectedByDoor;
                    reason = connectedByDoor ? "door_connected" : "different_group";
                } else {
                    // 使用缓存的视线检查结果
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
            
            // 减少调试记录频率
            if (shouldLogDebugInfo("culling_result")) {
                com.dongge0210.enclosedculling.debug.DebugManager.recordCullingResult(!visible);
                com.dongge0210.enclosedculling.debug.DebugManager.logCullingDetails(target, !visible, reason);
            }
            
        } catch (Exception e) {
            // 异常时默认可见，避免过度剔除
            visible = true;
            reason = "exception_fallback";
            
            if (shouldLogDebugInfo("culling_error")) {
                com.dongge0210.enclosedculling.debug.DebugManager.logDebug("剔除检查异常: {}", e.getMessage());
            }
        } finally {
            com.dongge0210.enclosedculling.debug.DebugManager.endTimer("culling_check");
            com.dongge0210.enclosedculling.hotswap.ScriptManager.callAfterCullingCheck(target, !visible, reason);
        }
        
        return visible;
    }
    public static boolean isPositionVisible(Level level, BlockPos target, BlockPos playerPos) {
        return hasLineOfSight(level, playerPos, target);
    }

    /**
     * 检查是否应该记录调试信息
     */
    private static boolean shouldLogDebugInfo(String logType) {
        try {
            if (!com.dongge0210.enclosedculling.config.ModConfig.COMMON.enableDebugMode.get()) {
                return false;
            }
            
            long currentTime = System.currentTimeMillis();
            Long lastTime = lastLogTime.get(logType);
            
            if (lastTime != null && (currentTime - lastTime) < LOG_THROTTLE_INTERVAL) {
                return false;
            }
            
            lastLogTime.put(logType, currentTime);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // --- 房间算法 ---
    private static int findOrCreateRoom(Level level, BlockPos pos) {
        if (posToRoomID.containsKey(pos)) {
            return posToRoomID.get(pos);
        }
        
        // 使用稳定的分区哈希算法生成房间ID
        // 将坐标按网格划分，确保相邻位置倾向于得到相同的基础ID
        int gridX = Math.floorDiv(pos.getX(), 16); // 16x16网格
        int gridY = Math.floorDiv(pos.getY(), 8);  // 8高度层
        int gridZ = Math.floorDiv(pos.getZ(), 16);
        String dimKey = level.dimension().toString();
        
        // 生成稳定的基础ID
        int baseRoomId = Objects.hash(gridX, gridY, gridZ, dimKey);
        
        // 确保ID为正数
        if (baseRoomId < 0) baseRoomId = -baseRoomId;
        if (baseRoomId == 0) baseRoomId = 1;
        
        int roomId = baseRoomId;
        
        // 如果这个房间ID已经存在，检查是否真的是同一个房间区域
        if (roomIdToGroupId.containsKey(roomId)) {
            boolean foundExistingRoom = false;
            // 检查是否在同一个网格区域内
            for (Map.Entry<BlockPos, Integer> entry : posToRoomID.entrySet()) {
                if (entry.getValue().equals(roomId)) {
                    BlockPos existingPos = entry.getKey();
                    int existingGridX = Math.floorDiv(existingPos.getX(), 16);
                    int existingGridY = Math.floorDiv(existingPos.getY(), 8);
                    int existingGridZ = Math.floorDiv(existingPos.getZ(), 16);
                    
                    // 如果在相同网格内，认为是同一房间
                    if (existingGridX == gridX && existingGridY == gridY && existingGridZ == gridZ) {
                        foundExistingRoom = true;
                        break;
                    }
                }
            }
            
            if (!foundExistingRoom) {
                // 使用确定性的方式生成新ID，避免随机性
                roomId = Objects.hash(baseRoomId, pos.getX(), pos.getZ());
                if (roomId < 0) roomId = -roomId;
                if (roomId == 0) roomId = baseRoomId + 1;
                
                // 如果还有冲突，继续增量查找
                int increment = 1;
                while (roomIdToGroupId.containsKey(roomId) && increment < 1000) {
                    roomId = baseRoomId + increment;
                    increment++;
                }
            }
        }
        
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
        
        // 铁轨应该被视为透明（不阻挡房间连通）
        if (id.contains("rail")) {
            return true;
        }
        
        // 半高方块（如楼梯、台阶）应该被视为部分透明
        if (id.contains("slab") || id.contains("stair")) {
            return true;
        }
        
        // 栅栏和栅栏门
        if (id.contains("fence") || id.contains("gate")) {
            return true;
        }
        
        // 原有逻辑
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
    
    /**
     * 获取指定位置的房间ID
     */
    public static Integer getRoomIdAt(Level level, BlockPos pos) {
        try {
            return findOrCreateRoom(level, pos);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取指定位置的房间组ID
     */
    public static Integer getGroupIdAt(Level level, BlockPos pos) {
        try {
            int roomId = findOrCreateRoom(level, pos);
            return roomIdToGroupId.getOrDefault(roomId, roomId);
        } catch (Exception e) {
            return null;
        }
    }
    
    // 简单统计接口
    public static String getRoomStats() {
        int roomCount = new HashSet<>(posToRoomID.values()).size();
        int groupCount = new HashSet<>(roomIdToGroupId.values()).size();
        int totalPositions = posToRoomID.size();
        int cacheSize = chunkVisibilityCache.size();
        
        return String.format("房间总数: %d\n连通群数: %d\n已分析位置: %d\n缓存区块: %d", 
                roomCount, groupCount, totalPositions, cacheSize);
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
    
    // --- 房间ID稳定性管理 ---
    private static final Map<String, Integer> playerLastRoomId = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastRoomCheckTime = new ConcurrentHashMap<>();
    private static final Map<String, BlockPos> playerLastPosition = new ConcurrentHashMap<>();
    private static final long ROOM_CHECK_COOLDOWN = 3000; // 3秒冷却时间，减少频繁变化
    
    /**
     * 获取指定位置的房间ID（带稳定性检查）
     */
    public static Integer getRoomIdAtStable(Level level, BlockPos pos, String playerId) {
        String key = playerId + "_" + level.dimension().toString();
        long currentTime = System.currentTimeMillis();
        Long lastCheck = lastRoomCheckTime.get(key);
        BlockPos lastPos = playerLastPosition.get(key);
        
        // 如果距离上次检查时间很短，或者玩家位置变化很小，返回缓存的房间ID
        if (lastCheck != null && (currentTime - lastCheck) < ROOM_CHECK_COOLDOWN) {
            Integer cachedId = playerLastRoomId.get(key);
            if (cachedId != null) {
                // 如果玩家位置变化很小（小于4格），直接返回缓存
                if (lastPos != null && lastPos.distSqr(pos) < 16) {
                    return cachedId;
                }
                
                // 验证缓存的房间ID是否仍然有效
                Integer currentId = posToRoomID.get(pos);
                if (currentId != null && currentId.equals(cachedId)) {
                    playerLastPosition.put(key, pos);
                    return cachedId;
                }
            }
        }
        
        // 获取新的房间ID
        Integer roomId = getRoomIdAt(level, pos);
        if (roomId != null) {
            playerLastRoomId.put(key, roomId);
            lastRoomCheckTime.put(key, currentTime);
            playerLastPosition.put(key, pos);
        }
        
        return roomId;
    }
    
    /**
     * 检查两个房间是否通过门连通
     */
    public static boolean areRoomsConnectedByDoor(Level level, BlockPos pos1, BlockPos pos2) {
        Integer room1 = getRoomIdAt(level, pos1);
        Integer room2 = getRoomIdAt(level, pos2);
        
        if (room1 == null || room2 == null || room1.equals(room2)) {
            return true; // 同一个房间或无法判断，认为连通
        }
        
        // 检查两个房间之间的路径
        Vec3 start = pos1.getCenter();
        Vec3 end = pos2.getCenter();
        double distance = start.distanceTo(end);
        
        if (distance > 32.0) {
            return false; // 距离太远，不考虑门连接
        }
        
        Vec3 direction = end.subtract(start).normalize();
        boolean foundDoor = false;
        
        // 沿路径检查是否有门
        for (double d = 1.0; d < distance; d += 0.5) {
            Vec3 checkPos = start.add(direction.scale(d));
            BlockPos blockPos = BlockPos.containing(checkPos);
            BlockState state = level.getBlockState(blockPos);
            
            String blockName = state.getBlock().getDescriptionId();
            if (blockName.contains("door") || blockName.contains("gate") || 
                blockName.contains("fence_gate") || blockName.contains("trapdoor")) {
                
                // 检查门是否开启
                if (isDoorOpen(state)) {
                    foundDoor = true;
                    break;
                }
            }
        }
        
        return foundDoor;
    }
    
    /**
     * 检查门是否开启
     */
    private static boolean isDoorOpen(BlockState state) {
        // 检查门的开启状态
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)) {
            return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN);
        }
        // 对于栅栏门
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)) {
            return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN);
        }
        // 默认认为是开启的（对于不确定的方块）
        return true;
    }
    
    /**
     * 改进的可见性判断，考虑门的连通性
     */
    public static boolean isPositionVisibleWithDoors(Level level, BlockPos target, Vec3 playerPos) {
        BlockPos playerBlockPos = BlockPos.containing(playerPos);
        
        // 首先检查基本的房间连通性
        boolean basicVisible = isPositionVisible(level, target, playerBlockPos);
        
        if (basicVisible) {
            return true; // 如果基本判断认为可见，直接返回true
        }
        
        // 如果基本判断认为不可见，检查是否有门连接
        return areRoomsConnectedByDoor(level, playerBlockPos, target);
    }
    
    /**
     * 获取两个房间之间的连通性状态
     */
    public static String getRoomConnectivityStatus(Level level, BlockPos pos1, BlockPos pos2) {
        Integer room1 = getRoomIdAt(level, pos1);
        Integer room2 = getRoomIdAt(level, pos2);
        
        if (room1 == null || room2 == null) {
            return "未知";
        }
        if (room1.equals(room2)) {
            return "同一房间";
        }
        return areRoomsConnectedByDoor(level, pos1, pos2) ? "通过门连通" : "不连通";
    }
}