package com.dongge0210.enclosedculling.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * VersionUtil 单元测试
 */
public class VersionUtilTest {
    
    @Test
    public void testGetModVersion() {
        String version = VersionUtil.getModVersion();
        assertNotNull(version, "版本号不应为null");
        assertFalse(version.isEmpty(), "版本号不应为空");
        
        // 版本号应该匹配基本格式
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"), 
            "版本号格式应符合 X.Y.Z 模式，实际: " + version);
    }
    
    @Test
    public void testGetFormattedVersion() {
        String formatted = VersionUtil.getFormattedVersion();
        assertNotNull(formatted, "格式化版本不应为null");
        assertTrue(formatted.contains("版本"), "格式化版本应包含'版本'字样");
        assertTrue(formatted.contains("封闭空间渲染剔除"), "格式化版本应包含模组名称");
    }
    
    @Test
    public void testRefreshVersionCache() {
        // 获取初始版本
        String version1 = VersionUtil.getModVersion();
        
        // 刷新缓存
        VersionUtil.refreshVersionCache();
        
        // 再次获取版本（应该重新读取）
        String version2 = VersionUtil.getModVersion();
        
        // 版本应该一致（因为实际版本没有改变）
        assertEquals(version1, version2, "刷新缓存后版本应保持一致");
    }
}
