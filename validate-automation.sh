#!/bin/bash
# 测试脚本：验证GitHub Actions配置
# Test Script: Validate GitHub Actions Configuration

echo "🔍 验证GitHub Actions配置 | Validating GitHub Actions Configuration"
echo "================================================"

# 检查必要文件是否存在
echo "📁 检查工作流文件 | Checking workflow files..."

if [ -f ".github/workflows/gradle.yml" ]; then
    echo "✅ gradle.yml 存在 | gradle.yml exists"
else
    echo "❌ gradle.yml 缺失 | gradle.yml missing"
    exit 1
fi

if [ -f ".github/workflows/release.yml" ]; then
    echo "✅ release.yml 存在 | release.yml exists"
else
    echo "❌ release.yml 缺失 | release.yml missing"
    exit 1
fi

# 检查配置文件
echo ""
echo "📋 检查配置文件 | Checking configuration files..."

if [ -f "gradle.properties" ]; then
    echo "✅ gradle.properties 存在 | gradle.properties exists"
    MOD_VERSION=$(grep 'mod_version=' gradle.properties | cut -d'=' -f2)
    echo "   版本号 | Version: $MOD_VERSION"
else
    echo "❌ gradle.properties 缺失 | gradle.properties missing"
fi

if [ -f "build.gradle" ]; then
    echo "✅ build.gradle 存在 | build.gradle exists"
else
    echo "❌ build.gradle 缺失 | build.gradle missing"
fi

# 验证YAML语法（如果有yq或python）
echo ""
echo "🔧 验证YAML语法 | Validating YAML syntax..."

if command -v python3 &> /dev/null; then
    python3 -c "
import yaml
import sys

files = ['.github/workflows/gradle.yml', '.github/workflows/release.yml']
for file in files:
    try:
        with open(file, 'r', encoding='utf-8') as f:
            yaml.safe_load(f)
        print(f'✅ {file} 语法正确 | {file} syntax valid')
    except Exception as e:
        print(f'❌ {file} 语法错误 | {file} syntax error: {e}')
        sys.exit(1)
"
else
    echo "⚠️  Python未安装，跳过YAML语法检查 | Python not installed, skipping YAML validation"
fi

# 检查Gradle配置
echo ""
echo "🏗️  检查Gradle配置 | Checking Gradle configuration..."

if [ -f "gradlew" ]; then
    echo "✅ Gradle Wrapper 存在 | Gradle Wrapper exists"
    if [ -x "gradlew" ]; then
        echo "✅ Gradle Wrapper 可执行 | Gradle Wrapper executable"
    else
        echo "⚠️  Gradle Wrapper 不可执行 | Gradle Wrapper not executable"
        chmod +x gradlew
        echo "✅ 已修复执行权限 | Fixed execution permissions"
    fi
else
    echo "❌ Gradle Wrapper 缺失 | Gradle Wrapper missing"
fi

# 输出配置摘要
echo ""
echo "📊 配置摘要 | Configuration Summary"
echo "================================================"
echo "构建工作流 | Build Workflow: gradle.yml"
echo "  - 触发条件 | Triggers: push to main, PR to main"
echo "  - 自动发布 | Auto Release: dev version on main push"
echo ""
echo "发布工作流 | Release Workflow: release.yml"
echo "  - 触发条件 | Triggers: weekly schedule, manual, workflow_dispatch"
echo "  - 发布类型 | Release Types: dev, beta, release, weekly"
echo "  - 清理策略 | Cleanup: aggressive cleanup enabled"
echo ""
echo "🎉 配置验证完成 | Configuration validation completed"
