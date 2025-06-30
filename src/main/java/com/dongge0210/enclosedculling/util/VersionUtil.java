package com.dongge0210.enclosedculling.util;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;

import java.io.InputStream;
import java.util.Properties;

/**
 * 版本信息工具类
 * 用于读取和管理Mod版本信息
 */
public class VersionUtil {
    
    private static String cachedVersion = null;
    
    /**
     * 获取Mod版本号
     * 优先从ModContainer获取，备用方案读取gradle.properties
     */
    public static String getModVersion() {
        if (cachedVersion != null) {
            return cachedVersion;
        }
        
        try {
            // 方法1: 从ModContainer获取版本号（推荐）
            ModContainer modContainer = ModList.get().getModContainerById(EnclosedSpaceRenderCulling.MODID).orElse(null);
            if (modContainer != null) {
                String version = modContainer.getModInfo().getVersion().toString();
                if (!version.isEmpty() && !version.equals("NONE")) {
                    cachedVersion = version;
                    return cachedVersion;
                }
            }
            
            // 方法2: 从资源文件读取gradle.properties（备用方案）
            cachedVersion = readVersionFromProperties();
            if (cachedVersion != null) {
                return cachedVersion;
            }
            
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.warn("Failed to read mod version: {}", e.getMessage());
        }
        
        // 默认版本号
        cachedVersion = "0.1.0-DEV";
        return cachedVersion;
    }
    
    /**
     * 从gradle.properties读取版本号
     */
    private static String readVersionFromProperties() {
        try {
            // 尝试从资源文件中读取
            InputStream stream = VersionUtil.class.getResourceAsStream("/version.properties");
            if (stream != null) {
                Properties props = new Properties();
                props.load(stream);
                String version = props.getProperty("mod_version");
                if (version != null && !version.isEmpty()) {
                    return version;
                }
            }
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.debug("Could not read version from properties: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 获取格式化的版本字符串（用于GUI显示）
     */
    public static String getFormattedVersion() {
        String version = getModVersion();
        return "§7版本 " + version + " - 封闭空间渲染剔除";
    }
    
    /**
     * 强制刷新版本缓存
     */
    public static void refreshVersionCache() {
        cachedVersion = null;
    }
}
