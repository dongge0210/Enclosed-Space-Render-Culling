package com.dongge0210.enclosedculling.client.gpu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批渲染优化器 - 将相似对象合并到单个渲染调用
 * GPU优化: 减少渲染状态切换和draw call数量
 */
public class BatchRenderer {
    
    // 批渲染数据结构
    private static final Map<BatchKey, RenderBatch> renderBatches = new ConcurrentHashMap<>();
    private static final Map<BlockState, List<BlockPos>> blockBatches = new ConcurrentHashMap<>();
    
    // 批渲染配置
    private static final int MAX_BATCH_SIZE = 1024;  // 每批最大对象数
    private static final int MIN_BATCH_SIZE = 4;     // 最小批大小
    private static final float BATCH_DISTANCE_THRESHOLD = 32.0f; // 批距离阈值
    
    /**
     * 批渲染键 - 用于分组相似的渲染对象
     */
    public static class BatchKey {
        private final String materialId;
        private final LODManager.LODLevel lodLevel;
        private final int chunkX, chunkZ; // 按区块分组
        
        public BatchKey(String materialId, LODManager.LODLevel lodLevel, int chunkX, int chunkZ) {
            this.materialId = materialId;
            this.lodLevel = lodLevel;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof BatchKey)) return false;
            BatchKey other = (BatchKey) obj;
            return Objects.equals(materialId, other.materialId) &&
                   lodLevel == other.lodLevel &&
                   chunkX == other.chunkX &&
                   chunkZ == other.chunkZ;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(materialId, lodLevel, chunkX, chunkZ);
        }
        
        @Override
        public String toString() {
            return String.format("Batch[%s-%s@%d,%d]", materialId, lodLevel, chunkX, chunkZ);
        }
    }
    
    /**
     * 渲染批次数据
     */
    public static class RenderBatch {
        private final List<BlockPos> positions = new ArrayList<>();
        private final List<BlockState> states = new ArrayList<>();
        private final BatchKey key;
        private boolean dirty = false;
        private long lastUpdateTime = 0;
        
        public RenderBatch(BatchKey key) {
            this.key = key;
        }
        
        public void addBlock(BlockPos pos, BlockState state) {
            if (positions.size() < MAX_BATCH_SIZE) {
                positions.add(pos.immutable());
                states.add(state);
                dirty = true;
                lastUpdateTime = System.currentTimeMillis();
            }
        }
        
        public boolean removeBlock(BlockPos pos) {
            int index = positions.indexOf(pos);
            if (index >= 0) {
                positions.remove(index);
                states.remove(index);
                dirty = true;
                lastUpdateTime = System.currentTimeMillis();
                return true;
            }
            return false;
        }
        
        public List<BlockPos> getPositions() { return Collections.unmodifiableList(positions); }
        public List<BlockState> getStates() { return Collections.unmodifiableList(states); }
        public BatchKey getKey() { return key; }
        public boolean isDirty() { return dirty; }
        public void markClean() { dirty = false; }
        public int size() { return positions.size(); }
        public boolean isEmpty() { return positions.isEmpty(); }
        public long getLastUpdateTime() { return lastUpdateTime; }
        
        public boolean shouldBatch() {
            return positions.size() >= MIN_BATCH_SIZE;
        }
    }
    
    /**
     * 添加方块到批渲染
     */
    public static void addBlockToBatch(BlockPos pos, BlockState state, Vec3 viewerPos) {
        String materialId = getMaterialId(state);
        LODManager.LODLevel lod = LODManager.calculateBlockLOD(pos, viewerPos);
        
        // 按区块分组
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        
        BatchKey key = new BatchKey(materialId, lod, chunkX, chunkZ);
        RenderBatch batch = renderBatches.computeIfAbsent(key, RenderBatch::new);
        batch.addBlock(pos, state);
        
        // 同时维护按方块状态分组的批次
        blockBatches.computeIfAbsent(state, k -> new ArrayList<>()).add(pos);
    }
    
    /**
     * 从批渲染中移除方块
     */
    public static void removeBlockFromBatch(BlockPos pos, BlockState state, Vec3 viewerPos) {
        String materialId = getMaterialId(state);
        LODManager.LODLevel lod = LODManager.calculateBlockLOD(pos, viewerPos);
        
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        
        BatchKey key = new BatchKey(materialId, lod, chunkX, chunkZ);
        RenderBatch batch = renderBatches.get(key);
        if (batch != null) {
            batch.removeBlock(pos);
            if (batch.isEmpty()) {
                renderBatches.remove(key);
            }
        }
        
        // 从方块状态批次中移除
        List<BlockPos> stateList = blockBatches.get(state);
        if (stateList != null) {
            stateList.remove(pos);
            if (stateList.isEmpty()) {
                blockBatches.remove(state);
            }
        }
    }
    
    /**
     * 获取材质ID（用于批分组）
     */
    private static String getMaterialId(BlockState state) {
        // 简化的材质分组逻辑
        String blockName = state.getBlock().toString();
        
        // 按材质类型分组
        if (blockName.contains("stone")) return "stone";
        if (blockName.contains("wood") || blockName.contains("log")) return "wood";
        if (blockName.contains("dirt") || blockName.contains("grass")) return "dirt";
        if (blockName.contains("sand")) return "sand";
        if (blockName.contains("glass")) return "glass";
        if (blockName.contains("metal") || blockName.contains("iron")) return "metal";
        
        return "other";
    }
    
    /**
     * 获取所有待渲染的批次
     */
    public static Collection<RenderBatch> getRenderBatches() {
        return renderBatches.values();
    }
    
    /**
     * 获取指定LOD级别的批次
     */
    public static List<RenderBatch> getBatchesByLOD(LODManager.LODLevel lod) {
        return renderBatches.values().stream()
            .filter(batch -> batch.getKey().lodLevel == lod)
            .filter(RenderBatch::shouldBatch)
            .toList();
    }
    
    /**
     * 获取指定材质的批次
     */
    public static List<RenderBatch> getBatchesByMaterial(String materialId) {
        return renderBatches.values().stream()
            .filter(batch -> batch.getKey().materialId.equals(materialId))
            .filter(RenderBatch::shouldBatch)
            .toList();
    }
    
    /**
     * 获取指定区块的批次
     */
    public static List<RenderBatch> getBatchesByChunk(int chunkX, int chunkZ) {
        return renderBatches.values().stream()
            .filter(batch -> batch.getKey().chunkX == chunkX && batch.getKey().chunkZ == chunkZ)
            .filter(RenderBatch::shouldBatch)
            .toList();
    }
    
    /**
     * 更新批次（重新计算LOD等）
     */
    public static void updateBatches(Vec3 viewerPos) {
        Iterator<Map.Entry<BatchKey, RenderBatch>> iterator = renderBatches.entrySet().iterator();
        Map<BatchKey, RenderBatch> updatedBatches = new HashMap<>();
        
        while (iterator.hasNext()) {
            Map.Entry<BatchKey, RenderBatch> entry = iterator.next();
            RenderBatch batch = entry.getValue();
            
            // 检查批次是否需要重新分组（LOD变化）
            if (!batch.getPositions().isEmpty()) {
                BlockPos firstPos = batch.getPositions().get(0);
                LODManager.LODLevel currentLOD = LODManager.calculateBlockLOD(firstPos, viewerPos);
                
                if (currentLOD != batch.getKey().lodLevel) {
                    // LOD级别变化，需要重新分组
                    iterator.remove();
                    
                    for (int i = 0; i < batch.getPositions().size(); i++) {
                        BlockPos pos = batch.getPositions().get(i);
                        BlockState state = batch.getStates().get(i);
                        addBlockToBatch(pos, state, viewerPos);
                    }
                }
            }
        }
    }
    
    /**
     * 清理空批次和过期批次
     */
    public static void cleanupBatches() {
        long currentTime = System.currentTimeMillis();
        long maxAge = 30000; // 30秒未更新的批次被清理
        
        renderBatches.entrySet().removeIf(entry -> {
            RenderBatch batch = entry.getValue();
            return batch.isEmpty() || (currentTime - batch.getLastUpdateTime() > maxAge);
        });
        
        blockBatches.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
    
    /**
     * 计算批渲染效率
     */
    public static BatchStats calculateBatchStats() {
        int totalBatches = renderBatches.size();
        int activeBatches = (int) renderBatches.values().stream()
            .filter(RenderBatch::shouldBatch)
            .count();
        int totalObjects = renderBatches.values().stream()
            .mapToInt(RenderBatch::size)
            .sum();
        int batchedObjects = renderBatches.values().stream()
            .filter(RenderBatch::shouldBatch)
            .mapToInt(RenderBatch::size)
            .sum();
        
        return new BatchStats(totalBatches, activeBatches, totalObjects, batchedObjects);
    }
    
    /**
     * 批渲染统计信息
     */
    public static class BatchStats {
        public final int totalBatches;
        public final int activeBatches;
        public final int totalObjects;
        public final int batchedObjects;
        public final float batchEfficiency;
        
        public BatchStats(int totalBatches, int activeBatches, int totalObjects, int batchedObjects) {
            this.totalBatches = totalBatches;
            this.activeBatches = activeBatches;
            this.totalObjects = totalObjects;
            this.batchedObjects = batchedObjects;
            this.batchEfficiency = totalObjects > 0 ? (float) batchedObjects / totalObjects : 0.0f;
        }
        
        @Override
        public String toString() {
            return String.format("Batches: %d/%d | Objects: %d/%d | Efficiency: %.1f%%",
                activeBatches, totalBatches, batchedObjects, totalObjects, batchEfficiency * 100);
        }
    }
    
    /**
     * 强制重建所有批次
     */
    public static void rebuildBatches(Level world, Vec3 viewerPos) {
        clearBatches();
        
        // 重新扫描附近区块并构建批次
        int viewDistance = 8; // 可配置
        BlockPos viewerBlock = BlockPos.containing(viewerPos);
        
        for (int x = -viewDistance; x <= viewDistance; x++) {
            for (int z = -viewDistance; z <= viewDistance; z++) {
                // 扫描区块中的方块并添加到批次
                // 这里需要实际的区块扫描逻辑
            }
        }
    }
    
    /**
     * 清空所有批次
     */
    public static void clearBatches() {
        renderBatches.clear();
        blockBatches.clear();
    }
    
    /**
     * 获取批渲染系统状态信息
     */
    public static String getBatchInfo() {
        BatchStats stats = calculateBatchStats();
        return "Batch Renderer: " + stats.toString();
    }
}
