# Enclosed Space Render Culling - 开发/调试辅助功能

本项目现在包含完整的开发/调试辅助和插件化/热更新功能，帮助开发者更好地调试和自定义模组行为。

## 🔧 调试功能

### 调试命令
模组提供了丰富的调试命令，需要OP权限使用：

```
/enclosedculling reload           # 重载配置文件
/enclosedculling stats            # 显示统计信息
/enclosedculling clearcache       # 清理所有缓存
/enclosedculling highlight <id>   # 高亮显示指定房间
/enclosedculling debug            # 切换调试模式
/enclosedculling benchmark        # 运行性能测试
```

### 调试HUD
启用调试模式后，客户端会显示实时调试信息：
- 玩家位置
- 剔除检查次数和成功率
- 平均检查时间
- 房间统计信息
- 实时性能指标

### 性能监控
- 自动记录剔除检查的性能数据
- 支持自定义性能指标
- 提供详细的性能报告
- 可通过脚本自定义性能监控逻辑

## 🔥 热重载功能

### 配置文件热重载
- 自动监听配置文件变化
- 无需重启游戏即可应用新配置
- 支持所有Forge配置格式

### 脚本系统
支持JavaScript脚本来自定义模组行为：

#### 剔除规则脚本 (`scripts/enclosed_culling/culling_rules.js`)
```javascript
// 自定义剔除逻辑
function shouldCullBlock(blockPos, blockState, playerPos) {
    var blockType = blockState.getBlock().getDescriptionId();
    
    // 重要方块永不剔除
    if (blockType.includes("chest") || blockType.includes("furnace")) {
        return false;
    }
    
    // 距离过远的装饰性方块可以剔除
    var distance = blockPos.distSqr(playerPos);
    if (distance > 1024 && blockType.includes("flower")) {
        return true;
    }
    
    return null; // 使用默认逻辑
}

// 自定义连通性判断
function isConnectedSpace(pos1, pos2, level) {
    // 自定义逻辑
    return null; // 使用默认逻辑
}
```

#### 调试钩子脚本 (`scripts/enclosed_culling/debug_hooks.js`)
```javascript
// 剔除检查前的钩子
function onBeforeCullingCheck(blockPos, playerPos) {
    console.log("Checking culling for: " + blockPos);
}

// 剔除检查后的钩子
function onAfterCullingCheck(blockPos, result, reason) {
    if (result) {
        console.log("Culled: " + blockPos + " (" + reason + ")");
    }
}

// 性能指标钩子
function onPerformanceMetric(metricName, value) {
    if (metricName === "culling_check_time" && value > 5.0) {
        console.warn("Slow culling check: " + value + "ms");
    }
}
```

### 自动文件监听
- 自动监听脚本文件变化
- 实时重载修改的脚本
- 支持多个脚本文件
- 可配置检查间隔

## ⚙️ 配置选项

在 `config/enclosed_culling-common.toml` 中新增了以下配置项：

```toml
[culling]
    # 是否启用AABB剔除
    enableCulling = true
    # 剔除距离（方块）
    cullDistance = 32

[debug]
    # 是否启用调试模式
    enableDebugMode = false
    # 是否显示调试HUD
    enableDebugHUD = false
    # 是否启用性能日志
    enablePerformanceLogging = false

[hotreload]
    # 是否启用热重载
    enableHotReload = true
    # 是否启用脚本支持
    enableScriptSupport = true
    # 文件检查间隔（秒）
    fileCheckInterval = 1

[performance]
    # 每tick最大剔除检查数
    maxCullingChecksPerTick = 100
    # 剔除检查时间限制（毫秒）
    cullingCheckTimeLimit = 5.0
```

## 📁 目录结构

```
scripts/
└── enclosed_culling/
    ├── culling_rules.js     # 自定义剔除规则
    └── debug_hooks.js       # 调试钩子脚本

config/
└── enclosed_culling-common.toml  # 模组配置文件
```

## 🚀 使用指南

### 1. 启用调试模式
```
/enclosedculling debug
```
或在配置文件中设置 `enableDebugMode = true`

### 2. 查看性能统计
```
/enclosedculling stats
```

### 3. 自定义剔除逻辑
1. 编辑 `scripts/enclosed_culling/culling_rules.js`
2. 保存文件，脚本会自动重载
3. 在游戏中测试新的剔除行为

### 4. 性能调优
1. 运行基准测试：`/enclosedculling benchmark`
2. 查看性能指标
3. 根据结果调整配置参数

### 5. 故障排除
1. 清理缓存：`/enclosedculling clearcache`
2. 重载配置：`/enclosedculling reload`
3. 查看日志文件中的详细信息

## 🔍 API说明

### 脚本API
脚本中可用的全局函数和对象：

```javascript
// 控制台输出
console.log(message)
console.warn(message)
console.error(message)
console.debug(message)

// 距离计算
distance(pos1, pos2)        // 欧几里得距离
distanceSquared(pos1, pos2)  // 平方距离（性能更好）

// 日志记录
logger.info(message)
logger.warn(message)
logger.error(message)
logger.debug(message)
```

### 钩子函数
可以在脚本中实现以下函数来自定义行为：

- `shouldCullBlock(blockPos, blockState, playerPos)` - 自定义剔除逻辑
- `isConnectedSpace(pos1, pos2, level)` - 自定义连通性判断
- `onBeforeCullingCheck(blockPos, playerPos)` - 剔除检查前钩子
- `onAfterCullingCheck(blockPos, result, reason)` - 剔除检查后钩子
- `onPerformanceMetric(metricName, value)` - 性能指标钩子

## 📊 性能优化建议

1. **合理配置检查限制**：根据服务器性能调整 `maxCullingChecksPerTick`
2. **使用缓存**：启用缓存机制减少重复计算
3. **脚本优化**：保持脚本函数简洁高效
4. **监控性能**：定期查看性能指标，及时发现问题

## 🤝 开发者支持

如果您在使用过程中遇到问题或有新的功能需求，请：

1. 查看日志文件获取详细错误信息
2. 使用调试命令收集相关数据
3. 在GitHub上提交issue
4. 附上配置文件和相关日志

---

这个强大的调试和热重载系统让您能够：
- 实时调试剔除逻辑
- 快速测试新的配置
- 自定义剔除行为
- 监控性能表现
- 无缝开发和调试体验
