package com.dongge0210.enclosedculling.hotswap;

import com.dongge0210.enclosedculling.EnclosedSpaceRenderCulling;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.script.*;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 脚本管理器 - 支持JavaScript脚本的动态加载和执行
 * Script Manager - Supports dynamic loading and execution of JavaScript scripts
 */
public class ScriptManager {
    
    private static final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
    private static ScriptEngine scriptEngine;
    private static final Map<String, CompiledScript> compiledScripts = new ConcurrentHashMap<>();
    
    private static boolean initialized = false;
    
    /**
     * 初始化脚本引擎
     */
    public static void initialize() {
        if (initialized) return;
        
        try {
            scriptEngine = scriptEngineManager.getEngineByName("javascript");
            if (scriptEngine == null) {
                // 尝试使用Nashorn
                scriptEngine = scriptEngineManager.getEngineByName("nashorn");
            }
            
            if (scriptEngine == null) {
                EnclosedSpaceRenderCulling.LOGGER.warn("No JavaScript engine available for scripting");
                return;
            }
            
            // 设置全局变量和函数
            setupGlobalContext();
            
            // 加载现有脚本
            loadAllScripts();
            
            initialized = true;
            EnclosedSpaceRenderCulling.LOGGER.info("Script manager initialized with engine: " + 
                scriptEngine.getClass().getSimpleName());
                
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Failed to initialize script manager", e);
        }
    }
    
    /**
     * 设置全局上下文
     */
    private static void setupGlobalContext() throws ScriptException {
        if (scriptEngine == null) return;
        
        // 添加全局函数和对象
        scriptEngine.put("logger", EnclosedSpaceRenderCulling.LOGGER);
        
        // 添加控制台对象
        String consoleScript = """
            var console = {
                log: function(msg) { logger.info('[Script] ' + msg); },
                warn: function(msg) { logger.warn('[Script] ' + msg); },
                error: function(msg) { logger.error('[Script] ' + msg); },
                debug: function(msg) { logger.debug('[Script] ' + msg); }
            };
            """;
        scriptEngine.eval(consoleScript);
        
        // 添加实用函数
        String utilityScript = """
            function distance(pos1, pos2) {
                var dx = pos1.getX() - pos2.getX();
                var dy = pos1.getY() - pos2.getY();
                var dz = pos1.getZ() - pos2.getZ();
                return Math.sqrt(dx*dx + dy*dy + dz*dz);
            }
            
            function distanceSquared(pos1, pos2) {
                var dx = pos1.getX() - pos2.getX();
                var dy = pos1.getY() - pos2.getY();
                var dz = pos1.getZ() - pos2.getZ();
                return dx*dx + dy*dy + dz*dz;
            }
            """;
        scriptEngine.eval(utilityScript);
    }
    
    /**
     * 加载所有脚本
     */
    private static void loadAllScripts() {
        Path scriptDir = Paths.get("scripts", "enclosed_culling");
        if (!Files.exists(scriptDir)) {
            return;
        }
        
        try {
            Files.walk(scriptDir)
                .filter(path -> path.toString().endsWith(".js"))
                .forEach(scriptFile -> {
                    try {
                        loadScript(scriptFile);
                    } catch (Exception e) {
                        EnclosedSpaceRenderCulling.LOGGER.error("Failed to load script: " + scriptFile, e);
                    }
                });
        } catch (IOException e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Failed to scan script directory", e);
        }
    }
    
    /**
     * 加载单个脚本文件
     */
    private static void loadScript(Path scriptFile) throws ScriptException, IOException {
        if (scriptEngine == null) return;
        
        String scriptName = scriptFile.getFileName().toString();
        
        try (FileReader reader = new FileReader(scriptFile.toFile())) {
            if (scriptEngine instanceof Compilable) {
                CompiledScript compiled = ((Compilable) scriptEngine).compile(reader);
                compiledScripts.put(scriptName, compiled);
                compiled.eval();
            } else {
                scriptEngine.eval(reader);
            }
            
            EnclosedSpaceRenderCulling.LOGGER.debug("Loaded script: " + scriptName);
        }
    }
    
    /**
     * 重新加载剔除规则脚本
     */
    public static void reloadCullingRules() {
        if (!initialized) return;
        
        Path scriptFile = Paths.get("scripts", "enclosed_culling", "culling_rules.js");
        if (Files.exists(scriptFile)) {
            try {
                loadScript(scriptFile);
                EnclosedSpaceRenderCulling.LOGGER.info("Reloaded culling rules script");
            } catch (Exception e) {
                EnclosedSpaceRenderCulling.LOGGER.error("Failed to reload culling rules script", e);
            }
        }
    }
    
    /**
     * 重新加载调试钩子脚本
     */
    public static void reloadDebugHooks() {
        if (!initialized) return;
        
        Path scriptFile = Paths.get("scripts", "enclosed_culling", "debug_hooks.js");
        if (Files.exists(scriptFile)) {
            try {
                loadScript(scriptFile);
                EnclosedSpaceRenderCulling.LOGGER.info("Reloaded debug hooks script");
            } catch (Exception e) {
                EnclosedSpaceRenderCulling.LOGGER.error("Failed to reload debug hooks script", e);
            }
        }
    }
    
    /**
     * 调用脚本函数 - 自定义剔除逻辑
     */
    public static Boolean callShouldCullBlock(BlockPos blockPos, BlockState blockState, BlockPos playerPos) {
        if (!initialized || scriptEngine == null) return null;
        
        try {
            // 尝试调用脚本中的shouldCullBlock函数
            if (scriptEngine instanceof Invocable) {
                Invocable invocable = (Invocable) scriptEngine;
                Object result = invocable.invokeFunction("shouldCullBlock", blockPos, blockState, playerPos);
                
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            }
        } catch (NoSuchMethodException e) {
            // 函数不存在，忽略
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Error calling shouldCullBlock script function", e);
        }
        
        return null; // 使用默认逻辑
    }
    
    /**
     * 调用脚本函数 - 自定义连通性判断
     */
    public static Boolean callIsConnectedSpace(BlockPos pos1, BlockPos pos2, Level level) {
        if (!initialized || scriptEngine == null) return null;
        
        try {
            if (scriptEngine instanceof Invocable) {
                Invocable invocable = (Invocable) scriptEngine;
                Object result = invocable.invokeFunction("isConnectedSpace", pos1, pos2, level);
                
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            }
        } catch (NoSuchMethodException e) {
            // 函数不存在，忽略
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Error calling isConnectedSpace script function", e);
        }
        
        return null; // 使用默认逻辑
    }
    
    /**
     * 调用脚本钩子 - 剔除检查前
     */
    public static void callBeforeCullingCheck(BlockPos blockPos, BlockPos playerPos) {
        if (!initialized || scriptEngine == null) return;
        
        try {
            if (scriptEngine instanceof Invocable) {
                Invocable invocable = (Invocable) scriptEngine;
                invocable.invokeFunction("onBeforeCullingCheck", blockPos, playerPos);
            }
        } catch (NoSuchMethodException e) {
            // 函数不存在，忽略
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Error calling onBeforeCullingCheck script hook", e);
        }
    }
    
    /**
     * 调用脚本钩子 - 剔除检查后
     */
    public static void callAfterCullingCheck(BlockPos blockPos, boolean result, String reason) {
        if (!initialized || scriptEngine == null) return;
        
        try {
            if (scriptEngine instanceof Invocable) {
                Invocable invocable = (Invocable) scriptEngine;
                invocable.invokeFunction("onAfterCullingCheck", blockPos, result, reason);
            }
        } catch (NoSuchMethodException e) {
            // 函数不存在，忽略
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Error calling onAfterCullingCheck script hook", e);
        }
    }
    
    /**
     * 调用脚本钩子 - 性能指标
     */
    public static void callPerformanceMetric(String metricName, double value) {
        if (!initialized || scriptEngine == null) return;
        
        try {
            if (scriptEngine instanceof Invocable) {
                Invocable invocable = (Invocable) scriptEngine;
                invocable.invokeFunction("onPerformanceMetric", metricName, value);
            }
        } catch (NoSuchMethodException e) {
            // 函数不存在，忽略
        } catch (Exception e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Error calling onPerformanceMetric script hook", e);
        }
    }
    
    /**
     * 执行任意脚本代码
     */
    public static Object evalScript(String script) {
        if (!initialized || scriptEngine == null) return null;
        
        try {
            return scriptEngine.eval(script);
        } catch (ScriptException e) {
            EnclosedSpaceRenderCulling.LOGGER.error("Error evaluating script", e);
            return null;
        }
    }
    
    /**
     * 获取脚本引擎状态
     */
    public static String getEngineInfo() {
        if (scriptEngine == null) {
            return "No script engine available";
        }
        
        StringBuilder info = new StringBuilder();
        info.append("Engine: ").append(scriptEngine.getClass().getSimpleName()).append("\n");
        info.append("Factory: ").append(scriptEngine.getFactory().getEngineName()).append("\n");
        info.append("Version: ").append(scriptEngine.getFactory().getEngineVersion()).append("\n");
        info.append("Language: ").append(scriptEngine.getFactory().getLanguageName()).append("\n");
        info.append("Compiled Scripts: ").append(compiledScripts.size()).append("\n");
        
        return info.toString();
    }
    
    /**
     * 清理脚本资源
     */
    public static void cleanup() {
        compiledScripts.clear();
        scriptEngine = null;
        initialized = false;
        EnclosedSpaceRenderCulling.LOGGER.info("Script manager cleaned up");
    }
    
    /**
     * 检查脚本管理器是否已初始化
     */
    public static boolean isInitialized() {
        return initialized && scriptEngine != null;
    }
}
