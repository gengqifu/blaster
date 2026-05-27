# Android 端侧资源占用测试结论报告（2026-05-26）

## 1. 结论摘要

本次资源测试覆盖两条真实端侧链路：

- `RUN AUDIO IDENTITY`
- `RUN LOCAL FEATURE`

结论先说：

- 两条链路都已经真实跑通，不是 mock，不是占位路径。
- `audio_identity` 已成功提取真实指纹，并留下完整资源画像证据。
- `local_feature` 已成功提取真实 embedding，并留下完整资源画像证据。
- 当前更重的阶段是 `local_feature`，不是 search/recommend。
- 以首版资源画像目标来看，本次测试**有效**，但还不能直接下“资源成本已经足够低”的结论。

一句话总结：

- `audio_identity` 可用，资源成本中等偏高。
- `local_feature` 可用，但明显更重，是后续优化优先级更高的链路。

## 2. 关键指标

### 2.1 Audio Identity

数据来源：

- [summary.json](/Volumes/ORICO/git/ext/Blaster/tools/resource-profile/artifacts/final-audio-identity-run-2/summary.json)
- [phase.logcat.txt](/Volumes/ORICO/git/ext/Blaster/tools/resource-profile/artifacts/final-audio-identity-run-2/phase.logcat.txt)

关键指标：

- `completion_signal = profile_window_elapsed_after_extract`
- `elapsed_ms = 61000`
- `cpu_peak_process = 125`
- `cpu_peak_thread = 103`
- `pss_peak_kb = 176815`
- `thread_peak = 43`
- `read_bytes_delta = 0`
- `syscr_delta = 6111`

### 2.2 Local Feature

数据来源：

- [summary.json](/Volumes/ORICO/git/ext/Blaster/tools/resource-profile/artifacts/final-local-feature-run/summary.json)
- [phase.logcat.txt](/Volumes/ORICO/git/ext/Blaster/tools/resource-profile/artifacts/final-local-feature-run/phase.logcat.txt)

关键指标：

- `completion_signal = drain_timeout`
- `elapsed_ms = 201000`
- `cpu_peak_process = 125`
- `cpu_peak_thread = 65.3`
- `pss_peak_kb = 199822`
- `thread_peak = 44`
- `read_bytes_delta = 13377536`
- `syscr_delta = 26309`
- `round_count = 9`

## 3. 分阶段解读

### 3.1 Audio Identity

这条链路已经真实执行并成功提取指纹。原始日志中可以看到多条：

- `BlasterAudioIdentity extracted ... digest=sha256:... payloadSize=...`

本轮完成信号 `profile_window_elapsed_after_extract` 的人话解释是：

- 已经成功提取到真实指纹
- 资源画像样本已经足够
- 但没有等待整个队列完全清空

这不是失败，也不是中途崩溃，而是为了让单次资源测试窗口可控，主动在“已经拿到有效样本”后收口。

从资源行为看：

- 单轮时长约 61 秒
- 进程 CPU 峰值约 1.25 核
- PSS 峰值约 173MB
- 更像“持续读文件 + 音频解码 + JNI 指纹提取”的复合负载

这条链路当前的总体判断是：

- 真实可用
- 资源成本中等偏高
- 可以继续做后续优化，但已经具备测试与验收价值

### 3.2 Local Feature

这条链路已经真实执行并成功提取 embedding。原始日志中可以看到多条：

- `BlasterLocalFeature extracted roundIndex=... embeddingDim=1024 ...`

本轮完成信号 `drain_timeout` 的人话解释是：

- Local Feature 在受控时间窗口内连续处理了多轮
- 到达设定窗口后主动收口
- 表示“bounded run 正常结束”，不是崩溃

同时，报告里能看到：

- 共执行 9 轮 drain
- 本轮实际处理了 45 首歌
- 首轮 `elapsedMs ≈ 22s`
- 末轮 `elapsedMs ≈ 202s`

从资源行为看：

- 单轮时长约 201 秒，明显长于 `audio_identity`
- 进程 CPU 峰值也约 1.25 核
- PSS 峰值接近 200MB
- 真实读取量约 13MB
- 线程峰值 44，和 `audio_identity` 同级，但持续时间明显更长

这条链路当前的总体判断是：

- 真实可用
- 比 `audio_identity` 明显更重
- 当前端侧资源压力的重点在 `local_feature`

## 4. 当前风险判断

### 4.1 已确认的风险

- `local_feature` 明显比 `audio_identity` 更重。
  - 主要体现在总时长更长、内存峰值更高、I/O 调用更多。
- `local_feature` 当前是受控超时收口，不是一次性把全队列自然跑完。
  - 这对“资源画像”是足够的，但对“全量一次跑完成本”还不是最终答案。
- `audio_identity` 的 `read_bytes_delta = 0` 本轮不应作为强结论依据。
  - 当前更可信的是 `syscr_delta` 和原始 phase 日志。

### 4.2 暂不视为异常的问题

- `profile_window_elapsed_after_extract`
  - 这是本次测试策略定义下的有效完成，不是失败。
- `drain_timeout`
  - 这是本次 bounded run 的正常收口，不代表崩溃或失控。

### 4.3 当前可接受性判断

如果目标只是验证“端侧两条真实提取链路是否已经能跑、是否能拿到资源画像证据”，本次结果是**可接受**的。

如果目标是验证“当前版本已经足够省资源，可直接大规模上线”，本次结果还**不够**。

原因很直接：

- `local_feature` 的单轮成本仍然偏高
- 当前更像是“已具备真实测量能力”，而不是“已完成性能优化”

## 5. 下一步建议

按优先级排序：

1. 补空闲基线。
   - 当前已经有两条提取链路的结果，但还缺一个统一的空闲对照，后续比较会更稳。
2. 每条链路至少再重复跑 3 次。
   - 观察 `elapsed_ms`、`pss_peak_kb`、`cpu_peak_process` 是否漂移。
3. 给 `local_feature` 增加热量 / 降频观察。
   - 目前它是明显更重的阶段，应该重点看是否越跑越慢。
4. 优先优化 `local_feature`，再看 `audio_identity`。
   - 因为当前的资源压力重点已经很清楚。
5. 保持 artifact 产出格式稳定。
   - 后续所有优化都应该继续复用当前自动化脚本和输出格式，避免回到手工分析。

## 6. 原始证据引用

### 6.1 Audio Identity

- [summary.json](/Volumes/ORICO/git/ext/Blaster/tools/resource-profile/artifacts/final-audio-identity-run-2/summary.json)
- [report.md](/Volumes/ORICO/git/ext/Blaster/tools/resource-profile/artifacts/final-audio-identity-run-2/report.md)
- [phase.logcat.txt](/Volumes/ORICO/git/ext/Blaster/tools/resource-profile/artifacts/final-audio-identity-run-2/phase.logcat.txt)

### 6.2 Local Feature

- [summary.json](/Volumes/ORICO/git/ext/Blaster/tools/resource-profile/artifacts/final-local-feature-run/summary.json)
- [report.md](/Volumes/ORICO/git/ext/Blaster/tools/resource-profile/artifacts/final-local-feature-run/report.md)
- [phase.logcat.txt](/Volumes/ORICO/git/ext/Blaster/tools/resource-profile/artifacts/final-local-feature-run/phase.logcat.txt)

## 7. 结尾判断

本次 2026-05-26 的测试已经证明三件事：

- 指纹提取是真实的
- embedding 提取是真实的
- 两条链路都已经具备可重复的资源画像证据

接下来不该再纠结“是不是 mock”，而应该进入下一阶段：

- 以 `local_feature` 为优先优化对象
- 用同一套自动化脚本继续做基线、回归和漂移对比
