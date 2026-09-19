# Baoji 功能路线图（方案文档）

> 生成日期：2026-09-15
> 依据：grill 拷问结论 —— 核心目标=**提升训练成绩**；本轮方向=周期回顾与趋势、习惯与提醒、记录更完整、AI 私教真接入；数据边界=训练 + **身体数据**；节奏=先出方案，不写代码。
> 现状基线：Room v2 / 4 Tab（训练·负荷·日历·私教）+ 动作库 / 26 个源文件 / 14 个单测。

---

## 0. 结论摘要

现有 App 已经完成「**记录 → 回看单日**」的闭环，缺三层：

| 缺口 | 表现 | 归属方案 |
|---|---|---|
| 没有**周期**这一层 | 只有单日（日历）和单动作曲线（E1RM），答不出"我这月比上月练得多吗" | M1 |
| 没有**为什么** | `WorkoutSessionEntity.notes`、`WorkoutSetEntity.rpe` 是死字段，全项目零读写；身体档案卡是写死的 73.5/15.2/肌肥大 | M3 |
| 没有**再回来** | 无提醒、无 streak、无休息日概念，日历只做事后展示 | M2 |

并且有一个**必须先修的技术债**：`BaojiDatabase.kt:44` 用了 `fallbackToDestructiveMigration()`，一旦有真实训练史，任何加字段都会清库（M0）。

**执行顺序（按"提升成绩"这个唯一目标排序）**：
`M0 迁移 → M1 周期回顾 → M3a 备注/RPE（零迁移）→ M3b 身体数据 → M2 习惯与提醒 → M4a 本地总结 → M4b 真 LLM`

依赖关系：

```
M0 迁移 ──┬─→ M1 周期回顾      （数据已有，价值最高）
          ├─→ M3b 身体数据     （需要新表）
          ├─→ M2 习惯与提醒    （需要设置存储）
          └─→ M4 AI 私教 ←───── 依赖 M3a 的上下文（备注/状态）
M3a 备注/RPE（字段已存在，零迁移，可随时插入）
```

---

## M0. 前置：Room 迁移策略（硬门槛，先做）

### 为什么必须先做
`BaojiDatabase.kt` 当前：
```kotlin
version = 2,
exportSchema = false
...
.fallbackToDestructiveMigration()   // ← 加字段即清库
```
代码注释自己写着"旧库全是开发期演示数据（无真实训练史），重建比写迁移便宜"。**这个前提在本轮失效** —— M1 之后你会积累真实训练史，M3b/M2 都要加表，再清一次库等于把路线图的目标全抹掉。

### 改法
1. `version = 3`，`exportSchema = true`，新增 `app/schemas/`（`build.gradle.kts` 加 `room.schemaLocation` ksp 参数）。
2. 删除 `fallbackToDestructiveMigration()`，改为 `.addMigrations(MIGRATION_2_3, ...)`。
3. MIGRATION_2_3：本方案只需要 `CREATE TABLE body_metrics`（见 M3b）；列级变更本轮为零。
4. 迁移测试：`MigrationTestHelper` + `androidTest` 各一条（v2 → v3 后旧训练记录仍在）。

### 改动文件
- `core/database/BaojiDatabase.kt`
- `app/build.gradle.kts`（schema 目录 + `androidTestImplementation(libs.androidx.room.testing)`，当前 `libs.versions.toml` **没有** room-testing，需新增）
- 新增 `app/src/androidTest/.../MigrationTest.kt`
- 新增 `app/schemas/com.baoji.fitness.core.database.BaojiDatabase/3.json`

### 验收标准
- 装 v2（含数据）→ 覆盖安装 v3 → 会话/组数一条不少，动作库不被重复播种（现有 `onOpen` + COUNT 检查已保证幂等）。

---

## M1. 周期回顾与趋势（P0，核心价值）

### 目标
回答三个问题：**这周练够了吗？比上周多还是少？哪个部位欠练？**

### 已有可复用的东西
- `WorkoutRepository.getWeeklyMuscleVolumes()`：已按 7 天窗口算部位有效组数，口径已统一（热身不计）。
- `OverloadCalculator.evaluateMuscleVolume()`：已返回 `MuscleVolumeStatus(mev/mav/mrv/statusText)`。
- `WorkoutDao.getCompletedSetsSince(sinceTimestamp)`：已有的取数入口。
- `AnalyticsScreen` 的 `OverloadCanvasChart`：已有的 Canvas 折线画法，直接照抄给趋势图。

### 新增
**DAO**（`WorkoutDao`）：
```kotlin
@Query("SELECT * FROM workout_sets WHERE isCompleted = 1 AND completedAt >= :from AND completedAt < :to")
fun getCompletedSetsBetween(from: Long, to: Long): Flow<List<WorkoutSetEntity>>

@Query("SELECT * FROM workout_sessions WHERE startTime >= :from AND startTime < :to ORDER BY startTime ASC")
fun getSessionsBetween(from: Long, to: Long): Flow<List<WorkoutSessionEntity>>
```

**纯函数**（新增 `domain/algorithm/PeriodReviewCalculator.kt`，**必须可单测**）：
```kotlin
data class PeriodReview(
    val label: String,                 // "本周" / "2026年9月" / "近8周"
    val sessionCount: Int,
    val totalVolumeKg: Double,
    val totalSets: Int,                // 不含热身
    val typeCounts: Map<String, Int>,  // 推/拉/腿 → 次数，复用 workoutTypeLabel()
    val muscleVolumes: List<MuscleVolumeStatus>,  // 复用 evaluateMuscleVolume
    val volumeDeltaPct: Double?        // 对比上一周期的容量变化，无上期为 null
)

fun buildPeriodReview(sets, sessions, exercises, from, to, previous: Pair<Long, Long>?): PeriodReview
fun buildWeeklyTrend(sets, exercises, weeks: Int): List<Pair<String, Double>>  // 近 N 周容量
```

**ViewModel**（`AnalyticsViewModel` 或新 `PeriodViewModel`）：`periodReview: StateFlow<PeriodReview?>`、`trend: StateFlow<List<Pair<String, Double>>>`。

**UI**（放「负荷」Tab 顶部，不新增 Tab）：
1. 周期切换 chips：`本周 / 本月 / 近8周`（复用 AnalyticsScreen 的动作筛选 chip 样式）。
2. 汇总卡：训练 N 次 · 总容量 X kg · 有效组数 Y · 环比 `+12%`（绿/红）。
3. 部位容量进度条：**本周 vs 上周**双色条，对照 MEV/MAV/MRV（现有 `VolumeProgressBar` 已画出单个，加一条上周即可）。
4. 容量趋势折线（近 8 周，Canvas 复用 `OverloadCanvasChart` 的写法）。

### 风险
- **时区**：周聚合不要用 SQLite `strftime`（本地时区踩坑），在 Kotlin 侧按 `LocalDate` 分组（个人 App 数据量小，几千行，内存聚合完全够）。
- 口径：所有统计必须继续排除 `setType == "WARMUP"`，和训练页/日历保持一致，否则三处数字打架。

### 验收标准
- 本周数字 = 训练页「完成」弹窗的累计容量（同一口径，能对上）。
- 单测覆盖：空周期（全 0 不崩）、跨周边界、热身组被排除、环比无上期时返回 null。

---

## M2. 习惯与提醒（P0）

### 目标
让明天的你还愿意打开它：**streak + 训练日提醒 + 休息日**。

### 已有可复用的东西
- `CalendarViewModel.trainedDayTypes: Map<LocalDate, String>` —— 打卡日期集合**已经在内存里**，streak 不需要新查询。
- `AndroidManifest.xml` **已经声明** `POST_NOTIFICATIONS`（还有 `INTERNET`/`CAMERA`/`VIBRATE`）—— 通知不需要新权限声明，只需运行时申请。

### 新增
**纯函数**（`domain/algorithm/StreakCalculator.kt`，可单测）：
```kotlin
fun currentStreak(trainedDays: Set<LocalDate>, today: LocalDate): Int   // 连续训练天数
fun longestStreak(trainedDays: Set<LocalDate>): Int
```
> 规则要定死：**今天没练但昨天练了，streak 不算断**（当天还没过完）。这条规则写进单测。

**设置存储**：用 `SharedPreferences` 就够（只有 3~4 个标量：提醒开关、提醒时间、每周训练日）。**不要**引入 DataStore/DI 框架 —— 个人单机 App 不值当。
新增 `data/settings/ReminderSettings.kt`（读写 + 默认值）。

**提醒**：新增依赖 `androidx.work:work-runtime-ktx`（`libs.versions.toml` 当前**没有**任何 work 依赖，需新增）。
- 新增 `data/reminder/WorkoutReminderWorker.kt`（CoroutineWorker，查今天是否已打卡 → 未打卡则发通知）
- 新增 `data/reminder/ReminderScheduler.kt`（`enqueueUniquePeriodicWork`，按设置的星期与时间）
- `MainActivity`：Android 13+ 用 `rememberLauncherForActivityResult` 申请 `POST_NOTIFICATIONS`

**UI**：
- 日历 Tab 顶部：`🔥 连续 12 天 · 最长 21 天` 徽章 + 「本周还剩 2 练」提示
- 日历 Tab 底部：提醒设置区（开关 / 时间 / 每周训练日 chips）
- 训练页归档态：今天练完 → `连续 13 天，明天是休息日` 之类一句文案（复用现有归档分支）

### 风险
- WorkManager 的最小周期是 15 分钟，**不能精确到点**。想精确需要 `AlarmManager`（更麻烦、要 `SCHEDULE_EXACT_ALARM`）。建议先用 WorkManager + 允许 ±15 分钟，个人自用足够；文档里标明这个取舍。
- 不引入 `SCHEDULE_EXACT_ALARM`（Android 14 起需要用户手动授权，体验差）。

### 验收标准
- 单测：streak 的 5 种边界（空集合 / 只今天 / 只昨天 / 断一天 / 连续）。
- 手动：改系统日期验证通知不重复发（WorkManager 唯一任务名保证）。

---

## M3. 记录更完整（P1，同时是 M4 的前置）

### M3a. 复活死字段：会话备注 + RPE（**零迁移，最便宜**）

`schema` 里**已经有这两列**，只是从来没有 UI/DAO 写它：
- `WorkoutSessionEntity.notes: String = ""`
- `WorkoutSetEntity.rpe: Double? = null`

所以这一步**不需要任何迁移**，只加读写：

```kotlin
// WorkoutDao
@Query("UPDATE workout_sessions SET notes = :notes WHERE id = :sessionId")
suspend fun updateSessionNotes(sessionId: Long, notes: String)
```

UI：
- 完成弹窗前加一个「今天状态如何？」输入框（一行 TextField + 预设 chips：`状态好 / 一般 / 累 / 睡眠不足`）→ 写进 `notes`
- 组编辑弹层（已有 `QuickAdjustSetBottomSheet`）末尾加一行可选的 RPE 选择（`6~10`，默认不填）
- **注意**：README 目前写着"不依赖 RPE（从未采集该字段）"和"双重渐进不依赖 RPE"—— 保持判定逻辑不读 RPE，RPE 只作为记录与 M4 的上下文，**不要**把它塞回渐进算法里。

### M3b. 身体数据（体重 / 体脂 / 围度）——需要迁移

现在 `LibraryScreen.kt:171-173` 是写死的 `73.5 / 15.2 / 肌肥大`。

**新表**（`MIGRATION_2_3` 里创建）：
```kotlin
@Entity(tableName = "body_metrics")
data class BodyMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,              // 当天本地 0 点 epoch millis，一天一条
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val armCm: Double? = null,
    val note: String = ""
)
```
DAO：`@Insert(onConflict = REPLACE)`（`date` 加唯一索引，一天一条）+ `getLatest()` + `getAll()`。

UI：
- 动作库 Tab 的「生理参数与健康上下文」卡 → 改读最新一条；空态显示"还没有记录，点这里记一次"
- 加记录入口（复用已有的 `ModalBottomSheet` 写法 + 步进器）
- 趋势图：体重/腰围折线，**直接复用 `OverloadCanvasChart` 的画法**
- 追加到日历当天明细弹层尾部（当天称重就顺手记）

### 明确不做（本轮）
**饮食/营养记录不做。** 你的核心目标是"提升训练成绩"，不是"最全的记录本"；饮食记录的输入摩擦极大、数据结构复杂，会拖垮这一轮。等 M4 真的接上 LLM 且你确实需要时再说。

### 验收标准
- M3a：完成训练后重进 App，备注还在（证明写库成功）。
- M3b：迁移测试通过；同一天记两次是覆盖而不是两条。

---

## M4. AI 私教真接入（P2，分两步，依赖 M3a）

现状：`CoachScreen` 是写死演示脚本（`CoachViewModel` 里硬编码 `右肩减压与动作替换建议` 等），主页明确标注"⚠️ 未实现 · 本地演示占位"。

### M4a. 本地规则化总结（**零依赖，先做，立刻有用**）

不接任何模型，用**已有数据**生成一句人话总结：
- 输入：M1 的 `PeriodReview` + M2 的 streak + M3a 的最近 3 次备注
- 输出模板，例如：
  `本周练了 3 次，容量环比 +8%。背部 14 组已在 MAV，胸部 6 组低于 MEV —— 明天建议补推日。上次备注"睡眠不足"，若今晚仍睡不好，推日重量下调一档。`
- 纯函数（`domain/algorithm/CoachNarrator.kt`）+ 单测。**这一步就完成了"私教有价值"的 60%**，且零网络、零费用、零隐私问题。

### M4b. 真 LLM（需要架构决策）

真要接入需要解决四件事，**每件都有不可跳过的约束**：

| 事项 | 方案 | 约束 |
|---|---|---|
| 上下文打包 | 把 M1 周期汇总 + 近 N 天训练 + 部位容量 + PR + M3a 备注/状态 + M3b 身体数据 → 结构化 JSON | 要点：**只发摘要不发全量**，否则 token 爆炸 |
| 网络层 | 新增 HTTP 客户端（OkHttp 或 Ktor） | 当前项目**没有任何网络依赖**（`INTERNET` 权限已声明但没用） |
| API Key | 个人自用：`local.properties` → `BuildConfig`（不进 git）<br>给别人用：**必须走自建后端代理** | **绝不能把 key 硬编码进 APK** —— 反编译即泄露 |
| 流式输出 | SSE/流式响应 + 已有的处方卡结构 | 现有 `CoachScreen` 的消息结构可复用 |

分两步走：
1. M4b-1：单轮问答（发上下文 → 收一段文字），跑到通。
2. M4b-2：流式 + 结构化处方卡 + 「一键应用到当前训练」（`CoachScreen` 已有 `onApplyToWorkout` 入口）。

### 隐私
M3b 的身体数据、M3a 的备注都会外发。**在设置里明确写"哪些数据会被发送"并给开关**，这是加 LLM 时唯一的合规底线。

### 验收标准
- M4a：给定固定输入，输出文案断言（快照测试）。
- M4b：断网时降级回 M4a 的本地总结，不白屏、不崩。

---

## 附录 A：不建议做（ponytail 视角）

| 想法 | 为什么不 |
|---|---|
| 饮食/营养记录 | 与"提升成绩"弱相关，输入摩擦极大，雪球大 |
| 云同步 / 多用户 / 后端 | 单机自用，`allowBackup=true` 已能覆盖换机；等真要给别人用再说 |
| Hilt / Koin / DataStore | 现在 `MainActivity` 手动构造 4 个 VM 完全够；引入 DI 框架是纯负债 |
| 自定义训练日模板库 | 现在 3 个模板 + 空白自定义够用；等 M1 上线后看真实使用再定 |
| 精确到分钟的提醒（AlarmManager） | 要 `SCHEDULE_EXACT_ALARM` 运行时授权，体验差；WorkManager ±15 分钟足够 |

## 附录 B：改动范围一览

| 模块 | 新增文件 | 修改文件 | 需迁移 |
|---|---|---|---|
| M0 | `MigrationTest.kt`、`app/schemas/**` | `BaojiDatabase.kt`、`build.gradle.kts` | 是 |
| M1 | `PeriodReviewCalculator.kt`（+测试） | `WorkoutDao.kt`、`WorkoutRepository.kt`、`AnalyticsViewModel.kt`、`AnalyticsScreen.kt` | 否 |
| M2 | `StreakCalculator.kt`（+测试）、`ReminderSettings.kt`、`WorkoutReminderWorker.kt`、`ReminderScheduler.kt` | `MainActivity.kt`、`CalendarScreen.kt`、`CalendarViewModel.kt`、`build.gradle.kts`（work-runtime） | 否 |
| M3a | — | `WorkoutDao.kt`、`WorkoutViewModel.kt`、`WorkoutScreen.kt` | **否（列已存在）** |
| M3b | `BodyMetricEntity.kt`、`BodyMetricDao.kt` | `BaojiDatabase.kt`、`LibraryScreen.kt` | 是 |
| M4a | `CoachNarrator.kt`（+测试） | `CoachViewModel.kt`、`CoachScreen.kt` | 否 |
| M4b | `data/llm/**` | `CoachViewModel.kt`、`build.gradle.kts`、`local.properties`（key） | 否 |

## 附录 C：本轮统一口径（三处数字必须一致）

1. **容量（Volume）**：`Σ weightKg × reps`，**排除 `setType == "WARMUP"`**。
2. **有效组数**：同上，排除热身组；「完成组数」显示时**含**热身（训练页现状，保留）。
3. **训练类型**：`workoutTypeLabel(title)` → 推 / 拉 / 腿 / 练。
4. **打卡日**：当天至少完成一组（`isCompleted = 1`），空壳会话不算。
5. **归期**：一次训练整体归属于**其会话的开始日**（跨零点的会话不拆成两天），
   日历打卡 / 周期回顾 / 趋势 / 部位组数四处共用同一口径。
   窗口切分全部在 Kotlin 侧按本地 `LocalDate` 做（`PeriodReviewCalculator.sessionsIn`），
   **不做 epoch 换算、不用 SQLite 日期函数** —— 否则会出现「训练 0 次却有 1280kg 容量」这种同卡自相矛盾。
6. **MEV/MAV/MRV 是周阈值**：部位状态只由固定自然周的 `getWeeklyMuscleComparison` 提供，
   **不跟随周期 chips**。「本月 / 近8周」的部位组数与周阈值不可比，因此 `PeriodReview` 不携带部位状态字段（已删）。

---

## 附录 D：M0 + M1 开工结果（2026-09-19 实现 + 审计）

**M0 达标**，无需改动：`version=3` + 显式 `MIGRATION_2_3`（与 `3.json` 的 createSql 逐字一致）、
`fallbackToDestructiveMigration()` 已移除、`exportSchema=true` + `room.schemaLocation`、
`room-testing` 依赖**与 `androidTest.assets.srcDir("$projectDir/schemas")`** 均已配置（后者最常被漏，缺了 MigrationTest 直接跑不起来）。
旧库 v1 不存在风险：v2 构建时 destructive 已把 v1 库重建为 v2，故不会命中「无 1→3 迁移路径」的崩溃。

**M1 审计发现并修复 2 处会说谎的地方**：

| 问题 | 影响 | 修法 |
|---|---|---|
| 组按 `completedAt`、会话按 `startTime` 两套窗口 | 跨零点训练 → 回顾卡显示「训练 0 次 · 容量 1280kg」自相矛盾 | 统一按**会话开始日**，且下沉到纯函数 `sessionsIn`/`setsOf`（可被单测钉住）；新增回归用例 |
| `PeriodReview.muscleVolumes` 用周阈值评估多周窗口 | 「本月/近8周」必然被判「过度训练风险」；当前无 UI 消费，属地雷 | 删除该字段；部位口径单一真相源 = `getWeeklyMuscleComparison`；抽出 `muscleSetCounts` 供其复用 |

附带净删除：`getCompletedSetsBetween` / `getSessionsBetween` 两条 M1 专用 DAO 查询与 `windowToMillis`
（时区换算整类消失），三个 M1 数据面改为共用仓储内的 `trainingHistory` 单一来源。
`PeriodReviewCalculatorTest` 8 → 11 个用例；全量 52 tests / 0 failures，`assembleDebug` 通过。