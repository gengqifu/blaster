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

## 3. 本地歌曲扫描

### 3.1 扫描来源

MVP-2 支持两类扫描来源：

- 真实设备 `MediaStore`：用于验证 Android 本地媒体扫描能力。
- 测试资源或构造数据：用于无设备音乐库、无权限或 CI 环境下的稳定测试。

扫描入口应抽象为统一接口，避免 pipeline 依赖具体来源。

### 3.2 扫描输出

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

### 3.3 权限与不可访问处理

- 没有媒体访问权限时，扫描结果应进入 `WAITING_TO_CONTINUE` 或等价待授权状态。
- MVP-2 不区分首次拒绝和永久拒绝，统一进入 `WAITING_TO_CONTINUE` 或等价待授权状态。
- 单首歌曲不可访问时，记录跳过原因并进入 `SKIPPED`。
- 已存在歌曲后续不可访问时，应更新状态，避免继续参与基础信息匹配。

## 4. 基础信息提取

### 4.1 信息来源优先级

基础信息提取顺序：

1. 优先使用系统媒体库字段。
2. 系统字段缺失或异常时，尝试 `MediaMetadataRetriever`。
3. 仍缺失时，使用文件名兜底解析。

MVP-2 只要求生成基础信息匹配所需字段，不要求识别歌词、封面或高级音频属性。

### 4.2 标准化规则

基础信息需要做最小标准化：

- 去除前后空白。
- 空字符串转为空值。
- 明显乱码或不可用字段标记为缺失。
- 文件名兜底结果需要标记来源，避免误认为可靠元数据。
- 时长缺失时允许继续匹配，但需要在请求中保留缺失状态。

### 4.3 输出对象

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

## 5. 变更识别与去重

### 5.1 content signature

MVP-2 使用以下字段生成 `contentSignature`：

- `uri`
- `sizeBytes`
- `dateModified`
- `durationMs`

如果部分字段缺失，应使用可用字段生成降级 signature，并记录 `qualityFlags`。

### 5.2 状态判断

扫描后需要判断：

- 新增：本地不存在该 `localSongId` 或 signature 记录。
- 变更：存在记录但 `contentSignature` 变化。
- 未变化：存在记录且 `contentSignature` 不变。
- 删除或不可访问：上次存在，本次扫描不可见或无法读取。

### 5.3 去重规则

- 未变化歌曲不重复执行基础信息 Mock 匹配。
- 变更歌曲需要重新提取基础信息并重新匹配。
- 删除或不可访问歌曲不进入基础信息匹配。
- 若同一文件被重复扫描，应以最新扫描记录覆盖旧扫描记录。

## 6. 基础信息 Mock 匹配

### 6.1 触发条件

基础信息 Mock 匹配只对以下歌曲触发：

- 新增歌曲。
- 内容变更歌曲。
- 之前基础信息匹配失败但允许重试的歌曲。
- 手动重试的歌曲。

### 6.2 分流规则

- `RELIABLE`：保存为 `RELIABLY_ASSOCIATED`，当前阶段结束，不进入高成本处理。
- `CANDIDATE`：保存为 `CANDIDATE_ASSOCIATED`，候选不可强展示或强推荐，并标记为 MVP-3 可继续音频识别。
- `NONE`：保存为 `UNASSOCIATED`，并标记为 MVP-3 可继续音频识别。
- `ERROR`：记录 `rejectReason`，按 MVP-1 重试策略处理。

MVP-2 不新增 `pendingAudioIdentify` 字段。`CANDIDATE_ASSOCIATED` 和 `UNASSOCIATED` 状态本身表达“后续可进入音频识别”的语义，MVP-3 以这两个状态作为音频识别输入队列。

`TIMEOUT`、`DEGRADED` 不作为 `MatchResult` 枚举值，仍通过 `ERROR` 的 `rejectReason` 或诊断原因表达。

### 6.3 Mock 规则复用

MVP-2 复用 MVP-1 的 Mock 规则语义：

- first-match。
- 支持 `localSongId`、`titleContains`、`artistContains`、`forceScenario`。
- 无规则命中默认 `NONE`。
- Mock 可构造 reliable、candidate、none、error、timeout、degrade。

## 7. ResultProvider 消费语义

MVP-2 不改变 MVP-1 的 ResultProvider 对外语义。

新增要求：

- 调用方查询到的结果必须来自真实扫描或测试资源扫描产生的歌曲记录。
- `RELIABLY_ASSOCIATED` 可被调用方视为可继承云端歌曲能力。
- `CANDIDATE_ASSOCIATED` 仅为候选，不可默认强展示或强推荐。
- `UNASSOCIATED` 可作为本地搜索兜底输入。
- `WAITING_TO_CONTINUE` 表示权限、系统状态或其他条件暂不满足。
- `SKIPPED` 表示单首歌曲不可访问或不满足处理条件。

## 8. Demo 验证入口

### 8.1 页面能力

`demo` 需要支持：

- 触发真实设备扫描。
- 触发测试资源或构造数据扫描。
- 展示扫描歌曲列表。
- 展示每首歌曲的基础信息和 `contentSignature`。
- 选择 Mock 基础信息匹配规则。
- 触发基础信息匹配。
- 展示 ResultProvider 查询结果。

### 8.2 Demo 场景

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

## 9. 单元测试清单

### 9.1 扫描测试

- 测试资源扫描可生成稳定歌曲列表。
- `MediaStore` 结果可映射为本地歌曲条目。
- 无权限时进入 waiting 或等价状态。
- 单首不可访问歌曲进入 skipped。

### 9.2 基础信息测试

- 系统字段完整时优先使用系统字段。
- 系统字段缺失时可使用 metadata retriever 结果。
- metadata retriever 失败时使用文件名兜底。
- 空字符串转为空值。
- 乱码字段标记为缺失或异常。
- `qualityFlags` 可表达缺失、冲突和兜底来源。

### 9.3 变更识别测试

- 新增歌曲触发基础信息匹配。
- `contentSignature` 不变时不重复匹配。
- `contentSignature` 变化时重新匹配。
- 删除或不可访问歌曲不进入匹配。
- 降级 signature 可生成并记录质量标记。

signature 变化测试应通过测试代码修改构造扫描结果或 repository 记录完成，不要求修改真实音频文件。

### 9.4 Mock 匹配测试

- reliable 保存为 `RELIABLY_ASSOCIATED`。
- candidate 保存为 `CANDIDATE_ASSOCIATED`，并保留候选信息。
- none 保存为 `UNASSOCIATED`，并标记为后续可继续音频识别。
- error 记录 `rejectReason` 并按重试策略处理。
- timeout / degrade 通过 `ERROR` 的诊断原因表达。

### 9.5 ResultProvider 测试

- 基于扫描歌曲可查询处理结果。
- reliable 可查询可靠关联。
- candidate 不可查询为可靠关联。
- unassociated 可查询为未关联。
- waiting / skipped 可查询原因。

## 10. 集成测试清单

- 测试资源扫描 -> 基础信息提取 -> Mock reliable -> ResultProvider 查询可靠关联。
- 测试资源扫描 -> Mock candidate -> ResultProvider 查询候选关联。
- 测试资源扫描 -> Mock none -> ResultProvider 查询未关联并进入 MVP-3 待处理语义。
- 重复扫描未变化歌曲 -> 不重复执行 Mock 匹配。
- 修改测试歌曲 signature -> 重新执行基础信息匹配。
- 不可访问歌曲 -> skipped 或 waiting。
- Demo 页面可展示扫描、基础信息、Mock 匹配和调用方结果。

## 11. 验收标准

MVP-2 完成必须满足：

- 可从真实设备 `MediaStore` 或测试资源生成稳定本地歌曲列表。
- 可提取基础信息并生成 `BasicSongInfo`。
- 可生成 `contentSignature` 并识别新增、变更、未变化、删除或不可访问歌曲。
- 未变化歌曲不会重复执行基础信息 Mock 匹配。
- 可通过 `CloudMatchGateway.matchByBasicInfo` 完成 reliable、candidate、none、error 分支。
- candidate 和 none 可标记为 MVP-3 可继续音频识别。
- 基于扫描产生的结果可通过 ResultProvider 查询。
- 候选关联不会被标记为可继承云端能力。
- 构建验证通过：`./gradlew :core:assemble :demo:assembleDebug`。
- 若已引入单元测试任务，对应单元测试通过。

## 12. 与 MVP-3 的交接

MVP-2 需要为 MVP-3 保留以下交接点：

- `CANDIDATE_ASSOCIATED` 歌曲可继续进入音频识别。
- `UNASSOCIATED` 歌曲可继续进入音频识别。
- MVP-3 以 `CANDIDATE_ASSOCIATED` 和 `UNASSOCIATED` 状态作为音频识别输入队列，不依赖额外 pending 字段。
- `BasicSongInfo` 需要传递给后续 `AudioIdentityMatchRequest`。
- `localSongId`、`uri`、`durationMs`、`contentSignature` 需要可被后续音频解码和指纹流程读取。
- 失败、跳过、等待状态不应自动进入音频指纹生成。
