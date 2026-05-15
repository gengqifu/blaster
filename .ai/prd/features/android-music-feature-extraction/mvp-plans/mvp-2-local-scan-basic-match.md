# MVP-2 详细开发计划：本地歌曲扫描与基础信息 Mock 匹配

## 1. 目标与非目标

### 1.1 目标

MVP-2 目标是把 MVP-1 中的构造歌曲输入替换为真实扫描或测试资源扫描输入，并跑通基础信息 Mock 匹配链路。

MVP-2 完成后应具备：

- 可从设备 `MediaStore` 或测试资源发现本地歌曲。
- 可提取或构造用于基础信息匹配的 `BasicSongInfo`。
- 可识别新增、变更、未变化、删除或不可访问歌曲。
- 未变化歌曲不重复执行基础信息 Mock 匹配。
- 可通过 `CloudMatchGateway.matchByBasicInfo` 执行 Mock 匹配。
- 可保存 reliable、candidate、none、error 结果。
- 可通过 ResultProvider 查询真实扫描或测试资源扫描产生的当前业务结果。

### 1.2 非目标

MVP-2 不做：

- 不生成音频指纹。
- 不解码 PCM。
- 不接 Chromaprint / JNI / NDK。
- 不接 YAMNet / VGGish / TFLite。
- 不接真实云端服务。
- 不维护云端曲库或云端指纹库。
- 不要求覆盖所有罕见音频格式。
- 不实现最终本地搜索排序或推荐排序。

## 2. 模块拆分

### 2.1 `core` 模块

MVP-2 在 MVP-1 基础上新增或细化以下职责：

| 职责 | 建议包 | 内容 |
| --- | --- | --- |
| 本地歌曲扫描 | `com.orion.blaster.core.scanner` | `LocalSongScanner`、扫描结果、扫描错误 |
| 基础信息提取 | `com.orion.blaster.core.metadata` | `BasicInfoExtractor`、文件名兜底解析 |
| 变更识别 | `com.orion.blaster.core.signature` | `ContentSignature` 生成和比较 |
| 处理漏斗 | `com.orion.blaster.core.pipeline` | 扫描结果进入基础信息匹配流程 |
| 存储接口 | `com.orion.blaster.core.store` | 扩展歌曲、基础信息、match、job state 存取能力 |
| Mock 匹配 | `com.orion.blaster.core.mock` | 复用 MVP-1 `MockCloudMatchGateway` 基础信息规则 |

MVP-2 可以继续沿用 repository 抽象。是否在 MVP-2 直接落地 Room，可在实现阶段根据复杂度决定；文档要求接口边界必须支持后续 Room 替换。

### 2.2 `demo` 模块

`demo` 需要增加：

- 触发本地扫描或测试资源扫描的入口。
- 展示扫描到的歌曲列表。
- 展示每首歌曲的基础信息。
- 选择或加载基础信息 Mock 规则。
- 触发基础信息匹配。
- 展示 ResultProvider 查询结果。

### 2.3 与 MVP-1 差异与迁移顺序

MVP-2 在 MVP-1 基线上按以下顺序迁移，避免一次性改动过大导致回归定位困难：

1. 扩展模型字段（可空 + 默认值优先），保持 MVP-1 单曲输入路径可继续运行。
2. 扩展 repository 接口，补齐 signature、扫描可见性和状态更新能力。
3. 新增扫描批处理编排和 demo 扫描入口，接入真实扫描或测试资源扫描。

兼容约束：

- `FeaturePipeline.process(localSong)` 在 MVP-2 保留兼容，作为单曲回归测试和故障定位入口。
- MVP-2 主流程新增批处理入口，不移除 MVP-1 入口。

## 3. 核心接口契约（必须落地）

### 3.1 扫描输出对象

MVP-2 扫描层最小输出对象需要包含：

- `localSongId`
- `uri`
- `title`
- `artist`
- `album`
- `durationMs`
- `sizeBytes`
- `dateModified`
- `mimeType`
- `contentSignature`
- `sourceState`

说明：

- 扫描层只输出“可观测事实”，不伪造缺失字段。
- `sourceState` 最小保持与现有生命周期协同：可访问、不可访问、已删除。

### 3.2 BasicSongInfo 扩展契约

`BasicSongInfo` 在现有字段基础上新增：

- `source`
- `qualityFlags`

`source` 最小枚举：

- `MEDIA_STORE`
- `METADATA_RETRIEVER`
- `FILENAME_FALLBACK`
- `TEST_CONSTRUCTED`

`qualityFlags` 最小枚举集合（可按实现定义为 enum set 或字符串集合）：

- `MISSING_FIELD`
- `GARBLED_TEXT`
- `CONFLICTING_METADATA`
- `FALLBACK_USED`
- `DEGRADED_SIGNATURE_INPUT`

### 3.3 Repository 扩展契约

MVP-2 repository 在 MVP-1 接口之上必须新增以下能力：

- 按 `localSongId` 读写最近一次 `contentSignature`。
- 在扫描完成后标记“本轮不可见但历史存在”的歌曲为删除或不可访问。
- 按生命周期状态查询待后续处理集合（至少支持 `CANDIDATE_ASSOCIATED`、`UNASSOCIATED` 查询）。
- 读取当前歌曲最近处理结果（用于未变化歌曲跳过匹配时直接复用）。

以上接口边界必须保持对 MVP-3 可复用，不引入 MVP-2 专属临时字段。

## 4. 本地歌曲扫描

### 4.1 扫描来源

MVP-2 支持两类扫描来源：

- 真实设备 `MediaStore`：用于验证 Android 本地媒体扫描能力。
- 测试资源或构造数据：用于无设备音乐库、无权限或 CI 环境下的稳定测试。

扫描入口应抽象为统一接口，避免 pipeline 依赖具体来源。

### 4.2 扫描输出

扫描输出需要包含：

- `localSongId`
- `uri`
- `title`
- `artist`
- `album`
- `durationMs`
- `sizeBytes`
- `dateModified`
- `mimeType`
- `contentSignature`
- `sourceState`

字段允许为空的情况需要显式保留，不应在扫描阶段伪造可靠信息。

`localSongId` 生成策略：

- MVP-2 默认使用规范化后的 `uri` 生成 hash，作为稳定 `localSongId`。
- `MediaStore._id` 只作为诊断或辅助字段，不作为主 ID。
- 如果同一文件的 URI 发生变化，MVP-2 视为新增歌曲；同文件合并不在 MVP-2 解决，后续可通过音频指纹或更强识别能力处理。

### 4.3 权限与不可访问处理

- 没有媒体访问权限时，扫描结果应进入 `WAITING_TO_CONTINUE` 或等价待授权状态。
- MVP-2 不区分首次拒绝和永久拒绝，统一进入 `WAITING_TO_CONTINUE` 或等价待授权状态。
- 单首歌曲不可访问时，记录跳过原因并进入 `SKIPPED`。
- 已存在歌曲后续不可访问时，应更新状态，避免继续参与基础信息匹配。

## 5. 基础信息提取

### 5.1 信息来源优先级

基础信息提取顺序：

1. 优先使用系统媒体库字段。
2. 系统字段缺失或异常时，尝试 `MediaMetadataRetriever`。
3. 仍缺失时，使用文件名兜底解析。

MVP-2 只要求生成基础信息匹配所需字段，不要求识别歌词、封面或高级音频属性。

### 5.2 标准化规则

基础信息需要做最小标准化：

- 去除前后空白。
- 空字符串转为空值。
- 明显乱码或不可用字段标记为缺失。
- 文件名兜底结果需要标记来源，避免误认为可靠元数据。
- 时长缺失时允许继续匹配，但需要在请求中保留缺失状态。

### 5.3 输出对象

`BasicSongInfo` 至少包含：

- `localSongId`
- `title`
- `artist`
- `album`
- `durationMs`
- `source`
- `qualityFlags`

`source` 用于区分系统字段、metadata retriever、文件名兜底或测试构造数据。

`source` 最小枚举：

- `MEDIA_STORE`
- `METADATA_RETRIEVER`
- `FILENAME_FALLBACK`
- `TEST_CONSTRUCTED`

`qualityFlags` 用于表达缺失、乱码、冲突、兜底等信息，供 Mock 规则和后续真实服务参考。

## 6. 变更识别与去重

### 6.1 content signature

MVP-2 使用以下字段生成 `contentSignature`：

- `uri`
- `sizeBytes`
- `dateModified`
- `durationMs`

如果部分字段缺失，应使用可用字段生成降级 signature，并记录 `qualityFlags`。

`contentSignature` 规范固定如下：

- 输入字段顺序固定：`uri|sizeBytes|dateModified|durationMs`。
- 空值占位符固定：`<null>`。
- 文本标准化固定：去前后空白，使用 UTF-8 编码。
- 哈希算法固定：`SHA-256`。
- 同一输入必须稳定生成同一 signature。

降级规则：

- 任一字段为 `<null>` 仍可生成 signature。
- 只要存在 `<null>` 输入，`qualityFlags` 必须包含 `DEGRADED_SIGNATURE_INPUT`。

### 6.2 状态判断

扫描后需要判断：

- 新增：本地不存在该 `localSongId` 或 signature 记录。
- 变更：存在记录但 `contentSignature` 变化。
- 未变化：存在记录且 `contentSignature` 不变。
- 删除或不可访问：上次存在，本次扫描不可见或无法读取。

### 6.3 去重规则

- 未变化歌曲不重复执行基础信息 Mock 匹配。
- 变更歌曲需要重新提取基础信息并重新匹配。
- 删除或不可访问歌曲不进入基础信息匹配。
- 若同一文件被重复扫描，应以最新扫描记录覆盖旧扫描记录。

## 7. 批处理入口与状态流

MVP-2 新增批处理入口（命名可等价）：

- `scanAndProcess(source, forceScenario?)`

状态流固定为：

1. 执行扫描（`MediaStore` 或测试资源）。
2. 对扫描输出执行新增/变更/未变化/删除或不可访问判定。
3. 仅新增与变更歌曲进入基础信息提取和 `matchByBasicInfo`。
4. 保存匹配结果并更新生命周期状态。
5. 通过 ResultProvider 暴露可查询结果。

规则：

- 未变化歌曲跳过基础信息匹配，保留历史关联结果并更新必要的扫描时间戳。
- 删除或不可访问歌曲不进入匹配。

## 8. 基础信息 Mock 匹配

### 8.1 触发条件

基础信息 Mock 匹配只对以下歌曲触发：

- 新增歌曲。
- 内容变更歌曲。
- 之前基础信息匹配失败但允许重试的歌曲。
- 手动重试的歌曲。

### 8.2 分流规则

- `RELIABLE`：保存为 `RELIABLY_ASSOCIATED`，当前阶段结束，不进入高成本处理。
- `CANDIDATE`：保存为 `CANDIDATE_ASSOCIATED`，候选不可强展示或强推荐，并标记为 MVP-3 可继续音频识别。
- `NONE`：保存为 `UNASSOCIATED`，并标记为 MVP-3 可继续音频识别。
- `ERROR`：记录 `rejectReason`，按 MVP-1 重试策略处理。

MVP-2 不新增 `pendingAudioIdentify` 字段。`CANDIDATE_ASSOCIATED` 和 `UNASSOCIATED` 状态本身表达“后续可进入音频识别”的语义，MVP-3 以这两个状态作为音频识别输入队列。

`TIMEOUT`、`DEGRADED` 不作为 `MatchResult` 枚举值，仍通过 `ERROR` 的 `rejectReason` 或诊断原因表达。

### 8.3 Mock 规则复用

MVP-2 复用 MVP-1 的 Mock 规则语义：

- first-match。
- 支持 `localSongId`、`titleContains`、`artistContains`、`forceScenario`。
- 无规则命中默认 `NONE`。
- Mock 可构造 reliable、candidate、none、error、timeout、degrade。

## 9. ResultProvider 消费语义

MVP-2 不改变 MVP-1 的 ResultProvider 对外语义。

新增要求：

- 调用方查询到的结果必须来自真实扫描或测试资源扫描产生的歌曲记录。
- `RELIABLY_ASSOCIATED` 可被调用方视为可继承云端歌曲能力。
- `CANDIDATE_ASSOCIATED` 仅为候选，不可默认强展示或强推荐。
- `UNASSOCIATED` 可作为本地搜索兜底输入。
- `WAITING_TO_CONTINUE` 表示权限、系统状态或其他条件暂不满足。
- `SKIPPED` 表示单首歌曲不可访问或不满足处理条件。

## 10. 测试资源扫描机制

MVP-2 需要提供稳定、可重复的测试资源扫描通道，避免依赖设备实时媒体库。

最小机制：

- 提供 `TestLocalSongScanner`（或等价实现），通过固定测试清单产出扫描结果。
- 测试清单记录 `uri/title/artist/album/durationMs/sizeBytes/dateModified/mimeType/sourceState`。
- 支持通过替换测试清单记录模拟新增、变更、删除、不可访问。

约束：

- CI 场景默认使用测试资源扫描，不依赖 `MediaStore` 权限。
- signature 变化测试通过修改构造记录完成，不要求修改真实音频文件。

## 11. Demo 验证入口

### 11.1 页面能力

`demo` 需要支持：

- 触发真实设备扫描。
- 触发测试资源或构造数据扫描。
- 展示扫描歌曲列表。
- 展示每首歌曲的基础信息和 `contentSignature`。
- 选择 Mock 基础信息匹配规则。
- 触发基础信息匹配。
- 展示 ResultProvider 查询结果。

### 11.2 最小交互闭环

Demo 页面动作顺序固定为：

1. 选择扫描源（真实设备或测试资源）。
2. 执行扫描。
3. 展示扫描歌曲列表与 `contentSignature`。
4. 触发基础信息匹配。
5. 展示 ResultProvider 结果。

重复扫描要求：

- 同一批次重复扫描必须可直观看到“未变化歌曲不重复匹配”。

### 11.3 Demo 场景

必须支持：

- 基础信息完整且 reliable。
- 基础信息完整但 candidate。
- 基础信息完整但 none。
- 标题或歌手缺失。
- 文件名兜底。
- 乱码或异常字段。
- 未变化歌曲跳过重复匹配。
- 删除或不可访问歌曲。
- Mock error / timeout / degrade。

乱码或异常字段场景通过测试资源或构造数据稳定模拟，不依赖真实设备上偶现的异常音乐文件。

## 12. 单元测试清单

单元测试按以下分组组织：

- `scanner`
- `metadata`
- `signature`
- `pipeline-batch`
- `repository-delta`

### 12.1 scanner

- 测试资源扫描可生成稳定歌曲列表。
- `MediaStore` 结果可映射为本地歌曲条目。
- 无权限时进入 waiting 或等价状态。
- 单首不可访问歌曲进入 skipped。

### 12.2 metadata

- 系统字段完整时优先使用系统字段。
- 系统字段缺失时可使用 metadata retriever 结果。
- metadata retriever 失败时使用文件名兜底。
- 空字符串转为空值。
- 乱码字段标记为缺失或异常。
- `qualityFlags` 可表达缺失、冲突和兜底来源。

### 12.3 signature

- 新增歌曲触发基础信息匹配。
- `contentSignature` 不变时不重复匹配。
- `contentSignature` 变化时重新匹配。
- 删除或不可访问歌曲不进入匹配。
- 降级 signature 可生成并记录质量标记。

signature 变化测试应通过测试代码修改构造扫描结果或 repository 记录完成，不要求修改真实音频文件。

### 12.4 pipeline-batch

- reliable 保存为 `RELIABLY_ASSOCIATED`。
- candidate 保存为 `CANDIDATE_ASSOCIATED`，并保留候选信息。
- none 保存为 `UNASSOCIATED`，并标记为后续可继续音频识别。
- error 记录 `rejectReason` 并按重试策略处理。
- timeout / degrade 通过 `ERROR` 的诊断原因表达。

### 12.5 repository-delta

- 按 `localSongId` 读写 signature 正确。
- 可将“本轮不可见但历史存在”歌曲更新为删除或不可访问。
- 可查询后续待处理状态集合（`CANDIDATE_ASSOCIATED`、`UNASSOCIATED`）。
- 未变化歌曲可保留历史关联结果。

### 12.6 ResultProvider 回归测试

- 基于扫描歌曲可查询处理结果。
- reliable 可查询可靠关联。
- candidate 不可查询为可靠关联。
- unassociated 可查询为未关联。
- waiting / skipped 可查询原因。

## 13. 集成测试清单

固定主链路：

1. 测试资源扫描 -> 基础信息提取 -> Mock reliable -> ResultProvider 查询可靠关联。
2. 测试资源扫描 -> Mock candidate -> ResultProvider 查询候选关联。
3. 测试资源扫描 -> Mock none -> ResultProvider 查询 `UNASSOCIATED`。
4. 测试资源扫描 -> Mock error -> 按重试策略重试后进入 `FAILED`，并保留 `lastReason`。
5. 重复扫描未变化歌曲 -> 不重复执行 Mock 匹配。
6. 修改构造 signature -> 重新执行基础信息匹配。

error 集成路径判定口径：

- 该用例必须验证：重试计数递增、最终失败状态为 `FAILED`、`lastReason` 被正确保留。
- “重试后恢复成功”可作为可选扩展用例，不可替代失败终态用例。

补充场景：

- 不可访问歌曲 -> skipped 或 waiting。
- Demo 页面可展示扫描、基础信息、Mock 匹配和调用方结果。

## 14. 验收标准

MVP-2 完成必须满足：

- 可从真实设备 `MediaStore` 或测试资源生成稳定本地歌曲列表。
- 可提取基础信息并生成 `BasicSongInfo`。
- 可生成 `contentSignature` 并识别新增、变更、未变化、删除或不可访问歌曲。
- 未变化歌曲不会重复执行基础信息 Mock 匹配。
- 可通过 `CloudMatchGateway.matchByBasicInfo` 完成 reliable、candidate、none、error 分支。
- reliable、candidate、none、error 四个分支在集成测试清单中各有至少一条对应主链路，不仅由单元测试覆盖。
- candidate 和 none 可标记为 MVP-3 可继续音频识别。
- 基于扫描产生的结果可通过 ResultProvider 查询。
- 候选关联不会被标记为可继承云端能力。
- 构建验证通过：`./gradlew :core:assemble :demo:assembleDebug`。
- `core` 单元测试通过。

## 15. 与 MVP-3 的交接

MVP-2 需要为 MVP-3 保留以下交接点：

- `CANDIDATE_ASSOCIATED` 歌曲可继续进入音频识别。
- `UNASSOCIATED` 歌曲可继续进入音频识别。
- MVP-3 以 `CANDIDATE_ASSOCIATED` 和 `UNASSOCIATED` 状态作为音频识别输入队列，不依赖额外 pending 字段。
- `BasicSongInfo` 需要传递给后续 `AudioIdentityMatchRequest`。
- `localSongId`、`uri`、`durationMs`、`contentSignature` 需要可被后续音频解码和指纹流程读取。
- 失败、跳过、等待状态不应自动进入音频指纹生成。

## 16. 本文档默认假设

- 本轮按“接口契约优先”补强，不在 MVP-2 引入 Room。
- `TIMEOUT`、`DEGRADED` 继续通过 `ERROR.rejectReason` 表达，不扩展 `MatchResult` 枚举。
- MVP-2 不新增 `pendingAudioIdentify` 字段；MVP-3 继续以 `CANDIDATE_ASSOCIATED` 和 `UNASSOCIATED` 作为音频识别输入队列。

## 17. 执行拆分（5 个里程碑）

在不改变 MVP-2 范围的前提下，执行按以下 5 个里程碑推进。每个里程碑都要求“实现 + 测试”闭环后再进入下一步，避免跨阶段混测导致验收口径漂移。

里程碑总览：

- [x] 里程碑 1：模型与契约基线（已完成）
- [x] 里程碑 2：扫描与基础信息提取（已完成）
- [x] 里程碑 3：增量判定与批处理编排（已完成）
- [x] 里程碑 4：Mock 匹配全分支集成（已完成）
- [x] 里程碑 5：Demo 接入与最终验收（已完成）

### 17.1 里程碑 1：模型与契约基线

目标：

- 锁定 MVP-2 领域模型扩展字段和仓储接口扩展边界，确保后续实现不会反复改接口。

实现项：

- 锁定 `LocalSong` 扩展字段（`uri/sizeBytes/dateModified/mimeType/contentSignature`）。
- 锁定 `BasicSongInfo` 扩展字段（`source/qualityFlags`）。
- 锁定 signature 相关类型与最小枚举集合。
- 锁定 repository 新接口签名：signature 读写、删除/不可访问标记、待处理集合查询。

本里程碑必过测试：

- 契约编译通过（接口与模型可被现有模块引用）。
- 模型与契约类单元测试通过。

完成标准：

- 模型/接口编译通过。
- 契约测试覆盖关键字段与最小枚举语义。

完成产物：

- `LocalSong` 已扩展 `uri/sizeBytes/dateModified/mimeType/contentSignature`，并保持可空兼容。
- `BasicSongInfo` 已扩展 `source/qualityFlags` 并提供默认值。
- 已新增 `BasicInfoSource`、`QualityFlag` 最小枚举集合。
- `FeatureRepository` 与 `InMemoryFeatureRepository` 已落地 signature 读写、删除/不可访问标记、按状态查询契约。
- 本里程碑不包含扫描器实现、metadata 提取器实现和 signature 计算流程实现。

### 17.2 里程碑 2：扫描与基础信息提取

目标：

- 打通“扫描发现 -> 基础信息提取 -> signature 生成”的能力基础。

实现项：

- 落地 `LocalSongScanner` 抽象，支持 `MediaStore` 与 `TestLocalSongScanner` 双来源。
- 落地 `BasicInfoExtractor` 提取优先级链路：MediaStore -> MetadataRetriever -> 文件名兜底。
- 落地 `contentSignature` 规范实现：固定顺序、`<null>` 占位、UTF-8、`SHA-256`、降级 flag。

本里程碑必过测试：

- `scanner` 分组测试。
- `metadata` 分组测试。
- `signature` 分组测试。

完成标准：

- 扫描输出和基础信息输出满足 Section 3/4/5/6 契约。
- `scanner/metadata/signature` 单测稳定通过。

完成产物：

- 已新增 `LocalSongScanner` 抽象与 `TestLocalSongScanner` 测试实现，支持稳定测试资源扫描输入。
- 已新增 `BasicInfoExtractor`，实现 MediaStore -> MetadataRetriever -> 文件名兜底优先级链路。
- 已新增 `contentSignature` 生成器，固定字段顺序、`<null>` 占位、UTF-8 和 `SHA-256`，并在降级输入时产出 `DEGRADED_SIGNATURE_INPUT` 标记。
- 已补齐 `scanner`、`metadata`、`signature` 三组单元测试并通过。

### 17.3 里程碑 3：增量判定与批处理编排

目标：

- 让 MVP-2 主流程从“单曲输入”升级为“扫描批处理输入”，并具备增量去重能力。

实现项：

- 落地 `scanAndProcess(source, forceScenario?)` 批处理入口（命名可等价）。
- 落地新增/变更/未变化/删除/不可访问判定逻辑。
- 落地状态写入与历史结果复用策略（未变化跳过匹配）。

本里程碑必过测试：

- `pipeline-batch` 分组测试。
- `repository-delta` 分组测试。

完成标准：

- 未变化歌曲不触发重复匹配可被稳定验证。
- `pipeline-batch/repository-delta` 单测通过。

完成产物：

- 已新增 `scanAndProcess(source, forceScenario?)` 批处理入口，并保留 `process(localSong)` 兼容路径。
- 已落地新增/变更/未变化/删除/不可访问判定，未变化歌曲跳过匹配并保留历史结果。
- 已新增 `ScanSource`、`ScanProcessSummary` 类型用于批处理输入与统计输出。
- 已在 repository 层补充 `getAllLocalSongIds()` 并完成内存实现，支持删除差集判定。
- 已补齐 `pipeline-batch` 与 `repository-delta` 对应测试场景并通过回归。

### 17.4 里程碑 4：Mock 匹配全分支集成

目标：

- 在集成层打通基础信息匹配四分支，确保与 Section 13/14 验收口径一致。

实现项：

- 打通 reliable/candidate/none/error 端到端链路。
- 明确 error 用例校验口径：重试计数、最终 `FAILED`、`lastReason` 保留。
- 保持“恢复成功”为可选扩展用例，不替代失败终态用例。

本里程碑必过测试：

- Section 13 固定主链路 1-6 全部通过。

完成标准：

- 集成测试能够稳定覆盖四分支与增量行为。
- 集成层覆盖与 Section 14 验收分支要求一致。

完成产物：

- 已新增 `Mvp2IntegrationTest`，基于 `scanAndProcess + ResultProvider` 验证 reliable/candidate/none/error 四分支端到端链路。
- 已验证 error 分支重试与失败口径：`retryCount` 递增、最终状态 `FAILED`、`lastReason` 保留。
- 已验证“未变化跳过匹配”行为：重复扫描不重复调用 `matchByBasicInfo`。
- 已验证“signature 变化重匹配”行为：同歌 signature 变化后触发重新匹配并更新状态。
- Section 13 固定主链路 1-6 已具备对应自动化集成测试覆盖并通过。

### 17.5 里程碑 5：Demo 接入与最终验收

目标：

- 输出可演示、可验收的 MVP-2 交付版本。

实现项：

- 完成 demo 固定闭环：选源 -> 扫描 -> 展示 signature -> 触发匹配 -> 查询结果。
- 保证 Demo 场景与文档定义语义一致。
- 归拢最终构建与测试验收证据。

本里程碑必过测试：

- demo 验证流程通过。
- `core` 单测通过。
- `./gradlew :core:assemble :demo:assembleDebug` 通过。

完成标准：

- Section 14 全量验收条目可由测试结果与 demo 证据一一对应。
- MVP-2 文档定义能力可被稳定复现与回归验证。

完成产物：

- Demo 已升级为 MVP-2 闭环：扫描源选择 -> 场景选择 -> 执行扫描匹配 -> 展示批处理摘要与结果列表。
- Demo 已支持二次扫描（changed-signature）入口，用于演示未变化跳过与 signature 变化重匹配。
- `MEDIA_STORE` 在 demo 侧提供占位提示，不阻塞 `TEST_RESOURCES` 主验收路径。
- Demo 结果展示已包含：
  - 批处理摘要：`scanned/new/changed/unchanged/deleted/unavailable/matched/skipped`
  - 歌曲结果：`localSongId/lifecycleState/contentSignature/association/candidates/lastReason`

验收记录：

- 命令通过：`./gradlew --no-daemon :core:test`
- 命令通过：`./gradlew --no-daemon :core:assemble :demo:assembleDebug`
- Demo 输出文本样例（节选）：
  - `summary: scanned=3, new=2, changed=0, unchanged=0, deleted=0, unavailable=1, matched=2, skipped=1`
  - `demo-song-1 | state=RELIABLY_ASSOCIATED | signature=sig-stable-1 | association=cloud-demo-song-1 | candidates=0 | lastReason=null`
  - `demo-song-3 | state=WAITING_TO_CONTINUE | signature=null | association=null | candidates=0 | lastReason=unavailable`
