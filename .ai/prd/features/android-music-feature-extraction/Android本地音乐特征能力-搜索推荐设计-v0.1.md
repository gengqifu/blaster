# Android 本地音乐特征能力搜索推荐设计 v0.1

Search/Recommend 这一层解决的是消费问题，不再负责提取问题。也就是说，到了这里，系统默认前面的扫描、基础信息、音频指纹和本地 embedding 已经留下了可用结果；搜索和推荐要做的，是把这些现有信号组织成一条稳定、可解释、能向后扩展的本地检索路径。

当前版本只做本地能力闭环，但接口和内部结构都按后续可能接入云端来收。这样做的目的很直接：先把调用方契约和排序主链稳定住，避免以后因为加 `CloudSource` 把整层能力推倒重来。

## 1. 输入前提与边界

搜索推荐链路只消费已有结果，不反向触发重型提取任务。它能看到的信息包括本地歌曲基础信息、可用的音频指纹摘要、可用的本地 embedding，以及这些信号当前是否仍然有效。

本期边界也比较明确：现在只有 `LocalSource`，没有真实云端检索；没有训练、学习排序，也没有个性化在线学习。这样做不是为了把问题做小，而是为了先把端侧本地消费语义定稳。

## 2. 对外接口

```kotlin
interface SearchRecommendService {
    suspend fun search(querySongId: String, topK: Int): SearchRecommendResponse
    suspend fun recommend(seedSongId: String, topK: Int): SearchRecommendResponse
}
```

接口本身保持得很克制：`search(querySongId, topK)` 和 `recommend(seedSongId, topK)` 两个入口，后面即使接入云端，也不打算在这里改签名。

请求侧只收几个硬边界：`querySongId` 或 `seedSongId` 必须属于允许输入集合，`topK` 要落在约定范围内。云端能力现在没接，不应该影响接口可用性。

响应统一包含：

- `requestId`
- `mode`
- `status`
- `results`
- `diagnostics`
- 可选 `errorCode`
- 可选 `errorMessage`

`status` 固定为 `OK / EMPTY / INVALID_INPUT / UNSUPPORTED`。其中一个关键约束是：云端未接入时只降级到本地路径，不把这种情况报成 `UNSUPPORTED`。另一个关键约束是：空结果可以接受，但不能没有原因。

## 3. 排序主链

当前 v0 的主线是 `metadata -> fingerprint -> embedding`。这不是为了把三个信号都平均看待，而是明确它们在排序里的层次关系。

`metadata` 是最便宜的一层，用来做候选召回或过滤；`fingerprint` 是更高置信的主精排信号；`embedding` 则放在最后，要么在 `fingerprint` 缺失时兜底，要么在 `fingerprint` 已经可用时做一点补充排序或平分打散。

之所以这样排，是因为这三类信号本身就不对等。`metadata` 更像入口筛选，`fingerprint` 更接近内容一致性，`embedding` 则更多承担相似性补充和兜底的角色。如果把它们机械地并排融合，很容易把主次关系搞乱。

### 3.1 `metadata`

`metadata` 的作用是先把候选集收窄。它不负责给出最终高置信相似性判断，但如果连这一层都不参与，后面的排序会在规模和噪音上都失去控制。

### 3.2 `fingerprint`

当 `fingerprint` 可用时，它应该主导排序方向。当前设计里不允许 embedding 在这时反客为主，否则“内容一致性”会被退化成“相似性猜测”。

### 3.3 `embedding`

`embedding` 只有两种位置：`fingerprint` 缺失时的 fallback，或者 `fingerprint` 已经参与后的小范围补充。它很重要，但不是当前 v0 里的第一判断依据。

## 4. explain 设计

Search/Recommend 这层不能只给一个分数，因为没有人能从一个裸分数里看出结果是怎么来的。当前最小 explain 集合里，既要能解释排序来源，也要能解释为什么空、为什么降级。

原因字典包括：

| reason | 含义 |
| --- | --- |
| `metadata_filtered` | 已经过 metadata 过滤/召回 |
| `fingerprint_primary` | fingerprint 参与主精排 |
| `fingerprint_missing` | 当前候选无可用 fingerprint |
| `embedding_fallback` | embedding 作为缺失 fingerprint 时的兜底排序 |
| `embedding_tiebreak` | embedding 仅作补充分或平分打散 |
| `embedding_missing` | 当前候选无可用 embedding |
| `no_retrieval_signal` | metadata、fingerprint、embedding 均不可用 |
| `degraded_signal_partial` | 至少一类信号缺失，但仍有其余信号可用 |
| `degraded_timeout_budget` | 受单次预算限制提前截断 |

结果里至少还要带上这些信号字段：

- `signals.metadataMatched`
- `signals.fingerprintMatched`
- `signals.embeddingScore`
- `signals.hasMetadata`
- `signals.hasAudioIdentity`
- `signals.hasLocalFeature`

这里的原则很简单：explain 不是调试彩蛋，而是接口语义的一部分。

## 5. 降级怎么走

最典型的几种情况如下：

- 有 `metadata`、有 `fingerprint`、没有 `embedding`
  - 排序仍然成立，主轴是 fingerprint
  - explain 里记录 `embedding_missing`

- 有 `metadata`、没有 `fingerprint`、有 `embedding`
  - 可以继续返回结果，但这时本质上走的是 embedding fallback
  - explain 里记录 `fingerprint_missing` 和 `embedding_fallback`

- 三类信号都不可用
  - 返回 `EMPTY`
  - explain 里记录 `no_retrieval_signal`

这些场景可以看成同一件事的不同落点：系统并不要求三类信号每次都齐全，但它必须把“用了什么、缺了什么、为什么还能排”说清楚。

状态语义对这里也有直接影响。`LOCAL_FEATURE_READY` 只说明本地 embedding 可用，不说明可靠云端关联已经建立；`OUTDATED` 需要结合失效来源判断具体是哪类信号失效；`WAITING_TO_CONTINUE / FAILED / SKIPPED` 是否还能参与排序，也要看真实信号是不是仍然存在且有效。

## 6. 模块分工

`SearchRecommendService` 负责对外入口、参数校验和响应语义收口；`HybridRetrievalEngine` 负责把候选源和排序器串起来，同时记录请求上下文、诊断信息和耗时。

`RetrievalSource` 负责候选召回，当前主实现是 `LocalSource`，未来才会扩展 `CloudSource`；`Ranker` 则负责真正的多阶段排序，把 metadata 过滤、fingerprint 主精排和 embedding 补充排序组合成最终的 `score / reasons / signals`。

## 7. 可观测性

当前日志建议统一打在 `BlasterSearchRecommend` 下，并保留以下字段：

- `requestId`
- `mode`
- `inputSongId`
- `topK`
- `candidateCountBeforeRank`
- `candidateCountAfterRank`
- `latencyMs`
- `degradePath`
- `status`

这部分不是为了把日志打得更全，而是为了让每次空结果、慢结果或异常降级都能在同一条链路上追得回来。

## 8. 当前边界与后续扩展

现在这套实现只跑 `LocalSource`，结果完全依赖本地已有的 metadata、fingerprint 和 embedding。demo 侧已经有了可触发、可展示、可输出 explain 的闭环，这说明调用方语义已经立住了。

后续如果引入真实 `CloudSource`，有两条边界不能动。第一，对外 `search/recommend` 签名不改；第二，端云混排继续通过 `RetrievalSource + Ranker` 扩展，不把云端逻辑硬塞进调用方或 demo。

## 9. 风险与约束

端云 embedding 空间不一致时，不能直接做无约束余弦比较；大曲库规模下，候选截断和 `topK` 需要继续承担复杂度控制；搜索推荐也不能反向触发高成本提取任务，否则前台链路很快会吃到本来应该留在后台的成本。

## 10. 关联文档

- 原始专题设计：[tech-design-search-recommend-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/tech-design-search-recommend-v0.1.md)
- 执行计划：[dev-plan-search-recommend-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/dev-plan-search-recommend-v0.1.md)
- 相关决策：
  - `decisions/2026-05-21-search-recommend-sdk-contract-policy.md`
  - `decisions/2026-05-21-search-recommend-ranking-policy.md`
  - `decisions/2026-05-21-search-recommend-hybrid-extension-policy.md`
