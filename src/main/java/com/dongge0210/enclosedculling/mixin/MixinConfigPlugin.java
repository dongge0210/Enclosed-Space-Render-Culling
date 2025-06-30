
package com.dongge0210.enclosedculling.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin配置插件 - 动态控制Mixin的加载
 */
public class MixinConfigPlugin implements IMixinConfigPlugin {
    
    @Override
    public void onLoad(String mixinPackage) {
        // Mixin包加载时调用
    }
    
    @Override
    public String getRefMapperConfig() {
        return null;
    }
    
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 动态决定是否应用特定的Mixin
        
        // 对于Create相关的Mixin，检查Create是否存在
        if (mixinClassName.contains("SmartBlockEntityMixin")) {
            try {
                Class.forName("com.simibubi.create.foundation.blockEntity.SmartBlockEntity");
                return true; // Create存在，应用Mixin
            } catch (ClassNotFoundException e) {
                System.out.println("[EnclosedCulling] Create模组未找到，跳过SmartBlockEntityMixin");
                return false; // Create不存在，跳过Mixin
            }
        }
        
        return true; // 其他Mixin正常应用
    }
    
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 接受目标类
    }
    
    @Override
    public List<String> getMixins() {
        return null;
    }
    
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Mixin应用前调用
    }
    
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Mixin应用后调用
    }
}