package com.dongge0210.enclosedculling.compat;

import net.minecraftforge.fml.ModList;
import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;

public class CreateCompatInit {
    
    private static boolean createLoaded = false;
    
    public static void init() {
        createLoaded = ModList.get().isLoaded("create");
        if (createLoaded) {
            EnclosedSpaceRenderCulling.LOGGER.info("Create mod detected, enabling compatibility features");
            initCreateCompat();
        }
    }
    
    private static void initCreateCompat() {
        // Create 兼容性初始化
        // 由于 Create API 变化,使用事件系统进行集成
        EnclosedSpaceRenderCulling.LOGGER.debug("Initializing Create compatibility");
    }
    
    public static boolean isCreateLoaded() {
        return createLoaded;
    }
    
    /**
     * 检查指定位置的 Create 方块实体是否应该被优化
     */
    public static boolean shouldOptimizeCreateBlockEntity(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (!createLoaded) {
            return false;
        }
        
        // 这里可以添加具体的优化逻辑
        // 例如检查是否在封闭空间中且不可见
        return false; // 暂时返回 false,避免影响正常功能
    }
}