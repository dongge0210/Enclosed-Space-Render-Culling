# Enclosed-Space-Render-Culling

<span style="color: red; font-size: 24px; font-weight: bold;">⚠️ 重要警告：此模组仅能在正式环境中使用,在开发环境（runClient）中将会导致 Mixin 报错！</span>

封闭空间渲染优化 (Enclosed Space Render Culling) 

**ModID**: `enclosed_culling`

## 模组简介

这是一个针对 Minecraft 1.20.1 Forge 的性能优化模组,专门用于优化封闭房间内的渲染和方块实体tick,能够极大节省GPU与CPU性能。

### 主要功能

- **智能房间检测**：使用高效的洪水填充算法检测封闭空间
- **渲染剔除优化**：对不可见的封闭空间进行渲染剔除
- **方块实体优化**：暂停不可见房间内的方块实体tick
- **Create模组兼容**：特别针对机械动力等高负载mod的BlockEntity进行优化
- **缓存系统**：使用LRU缓存提高性能,减少重复计算

### 技术特性

#### 房间检测算法
- 使用优化的洪水填充算法进行房间边界检测
- 支持自定义门洞检测（空气、玻璃、门等）
- 最大房间大小限制防止性能问题（MAX_ROOM_SIZE = 512）
- 递归可见性检测,支持房间连通性（最大深度8层）

#### 渲染优化
- 视线检测：使用Bresenham线段算法进行高精度视线判定
- 遮挡剔除：检查方块是否被完全包围
- LRU缓存：4096大小的遮挡缓存,提高查询效率
- 距离优化：3格半径内的精确检测

#### 方块实体优化
- 通过Mixin注入优化Create、Mekanism、Thermal等mod的方块实体
- 仅对不可见房间内的高负载方块实体暂停tick
- 保证游戏功能完整性的同时提升性能

### 兼容性

#### 依赖模组
- Minecraft: 1.20.1
- Forge: 47.4.0+
- Create: 6.0.6+ (必需)
- Flywheel: 1.0.4+ (通过Create包含)
- Ponder: 1.0.80+ (通过Create包含)
- MixinExtras: 0.4.1 (运行时依赖)

#### 可选兼容
- Mekanism
- Thermal Expansion
- 其他使用大量方块实体的模组

### 性能提升

- FPS提升：减少不必要的方块实体渲染,优化封闭空间的几何计算,智能剔除不可见区域
- CPU优化：暂停不可见区域的方块实体计算,减少物理模拟和逻辑更新
- 内存效率：智能缓存减少重复计算,LRU策略管理内存使用

### 开发信息

- 开发者: dongge0210
- 许可证: MIT
- 版本: 1.0.0
- 构建工具: Gradle + ForgeGradle 6.0
- 开发环境: Java 17 + IntelliJ IDEA

### 构建说明

环境要求：
- Java 17+
- Gradle 7.6+
- Minecraft 1.20.1
- Forge 47.4.0+

构建步骤：
1. 克隆项目：`git clone <repository-url>`
2. 进入目录：`cd Enclosed-Space-Render-Culling`
3. 构建项目：`./gradlew build`
4. 生成jar：`./gradlew reobfJar`

### 已知问题

- 开发环境限制：Mixin在开发环境下可能报错,建议仅在生产环境使用
- 复杂结构：极复杂的连通结构可能影响检测精度
- 方块识别：某些特殊方块可能不被正确识别为"门洞"
- 性能影响：初次运行时房间检测可能造成短暂卡顿

### 故障排除

常见问题：
1. Mixin报错：确保在生产环境运行,避免开发环境使用
2. 依赖缺失：检查Create、Flywheel、Ponder等依赖是否正确安装
3. 性能问题：调整配置文件中的缓存大小和检测参数
4. 兼容性问题：查看日志确认模组加载顺序

### 贡献指南

欢迎社区贡献！请遵循以下规范：

1. 代码规范：遵循Java编码标准
2. 测试要求：确保新功能经过充分测试
3. 文档更新：同步更新相关文档
4. 向后兼容：保持API稳定性

### 更新日志

v1.0.0 (2025-06-28)
- 初始版本发布
- 支持基础房间检测和渲染优化
- 集成Create模组兼容性
- 实现LRU缓存和Mixin优化

### 许可证

本项目采用 MIT 许可证开源。详见 [LICENSE](LICENSE) 文件。

### 链接

- 源代码: https://github.com/dongge0210/Enclosed-Space-Render-Culling
- 问题反馈: https://github.com/dongge0210/Enclosed-Space-Render-Culling/issues
- 开发文档: https://github.com/dongge0210/Enclosed-Space-Render-Culling/wiki
