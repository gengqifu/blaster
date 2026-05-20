# Blaster

Android 端侧音乐特征提取与关联验证工程。当前仓库包含：

- `MVP-1`：本地扫描 + 基础信息 mock 关联闭环
- `MVP-2`：本地扫描状态机与生命周期管理
- `MVP-3`：音频指纹队列/调度/云端 mock 匹配
- `MVP-4`：本地 embedding（YAMNet TFLite）提取、调度与结果暴露

## 项目介绍

Blaster 的目标是把“本地歌曲 -> 可用于匹配/检索的结构化特征”做成可扩展 pipeline，并保证：

- 各阶段可独立开关、可回放、可观测
- 高成本阶段（音频指纹、本地 embedding）可被调度与设备状态保护
- 结果语义稳定：`LOCAL_FEATURE_READY` 不等于 `RELIABLY_ASSOCIATED`

当前 Demo 默认使用内置 `YAMNet` 模型（`core/src/main/assets/models/yamnet.tflite`）做开发验证闭环。
当前主代码默认不接真实云端；匹配网关返回 `service_not_configured`，用于明确告知“云端未配置”，不会伪造可靠关联结果。

## 主代码约束（强制）

- `mock` 仅允许在测试目录：`src/test`、`src/androidTest`
- `core/src/main`、`demo/src/main` 禁止出现 mock/fake/test-scanner/占位未接线分支
- 提交前必须通过：

```bash
./gradlew checkMainNoMock
```

## 整体架构

### 模块划分

- `:core`：核心能力（扫描、队列、调度、pipeline、存储、模型推理）
- `:demo`：Android 演示应用（可视化触发各阶段并展示结果）

### MVP-4 核心链路

1. 扫描阶段：`FeaturePipeline.scanAndProcess(...)`
2. 音频指纹阶段：`AudioIdentityQueue + AudioIdentityScheduler + processAudioIdentityQueue(...)`
3. 本地特征阶段：`LocalFeatureQueue + LocalFeatureScheduler + processLocalFeatureQueue(...)`
4. 结果聚合：`ResultProvider`

关键类位置：

- `FeaturePipeline`：`core/src/main/java/com/orion/blaster/core/pipeline/FeaturePipeline.kt`
- `LocalFeatureScheduler`：`core/src/main/java/com/orion/blaster/core/scheduler/LocalFeatureScheduler.kt`
- `LocalEmbeddingModel`：`core/src/main/java/com/orion/blaster/core/embedding/LocalEmbeddingModel.kt`
- `ResultProvider`：`core/src/main/java/com/orion/blaster/core/result/ResultProvider.kt`
- Demo 入口：`demo/src/main/java/com/orion/blaster/demo/MainActivity.kt`

## 环境要求

- JDK 17
- Android SDK（compileSdk 34）
- 至少 1 台已连接设备或可用模拟器（`minSdk 24`）
- `adb` 可用

## 构建与测试

在仓库根目录执行：

```bash
./gradlew :core:test :core:assemble :demo:assembleDebug
```

可选：验证 YAMNet 最小真实加载/推理（Instrumentation）：

```bash
./gradlew :demo:connectedDebugAndroidTest --tests '*YamnetInstrumentationTest'
```

## Demo 使用方法（可直接操作）

### 1. 安装并启动 Demo

```bash
./gradlew :demo:installDebug
adb shell am start -n com.orion.blaster.demo/.MainActivity
```

### 2. 基础扫描闭环（先做）

在页面中设置：

- `Scan Source` 选 `MEDIA_STORE`（真实设备媒体库）
- `Match Scenario` 先选 `NONE`

点击：

1. `Run Scan + Match`

预期：

- `summary` 出现 `scanned/new/changed/...`
- `result provider` 区域可看到歌曲 lifecycle（如 `UNASSOCIATED`）

### 3. 音频指纹阶段（MVP-3）

说明：`Run Audio Identity` 默认是“提取优先、比对可选”。`Audio Compare=OFF` 时只做端上提取并保存摘要，不调用云端比对。当前提取链路使用 `DefaultAudioIdentifyInputGenerator + AudioFingerprintExtractor + NativeChromaprintBridge`，`payload/payloadDigest` 来自真实 Chromaprint JNI 提取，不是手工拼接字符串。

设置：

- `Audio Identity Scenario` 先选 `NONE`（可再试 `RELIABLE/CANDIDATE/ERROR`）
- `Audio Identity Guard` 先选 `ALLOW`

点击：

1. `Run Audio Identity`

预期：

- `audioIdentity:` 行出现 `scheduled/waiting/audioExtracted/audioCompared/compareSkipped/failed/reliable/candidate/none`
- `audio identity summaries:` 出现 `algorithm/version/costMs/reason`

可验证保护分支：

- 把 `Audio Identity Guard` 切到 `PLAYBACK/LOW_BATTERY/HIGH_TEMPERATURE/FOREGROUND_BUSY/HIGH_COST_DISABLED`
- 再点 `Run Audio Identity`，应出现 waiting/skipped 类结果

### 4. 本地 embedding 阶段（MVP-4）

设置：

- `Local Feature Guard` 先选 `ALLOW`
- `Candidate Queue Scope` 先选 `UNASSOCIATED_ONLY`

点击：

1. `Run Local Feature`

预期：

- `localFeature:` 行出现 `scheduled/waiting/ready/failed/skipped`
- `local feature diagnostics:` 出现：
  - `model=YAMNet@...`
  - `schema=...`
  - `shape=[...]`
  - `costMs=...`
- `result provider:` 出现：
  - `embedding=<非 0 维度>`
  - `model=YAMNet@...`
  - `schema=...`

### 5. 验证语义边界（重点）

验证 `LOCAL_FEATURE_READY != RELIABLY_ASSOCIATED`：

1. `Match Scenario` 选 `NONE`，先跑扫描（让歌曲处于非可靠关联）
2. 执行 `Run Local Feature`
3. 查看 `result provider:` 同一行中：
   - 有 `embedding`
   - `association` 仍可能是 `null`（或仍是候选语义）

这说明本地特征可用不代表云端可靠关联。

### 6. 验证候选是否入队

1. 先把 `Match Scenario` 设为 `CANDIDATE`，执行 `Run Scan + Match`
2. `Candidate Queue Scope = UNASSOCIATED_ONLY`，执行 `Run Local Feature`
3. 观察 `local feature queue:`（候选默认不入队）
4. 切换 `Candidate Queue Scope = INCLUDE_CANDIDATE`
5. 再执行 `Run Local Feature`，观察候选可入队

### 7. 验证失败/等待场景

把 `Local Feature Guard` 切换后执行 `Run Local Feature`：

- `DISABLED`：不执行提取
- `MODEL_MISSING`：进入 waiting（模型不可用）
- `PLAYBACK/LOW_BATTERY/HIGH_TEMPERATURE/FOREGROUND_BUSY`：进入等待或跳过保护路径

观察点：

- `localFeature:` 汇总计数变化
- `local feature diagnostics:` 的 `failure` 字段
- `result provider:` 的 `lastReason`

## 常用命令

```bash
# 全量验收
./gradlew :core:test :core:assemble :demo:assembleDebug

# 安装 demo
./gradlew :demo:installDebug

# 启动 demo
adb shell am start -n com.orion.blaster.demo/.MainActivity

# 查看设备
adb devices
```
