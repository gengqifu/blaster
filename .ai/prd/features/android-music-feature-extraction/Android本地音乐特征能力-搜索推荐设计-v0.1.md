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

响应至少包括：

- `requestId`
- `mode`
- `status`
- `results`
- `diagnostics`
- 可选 `errorCode`
- 可选 `errorMessage`

`status` 固定为：

- `OK`
- `EMPTY`
- `INVALID_INPUT`
- `UNSUPPORTED`

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

结果必须包含 explain。当前原因字典如下：

| reason | 含义 |
| --- | --- |
| `metadata_filtered` | 已经过 metadata 过滤/召回 |
| `fingerprint_primary` | fingerprint 参与主精排 |
| `fingerprint_missing` | 当前候选无可用 fingerprint |
| `embedding_fallback` | embedding 作为缺失 fingerprint 时的 fallback 排序 |
| `embedding_tiebreak` | embedding 仅作补充分或平分打散 |
| `embedding_missing` | 当前候选无可用 embedding |
| `no_retrieval_signal` | metadata、fingerprint、embedding 均不可用 |
| `degraded_signal_partial` | 至少一类信号缺失，但仍有其余信号可用 |
| `degraded_timeout_budget` | 受单次预算限制提前截断 |

信号字段至少包括：

- `signals.metadataMatched`
- `signals.fingerprintMatched`
- `signals.embeddingScore`
- `signals.hasMetadata`
- `signals.hasAudioIdentity`
- `signals.hasLocalFeature`

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
