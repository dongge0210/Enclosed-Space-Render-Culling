# EntityCulling 兼容性指南

## 兼容性概述

本MOD与EntityCulling MOD完全兼容，自动检测并避免功能冲突。

## 自动检测机制

### 检测的MOD ID
- `entityculling`
- `entity_culling`
- `entitycull`
- `entity-culling`
- `tr7zwEntityCulling`

### 其他冲突检测
- `cullingmod`
- `renderculling`
- `performanceplus`

## 兼容性行为

### 当检测到EntityCulling时：
1. **自动启用兼容模式**
2. **实体剔除功能委托给EntityCulling**
3. **专注于方块/空间剔除优化**
4. **显示兼容性状态警告**

### 功能分工：
- **EntityCulling**: 负责实体剔除优化
- **我们的MOD**: 负责方块和空间剔除优化

## 配置选项

在 `config/enclosed_culling-common.toml` 中：

```toml
[compatibility]
  # 强制启用实体剔除（即使检测到EntityCulling）
  forceEntityCulling = false
  
  # 显示兼容性警告信息
  showCompatibilityWarnings = true
  
  # 自动禁用冲突功能
  autoDisableConflictingFeatures = true
```

## 命令使用

### 查看兼容性状态
```
/enclosedculling compat
```

显示信息包括：
- EntityCulling检测状态
- 兼容模式状态
- 功能分工状态
- 兼容性建议

## 冲突处理

### 如果遇到问题：

1. **查看兼容性状态**
   ```
   /enclosedculling compat
   ```

2. **检查日志**
   - 启动时会显示兼容性警告
   - 搜索关键字：`COMPATIBILITY MODE`

3. **调整配置**
   - 设置 `forceEntityCulling = false` 确保委托给EntityCulling
   - 设置 `autoDisableConflictingFeatures = true` 启用自动处理

4. **手动控制**
   ```
   /enclosedculling debug  # 切换调试模式查看详情
   /enclosedculling stats  # 查看性能统计
   ```

## 性能建议

### 推荐配置组合：

**与EntityCulling共存时：**
- EntityCulling: 处理实体剔除
- 我们的MOD: 处理方块剔除
- 配置: `forceEntityCulling = false`

**单独使用时：**
- 我们的MOD: 处理所有剔除
- 配置: `forceEntityCulling = true`

## 故障排除

### 常见问题：

1. **实体剔除不工作**
   - 检查是否检测到EntityCulling
   - 使用 `/enclosedculling compat` 查看状态

2. **性能没有提升**
   - 两个MOD可能功能重叠
   - 考虑只保留一个实体剔除MOD

3. **游戏崩溃**
   - 设置 `autoDisableConflictingFeatures = true`
   - 重启游戏让自动兼容生效

## 开发者信息

### 兼容性API
```java
// 检查EntityCulling是否存在
EntityCullingCompatibility.isEntityCullingDetected()

// 检查是否应该跳过实体剔除
EntityCullingCompatibility.shouldSkipEntityCulling()

// 获取兼容性状态
EntityCullingCompatibility.getCompatibilityStatus()
```

### 集成示例
```java
// 在实体剔除前检查兼容性
if (!EntityCullingCompatibility.shouldSkipEntityCulling()) {
    // 执行我们的实体剔除逻辑
    performEntityCulling(entity);
}
```

## 更新日志

- **v0.1.31**: 首次添加EntityCulling兼容性检测
- 自动检测多种EntityCulling MOD ID变体
- 配置化的兼容性控制选项
- 详细的兼容性状态报告

## 最佳实践

1. **保留EntityCulling用于实体优化** - 它在这方面很专业
2. **使用我们的MOD进行方块剔除** - 我们的空间分析更强大
3. **启用自动兼容性检测** - 让系统自动处理冲突
4. **定期检查兼容性状态** - 使用命令监控状态
5. **报告问题** - 如果发现兼容性问题请及时反馈

---

*本兼容性系统设计目标是让两个MOD和谐共存，各自发挥优势，为玩家提供最佳的性能优化体验。*
