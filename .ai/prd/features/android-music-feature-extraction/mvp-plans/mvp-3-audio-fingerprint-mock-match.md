# MVP-3 详细开发计划：音频解码、指纹生成与 Mock 比对

## 1. 目标与非目标

### 1.1 目标

MVP-3 目标是在不依赖真实云端的前提下，补齐音频识别兜底链路：从 MVP-2 产出的 `CANDIDATE_ASSOCIATED` / `UNASSOCIATED` 歌曲队列中取出可处理歌曲，解码为 PCM，生成 `chromaprint-compatible` 音频指纹 payload，并通过 Mock 服务端完成指纹比对分支验证。

MVP-3 完成后应具备：

- 可识别哪些歌曲需要进入音频识别队列。
- 可用系统能力将本地音频解码为 PCM。
- 可封装 Chromaprint 类指纹生成能力。
- 可生成 `AudioIdentityMatchRequest`。
- 可通过 `CloudMatchGateway.matchByAudioIdentity` 执行 Mock 指纹比对。
- 可覆盖 reliable、candidate、none、error、timeout、degrade 分支。
- 可记录算法名、版本、截取策略、payload 编码、耗时和失败原因。
- 可在播放中、低电量、高温、前台繁忙等条件下暂停或降级高成本任务。

### 1.2 非目标

MVP-3 不做：

- 不接真实云端指纹比对服务。
- 不维护云端指纹库或近似匹配索引。
- 不执行 YAMNet / VGGish embedding。
- 不做本地相似歌曲检索。
- 不实现本地推荐排序。
- 不要求覆盖所有音频格式。
- 不引入 FFmpeg 作为默认依赖。
- 不上传原始音频文件。

## 2. 模块拆分

### 2.1 `core` 模块

MVP-3 在 MVP-2 基础上新增或细化以下职责：

| 职责 | 建议包 | 内容 |
| --- | --- | --- |
| 音频识别队列 | `com.orion.blaster.core.audioqueue` | `AudioIdentityQueue`，从状态中筛选待处理歌曲 |
| PCM 解码 | `com.orion.blaster.core.decoder` | `PcmDecoder`，基于 `MediaExtractor + MediaCodec` 解码 PCM |
| 指纹生成 | `com.orion.blaster.core.fingerprint` | `AudioFingerprintExtractor`，封装 Chromaprint 类算法 |
| 请求生成 | `com.orion.blaster.core.audioidentity` | `AudioIdentifyInputGenerator`，生成 `AudioIdentityMatchRequest` |
| 调度保护 | `com.orion.blaster.core.scheduler` | `AudioIdentityScheduler`，控制批次、暂停、重试、降级 |
| 存储扩展 | `com.orion.blaster.core.store` | 保存音频识别摘要、状态、错误、耗时 |
| Mock 比对 | `com.orion.blaster.core.mock` | 复用 `MockCloudMatchGateway.matchByAudioIdentity` |

职责边界：

- `FeaturePipeline` 负责状态流转、阶段串联和结果写入。
- `AudioIdentityScheduler` 负责批次控制、设备条件检查、暂停、重试和降级决策。
- `AudioIdentityScheduler` 不直接解释匹配语义，匹配语义仍由 pipeline 根据 `MatchResponse` 写入状态。

### 2.2 `demo` 模块

`demo` 需要增加：

- 展示音频识别输入队列。
- 触发单首或批量指纹生成。
- 展示 `algorithm`、`clipPolicy`、`payloadEncoding`、payload 摘要、`costMs`。
- 选择 Mock 指纹比对规则。
- 展示音频识别后 ResultProvider 查询结果。
- 展示高成本能力关闭、播放中暂停、低电量/高温等降级场景的结果。

## 3. 音频识别输入队列

### 3.1 入队条件

MVP-3 只处理以下状态的歌曲：

- `CANDIDATE_ASSOCIATED`
- `UNASSOCIATED`

这些状态来自 MVP-2 基础信息 Mock 匹配结果。

### 3.2 不入队条件

以下状态不进入音频指纹生成：

- `RELIABLY_ASSOCIATED`
- `FAILED`
- `SKIPPED`
- `WAITING_TO_CONTINUE`
- `OUTDATED`
- `LOCAL_FEATURE_READY`

`OUTDATED` 需要先经过重算或状态恢复策略，不直接进入 MVP-3 队列。

### 3.3 队列输出

队列项至少包含：

- `localSongId`
- `uri`
- `durationMs`
- `contentSignature`
- `basicInfo`
- `currentLifecycleState`
- `retryCount`

## 4. 音频解码到 PCM

### 4.1 解码方案

MVP-3 使用 `MediaExtractor + MediaCodec` 将本地音频解码为 PCM。

原则：

- 不直接读取压缩文件内容做 hash。
- 不把文件 MD5 当作音频指纹。
- 不引入 FFmpeg 作为默认依赖。
- 系统不支持的格式记录失败或跳过原因。

### 4.2 支持格式口径

MVP-3 优先验证 Android 系统常见可解码格式，例如：

- MP3
- AAC / M4A
- WAV
- FLAC
- OGG

APE、WMA、DSF、DFF、CUE 等罕见格式不作为 MVP-3 验收要求。

### 4.3 解码失败处理

- 文件不可访问：进入 `SKIPPED` 或 `WAITING_TO_CONTINUE`，并记录原因。
- 格式不支持：进入 `SKIPPED` 或 `FAILED`，并记录原因。
- 解码异常：按技术失败记录 retry count 和 last reason。
- 超过重试上限：进入 `FAILED`。

## 5. 指纹生成

### 5.1 算法口径

MVP-3 使用 Chromaprint 类方案，首版标识为：

- `algorithm = chromaprint-compatible`

具体依赖、ABI 包体和 license 风险需要在实现前做工程确认，但 MVP-3 计划按该方案展开。

MVP-3 实现启动前必须完成 Chromaprint 类库的工程可行性确认，确认项包括依赖接入方式、ABI 包体增量和 license 风险。如确认不可行，需要先更新本节算法口径和 Section 14 交接内容，不能按 `chromaprint-compatible` 直接进入实现。

### 5.2 输入与输出

输入：

- 解码后的 PCM。
- 歌曲时长。
- 片段策略。
- 基础歌曲信息。

输出：

- `algorithm`
- `algorithmVersion`
- `clipPolicy`
- `payloadEncoding`
- `payload`
- `costMs`

payload 可为二进制或编码字符串，MVP-3 不在文档中锁死内部格式，只要求外层字段稳定。

### 5.3 片段策略

MVP-3 采用以下策略：

- 普通歌曲优先取较长中间片段。
- 短歌按可用长度处理，不强行补齐。
- 长音频可取代表性多段并合并为一个请求 payload。
- 如果成本允许，可支持整首生成指纹。

具体片段长度和采样格式由实现阶段根据指纹库要求确定，但必须写入 `clipPolicy`。

### 5.4 指纹不是文件 hash

MVP-3 必须明确：

- 音频指纹来自 PCM 或算法要求的音频采样输入。
- 不对 MP3 / AAC / FLAC 文件直接做 MD5 作为指纹。
- 文件 hash 只能用于文件变更或诊断，不用于云端歌曲识别。

## 6. AudioIdentityMatchRequest

MVP-3 需要生成 `AudioIdentityMatchRequest`，外层字段保持与 Tech Design 一致：

- `localSongId`
- `durationMs`
- `clipPolicy`
- `algorithm`
- `payloadEncoding`
- `payload`
- `basicInfo`

字段语义：

- `localSongId` 用于关联本地歌曲状态。
- `durationMs` 用于辅助 Mock 或未来服务端候选确认。
- `clipPolicy` 描述端侧截取策略。
- `algorithm` 描述当前音频识别算法。
- `payloadEncoding` 标识 payload 编码方式。
- `payload` 承载算法相关指纹内容。
- `basicInfo` 作为辅助匹配信息。

## 7. 指纹 Mock 比对

### 7.1 调用方式

MVP-3 使用：

```kotlin
CloudMatchGateway.matchByAudioIdentity(request)
```

仍然通过 `MockCloudMatchGateway` 实现，不接真实服务端。

### 7.2 Mock 场景

必须支持：

- 指纹 reliable。
- 指纹 candidate。
- 指纹 none。
- 指纹 error。
- 指纹 timeout。
- 指纹 degrade。

`MatchResult` 仍保持四值：

- `RELIABLE`
- `CANDIDATE`
- `NONE`
- `ERROR`

`timeout`、`degrade` 通过 `ERROR.rejectReason` 或诊断原因表达。

### 7.3 状态分流

- `RELIABLE`：保存为 `RELIABLY_ASSOCIATED`，可继承云端歌曲能力。
- `CANDIDATE`：保存或更新为 `CANDIDATE_ASSOCIATED`，仍不可强展示或强推荐。
- `NONE`：保存为 `UNASSOCIATED`，可进入 MVP-4 本地特征兜底。
- `ERROR`：记录 `rejectReason`，按阶段 retry count 和 last reason 处理。

`NONE` 不消耗技术失败重试次数。

## 8. 调度与体验保护

### 8.1 触发方式

MVP-3 指纹生成由 FeaturePipeline 或调度任务异步触发，不在基础信息匹配同步调用栈中直接执行。

职责分工：

- `FeaturePipeline` 负责决定歌曲当前是否进入音频识别阶段、写入阶段状态和消费 Mock 比对结果。
- `AudioIdentityScheduler` 负责从可处理队列中按批次取任务，并根据设备条件、业务开关、重试次数决定立即执行、暂停、跳过或失败。

### 8.2 暂停条件

以下条件下应暂停或延后指纹任务：

- 正在播放音乐。
- 电量低。
- 设备温度高。
- 前台交互繁忙。
- 用户关闭高成本能力。
- 媒体权限不可用。

### 8.3 重试策略

- 解码异常、指纹生成异常、Mock 服务异常可重试。
- `NONE` 不重试。
- 用户关闭能力、权限缺失、设备条件不满足不按技术失败重试。
- 超过最大重试次数后进入 `FAILED`。
- 暂时条件不满足进入 `WAITING_TO_CONTINUE`。

## 9. ResultProvider 消费语义

MVP-3 不改变 MVP-1/MVP-2 的 ResultProvider 对外语义。

新增要求：

- 指纹 reliable 后，调用方可查询 `RELIABLY_ASSOCIATED`。
- 指纹 candidate 后，调用方只能查询候选关联，不能当作可靠关联消费。
- 指纹 none 后，调用方查询为未关联，后续可由 MVP-4 兜底。
- 解码失败、指纹失败、Mock error 需要可查询 last reason。
- 高成本能力关闭或设备条件不允许时，调用方可查询 waiting/skipped 原因。

## 10. Demo 验证入口

### 10.1 页面能力

`demo` 需要支持：

- 展示待音频识别歌曲队列。
- 对单首歌曲触发音频指纹生成。
- 对队列批量触发音频指纹生成。
- 展示 `AudioIdentityMatchRequest` 摘要。
- 选择 Mock 指纹比对场景。
- 展示最终 ResultProvider 结果。
- 模拟高成本能力关闭、播放中暂停、低电量/高温等条件。

### 10.2 Demo 场景

必须支持：

- MVP-2 none -> 指纹 reliable。
- MVP-2 none -> 指纹 candidate。
- MVP-2 none -> 指纹 none。
- MVP-2 candidate -> 指纹 reliable。
- MVP-2 candidate -> 指纹 none。
- MVP-2 none -> 指纹 error -> retry -> failed。
- 解码失败。
- 格式不支持。
- Mock timeout。
- Mock degrade。
- 高成本能力关闭。

## 11. 单元测试清单

### 11.1 队列测试

- `CANDIDATE_ASSOCIATED` 进入音频识别队列。
- `UNASSOCIATED` 进入音频识别队列。
- `RELIABLY_ASSOCIATED` 不进入队列。
- `FAILED` / `SKIPPED` / `WAITING_TO_CONTINUE` 不进入队列。
- 队列项包含 `localSongId`、`uri`、`durationMs`、`basicInfo`。

### 11.2 解码测试

- 可访问常见格式可进入 PCM 解码流程。
- 不支持格式产生明确错误。
- 文件不可访问产生 skipped 或 waiting。
- 解码异常记录 retry count 和 last reason。

### 11.3 指纹生成测试

- 普通歌曲生成 `chromaprint-compatible` payload。
- 短歌按可用长度处理。
- 长音频可生成多段策略描述。
- `algorithm`、`algorithmVersion`、`clipPolicy`、`payloadEncoding`、`costMs` 可记录。
- payload 不为空或失败时给出明确原因。

### 11.4 请求生成测试

- `AudioIdentityMatchRequest` 外层字段完整。
- `basicInfo` 能随请求传递。
- payload 内部格式不影响外层字段稳定性。

### 11.5 Mock 比对测试

- 指纹 reliable 保存为 `RELIABLY_ASSOCIATED`。
- 指纹 candidate 保存为 `CANDIDATE_ASSOCIATED`。
- 指纹 none 保存为 `UNASSOCIATED`。
- 指纹 error 记录原因并按重试策略处理。
- timeout / degrade 通过 `ERROR` 的诊断原因表达。
- `NONE` 不消耗技术失败重试次数。

### 11.6 调度与体验保护测试

- 播放中暂停或延后指纹任务。
- 低电量暂停或延后指纹任务。
- 高温暂停或延后指纹任务。
- 用户关闭高成本能力后停止后续指纹任务。
- 超过最大重试次数后进入 `FAILED`。

## 12. 集成测试清单

- MVP-2 none -> 指纹生成 -> Mock reliable -> ResultProvider 查询可靠关联。
- MVP-2 reliable -> 指纹队列不包含该歌曲 -> ResultProvider 仍为 `RELIABLY_ASSOCIATED`。
- MVP-2 candidate -> 指纹生成 -> Mock reliable -> ResultProvider 查询可靠关联。
- MVP-2 candidate -> 指纹生成 -> Mock none -> ResultProvider 查询未关联或保留候选语义清晰。
- MVP-2 none -> 指纹 error -> retry -> failed。
- 指纹 Mock timeout -> retry -> failed 或恢复。
- 解码失败 -> 记录原因 -> ResultProvider 可查询。
- 高成本能力关闭 -> waiting/skipped，不阻塞基础信息结果查询。
- Demo 页面可展示队列、请求摘要、Mock 比对和最终状态。

## 13. 验收标准

MVP-3 完成必须满足：

- 可从 `CANDIDATE_ASSOCIATED` / `UNASSOCIATED` 状态生成音频识别输入队列。
- 可将本地音频解码为 PCM 或给出明确失败原因。
- 至少覆盖 MP3 和 AAC/M4A 的解码路径。
- 不支持格式必须给出明确失败原因。
- 可生成 `chromaprint-compatible` 指纹 payload。
- 可生成字段完整的 `AudioIdentityMatchRequest`。
- 可通过 Mock 完成指纹 reliable、candidate、none、error、timeout、degrade 分支。
- timeout / degrade 不扩展 `MatchResult`，只作为 `ERROR` 诊断原因。
- 高成本任务可暂停、重试、失败或降级，且不影响基础信息结果查询。
- ResultProvider 可查询指纹比对后的当前业务结果。
- 构建验证通过：`./gradlew :core:assemble :demo:assembleDebug`。
- 若已引入单元测试任务，对应单元测试通过。

## 14. 与 MVP-4 的交接

MVP-3 需要为 MVP-4 保留以下交接点：

- 指纹 `NONE` 后的 `UNASSOCIATED` 歌曲可进入本地特征兜底。
- 指纹 `CANDIDATE` 后的候选歌曲是否进入本地特征兜底，按 MVP-4 计划确认。
- `BasicSongInfo`、`durationMs`、`contentSignature`、音频处理诊断信息应可被 MVP-4 查询。
- `FAILED`、`SKIPPED`、`WAITING_TO_CONTINUE` 不自动进入本地特征兜底。
