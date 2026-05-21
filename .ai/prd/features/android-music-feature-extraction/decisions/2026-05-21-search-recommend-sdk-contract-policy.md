# ADR: Search/Recommend SDK Contract Policy（2026-05-21）

## 背景

Search/Recommend 将作为 `core SDK` 对外能力提供。若接口契约不提前冻结，后续实现阶段会在入参、返回结构、错误语义和可解释字段上反复变更，导致 demo 与调用方反复适配。

## 决策

1. `core SDK` 对外检索能力固定为两类入口：
   - `search(querySongId, topK)`
   - `recommend(seedSongId, topK)`
2. 两类接口返回统一结果结构，至少包含：
   - `localSongId`
   - `score`
   - `reasons`
   - `signals`
3. `reasons` 与 `signals` 为 explain 最小集合，必须可用于排查排序来源；不得仅返回裸分数。
4. 结果为空时必须返回可追踪原因（例如 `no_retrieval_signal`），禁止“静默空结果”。
5. 对调用方保持接口稳定：
   - 后续接入云端能力时，不改变 `search/recommend` 函数签名。
   - 扩展通过内部 source/ranker 组合完成。
6. `status` 枚举语义固定为：
   - `OK`：成功返回非空结果
   - `EMPTY`：调用成功但无结果，且必须有可追踪原因
   - `INVALID_INPUT`：入参非法或输入对象不在允许输入集合
   - `UNSUPPORTED`：当前构建配置或功能开关未开放该操作模式
7. 云端未接入期不触发 `UNSUPPORTED`：
   - 应降级为仅执行 `LocalSource`
   - 不得因为 `CloudSource` 未接入而返回 `UNSUPPORTED`

## 影响范围

- 约束 `tech-design-search-recommend-v0.1.md` 的接口章节。
- 约束 `dev-plan-search-recommend-v0.1.md` 的里程碑 1 与里程碑 2 验收条件。

## 非目标

- 本 ADR 不定义具体排序公式权重。
- 本 ADR 不定义真实云端 API 协议。

## 校验标准

- 文档中所有接口描述与本 ADR 一致。
- 文档中 `UNSUPPORTED` 与 `INVALID_INPUT` 的边界清晰且无冲突。
- 文档明确“云端未接入只降级到 LocalSource，不触发 `UNSUPPORTED`”。
- 实现阶段若需变更接口签名，必须先更新 ADR 后再实施。
