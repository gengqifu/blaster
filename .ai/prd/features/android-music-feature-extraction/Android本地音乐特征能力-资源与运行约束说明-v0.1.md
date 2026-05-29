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

## 2. 结论

当前资源压力重点在 `local_feature`，不在基础信息处理，也不在 Search/Recommend 消费本身。

按成本粗分如下：

- 低成本：本地扫描、基础信息提取、metadata 检索消费
- 中等偏高：音频解码与指纹生成
- 高成本：本地 embedding 推理与多轮 drain

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

完成信号解释如下：

- `profile_window_elapsed_after_extract`：已拿到有效提取样本，窗口内主动停止采样
- `drain_timeout`：bounded run 正常结束

两者都不等于失败。

## 4. 优先级

当前优化顺序如下：

1. 先优化 `local_feature`
2. 补齐 idle baseline 和重复运行漂移观察
3. 稳定 `audio_identity` 的资源窗口和对照口径
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
