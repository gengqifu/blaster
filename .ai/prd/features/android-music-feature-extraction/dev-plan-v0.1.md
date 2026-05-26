# Android 本地音乐特征能力开发计划 v0.1

## 1. 目标与边界

### 1.1 当前版本目标

本开发计划用于把 PRD v0.1 与 Tech Design v0.1 拆解为可执行工程阶段。

当前版本目标是完成 Android 客户端独立闭环：

- 发现本地歌曲。
- 提取本地歌曲基础信息。
- 通过 Mock 服务端模拟云端歌曲关联。
- 在基础信息无法可靠关联时，进入音频识别兜底链路。
- 在仍无法可靠关联时，可选启用本地特征兜底能力。
- 向搜索、推荐、播放等调用方提供可消费的当前业务结果。

当前版本不依赖真实云端服务。所有云端能力通过 `MockCloudMatchGateway` 模拟。

### 1.2 当前版本不包含

- 不接入真实云端接口。
- 不进行真实云端联调。
- 不维护云端曲库或云端指纹库。
- 不自训练模型。
- 不承诺业务 `mood` / `genre` 标签输出。
- 不做线上灰度、真实用户数据评估和线上指标闭环。
- 不把候选关联结果当作可靠关联结果消费。

### 1.3 代码落点原则

- `core` 模块承载 SDK 核心能力。
- `demo` 模块用于验证客户端闭环、Mock 场景和调用方消费结果。
- Mock 与未来真实服务端实现必须共用同一个 `CloudMatchGateway` 抽象。
- 客户端业务漏斗不得依赖 Mock 专用分支。

## 2. 阶段总览

| 阶段 | 目标 | 是否依赖真实云端 | 是否依赖真实模型 |
| --- | --- | --- | --- |
| MVP-1 | 客户端 Mock 闭环骨架 | 否 | 否 |
| MVP-2 | 本地歌曲扫描与基础信息 Mock 匹配 | 否 | 否 |
| MVP-3 | 音频解码、指纹生成与 Mock 比对 | 否 | 否 |
| MVP-4 | 可选本地 embedding 验证 | 否 | 可使用测试资源或开发包 |

## 3. 详细 MVP 计划索引

| 阶段 | 详细计划 | 状态 |
| --- | --- | --- |
| MVP-1 | `mvp-plans/mvp-1-client-mock-loop.md` | 已补齐 |
| MVP-2 | `mvp-plans/mvp-2-local-scan-basic-match.md` | 已补齐 |
| MVP-3 | `mvp-plans/mvp-3-audio-fingerprint-mock-match.md` | 已补齐 |
| MVP-4 | `mvp-plans/mvp-4-local-embedding-validation.md` | 已补齐 |
| Search/Recommend（新增） | `tech-design-search-recommend-v0.1.md`、`dev-plan-search-recommend-v0.1.md`、`decisions/2026-05-21-search-recommend-sdk-contract-policy.md`、`decisions/2026-05-21-search-recommend-ranking-policy.md`、`decisions/2026-05-21-search-recommend-hybrid-extension-policy.md` | 规划中 |
| 资源占用测试（新增） | `test-plan-resource-profile-v0.1.md` | 规划中 |

## 4. MVP-1：客户端 Mock 闭环骨架

### 4.1 开发任务

- 定义核心领域对象：本地歌曲、基础信息、关联结果、本地特征结果、处理状态、调用方查询结果。
- 定义 `CloudMatchGateway` 抽象。
- 实现 `MockCloudMatchGateway`。
- 实现处理漏斗编排骨架。
- 实现 ResultProvider 查询能力。
- 实现本地存储基础结构，用于保存歌曲、关联结果、任务状态和错误原因。
- 在 `demo` 中提供最小验证入口，展示不同 Mock 场景下的结果。

### 4.2 输入与输出

输入：

- 构造的本地歌曲测试数据。
- Mock 匹配规则。
- 调用方查询请求。

输出：

- 可靠关联结果。
- 候选关联结果。
- 未关联结果。
- 处理中、失败、跳过、过期等状态。
- ResultProvider 对外查询结果。

### 4.3 不包含范围

- 不扫描真实设备音乐库。
- 不读取真实音频文件。
- 不生成真实音频指纹。
- 不接入 YAMNet / VGGish。
- 不接真实服务端。

### 4.4 单元测试

- `LifecycleState` 状态转换测试。
- `MockCloudMatchGateway` reliable / candidate / none / error / timeout / degrade 规则测试（不直接返回 `OUTDATED`）。
- ResultProvider 状态映射测试。
- 候选关联不得被映射为可靠关联测试。
- 失败重试计数与最终失败状态测试。
- `OUTDATED` 与 `WAITING_TO_CONTINUE` 区分测试。
- `OUTDATED` 仅由 `FeatureRepository.markOutdated(localSongId)` 触发测试。

### 4.5 集成测试

- 使用构造歌曲跑通基础 Mock 漏斗。
- 验证 reliable 场景可输出可靠关联。
- 验证 candidate 场景仅输出候选关联。
- 验证 none 场景可继续进入后续阶段占位。
- 验证 error / timeout 场景可记录原因并进入重试或失败路径。
- 验证 ResultProvider 能被 demo 查询并展示。

### 4.6 验收标准

- 不依赖真实云端即可跑通客户端状态流转。
- Mock 至少覆盖 reliable、candidate、none、error、timeout、degrade 六类场景。
- 调用方能区分可靠关联、候选关联、未关联、处理中、失败、跳过、过期。
- 候选关联不会被 ResultProvider 标记为可继承云端能力。
- `./gradlew :core:assemble :demo:assembleDebug` 通过。

### 4.7 执行拆分

- MVP-1 执行按 5 个里程碑推进（模型状态基线 -> 网关 Mock -> 存储实现 -> Pipeline/ResultProvider -> Demo/验收）。
- 详细拆分与每步完成标准见：`mvp-plans/mvp-1-client-mock-loop.md` 第 12 章“执行拆分（5 个里程碑）”。

## 5. MVP-2：本地歌曲扫描与基础信息 Mock 匹配

### 5.1 开发任务

- 实现本地歌曲扫描能力。
- 实现基础信息提取能力。
- 实现文件变更识别，避免未变化歌曲重复触发高成本处理。
- 将扫描结果写入本地存储。
- 将基础信息提交给 `CloudMatchGateway.matchByBasicInfo`。
- 用 Mock 服务端模拟基础信息可靠关联、候选关联和未关联。
- 在 `demo` 中展示真实或测试歌曲的基础信息处理结果。

### 5.2 输入与输出

输入：

- 设备可访问的本地音频文件，或测试资源中的音频样本。
- 文件基础信息与可解析的歌曲信息。
- Mock 基础信息匹配规则。

输出：

- 本地歌曲记录。
- 基础信息记录。
- 基础信息关联结果。
- 需要继续音频识别的歌曲队列。

### 5.3 不包含范围

- 不生成真实音频指纹。
- 不执行本地 embedding 推理。
- 不接真实云端基础信息匹配接口。
- 不处理所有罕见音频格式，首版只保证常见格式路径可验证。

### 5.4 单元测试

- 基础信息解析成功测试。
- 基础信息缺失、乱码、空字段兜底测试。
- 文件变更识别测试。
- 未变化歌曲不重复处理测试。
- 基础信息 Mock 匹配规则测试。

### 5.5 集成测试

- 扫描测试歌曲并写入本地存储。
- 基础信息 reliable 命中后停止后续高成本处理。
- 基础信息 candidate 命中后保存候选，并允许继续后续识别。
- 基础信息 none 后进入音频识别待处理队列。
- 无权限或不可访问文件进入 skipped 或 waiting 状态。

### 5.6 验收标准

- 可从本地或测试资源生成稳定的本地歌曲列表。
- 可提取基础信息并提交 Mock 匹配。
- 可正确保存可靠关联、候选关联和未关联结果。
- 已处理且未变化的歌曲不会重复触发基础信息匹配。
- 基于真实扫描或测试资源扫描产生的基础信息关联结果可通过 ResultProvider 查询。
- 构建验证通过：`./gradlew :core:assemble :demo:assembleDebug`。

## 6. MVP-3：音频解码、指纹生成与 Mock 比对

### 6.1 开发任务

- 实现音频解码到 PCM 的基础能力。
- 实现音频识别输入生成能力。
- 接入 Chromaprint 类音频指纹方案，首版使用 `chromaprint-compatible` 算法标识。
- 通过 JNI/NDK 封装指纹生成能力。
- 生成 `AudioIdentityMatchRequest`。
- 将音频指纹请求提交给 `CloudMatchGateway.matchByAudioIdentity`。
- 使用 Mock 服务端模拟指纹可靠命中、候选命中、未命中、异常和超时。
- 增加高成本任务的暂停、重试、失败和降级处理。
- 基础信息匹配结果为 `NONE` 或 `CANDIDATE` 后，由 FeaturePipeline 或调度任务异步触发音频指纹生成流程。

### 6.2 输入与输出

输入：

- 基础信息无法可靠关联的本地歌曲。
- 可访问的音频文件。
- Mock 指纹匹配规则。

输出：

- 本地音频识别摘要或指纹记录。
- 指纹 Mock 比对结果。
- 可靠关联、候选关联或未关联状态。
- 解码失败、指纹生成失败、服务异常、超时等错误结果。

### 6.3 不包含范围

- 不接真实云端指纹比对服务。
- 不维护本地云端指纹库。
- 不实现云端近似匹配索引。
- 不要求覆盖所有音频格式。
- 不执行本地 embedding 推理。

### 6.4 单元测试

- PCM 解码输入参数校验测试。
- 短音频、普通歌曲、长音频的截取策略测试。
- 指纹 payload 生成结果格式测试。
- 指纹算法名、版本和 payload 编码记录测试。
- 指纹 Mock reliable / candidate / none / error / timeout 测试。
- 高成本任务开关关闭时的跳过测试。
- 重试次数超过上限后转 failed 测试。

### 6.5 集成测试

- 基础信息 none 后进入指纹生成链路。
- 指纹 reliable 后写入可靠关联。
- 指纹 candidate 后保存候选，但不进入强展示或强推荐。
- 指纹 none 后进入本地特征兜底或未关联结束。
- 解码失败、格式不支持、权限失效、Mock 超时均可给出明确状态。
- 播放中、低电量、高温、前台繁忙等条件下可暂停或降级高成本任务。

### 6.6 验收标准

- 能回答并验证“音频指纹怎么提取”：本地音频解码为 PCM，再生成 chromaprint-compatible 指纹 payload。
- 不依赖真实云端即可验证完整指纹比对业务分支。
- 指纹生成结果可持久化并可追踪算法名、版本和处理耗时。
- Mock 指纹比对可覆盖可靠、候选、未命中、异常、超时和降级。
- 高成本任务不会明显影响播放和前台交互体验。
- 构建验证通过：`./gradlew :core:assemble :demo:assembleDebug`。

### 6.7 执行拆分

- MVP-3 执行按 6 个里程碑推进（文档/ADR/契约 -> 队列/存储/Mock -> PCM 解码/片段策略 -> Chromaprint Native -> Scheduler/Pipeline -> Demo/验收）。
- MVP-3 以完整音频指纹识别功能为目标，不停留在“工程可行性 + 接口基线”。
- Chromaprint 接入、license、ABI 和请求契约决策已固化到 MVP-3 详细计划与 ADR，后续实现按里程碑逐步闭环。
- 详细拆分与每步完成标准见：`mvp-plans/mvp-3-audio-fingerprint-mock-match.md` 第 15 章“执行拆分（6 个里程碑）”。

## 7. MVP-4：可选本地 embedding 验证

### 7.1 开发任务

- 增加本地特征能力开关。
- 验证 YAMNet TFLite 在 Android 端加载、推理和输出 embedding 的可行性。
- 评估 VGGish 作为备选模型的接入成本和包体影响。
- 将本地 embedding 与模型元信息写入本地存储。
- 明确 YAMNet 原始 top-K 分类仅用于内部诊断或实验，不作为业务 mood / genre 输出。
- 确保模型能力关闭时，整体漏斗仍可正常结束。

### 7.2 输入与输出

输入：

- 未可靠关联且允许启用本地特征的歌曲。
- 测试资源或开发包中的预训练模型。
- 本地特征能力开关配置。

输出：

- 本地 embedding。
- 模型名称、模型版本、特征 schema 版本。
- 本地特征生成成功、失败、跳过、过期状态。

### 7.3 不包含范围

- 不自训练模型。
- 不微调模型。
- 不承诺业务 mood / genre 标签。
- 不要求模型进入正式包体。
- 不确定模型内置或动态下载策略。
- 不接真实云端补全标签能力。

### 7.4 单元测试

- 本地特征开关开启和关闭测试。
- 模型不可用时跳过或失败状态测试。
- embedding 序列化和反序列化测试。
- 模型版本变化后标记 `OUTDATED` 测试。
- YAMNet top-K 不对外暴露为业务标签测试。

### 7.5 集成测试

- 未关联歌曲进入本地特征兜底。
- 模型加载成功后生成 embedding 并写入本地存储。
- 模型关闭时歌曲可结束为未关联或候选关联，不阻塞整体漏斗。
- 模型失败时可记录原因并不影响基础关联和指纹链路。
- ResultProvider 可区分 `LOCAL_FEATURE_READY` 与 `UNASSOCIATED`。

### 7.6 验收标准

- YAMNet TFLite 可在开发环境或测试设备完成加载和推理验证。
- embedding 可被存储、读取并通过 ResultProvider 暴露为本地特征兜底结果。
- 模型能力关闭时，客户端完整流程仍可闭环；该能力在 MVP-1 已覆盖，MVP-4 作为回归验证。
- 本版本不把模型分类结果作为业务 mood / genre 对外承诺。
- 模型包体、性能、隐私和下发策略形成后续决策输入。
- 构建验证通过：`./gradlew :core:assemble :demo:assembleDebug`。

### 7.7 执行拆分

- MVP-4 执行按 5 个里程碑推进（文档/ADR/准入门槛 -> 本地特征契约/队列/存储 -> 模型输入/推理 -> Scheduler/Pipeline -> Demo/验收）。
- MVP-4 以完整本地 embedding 兜底闭环为目标，但执行顺序必须先收口 YAMNet/TFLite 准入条件与本地特征契约。
- YAMNet 主路径、license/runtime/包体准入规则和本地特征契约已固化到 MVP-4 详细计划与 ADR，后续实现按里程碑逐步闭环。
- 详细拆分与每步完成标准见：`mvp-plans/mvp-4-local-embedding-validation.md` 第 16 章“执行拆分（5 个里程碑）”。

## 8. 调用方验收标准

### 8.0 Search/Recommend 文档入口

- 端侧搜索与推荐能力技术设计：`tech-design-search-recommend-v0.1.md`
- 端侧搜索与推荐能力开发计划：`dev-plan-search-recommend-v0.1.md`
- SDK 契约 ADR：`decisions/2026-05-21-search-recommend-sdk-contract-policy.md`
- 排序与降级 ADR：`decisions/2026-05-21-search-recommend-ranking-policy.md`
- 端云混排扩展 ADR：`decisions/2026-05-21-search-recommend-hybrid-extension-policy.md`
- 本节仅作引用入口，具体接口、策略、降级与里程碑以对应文档为准。

### 8.1 搜索侧

- 可读取可靠关联歌曲，并按云端歌曲能力参与搜索消费。
- 可读取未关联但具备本地基础信息的歌曲，用于本地搜索兜底。
- 可识别候选关联，且默认不当作可靠关联使用。
- 可识别处理中、失败、跳过、过期状态。

### 8.2 推荐侧

- 可读取可靠关联歌曲，并继承云端推荐能力。
- 可读取 `LOCAL_FEATURE_READY` 歌曲，作为本地特征兜底候选。
- 不把 `CANDIDATE_ASSOCIATED` 当作可靠关联进入强推荐。
- 模型能力关闭时，推荐侧仍可消费基础关联和指纹关联结果。

### 8.3 播放侧

- 可查询本地歌曲当前业务结果。
- 不因后台扫描、解码、指纹或模型任务影响播放稳定性。
- 高成本任务在播放中可暂停、降级或延后。

## 9. 所有 MVP 完成后的总体验收标准

- 当前版本所有功能不依赖真实云端服务。
- `MockCloudMatchGateway` 能覆盖 reliable、candidate、none、error、timeout、degrade。
- MVP-1 至 MVP-4 全部完成后，完整漏斗可验证：扫描 -> 基础信息 -> Mock 匹配 -> 指纹 -> Mock 比对 -> 本地特征兜底 -> 调用方消费。
- Section 9 是所有 MVP 完成后的端到端验收，不作为单个 MVP 或单个 PR 的验收条件。
- 模型能力关闭时，基础关联与音频识别链路仍可独立结束。
- 候选关联不会被误用为可靠关联。
- 已处理且未变化歌曲不会重复执行高成本处理。
- 过期结果可被标记为 `OUTDATED` 并等待重算。
- 隐私敏感信息不进入普通诊断日志。
- 构建验证通过：`./gradlew :core:assemble :demo:assembleDebug`。

## 10. 测试矩阵

| 测试类别 | 覆盖内容 | 阶段 |
| --- | --- | --- |
| 单元测试 | 状态流转、Mock 规则、ResultProvider 映射、重试与降级 | MVP-1 |
| 单元测试 | 基础信息解析、变更识别、基础信息 Mock 匹配 | MVP-2 |
| 单元测试 | 音频截取策略、指纹 payload、指纹 Mock 匹配、错误重试 | MVP-3 |
| 单元测试 | 模型开关、embedding 存取、OUTDATED、分类结果不外露 | MVP-4 |
| 集成测试 | 构造歌曲跑通 Mock 闭环 | MVP-1 |
| 集成测试 | 扫描歌曲并完成基础信息关联 | MVP-2 |
| 集成测试 | 解码、指纹生成、Mock 比对、异常降级 | MVP-3 |
| 集成测试 | YAMNet 加载、embedding 生成、模型关闭闭环 | MVP-4 |
| 构建验证 | `:core:assemble`、`:demo:assembleDebug` | 每个 MVP |

## 11. 风险与后续依赖

### 11.1 当前版本风险

- Android 本地媒体权限和不同系统版本行为差异。
- 本地音频格式兼容性不足。
- Chromaprint 类库 JNI/NDK 接入成本和 ABI 包体影响。
- 后台任务在不同厂商 ROM 上被限制。
- YAMNet TFLite 包体、推理耗时和内存峰值可能超过预期。
- 本地音乐内容识别涉及隐私和合规评审。

### 11.2 后续版本依赖

- 真实云端基础信息匹配接口。
- 真实云端指纹比对接口。
- 服务端 reliable / candidate / none 判定标准。
- 服务端 payload 格式和版本兼容策略。
- 模型内置或动态下载策略。
- 线上误绑定率、覆盖率、处理成功率和体验影响评估方案。

## 12. 交付物

- `core` 中的客户端 SDK 能力实现。
- `demo` 中的验证入口和结果展示。
- Mock 场景配置或测试 fixture。
- 单元测试与集成测试。
- 构建验证记录。
- MVP-4 的模型验证报告或结论记录。

## 13. 默认假设

- 当前版本云端全部 Mock，不接真实云端。
- 开发计划只规划当前版本，不规划真实云端联调版本。
- 以 PRD v0.1 和 Tech Design v0.1 为基准，若基准文档变更，需要同步更新开发计划。
- `core` 承载 SDK 能力，`demo` 承载验证流程。
- YAMNet 作为本地 embedding 首选验证模型，VGGish 作为备选。
- Gemma 不作为主链路模型。
