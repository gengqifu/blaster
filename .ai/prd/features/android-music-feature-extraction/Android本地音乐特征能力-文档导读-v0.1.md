# Android 本地音乐特征能力文档导读 v0.1

新增文档只做一件事：把已经散在 `PRD`、技术设计、开发计划、MVP 计划、ADR 和资源测试材料里的结论重新归档，方便连续阅读。旧文档保留原样，继续承担需求、过程、决策和证据的职责。

## 1. 阅读顺序

1. [Android本地音乐特征能力-总体设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-总体设计-v0.1.md)
2. [Android本地音乐特征能力-歌曲理解与特征链路设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-歌曲理解与特征链路设计-v0.1.md)
3. [Android本地音乐特征能力-搜索推荐设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-搜索推荐设计-v0.1.md)
4. [Android本地音乐特征能力-资源与运行约束说明-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-资源与运行约束说明-v0.1.md)
5. [Android本地音乐特征能力-性能测试设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-性能测试设计-v0.1.md)

## 2. 文档分工

[Android本地音乐特征能力-总体设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-总体设计-v0.1.md) 负责能力边界、总体架构、模块分工和状态语义。

[Android本地音乐特征能力-歌曲理解与特征链路设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-歌曲理解与特征链路设计-v0.1.md) 负责扫描、基础信息、音频指纹、本地 embedding 以及各阶段结束条件。

[Android本地音乐特征能力-搜索推荐设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-搜索推荐设计-v0.1.md) 负责 `search/recommend` 接口、排序规则、explain 和降级语义。

[Android本地音乐特征能力-资源与运行约束说明-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-资源与运行约束说明-v0.1.md) 负责资源画像结论及其对应的运行约束。

[Android本地音乐特征能力-性能测试设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-性能测试设计-v0.1.md) 负责性能测试口径、有效样本、artifact 和回归策略。

## 3. 底稿与证据

总体业务背景和原始方案来自 [prd-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/prd-v0.1.md) 与 [tech-design-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/tech-design-v0.1.md)。

阶段拆分、执行过程和验收记录继续看 [dev-plan-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/dev-plan-v0.1.md)、[dev-plan-search-recommend-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/dev-plan-search-recommend-v0.1.md) 以及 `mvp-plans/`。

接口、排序和扩展边界相关冻结结论继续以 `decisions/` 目录为准。

资源和性能证据继续以 [test-plan-resource-profile-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/test-plan-resource-profile-v0.1.md)、[resource-profile-report-2026-05-26.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/resource-profile-report-2026-05-26.md) 和 `tools/resource-profile/artifacts/` 为准。

## 4. 维护顺序

总体边界变化，先改“总体设计”。

歌曲处理链路、状态语义或本地特征边界变化，改“歌曲理解与特征链路设计”。

`search/recommend` 契约、排序或 explain 变化，改“搜索推荐设计”，并同步相关 ADR。

资源画像结论变化，改“资源与运行约束说明”。

测试口径、完成信号或回归策略变化，改“性能测试设计”。
