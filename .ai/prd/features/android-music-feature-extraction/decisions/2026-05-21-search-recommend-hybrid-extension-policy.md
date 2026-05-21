# ADR: Search/Recommend Hybrid Extension Policy（2026-05-21）

## 背景

本期不接真实云端检索，但产品方向要求后续支持端云混排。若不先定义扩展边界，后续接云端时容易破坏 SDK 接口稳定性并造成调用方返工。

## 决策

1. 检索架构采用插件式扩展：
   - `RetrievalSource`（本地/云端候选源）
   - `Ranker`（融合与排序）
   - `HybridRetrievalEngine`（编排）
2. 本期仅实现 `LocalSource`，`CloudSource` 只保留扩展接口与占位约束，不接真实网络。
3. 后续接云端时，调用方仍通过同一 `SearchRecommendService` 调用；禁止变更 `search/recommend` 对外签名。
4. 混排策略通过 `Ranker` 扩展实现；禁止把云端逻辑硬编码进调用方或 demo 页面。
5. 若云端接入导致返回结构扩展，只允许在 `signals/reasons` 中增加向后兼容字段，不破坏既有字段语义。

## 影响范围

- 约束 `tech-design-search-recommend-v0.1.md` 的“可扩展性（端云混排）”章节。
- 约束 `dev-plan-search-recommend-v0.1.md` 里程碑 2、4、5 的验收项。

## 非目标

- 本 ADR 不定义真实云端检索协议、鉴权、网络重试细则。

## 校验标准

- 设计文档中明确“CloudSource 接入不改调用方接口”。
- 开发计划中存在“端云扩展不破坏 SDK 签名”的门禁检查项。
