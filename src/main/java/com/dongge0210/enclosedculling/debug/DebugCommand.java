package com.dongge0210.enclosedculling.debug;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import com.dongge0210.enclosedculling.config.ModConfig;
import com.dongge0210.enclosedculling.room.RoomManager;
import com.dongge0210.enclosedculling.culling.SpaceConnectivityAnalyzer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.event.config.ModConfigEvent;
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
            
            // 重载配置：/enclosedculling reload
            .then(Commands.literal("reload")
                .executes(DebugCommand::reloadConfig))
            
            // 统计信息：/enclosedculling stats
            .then(Commands.literal("stats")
                .executes(DebugCommand::showStats))
            
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
            String stats = RoomManager.getRoomStats();
            String[] lines = stats.split("\n");
            
            context.getSource().sendSuccess(() -> 
                Component.literal("§b[EnclosedCulling] 统计信息:"), false);
            
            for (String line : lines) {
                context.getSource().sendSuccess(() -> 
                    Component.literal("§7" + line), false);
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
            String status = DevelopmentInitializer.getSystemStatus();
            String[] lines = status.split("\n");
            
            for (String line : lines) {
                context.getSource().sendSuccess(() -> 
                    Component.literal("§7" + line), false);
            }
            
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
}
