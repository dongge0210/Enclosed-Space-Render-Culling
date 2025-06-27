package com.dongge0210.enclosedculling;

import com.dongge0210.enclosedculling.config.ModConfig;
import com.dongge0210.enclosedculling.culling.SpaceCullingManager;
import com.dongge0210.enclosedculling.compat.CreateCompatInit;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(EnclosedSpaceRenderCulling.MODID)
public class EnclosedSpaceRenderCulling {
    public static final String MODID = "enclosed_culling";
    public static final Logger LOGGER = LogManager.getLogger();

    public EnclosedSpaceRenderCulling(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("EnclosedSpaceRenderCulling 构造函数被调用！");
        // 注册配置
        modEventBus.register(this);
        ModLoadingContext.get().registerConfig(Type.COMMON, ModConfig.COMMON_SPEC);
        // 注册事件监听
        MinecraftForge.EVENT_BUS.register(this);
        // 兼容注册
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("EnclosedSpaceRenderCulling onClientSetup 被调用，注册兼容逻辑！");
        CreateCompatInit.registerCompat();
        SpaceCullingManager.register();
    }
}