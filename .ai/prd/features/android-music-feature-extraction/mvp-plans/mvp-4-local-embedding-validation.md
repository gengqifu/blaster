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

## 5. YAMNet TFLite 验证

### 5.1 前置确认

MVP-4 实现启动前需要完成 YAMNet TFLite 的工程可行性确认：

- 模型文件来源。
- TensorFlow Lite runtime 依赖。
- 模型文件大小。
- runtime 包体增量。
- license 风险。
- Android 端加载方式。

如确认不可行，需要先更新本节模型口径，不能按 YAMNet TFLite 直接实现。

### 5.2 验证目标

YAMNet TFLite 验证目标：

- Android 端可加载模型。
- 可基于音频输入完成推理。
- 可获得 embedding。
- 可记录模型名称、模型版本和 schema 版本。
- 可记录推理耗时和失败原因。

### 5.3 模型输入

MVP-4 需要通过 `AudioModelInputGenerator` 生成模型所需音频输入。

输入来源：

- MVP-3 已验证的本地音频读取能力。
- 测试资源或开发包中的音频样本。

MVP-4 不在文档中锁死具体采样率、窗口长度和张量形态，具体参数以模型要求为准，但实现必须记录输入策略和模型版本。

### 5.4 模型资源策略

MVP-4 使用测试资源或开发包完成验证。

- 不承诺模型进入正式包体。
- 不决定模型内置或动态下载上线方案。
- 模型体积目标不超过 20MB。
- 若模型文件或 runtime 增量超出预期，应记录为后续包体决策风险。

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
- 构建验证通过：`./gradlew :core:assemble :demo:assembleDebug`。
- 若已引入单元测试任务，对应单元测试通过。

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
