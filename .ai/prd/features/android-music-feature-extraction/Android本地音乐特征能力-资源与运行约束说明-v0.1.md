# Android 本地音乐特征能力资源与运行约束说明 v0.1

当前已有真实资源画像的阶段有两条：

- `audio_identity`
- `local_feature`

这两条链路都已真实跑通。后续设计约束和优化优先级以这批结果为基础。

## 1. 已有结果

### 1.1 `audio_identity`

参考来源：

- [resource-profile-report-2026-05-26.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/resource-profile-report-2026-05-26.md)
- [report.md](/Volumes/ORICO/git/ext/Blaster/tools/resource-profile/artifacts/final-audio-identity-run-2/report.md)

当前样本显示：

- 已成功提取真实音频指纹
- 单轮采样窗口约 61 秒
- 进程 CPU 峰值约 1.25 核
- PSS 峰值约 173MB

### 1.2 `local_feature`

参考来源：

- [resource-profile-report-2026-05-26.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/resource-profile-report-2026-05-26.md)

当前样本显示：

- 已成功提取真实 embedding
- bounded run 约 201 秒
- CPU 峰值与 `audio_identity` 接近
- PSS 峰值接近 200MB
- I/O 调用更多，存在多轮 drain

资源指标口径如下：

| 指标 | 含义 | 典型来源 | 在本页如何被引用 | 使用边界 |
| --- | --- | --- | --- | --- |
| `elapsed_ms` | 当前阶段从触发到出现完成信号的总耗时，单位毫秒 | `summary.json` / 汇总结果 | `audio_identity` 约 61 秒，`local_feature` 约 201 秒 | 用于阶段级对比，不直接表示单首歌曲耗时 |
| `cpu_peak_process` | 采样窗口内目标进程的 CPU 峰值 | `top` / 汇总结果 | “进程 CPU 峰值约 1.25 核”对应此指标 | 反映进程级峰值，不等于平均 CPU |
| `cpu_peak_thread` | 采样窗口内单线程 CPU 峰值 | `top -H` / 汇总结果 | 本页未单独展开，只作为 CPU 峰值拆分参考 | 用于看最忙线程，不单独决定阶段轻重 |
| `pss_peak_kb` | 采样窗口内进程 PSS 内存峰值，单位 KB | `dumpsys meminfo` / 汇总结果 | “PSS 峰值约 173MB / 接近 200MB”对应此指标 | 用于比较内存压力，不等于单独 heap 大小 |
| `thread_peak` | 采样窗口内线程数峰值 | `/proc/$PID/status` / 汇总结果 | 本页未单列展示，作为辅助观测项 | 用于观察线程膨胀，不直接表示线程质量 |
| `read_bytes_delta` | 采样窗口内进程读字节增量 | `/proc/$PID/io` / 汇总结果 | “I/O 调用更多”部分参考此指标 | 个别样本可能为 0，通常不单独下强结论 |
| `syscr_delta` | 采样窗口内进程读系统调用次数增量 | `/proc/$PID/io` / 汇总结果 | “I/O 调用更多”部分同时参考此指标 | 主要用于辅助判断读操作活跃度，通常和 `read_bytes_delta` 一起看 |
| `round_count` | 多轮处理阶段的轮次统计 | 阶段日志 / 汇总结果 | “存在多轮 drain”主要对应此指标 | 只对 `local_feature` 这类多轮阶段有意义 |

## 2. 结论

当前资源压力重点在 `local_feature`，不在基础信息处理，也不在 Search/Recommend 消费本身。

按成本粗分如下：

- 低成本：本地扫描、基础信息提取、metadata 检索消费
- 中等偏高：音频解码与指纹生成
- 高成本：本地 embedding 推理与多轮 drain

这里用到的几个术语含义如下：

| 术语 | 含义 |
| --- | --- |
| `metadata 检索消费` | 只消费已有元数据结果的检索请求，不包含新的音频解码、指纹生成或模型推理 |
| `Search/Recommend 消费` | 前台或调用侧使用已有信号完成搜索或推荐，不负责后台提取 |
| `本地 embedding 推理` | 端侧模型对本地歌曲生成 embedding 的过程，属于高成本后台阶段 |
| `drain` | 后台阶段按轮处理待处理队列的过程，常见于 `local_feature` 这类阶段 |

当前结果已经证明：

- 音频指纹提取是真实执行的
- 本地 embedding 提取是真实执行的
- 两条链路具备可重复资源画像能力

当前结果还不能证明：

- 当前版本已足够省资源，可以直接大规模上线
- 全量队列自然跑完时的最终成本已可接受
- 长时间连续运行后不存在明显热衰减或资源堆积

## 3. 运行约束

高成本任务约束如下：

- 不明显影响播放
- 不明显拖慢前台交互
- 高温、低电量、前台繁忙时支持暂停或延后
- 支持按业务开关关闭

Search/Recommend 约束如下：

- 只消费已有信号
- 不在前台路径触发音频解码
- 不在前台路径触发指纹生成
- 不在前台路径触发本地模型推理

调度约束如下：

- 低成本元数据处理与高成本特征处理分开看待
- 保留限流和预算控制
- 明确区分 bounded run 与自然清空

相关术语说明如下：

| 术语 | 含义 |
| --- | --- |
| `bounded run` | 在受控时间窗口内运行的测试方式，用于拿到可比样本，不等于自然清空全队列 |
| `自然清空` | 不额外截断时间窗口，等待当前待处理队列自行处理完成 |
| `idle baseline` | 空闲状态下的对照样本，用于和真实处理阶段比较资源增量 |
| `重复运行漂移` | 同一阶段重复执行多次后，耗时、峰值或 I/O 指标的波动情况 |

完成信号解释如下：

| 信号 | 适用阶段 | 含义 | 是否视为有效样本 | 为什么不等于失败 |
| --- | --- | --- | --- | --- |
| `profile_window_elapsed_after_extract` | `audio_identity` | 已拿到有效提取样本，采样窗口到点后停止 | 是 | 表示测试窗口按预设策略结束，不是提取失败或中途退出 |
| `drain_timeout` | `local_feature` | bounded run 到达设定窗口后正常结束 | 是 | 表示受控测试窗口结束，不代表崩溃、卡死或失控 |

两者都不等于失败。

## 4. 优先级

当前优化顺序如下：

1. 先优化 `local_feature`
   当前依据是 `elapsed_ms` 更长、`pss_peak_kb` 更高、`read_bytes_delta / syscr_delta` 更高，且存在 `round_count`。
2. 补齐 idle baseline 和重复运行漂移观察
   当前已有真实样本，但还缺空闲对照和重复执行后的波动判断。
3. 稳定 `audio_identity` 的资源窗口和对照口径
   当前已可测，但 `read_bytes_delta = 0` 这类单项结果不宜直接下强结论。
4. 本地提取链路稳定后，再看 Search/Recommend 前台性能

持续观察项如下：

- 多轮 drain 是否持续拉长处理时间
- 曲库规模扩大后，耗时是否近似线性放大
- 模型版本变化后，是否引入新的内存峰值或 I/O 压力
- 不同 ROM 和热状态下，后台执行是否稳定

## 5. 关联文档

- 资源测试计划：[test-plan-resource-profile-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/test-plan-resource-profile-v0.1.md)
- 资源测试结论：[resource-profile-report-2026-05-26.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/resource-profile-report-2026-05-26.md)
- 性能测试设计：[Android本地音乐特征能力-性能测试设计-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-性能测试设计-v0.1.md)
