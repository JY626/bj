# Baoji (暴肌 / 宝肌) - Android 健身渐进式负荷与 AI 私教 App

一款基于 **Kotlin + Jetpack Compose (Material 3)** 构建的现代化健身记录应用。
专注于**极速训练记录、科学渐进式负荷量化、以及接入大模型私教**。界面遵循 **Apple Human Interface Guidelines (HIG)** 极简黑曜石设计，全界面采用 **Lucide 矢量线性图标**。

---

## 一、 核心功能特色

1. **零延迟极速记录 (Zero-Friction Logging)**：
   * 自动回填上周同动作记录（重量、次数）；
   * 针对健身房疲劳场景的大触控区设计（48dp+）；
   * 打勾即刻触发 Apple 质感震动反馈与实时容量（Volume kg）累加。
2. **Apple Watch 风格悬浮组间休息胶囊 (Rest Timer Capsule)**：
   * 打勾后自动在底部浮起半透明高斯模糊胶囊，环形进度条倒计时，支持一键 `+30s` 与 `跳过`，不阻碍浏览后续动作。
3. **渐进式负荷量化 (Progressive Overload Analytics)**：
   * **E1RM 极限拟合**：采用 Epley 经典公式 $1\text{RM} = \text{Weight} \times (1 + \text{Reps} / 30)$，以 Canvas 动态绘制力量突破曲线与 PR 标记；
   * **RP 周期容量监控**：量化每周胸/背/腿/肩/臂的有效组数，对照 MEV/MAV/MRV 科学阈值。
   * **双重渐进协议**：智能判定加重节点（如 80kg x 8次达标 -> 建议首组冲击 82.5kg x 6次）。
4. **AI 私教智能体搭档 (Apple Intelligence Style Coach)**：
   * 伤病与器械被占时的自调节（Auto-regulation）动作替换与重量折算；
   * 多模态视觉（Gemini Vision）拍照识别外卖/餐盘，拆解热量与蛋白质占比。
5. **离线优先架构 (Offline-First)**：
   * 基于 Android Jetpack Room (SQLite) 数据库，健身房地下室弱网无痛运行。

---

## 二、 工程目录结构

```
app/src/main/java/com/baoji/fitness/
├── core/
│   ├── theme/                 # Apple OLED 黑色系、字阶排版、等宽数字 (Tabular nums)
│   ├── designsystem/icons/    # 纯净 Lucide Compose 矢量线性图标 (Dumbbell, Activity, Sparkles, Layers 等)
│   └── database/              # Room 数据库实体、DAO 与预置经典动作种子数据 (BaojiDatabase)
├── domain/
│   ├── model/                 # 核心领域模型 (WorkoutSession, WorkoutSet, MuscleVolumeStatus)
│   └── algorithm/             # 渐进负荷与 RP 周期理论计算器 (OverloadCalculator)
├── data/
│   └── repository/            # 数据仓库 (WorkoutRepository)
├── ui/
│   ├── workout/               # 训练记录主屏 & Apple Watch 悬浮休息计时条
│   ├── analytics/             # 渐进负荷看板 & Canvas E1RM 折线图
│   ├── coach/                 # AI 私教对话 & 结构化处方卡片
│   ├── library/               # 动作库分类与生理健康档案
│   └── navigation/            # 4-Tab 磨砂底部导航与路由宿主 (MainScreen)
├── BaojiApplication.kt        # 应用级依赖注入与 Room 初始化
└── MainActivity.kt            # 入口 Activity 与边缘延展 (Edge-to-Edge)
```

---

## 三、 如何在 Android Studio 中打开与运行

1. 打开 **Android Studio (Ladybug 2024.2 或更高版本)**；
2. 选择 **Open**，直接选中本项目根目录 `/Users/a123456/project/baoji`；
3. 等待 Gradle 完成依赖解析与 Sync（网络环境需能访问 Google / Maven Central 仓库）；
4. 连接 Android 8.0 (API 26) 以上的真机或模拟器，点击绿色 **Run (Shift + F10)** 即可构建运行。
