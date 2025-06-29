package com.dongge0210.enclosedculling;

import com.dongge0210.enclosedculling.config.ModConfig;
import com.dongge0210.enclosedculling.culling.SpaceCullingManager;
import com.dongge0210.enclosedculling.compat.CreateCompatInit;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(EnclosedSpaceRenderCulling.MODID)
public class EnclosedSpaceRenderCulling {
    public static final String MODID = "enclosed_culling";
    public static final Logger LOGGER = LogManager.getLogger();

    @SuppressWarnings("deprecation") // 暂时使用已弃用的API,直到找到替代方案
    public EnclosedSpaceRenderCulling() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // 使用正确的配置注册方式
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.COMMON_SPEC);
        
        // 注册事件监听器 - 使用正确的方法签名
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        
        // 注册 Create 兼容性
        CreateCompatInit.init();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("EnclosedSpaceRenderCulling clientSetup 被调用,注册兼容逻辑！");
        event.enqueueWork(() -> {
            SpaceCullingManager.register();
        });
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("EnclosedSpaceRenderCulling commonSetup 被调用！");
        event.enqueueWork(() -> {
            // 在这里可以添加通用设置逻辑
        });
    }
}