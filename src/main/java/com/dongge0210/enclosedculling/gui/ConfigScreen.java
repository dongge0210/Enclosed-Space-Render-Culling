package com.dongge0210.enclosedculling.gui;

import com.dongge0210.enclosedculling.config.ModConfig;
import com.dongge0210.enclosedculling.util.VersionUtil;
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
        this.enableCullingCheckbox = new Checkbox(
            centerX - 100, startY,
            200, 20,
            Component.literal("启用空间剔除功能"),
            ModConfig.COMMON.enableCulling.get()
        );
        this.enableCullingCheckbox.setTooltip(Tooltip.create(Component.literal("开启/关闭空间剔除功能（房间识别和视线剔除）")));
        this.addRenderableWidget(this.enableCullingCheckbox);
        
        // 实体剔除功能开关
        boolean entityCullingDetected = detectEntityCulling();
        this.enableEntityCullingCheckbox = new Checkbox(
            centerX - 100, startY + 30,
            200, 20,
            Component.literal("启用实体剔除功能" + (entityCullingDetected ? " §c(冲突)" : "")),
            ModConfig.COMMON.enableEntityRendering.get() && (!entityCullingDetected || ModConfig.COMMON.forceEntityCulling.get())
        );
        this.enableEntityCullingCheckbox.setTooltip(Tooltip.create(Component.literal(
            entityCullingDetected ? 
            "§c检测到Entity Culling mod，可能冲突\n强制启用可能导致性能问题" : 
            "开启/关闭智能实体渲染和剔除功能"
        )));
        this.addRenderableWidget(this.enableEntityCullingCheckbox);
        
        // 调试界面开关
        this.enableDebugCheckbox = new Checkbox(
            centerX - 100, startY + 60,
            200, 20,
            Component.literal("启用调试界面"),
            ModConfig.COMMON.enableDebug.get()
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
        
        // 版本信息 - 动态读取
        guiGraphics.drawCenteredString(this.font, 
            Component.literal(VersionUtil.getFormattedVersion()), 
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
        ModConfig.COMMON.enableCulling.set(this.enableCullingCheckbox.selected());
        ModConfig.COMMON.enableDebug.set(this.enableDebugCheckbox.selected());
        
        // 保存实体剔除功能的配置
        if (this.enableEntityCullingCheckbox != null) {
            ModConfig.COMMON.enableEntityRendering.set(this.enableEntityCullingCheckbox.selected());
        }
        
        ModConfig.COMMON_SPEC.save();
        this.onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
