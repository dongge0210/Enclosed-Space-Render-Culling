# Enclosed Space Render Culling - Development/Debug Features

This project now includes complete development/debug assistance and plugin/hot-reload functionality to help developers better debug and customize mod behavior.

## Debug Features

### Debug Commands
The mod provides rich debug commands that require OP permissions:

```
/enclosedculling reload           # Reload configuration files
/enclosedculling stats            # Show statistics
/enclosedculling clearcache       # Clear all caches
/enclosedculling highlight <id>   # Highlight specified room
/enclosedculling debug            # Toggle debug mode
/enclosedculling benchmark        # Run performance test
```

### Debug HUD
After enabling debug mode, the client will display real-time debug information:
- Player position
- Culling check count and success rate
- Average check time
- Room statistics
- Real-time performance metrics

### Performance Monitoring
- Automatically record performance data for culling checks
- Support custom performance metrics
- Provide detailed performance reports
- Customizable performance monitoring logic through scripts

## Hot Reload Features

### Configuration File Hot Reload
- Auto monitor configuration file changes
- Apply new configuration without restarting the game
- Support all Forge configuration formats

### Script System
Support JavaScript scripts to customize mod behavior:

#### Culling Rules Script (`scripts/enclosed_culling/culling_rules.js`)
```javascript
// Custom culling logic
function shouldCullBlock(blockPos, blockState, playerPos) {
    var blockType = blockState.getBlock().getDescriptionId();
    
    // Important blocks should never be culled
    if (blockType.includes("chest") || blockType.includes("furnace")) {
        return false;
    }
    
    // Decorative blocks at far distance can be culled
    var distance = blockPos.distSqr(playerPos);
    if (distance > 1024 && blockType.includes("flower")) {
        return true;
    }
    
    return null; // Use default logic
}

// Custom connectivity check
function isConnectedSpace(pos1, pos2, level) {
    // Custom logic
    return null; // Use default logic
}
```

#### Debug Hook Script (`scripts/enclosed_culling/debug_hooks.js`)
```javascript
// Hook before culling check
function onBeforeCullingCheck(blockPos, playerPos) {
    console.log("Checking culling for: " + blockPos);
}

// Hook after culling check
function onAfterCullingCheck(blockPos, result, reason) {
    if (result) {
        console.log("Culled: " + blockPos + " (" + reason + ")");
    }
}

// Performance metric hook
function onPerformanceMetric(metricName, value) {
    if (metricName === "culling_check_time" && value > 5.0) {
        console.warn("Slow culling check: " + value + "ms");
    }
}
```

### Auto File Monitoring
- Auto monitor script file changes
- Real-time reload of modified scripts
- Support multiple script files
- Configurable check interval

## Configuration Options

The following configuration items have been added to `config/enclosed_culling-common.toml`:

```toml
[culling]
    # Whether to enable AABB culling
    enableCulling = true
    # Culling distance (blocks)
    cullDistance = 32

[debug]
    # Whether to enable debug mode
    enableDebugMode = false
    # Whether to show debug HUD
    enableDebugHUD = false
    # Whether to enable performance logging
    enablePerformanceLogging = false

[hotreload]
    # Whether to enable hot reload
    enableHotReload = true
    # Whether to enable script support
    enableScriptSupport = true
    # File check interval (seconds)
    fileCheckInterval = 1

[performance]
    # Maximum culling checks per tick
    maxCullingChecksPerTick = 100
    # Culling check time limit (milliseconds)
    cullingCheckTimeLimit = 5.0
```

## Directory Structure

```
scripts/
└── enclosed_culling/
    ├── culling_rules.js     # Custom culling rules
    └── debug_hooks.js       # Debug hook scripts

config/
└── enclosed_culling-common.toml  # Mod configuration file
```

## Usage Guide

### 1. Enable Debug Mode
```
/enclosedculling debug
```
or set `enableDebugMode = true` in the configuration file

### 2. View Performance Statistics
```
/enclosedculling stats
```

### 3. Custom Culling Logic
1. Edit `scripts/enclosed_culling/culling_rules.js`
2. Save the file, script will auto-reload
3. Test new culling behavior in-game

### 4. Performance Tuning
1. Run benchmark: `/enclosedculling benchmark`
2. View performance metrics
3. Adjust configuration parameters based on results

### 5. Troubleshooting
1. Clear cache: `/enclosedculling clearcache`
2. Reload configuration: `/enclosedculling reload`
3. Check detailed information in log files

## API Documentation

### Script API
Global functions and objects available in scripts:

```javascript
// Console output
console.log(message)
console.warn(message)
console.error(message)
console.debug(message)

// Distance calculation
distance(pos1, pos2)        // Euclidean distance
distanceSquared(pos1, pos2)  // Squared distance (better performance)

// Logging
logger.info(message)
logger.warn(message)
logger.error(message)
logger.debug(message)
```

### Hook Functions
You can implement the following functions in scripts to customize behavior:

- `shouldCullBlock(blockPos, blockState, playerPos)` - Custom culling logic
- `isConnectedSpace(pos1, pos2, level)` - Custom connectivity check
- `onBeforeCullingCheck(blockPos, playerPos)` - Pre-culling check hook
- `onAfterCullingCheck(blockPos, result, reason)` - Post-culling check hook
- `onPerformanceMetric(metricName, value)` - Performance metric hook

## Performance Optimization Tips

1. **Reasonable Check Limits**: Adjust `maxCullingChecksPerTick` based on server performance
2. **Use Caching**: Enable caching mechanisms to reduce redundant calculations
3. **Script Optimization**: Keep script functions concise and efficient
4. **Monitor Performance**: Regularly check performance metrics to identify issues

## Developer Support

If you encounter problems or have feature requests:

1. Check log files for detailed error information
2. Use debug commands to collect relevant data
3. Submit issues on GitHub
4. Include configuration files and relevant logs

---

This powerful debug and hot-reload system allows you to:
- Debug culling logic in real-time
- Quickly test new configurations
- Customize culling behavior
- Monitor performance
- Seamless development and debugging experience
