package com.dongge0210.enclosedculling;

import com.dongge0210.enclosedculling.config.ModConfig;
import com.dongge0210.enclosedculling.culling.SpaceCullingManager;
import com.dongge0210.enclosedculling.compat.CreateCompatInit;
import com.dongge0210.enclosedculling.compat.EntityCullingCompatibility;
import com.dongge0210.enclosedculling.client.KeyBindings;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(EnclosedSpaceRenderCulling.MODID)
public class EnclosedSpaceRenderCulling {
    public static final String MODID = "enclosed_culling";
    public static final Logger LOGGER = LogManager.getLogger();

    @SuppressWarnings("deprecation") // 暂时抑制过时警告，等待Forge提供新的API
    public EnclosedSpaceRenderCulling() {
        // 获取事件总线
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // 注册配置
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.COMMON_SPEC);
        
        // 注册事件监听器
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        
        LOGGER.info("EnclosedSpaceRenderCulling constructor completed successfully");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("EnclosedSpaceRenderCulling clientSetup 被调用,注册兼容逻辑！");
        event.enqueueWork(() -> {
            try {
                SpaceCullingManager.register();
                
                // 初始化调试系统（仅客户端）
                com.dongge0210.enclosedculling.debug.DebugManager.resetStats();
                LOGGER.info("Debug system initialized for client");
                
                // 显示兼容性警告
                EntityCullingCompatibility.showStartupWarnings();
            } catch (Exception e) {
                LOGGER.error("Failed to initialize client systems: {}", e.getMessage(), e);
            }
        });
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("EnclosedSpaceRenderCulling commonSetup 被调用！");
        event.enqueueWork(() -> {
            // 安全地初始化兼容性检测
            try {
                CreateCompatInit.init();
                EntityCullingCompatibility.initialize();
                LOGGER.info("Compatibility systems initialized successfully");
            } catch (Exception e) {
                LOGGER.warn("Failed to initialize compatibility systems: {}", e.getMessage());
            }
            
            // 初始化热重载系统
            try {
                com.dongge0210.enclosedculling.hotswap.HotReloadManager.initialize();
                com.dongge0210.enclosedculling.hotswap.ScriptManager.initialize();
                com.dongge0210.enclosedculling.hotswap.HotReloadManager.createExampleScripts();
                LOGGER.info("Development and hot-reload systems initialized");
            } catch (Exception e) {
                LOGGER.warn("Failed to initialize development systems: {}", e.getMessage());
            }
        });
    }
}