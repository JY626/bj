# 贡献指南 (Contributing to Rep)

感谢你关注并愿意为 **Rep** 做出贡献！本项目致力于打造 Android 平台上最纯粹、零负担的极简渐进式负荷健身记录神器。

---

## 一、 开发环境与前置准备

- **JDK**: Java 17 或更高版本
- **Android Studio**: Ladybug (2024.2) 或更高版本
- **compileSdk**: 35
- **minSdk**: 26 (Android 8.0)
- **Kotlin**: 2.0.21
- **UI 框架**: Jetpack Compose (Material 3)

---

## 二、 本地构建与验证

在提交任何改动前，请务必执行本地全量单元测试与打包构建，确保代码干净、无编译告警：

```bash
# 1. 执行全量单元测试
./gradlew testDebugUnitTest

# 2. 构建调试版 APK
./gradlew assembleDebug

# 3. 构建发布版 APK
./gradlew assembleRelease
```

---

## 三、 代码风格与开发原则

1. **100% 离线优先与零隐私风险**：
   - 严禁在代码、测试用例或演示物料中硬编码任何真实用户的个人身体指标或真实私密训练历史；
   - 坚持纯单机架构，严禁引入自建云端同步或隐蔽数据回传。
2. **渐进负荷口径绝对对齐**：
   - 热身组（`setType == "WARMUP"`）严格排除在有效容量与周有效组数之外；
   - 训练页显示的容量、Health Connect 写入的容量与一键分享文本的容量必须**完全逐字对齐**。
3. **极简现代交互风格**：
   - 遵循 Apple Human Interface Guidelines (HIG) 黑曜石纯黑背景；
   - 触控区域严格保证 48dp+，满足健身房疲劳抖手场景下的盲打勾交互。
4. **Git Commit 规范**：
   采用 [Conventional Commits](https://www.conventionalcommits.org/) 格式：
   - `feat(scope): ...` 新功能
   - `fix(scope): ...` 修复 Bug
   - `docs(scope): ...` 文档变动
   - `refactor(scope): ...` 代码重构
   - `chore(scope): ...` 依赖构建或杂项

---

## 四、 提 PR 流程

1. Fork 本仓库到个人 GitHub 账号；
2. 基于 `master` 分支切出功能分支（例如 `feature/add-rest-vibration`）；
3. 完成代码编写并补充单元测试；
4. 运行 `./gradlew testDebugUnitTest` 验证通过；
5. 提交 PR 并按照 PR 模板勾选测试与影响项；
6. 等待 Code Review 与合并。
