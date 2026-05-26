# Android 端侧资源占用测试计划 v0.1

## 1. 测试目标与非目标

### 1.1 测试目标

本测试计划用于把 Android 端提前提取阶段的资源占用测试升级为自动化执行方案，覆盖以下两类阶段：

- `RUN AUDIO IDENTITY`
- `RUN LOCAL FEATURE`

首版自动化测试完成后应具备：

- 自动触发目标阶段。
- 自动采集 CPU / 内存 / I/O / 时延数据。
- 自动落盘原始采样文件。
- 自动生成 `summary.json` 与 `report.md`。
- 通过统一 artifact 目录保留可回溯证据。
- 仅保留人工复核结论与门禁回写，不再要求人工手算峰值和增量。

### 1.2 非目标

本计划不做：

- 不直接实施性能优化。
- 不纳入 `RUN SEARCH` / `RUN RECOMMEND` 资源测试。
- 不纳入真实云端链路资源占用。
- 不强制使用 Android Studio Profiler、Perfetto 或外部功耗仪器作为首版验收条件。
- 不在本文中并列维护手工版与自动化版两套主路径。

## 2. 测试对象与自动化阶段边界

### 2.1 测试对象

- 应用包名：`com.orion.blaster.demo`
- Demo 入口：`com.orion.blaster.demo/.MainActivity`
- 自动化脚本目录：`tools/resource-profile/`
- 自动化主入口：`tools/resource-profile/run_resource_profile.sh`
- 首版 phase 参数：
  - `audio_identity`
  - `local_feature`
- 指纹阶段日志标签：`BlasterAudioIdentity`
- embedding 阶段日志标签：`BlasterLocalFeature`

### 2.2 自动化执行链路

脚本 `run_resource_profile.sh` 负责完整编排：

1. 检查设备连接与包名。
2. 获取 PID。
3. 清理 logcat。
4. 启动 app。
5. 可选执行 `RUN SCAN + MATCH` 作为前置准备。
6. 通过 `adb shell input tap` 自动点击目标阶段按钮。
7. 在阶段运行期间定时采样：
   - `top -H`
   - `dumpsys meminfo`
   - `/proc/$PID/status`
   - `/proc/$PID/io`
   - `logcat`
8. 监听阶段完成日志。
9. 停止采样。
10. 生成原始文件、汇总文件和 Markdown 报告草稿。

### 2.3 阶段边界定义

#### 空闲基线

从 app 启动完成并进入 `MainActivity` 开始，到自动化脚本在不执行任何阶段按钮的情况下静置 10 秒结束。

#### 指纹提取阶段

从脚本自动点击 `RUN AUDIO IDENTITY` 开始，到脚本确认当前轮次提取完成为止。

自动完成判定：

- 至少出现一条 `BlasterAudioIdentity extracted ... digest=sha256:... payloadSize=...`
- 且静默窗口内不再出现新的 `extracted`
- compare 关闭时允许伴随 `compare_skipped`
- 若超时未出现提取日志，则：
  - `completion_signal = timeout_no_extract`

#### embedding 提取阶段

从脚本自动点击 `RUN LOCAL FEATURE` 开始，到脚本确认当前 drain 周期完成为止。

自动完成判定：

- 至少出现一条 `BlasterLocalFeature extracted ... embeddingDim=1024 ...`
- 且出现：
  - `drain_completed`
  - 或 `drain_timeout`
- 若无 embedding 提取日志，则：
  - `completion_signal = timeout_no_embedding`

## 3. 状态变更规则

本资源测试执行状态只允许在本文档维护，`dev-plan-v0.1.md` 与 `mvp-4-local-embedding-validation.md` 只保留入口引用，不承载进度状态。

状态只允许三种：

- `[ ]` 未开始
- `[~]` 进行中
- `[x]` 已完成

状态变更规则：

- 进入某个里程碑时，将对应进度标记从 `[ ]` 改为 `[~]`。
- 进入里程碑时，必须在该里程碑的 `验收记录` 中写入：`开始时间`、`负责人`、`基线分支`。
- 完成里程碑时，才允许将对应进度标记改为 `[x]`。
- 完成里程碑时，必须在 `验收记录` 中补齐：`完成时间`、`执行命令`、`关键日志/截图`、`数据产物`、`门禁结论`。
- 若执行失败或被阻塞，保留 `[~]`，并在 `验收记录` 中写明：`阻塞点`、`停止条件命中`、`回退动作`。
- 禁止跳步：只有前一里程碑为 `[x]`，下一里程碑才允许从 `[ ]` 切换到 `[~]`。

## 4. 执行拆分（5 个里程碑）

本计划按 5 个里程碑推进。每个里程碑都必须形成自动化测试可核查产物，后续里程碑不得跳步。

进度标记：

- [x] 里程碑 1：文档与自动化接口冻结
- [x] 里程碑 2：采集器与 artifact 骨架
- [ ] 里程碑 3：Audio Identity 自动采集与汇总
- [ ] 里程碑 4：Local Feature 自动采集与汇总
- [ ] 里程碑 5：验收与文档回写规范

### 4.1 里程碑 1：文档与自动化接口冻结

产出：

- 本文档自动化版本定稿。
- `tools/resource-profile/README.md`
- 脚本参数、输出文件格式、完成判定冻结。
- `dev-plan-v0.1.md` 与 `mvp-4-local-embedding-validation.md` 的自动化入口引用。

完成标准：

- 本文档存在，且包含 `状态变更规则` 与 `进度标记`。
- 文档中已写死：
  - 脚本入口
  - phase 参数
  - 输出目录结构
  - 自动完成判定
- 状态只在本文档维护，不在入口文档双写。

验收记录：

- 开始时间：2026-05-26 11:27:37 CST
- 完成时间：2026-05-26 11:27:37 CST
- 负责人：Codex
- 基线分支：main
- 执行命令：`rg -n "资源占用测试|进度标记|里程碑 1|里程碑 2|里程碑 3|里程碑 4|里程碑 5" .ai/prd/features/android-music-feature-extraction/test-plan-resource-profile-v0.1.md .ai/prd/features/android-music-feature-extraction/dev-plan-v0.1.md .ai/prd/features/android-music-feature-extraction/mvp-plans/mvp-4-local-embedding-validation.md`；`sed -n '1,260p' .ai/prd/features/android-music-feature-extraction/test-plan-resource-profile-v0.1.md`；`rg -n "BlasterAudioIdentity|BlasterLocalFeature|drain_completed|drain_timeout|compare_skipped|extracted" demo/src/main/java core/src/main/java`
- 关键日志/截图：确认 `BlasterAudioIdentity` 日志由 `FeaturePipeline` 输出；确认 `BlasterLocalFeature` 的 `extracted`、`drain_completed`、`drain_timeout` 由 `MainActivity` 输出，可作为自动完成判定信号。
- 数据产物：本文档自动化版本定稿；`dev-plan-v0.1.md` 与 `mvp-4-local-embedding-validation.md` 已保持自动化入口引用且不双写状态。
- 门禁结论：通过。脚本入口、phase 参数、输出文件集合、完成判定与唯一状态来源均已冻结，允许进入里程碑 2。
- 阻塞与处理：无

### 4.2 里程碑 2：采集器与 artifact 骨架

产出：

- `run_resource_profile.sh` 主脚本骨架。
- CPU / 内存 / I/O / logcat 采集器子脚本。
- artifact 目录结构。
- 样例空输出。

完成标准：

- 里程碑 1 必须为 `[x]`。
- 新增目录结构已固定：
  - `tools/resource-profile/README.md`
  - `tools/resource-profile/run_resource_profile.sh`
  - `tools/resource-profile/collectors/`
  - `tools/resource-profile/templates/`
  - `tools/resource-profile/artifacts/`
- 不运行真实测试也能生成目录骨架。
- 脚本失败时仍会写出 `meta.json` 与错误状态。
- `.gitignore` 已忽略 `tools/resource-profile/artifacts/**`。

验收记录：

- 开始时间：2026-05-26 11:27:37 CST
- 完成时间：2026-05-26 11:32:15 CST
- 负责人：Codex
- 基线分支：main
- 执行命令：`chmod +x tools/resource-profile/run_resource_profile.sh tools/resource-profile/collectors/collect_cpu_sample.sh tools/resource-profile/collectors/collect_mem_sample.sh tools/resource-profile/collectors/collect_io_sample.sh tools/resource-profile/collectors/capture_phase_logcat.sh`；`tools/resource-profile/run_resource_profile.sh --phase audio_identity --dry-run`；`ls -la tools/resource-profile/artifacts/20260526-113149-audio_identity`
- 关键日志/截图：dry-run 输出 `Dry run scaffold generated at /Volumes/ORICO/git/ext/Blaster/tools/resource-profile/artifacts/20260526-113149-audio_identity`；`meta.json` 状态为 `dry_run_ready`。
- 数据产物：`tools/resource-profile/README.md`；`tools/resource-profile/run_resource_profile.sh`；`tools/resource-profile/collectors/`；`tools/resource-profile/templates/`；`tools/resource-profile/artifacts/.gitkeep`；样例 artifact：`tools/resource-profile/artifacts/20260526-113149-audio_identity/`
- 门禁结论：通过。工具目录结构、参数解析、占位输出文件和失败前落盘机制已建立；`.gitignore` 已忽略 `tools/resource-profile/artifacts/**`。
- 阻塞与处理：无

### 4.3 里程碑 3：Audio Identity 自动采集与汇总

产出：

- `audio_identity` 自动执行能力。
- 原始采样文件。
- 自动生成 `summary.json` 与 `report.md`。

完成标准：

- 里程碑 2 必须为 `[x]`。
- 脚本可自动触发 `RUN AUDIO IDENTITY`。
- 能捕捉 `BlasterAudioIdentity extracted`。
- 自动生成以下文件：
  - `meta.json`
  - `cpu_samples.csv`
  - `mem_samples.csv`
  - `io_samples.csv`
  - `phase.logcat.txt`
  - `summary.json`
  - `report.md`
- 自动计算 CPU / 内存 / I/O / 耗时摘要。

验收记录：

- 开始时间：未完成
- 完成时间：未完成
- 负责人：未完成
- 基线分支：未完成
- 执行命令：未完成
- 关键日志/截图：未完成
- 数据产物：未完成
- 门禁结论：未完成
- 阻塞与处理：无

### 4.4 里程碑 4：Local Feature 自动采集与汇总

产出：

- `local_feature` 自动执行能力。
- 原始采样文件。
- 自动生成 `summary.json` 与 `report.md`。

完成标准：

- 里程碑 3 必须为 `[x]`。
- 脚本可自动触发 `RUN LOCAL FEATURE`。
- 能捕捉 `BlasterLocalFeature extracted`。
- 能识别 `drain_completed` 或 `drain_timeout`。
- 自动生成多轮耗时与峰值摘要。

验收记录：

- 开始时间：未完成
- 完成时间：未完成
- 负责人：未完成
- 基线分支：未完成
- 执行命令：未完成
- 关键日志/截图：未完成
- 数据产物：未完成
- 门禁结论：未完成
- 阻塞与处理：无

### 4.5 里程碑 5：验收与文档回写规范

产出：

- 自动化执行验收记录。
- 文档回写模板。
- `v0.1` 作为自动化版的替代关系说明。

完成标准：

- 里程碑 4 必须为 `[x]`。
- 每次真实执行都能产出可留档 artifact。
- 文档验收记录可直接引用 `report.md` / `summary.json`。
- 人工只需补结论，不需要手算峰值和增量。

验收记录：

- 开始时间：未完成
- 完成时间：未完成
- 负责人：未完成
- 基线分支：未完成
- 执行命令：未完成
- 关键日志/截图：未完成
- 数据产物：未完成
- 门禁结论：未完成
- 阻塞与处理：无

## 5. 自动化测试验收基线

### 5.1 指标冻结

本计划固定采集以下 6 类指标：

- CPU
  - 进程 CPU
  - 线程级 CPU
- 内存
  - `TOTAL PSS`
  - `Native Heap`
  - `Dalvik Heap`
  - `Threads`
- I/O
  - `read_bytes`
  - `write_bytes`
  - `syscr`
  - `syscw`
- 时延
  - 阶段开始到阶段完成耗时
- 稳定性
  - 连续运行是否崩溃、ANR、耗时漂移
- 补充观测
  - 功耗 / 热量 / 降频现象（首版只记录观察，不强制仪器量化）

### 5.2 输出目录与文件结构

输出目录固定为：

- `tools/resource-profile/artifacts/<timestamp>-<phase>/`

每次执行一个阶段，固定输出以下文件：

- `meta.json`
- `cpu_samples.csv`
- `mem_samples.csv`
- `io_samples.csv`
- `phase.logcat.txt`
- `summary.json`
- `report.md`

### 5.3 自动汇总字段冻结

`summary.json` 必须至少包含以下字段：

- `phase`
- `started_at`
- `ended_at`
- `elapsed_ms`
- `cpu_peak_process`
- `cpu_peak_thread`
- `pss_peak_kb`
- `native_heap_peak_kb`
- `dalvik_heap_peak_kb`
- `thread_peak`
- `read_bytes_delta`
- `write_bytes_delta`
- `syscr_delta`
- `syscw_delta`
- `completion_signal`
- `sample_count`

### 5.4 报告自动汇总与人工复核边界

自动分析只做统计和事实提炼，不自动输出性能优劣结论。

`report.md` 至少生成以下段落：

- 测试元信息
- 阶段完成信号
- CPU 峰值摘要
- 内存峰值摘要
- I/O 增量摘要
- 耗时摘要
- 关键日志片段
- 人工复核结论占位

人工仍需补充两类内容：

- 是否达到“首版可接受资源行为”
- 风险判断与后续优化建议

### 5.5 验收记录模板字段

每个里程碑统一使用以下字段，不允许删减：

- `开始时间`
- `完成时间`
- `负责人`
- `基线分支`
- `执行命令`
- `关键日志/截图`
- `数据产物`
- `门禁结论`
- `阻塞与处理`

要求：

- `数据产物` 必须引用具体 artifact 路径或文件名。
- `关键日志/截图` 必须引用 `phase.logcat.txt` 中的关键片段或自动报告摘要。
- 不再接受“已手动采集”这类描述。

## 6. 与后续优化工作的交接

后续优化阶段可以基于本文自动化产物继续推进，但不在本文中直接执行优化：

- 若 CPU 峰值异常，优先分析解码线程、推理线程与批次大小。
- 若 `Native Heap` 持续不回落，优先排查 JNI、TFLite runtime、MediaCodec 资源释放。
- 若 I/O 增量异常，优先排查重复解码、重复读文件与重复落库。
- 若后几轮耗时显著高于前几轮，优先排查热衰减、降频与后台调度。
- 若首版资源行为不可接受，应新开优化计划文档，而不是改写本文历史验收结果。
