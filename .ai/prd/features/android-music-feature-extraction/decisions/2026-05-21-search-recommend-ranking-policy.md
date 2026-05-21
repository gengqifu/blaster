# ADR: Search/Recommend Ranking Policy v0（2026-05-21）

## 背景

当前端侧已具备 metadata、fingerprint 与 embedding 信号，但未形成可核查的排序策略。若不冻结 v0 规则，实现阶段将出现“每次改排序逻辑”的不可验证状态。

## 决策

1. v0 排序主策略固定为：
   - metadata 先做候选召回/过滤
   - fingerprint 做主精排与高置信相似性判断
   - embedding 做补充排序与 fingerprint 缺失时的兜底排序
2. 降级策略固定为：
   - 有 metadata、有 fingerprint、无 embedding：保留 fingerprint 主精排，记录 `embedding_missing`
   - 有 metadata、无 fingerprint、有 embedding：走 embedding 兜底排序，记录 `fingerprint_missing` 与 `embedding_fallback`
   - 有 metadata、fingerprint 与 embedding 同时可用：fingerprint 为主，embedding 仅作补充分或平分打散，记录 `embedding_tiebreak`
   - metadata、fingerprint、embedding 全缺失：返回空结果并记录 `no_retrieval_signal`
3. `LOCAL_FEATURE_READY` 仅表示本地特征可用，不等价可靠云端关联。
4. `OUTDATED` 必须绑定失效来源，而不是直接代表所有信号同时失效：
   - 仅 embedding schema/version 升级时，仅 embedding 失效
   - contentSignature 变化时，metadata、fingerprint、embedding 均失效
5. `WAITING_TO_CONTINUE/FAILED/SKIPPED` 不自动进入高置信排序主路径，是否可参与取决于对应信号是否真实存在且未失效。
6. 所有排序结果必须输出 explain 字段，至少包括：
   - `reasons`
   - `signals.hasMetadata`
   - `signals.hasAudioIdentity`
   - `signals.hasLocalFeature`
   - 可用时的 `signals.embeddingScore`
7. v0 不再以未定量程的线性融合公式作为主策略表述；实现阶段应按多阶段排序链路落地。

## 影响范围

- 约束 `tech-design-search-recommend-v0.1.md` 的“检索与排序策略（v0）”章节。
- 约束 `dev-plan-search-recommend-v0.1.md` 的里程碑 3 测试门禁。

## 非目标

- 本 ADR 不引入训练、学习排序或个性化特征。
- 本 ADR 不定义 embedding 与 fingerprint 的精确数值权重公式。
- 本 ADR 不定义线上阈值调优流程。

## 校验标准

- 设计文档中存在按信号拆分的可用性矩阵和 explain 字段字典。
- 设计文档明确 `metadata -> fingerprint -> embedding` 的多阶段排序顺序。
- 开发计划里程碑 3 包含以下门禁场景：metadata 召回 + fingerprint 主精排、metadata 召回 + embedding 兜底、mixed、no-signal。
