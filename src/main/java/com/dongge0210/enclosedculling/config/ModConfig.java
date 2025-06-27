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

        public Common(ForgeConfigSpec.Builder builder) {
            enableCulling = builder.comment("是否启用AABB剔除").define("enableCulling", true);
            cullDistance = builder.comment("剔除距离（方块）").defineInRange("cullDistance", 32, 8, 128);
        }
    }
}