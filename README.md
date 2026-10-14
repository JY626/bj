# Rep

> 为薄肌训练设计的极简渐进式负荷健身记录 App

一款基于 **Kotlin + Jetpack Compose (Material 3)** 构建的现代化、**100% 离线单机**健身记录应用。
专注于**极速训练记录、科学渐进式负荷量化与身体指标追踪**。界面遵循 **Apple Human Interface Guidelines (HIG)** 极简黑曜石设计，全界面采用 **Lucide 矢量线性图标**。

---

## 一、 核心功能特色

1. **零延迟极速记录 (Zero-Friction Logging)**：
   * 开练时自动回填上周同动作记录（重量、次数）；打勾后下一未完成组自动继承本组重量次数，全程零键盘；
   * 针对健身房疲劳场景的大触控区设计（48dp+）；
   * 打勾即刻触发 Apple 质感震动反馈与实时容量（Volume kg）累加（热身组不计入容量）。
2. **Apple Watch 风格悬浮组间休息胶囊 (Rest Timer Capsule)**：
   * 打勾后自动在底部浮起半透明高斯模糊胶囊，环形进度条倒计时，支持一键 `+30s` 与 `跳过`，不阻碍浏览后续动作。
3. **渐进式负荷量化 (Progressive Overload Analytics)**：
   * **E1RM 极限拟合**：采用 Epley 经典公式 $1\text{RM} = \text{Weight} \times (1 + \text{Reps} / 30)$，以 Canvas 动态绘制力量突破曲线与 PR 标记；
   * **RP 周期容量监控**：量化每周胸/背/腿/肩/臂/核心的有效组数，对照 MEV/MAV/MRV 科学阈值；一个动作同时给多个肌群计组（主动作 1.0 组 + 每个间接肌群 0.5 组，如高位下拉 = 背部 1 + 手臂 0.5）；
   * **双重渐进协议**：基于前次首组真实数据纯次数判定加重节点（如 80kg x 8次达标 -> 建议首组冲击 82.5kg x 6次），实时显示在训练页顶部建议条。
4. **每日记录日历与习惯追踪 (Calendar & Habits)**：
   * 训练日打卡日历（推 / 拉 / 腿 快速辨识）；
   * 连续训练天数（Streak）与周计划达成进度统计；
   * 每日详情内直观查看当天完成组数、容量与当天的身体测量数据。
5. **身体指标管理 (Body Metrics)**：
   * 记录身高、体重、体脂率、胸围、腰围、臂围等身体指标；
   * 自动计算 BMI 与相对上一次记录的增减变化趋势。
6. **Health Connect 自动同步与 AI 数据生态 (Muse Sync)**：
   * **系统健康底座写入**：训练归档后自动将力量训练、体重、体脂率写入 Android Health Connect，供外部 AI 教练（如 Muse）在后台无感直接读取；
   * **一键分享给 AI**：一键生成规范 Markdown 结构化卡片（动作组智能折叠、环比与身体数据较上次增减），通过系统 Sharesheet 一键转发。
7. **100% 离线优先架构 (Offline-First)**：
   * 基于 Android Jetpack Room (SQLite) 本地数据库，无任何自建网络请求，健身房地下室弱网无痛运行，隐私数据 100% 本地存储。

---

## 二、 工程目录结构

```
app/src/main/java/com/baoji/fitness/
├── core/
│   ├── theme/                 # Apple OLED 黑色系、字阶排版、等宽数字 (Tabular nums)
│   ├── designsystem/icons/    # 纯净 Lucide Compose 矢量线性图标 (Dumbbell, Activity, Calendar, Layers, User 等)
│   └── database/              # Room 数据库实体、DAO 与预置经典动作种子数据 (BaojiDatabase)
├── domain/
│   ├── model/                 # 核心领域模型 (WorkoutSession, WorkoutSet, WorkoutDayDetail)
│   └── algorithm/             # 渐进负荷、RP 周期容量与 Markdown 分享格式化器 (OverloadCalculator, PeriodReviewCalculator, AiShareTextFormatter)
├── data/
│   ├── repository/            # 数据仓库 (WorkoutRepository, BodyMetricRepository)
│   ├── healthconnect/         # Health Connect 写入器 (HealthConnectWriter)
│   ├── reminder/              # 训练日定时通知 (WorkManager)
│   └── settings/              # 提醒、Health Connect 同步与组间休息偏好设置
├── ui/
│   ├── workout/               # 训练记录主屏 & 悬浮休息计时胶囊
│   ├── analytics/             # 渐进负荷看板 & Canvas E1RM 折线图与周期容量对比
│   ├── calendar/              # 每日记录：训练打卡月历、习惯面板与 Health Connect 设置
│   ├── library/               # 动作库分类与自定义动作
│   ├── body/                  # 身体指标管理与趋势面板
│   ├── components/            # AI 分享预览弹层 (AiSharePreviewBottomSheet) 等通用组件
│   └── navigation/            # 5-Tab 磨砂底部导航与路由宿主 (MainScreen)
├── BaojiApplication.kt        # 应用级依赖注入、Room 初始化与 Health Connect 桥接
└── MainActivity.kt            # 入口 Activity、边缘延展 (Edge-to-Edge) 与权限 Rationale
```

---

## 三、 如何在 Android Studio 中打开与运行

1. 打开 **Android Studio (Ladybug 2024.2 或更高版本)**；
2. 选择 **Open**，直接选中本项目根目录；
3. 等待 Gradle 同步完成后，点击运行按钮启动或安装至真机。

---

## 四、 开源协议

本项目采用 [MIT License](LICENSE) 授权。
