# 开发者文档目录

## 项目结构概览

```
Enclosed-Space-Render-Culling/
├── src/main/java/com/dongge0210/enclosedculling/
│   ├── EnclosedSpaceRenderCulling.java          # 主模组类
│   ├── client/
│   │   ├── CullingRenderer.java                 # 核心渲染剔除逻辑
│   │   └── gpu/                                 # GPU优化系统
│   │       ├── FrustumCuller.java              # 视锥剔除
│   │       ├── LODManager.java                 # LOD系统
│   │       └── BatchRenderer.java              # 批渲染优化
│   ├── config/
│   │   └── ModConfig.java                      # 配置管理
│   ├── room/
│   │   └── RoomManager.java                    # 房间检测系统
│   ├── debug/                                  # 调试系统
│   │   ├── DebugCommand.java                   # 调试命令
│   │   ├── DebugManager.java                   # 调试管理器
│   │   └── DevelopmentInitializer.java         # 开发环境初始化
│   ├── hotswap/                               # 热更新系统
│   │   ├── HotReloadManager.java              # 热重载管理
│   │   └── ScriptManager.java                 # 脚本系统
│   └── compat/
│       └── EntityCullingCompatibility.java    # EntityCulling兼容性
├── SUMMARY/                                    # 📋 完整开发文档
│   ├── README.md                              # 文档索引
│   ├── PROJECT_SUMMARY.md                     # 项目整体总结
│   ├── GPU_OPTIMIZATION_SUMMARY.md            # GPU优化详细说明
│   ├── ENTITYCULLING_COMPATIBILITY.md         # 兼容性详细文档
│   └── FINAL_BUILD_SUMMARY.md                # 构建总结
└── README.md                                  # 用户使用说明
```

## 快速开始指南

### 对于开发者
1. **了解项目架构**: 查看 [`SUMMARY/PROJECT_SUMMARY.md`](./SUMMARY/PROJECT_SUMMARY.md)
2. **理解GPU优化**: 查看 [`SUMMARY/GPU_OPTIMIZATION_SUMMARY.md`](./SUMMARY/GPU_OPTIMIZATION_SUMMARY.md)
3. **兼容性处理**: 查看 [`SUMMARY/ENTITYCULLING_COMPATIBILITY.md`](./SUMMARY/ENTITYCULLING_COMPATIBILITY.md)

### 对于用户
1. **基本使用**: 查看 [`README.md`](./README.md)
2. **配置选项**: 查看配置文件和调试命令

## 核心技术栈

- **Minecraft**: 1.20.1
- **Forge**: 47.3.0
- **主要技术**:
  - 房间检测算法（洪水填充）
  - GPU多层次优化（视锥剔除、LOD、批渲染）
  - 热重载与脚本系统
  - 智能兼容性处理

## 性能优势

- **渲染性能**: 40-70% 提升（通过GPU优化）
- **逻辑性能**: 减少不必要的方块实体tick
- **兼容性**: 与EntityCulling等模组和谐共存
- **可配置**: 完整的配置系统，支持细粒度调整

---

*完整文档请查看 [SUMMARY/](./SUMMARY/) 文件夹*
