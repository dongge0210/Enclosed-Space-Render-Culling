package com.dongge0210.enclosedculling;

import net.minecraftforge.fml.ModList;

public class CreateCompatInit {
    public static void registerCompat() {
        if (ModList.get().isLoaded("create")) {
            // 这里写Create相关注册或兼容逻辑
            System.out.println("[enclosed_culling] 检测到已安装 Create，启用兼容功能！");
            // 比如：CreateCompat.registerEvents();
        } else {
            System.out.println("[enclosed_culling] 未检测到 Create，不启用兼容。");
        }
    }
}