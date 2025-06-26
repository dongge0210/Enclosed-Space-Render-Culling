package com.dongge0210.enclosedculling;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(EnclosedSpaceRenderCulling.MODID)
public class EnclosedSpaceRenderCulling {
    public static final String MODID = "enclosed_culling";
    public EnclosedSpaceRenderCulling() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    }
    private void onClientSetup(final FMLClientSetupEvent event) {
        System.out.println("封闭空间渲染优化Mod已加载！");
    }
}