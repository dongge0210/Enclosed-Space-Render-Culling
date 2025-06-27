package com.dongge0210.enclosedculling.compat;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import net.minecraftforge.fml.ModList;

public class CreateCompatInit {
    private static final String CREATE_MODID = "create";

    public static void registerCompat() {
        if (isCreateLoaded()) {
            EnclosedSpaceRenderCulling.LOGGER.info("检测到 Create Mod，正在注册兼容内容...");
            try {
                registerCreateCompat();
                EnclosedSpaceRenderCulling.LOGGER.info("Create 兼容内容注册完成。");
            } catch (Exception e) {
                EnclosedSpaceRenderCulling.LOGGER.error("Create 兼容内容注册失败！", e);
            }
        } else {
            EnclosedSpaceRenderCulling.LOGGER.info("未检测到 Create Mod，无需进行兼容处理。");
        }
    }

    private static boolean isCreateLoaded() {
        return ModList.get().isLoaded(CREATE_MODID);
    }

    // 这里写和Create mod相关的兼容注册逻辑
    private static void registerCreateCompat() {
        // TODO: 在这里扩展你的具体兼容逻辑
        // 例如注册culling handler、重定向渲染、监听Create的事件等
        EnclosedSpaceRenderCulling.LOGGER.info("Create兼容逻辑已调用（请在这里实现具体功能）。");
    }
}