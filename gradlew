#!/bin/sh

#
# 版权所有 © 2015-2021 原作者。
#
# 根据 Apache License, Version 2.0（“许可证”）授权；
# 除非遵守许可证，否则您不得使用此文件。
# 您可以在以下地址获取许可证副本:
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# 除非适用法律要求或书面同意，否则根据许可证分发的软件
# 按“原样”分发，不附带任何明示或暗示的担保或条件。
# 有关许可证下权限和限制的具体语言，请参见许可证。
#

##############################################################################
#
#   由 Gradle 生成的 POSIX 启动脚本。
#
#   运行须知:
#
#   (1) 需要 POSIX 兼容的 shell 来运行此脚本。如果您的 /bin/sh
#       不兼容，但有其他兼容 shell（如 ksh 或 bash），则运行此脚本时，
#       在命令行前加上 shell 名称，例如:
#
#           ksh Gradle
#
#       Busybox 及类似的精简 shell 不可用，因为本脚本需要以下 POSIX shell 特性:
#         * 函数；
#         * 扩展 «$var», «${var}», «${var:-default}», «${var+SET}»,
#           «${var#prefix}», «${var%suffix}», 以及 «$( cmd )»；
#         * 具有可测试退出状态的复合命令，尤其是 «case»；
#         * 各种内建命令，包括 «command», «set», 和 «ulimit»。
#
#   补丁须知:
#
#   (2) 本脚本面向所有 POSIX shell，因此避免使用 Bash、Ksh 等扩展；
#       特别是避免使用数组。
#
#       “传统”做法是将多个参数打包为空格分隔的字符串，
#       这是众所周知的 bug 和安全隐患来源，因此本脚本（大多）避免此做法，
#       通过逐步累积选项到 "$@"，最终传递给 Java。
#
#       对于继承的环境变量（DEFAULT_JVM_OPTS、JAVA_OPTS、
#       和 GRADLE_OPTS）依赖于单词分割，脚本会显式执行；
#       详见内联注释。
#
#       针对特定操作系统如 AIX、CygWin、Darwin、MinGW 和 NonStop 有特殊处理。
#
#   (3) 本脚本由 Groovy 模板
#       https://github.com/gradle/gradle/blob/HEAD/subprojects/plugins/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt
#       在 Gradle 项目中生成。
#
#       Gradle 项目地址:https://github.com/gradle/gradle/
#
##############################################################################

# 尝试设置 APP_HOME

# 解析链接:$0 可能是一个链接
app_path=$0

# 需要此步骤以处理链式符号链接。
while
    APP_HOME=${app_path%"${app_path##*/}"}  # 保留结尾的 /；如果无前置路径则为空
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

# 通常未使用
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
APP_HOME=$( cd "${APP_HOME:-./}" && pwd -P ) || exit

# 使用最大可用值，或设置 MAX_FD != -1 以使用该值。
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# 操作系统相关支持（必须为 'true' 或 'false'）。
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in                #(
  CYGWIN* )         cygwin=true  ;; #(
  Darwin* )         darwin=true  ;; #(
  MSYS* | MINGW* )  msys=true    ;; #(
  NONSTOP* )        nonstop=true ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar


# 确定用于启动 JVM 的 Java 命令。
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM 的 AIX JDK 可执行文件位置特殊
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "错误:JAVA_HOME 设置为无效目录:$JAVA_HOME

请在环境变量中设置 JAVA_HOME，指向您的 Java 安装路径。"
    fi
else
    JAVACMD=java
    which java >/dev/null 2>&1 || die "错误:JAVA_HOME 未设置且 PATH 中找不到 'java' 命令。

请在环境变量中设置 JAVA_HOME，指向您的 Java 安装路径。"
fi

# 如���可以，提升最大文件描述符数。
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in #(
      max*)
        # 在 POSIX sh 中，ulimit -H 未定义。因此需检查结果是否有效。
        # shellcheck disable=SC3045
        MAX_FD=$( ulimit -H -n ) ||
            warn "无法查询最大文件描述符限制"
    esac
    case $MAX_FD in  #(
      '' | soft) :;; #(
      *)
        # 在 POSIX sh 中，ulimit -n 未定义。因此需检查结果是否有效。
        # shellcheck disable=SC3045
        ulimit -n "$MAX_FD" ||
            warn "无法将最大文件描述符限制设置为 $MAX_FD"
    esac
fi

# 收集所有 java 命令参数，逆序堆叠:
#   * 命令行参数
#   * 主类名
#   * -classpath
#   * -D...appname 设置
#   * --module-path（如需）
#   * DEFAULT_JVM_OPTS、JAVA_OPTS 和 GRADLE_OPTS 环境变量。

# 对于 Cygwin 或 MSYS，运行 java 前将路径转换为 Windows 格式
if "$cygwin" || "$msys" ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )
    CLASSPATH=$( cygpath --path --mixed "$CLASSPATH" )

    JAVACMD=$( cygpath --unix "$JAVACMD" )

    # 现在转换参数 - 为限制自己只用 /bin/sh 做特殊处理
    for arg do
        if
            case $arg in                                #(
              -*)   false ;;                            # 不处理选项 #(
              /?*)  t=${arg#/} t=/${t%%/*}              # 看起来像 POSIX 路径
                    [ -e "$t" ] ;;                      #(
              *)    false ;;
            esac
        then
            arg=$( cygpath --path --ignore --mixed "$arg" )
        fi
        # 将参数列表循环与参数数目相同的次数，
        # 这样每个参数都回到原位，但可能已被修改。
        #
        # 注意:`for` 循环在开始前捕获其迭代列表，
        # 所以这里改变位置参数不会影响迭代次数或 arg 的值。
        shift                   # 移除旧参数
        set -- "$@" "$arg"      # 添加替换参数
    done
fi


# 在此添加默认 JVM 选项。也可用 JAVA_OPTS 和 GRADLE_OPTS 传递 JVM 选项给本脚本。
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# 收集所有 java 命令参数；
#   * $DEFAULT_JVM_OPTS、$JAVA_OPTS 和 $GRADLE_OPTS 可能包含 shell 脚本片段，包括引号和变量替换，因此用双引号包裹以确保重新展开；
#   * 其他参数用单引号包裹，避免重新展开。

set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

# 如果 "xargs" 不可用则停止。
if ! command -v xargs >/dev/null 2>&1
then
    die "未找到 xargs"
fi

# 用 "xargs" 解析带引号的参数。
#
# -n1 使其每行输出一个参数，去除引号和反斜杠。
#
# 在 Bash 中可直接:
#
#   readarray ARGS < <( xargs -n1 <<<"$var" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# 但 POSIX shell 无数组和命令替换，因此
# 用 sed 处理每个参数（每行为 sed 输入），对可能为 shell 元字符的字符加反斜杠，
# 然后用 eval 反向处理（保持参数分隔），整体用单个 "set" 语句包裹。
#
# 若变量包含换行或不匹配的引号则会出错。
#

eval "set -- $(
        printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"
