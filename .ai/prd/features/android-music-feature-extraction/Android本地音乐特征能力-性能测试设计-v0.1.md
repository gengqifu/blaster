# Android 本地音乐特征能力性能测试设计 v0.1

本设计固定后续性能测试口径，覆盖 `audio_identity`、`local_feature` 以及 `search/recommend`。

## 1. 测试对象

单链路测试：

- `basic_info`
- `audio_identity`
- `local_feature`

消费侧测试：

- `search`
- `recommend`

对照测试：

- `idle baseline`
- `bounded run`
- 重复运行漂移

## 2. 测什么

单链路关注：

- 时延
- CPU 峰值
- 内存峰值
- I/O 成本
- 线程峰值

消费侧关注：

- 请求时延
- 候选规模变化带来的排序成本
- `topK` 变化的影响
- 降级路径对结果与耗时的影响

回归关注：

- 资源是否回升
- 时延是否漂移
- 后台成本是否错误进入前台链路

## 3. 测试场景

场景如下：

- 首次冷启动：观察初始化、首次提取和第一轮峰值
- 重复运行：观察抖动是否消失、峰值是否稳定、时延是否漂移
- 多轮 drain：观察 `round_count`、首轮与末轮耗时差异、是否越跑越慢
- 不同歌曲规模：区分小规模、中等规模、较大规模样本
- 信号缺失与降级：覆盖 metadata only、metadata + fingerprint、metadata + embedding fallback、mixed、no-signal

## 4. 指标口径

资源类指标：

- `elapsed_ms`
- `cpu_peak_process`
- `cpu_peak_thread`
- `pss_peak_kb`
- `thread_peak`
- `read_bytes_delta`
- `syscr_delta`
- `round_count`

消费侧指标：

- `latencyMs`
- `candidateCountBeforeRank`
- `candidateCountAfterRank`
- `topK`
- `degradePath`
- `status`

资源类指标说明如下：

| 指标 | 含义 | 典型来源 | 使用边界 |
| --- | --- | --- | --- |
| `elapsed_ms` | 当前阶段从触发到完成信号出现的总耗时，单位毫秒 | `summary.json` / 自动化汇总结果 | 用于阶段级耗时对比，不直接表示单首歌曲耗时 |
| `cpu_peak_process` | 采样窗口内目标进程的 CPU 峰值 | `top` / 采样脚本汇总 | 反映进程级最高 CPU 占用，不等于平均 CPU |
| `cpu_peak_thread` | 采样窗口内单线程 CPU 峰值 | `top -H` / 采样脚本汇总 | 反映最忙线程的峰值，不等于进程整体负载 |
| `pss_peak_kb` | 采样窗口内进程 PSS 内存峰值，单位 KB | `dumpsys meminfo` / 汇总结果 | 用于比较内存压力，不等于 Java heap 单独大小 |
| `thread_peak` | 采样窗口内线程数峰值 | `/proc/$PID/status` / 汇总结果 | 用于观察线程膨胀，不直接表示线程质量 |
| `read_bytes_delta` | 采样窗口内进程读字节增量 | `/proc/$PID/io` / 汇总结果 | 反映读 I/O 变化；个别阶段可参考但不单独作为强结论依据 |
| `syscr_delta` | 采样窗口内进程读系统调用次数增量 | `/proc/$PID/io` / 汇总结果 | 用于辅助判断读操作活跃度，通常与 `read_bytes_delta` 一起看 |
| `round_count` | 多轮处理阶段的轮次统计 | 阶段日志 / 汇总结果 | 主要用于 `local_feature` 等 drain 型阶段 |

消费侧指标说明如下：

| 指标 | 含义 | 典型来源 | 使用边界 |
| --- | --- | --- | --- |
| `latencyMs` | 单次 `search/recommend` 请求耗时，单位毫秒 | `BlasterSearchRecommend` 日志 / diagnostics | 只表示消费侧请求耗时，不包含后台提取成本 |
| `candidateCountBeforeRank` | 排序前候选规模 | 检索阶段日志 / diagnostics | 用于判断召回规模，不直接表示结果质量 |
| `candidateCountAfterRank` | 排序后保留下来的结果规模 | 排序阶段日志 / diagnostics | 用于判断最终输出规模 |
| `topK` | 本次请求要求返回的数量上限 | 调用方输入 / 日志 | 用于约束结果规模，不表示实际返回数量 |
| `degradePath` | 本次请求实际采用的降级路径 | diagnostics / 日志 | 表示请求走了哪种信号组合或本地降级方式，不是错误码 |
| `status` | 本次请求结果状态 | diagnostics / 日志 | 用于区分 `OK/EMPTY/INVALID_INPUT/UNSUPPORTED` |

所有记录都附带测试时间、设备、数据集和阶段上下文。横向比较不同阶段时，完成信号和停止条件保持一致。

## 5. 有效样本

有效样本满足以下条件：

- 目标阶段被真实触发
- 采样窗口覆盖核心执行区间
- artifact 完整
- 完成信号可解释

### 5.1 `audio_identity`

有效条件：

- 出现真实 `extracted` 日志
- `completion_signal = profile_window_elapsed_after_extract` 可接受

无效条件：

- 超时且无真实提取日志
- 关键 artifact 缺失，无法还原峰值与耗时

完成信号说明如下：

| 信号 | 适用阶段 | 含义 | 是否视为有效样本 |
| --- | --- | --- | --- |
| `profile_window_elapsed_after_extract` | `audio_identity` | 已出现真实提取日志，采样窗口到点后主动停止采样 | 是 |
| `drain_completed` | `local_feature` | 多轮处理自然完成，队列在当前轮次内清空或正常结束 | 是 |
| `drain_timeout` | `local_feature` | bounded run 到达设定窗口后正常结束 | 是 |
| `timeout_no_extract` | `audio_identity` | 超时且没有出现真实提取日志 | 否 |
| `timeout_no_embedding` | `local_feature` | 超时且没有出现真实 embedding 提取日志 | 否 |

### 5.2 `local_feature`

有效条件：

- 出现真实 embedding 提取日志
- 出现 `drain_completed` 或 `drain_timeout`

无效条件：

- 只有按钮触发，没有 embedding 提取日志
- 没有明确完成信号

### 5.3 `search/recommend`

有效条件：

- 发生一次真实 `search` 或 `recommend`
- 日志中有完整 `BlasterSearchRecommend` 输出
- 能获取 `status`、候选数和时延字段

## 6. 测试方法与产物

当前自动化主入口：

- `tools/resource-profile/run_resource_profile.sh`

当前主要覆盖：

- `audio_identity`
- `local_feature`

每次测试至少保留：

- `meta.json`
- `cpu_samples.csv`
- `mem_samples.csv`
- `io_samples.csv`
- `phase.logcat.txt`
- `summary.json`
- `report.md`

artifact 说明如下：

| 文件 | 含义 | 典型用途 |
| --- | --- | --- |
| `meta.json` | 本次测试的基础元信息与执行状态 | 记录 phase、时间、状态、设备与运行上下文 |
| `cpu_samples.csv` | CPU 采样原始数据 | 回看采样点、复核峰值 |
| `mem_samples.csv` | 内存采样原始数据 | 回看 PSS 等内存变化 |
| `io_samples.csv` | I/O 采样原始数据 | 回看读写增量与系统调用变化 |
| `phase.logcat.txt` | 阶段日志原文 | 验证完成信号、提取日志和异常原因 |
| `summary.json` | 机器可读汇总结果 | 自动对比、回归判断、脚本消费 |
| `report.md` | 人工可读摘要报告 | 快速复核本次结果、回写结论 |

产物分工如下：

- `summary.json`：机器可读汇总
- `report.md`：人工复核摘要
- `phase.logcat.txt`：完成信号、阶段日志和异常线索

补充说明：

- `meta.json` 关注“这次测试是什么、跑成什么状态”。
- `summary.json` 关注“核心指标最后是多少”。
- `phase.logcat.txt` 关注“为什么认为这次样本有效或无效”。
- `report.md` 关注“给人快速看结论”。

## 7. 验收口径

链路已真实跑通，至少满足：

- 出现真实提取或真实检索日志
- 关键 artifact 完整
- 完成信号与阶段语义一致
- 结果可复核

当前版本可测但未优化完成，通常表现为：

- 已拿到稳定资源画像，但峰值或耗时偏高
- bounded run 能结束，但全量自然跑完成本未知
- 多次重复结果波动较大

优化前后比较时，固定：

- 同一设备
- 同一数据集
- 同一阶段
- 同一参数
- 同一完成信号语义

每组结果至少重复 3 次。优先对比：

- `elapsed_ms`
- `pss_peak_kb`
- `cpu_peak_process`
- `thread_peak`
- `read_bytes_delta`

## 8. 回归策略

以下改动至少重跑对应阶段性能测试：

- 音频解码策略变化
- 指纹算法、版本、ABI 或 native 集成变化
- 本地模型、模型版本或 schema 变化
- 队列、调度、限流或 drain 逻辑变化
- Search/Recommend 候选召回、排序或 explain 逻辑变化

最低重复次数：

- 单链路资源测试：3 次
- Search/Recommend 请求时延测试：每组输入 5 次

基线保留规则：

- 每次版本测试保留独立 artifact 目录
- 标注日期、分支、设备、数据集和完成信号
- 比较新旧结果时不覆盖历史产物

## 9. 关联文档

- 资源测试计划：[test-plan-resource-profile-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/test-plan-resource-profile-v0.1.md)
- 资源测试实例报告：[resource-profile-report-2026-05-26.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/resource-profile-report-2026-05-26.md)
- 资源约束说明：[Android本地音乐特征能力-资源与运行约束说明-v0.1.md](/Volumes/ORICO/git/ext/Blaster/.ai/prd/features/android-music-feature-extraction/Android本地音乐特征能力-资源与运行约束说明-v0.1.md)
