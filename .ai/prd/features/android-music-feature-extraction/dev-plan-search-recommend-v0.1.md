# Android 端侧 Search/Recommend 开发计划 v0.1

## 1. 决策冻结来源

Search/Recommend 后续实现必须遵循以下冻结文档，不允许在编码阶段重拍关键策略：

- `tech-design-search-recommend-v0.1.md`
- `decisions/2026-05-21-search-recommend-sdk-contract-policy.md`
- `decisions/2026-05-21-search-recommend-ranking-policy.md`
- `decisions/2026-05-21-search-recommend-hybrid-extension-policy.md`

## 2. 目标与边界

本计划用于把当前已具备的 `metadata + fingerprint + embedding` 信号，落地为 `core SDK` 可消费的端侧 Search/Recommend 能力。

当前版本目标：

- 提供稳定的 `search(querySongId, topK)`、`recommend(seedSongId, topK)` 接口。
- 以 `metadata -> fingerprint -> embedding` 作为固定排序主链。
- 在 demo 中提供可触发、可展示、可解释的结果闭环。

当前版本不包含：

- 不接真实云端检索服务。
- 不做训练、蒸馏或学习排序。
- 不做复杂个性化、在线学习和服务化索引。

## 3. 状态变更规则

Search/Recommend 执行状态只允许在本文档维护，`dev-plan-v0.1.md` 只保留入口引用，不承载进度状态。

状态只允许三种：

- `[ ]` 未开始
- `[~]` 进行中
- `[x]` 已完成

状态变更规则：

- 进入某个里程碑时，将对应进度标记从 `[ ]` 改为 `[~]`。
- 进入里程碑时，必须在该里程碑的 `验收记录` 中写入：`开始时间`、`负责人`、`基线分支`。
- 完成里程碑时，才允许将对应进度标记改为 `[x]`。
- 完成里程碑时，必须在 `验收记录` 中补齐：`完成时间`、`执行命令`、`关键日志/截图`、`代码/文档产物`、`门禁结论`。
- 若执行失败或被阻塞，保留 `[~]`，并在 `验收记录` 中写明：`阻塞点`、`停止条件命中`、`回退动作`。
- 禁止跳步：只有前一里程碑为 `[x]`，下一里程碑才允许从 `[ ]` 切换到 `[~]`。

## 4. 执行拆分（5 个里程碑）

在不改变 Search/Recommend 当前范围的前提下，执行按以下 5 个里程碑推进。每个里程碑都要求“实现 + 测试”闭环后再进入下一步。

进度标记：

- [x] 里程碑 1：文档与接口冻结
- [x] 里程碑 2：Core 检索骨架
- [ ] 里程碑 3：本地检索与排序实现
- [ ] 里程碑 4：Demo 接线与可视化
- [ ] 里程碑 5：验收与收口

### 4.1 里程碑 1：文档与接口冻结

产出：

- `tech-design-search-recommend-v0.1.md` 中冻结 request/response 字段表、explain 字段字典、状态参与矩阵。
- `dev-plan-search-recommend-v0.1.md` 中冻结执行顺序、状态推进规则和里程碑验收模板。
- 3 份 ADR：SDK 契约、排序与降级、端云混排扩展。
- `dev-plan-v0.1.md` 中 Search/Recommend 文档与 ADR 入口引用。

完成标准：

- 本里程碑依赖 ADR：
  - `2026-05-21-search-recommend-sdk-contract-policy.md`
  - `2026-05-21-search-recommend-ranking-policy.md`
  - `2026-05-21-search-recommend-hybrid-extension-policy.md`
- 文档不再出现“实现时再决定”类措辞。
- `rg "search-recommend-.*policy|tech-design-search-recommend-v0.1.md|dev-plan-search-recommend-v0.1.md" .ai/prd/features/android-music-feature-extraction -n` 可确认入口与 ADR 全部存在。
- 技术设计、开发计划、ADR 三者在接口名、排序规则、扩展边界上无冲突。

验收记录：

- 开始时间：2026-05-21 16:07:05 +0800
- 完成时间：2026-05-21 16:07:05 +0800
- 负责人：Codex
- 基线分支：main
- 执行命令：`git branch --show-current`、`date '+%Y-%m-%d %H:%M:%S %z'`、`rg "search-recommend-.*policy|tech-design-search-recommend-v0.1.md|dev-plan-search-recommend-v0.1.md" .ai/prd/features/android-music-feature-extraction -n`
- 关键日志/截图：文档 grep 结果确认设计文档、子计划、3 份 ADR 与总计划入口已存在；技术设计已明确 `metadata -> fingerprint -> embedding`、`OUTDATED` 来源映射、`UNSUPPORTED` 语义。
- 代码/文档产物：`tech-design-search-recommend-v0.1.md`、3 份 Search/Recommend ADR、`dev-plan-search-recommend-v0.1.md`、`dev-plan-v0.1.md` 中 Search/Recommend 入口引用。
- 门禁结论：文档与 ADR 口径一致，里程碑 1 完成，允许进入里程碑 2。
- 阻塞与处理：无

### 4.2 里程碑 2：Core 检索骨架

产出：

- `RetrievalSource` 抽象与 `LocalSource` 默认实现骨架。
- `Ranker` 抽象与本地混排默认实现骨架。
- `HybridRetrievalEngine` 骨架。
- `SearchRecommendService` 接口与默认实现骨架。
- Search/Recommend 骨架单测。

完成标准：

- 本里程碑以前置完成项为准：里程碑 1 必须为 `[x]`。
- `./gradlew :core:assemble --no-daemon` 通过。
- `./gradlew :core:testDebugUnitTest --no-daemon --tests '*SearchRecommend*'` 通过至少骨架单测。
- 对外接口字段与 `sdk-contract-policy` 保持一致，不得私自增减调用方必填字段。
- 骨架实现不得把 `UNSUPPORTED` 作为默认占位返回值；仅当当前构建配置或功能开关未开放该操作模式时才允许返回 `UNSUPPORTED`。
- 云端未接入期必须降级为只跑 `LocalSource`，不得因为 `CloudSource` 缺失直接返回 `UNSUPPORTED`。

验收记录：

- 开始时间：2026-05-21 16:07:05 +0800
- 完成时间：2026-05-21 16:07:05 +0800
- 负责人：Codex
- 基线分支：main
- 执行命令：`./gradlew :core:assemble --no-daemon`、`./gradlew :core:testDebugUnitTest --no-daemon --tests '*SearchRecommend*'`
- 关键日志/截图：`:core:assemble` 成功；`:core:testDebugUnitTest --tests '*SearchRecommend*'` 成功。执行中发现 Android library 的 `:core:test` 不支持直接带 `--tests` 过滤，已按实际可执行任务 `:core:testDebugUnitTest` 收口。
- 代码/文档产物：新增 `core/searchrecommend` 骨架：`SearchRecommendService`、`RetrievalSource`、`LocalSource`、`Ranker`、`DefaultLocalRanker`、`HybridRetrievalEngine`、响应模型；新增 `SearchRecommendServiceTest` 骨架单测。
- 门禁结论：里程碑 2 完成，接口契约与 ADR 一致，允许进入里程碑 3。
- 阻塞与处理：无

### 4.3 里程碑 3：本地检索与排序实现

产出：

- metadata 候选召回/过滤实现。
- fingerprint 主精排实现。
- embedding 补充排序与兜底排序实现。
- `score/reasons/signals` explain 结果实现。
- 降级路径：`metadata + fingerprint`、`metadata + embedding fallback`、`mixed`、`no-signal`。
  - `mixed`：同一次请求的结果集中，部分候选经 `fingerprint` 主精排，部分候选因 `fingerprint` 缺失降级为 `embedding fallback`，两类 `reasons` 共存。
- 对应单元测试与排序稳定性测试。

完成标准：

- 本里程碑依赖 ADR：
  - `2026-05-21-search-recommend-ranking-policy.md`
- 里程碑 2 必须为 `[x]`。
- `./gradlew :core:testDebugUnitTest --no-daemon --tests '*SearchRecommend*'` 通过。
- 四类测试场景必须可核查：`metadata + fingerprint`、`metadata + embedding fallback`、`mixed`、`no-signal`。
  - `mixed`：同一次请求的结果集中，部分候选走 `fingerprint` 主精排，部分候选因 `fingerprint` 缺失走 `embedding fallback`，两类 `reasons` 共存。
- fingerprint 可用时必须作为主精排依据，不允许 embedding 反客为主。
- explain 字段必须与技术设计中的字典一致，不能新增未定义 reason 值。
- 任一信号缺失都必须走明确降级，不允许 silent fallback。

验收记录：

- 开始时间：未完成
- 完成时间：未完成
- 负责人：未完成
- 基线分支：未完成
- 执行命令：未完成
- 关键日志/截图：未完成
- 代码/文档产物：未完成
- 门禁结论：未完成
- 阻塞与处理：无

### 4.4 里程碑 4：Demo 接线与可视化

产出：

- demo 中 Search/Recommend 触发入口。
- 结果展示字段：`songId`、`score`、`reasons`、`signals`。
- 调试日志：`BlasterSearchRecommend`。
- 结果 explain 可视化与关键调试证据。

完成标准：

- 本里程碑依赖 ADR：
  - `2026-05-21-search-recommend-hybrid-extension-policy.md`
- 里程碑 3 必须为 `[x]`。
- `./gradlew :demo:assembleDebug --no-daemon` 通过。
- demo 中可稳定触发 `search/recommend`，且不改调用方 API 形状。
- 必须能在日志中看到：
  - `requestId`
  - `mode`
  - `inputSongId`
  - `topK`
  - `candidateCountBeforeRank`
  - `candidateCountAfterRank`
  - `latencyMs`
  - `degradePath`
  - `status`
- 验收记录中必须包含 demo 可视化证据字段，至少为截图、录屏或等价说明。

验收记录：

- 开始时间：未完成
- 完成时间：未完成
- 负责人：未完成
- 基线分支：未完成
- 执行命令：未完成
- 关键日志/截图：未完成
- 代码/文档产物：未完成
- 门禁结论：未完成
- 阻塞与处理：无

### 4.5 里程碑 5：验收与收口

产出：

- Search/Recommend 最终验收记录。
- 构建与单测通过记录。
- 关键场景验证记录与性能基线摘要。

完成标准：

- 里程碑 4 必须为 `[x]`。
- 最终总验收命令必须执行并记录结果：
  - `./gradlew :core:test :core:assemble :demo:assembleDebug --no-daemon`
- 文档中的验收项都能映射到自动化测试、命令结果或 demo 日志证据。
- 若存在性能基线，至少记录候选规模、`topK`、平均耗时或首版观察值。
- 本里程碑完成后，才允许将 Search/Recommend 标记为当前版本“可进入实现完成态”。

验收记录：

- 开始时间：未完成
- 完成时间：未完成
- 负责人：未完成
- 基线分支：未完成
- 执行命令：未完成
- 关键日志/截图：未完成
- 代码/文档产物：未完成
- 门禁结论：未完成
- 阻塞与处理：无

## 5. 文档阶段验收

本轮仅文档交付，不宣称功能已完成。文档完成必须满足：

- 本文包含 `进度标记` 与 5 个里程碑分节。
- 每个里程碑均包含：`产出`、`完成标准`、`验收记录`。
- `验收记录` 中包含：`开始时间`、`完成时间`、`负责人`、`执行命令`、`关键日志/截图`、`代码/文档产物`、`门禁结论`、`阻塞与处理`。
- 本文显式定义 `[ ]/[~]/[x]` 状态变更规则与禁止跳步约束。
- Search/Recommend 状态唯一来源为本文，不在总计划双写。

## 6. 与后续实现的交接

后续编码阶段必须按本文里程碑顺序推进：

- 先冻结文档与 ADR，再进 `core` 骨架。
- 排序实现完成前，不接 demo 展示。
- demo 接线完成前，不做最终验收宣告。
- 任一里程碑若未达到完成标准，不允许推进下一个里程碑。
