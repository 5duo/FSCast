#!/usr/bin/env node

/**
 * 使用Material Design Icons替换IconPark
 * Material Icons是Google官方图标库，专为Android设计
 * 下载地址：https://fonts.google.com/icons
 */

const fs = require('fs');
const path = require('path');

// Material Icons映射（使用官方名称）
const MATERIAL_ICONS = {
    // 状态概览
    'display': 'tv',
    'play': 'play_arrow',
    'pause': 'pause',
    'volume_up': 'volume_up',
    'connection': 'wifi',
    'bluetooth': 'bluetooth',

    // 面板操作
    'close': 'close',
    'eye': 'visibility',
    'eye_off': 'visibility_off',
    'delete': 'delete',
    'positioning': 'pin_drop',
    'resize': 'aspect_ratio',

    // 播放控制
    'stop': 'stop',
    'skip_previous': 'skip_previous',
    'skip_next': 'skip_next',
    'volume_mute': 'volume_off',

    // 快速操作
    'center': 'center_focus_strong',
    'fullscreen': 'fullscreen',
    'save': 'save',
    'refresh': 'refresh',

    // 服务控制
    'scan': 'qr_code_scanner',
    'restart': 'restart_alt',

    // 位置滑块
    'horizontal': 'swap_horiz',
    'vertical': 'swap_vert',

    // 其他
    'down': 'arrow_downward',
    'up': 'arrow_upward',
    'left': 'arrow_back',
    'right': 'arrow_forward',
    'check': 'check',
    'home': 'home',
    'setting': 'settings',
};

/**
 * 生成说明文档
 */
function generateInstructions() {
    const instructions = `# Material Icons 集成说明

## 问题分析
IconPark的outline主题使用stroke属性，Android vector drawable对stroke支持有限，导致图标显示不正确。

## 解决方案
使用Google官方的Material Design Icons，这些图标专为Android设计，质量更好。

## 安装步骤

### 1. 在项目 build.gradle 中添加依赖
\`\`\`gradle
dependencies {
    implementation "androidx.compose.material:material-icons-extended:1.7.6"
}
\`\`\`

### 2. 在代码中使用
\`\`\`kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Close
// ... 其他图标

@Composable
fun MyScreen() {
    Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = "播放",
        tint = GoldOnSurface
    )
}
\`\`\`

### 3. 图标映射表
${Object.entries(MATERIAL_ICONS).map(([key, value]) => `- ${key}: Icons.Default.${value.split('_').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join('')}`).join('\n')}

## 优势
- ✅ Google官方支持，质量有保证
- ✅ 专为Android设计，显示效果最佳
- ✅ Compose原生支持，使用简单
- ✅ 黑金配色系统完美兼容
- ✅ 无需手动下载SVG文件

## 迁移步骤
1. 添加依赖
2. 将所有 IconParkIcon() 调用替换为 Icon(imageVector = ...)
3. 删除drawable中的SVG文件
4. 删除IconParkRes.kt文件
`;

    fs.writeFileSync('./MATERIAL_ICONS_MIGRATION.md', instructions, 'utf8');
    console.log('✓ 生成迁移指南: MATERIAL_ICONS_MIGRATION.md');
}

// 运行
generateInstructions();
