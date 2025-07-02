package com.dongge0210.enclosedculling.gui;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import com.dongge0210.enclosedculling.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ConfigScreen extends Screen {
    private final Screen parent;
    private Checkbox enableCullingCheckbox;  // 空间剔除开关
    private Checkbox enableDebugCheckbox;    // 调试界面开关
    private Checkbox enableEntityCullingCheckbox; // 实体剔除开关

    public ConfigScreen(Screen parent) {
        super(Component.literal("Enclosed Culling 配置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        
        int centerX = this.width / 2;
        int startY = this.height / 4;
        
        // 空间剔除功能开关
        boolean enableCullingValue = false;
        try {
            enableCullingValue = ModConfig.COMMON.enableCulling.get();
        } catch (Exception e) {
            // 配置未加载，使用默认值
        }
        
        this.enableCullingCheckbox = new Checkbox(
            centerX - 100, startY,
            200, 20,
            Component.literal("启用空间剔除功能"),
            enableCullingValue
        );
        this.enableCullingCheckbox.setTooltip(Tooltip.create(Component.literal("开启/关闭空间剔除功能（房间识别和视线剔除）")));
        this.addRenderableWidget(this.enableCullingCheckbox);
        
        // 实体剔除功能已移除
        
        // 实体剔除功能已移除，只显示空间剔除
        this.enableEntityCullingCheckbox = new Checkbox(
            centerX - 100, startY + 30,
            200, 20,
            Component.literal("实体剔除功能已移除"),
            false
        );
        this.enableEntityCullingCheckbox.active = false; // 禁用复选框
        this.enableEntityCullingCheckbox.setTooltip(Tooltip.create(Component.literal(
            "§7实体剔除功能已从此版本中移除\n§7本MOD现在只专注于空间剔除和方块剔除功能"
        )));
        this.addRenderableWidget(this.enableEntityCullingCheckbox);
        
        // 调试界面开关
        boolean enableDebugValue = false;
        try {
            enableDebugValue = ModConfig.COMMON.enableDebug.get();
        } catch (Exception e) {
            // 配置未加载，使用默认值
        }
        
        this.enableDebugCheckbox = new Checkbox(
            centerX - 100, startY + 60,
            200, 20,
            Component.literal("启用调试界面"),
            enableDebugValue
        );
        this.enableDebugCheckbox.setTooltip(Tooltip.create(Component.literal("开启/关闭调试信息显示")));
        this.addRenderableWidget(this.enableDebugCheckbox);
        
        // 保存按钮
        Button saveButton = Button.builder(
            Component.literal("保存设置"),
            button -> this.saveAndClose())
            .bounds(centerX - 50, startY + 100, 100, 20)
            .build();
        this.addRenderableWidget(saveButton);
        
        // 取消按钮
        Button cancelButton = Button.builder(
            Component.literal("取消"),
            button -> this.onClose())
            .bounds(centerX - 50, startY + 130, 100, 20)
            .build();
        this.addRenderableWidget(cancelButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        
        // 配置状态指示器
        String configStatus = "§7配置状态: ";
        boolean configReady = false;
        try {
            ModConfig.COMMON.enableCulling.get();
            configStatus += "§a已就绪 - 可以保存设置";
            configReady = true;
        } catch (Exception e) {
            configStatus += "§e初始化中... - 请等待几秒";
        }
        guiGraphics.drawCenteredString(this.font, 
            Component.literal(configStatus), 
            this.width / 2, 35, 0xFFFFFF);
        
        // 如果配置未就绪，显示额外提示
        if (!configReady) {
            guiGraphics.drawCenteredString(this.font, 
                Component.literal("§6配置系统启动中，保存按钮将在就绪后生效"), 
                this.width / 2, 50, 0xFFAA00);
        }
        
        // 版本信息
        guiGraphics.drawCenteredString(this.font, 
            Component.literal("§7版本 0.1.60-E7 - 封闭空间渲染剔除"), 
            this.width / 2, this.height - 30, 0xAAAAAAA);
            
        // 功能分离说明
        guiGraphics.drawCenteredString(this.font, 
            Component.literal("§e空间剔除和实体剔除功能可独立控制"), 
            this.width / 2, this.height / 4 + 85, 0xFFAA00);
    }

    /**
     * 检测Entity Culling mod是否存在
     */
    private boolean detectEntityCulling() {
        try {
            Class.forName("net.mehvahdjukaar.entityculling.EntityCullingMod");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void saveAndClose() {
        // 保存配置
        boolean configReady = false;
        
        // 先检查配置是否准备好
        try {
            ModConfig.COMMON.enableCulling.get();
            configReady = true;
        } catch (Exception e) {
            // 配置还没准备好
        }
        
        if (configReady) {
            try {
                // 配置已就绪，安全地保存
                ModConfig.COMMON.enableCulling.set(this.enableCullingCheckbox.selected());
                ModConfig.COMMON.enableDebug.set(this.enableDebugCheckbox.selected());
                
                // 实体剔除功能已移除，不再保存相关配置
                
                ModConfig.COMMON_SPEC.save();
                
                // 通知用户配置已保存
                this.showMessage("§a配置已保存并生效");
                EnclosedSpaceRenderCulling.LOGGER.info("Configuration saved successfully");
                
                // 保存成功，关闭界面
                this.onClose();
                
            } catch (Exception e) {
                // 保存失败
                this.showMessage("§c配置保存失败: " + e.getMessage());
                EnclosedSpaceRenderCulling.LOGGER.error("Failed to save config", e);
                this.onClose();
            }
        } else {
            // 配置还没有准备好，显示提示但不关闭界面
            this.showMessage("§e配置系统正在初始化中，请稍等片刻后再点击保存");
            EnclosedSpaceRenderCulling.LOGGER.info("Config system not ready yet, keeping config screen open for user to retry");
            
            // 不关闭界面，让用户可以重试
        }
    }

    /**
     * 安全地向玩家显示消息
     */
    private void showMessage(String message) {
        try {
            Minecraft mc = this.minecraft;
            if (mc != null) {
                net.minecraft.client.player.LocalPlayer player = mc.player;
                if (player != null) {
                    player.displayClientMessage(Component.literal(message), true);
                }
            }
        } catch (Exception e) {
            // 如果消息发送失败，只记录日志，不抛出异常
            EnclosedSpaceRenderCulling.LOGGER.debug("Failed to show message to player: {}", e.getMessage());
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        if (this.minecraft != null && this.parent != null) {
            try {
                this.minecraft.setScreen(this.parent);
            } catch (Exception e) {
                // 忽略设置屏幕失败
                EnclosedSpaceRenderCulling.LOGGER.warn("Failed to set parent screen: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
