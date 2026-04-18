#!/usr/bin/env node

/**
 * IconPark图标自动下载脚本（使用outline主题）
 * 使用@icon-park/svg包生成SVG图标并转换为Android XML格式
 */

const fs = require('fs');
const path = require('path');

// 需要下载的图标列表（使用outline主题）
const ICONS_TO_DOWNLOAD = [
    // 状态概览
    { name: 'Display', theme: 'outline' },
    { name: 'Play', theme: 'outline' },
    { name: 'PauseOne', theme: 'outline' },
    { name: 'VolumeUp', theme: 'outline' },
    { name: 'Connection', theme: 'outline' },

    // 面板操作
    { name: 'Close', theme: 'outline' },
    { name: 'Eye', theme: 'outline' },  // 使用eye替代eye-open
    { name: 'Delete', theme: 'outline' },
    { name: 'UserPositioning', theme: 'outline' },
    { name: 'ProportionalScaling', theme: 'outline' },

    // 播放控制
    { name: 'CloseOne', theme: 'outline' },  // 使用close-one替代stop
    { name: 'PlayCycle', theme: 'outline' },
    { name: 'PlayTwo', theme: 'outline' },
    { name: 'VolumeMute', theme: 'outline' },

    // 快速操作
    { name: 'AlignTextCenter', theme: 'outline' },
    { name: 'FullScreen', theme: 'outline' },
    { name: 'Save', theme: 'outline' },
    { name: 'Refresh', theme: 'outline' },

    // 服务控制
    { name: 'Scan', theme: 'outline' },

    // 位置滑块
    { name: 'AlignHorizontally', theme: 'outline' },
    { name: 'AlignVertically', theme: 'outline' },

    // 其他
    { name: 'Down', theme: 'outline' },
    { name: 'Up', theme: 'outline' },
    { name: 'Left', theme: 'outline' },
    { name: 'Right', theme: 'outline' },
    { name: 'ToTop', theme: 'outline' },
    { name: 'ToBottom', theme: 'outline' },
    { name: 'Check', theme: 'outline' },
    { name: 'Home', theme: 'outline' },
    { name: 'Setting', theme: 'outline' },
];

/**
 * 将SVG转换为Android XML
 */
function svgToAndroidXml(svgString, iconName) {
    // 移除XML声明和svg标签，只保留内容
    const contentMatch = svgString.match(/<svg[^>]*>([\s\S]*?)<\/svg>/);
    if (!contentMatch) {
        console.error(`无法解析SVG: ${iconName}`);
        return null;
    }

    const svgContent = contentMatch[1];

    // 提取viewBox
    const viewBoxMatch = svgString.match(/viewBox="([^"]+)"/);
    const viewBox = viewBoxMatch ? viewBoxMatch[1] : "0 0 1024 1024";

    // 提取所有path元素
    const pathMatches = svgContent.match(/<path[^>]*>/g);
    if (!pathMatches || pathMatches.length === 0) {
        console.error(`无法提取path数据: ${iconName}`);
        return null;
    }

    // 转换每个path为Android格式
    const pathsXml = pathMatches.map(pathTag => {
        // 提取d属性
        const dMatch = pathTag.match(/d="([^"]+)"/);
        if (!dMatch) return null;

        const pathData = dMatch[1];

        return `    <path
        android:fillColor="#E8E8E8"
        android:pathData="${pathData}"/>`;
    }).filter(p => p !== null).join('\n');

    return `<?xml version="1.0" encoding="utf-8"?>
<!--
IconPark ${iconName}图标（outline主题）
从 https://iconpark.oceanengine.com/ 自动生成
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="${viewBox.split(' ')[2]}"
    android:viewportHeight="${viewBox.split(' ')[3]}">
${pathsXml}
</vector>
`;
}

/**
 * 主函数：下载并转换图标
 */
async function downloadIcons() {
    console.log('开始下载IconPark outline图标...\n');

    // 动态导入@icon-park/svg
    let iconParkSvg;
    try {
        iconParkSvg = await import('@icon-park/svg');
    } catch (error) {
        console.error('无法导入@icon-park/svg包，请先安装：');
        console.error('npm install @icon-park/svg');
        process.exit(1);
    }

    const outputDir = './app/src/main/res/drawable';

    // 确保输出目录存在
    if (!fs.existsSync(outputDir)) {
        fs.mkdirSync(outputDir, { recursive: true });
    }

    let successCount = 0;
    let failCount = 0;

    // 下载每个图标
    for (const icon of ICONS_TO_DOWNLOAD) {
        try {
            console.log(`正在下载: ${icon.name} (${icon.theme})`);

            // 获取SVG字符串
            const iconFunc = iconParkSvg[icon.name];
            if (typeof iconFunc !== 'function') {
                console.error(`  ✗ 图标不存在: ${icon.name}`);
                failCount++;
                continue;
            }

            const svgString = iconFunc({ theme: icon.theme });

            // 转换为Android XML
            const androidXml = svgToAndroidXml(svgString, icon.name);

            if (!androidXml) {
                console.error(`  ✗ 转换失败: ${icon.name}`);
                failCount++;
                continue;
            }

            // 保存文件
            const fileName = `ic_${icon.name.replace(/([A-Z])/g, '_$1').toLowerCase().replace(/^_/, '')}.xml`;
            const filePath = path.join(outputDir, fileName);

            fs.writeFileSync(filePath, androidXml, 'utf8');
            console.log(`  ✓ 保存成功: ${fileName}`);
            successCount++;

        } catch (error) {
            console.error(`  ✗ 下载失败: ${icon.name}`, error.message);
            failCount++;
        }
    }

    console.log(`\n下载完成！`);
    console.log(`成功: ${successCount} 个`);
    console.log(`失败: ${failCount} 个`);
    console.log(`\n图标文件保存在: ${outputDir}`);
}

// 运行
downloadIcons().catch(console.error);
