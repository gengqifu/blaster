# Android 端侧资源占用测试计划 v0.1

## 1. 测试目标与非目标

### 1.1 测试目标

本测试计划用于验证 Android 端以下两类提前提取阶段的资源占用情况：

- `RUN AUDIO IDENTITY`
- `RUN LOCAL FEATURE`

首版测试完成后应具备：

- 可复现的空闲基线采集方法。
- 可复现的指纹提取阶段资源采集方法。
- 可复现的 embedding 提取阶段资源采集方法。
- 可回溯到命令、日志和数据结果的验收记录。
- 可对比两阶段在 CPU、内存、I/O、时延与稳定性上的差异。

### 1.2 非目标

本计划不做：

- 不执行性能优化改造。
- 不把 `RUN SEARCH` / `RUN RECOMMEND` 纳入首版资源画像。
- 不测试真实云端链路资源占用。
- 不强制使用 Android Studio Profiler、Perfetto 或外部功耗仪器作为首版验收条件。
- 不在本计划中维护功能开发进度状态。

## 2. 测试对象与阶段边界

### 2.1 测试对象

- 应用包名：`com.orion.blaster.demo`
- Demo 入口：`com.orion.blaster.demo/.MainActivity`
- 指纹阶段日志标签：`BlasterAudioIdentity`
- embedding 阶段日志标签：`BlasterLocalFeature`

### 2.2 阶段边界定义

#### 空闲基线

从 app 启动完成并进入 `MainActivity` 开始，到用户不执行任何按钮操作并静置 10 秒结束。

#### 指纹提取阶段

从点击 `RUN AUDIO IDENTITY` 开始，到当前轮次最后一条有效提取日志结束。

首版完成判定日志：

- `extracted ... digest=sha256:... payloadSize=...`
- compare 关闭时可见 `compare_skipped ...`

#### embedding 提取阶段

从点击 `RUN LOCAL FEATURE` 开始，到当前 drain 周期出现完成日志结束。

首版完成判定日志：

- `drain_round_start`
- `drain_round_result`
- `extracted ... embeddingDim=1024 embeddingHead=[...]`
- `drain_completed` 或 `drain_timeout`

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

本计划按 5 个里程碑推进。每个里程碑必须形成可核查产物，后续里程碑不得跳步。

进度标记：

- [ ] 里程碑 1：文档与测试口径冻结
- [ ] 里程碑 2：空闲基线采集
- [ ] 里程碑 3：Audio Identity 资源测试
- [ ] 里程碑 4：Local Feature 资源测试
- [ ] 里程碑 5：验收与收口

### 4.1 里程碑 1：文档与测试口径冻结

产出：

- 新测试计划文档初稿。
- 测试范围、指标、命令、日志口径冻结。
- `dev-plan-v0.1.md` 与 `mvp-4-local-embedding-validation.md` 的入口引用。

完成标准：

- 本文档存在，且包含 `状态变更规则` 与 `进度标记`。
- 测试范围仅覆盖 `RUN AUDIO IDENTITY` 与 `RUN LOCAL FEATURE`。
- 指标、命令、日志字段、阶段完成判定已写死。
- 入口引用已加，但状态只在本文档维护。

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

### 4.2 里程碑 2：空闲基线采集

产出：

- demo 空闲基线数据。
- 一次完整的 CPU / 内存 / I/O 基线记录。

完成标准：

- 里程碑 1 必须为 `[x]`。
- 成功记录 PID。
- 成功记录以下命令结果：
  - `adb shell dumpsys cpuinfo | rg com.orion.blaster.demo`
  - `adb shell top -H -p "$PID" -n 1`
  - `adb shell dumpsys meminfo com.orion.blaster.demo`
  - `adb shell cat /proc/$PID/status | rg 'VmRSS|VmSize|Threads'`
  - `adb shell cat /proc/$PID/io`
- 验收记录中必须写出基线数值，不允许只写“已采集”。

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

### 4.3 里程碑 3：Audio Identity 资源测试

产出：

- `RUN AUDIO IDENTITY` 阶段资源数据。
- 指纹提取阶段日志证据。
- 与空闲基线的增量对比。

完成标准：

- 里程碑 2 必须为 `[x]`。
- 成功执行指纹阶段前置：
  - `adb shell pidof -s com.orion.blaster.demo`
  - `adb logcat -c`
  - `adb shell am start -n com.orion.blaster.demo/.MainActivity`
- 日志中出现：
  - `extracted ... digest=sha256:... payloadSize=...`
- compare 关闭时可补充记录：
  - `compare_skipped ...`
- 有 CPU / 内存 / I/O 采样结果。
- 有阶段开始时间、结束时间与阶段耗时。
- 验收记录必须写出峰值与增量，不允许只写“有波动”。

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

### 4.4 里程碑 4：Local Feature 资源测试

产出：

- `RUN LOCAL FEATURE` 阶段资源数据。
- embedding 提取阶段日志证据。
- 多轮 drain 资源变化记录。

完成标准：

- 里程碑 3 必须为 `[x]`。
- 日志中出现：
  - `extracted ... embeddingDim=1024 embeddingHead=[...]`
  - `drain_completed` 或 `drain_timeout`
- 有 CPU / 内存 / I/O 峰值与阶段耗时。
- 有“前几轮 vs 后几轮”耗时或现象记录，用于判断是否存在降频、热衰减或漂移。
- 验收记录必须区分：
  - 进程级资源变化
  - 阶段级日志完成证据

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

### 4.5 里程碑 5：验收与收口

产出：

- 指纹阶段 vs embedding 阶段对比结论。
- 风险清单。
- 后续优化建议入口。

完成标准：

- 里程碑 4 必须为 `[x]`。
- 文档中必须存在：
  - 空闲基线
  - Audio Identity 测试结果
  - Local Feature 测试结果
  - 差异对比结论
- 每个阶段都能回溯到具体命令和日志。
- 验收记录中必须给出“是否达到首版可接受资源行为”的结论。

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

## 5. 测试验收基线

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
  - 功耗 / 热量 / 降频现象（首版以观察记录为主，不强制仪器量化）

### 5.2 测试命令口径

基础前置：

```bash
adb shell pidof -s com.orion.blaster.demo
adb logcat -c
adb shell am start -n com.orion.blaster.demo/.MainActivity
```

空闲基线：

```bash
adb shell dumpsys cpuinfo | rg com.orion.blaster.demo
adb shell top -H -p "$PID" -n 1
adb shell dumpsys meminfo com.orion.blaster.demo
adb shell cat /proc/$PID/status | rg 'VmRSS|VmSize|Threads'
adb shell cat /proc/$PID/io
```

指纹阶段日志：

```bash
adb logcat -v time BlasterAudioIdentity:I '*:S'
```

embedding 阶段日志：

```bash
adb logcat -v time BlasterLocalFeature:I '*:S'
```

### 5.3 首版完成判定日志

Audio Identity：

- `extracted ... digest=sha256:... payloadSize=...`
- compare 关闭时可见 `compare_skipped ...`

Local Feature：

- `drain_round_start`
- `drain_round_result`
- `extracted ... embeddingDim=1024 embeddingHead=[...]`
- `drain_completed` 或 `drain_timeout`

### 5.4 验收记录模板字段

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

- `数据产物` 中必须写实际测到的表格、日志摘要或截图文件名。
- 里程碑 2 / 3 / 4 / 5 必须有真实数据，不允许只写“已执行”。

## 6. 与后续优化工作的交接

后续优化阶段可基于本文档结果继续推进，但不在本文中直接执行：

- 若 CPU 峰值异常，优先分析解码线程、推理线程与批次大小。
- 若 `Native Heap` 持续不回落，优先排查 JNI、TFLite runtime、MediaCodec 资源释放。
- 若 I/O 增量异常，优先排查重复解码、重复读文件与重复落库。
- 若后几轮耗时显著高于前几轮，优先排查热衰减、降频与后台调度影响。
- 若首版资源行为不可接受，应新开优化计划文档，不直接改写本文验收结果。
