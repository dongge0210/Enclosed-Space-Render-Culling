#!/bin/bash
# 版本号同步脚本 (Version Sync Script)
# 用于同步更新项目中所有文件的版本号

# 设置颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 获取当前版本号
current_version=$(grep "mod_version=" gradle.properties | cut -d'=' -f2)

echo -e "${GREEN}=== Enclosed Space Render Culling 版本同步工具 ===${NC}"
echo -e "${YELLOW}当前版本: $current_version${NC}"
echo ""

# 提示用户输入新版本号
echo "请输入新版本号 (当前: $current_version):"
echo "格式示例:"
echo "  - 正常新功能: 0.1.58"
echo "  - 小更新: 0.1.571"
echo "  - 大更新: 0.2.0"
echo "  - 错误修复: 0.1.57-E1"
echo "  - Bug修复: 0.1.57-B1"
echo "  - 混合修复: 0.1.57-E1/B1"
echo ""

read -p "新版本号: " new_version

# 验证版本号格式
if [[ ! $new_version =~ ^[0-9]+\.[0-9]+\.[0-9]+.*$ ]]; then
    echo -e "${RED}错误: 版本号格式不正确${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}准备更新版本号从 '$current_version' 到 '$new_version'${NC}"
echo ""

# 确认更新
read -p "确认更新? (y/N): " confirm
if [[ $confirm != [yY] ]]; then
    echo "操作已取消"
    exit 0
fi

echo ""
echo -e "${GREEN}开始更新版本号...${NC}"

# 更新 gradle.properties
echo "1. 更新 gradle.properties..."
if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]]; then
    # Windows (Git Bash)
    sed -i "s/mod_version=$current_version/mod_version=$new_version/g" gradle.properties
else
    # Linux/macOS
    sed -i '' "s/mod_version=$current_version/mod_version=$new_version/g" gradle.properties
fi

# 注意: ConfigScreen.java 现在会自动从版本属性文件读取版本号
echo "2. ConfigScreen.java 版本号将自动从属性文件读取，无需手动更新"

# 更新 mods.toml (如果存在)
mods_toml="src/main/resources/META-INF/mods.toml"
if [[ -f "$mods_toml" ]]; then
    echo "3. 更新 mods.toml..."
    if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]]; then
        sed -i "s/version=\"$current_version\"/version=\"$new_version\"/g" "$mods_toml"
    else
        sed -i '' "s/version=\"$current_version\"/version=\"$new_version\"/g" "$mods_toml"
    fi
    echo "   ✓ mods.toml 已更新"
else
    echo -e "   ${YELLOW}⚠ mods.toml 未找到，跳过${NC}"
fi

# 更新版本规范文档
echo "4. 更新 VERSION_SPECIFICATION.md..."
if [[ -f "VERSION_SPECIFICATION.md" ]]; then
    current_date=$(date +%Y-%m-%d)
    if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]]; then
        sed -i "s/#### 当前版本: \`.*\`/#### 当前版本: \`$new_version\`/g" VERSION_SPECIFICATION.md
        sed -i "s/- \*\*发布日期\*\*: .*/- **发布日期**: $current_date/g" VERSION_SPECIFICATION.md
    else
        sed -i '' "s/#### 当前版本: \`.*\`/#### 当前版本: \`$new_version\`/g" VERSION_SPECIFICATION.md
        sed -i '' "s/- \*\*发布日期\*\*: .*/- **发布日期**: $current_date/g" VERSION_SPECIFICATION.md
    fi
    echo "   ✓ VERSION_SPECIFICATION.md 已更新"
else
    echo -e "   ${YELLOW}⚠ VERSION_SPECIFICATION.md 未找到${NC}"
fi

echo ""
echo -e "${GREEN}✓ 版本号更新完成!${NC}"
echo -e "${YELLOW}新版本: $new_version${NC}"
echo ""
echo "建议接下来执行:"
echo "1. 检查修改的文件: git diff"
echo "2. 编译测试: gradlew compileJava"
echo "3. 提交更改: git add . && git commit -m \"Bump version to $new_version\""
echo ""
