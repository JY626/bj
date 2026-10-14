#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
生成 Rep 项目的官方高质量开源物料：
1. docs/screenshots/01_workout.png
2. docs/screenshots/02_rest_timer.png
3. docs/screenshots/03_analytics.png
4. docs/screenshots/04_calendar.png
5. docs/screenshots/05_body.png
6. docs/demo.gif
7. docs/social-preview.png (1200x630)

设计规范遵循 Rep 应用的实际实现：
- Apple OLED 黑 (#000000)
- Apple Gray 6 (#1C1C1E), Apple Gray 5 (#2C2C2E), Apple Gray 4 (#3A3A3C)
- Apple Fitness Green (#00E676)
- Apple Caution Orange (#FF9500)
- 100% 干净规范的演示数据，无任何真实个人隐私
"""

import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 400, 840
SCALE = 2
CANVAS_W, CANVAS_H = W * SCALE, H * SCALE

# 尝试加载字体
def get_font(size, bold=False, mono=False):
    scaled_size = int(size * SCALE)
    font_paths = []
    if mono:
        font_paths = [
            "/System/Library/Fonts/Menlo.ttc",
            "/System/Library/Fonts/Monaco.ttf",
            "/System/Library/Fonts/Courier.dfont"
        ]
    elif bold:
        font_paths = [
            "/System/Library/Fonts/STHeiti Medium.ttc",
            "/System/Library/Fonts/Helvetica.ttc",
            "/System/Library/Fonts/SFNS.ttf"
        ]
    else:
        font_paths = [
            "/System/Library/Fonts/STHeiti Light.ttc",
            "/System/Library/Fonts/Helvetica.ttc",
            "/System/Library/Fonts/SFNS.ttf"
        ]
    
    for p in font_paths:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, scaled_size)
            except Exception:
                continue
    return ImageFont.load_default()

# 颜色常量
C_BG = (0, 0, 0)
C_CARD = (28, 28, 30)
C_CARD_LIGHT = (44, 44, 46)
C_BORDER = (58, 58, 60)
C_GREEN = (0, 230, 118)
C_GREEN_BG = (0, 230, 118, 38)
C_ORANGE = (255, 149, 0)
C_ORANGE_BG = (255, 149, 0, 38)
C_TEXT_PRIMARY = (255, 255, 255)
C_TEXT_SEC = (142, 142, 147)
C_TEXT_TERT = (99, 99, 102)

def draw_device_chrome(draw, img):
    # 状态栏
    f_time = get_font(12, bold=True)
    f_sub = get_font(10)
    draw.text((32 * SCALE, 14 * SCALE), "09:41", font=f_time, fill=C_TEXT_PRIMARY)
    
    # 灵动岛 / 顶部药丸
    island_w, island_h = 100 * SCALE, 24 * SCALE
    island_x = (CANVAS_W - island_w) // 2
    draw.rounded_rectangle([island_x, 10 * SCALE, island_x + island_w, (10 * SCALE) + island_h], radius=12 * SCALE, fill=(20, 20, 20))
    # 摄像头镜头亮点
    draw.ellipse([island_x + island_w - 20 * SCALE, 18 * SCALE, island_x + island_w - 12 * SCALE, 26 * SCALE], fill=(10, 10, 30))

    # 右侧状态图标：信号、WiFi、电池
    bx = CANVAS_W - 55 * SCALE
    draw.rectangle([bx, 18 * SCALE, bx + 18 * SCALE, 26 * SCALE], outline=C_TEXT_SEC, width=SCALE)
    draw.rectangle([bx + 2 * SCALE, 20 * SCALE, bx + 14 * SCALE, 24 * SCALE], fill=C_GREEN)
    draw.rectangle([bx + 19 * SCALE, 21 * SCALE, bx + 21 * SCALE, 23 * SCALE], fill=C_TEXT_SEC)

def draw_bottom_bar(draw, active_index=0):
    y = CANVAS_H - 64 * SCALE
    draw.line([(0, y), (CANVAS_W, y)], fill=C_BORDER, width=1)
    draw.rectangle([(0, y), (CANVAS_W, CANVAS_H)], fill=(0, 0, 0))
    
    tabs = ["训练", "负荷", "日历", "动作库", "身体"]
    item_w = CANVAS_W // len(tabs)
    f_tab = get_font(10, bold=True)
    
    for i, t in enumerate(tabs):
        cx = i * item_w + item_w // 2
        is_active = (i == active_index)
        color = C_GREEN if is_active else C_TEXT_SEC
        
        # 简单图标占位
        icon_y = y + 14 * SCALE
        draw.ellipse([cx - 8 * SCALE, icon_y - 8 * SCALE, cx + 8 * SCALE, icon_y + 8 * SCALE], outline=color, width=SCALE)
        
        # 文字
        bbox = f_tab.getbbox(t)
        tw = bbox[2] - bbox[0]
        draw.text((cx - tw // 2, y + 28 * SCALE), t, font=f_tab, fill=color)

    # 底部横条
    bar_w = 120 * SCALE
    bar_x = (CANVAS_W - bar_w) // 2
    draw.rounded_rectangle([bar_x, CANVAS_H - 10 * SCALE, bar_x + bar_w, CANVAS_H - 6 * SCALE], radius=2 * SCALE, fill=C_TEXT_SEC)

# ----------------- 画面 1：训练主屏 -----------------
def generate_workout_screen(with_timer=False):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), C_BG)
    draw = ImageDraw.Draw(img)
    draw_device_chrome(draw, img)
    
    # 顶部标题栏
    f_date = get_font(11, bold=True)
    f_title = get_font(20, bold=True)
    f_btn = get_font(12, bold=True)
    
    draw.text((20 * SCALE, 50 * SCALE), "10月14日 星期三", font=f_date, fill=C_GREEN)
    draw.text((20 * SCALE, 68 * SCALE), "DAY 1 · 推日 (胸/肩/三头)", font=f_title, fill=C_TEXT_PRIMARY)
    
    # 右侧按钮：计划 & 完成
    draw.rounded_rectangle([250 * SCALE, 66 * SCALE, 310 * SCALE, 92 * SCALE], radius=13 * SCALE, fill=C_CARD_LIGHT)
    draw.text((264 * SCALE, 73 * SCALE), "计划", font=f_btn, fill=C_GREEN)
    
    draw.rounded_rectangle([320 * SCALE, 66 * SCALE, 380 * SCALE, 92 * SCALE], radius=13 * SCALE, fill=C_GREEN)
    draw.text((334 * SCALE, 73 * SCALE), "完成", font=f_btn, fill=(0, 0, 0))
    
    # 指标卡片 (累计容量 / 完成组数 / 用时)
    card_y = 106 * SCALE
    draw.rounded_rectangle([20 * SCALE, card_y, (W - 20) * SCALE, card_y + 58 * SCALE], radius=16 * SCALE, fill=C_CARD, outline=C_BORDER, width=1)
    
    f_stat_lbl = get_font(9)
    f_stat_val = get_font(15, bold=True, mono=True)
    
    # 累计容量
    draw.text((45 * SCALE, card_y + 10 * SCALE), "累计容量", font=f_stat_lbl, fill=C_TEXT_SEC)
    draw.text((45 * SCALE, card_y + 26 * SCALE), "3920 kg", font=f_stat_val, fill=C_TEXT_PRIMARY)
    
    # 完成组数
    draw.text((170 * SCALE, card_y + 10 * SCALE), "完成组数", font=f_stat_lbl, fill=C_TEXT_SEC)
    draw.text((170 * SCALE, card_y + 26 * SCALE), "12/15 组", font=f_stat_val, fill=C_GREEN)
    
    # 本次用时
    draw.text((295 * SCALE, card_y + 10 * SCALE), "本次用时", font=f_stat_lbl, fill=C_TEXT_SEC)
    draw.text((295 * SCALE, card_y + 26 * SCALE), "42:15", font=f_stat_val, fill=C_TEXT_PRIMARY)
    
    # 双重渐进建议条
    rec_y = card_y + 68 * SCALE
    draw.rounded_rectangle([20 * SCALE, rec_y, (W - 20) * SCALE, rec_y + 36 * SCALE], radius=12 * SCALE, fill=C_CARD, outline=C_BORDER, width=1)
    draw.text((32 * SCALE, rec_y + 10 * SCALE), "💡 卧推前次达标 60kg × 8次，建议首组冲击 62.5kg × 6次", font=get_font(11), fill=C_GREEN)
    
    # 动作 1：平板杠铃卧推
    ex_y = rec_y + 46 * SCALE
    draw.rounded_rectangle([20 * SCALE, ex_y, (W - 20) * SCALE, ex_y + 230 * SCALE], radius=18 * SCALE, fill=C_CARD, outline=C_BORDER, width=1)
    
    draw.text((35 * SCALE, ex_y + 14 * SCALE), "平板杠铃卧推", font=get_font(15, bold=True), fill=C_TEXT_PRIMARY)
    draw.text((35 * SCALE, ex_y + 34 * SCALE), "胸部 · 杠铃 · 前次基准: 60 kg × 8", font=get_font(10), fill=C_TEXT_SEC)
    
    # 表头
    th_y = ex_y + 54 * SCALE
    draw.text((38 * SCALE, th_y), "组别", font=get_font(10), fill=C_TEXT_TERT)
    draw.text((100 * SCALE, th_y), "前次基准", font=get_font(10), fill=C_TEXT_TERT)
    draw.text((195 * SCALE, th_y), "重量 kg", font=get_font(10), fill=C_TEXT_TERT)
    draw.text((275 * SCALE, th_y), "次数", font=get_font(10), fill=C_TEXT_TERT)
    draw.text((345 * SCALE, th_y), "状态", font=get_font(10), fill=C_TEXT_TERT)
    
    # 组数据
    sets_data = [
        ("W", "20 kg × 15", "20", "15", True, C_ORANGE),
        ("1", "50 kg × 10", "50", "10", True, C_GREEN),
        ("2", "60 kg × 8", "60", "8", True, C_GREEN),
        ("3", "60 kg × 8", "60", "7", False, C_TEXT_SEC)
    ]
    
    for row_idx, (stype, prev, wt, reps, done, tag_c) in enumerate(sets_data):
        sy = th_y + (16 + row_idx * 38) * SCALE
        # 组别 Badge
        draw.rounded_rectangle([36 * SCALE, sy + 4 * SCALE, 56 * SCALE, sy + 24 * SCALE], radius=6 * SCALE, fill=C_CARD_LIGHT)
        draw.text((41 * SCALE, sy + 6 * SCALE), stype, font=get_font(10, bold=True), fill=tag_c)
        
        draw.text((100 * SCALE, sy + 7 * SCALE), prev, font=get_font(10, mono=True), fill=C_TEXT_SEC)
        
        # 重量 & 次数输入框
        draw.rounded_rectangle([185 * SCALE, sy + 2 * SCALE, 245 * SCALE, sy + 26 * SCALE], radius=6 * SCALE, fill=C_CARD_LIGHT)
        draw.text((205 * SCALE, sy + 6 * SCALE), wt, font=get_font(12, bold=True, mono=True), fill=C_TEXT_PRIMARY)
        
        draw.rounded_rectangle([265 * SCALE, sy + 2 * SCALE, 315 * SCALE, sy + 26 * SCALE], radius=6 * SCALE, fill=C_CARD_LIGHT)
        draw.text((284 * SCALE, sy + 6 * SCALE), reps, font=get_font(12, bold=True, mono=True), fill=C_TEXT_PRIMARY)
        
        # 打勾圆形
        chk_x = 346 * SCALE
        chk_y = sy + 3 * SCALE
        if done:
            draw.ellipse([chk_x, chk_y, chk_x + 22 * SCALE, chk_y + 22 * SCALE], fill=C_GREEN)
            draw.text((chk_x + 5 * SCALE, chk_y + 2 * SCALE), "✓", font=get_font(12, bold=True), fill=(0, 0, 0))
        else:
            draw.ellipse([chk_x, chk_y, chk_x + 22 * SCALE, chk_y + 22 * SCALE], outline=C_TEXT_TERT, width=2 * SCALE)

    # 动作 2：上斜哑铃卧推（部分显示）
    ex2_y = ex_y + 242 * SCALE
    draw.rounded_rectangle([20 * SCALE, ex2_y, (W - 20) * SCALE, ex2_y + 120 * SCALE], radius=18 * SCALE, fill=C_CARD, outline=C_BORDER, width=1)
    draw.text((35 * SCALE, ex2_y + 14 * SCALE), "上斜哑铃卧推", font=get_font(15, bold=True), fill=C_TEXT_PRIMARY)
    draw.text((35 * SCALE, ex2_y + 34 * SCALE), "上胸 · 哑铃 · 前次: 22.5 kg × 10", font=get_font(10), fill=C_TEXT_SEC)

    # 悬浮休息胶囊 (如果开启)
    if with_timer:
        cap_w, cap_h = 320 * SCALE, 54 * SCALE
        cap_x = (CANVAS_W - cap_w) // 2
        cap_y = CANVAS_H - 130 * SCALE
        
        # 阴影层
        draw.rounded_rectangle([cap_x - 2 * SCALE, cap_y - 2 * SCALE, cap_x + cap_w + 2 * SCALE, cap_y + cap_h + 2 * SCALE], radius=28 * SCALE, fill=(0, 0, 0, 160))
        draw.rounded_rectangle([cap_x, cap_y, cap_x + cap_w, cap_y + cap_h], radius=27 * SCALE, fill=(24, 24, 26), outline=C_GREEN, width=2 * SCALE)
        
        # 倒计时环形
        rx, ry = cap_x + 28 * SCALE, cap_y + 27 * SCALE
        draw.ellipse([rx - 16 * SCALE, ry - 16 * SCALE, rx + 16 * SCALE, ry + 16 * SCALE], outline=C_BORDER, width=3 * SCALE)
        draw.arc([rx - 16 * SCALE, ry - 16 * SCALE, rx + 16 * SCALE, ry + 16 * SCALE], start=-90, end=140, fill=C_GREEN, width=3 * SCALE)
        draw.text((rx - 7 * SCALE, ry - 8 * SCALE), "⏱", font=get_font(11), fill=C_GREEN)
        
        draw.text((cap_x + 58 * SCALE, cap_y + 11 * SCALE), "组间休息", font=get_font(10), fill=C_TEXT_SEC)
        draw.text((cap_x + 58 * SCALE, cap_y + 25 * SCALE), "01:24", font=get_font(18, bold=True, mono=True), fill=C_GREEN)
        
        # +30s 按钮
        draw.rounded_rectangle([cap_x + 185 * SCALE, cap_y + 12 * SCALE, cap_x + 245 * SCALE, cap_y + 42 * SCALE], radius=15 * SCALE, fill=C_CARD_LIGHT)
        draw.text((cap_x + 197 * SCALE, cap_y + 19 * SCALE), "+30s", font=get_font(11, bold=True), fill=C_TEXT_PRIMARY)
        
        # 跳过按钮
        draw.rounded_rectangle([cap_x + 255 * SCALE, cap_y + 12 * SCALE, cap_x + 305 * SCALE, cap_y + 42 * SCALE], radius=15 * SCALE, fill=C_CARD_LIGHT)
        draw.text((cap_x + 269 * SCALE, cap_y + 19 * SCALE), "跳过", font=get_font(11), fill=C_TEXT_SEC)

    draw_bottom_bar(draw, active_index=0)
    return img

# ----------------- 画面 3：渐进负荷看板 -----------------
def generate_analytics_screen():
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), C_BG)
    draw = ImageDraw.Draw(img)
    draw_device_chrome(draw, img)
    
    # 顶部
    draw.text((20 * SCALE, 50 * SCALE), "PROGRESSIVE OVERLOAD", font=get_font(11, bold=True), fill=C_TEXT_SEC)
    draw.text((20 * SCALE, 68 * SCALE), "负荷与周期回顾", font=get_font(20, bold=True), fill=C_TEXT_PRIMARY)
    
    # 周期切换 Chips [本周] [本月] [近8周]
    chips = ["本周", "本月", "近8周"]
    cx = 20 * SCALE
    for i, c in enumerate(chips):
        is_sel = (i == 0)
        cw = 70 * SCALE
        draw.rounded_rectangle([cx, 102 * SCALE, cx + cw, 128 * SCALE], radius=13 * SCALE, fill=C_TEXT_PRIMARY if is_sel else C_CARD)
        draw.text((cx + 20 * SCALE, 108 * SCALE), c, font=get_font(11, bold=True), fill=(0, 0, 0) if is_sel else C_TEXT_SEC)
        cx += cw + 10 * SCALE

    # E1RM 力量曲线卡片
    chart_y = 142 * SCALE
    draw.rounded_rectangle([20 * SCALE, chart_y, (W - 20) * SCALE, chart_y + 260 * SCALE], radius=18 * SCALE, fill=C_CARD, outline=C_BORDER, width=1)
    
    draw.text((36 * SCALE, chart_y + 16 * SCALE), "平板杠铃卧推 · 力量突破曲线", font=get_font(14, bold=True), fill=C_TEXT_PRIMARY)
    draw.text((36 * SCALE, chart_y + 36 * SCALE), "估算 1RM 极限", font=get_font(10), fill=C_TEXT_SEC)
    draw.text((36 * SCALE, chart_y + 50 * SCALE), "76.0 kg", font=get_font(22, bold=True, mono=True), fill=C_GREEN)
    draw.text((130 * SCALE, chart_y + 56 * SCALE), "+16.9% 本周期", font=get_font(11, bold=True), fill=C_GREEN)

    # 绘制折线图
    pts = [
        (60 * SCALE, chart_y + 210 * SCALE, "S1", "65.0"),
        (130 * SCALE, chart_y + 185 * SCALE, "S2", "68.0"),
        (200 * SCALE, chart_y + 190 * SCALE, "S3", "67.5"),
        (270 * SCALE, chart_y + 155 * SCALE, "S4", "72.0"),
        (340 * SCALE, chart_y + 120 * SCALE, "S5", "76.0")  # PR
    ]
    
    # 辅助网格虚线
    for gy in [chart_y + 120 * SCALE, chart_y + 160 * SCALE, chart_y + 200 * SCALE]:
        draw.line([(45 * SCALE, gy), (355 * SCALE, gy)], fill=(40, 40, 42), width=1)
    
    # 连接折线
    for k in range(len(pts) - 1):
        draw.line([(pts[k][0], pts[k][1]), (pts[k+1][0], pts[k+1][1])], fill=C_GREEN, width=3 * SCALE)
        
    for x, y, lbl, val in pts:
        is_pr = (lbl == "S5")
        if is_pr:
            # PR 突破光晕
            draw.ellipse([x - 9 * SCALE, y - 9 * SCALE, x + 9 * SCALE, y + 9 * SCALE], fill=C_GREEN_BG)
            draw.ellipse([x - 5 * SCALE, y - 5 * SCALE, x + 5 * SCALE, y + 5 * SCALE], fill=C_GREEN)
            draw.rounded_rectangle([x - 18 * SCALE, y - 28 * SCALE, x + 18 * SCALE, y - 10 * SCALE], radius=5 * SCALE, fill=C_GREEN)
            draw.text((x - 12 * SCALE, y - 26 * SCALE), "PR", font=get_font(9, bold=True), fill=(0, 0, 0))
        else:
            draw.ellipse([x - 4 * SCALE, y - 4 * SCALE, x + 4 * SCALE, y + 4 * SCALE], fill=C_TEXT_PRIMARY)
        draw.text((x - 10 * SCALE, chart_y + 225 * SCALE), lbl, font=get_font(10, mono=True), fill=C_TEXT_SEC)

    # 科学容量 RP 监控 (MEV / MAV / MRV)
    rp_y = chart_y + 272 * SCALE
    draw.rounded_rectangle([20 * SCALE, rp_y, (W - 20) * SCALE, rp_y + 250 * SCALE], radius=18 * SCALE, fill=C_CARD, outline=C_BORDER, width=1)
    
    draw.text((36 * SCALE, rp_y + 16 * SCALE), "肌群周容量监控 (RP 阈值)", font=get_font(14, bold=True), fill=C_TEXT_PRIMARY)
    draw.text((36 * SCALE, rp_y + 34 * SCALE), "本周有效组数对照维持(MEV)与适应量(MAV)", font=get_font(10), fill=C_TEXT_SEC)
    
    muscles = [
        ("胸部", 14, 20, "MAV 最佳适应", C_GREEN),
        ("背部", 16, 22, "MAV 最佳适应", C_GREEN),
        ("腿部", 6, 18, "低于 MEV 维持量", C_ORANGE),
        ("肩部", 12, 16, "MAV 最佳适应", C_GREEN)
    ]
    
    for midx, (mname, csets, tsets, stat_txt, bar_c) in enumerate(muscles):
        my = rp_y + (56 + midx * 44) * SCALE
        draw.text((36 * SCALE, my), mname, font=get_font(12, bold=True), fill=C_TEXT_PRIMARY)
        draw.text((80 * SCALE, my), f"{csets} 组 · {stat_txt}", font=get_font(10), fill=bar_c)
        
        # 进度条底槽
        bar_bg_y = my + 18 * SCALE
        draw.rounded_rectangle([36 * SCALE, bar_bg_y, 360 * SCALE, bar_bg_y + 8 * SCALE], radius=4 * SCALE, fill=C_CARD_LIGHT)
        fill_w = int((360 - 36) * SCALE * (csets / tsets))
        draw.rounded_rectangle([36 * SCALE, bar_bg_y, 36 * SCALE + fill_w, bar_bg_y + 8 * SCALE], radius=4 * SCALE, fill=bar_c)

    draw_bottom_bar(draw, active_index=1)
    return img

# ----------------- 画面 4：日历打卡与设置 -----------------
def generate_calendar_screen():
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), C_BG)
    draw = ImageDraw.Draw(img)
    draw_device_chrome(draw, img)
    
    draw.text((20 * SCALE, 50 * SCALE), "TRAINING LOG", font=get_font(11, bold=True), fill=C_TEXT_SEC)
    draw.text((20 * SCALE, 68 * SCALE), "每日记录与打卡", font=get_font(20, bold=True), fill=C_TEXT_PRIMARY)
    
    # 习惯 Banner (🔥 连续 14 天)
    ban_y = 104 * SCALE
    draw.rounded_rectangle([20 * SCALE, ban_y, (W - 20) * SCALE, ban_y + 60 * SCALE], radius=18 * SCALE, fill=C_CARD, outline=C_BORDER, width=1)
    draw.text((36 * SCALE, ban_y + 12 * SCALE), "🔥 连续 14 天", font=get_font(16, bold=True, mono=True), fill=C_GREEN)
    draw.text((36 * SCALE, ban_y + 34 * SCALE), "最长连续 28 天 · 状态稳健", font=get_font(10), fill=C_TEXT_SEC)
    
    draw.rounded_rectangle([260 * SCALE, ban_y + 14 * SCALE, 360 * SCALE, ban_y + 46 * SCALE], radius=16 * SCALE, fill=C_GREEN_BG)
    draw.text((276 * SCALE, ban_y + 22 * SCALE), "本周还剩 1 练", font=get_font(11, bold=True), fill=C_GREEN)
    
    # 月历卡片
    cal_y = ban_y + 72 * SCALE
    draw.rounded_rectangle([20 * SCALE, cal_y, (W - 20) * SCALE, cal_y + 270 * SCALE], radius=18 * SCALE, fill=C_CARD, outline=C_BORDER, width=1)
    
    draw.text((36 * SCALE, cal_y + 16 * SCALE), "2026 年 10 月", font=get_font(14, bold=True), fill=C_TEXT_PRIMARY)
    draw.text((280 * SCALE, cal_y + 18 * SCALE), "打卡 16 天", font=get_font(11), fill=C_GREEN)
    
    # 星期行
    weeks = ["一", "二", "三", "四", "五", "六", "日"]
    cell_w = (W - 40) // 7
    for widx, wname in enumerate(weeks):
        draw.text(((26 + widx * cell_w) * SCALE, cal_y + 44 * SCALE), wname, font=get_font(11), fill=C_TEXT_TERT)
        
    # 日期格子（演示数据排布）
    trained_days = {
        1: ("推", C_GREEN), 2: ("拉", (0, 180, 255)), 4: ("腿", C_ORANGE),
        6: ("推", C_GREEN), 7: ("拉", (0, 180, 255)), 9: ("腿", C_ORANGE),
        11: ("推", C_GREEN), 12: ("拉", (0, 180, 255)), 14: ("推", C_GREEN)
    }
    
    day_num = 1
    for r in range(4):
        for c in range(7):
            if day_num <= 28:
                dx = (26 + c * cell_w) * SCALE
                dy = (cal_y + 70 + r * 46) * SCALE
                is_today = (day_num == 14)
                
                if is_today:
                    draw.ellipse([dx - 4 * SCALE, dy - 4 * SCALE, dx + 24 * SCALE, dy + 24 * SCALE], outline=C_GREEN, width=2 * SCALE)
                    
                draw.text((dx + 2 * SCALE, dy), f"{day_num:02d}", font=get_font(11, mono=True), fill=C_TEXT_PRIMARY if day_num <= 14 else C_TEXT_TERT)
                
                if day_num in trained_days:
                    tlabel, tcolor = trained_days[day_num]
                    draw.rounded_rectangle([dx - 1 * SCALE, dy + 18 * SCALE, dx + 21 * SCALE, dy + 32 * SCALE], radius=4 * SCALE, fill=tcolor)
                    draw.text((dx + 3 * SCALE, dy + 19 * SCALE), tlabel, font=get_font(8, bold=True), fill=(0, 0, 0))
                day_num += 1

    # Health Connect 自动同步卡片
    hc_y = cal_y + 282 * SCALE
    draw.rounded_rectangle([20 * SCALE, hc_y, (W - 20) * SCALE, hc_y + 116 * SCALE], radius=18 * SCALE, fill=C_CARD, outline=C_BORDER, width=1)
    
    draw.text((36 * SCALE, hc_y + 16 * SCALE), "Health Connect 自动同步", font=get_font(13, bold=True), fill=C_TEXT_PRIMARY)
    draw.text((36 * SCALE, hc_y + 36 * SCALE), "归档时自动将力量训练、体重、体脂率写入系统健康底座，", font=get_font(10), fill=C_TEXT_SEC)
    draw.text((36 * SCALE, hc_y + 50 * SCALE), "供外部 AI 教练（如 Muse）后台无感读取。", font=get_font(10), fill=C_TEXT_SEC)
    
    # 绿色 Switch 开关 (Checked)
    sw_x, sw_y = 320 * SCALE, hc_y + 16 * SCALE
    draw.rounded_rectangle([sw_x, sw_y, sw_x + 44 * SCALE, sw_y + 26 * SCALE], radius=13 * SCALE, fill=C_GREEN)
    draw.ellipse([sw_x + 20 * SCALE, sw_y + 3 * SCALE, sw_x + 40 * SCALE, sw_y + 23 * SCALE], fill=(0, 0, 0))
    
    # 隐私说明
    draw.rounded_rectangle([32 * SCALE, hc_y + 72 * SCALE, 368 * SCALE, hc_y + 102 * SCALE], radius=8 * SCALE, fill=C_CARD_LIGHT)
    draw.text((42 * SCALE, hc_y + 80 * SCALE), "🔒 纯本地零服务器中转 · 100% 隐私安全保障", font=get_font(9), fill=C_GREEN)

    draw_bottom_bar(draw, active_index=2)
    return img

# ----------------- 画面 5：身体指标趋势 -----------------
def generate_body_screen():
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), C_BG)
    draw = ImageDraw.Draw(img)
    draw_device_chrome(draw, img)
    
    draw.text((20 * SCALE, 50 * SCALE), "BODY METRICS", font=get_font(11, bold=True), fill=C_TEXT_SEC)
    draw.text((20 * SCALE, 68 * SCALE), "身体指标与形态", font=get_font(20, bold=True), fill=C_TEXT_PRIMARY)
    
    # 当前核心指标卡片 (体重 / 体脂率 / BMI)
    top_y = 104 * SCALE
    draw.rounded_rectangle([20 * SCALE, top_y, (W - 20) * SCALE, top_y + 90 * SCALE], radius=18 * SCALE, fill=C_CARD, outline=C_BORDER, width=1)
    
    # 体重
    draw.text((40 * SCALE, top_y + 14 * SCALE), "体重", font=get_font(11), fill=C_TEXT_SEC)
    draw.text((40 * SCALE, top_y + 32 * SCALE), "75.2", font=get_font(24, bold=True, mono=True), fill=C_GREEN)
    draw.text((105 * SCALE, top_y + 44 * SCALE), "kg", font=get_font(12), fill=C_TEXT_SEC)
    draw.text((40 * SCALE, top_y + 66 * SCALE), "较上次 -0.3 kg", font=get_font(9), fill=C_GREEN)
    
    # 体脂率
    draw.text((165 * SCALE, top_y + 14 * SCALE), "体脂率", font=get_font(11), fill=C_TEXT_SEC)
    draw.text((165 * SCALE, top_y + 32 * SCALE), "15.8", font=get_font(24, bold=True, mono=True), fill=C_TEXT_PRIMARY)
    draw.text((230 * SCALE, top_y + 44 * SCALE), "%", font=get_font(12), fill=C_TEXT_SEC)
    draw.text((165 * SCALE, top_y + 66 * SCALE), "健康薄肌区间", font=get_font(9), fill=C_GREEN)
    
    # BMI
    draw.text((285 * SCALE, top_y + 14 * SCALE), "BMI 指数", font=get_font(11), fill=C_TEXT_SEC)
    draw.text((285 * SCALE, top_y + 32 * SCALE), "22.4", font=get_font(24, bold=True, mono=True), fill=C_TEXT_PRIMARY)
    draw.text((285 * SCALE, top_y + 66 * SCALE), "标准身材", font=get_font(9), fill=C_TEXT_SEC)
    
    # 体重 30 天趋势折线卡片
    tr_y = top_y + 104 * SCALE
    draw.rounded_rectangle([20 * SCALE, tr_y, (W - 20) * SCALE, tr_y + 240 * SCALE], radius=18 * SCALE, fill=C_CARD, outline=C_BORDER, width=1)
    
    draw.text((36 * SCALE, tr_y + 16 * SCALE), "体重 30 天变化趋势", font=get_font(14, bold=True), fill=C_TEXT_PRIMARY)
    draw.text((36 * SCALE, tr_y + 34 * SCALE), "稳步减脂期 · 阶段下降 1.8 kg", font=get_font(10), fill=C_GREEN)
    
    # 趋势线
    bpts = [
        (60 * SCALE, tr_y + 90 * SCALE, "09-14", "77.0"),
        (130 * SCALE, tr_y + 115 * SCALE, "09-21", "76.4"),
        (200 * SCALE, tr_y + 130 * SCALE, "09-28", "76.0"),
        (270 * SCALE, tr_y + 150 * SCALE, "10-07", "75.5"),
        (340 * SCALE, tr_y + 165 * SCALE, "10-14", "75.2")
    ]
    
    for k in range(len(bpts) - 1):
        draw.line([(bpts[k][0], bpts[k][1]), (bpts[k+1][0], bpts[k+1][1])], fill=C_GREEN, width=3 * SCALE)
        
    for bx, by, blbl, bval in bpts:
        draw.ellipse([bx - 4 * SCALE, by - 4 * SCALE, bx + 4 * SCALE, by + 4 * SCALE], fill=C_GREEN)
        draw.text((bx - 12 * SCALE, by - 18 * SCALE), bval, font=get_font(9, mono=True), fill=C_TEXT_PRIMARY)
        draw.text((bx - 14 * SCALE, tr_y + 205 * SCALE), blbl, font=get_font(9, mono=True), fill=C_TEXT_SEC)

    # 围度数据列表卡片
    cir_y = tr_y + 252 * SCALE
    draw.rounded_rectangle([20 * SCALE, cir_y, (W - 20) * SCALE, cir_y + 130 * SCALE], radius=18 * SCALE, fill=C_CARD, outline=C_BORDER, width=1)
    
    draw.text((36 * SCALE, cir_y + 16 * SCALE), "围度测量记录", font=get_font(14, bold=True), fill=C_TEXT_PRIMARY)
    
    circs = [
        ("胸围", "102.0 cm", "+1.5 cm"),
        ("腰围", "78.0 cm", "-2.0 cm"),
        ("臂围", "37.5 cm", "+0.8 cm")
    ]
    for cidx, (cname, cval, cdiff) in enumerate(circs):
        cy = cir_y + (46 + cidx * 26) * SCALE
        draw.text((36 * SCALE, cy), cname, font=get_font(11), fill=C_TEXT_SEC)
        draw.text((160 * SCALE, cy), cval, font=get_font(12, bold=True, mono=True), fill=C_TEXT_PRIMARY)
        draw.text((280 * SCALE, cy), cdiff, font=get_font(11, bold=True), fill=C_GREEN)

    draw_bottom_bar(draw, active_index=4)
    return img

# ----------------- 生成 Demo GIF -----------------
def generate_demo_gif():
    print("Generating demo.gif frames...")
    # 基于前面界面制作动态操作流程帧
    frames = []
    
    # 状态 A：未打勾
    base_uncompleted = generate_workout_screen(with_timer=False)
    
    # 状态 B：打勾瞬间
    base_completed = generate_workout_screen(with_timer=False)
    
    # 状态 C：弹出休息计时胶囊
    base_timer = generate_workout_screen(with_timer=True)
    
    # 制作缩放至 400x840 的 GIF 帧（控制文件体积）
    def prep_frame(img_rgba):
        return img_rgba.resize((400, 840), Image.Resampling.LANCZOS).convert("P", palette=Image.Palette.ADAPTIVE, colors=128)

    # 帧 1: 浏览动作 (未打勾)
    for _ in range(5):
        frames.append(prep_frame(base_uncompleted))
        
    # 帧 2: 打勾完成
    for _ in range(3):
        frames.append(prep_frame(base_completed))
        
    # 帧 3: 悬浮休息计时浮现走秒
    for _ in range(8):
        frames.append(prep_frame(base_timer))
        
    # 帧 4: 切换到负荷看板 E1RM PR
    screen_analytics = generate_analytics_screen()
    for _ in range(6):
        frames.append(prep_frame(screen_analytics))
        
    # 帧 5: 切换到每日日历与 Health Connect 同步
    screen_cal = generate_calendar_screen()
    for _ in range(6):
        frames.append(prep_frame(screen_cal))

    gif_path = "docs/demo.gif"
    frames[0].save(
        gif_path,
        save_all=True,
        append_images=frames[1:],
        duration=400,
        loop=0,
        optimize=True
    )
    print(f"Saved: {gif_path} ({os.path.getsize(gif_path) // 1024} KB)")

# ----------------- 生成 Social Preview (1200x630) -----------------
def generate_social_preview():
    print("Generating social-preview.png...")
    SP_W, SP_H = 1200, 630
    sp = Image.new("RGBA", (SP_W, SP_H), (10, 10, 12))
    draw = ImageDraw.Draw(sp)
    
    # 背景绿色微光光晕 (极简 Apple 质感)
    for rad in range(300, 50, -20):
        alpha = int((300 - rad) * 0.08)
        draw.ellipse([850 - rad, 300 - rad, 850 + rad, 300 + rad], fill=(0, 230, 118, alpha))
        
    # 左侧文案
    # Logo 徽章
    draw.rounded_rectangle([80, 110, 150, 180], radius=20, fill=C_GREEN)
    draw.text((95, 122), "R", font=get_font(26, bold=True), fill=(0, 0, 0))
    
    # 标题 Rep
    draw.text((170, 114), "Rep", font=get_font(34, bold=True), fill=C_TEXT_PRIMARY)
    
    # 一句话卖点
    draw.text((80, 205), "极简渐进式负荷健身记录 App", font=get_font(22, bold=True), fill=C_GREEN)
    draw.text((80, 255), "专为追求力量增长与科学训练设计的 100% 离线单机神器。", font=get_font(13), fill=C_TEXT_SEC)
    draw.text((80, 285), "Apple OLED 极简黑曜石设计 · 零键盘极速记录 · 双重渐进算法", font=get_font(13), fill=C_TEXT_SEC)
    draw.text((80, 315), "无缝自动同步 Android Health Connect，赋能 AI 私教生态", font=get_font(13), fill=C_TEXT_SEC)

    # 4 个特性药丸
    pills = [
        "100% 离线优先",
        "E1RM 极限拟合",
        "RP 科学周容量",
        "Health Connect"
    ]
    px, py = 80, 380
    for p in pills:
        draw.rounded_rectangle([px, py, px + 125, py + 34], radius=10, fill=C_CARD, outline=C_BORDER, width=1)
        draw.text((px + 14, py + 8), p, font=get_font(10, bold=True), fill=C_TEXT_PRIMARY)
        px += 135

    # 开源与技术栈
    draw.text((80, 480), "Kotlin 2.0  ·  Jetpack Compose M3  ·  Room  ·  MIT License", font=get_font(11, mono=True), fill=C_TEXT_TERT)

    # 右侧：两部手机屏幕倾斜叠加拼贴效果
    # 手机 1: 训练主屏
    s1 = generate_workout_screen(with_timer=True).resize((220, 462), Image.Resampling.LANCZOS)
    # 手机 2: 渐进负荷看板
    s2 = generate_analytics_screen().resize((220, 462), Image.Resampling.LANCZOS)
    
    # 添加圆角边框外壳
    mask = Image.new("L", (220, 462), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, 220, 462], radius=24, fill=255)
    
    # 粘贴手机 1 (左后)
    sp.paste(s1, (660, 95), mask)
    # 粘贴手机 2 (右前，带微弱阴影)
    sp.paste(s2, (880, 135), mask)
    
    # 边缘修饰边框
    draw.rounded_rectangle([660, 95, 880, 557], radius=24, outline=C_BORDER, width=2)
    draw.rounded_rectangle([880, 135, 1100, 597], radius=24, outline=C_GREEN, width=2)

    sp_path = "docs/social-preview.png"
    sp.convert("RGB").save(sp_path, quality=95)
    print(f"Saved: {sp_path} ({os.path.getsize(sp_path) // 1024} KB)")

def main():
    os.makedirs("docs/screenshots", exist_ok=True)
    
    print("Generating 5 core screenshots...")
    # 1. 训练主屏
    generate_workout_screen(with_timer=False).resize((400, 840), Image.Resampling.LANCZOS).save("docs/screenshots/01_workout.png")
    print("Saved docs/screenshots/01_workout.png")
    
    # 2. 组间休息胶囊
    generate_workout_screen(with_timer=True).resize((400, 840), Image.Resampling.LANCZOS).save("docs/screenshots/02_rest_timer.png")
    print("Saved docs/screenshots/02_rest_timer.png")
    
    # 3. 负荷与 E1RM PR
    generate_analytics_screen().resize((400, 840), Image.Resampling.LANCZOS).save("docs/screenshots/03_analytics.png")
    print("Saved docs/screenshots/03_analytics.png")
    
    # 4. 日历打卡与 Health Connect
    generate_calendar_screen().resize((400, 840), Image.Resampling.LANCZOS).save("docs/screenshots/04_calendar.png")
    print("Saved docs/screenshots/04_calendar.png")
    
    # 5. 身体指标
    generate_body_screen().resize((400, 840), Image.Resampling.LANCZOS).save("docs/screenshots/05_body.png")
    print("Saved docs/screenshots/05_body.png")
    
    # 6. Demo GIF
    generate_demo_gif()
    
    # 7. Social Preview (1200x630)
    generate_social_preview()
    
    print("All assets successfully generated!")

if __name__ == "__main__":
    main()
