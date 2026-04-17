package com.example.floatingscreencasting.ui.theme

import androidx.compose.ui.graphics.Color

// ==================== 现代渐变色方案 ====================

// 主色调 - 蓝紫色渐变
val PrimaryStart = Color(0xFF6366F1)  // Indigo 500
val PrimaryEnd = Color(0xFF8B5CF6)    // Violet 500
val Primary = Color(0xFF6366F1)        // 主色
val OnPrimary = Color(0xFFFFFFFF)      // 主色上的文字
val PrimaryContainer = Color(0xFFE0E7FF) // 主色容器
val OnPrimaryContainer = Color(0xFF1E1B4B) // 主色容器上的文字

// 次要色 - 粉紫色
val Secondary = Color(0xFFEC4899)      // Pink 500
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFFFD6E8)
val OnSecondaryContainer = Color(0xFF31111D)

// 背景色 - 更现代的深色背景（增强对比度，提升强光下可读性）
val Background = Color(0xFF0B1120)     // 更深的背景（Slate 950）
val OnBackground = Color(0xFFF8FAFC)   // 更亮的文字（Slate 50）
val Surface = Color(0xFF1E293B)        // Slate 800
val OnSurface = Color(0xFFF8FAFC)      // 更亮的表面文字
val SurfaceVariant = Color(0xFF334155) // Slate 700
val OnSurfaceVariant = Color(0xFFCBD5E1) // 更亮的次要文字（Slate 300）

// 状态色
val Success = Color(0xFF10B981)        // Emerald 500
val Warning = Color(0xFFF59E0B)        // Amber 500
val Error = Color(0xFFEF4444)          // Red 500
val Info = Color(0xFF3B82F6)           // Blue 500

// ==================== 卡片效果 ====================

// 卡片背景渐变
val CardGradientStart = Color(0xFF1E293B)
val CardGradientEnd = Color(0xFF0F172A)

// 卡片阴影色
val CardShadowColor = Color(0x1A000000)

// ==================== 特殊效果 ====================

// 玻璃态效果（半透明）
val GlassBackground = Color(0xE61E293B) // 90% 透明
val GlassBorder = Color(0x33FFFFFF)      // 20% 透明白色边框

// 发光效果
val GlowColor = Color(0x4D6366F1)        // 30% 透明的紫色

// ==================== iOS 颜色（保留兼容） ====================
val ios_background = Color(0xFFF2F2F7)
val ios_card_background = Color(0xFFFFFFFF)
val ios_text_primary = Color(0xFF000000)
val ios_text_secondary = Color(0xFF3C3C43)
val ios_text_tertiary = Color(0x993C3C43)
val ios_separator = Color(0xFFC6C6C8)
val ios_blue = Color(0xFF007AFF)
val ios_red = Color(0xFFFF3B30)
val ios_green = Color(0xFF34C759)
val ios_orange = Color(0xFFFF9500)
