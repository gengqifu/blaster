# Android 本地音乐特征能力搜索推荐设计 v0.1

当前版本的 Search/Recommend 只处理消费问题，不处理提取问题。输入来自已有 metadata、fingerprint、embedding 结果。真实云端未接入时，只跑本地路径。

## 1. 输入与边界

输入包括：

- 本地歌曲基础信息
- 可用音频指纹摘要
- 可用本地 embedding
- 当前状态与失效来源

当前边界如下：

- 只实现 `LocalSource`
- 不接真实云端检索
- 不做训练、学习排序或个性化在线学习
- 不反向触发音频解码、指纹生成或模型推理

## 2. 对外接口

```kotlin
interface SearchRecommendService {
    suspend fun search(querySongId: String, topK: Int): SearchRecommendResponse
    suspend fun recommend(seedSongId: String, topK: Int): SearchRecommendResponse
}
```

请求约束如下：

- `querySongId` 或 `seedSongId` 属于允许输入集合
- `topK` 落在约定范围内

Request 字段说明如下：

| 接口 | 字段 | 必填 | 可空 | 含义 | 来源 | 使用边界 |
| --- | --- | --- | --- | --- | --- | --- |
| `search` | `querySongId` | 是 | 否 | 查询歌曲 ID | 调用方输入 | 必须属于允许输入集合 |
| `search` | `topK` | 是 | 否 | 返回结果数量上限 | 调用方输入 | 当前设计按约定范围处理；超出范围进入 `INVALID_INPUT` |
| `recommend` | `seedSongId` | 是 | 否 | 推荐种子歌曲 ID | 调用方输入 | 必须属于允许输入集合 |
| `recommend` | `topK` | 是 | 否 | 返回结果数量上限 | 调用方输入 | 当前设计按约定范围处理；超出范围进入 `INVALID_INPUT` |

响应至少包括：

- `requestId`
- `mode`
- `status`
- `results`
- `diagnostics`
- 可选 `errorCode`
- 可选 `errorMessage`

Response 字段说明如下：

| 字段 | 必填 | 可空 | 含义 | 来源 | 使用边界 |
| --- | --- | --- | --- | --- | --- |
| `requestId` | 是 | 否 | 单次请求追踪 ID | Engine 生成 | 用于日志与结果追踪，不由调用方传入 |
| `mode` | 是 | 否 | 请求模式，取值为 `search` 或 `recommend` | Engine / Service | 表示本次请求类型，不表示排序结果 |
| `status` | 是 | 否 | 请求结果状态 | Service | 用于区分成功、空结果、非法输入和未开放模式 |
| `results` | 是 | 否 | 排序结果列表 | Ranker | 为空时仍需通过 `status` 和 `diagnostics/reasons` 说明原因 |
| `diagnostics` | 是 | 否 | 本次请求的诊断信息容器 | Engine | 承载候选规模、耗时、降级路径等，不属于业务结果主体 |
| `errorCode` | 否 | 是 | 失败码 | Service | 仅在需要补充失败信息时返回 |
| `errorMessage` | 否 | 是 | 失败说明 | Service | 面向排障，不作为排序逻辑输入 |

`status` 固定为：

- `OK`
- `EMPTY`
- `INVALID_INPUT`
- `UNSUPPORTED`

状态语义如下：

| 状态 | 含义 | 触发条件 | 是否表示请求成功执行 | 调用方理解方式 |
| --- | --- | --- | --- | --- |
| `OK` | 请求成功并返回非空结果 | 排序完成，且 `results` 非空 | 是 | 可直接消费结果 |
| `EMPTY` | 请求成功，但没有可返回结果 | 请求执行完成，但候选为空或全部信号不可用 | 是 | 说明流程跑完了，只是当前没有结果 |
| `INVALID_INPUT` | 请求参数非法 | `songId` 缺失、输入对象不在允许集合、`topK` 越界等 | 否 | 调用方需要修正输入 |
| `UNSUPPORTED` | 当前模式在构建配置或功能开关下未开放 | 功能本身未开放 | 否 | 表示模式不可用，不等于云端未接入 |

约束如下：

- 云端未接入时降级到本地路径，不返回 `UNSUPPORTED`
- 空结果必须带原因

## 3. 排序规则

排序主链固定为：

1. `metadata` 候选召回/过滤
2. `fingerprint` 主精排
3. `embedding` 补充分或 fallback

规则如下：

- `metadata` 负责收窄候选集
- `fingerprint` 可用时主导排序方向
- `embedding` 只在 `fingerprint` 缺失时作为 fallback，或在 `fingerprint` 已可用时做补充排序

`embedding` 不取代 `fingerprint` 成为主判断依据。

## 4. explain

结果必须包含 explain。`reasons` 表示本次排序为什么得到这个结果，`signals` 表示本次排序实际用到了哪些信号、缺了哪些信号。

`results` 中单条结果的字段如下：

| 字段 | 含义 | 来源 | 使用边界 |
| --- | --- | --- | --- |
| `localSongId` | 当前结果对应的本地歌曲 ID | 本地歌曲记录 | 结果定位字段，不代表云端歌曲 ID |
| `score` | 当前排序分 | Ranker | 只表示当前排序结果，不单独承诺跨版本可比性 |
| `reasons` | explain 原因数组 | Ranker | 用于解释排序和降级来源，不是业务标签 |
| `signals` | explain 信号对象 | Ranker | 用于表达命中情况、信号可用性和补充分数 |

`reasons` 字典如下：

| reason | 触发条件 | 含义 | 是否表示降级 |
| --- | --- | --- | --- |
| `metadata_filtered` | 候选经过 metadata 过滤/召回 | metadata 已参与本次候选收窄 | 否 |
| `fingerprint_primary` | fingerprint 可用且参与主精排 | fingerprint 是本次主排序依据 | 否 |
| `fingerprint_missing` | 当前候选无可用 fingerprint | fingerprint 未参与排序 | 是 |
| `embedding_fallback` | fingerprint 不可用，但 embedding 可用 | embedding 作为 fallback 排序信号 | 是 |
| `embedding_tiebreak` | fingerprint 可用，embedding 参与补充分或平分打散 | embedding 不是主排序依据，只做补充 | 否 |
| `embedding_missing` | 当前候选无可用 embedding | embedding 未参与补充排序 | 是 |
| `no_retrieval_signal` | metadata、fingerprint、embedding 全部不可用 | 当前请求没有可用检索信号 | 是 |
| `degraded_signal_partial` | 至少一类信号缺失，但其余信号仍可用 | 当前请求以部分信号完成排序 | 是 |
| `degraded_timeout_budget` | 超过单次预算提前截断 | 结果受时间预算约束 | 是 |

信号字段至少包括：

- `signals.metadataMatched`
- `signals.fingerprintMatched`
- `signals.embeddingScore`
- `signals.hasMetadata`
- `signals.hasAudioIdentity`
- `signals.hasLocalFeature`

`signals` 字段说明如下：

| 字段 | 含义 | 数据来源 | 可空 | 与排序/降级的关系 |
| --- | --- | --- | --- | --- |
| `signals.metadataMatched` | 当前结果是否经过 metadata 过滤或召回 | Metadata 过滤阶段 | 否 | 说明 metadata 是否实际参与本次结果形成 |
| `signals.fingerprintMatched` | 当前结果是否命中可用 fingerprint 主精排 | Fingerprint 排序阶段 | 是 | 为 `true` 时说明 fingerprint 实际参与主精排；缺失或 `null` 不等于一定有信号 |
| `signals.embeddingScore` | 当前结果上的 embedding 分数 | Embedding 排序阶段 | 是 | 只在 embedding 实际参与 fallback 或补充排序时出现 |
| `signals.hasMetadata` | 当前候选是否存在可用 metadata | 本地歌曲与索引数据 | 否 | 表示 metadata 是否存在，不等于本次一定使用了 metadata 过滤 |
| `signals.hasAudioIdentity` | 当前候选是否存在可用音频指纹摘要 | 音频指纹结果 | 否 | 表示 fingerprint 信号是否存在，不等于本次一定命中主精排 |
| `signals.hasLocalFeature` | 当前候选是否存在可用 embedding | 本地特征结果 | 否 | 表示 embedding 是否存在，不等于本次一定参与排序 |

补充说明：

- `metadata` 指用于候选召回或过滤的基础信息信号，不等于最终相似性判断。
- `fingerprint` 指音频指纹信号，用于主精排和更高置信的相似性判断。
- `embedding` 指本地向量特征，用于 fallback 或补充排序。
- `fallback` 指前一层信号不足时启用的补充排序方式，不等于可靠关联替代。

## 5. 降级规则

典型场景如下：

- 有 `metadata`、有 `fingerprint`、无 `embedding`
  - 继续排序
  - 记录 `embedding_missing`

- 有 `metadata`、无 `fingerprint`、有 `embedding`
  - 走 embedding fallback
  - 记录 `fingerprint_missing` 与 `embedding_fallback`

- 有 `metadata`、有 `fingerprint`、有 `embedding`
  - fingerprint 为主
  - embedding 记录为 `embedding_tiebreak`

- 三类信号全缺失
  - 返回 `EMPTY`
  - 记录 `no_retrieval_signal`

状态对降级有直接影响：

- `LOCAL_FEATURE_READY` 只表示本地 embedding 可用
- `OUTDATED` 需要结合失效来源判断具体失效信号
- `WAITING_TO_CONTINUE / FAILED / SKIPPED` 是否参与排序，取决于真实信号是否存在且有效

## 6. 模块分工

`SearchRecommendService` 负责入口、参数校验和响应语义整理。

`HybridRetrievalEngine` 负责请求编排、诊断信息和耗时统计。

`RetrievalSource` 负责候选召回，当前主实现是 `LocalSource`，未来预留 `CloudSource`。

`Ranker` 负责 metadata 过滤、fingerprint 主精排和 embedding 补充排序，输出 `score / reasons / signals`。

## 7. 可观测性

日志标签使用：

- `BlasterSearchRecommend`

单次请求保留以下字段：

- `requestId`
- `mode`
- `inputSongId`
- `topK`
- `candidateCountBeforeRank`
- `candidateCountAfterRank`
- `latencyMs`
- `degradePath`
- `status`

`diagnostics` 字段说明如下：

| 字段 | 含义 | 来源 | 使用边界 |
| --- | --- | --- | --- |
| `candidateCountBeforeRank` | 排序前候选规模 | Engine / RetrievalSource | 用于判断召回规模，不直接表示结果质量 |
| `candidateCountAfterRank` | 排序后保留下来的结果规模 | Engine / Ranker | 用于判断排序后实际返回规模 |
| `latencyMs` | 单次请求耗时，单位毫秒 | Engine | 表示本次请求执行耗时，不是后台提取耗时 |
| `degradePath` | 本次请求实际采用的降级路径 | Engine | 记录当前请求是完整信号、部分信号还是本地降级路径，不是错误码 |

日志观测字段说明如下：

| 字段 | 含义 | 典型用途 |
| --- | --- | --- |
| `requestId` | 单次请求追踪 ID | 串联日志与返回结果 |
| `mode` | 请求模式 | 区分 `search` 与 `recommend` |
| `inputSongId` | 本次输入歌曲 ID | 定位请求输入对象 |
| `topK` | 请求的返回上限 | 分析请求规模配置 |
| `candidateCountBeforeRank` | 排序前候选规模 | 判断召回范围 |
| `candidateCountAfterRank` | 排序后结果规模 | 判断实际输出规模 |
| `latencyMs` | 单次请求耗时 | 性能分析 |
| `degradePath` | 实际降级路径 | 排查为什么走了本地路径或部分信号路径 |
| `status` | 请求结果状态 | 区分成功、空结果、非法输入和未开放模式 |

补充说明：

- `degradePath` 关注的是“本次请求实际走了哪条降级路径”，不是失败码。
- `LocalSource` 表示当前版本本地候选召回来源。
- `CloudSource` 表示后续预留的云端候选召回来源，当前版本未接入。

## 8. 扩展边界

当前只跑 `LocalSource`。

后续接入真实 `CloudSource` 时：

- `search/recommend` 对外签名不变
- 端云混排继续通过 `RetrievalSource + Ranker` 扩展
- 新字段只做向后兼容扩展，不破坏现有 `signals/reasons` 语义

## 9. 关联文档

- 原始专题设计：[tech-design-search-recommend-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/tech-design-search-recommend-v0.1.md)
- 执行计划：[dev-plan-search-recommend-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/dev-plan-search-recommend-v0.1.md)
- 相关决策：
  - `decisions/2026-05-21-search-recommend-sdk-contract-policy.md`
  - `decisions/2026-05-21-search-recommend-ranking-policy.md`
  - `decisions/2026-05-21-search-recommend-hybrid-extension-policy.md`
