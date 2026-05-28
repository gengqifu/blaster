# Android 本地音乐特征能力资源与运行约束说明 v0.1

这篇文档的出发点很简单：资源测试已经做了，数字也已经拿到了，接下来不能只把它们留在报告里。更重要的是把这些结果翻译成设计约束，明确哪些链路现在就该收着跑，哪些地方后面要优先优化。

当前最有价值的事实并不是某个单一峰值，而是两条真实端侧链路已经跑通，并且它们的资源特征差异已经足够清楚：`audio_identity` 能跑，`local_feature` 也能跑，但后者明显更重。

## 1. 当前已有的资源画像

截至 `2026-05-26`，已经完成真实画像的阶段有两条：

- `audio_identity`
- `local_feature`

这里强调“真实”，是为了把结论和 mock、占位路径区分开。当前看到的 CPU、内存和 I/O 数据，都来自真实提取过程，而不是空跑流程。

## 2. 两条链路的直观差别

### 2.1 `audio_identity`

参考来源：

- [resource-profile-report-2026-05-26.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/resource-profile-report-2026-05-26.md)
- [report.md](/Volumes/ORICO/git/ext/Blaster/tools/resource-profile/artifacts/final-audio-identity-run-2/report.md)

这一条链路已经能稳定提取真实音频指纹。当前样本里，单轮采样窗口大约 61 秒，进程 CPU 峰值约 1.25 核，PSS 峰值大约 173MB。它的资源形态比较像“持续读文件 + 音频解码 + JNI 指纹提取”的组合负载。

### 2.2 `local_feature`

参考来源：

- [resource-profile-report-2026-05-26.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/resource-profile-report-2026-05-26.md)

`local_feature` 也已经真实跑通，但资源表现更重。当前样本里，bounded run 大约 201 秒，CPU 峰值和 `audio_identity` 接近，持续时间却明显更长；PSS 峰值接近 200MB，同时还能看到更多 I/O 调用和多轮 drain 行为。

## 3. 这些结果说明了什么

最直接的结论是：当前资源压力不在基础信息处理，也不在 Search/Recommend 消费本身，重点落在 `local_feature`。它比 `audio_identity` 更重，不只是因为某个指标更高，而是因为时间、内存和 I/O 都在同一个方向上变差。

如果把整条链路按成本粗分，大致是这样的：

- 低成本：本地扫描、基础信息提取、metadata 检索消费
- 中等偏高：音频解码和指纹生成
- 高成本：本地 embedding 推理和多轮 drain

这个分层很重要，因为它直接决定了哪些阶段适合放在前面做早停，哪些阶段必须被开关和调度保护起来。

## 4. 当前结论的边界

已有测试已经证明三件事：音频指纹提取是真实执行的，本地 embedding 提取也是真实执行的，这两条链路都已经具备可重复的资源画像能力。

但这还不足以推出“当前版本已经足够省资源，可以直接大规模上线”。同样也还不能说明，全量队列自然跑完时的最终成本已经落在可接受范围内，更不能说明长时间连续运行之后就一定不会出现热衰减、性能漂移或资源堆积。

换句话说，当前阶段更接近“已经能稳定测”，还不是“已经把成本压到位”。

## 5. 完成信号怎么理解

`profile_window_elapsed_after_extract` 代表的是：已经拿到有效提取样本，资源画像窗口也够了，所以脚本主动在受控窗口内收口。它不是失败。

`drain_timeout` 表达的也是类似的意思：bounded run 在设定窗口内正常结束。这同样不等于崩溃，只说明当前采样策略选择了一个可控的停止点。

把这两个信号理解清楚，后面看资源报告时就不会把“正常收口”和“异常退出”混在一起。

## 6. 对运行方式的约束

音频指纹和本地 embedding 都属于高成本任务，所以它们不能像普通元数据读取那样随时跑。至少在当前版本，它们应该满足几条共同约束：不要明显影响播放，不要把前台交互拖慢，在高温、低电量或前台繁忙时可以暂停或延后，并且能被业务开关直接关闭。

Search/Recommend 这边的约束更简单，也更硬：它现在只能消费已有信号，不能在前台请求路径里临时触发音频解码、指纹生成或本地模型推理。否则后台处理成本会直接外溢到前台体验。

调度层面也要把低成本元数据处理和高成本特征处理分开看，尤其要保留清晰的限流和预算意识，而不是把 bounded run 和自然清空混成同一种执行模式。

## 7. 对后续设计的影响

这些结果会直接反过来影响设计取舍。基础信息层必须承担更多早停责任；`fingerprint` 这层要继续在“提升置信度”和“控制提取窗口”之间找平衡；`local_feature` 则应该被默认视为当前优化优先级最高的一段。

接口语义上，调用方也得清楚地区分“已经可靠关联”与“只有本地特征可兜底”这两种结果。Search/Recommend 的 explain 和降级路径之所以要写得细，本质上也是为了避免把不同置信度的结果混成一类。

## 8. 当前优化优先级

按现有证据，后续更合理的顺序是：

1. 先优化 `local_feature`
2. 补齐 idle baseline 和重复运行漂移观察
3. 稳定 `audio_identity` 的资源窗口和对照口径
4. 等本地提取链路更稳之后，再讨论 Search/Recommend 的前台性能优化

## 9. 后续持续观察项

- 多轮 drain 会不会导致处理时间持续膨胀
- 曲库规模变大时，耗时是否近似线性放大
- 模型版本变化后，是否引入新的内存峰值或 I/O 压力
- 不同 ROM 和热状态下，后台执行是否还能保持稳定

## 10. 关联文档

- 资源测试计划：[test-plan-resource-profile-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/test-plan-resource-profile-v0.1.md)
- 资源测试结论：[resource-profile-report-2026-05-26.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/resource-profile-report-2026-05-26.md)
- 性能测试设计：[Android本地音乐特征能力-性能测试设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-性能测试设计-v0.1.md)
