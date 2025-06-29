package com.dongge0210.enclosedculling.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ModConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        final Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static class Common {
        public final ForgeConfigSpec.BooleanValue enableCulling;
        public final ForgeConfigSpec.IntValue cullDistance;
        
        // 调试选项
        public final ForgeConfigSpec.BooleanValue enableDebugMode;
        public final ForgeConfigSpec.BooleanValue enableDebugHUD;
        public final ForgeConfigSpec.BooleanValue enablePerformanceLogging;
        
        // 热重载选项
        public final ForgeConfigSpec.BooleanValue enableHotReload;
        public final ForgeConfigSpec.BooleanValue enableScriptSupport;
        public final ForgeConfigSpec.IntValue fileCheckInterval;
        
        // 性能选项
        public final ForgeConfigSpec.IntValue maxCullingChecksPerTick;
        public final ForgeConfigSpec.DoubleValue cullingCheckTimeLimit;
        
        public Common(ForgeConfigSpec.Builder builder) {
            builder.comment("基础剔除设置").push("culling");
            enableCulling = builder.comment("是否启用AABB剔除").define("enableCulling", true);
            cullDistance = builder.comment("剔除距离（方块）").defineInRange("cullDistance", 32, 8, 128);
            builder.pop();
            
            builder.comment("调试功能设置").push("debug");
            enableDebugMode = builder.comment("是否启用调试模式").define("enableDebugMode", false);
            enableDebugHUD = builder.comment("是否显示调试HUD").define("enableDebugHUD", false);
            enablePerformanceLogging = builder.comment("是否启用性能日志").define("enablePerformanceLogging", false);
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
        }
    }
}