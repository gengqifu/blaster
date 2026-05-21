# Android 端侧 Search/Recommend 能力 Tech Design v0.1（可核查版）

## 1. 文档范围与 ADR 绑定

本设计文档的策略性结论以以下 ADR 为准，本文只做实现级展开：

- `decisions/2026-05-21-search-recommend-sdk-contract-policy.md`
- `decisions/2026-05-21-search-recommend-ranking-policy.md`
- `decisions/2026-05-21-search-recommend-hybrid-extension-policy.md`

## 2. 目标与边界

本期目标：

- 在 `core` 提供稳定 `search/recommend` 能力。
- 端侧使用 `metadata -> fingerprint -> embedding` 的分层检索顺序。
- 调用方 API 在后续接云端时保持不变。

非目标：

- 不接真实云端检索服务。
- 不做训练/学习排序。
- 不做个性化在线学习。

## 3. 架构与职责

- `SearchRecommendService`：对外 API 门面。
- `HybridRetrievalEngine`：统一编排 source 与 ranker。
- `RetrievalSource`：候选召回（`LocalSource` 必做，`CloudSource` 预留）。
- `Ranker`：对候选执行 metadata 过滤、fingerprint 主精排、embedding 补充排序与 explain 产出。

## 4. 可验证接口定义

### 4.1 SDK 接口

```kotlin
interface SearchRecommendService {
    suspend fun search(querySongId: String, topK: Int): SearchRecommendResponse
    suspend fun recommend(seedSongId: String, topK: Int): SearchRecommendResponse
}
```

### 4.2 Request 字段表

| 接口 | 字段 | 必填 | 可空 | 含义 | 来源 |
| --- | --- | --- | --- | --- | --- |
| `search` | `querySongId` | 是 | 否 | 查询歌曲 ID | 调用方输入 |
| `search` | `topK` | 是 | 否 | 返回上限（1~100） | 调用方输入 |
| `recommend` | `seedSongId` | 是 | 否 | 种子歌曲 ID | 调用方输入 |
| `recommend` | `topK` | 是 | 否 | 返回上限（1~100） | 调用方输入 |

### 4.3 Response 字段表

| 字段 | 必填 | 可空 | 含义 | 来源 |
| --- | --- | --- | --- | --- |
| `requestId` | 是 | 否 | 单次请求追踪 ID | Engine 生成 |
| `mode` | 是 | 否 | `search/recommend` | Engine |
| `status` | 是 | 否 | `OK/EMPTY/INVALID_INPUT/UNSUPPORTED` | Service |
| `results` | 是 | 否 | 排序结果列表 | Ranker |
| `diagnostics` | 是 | 否 | 候选数、耗时、降级路径 | Engine |
| `errorCode` | 否 | 是 | 失败码 | Service |
| `errorMessage` | 否 | 是 | 失败说明 | Service |

`status` 语义约束：

- `OK`：调用成功，返回非空结果。
- `EMPTY`：调用成功，但无可返回结果；必须可追踪原因，如 `no_retrieval_signal`。
- `INVALID_INPUT`：入参非法，如 `songId` 缺失、`topK` 越界、`seed/query` 不存在于允许输入集合。
- `UNSUPPORTED`：请求的操作模式在当前构建配置或功能开关下未开放。
- 云端未接入期：单独降级为只跑 `LocalSource`，不返回 `UNSUPPORTED`。

### 4.4 `RankedSong` 字段表

| 字段 | 必填 | 可空 | 含义 |
| --- | --- | --- | --- |
| `localSongId` | 是 | 否 | 本地歌曲 ID |
| `score` | 是 | 否 | 最终排序分 |
| `reasons` | 是 | 否 | explain 原因数组 |
| `signals.metadataMatched` | 是 | 否 | 是否通过 metadata 过滤/召回 |
| `signals.fingerprintMatched` | 否 | 是 | 是否命中可用 fingerprint 主精排 |
| `signals.embeddingScore` | 否 | 是 | embedding 兜底或补充分 |
| `signals.hasMetadata` | 是 | 否 | 是否有可用 metadata |
| `signals.hasAudioIdentity` | 是 | 否 | 是否有可用 fingerprint 摘要 |
| `signals.hasLocalFeature` | 是 | 否 | 是否有可用 embedding |

## 5. 排序 explain 字段字典

| 字段值 | 触发条件 | 说明 |
| --- | --- | --- |
| `metadata_filtered` | 候选先通过 metadata 过滤/召回 | metadata 已参与第一层过滤 |
| `fingerprint_primary` | fingerprint 可用且参与主精排 | fingerprint 作为主排序依据 |
| `fingerprint_missing` | 无 fingerprint 可用 | fingerprint 未参与主精排 |
| `embedding_fallback` | 无 fingerprint，可用 embedding 参与排序 | embedding 作为兜底排序信号 |
| `embedding_tiebreak` | fingerprint 可用，同时 embedding 参与补充分或平分打散 | embedding 非主排序，只做补充 |
| `embedding_missing` | 无 embedding 可用 | 无 embedding 补充信号 |
| `no_retrieval_signal` | metadata、fingerprint、embedding 均不可用 | 空结果 |
| `degraded_signal_partial` | 至少一类信号缺失，但仍有其余信号可用 | 部分降级 |
| `degraded_timeout_budget` | 超过单次预算提前截断 | 预算降级 |

## 6. 信号可用性矩阵（按信号判断）

| 条件 | Metadata | Fingerprint | Embedding |
| --- | --- | --- | --- |
| `SourceState=AVAILABLE` | 是 | 视各自摘要是否存在 | 视各自特征是否存在 |
| `SourceState=UNAVAILABLE` | 否 | 否 | 否 |
| `SourceState=DELETED` | 否 | 否 | 否 |
| `仅 embedding schema/version 升级` | 是 | 是 | 否 |
| `contentSignature 变化` | 否 | 否 | 否 |
| `AudioIdentitySummary 缺失/失败/跳过` | 是 | 否 | 视 embedding 是否存在 |
| `LocalFeature 缺失/失败/跳过` | 是 | 视 fingerprint 是否存在 | 否 |
| `Association=RELIABLY_ASSOCIATED/CANDIDATE_ASSOCIATED/UNASSOCIATED` | 是 | 视 fingerprint 是否存在 | 视 embedding 是否存在 |

约束：

- `FeatureState=OUTDATED` 不再单独决定某个信号是否可参与排序。
- `OUTDATED` 必须绑定失效来源；实现阶段按来源映射到 `metadata/fingerprint/embedding` 的具体可用性。
- 若仅因 `embedding schema/version` 升级触发过期，仅 `embedding` 失效，`metadata` 与 `fingerprint` 仍可参与排序。
- 若因 `contentSignature` 变化触发过期，`metadata/fingerprint/embedding` 均视为待重算，不参与当前排序。
- `LOCAL_FEATURE_READY != RELIABLY_ASSOCIATED`。
- `CANDIDATE_ASSOCIATED` 不自动提权为高置信结果。

## 7. 排序与降级规则（v0）

- 第一层：`metadata` 用于候选召回/过滤，作为低成本入口信号。
- 第二层：`fingerprint` 对 metadata 候选做主精排与高置信相似性判断。
- 第三层：`embedding` 仅在以下场景参与：
  - `fingerprint` 可用时：作为次级补充分或平分打散信号，记录 `embedding_tiebreak`
  - `fingerprint` 不可用时：作为兜底排序信号，记录 `embedding_fallback`
- `metadata`、`fingerprint`、`embedding` 全部不可用时，返回 `EMPTY`，`reasons` 含 `no_retrieval_signal`。
- v0 不再以 `finalScore = embeddingScore + fingerprintBoost` 作为主策略表述；排序按多阶段链路执行。
- `mixed` 为开发计划中的测试术语，表示同次请求结果集内同时存在 `fingerprint` 主精排结果与 `embedding fallback` 结果；它不是新的 response status，也不是单独的 explain 值。
- 云端未接入期：只跑 `LocalSource`，`CloudSource` 不参与，不报错。

## 8. 可观测性规范

建议日志 tag：`BlasterSearchRecommend`。

单次请求必打字段：

- `requestId`
- `mode`
- `inputSongId`
- `topK`
- `candidateCountBeforeRank`
- `candidateCountAfterRank`
- `latencyMs`
- `degradePath`
- `status`

## 9. 风险与约束

- 端云向量空间不一致风险：云端接入后必须通过 `RetrievalSource + Ranker` 融合，不做直接跨模型余弦比较。
- 大库性能风险：通过候选截断与 `topK` 限制控制。
- 功耗风险：检索链路只消费已存在特征，不触发重型提取任务。

## 10. 设计阶段验收

- 接口、排序、扩展策略均可追溯到 3 份 ADR。
- 本文已提供 request/response 表、explain 字典、状态参与矩阵。
- `UNSUPPORTED` 与 `INVALID_INPUT` 语义边界已明确，且云端未接入不触发 `UNSUPPORTED`。
- 文中不存在“实现时再决定”措辞。
