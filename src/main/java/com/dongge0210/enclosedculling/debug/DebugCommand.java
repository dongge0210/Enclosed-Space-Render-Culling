package com.dongge0210.enclosedculling.debug;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import com.dongge0210.enclosedculling.config.ModConfig;
import com.dongge0210.enclosedculling.room.RoomManager;
import com.dongge0210.enclosedculling.culling.SpaceConnectivityAnalyzer;
import com.dongge0210.enclosedculling.compat.EntityCullingCompatibility;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 调试命令系统 - 提供开发和调试时使用的各种命令
 * Debug Command System - Provides various commands for development and debugging
 */
@Mod.EventBusSubscriber(modid = EnclosedSpaceRenderCulling.MODID)
public class DebugCommand {
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        dispatcher.register(Commands.literal("enclosedculling")
            .requires(source -> source.hasPermission(2)) // 需要OP权限
            .executes(DebugCommand::showHelp) // 默认显示帮助
            
            // 帮助文档：/enclosedculling help
            .then(Commands.literal("help")
                .executes(DebugCommand::showHelp))
            
            // 重载配置：/enclosedculling reload
            .then(Commands.literal("reload")
                .executes(DebugCommand::reloadConfig))
            
            // 统计信息：/enclosedculling stats
            .then(Commands.literal("stats")
                .executes(DebugCommand::showStats))
            
            // 打开配置界面：/enclosedculling config
            .then(Commands.literal("config")
                .executes(DebugCommand::openConfig))
            
            // 清理缓存：/enclosedculling clearcache
            .then(Commands.literal("clearcache")
                .executes(DebugCommand::clearCache))
            
            // 房间高亮：/enclosedculling highlight <roomId>
            .then(Commands.literal("highlight")
                .then(Commands.argument("roomId", IntegerArgumentType.integer())
                    .executes(DebugCommand::highlightRoom)))
            
            // 调试模式切换：/enclosedculling debug
            .then(Commands.literal("debug")
                .executes(DebugCommand::toggleDebugMode))
            
            // 性能测试：/enclosedculling benchmark
            .then(Commands.literal("benchmark")
                .executes(DebugCommand::runBenchmark))
            
            // 系统状态：/enclosedculling status
            .then(Commands.literal("status")
                .executes(DebugCommand::showSystemStatus))
            
            // 强制重初始化：/enclosedculling reinit
            .then(Commands.literal("reinit")
                .executes(DebugCommand::forceReinitialize))
            
            // 兼容性状态：/enclosedculling compat
            .then(Commands.literal("compat")
                .executes(DebugCommand::showCompatibilityStatus))
            
            // 触发房间检测：/enclosedculling detect
            .then(Commands.literal("detect")
                .executes(DebugCommand::triggerRoomDetection))
            
            // 实体剔除诊断：/enclosedculling entities
            .then(Commands.literal("entities")
                .executes(DebugCommand::diagnoseEntityCulling))
            
            // 门连通性测试：/enclosedculling doors
            .then(Commands.literal("doors")
                .executes(DebugCommand::testDoorConnectivity))
            
            // 距离剔除测试：/enclosedculling distance
            .then(Commands.literal("distance")
                .executes(DebugCommand::testDistanceCulling))
        );
    }
    
    /**
     * 重载配置文件
     */
    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        try {
            // 简单通过重新加载配置文件的方式触发重载
            // 在Forge 1.20.1中，配置会自动重载
            ModConfig.reload();
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§a[EnclosedCulling] 配置重载成功！"), false);
            
            EnclosedSpaceRenderCulling.LOGGER.info("Configuration reloaded via command");
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[EnclosedCulling] 配置重载失败: " + e.getMessage()));
            EnclosedSpaceRenderCulling.LOGGER.error("Failed to reload configuration", e);
            return 0;
        }
    }
    
    /**
     * 显示统计信息
     */
    private static int showStats(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            BlockPos playerPos = player.blockPosition();
            ServerLevel level = (ServerLevel) player.level();
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§b[EnclosedCulling] === 详细统计信息 ==="), false);
            
            // 玩家位置和房间信息
            try {
                Integer playerRoomId = RoomManager.getRoomIdAt(level, playerPos);
                Integer playerGroupId = RoomManager.getGroupIdAt(level, playerPos);
                
                context.getSource().sendSuccess(() -> 
                    Component.literal("§e玩家信息:"), false);
                context.getSource().sendSuccess(() -> 
                    Component.literal("§7  位置: " + playerPos.toShortString()), false);
                context.getSource().sendSuccess(() -> 
                    Component.literal("§7  房间ID: " + (playerRoomId != null ? playerRoomId : "未识别")), false);
                context.getSource().sendSuccess(() -> 
                    Component.literal("§7  房间组ID: " + (playerGroupId != null ? playerGroupId : "未识别")), false);
                
                // 房间连通性测试
                BlockPos testPos = playerPos.offset(5, 0, 0);
                String connectivity = RoomManager.getRoomConnectivityStatus(level, playerPos, testPos);
                context.getSource().sendSuccess(() -> 
                    Component.literal("§7  连通性状态: " + connectivity), false);
                    
            } catch (Exception e) {
                context.getSource().sendSuccess(() -> 
                    Component.literal("§c  房间信息获取失败: " + e.getMessage()), false);
            }
            
            // 系统配置状态
            context.getSource().sendSuccess(() -> 
                Component.literal("§e系统配置:"), false);
            context.getSource().sendSuccess(() -> 
                Component.literal("§7  剔除功能: " + (ModConfig.COMMON.enableCulling.get() ? "§a已启用" : "§c已禁用")), false);
            context.getSource().sendSuccess(() -> 
                Component.literal("§7  调试模式: " + (ModConfig.COMMON.enableDebugMode.get() ? "§a已启用" : "§c已禁用")), false);
            context.getSource().sendSuccess(() -> 
                Component.literal("§7  热重载: " + (ModConfig.COMMON.enableHotReload.get() ? "§a已启用" : "§c已禁用")), false);
            
            // 房间管理器统计
            context.getSource().sendSuccess(() -> 
                Component.literal("§e房间系统统计:"), false);
            String stats = RoomManager.getRoomStats();
            String[] lines = stats.split("\n");
            
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    context.getSource().sendSuccess(() -> 
                        Component.literal("§7  " + line), false);
                }
            }
            
            // 性能统计
            context.getSource().sendSuccess(() -> 
                Component.literal("§e性能统计:"), false);
            String perfReport = DebugManager.getPerformanceReport();
            String[] perfLines = perfReport.split("\n");
            for (String line : perfLines) {
                if (!line.trim().isEmpty() && !line.contains("===")) {
                    context.getSource().sendSuccess(() -> 
                        Component.literal("§7  " + line), false);
                }
            }
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[EnclosedCulling] 获取统计信息失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 清理缓存
     */
    private static int clearCache(CommandContext<CommandSourceStack> context) {
        try {
            RoomManager.clearAll();
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§a[EnclosedCulling] 所有缓存已清理！"), false);
            
            EnclosedSpaceRenderCulling.LOGGER.info("All caches cleared via command");
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[EnclosedCulling] 清理缓存失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 高亮指定房间
     */
    private static int highlightRoom(CommandContext<CommandSourceStack> context) {
        try {
            int roomId = IntegerArgumentType.getInteger(context, "roomId");
            ServerPlayer player = context.getSource().getPlayerOrException();
            ServerLevel level = (ServerLevel) player.level();
            
            RoomManager.debugHighlightRoom(level, roomId, player);
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§a[EnclosedCulling] 房间 " + roomId + " 已高亮显示！"), false);
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[EnclosedCulling] 高亮房间失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 切换调试模式
     */
    private static int toggleDebugMode(CommandContext<CommandSourceStack> context) {
        try {
            boolean newState = DebugManager.toggleDebugMode();
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§b[EnclosedCulling] 调试模式: " + 
                    (newState ? "§a已启用" : "§c已禁用")), false);
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[EnclosedCulling] 切换调试模式失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 运行性能基准测试
     */
    private static int runBenchmark(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Level level = player.level();
            BlockPos playerPos = player.blockPosition();
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§e[EnclosedCulling] 开始性能测试..."), false);
            
            // 异步运行基准测试
            new Thread(() -> runBenchmarkAsync(context.getSource(), level, playerPos)).start();
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[EnclosedCulling] 启动性能测试失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 异步运行基准测试
     */
    private static void runBenchmarkAsync(CommandSourceStack source, Level level, BlockPos playerPos) {
        try {
            long startTime = System.nanoTime();
            
            // 测试房间分析性能
            SpaceConnectivityAnalyzer analyzer = new SpaceConnectivityAnalyzer(level, 5000);
            analyzer.floodFrom(playerPos);
            
            long endTime = System.nanoTime();
            double duration = (endTime - startTime) / 1_000_000.0; // 转换为毫秒
            
            // 在主线程发送结果
            source.getServer().execute(() -> {
                source.sendSuccess(() -> 
                    Component.literal("§a[EnclosedCulling] 性能测试完成！"), false);
                source.sendSuccess(() -> 
                    Component.literal("§7分析耗时: " + String.format("%.2f", duration) + "ms"), false);
            });
            
        } catch (Exception e) {
            source.getServer().execute(() -> {
                source.sendFailure(
                    Component.literal("§c[EnclosedCulling] 性能测试失败: " + e.getMessage()));
            });
        }
    }
    
    /**
     * 显示系统状态
     */
    private static int showSystemStatus(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            BlockPos playerPos = player.blockPosition();
            ServerLevel level = (ServerLevel) player.level();
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§b[EnclosedCulling] === 系统状态 ==="), false);
            
            // 基础系统状态
            String status = DevelopmentInitializer.getSystemStatus();
            String[] lines = status.split("\n");
            
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    // 为状态信息添加颜色
                    final String coloredLine;
                    if (line.contains("✓")) {
                        coloredLine = "§a" + line;
                    } else if (line.contains("✗")) {
                        coloredLine = "§c" + line;
                    } else if (line.contains("===")) {
                        coloredLine = "§e" + line;
                    } else {
                        coloredLine = "§7" + line;
                    }
                    
                    context.getSource().sendSuccess(() -> 
                        Component.literal(coloredLine), false);
                }
            }
            
            // 添加实时运行状态
            context.getSource().sendSuccess(() -> 
                Component.literal("§e实时运行状态:"), false);
            
            // 当前玩家位置和房间信息
            try {
                Integer roomId = RoomManager.getRoomIdAt(level, playerPos);
                Integer groupId = RoomManager.getGroupIdAt(level, playerPos);
                
                context.getSource().sendSuccess(() -> 
                    Component.literal("§7  当前玩家位置: " + playerPos.toShortString()), false);
                context.getSource().sendSuccess(() -> 
                    Component.literal("§7  当前房间ID: " + (roomId != null ? roomId : "未识别")), false);
                context.getSource().sendSuccess(() -> 
                    Component.literal("§7  当前房间组: " + (groupId != null ? groupId : "未识别")), false);
                    
            } catch (Exception e) {
                context.getSource().sendSuccess(() -> 
                    Component.literal("§c  房间状态获取失败: " + e.getMessage()), false);
            }
            
            // 剔除系统状态
            context.getSource().sendSuccess(() -> 
                Component.literal("§e剔除系统状态:"), false);
            context.getSource().sendSuccess(() -> 
                Component.literal("§7  剔除功能: " + (ModConfig.COMMON.enableCulling.get() ? "§a运行中" : "§c已禁用")), false);
            
            // 检查是否有实体在附近被剔除
            try {
                java.util.List<net.minecraft.world.entity.Entity> nearbyEntities = level.getEntities(
                    player, player.getBoundingBox().inflate(32.0), entity -> true);
                
                long visibleEntities = nearbyEntities.stream().filter(e -> !e.isInvisible()).count();
                long invisibleEntities = nearbyEntities.stream().filter(e -> e.isInvisible()).count();
                
                context.getSource().sendSuccess(() -> 
                    Component.literal(String.format("§7  附近实体: 总计%d, 可见%d, 被剔除%d", 
                        nearbyEntities.size(), visibleEntities, invisibleEntities)), false);
                        
            } catch (Exception e) {
                context.getSource().sendSuccess(() -> 
                    Component.literal("§c  实体统计失败: " + e.getMessage()), false);
            }
            
            // 内存和性能状态
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory() / 1024 / 1024;
            long totalMemory = runtime.totalMemory() / 1024 / 1024;
            long freeMemory = runtime.freeMemory() / 1024 / 1024;
            long usedMemory = totalMemory - freeMemory;
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§e内存状态:"), false);
            context.getSource().sendSuccess(() -> 
                Component.literal(String.format("§7  已使用: %dMB / %dMB (%.1f%%)", 
                    usedMemory, maxMemory, (double)usedMemory / maxMemory * 100)), false);
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[EnclosedCulling] 获取系统状态失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 强制重新初始化系统
     */
    private static int forceReinitialize(CommandContext<CommandSourceStack> context) {
        try {
            DevelopmentInitializer.forceReinitialize();
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§a[EnclosedCulling] 系统重新初始化完成！"), false);
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[EnclosedCulling] 系统重新初始化失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 显示兼容性状态
     */
    private static int showCompatibilityStatus(CommandContext<CommandSourceStack> context) {
        try {
            // 获取兼容性状态信息
            String compatStatus = EntityCullingCompatibility.getCompatibilityStatus();
            String[] lines = compatStatus.split("\n");
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§b[EnclosedCulling] 兼容性状态:"), false);
            
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                
                final String color; // 声明为final
                if (line.contains("YES") || line.contains("ENABLED")) {
                    color = "§a"; // 绿色
                } else if (line.contains("NO") || line.contains("DISABLED")) {
                    color = "§c"; // 红色
                } else if (line.contains("AVAILABLE")) {
                    color = "§e"; // 黄色
                } else {
                    color = "§7"; // 默认灰色
                }
                
                context.getSource().sendSuccess(() -> 
                    Component.literal(color + line), false);
            }
            
            // 显示兼容性建议
            if (EntityCullingCompatibility.isEntityCullingDetected()) {
                context.getSource().sendSuccess(() -> 
                    Component.literal("§e[兼容性建议]:"), false);
                    
                String advice = EntityCullingCompatibility.getCompatibilityAdvice();
                String[] adviceLines = advice.split("\n");
                for (String adviceLine : adviceLines) {
                    if (adviceLine.trim().isEmpty()) continue;
                    context.getSource().sendSuccess(() -> 
                        Component.literal("§7" + adviceLine), false);
                }
            }
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[EnclosedCulling] 获取兼容性状态失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 显示帮助文档
     */
    private static int showHelp(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
            "§6=== 封闭空间渲染剔除 - 调试命令 ===\n" +
            "§e/enclosedculling help§f - 显示此帮助信息\n" +
            "§e/enclosedculling config§f - 打开配置界面 (客户端)\n" +
            "§e/enclosedculling reload§f - 重载配置文件\n" +
            "§e/enclosedculling stats§f - 显示性能统计\n" +
            "§e/enclosedculling clearcache§f - 清理所有缓存\n" +
            "§e/enclosedculling highlight <房间ID>§f - 高亮指定房间\n" +
            "§e/enclosedculling debug§f - 切换调试模式\n" +
            "§e/enclosedculling benchmark§f - 运行性能基准测试\n" +
            "§e/enclosedculling status§f - 显示系统状态\n" +
            "§e/enclosedculling reinit§f - 强制重新初始化\n" +
            "§e/enclosedculling compat§f - 显示兼容性状态\n" +
            "§e/enclosedculling detect§f - 触发房间检测\n" +
            "§e/enclosedculling entities§f - 实体剔除诊断\n" +
            "§e/enclosedculling doors§f - 门连通性测试\n" +
            "§e/enclosedculling distance§f - 距离剔除测试\n" +
            "§6=========================================="
        ), false);
        return 1;
    }
    
    /**
     * 触发房间检测
     */
    private static int triggerRoomDetection(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            BlockPos playerPos = player.blockPosition();
            
            // 强制触发房间检测
            RoomManager.getRoomIdAt(player.level(), playerPos);
            
            // 触发周围区域的房间检测
            int detectedRooms = 0;
            for (int x = -16; x <= 16; x += 8) {
                for (int z = -16; z <= 16; z += 8) {
                    for (int y = -8; y <= 8; y += 4) {
                        BlockPos checkPos = playerPos.offset(x, y, z);
                        Integer roomId = RoomManager.getRoomIdAt(player.level(), checkPos);
                        if (roomId != null) {
                            detectedRooms++;
                        }
                    }
                }
            }
            
            final int finalDetectedRooms = detectedRooms;
            context.getSource().sendSuccess(() -> 
                Component.literal("§a[EnclosedCulling] 房间检测完成！检测到 " + finalDetectedRooms + " 个房间位置"), false);
            
            // 显示最新统计
            String stats = RoomManager.getRoomStats();
            String[] lines = stats.split("\n");
            for (String line : lines) {
                final String finalLine = line; // 确保变量是final
                context.getSource().sendSuccess(() -> 
                    Component.literal("§7" + finalLine), false);
            }
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[EnclosedCulling] 房间检测失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 实体剔除诊断
     */
    private static int diagnoseEntityCulling(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            BlockPos playerPos = player.blockPosition();
            ServerLevel level = (ServerLevel) player.level();
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§6[EnclosedCulling] === 实体剔除诊断 ==="), false);
            
            // 获取玩家当前房间
            Integer playerRoomId = RoomManager.getRoomIdAt(level, playerPos);
            Integer playerGroupId = RoomManager.getGroupIdAt(level, playerPos);
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§7玩家位置: " + playerPos.toShortString()), false);
            context.getSource().sendSuccess(() -> 
                Component.literal("§7玩家房间ID: " + (playerRoomId != null ? playerRoomId : "未识别")), false);
            context.getSource().sendSuccess(() -> 
                Component.literal("§7玩家组ID: " + (playerGroupId != null ? playerGroupId : "未识别")), false);
            
            // 检查附近的实体
            java.util.List<net.minecraft.world.entity.Entity> nearbyEntities = level.getEntities(
                player, player.getBoundingBox().inflate(32.0), entity -> true);
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§e检查附近 " + nearbyEntities.size() + " 个实体..."), false);
            
            int visibleCount = 0;
            int culledCount = 0;
            
            for (net.minecraft.world.entity.Entity entity : nearbyEntities) {
                if (entity == player) continue;
                
                BlockPos entityPos = entity.blockPosition();
                Integer entityRoomId = RoomManager.getRoomIdAt(level, entityPos);
                Integer entityGroupId = RoomManager.getGroupIdAt(level, entityPos);
                
                // 检查可见性
                boolean isVisible = RoomManager.isPositionVisible(level, entityPos, playerPos);
                String visibilityReason = isVisible ? "可见" : "被剔除";
                
                // 检查房间连通性
                boolean sameGroup = (playerGroupId != null && entityGroupId != null && 
                                   playerGroupId.equals(entityGroupId));
                
                String entityType = entity.getType().getDescription().getString();
                double distance = entity.distanceTo(player);
                
                String color = isVisible ? "§a" : "§c";
                context.getSource().sendSuccess(() -> 
                    Component.literal(String.format("%s%s@%s (%.1fm): %s", 
                        color, entityType, entityPos.toShortString(), distance, visibilityReason)), false);
                
                if (!isVisible) {
                    context.getSource().sendSuccess(() -> 
                        Component.literal(String.format("§7  房间: %s -> %s, 同组: %s", 
                            playerRoomId, entityRoomId, sameGroup ? "是" : "否")), false);
                    culledCount++;
                } else {
                    visibleCount++;
                }
                
                // 检查门的影响
                if (!isVisible && entityRoomId != null && playerRoomId != null && !entityRoomId.equals(playerRoomId)) {
                    // 检查是否有门连接这两个房间
                    boolean hasDoorConnection = checkDoorConnection(level, playerPos, entityPos);
                    if (hasDoorConnection) {
                        context.getSource().sendSuccess(() -> 
                            Component.literal("§e  警告: 检测到门连接，但实体仍被剔除！"), false);
                    }
                }
            }
            
            final int finalVisibleCount = visibleCount;
            final int finalCulledCount = culledCount;
            context.getSource().sendSuccess(() -> 
                Component.literal(String.format("§6诊断结果: 可见 %d, 被剔除 %d", finalVisibleCount, finalCulledCount)), false);
            
            // EntityCulling兼容性检查
            if (EntityCullingCompatibility.isEntityCullingDetected()) {
                context.getSource().sendSuccess(() -> 
                    Component.literal("§e注意: 检测到EntityCulling模组，可能存在冲突"), false);
                
                String advice = EntityCullingCompatibility.getCompatibilityAdvice();
                if (!advice.isEmpty()) {
                    context.getSource().sendSuccess(() -> 
                        Component.literal("§7建议: " + advice.split("\n")[0]), false);
                }
            }
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[EnclosedCulling] 实体诊断失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 检查两个位置之间是否有门连接
     */
    private static boolean checkDoorConnection(ServerLevel level, BlockPos pos1, BlockPos pos2) {
        // 简单检查：在两点之间的路径上是否有门
        Vec3 start = pos1.getCenter();
        Vec3 end = pos2.getCenter();
        Vec3 direction = end.subtract(start).normalize();
        
        double distance = start.distanceTo(end);
        for (double d = 0; d < distance; d += 1.0) {
            Vec3 checkPos = start.add(direction.scale(d));
            BlockPos blockPos = BlockPos.containing(checkPos);
            
            String blockName = level.getBlockState(blockPos).getBlock().getDescriptionId();
            if (blockName.contains("door") || blockName.contains("gate")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 测试门连通性
     */
    private static int testDoorConnectivity(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            BlockPos playerPos = player.blockPosition();
            ServerLevel level = (ServerLevel) player.level();
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§6[EnclosedCulling] === 门连通性测试 ==="), false);
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§7玩家位置: " + playerPos.toShortString()), false);
            
            // 测试周围32格范围内的连通性
            int testedPositions = 0;
            int connectedByDoor = 0;
            int directConnected = 0;
            int notConnected = 0;
            
            for (int x = -16; x <= 16; x += 4) {
                for (int z = -16; z <= 16; z += 4) {
                    for (int y = -4; y <= 4; y += 2) {
                        BlockPos testPos = playerPos.offset(x, y, z);
                        
                        // 检查基本连通性
                        boolean basicVisible = RoomManager.isPositionVisible(level, testPos, playerPos);
                        
                        // 检查门连通性
                        boolean doorConnected = RoomManager.areRoomsConnectedByDoor(level, playerPos, testPos);
                        
                        testedPositions++;
                        
                        if (basicVisible) {
                            directConnected++;
                        } else if (doorConnected) {
                            connectedByDoor++;
                            
                            // 报告通过门连接的位置
                            double distance = playerPos.distSqr(testPos);
                            context.getSource().sendSuccess(() -> 
                                Component.literal(String.format("§a门连接: %s (距离: %.1f)", 
                                    testPos.toShortString(), Math.sqrt(distance))), false);
                        } else {
                            notConnected++;
                        }
                    }
                }
            }
            
            final int finalTestedPositions = testedPositions;
            context.getSource().sendSuccess(() -> 
                Component.literal(String.format("§6测试结果 (总计 %d 个位置):", finalTestedPositions)), false);
            
            final int finalDirectConnected = directConnected;
            final int finalConnectedByDoor = connectedByDoor;
            final int finalNotConnected = notConnected;
            
            context.getSource().sendSuccess(() -> 
                Component.literal(String.format("§a直接连通: %d", finalDirectConnected)), false);
            context.getSource().sendSuccess(() -> 
                Component.literal(String.format("§e门连通: %d", finalConnectedByDoor)), false);
            context.getSource().sendSuccess(() -> 
                Component.literal(String.format("§c不连通: %d", finalNotConnected)), false);
            
            // 检查附近的门
            context.getSource().sendSuccess(() -> 
                Component.literal("§6附近的门方块:"), false);
            
            int doorCount = 0;
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    for (int y = -2; y <= 2; y++) {
                        BlockPos checkPos = playerPos.offset(x, y, z);
                        BlockState state = level.getBlockState(checkPos);
                        String blockName = state.getBlock().getDescriptionId();
                        
                        if (blockName.contains("door") || blockName.contains("gate") || 
                            blockName.contains("trapdoor")) {
                            
                            boolean isOpen = true;
                            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)) {
                                isOpen = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN);
                            }
                            
                            final String finalBlockName = blockName;
                            final String status = isOpen ? "§a开启" : "§c关闭";
                            final BlockPos finalCheckPos = checkPos;
                            context.getSource().sendSuccess(() -> 
                                Component.literal(String.format("§7%s: %s (%s)", 
                                    finalCheckPos.toShortString(), finalBlockName, status)), false);
                            doorCount++;
                        }
                    }
                }
            }
            
            if (doorCount == 0) {
                context.getSource().sendSuccess(() -> 
                    Component.literal("§7未找到附近的门"), false);
            }
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[EnclosedCulling] 门连通性测试失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 距离剔除测试
     */
    private static int testDistanceCulling(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            BlockPos playerPos = player.blockPosition();
            ServerLevel level = (ServerLevel) player.level();
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§6[EnclosedCulling] === 距离剔除测试 ==="), false);
            
            // 显示环境信息
            int lightLevel = level.getMaxLocalRawBrightness(playerPos);
            boolean inCave = playerPos.getY() < 50 && lightLevel < 8;
            float renderDistance = 16.0f; // 简化处理，实际应该从客户端获取
            
            context.getSource().sendSuccess(() -> 
                Component.literal(String.format("§7环境: %s, 光照: %d, Y坐标: %d", 
                    inCave ? "洞穴" : "地表", lightLevel, playerPos.getY())), false);
            
            // 检测极简血量显示mod（服务器端简化检测）
            boolean neatDetected = false;
            try {
                Class.forName("vazkii.neat.NeatMod");
                neatDetected = true;
            } catch (ClassNotFoundException e) {
                neatDetected = false;
            }
            
            final boolean finalNeatDetected = neatDetected;
            context.getSource().sendSuccess(() -> 
                Component.literal("§7极简血量显示mod: " + (finalNeatDetected ? "§a已检测" : "§c未检测")), false);
            
            // 测试不同距离的剔除判断
            float[] testDistances = {2.0f, 5.0f, 12.0f, 16.0f, 24.0f, 32.0f, 48.0f, 64.0f};
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§6距离剔除测试结果:"), false);
            
            for (float distance : testDistances) {
                // 模拟在指定距离的实体
                BlockPos testPos = playerPos.offset((int)distance, 0, 0);
                
                boolean shouldCull = false;
                String reason = "";
                
                // 强制渲染距离
                if (distance <= 3.0f) {
                    shouldCull = false;
                    reason = "强制渲染区域";
                } 
                // 超过最大距离
                else if (distance > 64.0f) {
                    shouldCull = true;
                    reason = "超过最大距离";
                }
                // 洞穴环境
                else if (inCave && lightLevel < 3) {
                    float darkCaveLimit = finalNeatDetected ? Math.min(24.0f * 0.6f, 16.0f) : 12.0f;
                    if (distance > darkCaveLimit) {
                        shouldCull = true;
                        reason = String.format("洞穴环境过暗 (阈值:%.1f)", darkCaveLimit);
                    } else {
                        shouldCull = false;
                        reason = "洞穴内可见范围";
                    }
                }
                // 极简血量显示mod影响
                else if (finalNeatDetected && distance > 24.0f && lightLevel < 3) {
                    shouldCull = true;
                    reason = "Neat模组+环境过暗";
                }
                // 默认判断
                else {
                    shouldCull = distance > 32.0f;
                    reason = shouldCull ? "超过默认距离阈值" : "正常渲染范围";
                }
                
                final String color = shouldCull ? "§c" : "§a";
                final String status = shouldCull ? "剔除" : "渲染";
                final String finalReason = reason;
                final float finalDistance = distance;
                
                context.getSource().sendSuccess(() -> 
                    Component.literal(String.format("%s%.1f格: %s (%s)", 
                        color, finalDistance, status, finalReason)), false);
            }
            
            // 检查附近的实体
            java.util.List<net.minecraft.world.entity.Entity> nearbyEntities = level.getEntities(
                player, player.getBoundingBox().inflate(32.0), entity -> true);
            
            if (!nearbyEntities.isEmpty()) {
                context.getSource().sendSuccess(() -> 
                    Component.literal("§6附近实体距离剔除分析:"), false);
                
                int shown = 0;
                for (net.minecraft.world.entity.Entity entity : nearbyEntities) {
                    if (entity == player || shown >= 10) continue;
                    
                    double distance = entity.distanceTo(player);
                    String entityType = entity.getType().getDescription().getString();
                    
                    // 模拟距离剔除判断
                    boolean shouldCull = false;
                    String reason = "";
                    
                    if (distance <= 3.0f) {
                        reason = "强制渲染";
                    } else if (distance > 64.0f) {
                        shouldCull = true;
                        reason = "超远距离";
                    } else if (inCave && lightLevel < 3 && distance > 16.0f) {
                        shouldCull = true;
                        reason = "洞穴过暗";
                    } else if (finalNeatDetected && distance > 24.0f && lightLevel < 3) {
                        shouldCull = true;
                        reason = "Neat+暗环境";
                    } else {
                        reason = "正常范围";
                    }
                    
                    final String color = shouldCull ? "§c" : "§a";
                    final String finalEntityType = entityType;
                    final double finalDistance = distance;
                    final String finalReason = reason;
                    
                    context.getSource().sendSuccess(() -> 
                        Component.literal(String.format("%s%s: %.1f格 - %s", 
                            color, finalEntityType, finalDistance, finalReason)), false);
                    shown++;
                }
            }
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[EnclosedCulling] 距离测试失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 打开配置界面命令
     */
    private static int openConfig(CommandContext<CommandSourceStack> context) {
        try {
            // 检查是否在客户端执行
            if (context.getSource().getLevel().isClientSide) {
                // 在客户端线程中打开GUI
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.screen == null) {
                        mc.setScreen(new com.dongge0210.enclosedculling.gui.ConfigScreen(null));
                    }
                });
                
                context.getSource().sendSuccess(() -> 
                    Component.literal("§a[EnclosedCulling] 配置界面已打开"), false);
            } else {
                context.getSource().sendFailure(
                    Component.literal("§c[EnclosedCulling] 配置界面只能在客户端打开"));
            }
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(
                Component.literal("§c[EnclosedCulling] 打开配置界面失败: " + e.getMessage()));
            return 0;
        }
    }
}
