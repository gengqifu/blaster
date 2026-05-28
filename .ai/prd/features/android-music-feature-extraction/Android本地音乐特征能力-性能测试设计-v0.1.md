# Android 本地音乐特征能力性能测试设计 v0.1

这篇文档不记录某一次测试跑出了什么结果，而是把后续性能验证的方法固定下来。目的是让 `audio_identity`、`local_feature` 以及 `search/recommend` 的测试都能落在同一套口径里：怎么采样、什么信号算完成、哪些样本有效、什么时候该重跑、不同版本之间怎么比较。

如果说资源测试报告回答的是“这次发生了什么”，那这里回答的是“以后都按什么方式测”。

## 1. 测试对象

目前可以把测试对象分成三层。

第一层是单链路测试，主要看：

- `basic_info`
- `audio_identity`
- `local_feature`

第二层是消费侧测试，也就是直接面对调用方的：

- `search`
- `recommend`

第三层是对照测试，用来给前两层提供参照：

- `idle baseline`
- `bounded run`
- 重复运行漂移

这样拆不是为了形式完整，而是因为三层问题不一样。单链路关注提取成本，消费侧关注前台请求成本，对照测试则决定后面这些数字还能不能放在一起看。

## 2. 测试目标

对单链路来说，重点是看真实 Android 端上的时延、CPU、内存、I/O 和线程峰值。这里关心的是每一段链路单独跑起来到底有多重。

对 `search/recommend` 来说，重点不再是模型或提取本身，而是请求时延、候选规模变化带来的排序成本、`topK` 变化的影响，以及降级路径是否会显著改变结果和耗时。

对回归来说，真正要盯的是版本变化后有没有明显退化：资源是否回升、时延是否漂移、原本应该留在后台的成本有没有被错误地带进前台链路。

## 3. 典型测试场景

### 3.1 首次冷启动

这个场景主要用来看冷态初始化、首次提取和第一轮资源峰值。它通常最容易暴露出初始化成本和一次性抖动。

### 3.2 重复运行

同一设备、同一数据集、同一参数连续运行，可以帮助判断初始化抖动是否消失，峰值是否稳定，时延有没有明显漂移。

### 3.3 多轮 drain

`local_feature` 这种阶段不能只看首轮，还要看多轮 drain。这里关心的是 `round_count`、首轮与末轮耗时差异，以及是否出现“越跑越慢”。

### 3.4 不同歌曲规模

规模变化会直接影响判断。至少要区分小规模、中等规模和较大规模样本，否则很难知道当前结论是链路本身的问题，还是规模一放大就会出现的放大效应。

### 3.5 信号缺失与降级

对 `search/recommend` 来说，还需要覆盖几类典型信号组合：

- 只有 metadata
- metadata + fingerprint
- metadata + embedding fallback
- mixed
- no-signal

这些场景不仅影响结果，也会影响请求耗时和 explain 路径。

## 4. 指标口径

当前资源类指标主要包括：

- `elapsed_ms`
- `cpu_peak_process`
- `cpu_peak_thread`
- `pss_peak_kb`
- `thread_peak`
- `read_bytes_delta`
- `syscr_delta`
- `round_count`

消费侧更关注：

- `latencyMs`
- `candidateCountBeforeRank`
- `candidateCountAfterRank`
- `topK`
- `degradePath`
- `status`

这些指标单独看意义有限。每次记录都要带上测试时间、设备、数据集和阶段上下文；如果要横向比较不同阶段，还要确认完成信号和收口方式是不是一致。

## 5. 什么样本算有效

有效样本至少要满足四个条件：目标阶段真的被触发了，采样窗口覆盖到了核心执行区间，artifact 是完整的，而且完成信号能说清这次到底是正常收口还是异常退出。

### 5.1 `audio_identity`

这条链路里，至少出现一条真实 `extracted` 日志，才算真的跑到了提取阶段。`completion_signal = profile_window_elapsed_after_extract` 可以接受，它表示已经拿到了有效样本，窗口也足够，不要求整个队列自然清空。

如果超时了但没有任何真实提取日志，或者关键 artifact 缺失到无法还原峰值和耗时，这次就不该算有效样本。

### 5.2 `local_feature`

这里的最低要求是出现真实 embedding 提取日志，并且看到 `drain_completed` 或 `drain_timeout`。其中 `drain_timeout` 表达的是 bounded run 的正常收口，不是崩溃。

如果只是点了按钮，没有任何 embedding 提取日志，也没有明确完成信号，那这次测试不应进入后续分析。

### 5.3 `search/recommend`

消费侧有效样本的标准更简单：要有一次真实 `search` 或 `recommend` 请求，日志里能看到完整的 `BlasterSearchRecommend` 输出，并且能拿到 `status`、候选数和时延字段。

## 6. 测试方法与产物

当前资源画像自动化主入口是：

- `tools/resource-profile/run_resource_profile.sh`

这套入口目前主要覆盖：

- `audio_identity`
- `local_feature`

每次测试至少要保留这些产物：

- `meta.json`
- `cpu_samples.csv`
- `mem_samples.csv`
- `io_samples.csv`
- `phase.logcat.txt`
- `summary.json`
- `report.md`

`summary.json` 负责机器可读汇总，便于后续自动比较；`report.md` 负责人类快速复核；`phase.logcat.txt` 则保留完成信号、阶段日志和异常线索，帮助解释这次收口到底是正常 bounded run 还是异常退出。

## 7. 验收口径

判断“链路已真实跑通”时，不能只看一个按钮点没点，也不能只看脚本有没有产出目录。至少要同时满足：出现真实提取或真实检索日志，关键 artifact 完整，完成信号和阶段语义一致，而且结果可复核。

判断“当前版本已经可测，但还没优化完成”时，常见信号反而更现实一些：已经拿到了稳定资源画像，但峰值或耗时仍然偏高；bounded run 能正常结束，但全量自然跑完的成本还不清楚；或者多次重复结果波动仍然比较大。

优化前后做对比时，至少要固定住同一设备、同一数据集、同一阶段和同一参数，并保证完成信号语义一致。每组结果至少重复 3 次，再优先对比 `elapsed_ms`、`pss_peak_kb`、`cpu_peak_process`、`thread_peak` 和 `read_bytes_delta`。

## 8. 回归策略

以下改动至少需要重跑对应阶段性能测试：

- 音频解码策略变化
- 指纹算法、版本、ABI 或 native 集成变化
- 本地模型、模型版本或 schema 变化
- 队列、调度、限流或 drain 逻辑变化
- Search/Recommend 候选召回、排序或 explain 逻辑变化

重复次数建议至少做到：

- 单链路资源测试 3 次
- Search/Recommend 请求时延测试每组输入 5 次

基线也要单独保留。每次版本测试都应该留下独立 artifact 目录，并注明日期、分支、设备、数据集和完成信号。比较新旧结果时，不覆盖历史产物。

## 9. 和现有文档的关系

这份设计和现有几份文档是互补关系。

[test-plan-resource-profile-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/test-plan-resource-profile-v0.1.md) 保留自动化执行方案和已有执行记录；[resource-profile-report-2026-05-26.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/resource-profile-report-2026-05-26.md) 继续记录某次测试的具体结论；[Android本地音乐特征能力-资源与运行约束说明-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-资源与运行约束说明-v0.1.md) 则把这些结论翻译成工程约束。

这篇文档的角色，是把长期复用的测试口径固定下来。
