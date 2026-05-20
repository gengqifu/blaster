# MVP-4 详细开发计划：可选本地 embedding 验证

## 1. 目标与非目标

### 1.1 目标

MVP-4 目标是在不依赖真实云端、不训练模型、不承诺业务标签的前提下，验证预训练音频模型在 Android 端生成本地 embedding 的可行性。

MVP-4 默认首选 YAMNet TFLite，VGGish 作为备选评估。

MVP-4 完成后应具备：

- 可从指纹后仍未可靠关联的歌曲中筛选本地特征输入队列。
- 可通过开关控制本地特征能力是否启用。
- 可加载测试资源或开发包中的 YAMNet TFLite 模型。
- 可生成本地 embedding。
- 可保存 embedding、模型元信息、schema 版本、耗时和失败原因。
- 可通过 ResultProvider 查询 `LOCAL_FEATURE_READY`。
- 模型能力关闭时，整体流程仍可正常结束。
- YAMNet top-K 分类只用于内部诊断或实验，不作为业务 mood / genre 输出。

### 1.2 非目标

MVP-4 不做：

- 不训练模型。
- 不微调模型。
- 不接真实云端。
- 不上传原始音频。
- 不把 YAMNet top-K 分类作为业务 mood / genre。
- 不实现本地向量检索。
- 不实现相似歌曲产品能力。
- 不决定正式包体策略。
- 不决定模型内置或动态下载的上线方案。
- 不使用 Gemma 作为主链路模型。

### 1.3 当前轮次边界

MVP-4 按完整开发推进，但执行顺序必须先完成里程碑 1 的最小真实工程验证，再进入后续完整闭环实现。

本轮明确：

- 主路径固定为 `YAMNet TFLite`。
- `VGGish` 仅作为 failover，不并行推进。
- demo / 开发验证阶段允许先使用内置模型资源。
- 产品化方向必须保留云端下载能力。
- 当前阶段从“里程碑 1：最小真实工程验证”开始。

本轮不做：

- 不跳过里程碑 1 直接进入本地特征完整闭环开发。
- 不并行实现 `VGGish`。
- 不为了产品化下载能力阻塞首版 demo / 开发验证闭环。

## 2. 模块拆分

### 2.1 `core` 模块

MVP-4 在 MVP-3 基础上新增或细化以下职责：

| 职责 | 建议包 | 内容 |
| --- | --- | --- |
| 本地特征队列 | `com.orion.blaster.core.featurequeue` | `LocalFeatureQueue`，筛选本地特征输入歌曲 |
| 能力开关 | `com.orion.blaster.core.featuretoggle` | `LocalFeatureToggle`，控制本地特征能力开关 |
| 模型输入 | `com.orion.blaster.core.modelinput` | `AudioModelInputGenerator`，生成模型所需音频输入 |
| 模型推理 | `com.orion.blaster.core.embedding` | `LocalEmbeddingModel`，封装 YAMNet TFLite 推理 |
| 特征存储 | `com.orion.blaster.core.store` | `LocalFeatureRepository`，保存 embedding 和模型元信息 |
| 调度保护 | `com.orion.blaster.core.scheduler` | `LocalFeatureScheduler`，控制批次、暂停、重试、降级 |
| 诊断 | `com.orion.blaster.core.diagnostics` | 模型加载、推理耗时、失败原因、包体风险记录 |

职责边界：

- `FeaturePipeline` 负责状态流转、阶段串联和结果写入。
- `LocalFeatureScheduler` 负责批次控制、设备条件检查、暂停、重试和降级决策。
- `LocalEmbeddingModel` 只负责模型加载和推理，不决定业务状态。
- ResultProvider 只暴露本地特征结果，不把 embedding 写成云端可靠关联。

### 2.2 `demo` 模块

`demo` 需要增加：

- 本地特征能力开关。
- 展示本地特征输入队列。
- 触发单首或批量 embedding 生成。
- 展示模型名称、模型版本、schema 版本、embedding 维度、costMs。
- 展示 YAMNet top-K 内部诊断结果，且明确不作为业务标签。
- 展示 ResultProvider 查询到的 `LOCAL_FEATURE_READY`。
- 展示模型关闭、模型不可用、模型加载失败、推理失败等场景。

## 3. 本地特征输入队列

### 3.1 默认入队条件

MVP-4 默认处理以下歌曲：

- 指纹 `NONE` 后仍为 `UNASSOCIATED` 的歌曲。

### 3.2 可配置入队条件

`CANDIDATE_ASSOCIATED` 是否进入本地特征兜底可配置，默认不处理。

原因：

- 候选关联仍可能与云端歌曲有关。
- 默认进入本地特征可能混淆候选关联和本地兜底语义。
- 如后续搜索推荐需要，可通过配置打开并单独评估。

### 3.3 不入队条件

以下状态不进入本地特征生成：

- `RELIABLY_ASSOCIATED`
- `FAILED`
- `SKIPPED`
- `WAITING_TO_CONTINUE`
- `OUTDATED`
- `LOCAL_FEATURE_READY`

`OUTDATED` 需要先经过重算策略，不直接作为新任务入队。

## 4. 模型能力开关

### 4.1 开关语义

本地特征能力必须可关闭。

开关关闭时：

- 不触发模型加载。
- 不触发模型推理。
- 已有基础信息和指纹结果保持可查询。
- 歌曲可保持 `UNASSOCIATED` 或原候选状态，整体流程正常结束。

### 4.2 开关测试要求

- 开关关闭后不进入本地特征队列。
- 开关关闭时 ResultProvider 不返回 `LOCAL_FEATURE_READY`。
- 开关重新打开后，可重新扫描符合条件的歌曲进入队列。

## 5. YAMNet TFLite 实施准入条件

### 5.1 主路径决策

MVP-4 默认且唯一主路径为 `YAMNet TFLite`。

- `VGGish` 只作为 failover，不并行实现。
- 若 `YAMNet` 在模型来源、license、Android 加载、runtime 集成或最小推理验证中任一项不通过，MVP-4 必须停在里程碑 1，先更新本文档与决策记录，再决定是否切换到 `VGGish`。
- 里程碑 1 必须执行最小真实工程验证，不得只停留在文档与 ADR 层。

本节实施口径以以下决策记录为准：

- `decisions/2026-05-15-mvp4-yamnet-android-tflite-policy.md`
- `decisions/2026-05-15-mvp4-local-feature-contract-policy.md`

### 5.2 实施准入条件

MVP-4 进入后续完整闭环实现前，必须先完成以下里程碑 1 准入条件：

- 确认所选 `YAMNet TFLite` 模型产物具有官方可追溯来源。
- 记录模型文件名、来源 URL、文件 hash 或等价不可变标识。
- 确认 Android 侧采用 TensorFlow Lite 作为唯一主 runtime。
- 确认模型文件、runtime 增量与相关附属文件的包体预算口径。
- 确认上游 license / terms 与必要 notice 处理口径。
- 确认 Android 开发环境下的加载方式：测试资源或开发包可访问。

若任一项无法确认，MVP-4 不进入里程碑 2 及后续实现。

里程碑 1 已确认的准入事实：

- 模型文件名：`yamnet.tflite`
- 官方来源 URL：`https://tfhub.dev/google/lite-model/yamnet/tflite/1?lite-format=tflite`
- 模型 SHA-256：`141fba1cdaae842c816f28edc4937e8b4f0af4c8df21862ccc6b52dc567993c3`
- Android runtime：`org.tensorflow:tensorflow-lite:2.14.0`
- 附属文件：首轮验证未引入 labels / metadata 文件
- 开发加载方式：`core/src/main/assets/models/yamnet.tflite` 随 demo / androidTest 合并打包
- license / redistribution 口径：首轮验证采用官方 TFHub 分发的 TFLite 产物，按上游 `Apache-2.0` notice 口径处理
- 包体记录：模型文件 `16,096,668` bytes，TFLite AAR `16,304,220` bytes，合计约 `32.4MB`，超过默认 `20MB` 目标；该偏差在 MVP-4 中接受，仅用于开发验证，后续产品化需通过云端下载或独立交付策略收口

### 5.3 验证目标

YAMNet TFLite 验证目标：

- Android 端可加载模型。
- 可基于音频输入完成推理。
- 可获得 embedding。
- 可记录模型名称、模型版本和 schema 版本。
- 可记录推理耗时和失败原因。
- 可返回非空 embedding tensor 或明确的模型不支持失败原因。

里程碑 1 实际验证结果：

- 真实设备：`S4VCMV95CATGQGT4`
- 执行方式：`adb shell am instrument -w -e class com.orion.blaster.demo.YamnetInstrumentationTest com.orion.blaster.demo.test/androidx.test.runner.AndroidJUnitRunner`
- 设备侧日志确认：
  - `InterpreterApi: Loaded native library: tensorflowlite_jni`
  - `tflite: Initialized TensorFlow Lite runtime.`
  - `tflite: Created TensorFlow Lite XNNPACK delegate for CPU.`
  - `inputShape=[1]`
  - `outputShapes=[[1, 521], [1, 1024], [1, 64]]`
  - `embeddingOutputIndex=1`
  - `embeddingVectorCount=1024`
- 结论：YAMNet TFLite 已在 Android 端完成真实模型加载和真实 inference，并返回非空 embedding 输出

### 5.4 模型输入

MVP-4 需要通过 `AudioModelInputGenerator` 生成模型所需音频输入。

输入来源：

- MVP-3 已验证的本地音频读取能力。
- 测试资源或开发包中的音频样本。

MVP-4 不在文档中锁死具体采样率、窗口长度和张量形态，具体参数以模型要求为准，但实现必须记录输入策略、输出 tensor shape 和模型版本。

### 5.5 模型资源策略

MVP-4 的模型资源策略分两层：

- demo / 开发验证阶段：允许先使用内置模型资源，以最快完成最小真实工程验证和首版闭环。
- 产品化阶段：必须保留云端下载能力。

首版开发不要求先落地下载链路，但必须为后续下载模型保留接口边界。

- 不承诺模型进入正式包体。
- 不在本轮决定正式上线时采用内置还是动态下载。
- 模型文件与 runtime 增量默认目标不超过 20MB；如超过该目标，必须在文档中记录偏差与原因。
- 若模型文件或 runtime 增量超出预期，应记录为后续包体决策风险。
- 后续实现必须为“云端下载模型”保留接口边界，不能把模型来源写死为仅支持内置资源。

### 5.6 失败停止条件

以下任一条件成立时，MVP-4 必须停止进入后续实现，并先更新本文档和 ADR：

- 模型来源不可追溯。
- license 或 redistribution 口径无法确认。
- Android 端无法稳定加载模型。
- 无法完成最小单次推理验证。
- 关键依赖超出 MVP-4 预设边界，导致主路径不再是单纯的 TFLite 验证方案。

## 6. VGGish 备选评估

VGGish 只作为备选评估，不作为 MVP-4 默认实现路径。

纳入条件：

- 有稳定 Android/TFLite 产物。
- 模型文件和 runtime 体积满足 20MB 目标或有明确包体豁免理由。
- 可稳定输出 embedding。
- 接入成本不高于 YAMNet。

若 YAMNet 不可行，MVP-4 需要先更新计划，再决定是否切换 VGGish。

## 7. embedding 输出与存储

### 7.1 输出内容

MVP-4 对外只暴露：

- embedding。
- 模型名称。
- 模型版本。
- feature schema 版本。
- generatedAt。

`LocalFeature` 对外字段边界以 `decisions/2026-05-15-mvp4-local-feature-contract-policy.md` 为准。

### 7.2 不对外暴露内容

- YAMNet top-K 分类不作为业务 mood / genre 输出。
- 内部诊断标签不作为搜索推荐正式标签。
- embedding 不作为云端可靠关联依据。

### 7.3 存储要求

需要保存：

- `localSongId`
- embedding BLOB 或等价序列化结果。
- `modelName`
- `modelVersion`
- `featureSchemaVersion`
- `costMs`
- `updatedAt`
- 失败或跳过原因。

embedding 序列化格式必须可测试、可反序列化、可版本化。

内部诊断字段至少包括：

- `costMs`
- top-K internal diagnostics
- 输入策略
- 输出 tensor shape
- 失败原因

上述字段不属于 ResultProvider 对外消费的 `LocalFeature` 字段。

`costMs` 仅作为内部存储、诊断和性能评估字段，不属于 ResultProvider 对外消费的 `LocalFeature` 字段。

## 8. top-K 分类诊断边界

YAMNet 原始 top-K 分类仅用于内部诊断或实验。

约束：

- 不通过 ResultProvider 作为业务标签输出。
- 不命名为业务 mood / genre。
- 不参与可靠关联判断。
- 不影响强展示或强推荐。
- 如 Demo 展示，需要明确标注为 internal diagnostics。

## 9. 调度与体验保护

### 9.1 触发方式

MVP-4 本地特征生成由 FeaturePipeline 或调度任务异步触发，不在指纹 Mock 比对同步调用栈中直接执行。

职责分工：

- `FeaturePipeline` 负责决定歌曲当前是否进入本地特征阶段、写入阶段状态和消费模型输出。
- `LocalFeatureScheduler` 负责从可处理队列中按批次取任务，并根据设备条件、业务开关、重试次数决定立即执行、暂停、跳过或失败。
- `LocalEmbeddingModel` 负责模型加载与推理，不直接决定业务 lifecycle state。

状态写入规则：

- `FeaturePipeline` 触发 embedding 生成前写入 `LOCAL_FEATURE_EXTRACTING`。
- embedding 生成成功后写入 `LOCAL_FEATURE_READY`。
- 失败、跳过、等待按既有失败处理规则写入 `FAILED` / `SKIPPED` / `WAITING_TO_CONTINUE`。

### 9.2 暂停条件

以下条件下应暂停或延后模型推理：

- 正在播放音乐。
- 电量低。
- 设备温度高。
- 前台交互繁忙。
- 用户关闭本地特征能力。
- 模型资源不可用。

### 9.3 失败处理

- 模型不可用：进入 `WAITING_TO_CONTINUE` 或 `SKIPPED`，并记录原因。
- 模型加载失败：按技术失败记录 retry count 和 last reason。
- 推理失败：按技术失败记录 retry count 和 last reason。
- 超过最大重试次数：进入 `FAILED`。
- 模型关闭：不作为技术失败，不消耗 retry count。

## 10. ResultProvider 消费语义

MVP-4 不改变 MVP-1/2/3 的 ResultProvider 对外语义。

新增要求：

- `LOCAL_FEATURE_EXTRACTING` 对调用方表示处理中，本地特征结果未完成。
- `LOCAL_FEATURE_EXTRACTING` 不应被调用方当作可用本地特征。
- 成功生成 embedding 后，调用方可查询 `LOCAL_FEATURE_READY`。
- `LOCAL_FEATURE_READY` 表示本地特征兜底可用，不表示云端可靠关联。
- `LOCAL_FEATURE_READY` 不等于 `RELIABLY_ASSOCIATED`。
- `association` 仍为空或保持原有候选语义，不写入可靠云端关联。
- 模型关闭时，调用方仍可查询原有 `UNASSOCIATED` 或 `CANDIDATE_ASSOCIATED` 状态。
- 模型失败时，调用方可读取 last reason。

## 11. Demo 验证入口

### 11.1 页面能力

`demo` 需要支持：

- 打开/关闭本地特征能力。
- 展示本地特征输入队列。
- 对单首歌曲触发 embedding 生成。
- 对队列批量触发 embedding 生成。
- 展示模型名称、模型版本、schema 版本。
- 展示 embedding 维度和 costMs；`costMs` 需要标注为 diagnostic，不属于 ResultProvider 对外消费字段。
- 展示 top-K internal diagnostics。
- 展示 ResultProvider 查询结果。

### 11.2 Demo 场景

必须支持：

- 指纹 none -> YAMNet 推理 -> `LOCAL_FEATURE_READY`。
- `RELIABLY_ASSOCIATED` 不进入本地特征队列。
- `CANDIDATE_ASSOCIATED` 默认不进入队列。
- 配置打开后，`CANDIDATE_ASSOCIATED` 可进入队列。
- 模型能力关闭。
- 模型文件缺失。
- 模型加载失败。
- 推理失败。
- 模型版本变化 -> `OUTDATED`。

## 12. 单元测试清单

### 12.1 队列测试

- `UNASSOCIATED` 可进入本地特征队列。
- `RELIABLY_ASSOCIATED` 不进入本地特征队列。
- `CANDIDATE_ASSOCIATED` 默认不进入队列。
- `CANDIDATE_ASSOCIATED` 配置打开后进入队列。
- `FAILED` / `SKIPPED` / `WAITING_TO_CONTINUE` 不进入队列。

### 12.2 开关测试

- 开关关闭时不触发模型加载。
- 开关关闭时不触发模型推理。
- 开关关闭时流程保持 `UNASSOCIATED` 或原候选状态。
- 开关重新打开后可重新生成队列。

### 12.3 模型测试

- YAMNet TFLite 可加载或给出明确失败原因。
- 模型不可用可进入 waiting/skipped。
- 推理失败记录 retry count 和 last reason。
- 推理成功输出 embedding。
- 模型名称、版本、schema 版本可记录。

### 12.4 embedding 存储测试

- embedding 可序列化。
- embedding 可反序列化。
- schema 版本变化可标记 `OUTDATED`。
- 存储失败记录明确原因。

### 12.5 top-K 边界测试

- YAMNet top-K 不通过 ResultProvider 作为业务标签暴露。
- top-K 不命名为 mood / genre。
- top-K 不影响可靠关联状态。

### 12.6 ResultProvider 测试

- `LOCAL_FEATURE_READY` 可查询 embedding 和模型元信息。
- `LOCAL_FEATURE_READY` 不等于 `RELIABLY_ASSOCIATED`。
- 模型关闭时仍可查询原状态。
- 模型失败时可查询 last reason。

## 13. 集成测试清单

- 指纹 none -> YAMNet 推理 -> `LOCAL_FEATURE_READY` -> ResultProvider 查询 embedding。
- `RELIABLY_ASSOCIATED` -> 不进入本地特征队列。
- `CANDIDATE_ASSOCIATED` 默认不进入队列。
- `CANDIDATE_ASSOCIATED` 配置打开 -> YAMNet 推理 -> `LOCAL_FEATURE_READY`。
- 模型关闭 -> 流程结束为未关联或候选，不阻塞整体漏斗。
- 模型文件缺失 -> 记录原因 -> ResultProvider 可查询。
- 模型加载失败 -> 记录原因 -> 不影响基础信息和指纹结果。
- 模型版本/schema 变化 -> 标记 `OUTDATED`。
- Demo 页面可展示模型开关、模型元信息、embedding 维度、costMs 和最终状态。

## 14. 验收标准

MVP-4 完成必须满足：

- 可从符合条件的 `UNASSOCIATED` 状态生成本地特征输入队列。
- `CANDIDATE_ASSOCIATED` 默认不进入队列，配置打开后才进入。
- `RELIABLY_ASSOCIATED` 不进入本地特征队列。
- 本地特征能力开关关闭时不触发模型推理，整体流程仍可结束。
- YAMNet TFLite 可在开发环境或测试设备完成加载和推理验证，或给出明确不可行原因。
- 成功时可生成 embedding 并写入本地存储。
- ResultProvider 可查询 `LOCAL_FEATURE_READY`，且不会将其误认为云端可靠关联。
- YAMNet top-K 不作为业务 mood / genre 对外暴露。
- 模型包体、runtime 增量、性能、license 和下发策略形成后续决策输入。
- 构建与单元测试验证通过：`./gradlew :core:test :core:assemble :demo:assembleDebug`。

## 15. 后续交接

MVP-4 完成后需要向后续阶段交接：

- YAMNet TFLite 模型文件大小和 runtime 包体增量。
- 模型加载和推理耗时。
- embedding 维度、schema 版本和序列化格式。
- 推理失败率和主要失败原因。
- 是否需要切换 VGGish 或其他模型。
- 是否适合内置模型，或需要动态下载。
- 是否需要合规补充提示或用户开关调整。
- 本地 embedding 是否足以支持后续搜索推荐兜底实验。

## 16. 执行拆分（5 个里程碑）

在不改变 MVP-4 完整功能范围的前提下，执行按以下 5 个里程碑推进。每个里程碑都要求“实现 + 测试”闭环后再进入下一步，避免模型可行性、契约、推理、调度和 demo 混在同一次交付中导致验收口径漂移。

当前决策结论：

- 现在按完整开发启动 `MVP-4`。
- 当前必须从里程碑 1 开始，不允许跳过最小真实工程验证。
- 里程碑 1 完成前，不进入里程碑 2–5 的完整闭环实现。

进度标记：

- [x] 里程碑 1：文档、ADR 与最小真实工程验证
- [x] 里程碑 2：本地特征契约、队列与存储基线
- [x] 里程碑 3：模型输入生成与 YAMNet 推理接入
- [x] 里程碑 4：Scheduler 与 Pipeline 本地特征阶段
- [x] 里程碑 5：ResultProvider、Demo 与最终验收

### 16.1 里程碑 1：文档、ADR 与最小真实工程验证

产出：

- `decisions/2026-05-15-mvp4-yamnet-android-tflite-policy.md`
- `decisions/2026-05-15-mvp4-local-feature-contract-policy.md`
- 最小 `TensorFlow Lite` 依赖接入方案。
- 用于 demo / 开发验证的内置 `YAMNet` 模型产物。
- 单次真实模型加载与推理验证结果。
- Section 5 的准入条件、失败停止条件、ADR 引用。
- `dev-plan-v0.1.md` 中 `7.7 执行拆分` 引用。

完成标准：

- 文档中不再出现“实现前先确认”类悬空表述。
- 可直接从文档确认模型来源、license、runtime、包体、加载方式、go/no-go 规则。
- 实现者不需要再补 `YAMNet` 主路径和 `VGGish` failover 的决策。
- Android 端已完成一次真实模型加载。
- Android 端已完成一次真实 inference，并拿到非空 embedding 或明确失败原因。
- 里程碑 1 完成后，才允许进入里程碑 2。

验收记录：

- 代码产物：
  - `core/build.gradle.kts` 已接入 `org.tensorflow:tensorflow-lite:2.14.0`
  - `core/src/main/java/com/orion/blaster/core/localfeature/model/YamnetModelValidator.kt` 已提供最小真实模型验证路径
  - `demo/src/androidTest/java/com/orion/blaster/demo/YamnetInstrumentationTest.kt` 已提供设备侧 smoke test
  - `core/src/main/assets/models/yamnet.tflite` 已纳入开发验证资源
- 构建结果：
  - `./gradlew :core:testDebugUnitTest :demo:assembleDebug :demo:assembleDebugAndroidTest --no-daemon` 通过
- 设备侧真实验证结果：
  - `adb -s S4VCMV95CATGQGT4 shell am instrument -w -e class com.orion.blaster.demo.YamnetInstrumentationTest com.orion.blaster.demo.test/androidx.test.runner.AndroidJUnitRunner`
  - 输出：`OK (1 test)`
  - 设备日志：`inputShape=[1] outputShapes=[[1, 521], [1, 1024], [1, 64]] embeddingVectorCount=1024`
- 里程碑结论：
  - YAMNet 主路径可继续
  - 里程碑 1 完成，允许进入里程碑 2

### 16.2 里程碑 2：本地特征契约、队列与存储基线

产出：

- `LocalFeature` 对外字段约束。
- 本地特征内部诊断摘要边界。
- `LocalFeatureQueue`。
- `LocalFeatureToggle`。
- repository/store 的本地特征读写扩展。
- `OUTDATED` 版本触发规则。
- 对应单元测试：队列状态、开关关闭不入队、schema/version 变更。

完成标准：

- `UNASSOCIATED`、`CANDIDATE_ASSOCIATED`、`RELIABLY_ASSOCIATED` 的入队边界明确。
- `LOCAL_FEATURE_READY` 与可靠关联语义分离明确。
- 存储和状态契约已足以支持后续模型接入，不需要实现者二次发明数据结构。
- 单元测试要求已覆盖队列、开关、版本变化触发 `OUTDATED`。

验收记录：

- 代码产物：
  - `core/src/main/java/com/orion/blaster/core/model/MatchModels.kt`
    - `LocalFeature` 新增 `featureSchemaVersion`
    - 新增 `LocalFeatureDiagnostics` 与 `LocalFeatureTopClass`
  - `core/src/main/java/com/orion/blaster/core/featuretoggle/LocalFeatureToggle.kt`
  - `core/src/main/java/com/orion/blaster/core/featurequeue/LocalFeatureQueue.kt`
  - `core/src/main/java/com/orion/blaster/core/store/FeatureRepository.kt`
  - `core/src/main/java/com/orion/blaster/core/store/InMemoryFeatureRepository.kt`
- 存储能力完成项：
  - 已支持 `save/get LocalFeature`
  - 已支持 `save/get LocalFeatureDiagnostics`
  - 已支持 `markLocalFeatureOutdatedIfVersionChanged`（基于 modelVersion/schemaVersion）
- 测试产物：
  - `core/src/test/java/com/orion/blaster/core/featurequeue/LocalFeatureQueueTest.kt`
  - `core/src/test/java/com/orion/blaster/core/store/InMemoryFeatureRepositoryTest.kt`（新增本地特征存储与版本失效测试）
  - `core/src/test/java/com/orion/blaster/core/pipeline/AudioIdentityPipelineTest.kt`（同步仓储接口扩展）
- 测试结果：
  - `./gradlew :core:testDebugUnitTest --tests '*LocalFeatureQueueTest' --tests '*InMemoryFeatureRepositoryTest' --tests '*AudioIdentityPipelineTest' --no-daemon` 通过
- 里程碑结论：
  - 里程碑 2 完成，允许进入里程碑 3

### 16.3 里程碑 3：模型输入生成与 YAMNet 推理接入

产出：

- `AudioModelInputGenerator`
- 输入策略记录字段。
- `LocalEmbeddingModel`。
- embedding 输出、模型版本、schema version、diagnostic top-K 边界。
- 对应单元测试：输入生成、推理成功、模型缺失、加载失败、推理失败。

完成标准：

- 文档足以指导实现“单首歌曲 -> embedding”最小闭环。
- top-K 被明确限制为 internal diagnostics，不进入业务结果。
- 推理失败路径、模型缺失路径、加载失败路径都有明确验收口径。
- 单元测试要求能够直接映射到模型输入/推理代码。

验收记录：

- 代码产物：
  - `core/src/main/java/com/orion/blaster/core/modelinput/AudioModelInputGenerator.kt`
  - `core/src/main/java/com/orion/blaster/core/embedding/LocalEmbeddingModel.kt`
- 输入策略与产物边界：
  - `AudioModelInputGenerator` 已复用现有 PCM 解码路径并输出固定长度输入
  - `LocalEmbeddingModel` 已输出 `LocalFeature`（embedding/modelVersion/schemaVersion）
  - top-K 仅进入 `LocalFeatureDiagnostics`，不进入对外 `LocalFeature` 字段
- 失败路径覆盖：
  - 模型缺失：`model_missing:*`
  - 模型加载失败：`model_load_failed:*`
  - 推理失败：`inference_failed:*`
- 测试产物：
  - `core/src/test/java/com/orion/blaster/core/modelinput/AudioModelInputGeneratorTest.kt`
  - `core/src/test/java/com/orion/blaster/core/embedding/LocalEmbeddingModelTest.kt`
- 测试结果：
  - `./gradlew :core:testDebugUnitTest --tests '*AudioModelInputGeneratorTest' --tests '*LocalEmbeddingModelTest' --tests '*LocalFeatureQueueTest' --tests '*InMemoryFeatureRepositoryTest' --no-daemon` 通过
  - `./gradlew :core:testDebugUnitTest --no-daemon` 通过
- 里程碑结论：
  - 里程碑 3 完成，允许进入里程碑 4

### 16.4 里程碑 4：Scheduler 与 Pipeline 本地特征阶段

产出：

- `LocalFeatureScheduler`。
- `FeaturePipeline` 本地特征阶段状态流。
- retry / skip / waiting 规则。
- 端到端集成测试：`UNASSOCIATED -> LOCAL_FEATURE_READY`、候选默认不入队、设备保护条件。

完成标准：

- 本地特征阶段能被接入现有漏斗而不改变既有云端语义。
- `LOCAL_FEATURE_EXTRACTING -> LOCAL_FEATURE_READY / WAITING_TO_CONTINUE / SKIPPED / FAILED` 流转明确。
- 设备保护条件、开关关闭、模型资源不可用的分支都已有清晰测试口径。
- 集成测试要求足以验证 scheduler 与 pipeline 的协同行为。

验收记录：

- 代码产物：
  - `core/src/main/java/com/orion/blaster/core/scheduler/LocalFeatureScheduler.kt`
  - `core/src/main/java/com/orion/blaster/core/pipeline/FeaturePipeline.kt`（新增 `processLocalFeatureQueue` 与本地特征失败分支处理）
  - `core/src/main/java/com/orion/blaster/core/featurequeue/LocalFeatureQueue.kt`（补充队列输入字段）
- 状态流实现结果：
  - `UNASSOCIATED/CANDIDATE_ASSOCIATED` 入队由 `LocalFeatureQueue + LocalFeatureToggle` 控制
  - 执行中写入 `LOCAL_FEATURE_EXTRACTING`
  - 成功写入 `LOCAL_FEATURE_READY`
  - 失败按原因进入 `WAITING_TO_CONTINUE / SKIPPED / FAILED`
- 分支语义：
  - `model_missing` 与设备保护条件进入 `WAITING_TO_CONTINUE`（不计技术重试）
  - `model_load_failed / inference_failed / decode_error` 计技术重试，超上限转 `FAILED`
- 测试产物：
  - `core/src/test/java/com/orion/blaster/core/scheduler/LocalFeatureSchedulerTest.kt`
  - `core/src/test/java/com/orion/blaster/core/pipeline/LocalFeaturePipelineTest.kt`
- 测试结果：
  - `./gradlew :core:testDebugUnitTest --tests '*LocalFeatureSchedulerTest' --tests '*LocalFeaturePipelineTest' --tests '*AudioIdentityPipelineTest' --tests '*LocalFeatureQueueTest' --no-daemon` 通过
  - `./gradlew :core:testDebugUnitTest --no-daemon` 通过
- 里程碑结论：
  - 里程碑 4 完成，允许进入里程碑 5

### 16.5 里程碑 5：ResultProvider、Demo 与最终验收

产出：

- `ResultProvider` 暴露 `localFeature`。
- Demo MVP-4 闭环。
- Demo 展示项：开关、队列、单首/批量触发、模型信息、embedding 维度、diagnostic `costMs`、top-K internal diagnostics、失败/缺失场景。
- 最终验收记录。
- 构建与测试通过记录。

完成标准：

- demo 可复现文档关键场景。
- `LOCAL_FEATURE_READY` 不会被误判为 `RELIABLY_ASSOCIATED`。
- 最终验证命令统一为 `./gradlew :core:test :core:assemble :demo:assembleDebug`。
- 文档中的验收项都能映射到 demo 证据或自动化测试结果。

验收记录：

- 代码产物：
  - `core/src/test/java/com/orion/blaster/core/result/ResultProviderTest.kt`（新增 `LOCAL_FEATURE_READY` 语义测试）
  - `demo/src/main/java/com/orion/blaster/demo/MainActivity.kt`（新增本地特征开关、候选入队开关、本地特征执行入口与结果展示）
  - `demo/src/main/res/layout/activity_main.xml`（新增本地特征 guard/candidate spinner 与触发按钮）
  - `demo/src/main/res/values/strings.xml`（新增 MVP-4 demo 文案）
- demo 展示能力：
  - 支持本地特征开关（`ALLOW/DISABLED/MODEL_MISSING/...`）
  - 支持候选是否入队切换（`UNASSOCIATED_ONLY/INCLUDE_CANDIDATE`）
  - 支持展示 local feature queue、embedding 维度、模型信息、`costMs`、top-K internal diagnostics、failure reason
- 自动化验证结果：
  - `./gradlew :core:test :core:assemble :demo:assembleDebug --no-daemon` 通过
  - `ResultProvider` 语义验证通过：`LOCAL_FEATURE_READY != RELIABLY_ASSOCIATED`
- 里程碑结论：
  - 里程碑 5 完成，MVP-4 五个里程碑全部完成。

## 17. 主代码去占位与真实化整改（MVP-4+Demo）

进度标记：

- [x] Step 1：建立主代码禁 Mock 红线
- [x] Step 2：真实接入 MediaStore 扫描
- [x] Step 3：移除主流程中的测试扫描器与硬编码 demo 歌曲
- [x] Step 4：移除假音频输入分支，统一真实解码
- [x] Step 5：主代码替换 MockCloud 网关（不接后端，返回明确失败语义）
- [ ] Step 6：收口验收与防回归

### 17.1 Step 1：建立主代码禁 Mock 红线

验收记录：

- 新增主代码校验任务：`./gradlew checkMainNoMock`
- 校验范围：`core/src/main`、`demo/src/main`
- 禁止 token：
  - `MockCloudMatchGateway`
  - `TestLocalSongScanner`
  - `content://demo/`
  - `DemoAudio`
  - `not wired`
- README 已补充“主代码约束（强制）”与执行命令。

### 17.2 Step 2：真实接入 MediaStore 扫描

验收记录：

- 新增 `MediaStoreLocalSongScanner`：`core/src/main/java/com/orion/blaster/core/scanner/MediaStoreLocalSongScanner.kt`
- `demo` 中 `ScanSource.MEDIA_STORE` 已接入真实扫描，不再走“not wired”短路
- `demo` 已补齐读取音频权限声明与运行时申请：
  - `READ_MEDIA_AUDIO`（Android 13+）
  - `READ_EXTERNAL_STORAGE`（Android 12 及以下）
- 构建验证通过：
  - `./gradlew :core:assemble :demo:assembleDebug --no-daemon`

### 17.3 Step 3：移除主流程中的测试扫描器与硬编码 demo 歌曲

验收记录：

- `demo/src/main/java/com/orion/blaster/demo/MainActivity.kt` 主流程已移除：
  - `TestLocalSongScanner`
  - `TestSongRecord`
  - `stableRecords()/changedSignatureRecords()` 假数据路径
- Demo 扫描源改为 `MEDIA_STORE` 真实路径，扫描展示基于仓储中的真实歌曲数据。
- 构建验证通过：
  - `./gradlew :demo:assembleDebug --no-daemon`

### 17.4 Step 4：移除假音频输入分支，统一真实解码

验收记录：

- `DemoAudioModelInputGenerator` 已移除 `content://demo/*` 快捷成功分支。
- 主流程模型输入统一走 `PcmDecoder + DefaultAudioModelInputGenerator`。
- 主代码搜索验证：
  - `rg -n "content://demo/" core/src/main demo/src/main -S` 无命中。
- 构建验证通过：
  - `./gradlew :demo:assembleDebug --no-daemon`

### 17.5 Step 5：主代码替换 MockCloud 网关（不接后端，返回明确失败语义）

验收记录：

- 新增 `NoopCloudMatchGateway`：`core/src/main/java/com/orion/blaster/core/gateway/NoopCloudMatchGateway.kt`
- `demo/src/main/java/com/orion/blaster/demo/MainActivity.kt` 已从 `MockCloudMatchGateway` 切换为 `NoopCloudMatchGateway`。
- 语义：
  - 基础匹配、音频匹配均返回 `MatchResult.ERROR`
  - `rejectReason = service_not_configured`
  - 不伪造 `RELIABLY_ASSOCIATED`
- 测试产物：
  - `core/src/test/java/com/orion/blaster/core/gateway/NoopCloudMatchGatewayTest.kt`
