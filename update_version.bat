@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: 版本号同步脚本 (Version Sync Script) - Windows版本
:: 用于同步更新项目中所有文件的版本号

echo === Enclosed Space Render Culling 版本同步工具 ===
echo.

:: 获取当前版本号
for /f "tokens=2 delims==" %%a in ('findstr "mod_version=" gradle.properties') do set current_version=%%a

echo 当前版本: %current_version%
echo.

:: 提示用户输入新版本号
echo 请输入新版本号:
echo 格式示例:
echo   - 正常新功能: 0.1.58
echo   - 小更新: 0.1.571
echo   - 大更新: 0.2.0
echo   - 错误修复: 0.1.57-E1
echo   - Bug修复: 0.1.57-B1
echo   - 混合修复: 0.1.57-E1B1
echo.

set /p new_version="新版本号: "

:: 验证版本号不为空
if "%new_version%"=="" (
    echo 错误: 版本号不能为空
    pause
    exit /b 1
)

echo.
echo 准备更新版本号从 '%current_version%' 到 '%new_version%'
echo.

set /p confirm="确认更新? (y/N): "
if /i not "%confirm%"=="y" (
    echo 操作已取消
    pause
    exit /b 0
)

echo.
echo 开始更新版本号...

:: 更新 gradle.properties
echo 1. 更新 gradle.properties...
powershell -Command "(Get-Content gradle.properties) -replace 'mod_version=%current_version%', 'mod_version=%new_version%' | Set-Content gradle.properties"

:: 注意: ConfigScreen.java 现在会自动从版本属性文件读取版本号，不需要手动更新
echo 2. ConfigScreen.java 版本号将自动从属性文件读取，无需手动更新

:: 更新 mods.toml (如果存在)
set mods_toml=src\main\resources\META-INF\mods.toml
if exist "%mods_toml%" (
    echo 3. 更新 mods.toml...
    powershell -Command "(Get-Content '%mods_toml%') -replace 'version=\"%current_version%\"', 'version=\"%new_version%\"' | Set-Content '%mods_toml%'"
    echo    ✓ mods.toml 已更新
) else (
    echo    ⚠ mods.toml 未找到，跳过
)

:: 更新版本规范文档
echo 4. 更新 VERSION_SPECIFICATION.md...
if exist "VERSION_SPECIFICATION.md" (
    :: 获取当前日期
    for /f "tokens=1-3 delims=/ " %%a in ('date /t') do (
        set current_date=%%c-%%a-%%b
    )
    powershell -Command "(Get-Content 'VERSION_SPECIFICATION.md') -replace '#### 当前版本: `.*`', '#### 当前版本: `%new_version%`' | Set-Content 'VERSION_SPECIFICATION.md'"
    powershell -Command "(Get-Content 'VERSION_SPECIFICATION.md') -replace '- \*\*发布日期\*\*: .*', '- **发布日期**: !current_date!' | Set-Content 'VERSION_SPECIFICATION.md'"
    echo    ✓ VERSION_SPECIFICATION.md 已更新
) else (
    echo    ⚠ VERSION_SPECIFICATION.md 未找到
)

echo.
echo ✓ 版本号更新完成!
echo 新版本: %new_version%
echo.
echo 建议接下来执行:
echo 1. 检查修改的文件: git diff
echo 2. 编译测试: gradlew.bat compileJava
echo 3. 提交更改: git add . ^&^& git commit -m "Bump version to %new_version%"
echo.

pause
