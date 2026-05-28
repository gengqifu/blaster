# Android 本地音乐特征能力文档导读 v0.1

这组文档是对现有材料的一次整理。原有 `PRD`、技术设计、开发计划、MVP 细化方案、ADR 和资源测试报告都保留不动，继续承担各自的职责；这里新增的几篇文档，只是把已经分散的结论重新归拢成一套更容易连读的设计稿。

如果是第一次接触这个方向，建议先看[总体设计](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-总体设计-v0.1.md)，先建立整体图景，再顺着歌曲处理链路、搜索推荐、资源约束和性能测试往下读。这样读下来，基本能把“这套能力现在做到哪、核心约束是什么、后面该怎么验证”连成一条线。

## 1. 阅读顺序

### 1.1 建议顺序

1. [Android本地音乐特征能力-总体设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-总体设计-v0.1.md)
2. [Android本地音乐特征能力-歌曲理解与特征链路设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-歌曲理解与特征链路设计-v0.1.md)
3. [Android本地音乐特征能力-搜索推荐设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-搜索推荐设计-v0.1.md)
4. [Android本地音乐特征能力-资源与运行约束说明-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-资源与运行约束说明-v0.1.md)
5. [Android本地音乐特征能力-性能测试设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-性能测试设计-v0.1.md)

### 1.2 按问题查文档

想先看全局边界、模块分工和状态语义，直接看“总体设计”。

想知道一首本地歌曲如何从扫描一路走到基础信息匹配、音频指纹，再到本地 embedding 兜底，看“歌曲理解与特征链路设计”。

想确认 `search/recommend` 现在到底消费哪些信号、排序顺序怎么定、降级时怎么解释结果，看“搜索推荐设计”。

想判断当前资源压力主要落在哪一段、为什么设计上要加开关和限流，看“资源与运行约束说明”。

想知道后面怎么持续做性能回归、哪些指标可以直接拿来对版本、什么样本才算有效，看“性能测试设计”。

## 2. 这几篇新文档分别做什么

### 2.1 总体设计

[Android本地音乐特征能力-总体设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-总体设计-v0.1.md)

这一篇负责把整套能力讲完整：为什么要做、本期边界在哪、客户端里有哪些关键模块、结果最终如何给搜索、推荐和播放使用。它相当于整套文档的入口。

### 2.2 歌曲理解与特征链路设计

[Android本地音乐特征能力-歌曲理解与特征链路设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-歌曲理解与特征链路设计-v0.1.md)

这一篇聚焦具体处理链路。读它时不用来回翻 `MVP-2/3/4`，就能把基础信息、音频指纹和本地 embedding 各自负责什么、在什么条件下推进或收口看清楚。

### 2.3 搜索推荐设计

[Android本地音乐特征能力-搜索推荐设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-搜索推荐设计-v0.1.md)

这一篇处理的是消费侧问题：已有的 `metadata`、`fingerprint`、`embedding` 到了检索阶段怎么组织起来，为什么是 `metadata -> fingerprint -> embedding` 这条主链，以及空结果、降级结果该怎么解释。

### 2.4 资源与运行约束说明

[Android本地音乐特征能力-资源与运行约束说明-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-资源与运行约束说明-v0.1.md)

这一篇不是单纯贴测试数字，而是把已经拿到的资源画像转成工程约束。后续再看调度、开关、限流和优化优先级时，可以把这里当作统一口径。

### 2.5 性能测试设计

[Android本地音乐特征能力-性能测试设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-性能测试设计-v0.1.md)

这一篇负责把“怎么测”固定下来。它不替代某次测试报告，而是定义测试对象、样本有效性、完成信号、artifact 结构和回归策略，便于后面稳定复用。

## 3. 和现有文档怎么配合

业务目标和最初的总体方案，仍然以 [prd-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/prd-v0.1.md) 与 [tech-design-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/tech-design-v0.1.md) 为底稿。这两份文档定义了问题和最初的系统框架，新文档是在这个基础上做收束。

执行过程和阶段拆分，仍然看 [dev-plan-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/dev-plan-v0.1.md)、[dev-plan-search-recommend-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/dev-plan-search-recommend-v0.1.md) 以及 `mvp-plans/`。这些文档保留了“怎么推进、在哪里验收”的过程信息，整理版文档不重复写这部分。

接口、排序和扩展边界相关的冻结结论，仍然要回到 `decisions/` 目录看 ADR。整理版只引用决策结论，不复述决策过程。

资源和性能方面，事实证据仍然来自 [test-plan-resource-profile-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/test-plan-resource-profile-v0.1.md)、[resource-profile-report-2026-05-26.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/resource-profile-report-2026-05-26.md) 以及 `tools/resource-profile/artifacts/` 下的产物目录。

## 4. 维护约定

后续如果总体边界变了，先改“总体设计”；如果歌曲处理链路、状态语义或本地特征边界变了，改“歌曲理解与特征链路设计”；如果 `search/recommend` 的契约或排序逻辑变了，改“搜索推荐设计”，同时同步相关 ADR。

资源画像结论发生变化时，优先更新“资源与运行约束说明”；测试口径、完成信号或回归策略变化时，更新“性能测试设计”。这样可以把“设计怎么定”和“测试怎么验证”持续分开维护。
