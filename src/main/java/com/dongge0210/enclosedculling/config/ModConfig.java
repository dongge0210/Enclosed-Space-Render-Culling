package com.dongge0210.enclosedculling.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import org.apache.commons.lang3.tuple.Pair;

public class ModConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        final Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }
    
    /**
     * 注册配置系统
     */
    public static void register(IEventBus modEventBus) {
        // 在Forge 1.20.1+中，配置注册应该通过其他方式处理
        // 这里提供一个简化的实现
    }
    
    /**
     * 重载配置（命令使用）
     */
    public static void reload() {
        // 在Forge 1.20.1中，配置会自动重载
        // 这里只是提供一个占位方法供命令调用
    }

    public static class Common {
        public final ForgeConfigSpec.BooleanValue enableCulling;
        public final ForgeConfigSpec.IntValue cullDistance;
        
        // 调试选项
        public final ForgeConfigSpec.BooleanValue enableDebugMode;
        public final ForgeConfigSpec.BooleanValue enableDebugHUD;
        public final ForgeConfigSpec.BooleanValue enablePerformanceLogging;
        
        // 新增：更简洁的调试选项
        public final ForgeConfigSpec.BooleanValue enableDebug;
        
        // 实体渲染Beta功能
        public final ForgeConfigSpec.BooleanValue enableEntityRendering;
        public final ForgeConfigSpec.BooleanValue enableNightVisionSupport;
        public final ForgeConfigSpec.BooleanValue enableRaidDetection;
        
        // 热重载选项
        public final ForgeConfigSpec.BooleanValue enableHotReload;
        public final ForgeConfigSpec.BooleanValue enableScriptSupport;
        public final ForgeConfigSpec.IntValue fileCheckInterval;
        
        // 性能选项
        public final ForgeConfigSpec.IntValue maxCullingChecksPerTick;
        public final ForgeConfigSpec.DoubleValue cullingCheckTimeLimit;
        
        // 兼容性选项
        public final ForgeConfigSpec.BooleanValue forceEntityCulling;
        public final ForgeConfigSpec.BooleanValue showCompatibilityWarnings;
        public final ForgeConfigSpec.BooleanValue autoDisableConflictingFeatures;
        
        // GPU优化选项
        public final ForgeConfigSpec.BooleanValue enableFrustumCulling;
        public final ForgeConfigSpec.BooleanValue enableLODSystem;
        public final ForgeConfigSpec.BooleanValue enableBatchRendering;
        public final ForgeConfigSpec.BooleanValue enableAdaptiveLOD;
        public final ForgeConfigSpec.IntValue batchMaxSize;
        public final ForgeConfigSpec.DoubleValue lodDistance1;
        public final ForgeConfigSpec.DoubleValue lodDistance2;
        public final ForgeConfigSpec.DoubleValue lodDistance3;
        
        public Common(ForgeConfigSpec.Builder builder) {
            builder.comment("基础剔除设置").push("culling");
            enableCulling = builder.comment("是否启用AABB剔除").define("enableCulling", true);
            cullDistance = builder.comment("剔除距离（方块）").defineInRange("cullDistance", 32, 8, 128);
            builder.pop();
            
            builder.comment("调试功能设置").push("debug");
            enableDebugMode = builder.comment("是否启用调试模式").define("enableDebugMode", false);
            enableDebugHUD = builder.comment("是否显示调试HUD").define("enableDebugHUD", false);
            enablePerformanceLogging = builder.comment("是否启用性能日志").define("enablePerformanceLogging", false);
            enableDebug = builder.comment("是否启用调试界面（简化版）").define("enableDebug", false);
            builder.pop();
            
            builder.comment("实体渲染Beta功能").push("entity_rendering");
            enableEntityRendering = builder.comment("是否启用智能实体渲染（Beta功能）").define("enableEntityRendering", true);
            enableNightVisionSupport = builder.comment("是否启用夜视效果支持").define("enableNightVisionSupport", true);
            enableRaidDetection = builder.comment("是否启用村庄袭击检测").define("enableRaidDetection", true);
            builder.pop();
            
            builder.comment("热重载功能设置").push("hotreload");
            enableHotReload = builder.comment("是否启用热重载").define("enableHotReload", true);
            enableScriptSupport = builder.comment("是否启用脚本支持").define("enableScriptSupport", true);
            fileCheckInterval = builder.comment("文件检查间隔（秒）").defineInRange("fileCheckInterval", 1, 1, 10);
            builder.pop();
            
            builder.comment("性能优化设置").push("performance");
            maxCullingChecksPerTick = builder.comment("每tick最大剔除检查数").defineInRange("maxCullingChecksPerTick", 100, 10, 1000);
            cullingCheckTimeLimit = builder.comment("剔除检查时间限制（毫秒）").defineInRange("cullingCheckTimeLimit", 5.0, 0.1, 50.0);
            builder.pop();
            
            builder.comment("兼容性设置").push("compatibility");
            forceEntityCulling = builder.comment("强制启用实体剔除（即使检测到EntityCulling MOD）").define("forceEntityCulling", false);
            showCompatibilityWarnings = builder.comment("显示兼容性警告信息").define("showCompatibilityWarnings", true);
            autoDisableConflictingFeatures = builder.comment("自动禁用冲突功能").define("autoDisableConflictingFeatures", true);
            builder.pop();
            
            builder.comment("GPU优化设置").push("gpu");
            enableFrustumCulling = builder.comment("启用视锥剔除").define("enableFrustumCulling", true);
            enableLODSystem = builder.comment("启用LOD系统").define("enableLODSystem", true);
            enableBatchRendering = builder.comment("启用批渲染").define("enableBatchRendering", true);
            enableAdaptiveLOD = builder.comment("启用自适应LOD").define("enableAdaptiveLOD", true);
            batchMaxSize = builder.comment("批渲染最大对象数").defineInRange("batchMaxSize", 1024, 64, 4096);
            lodDistance1 = builder.comment("LOD距离1（高质量）").defineInRange("lodDistance1", 32.0, 8.0, 128.0);
            lodDistance2 = builder.comment("LOD距离2（中等质量）").defineInRange("lodDistance2", 64.0, 16.0, 256.0);
            lodDistance3 = builder.comment("LOD距离3（低质量）").defineInRange("lodDistance3", 128.0, 32.0, 512.0);
            builder.pop();
        }
    }
}