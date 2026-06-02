# NeuroServe 项目代码深度审查报告 (v2)

> 审查范围：`com.neuroserve` 核心架构与业务逻辑
> 审查时间：当前最新状态

## 总评

在经过上一轮的重构和修复后，NeuroServe 的代码质量得到了**巨大的提升**。之前发现的 17 个严重及重要问题（包括 P0 级别的 API 鉴权缺失、端口硬编码、单体 ViewModel 臃肿等）已全部得到有效解决。项目分层（`engine` / `server` / `ui`）非常清晰，协程异常处理和生命周期管理更加稳健。

以下是针对当前最新代码库发现的**新一轮潜在问题与改进建议**。

---

## 🟡 逻辑缺陷（P1 — 建议尽快修复）

### 1. ApiServer 配置未能热重载

| 文件 | 问题 |
|------|------|
| `ApiServer.kt` | `configureServer(settings)` 消费的是静态快照 |
| `ChatRoute.kt` | `settings.temperature` 及鉴权消费静态值 |

**详情：**
`ApiServer.start()` 中通过 `val settings = settingsRepository.settingsFlow.first()` 获取了一次配置快照，并将该对象传递给了路由层（`chatCompletionsRoute` 等）。这导致用户在界面修改 **API Key、鉴权开关、默认 Temperature/TopK** 等设置后，由于路由层持有的依然是旧的快照，新配置**完全不会生效**，直到用户重启服务器（或重启应用）。

**修复建议：**
不要将 `SettingsData` 作为静态参数传递给路由，而是将 `SettingsRepository`（或 `StateFlow`）传递给路由。在每次处理请求（如 `post("/v1/chat/completions")`）时，通过 `settingsFlow.value` 动态获取最新配置。

---

### 2. LiteRtEngine 忽略推理参数配置

| 文件 | 问题 |
|------|------|
| `LiteRtEngine.kt` | `generate()` 完全未使用 `InferenceConfig` |

**详情：**
在 `LiteRtEngine` 的 `generate(prompt: String, config: InferenceConfig?)` 方法中，参数 `config` 被完全忽略了。虽然 `NexaEngine` 将参数映射到了 `GenerationConfig`，但 `LiteRtEngine` 的调用链 `conv.sendMessageAsync(prompt)` 并没有体现对 `temperature`、`maxTokens` 的控制。如果 LiteRT 底层支持动态设置这些参数，应当补充传递；如果不支持，应当在代码注释或日志中明确标注“当前后端不支持动态采样参数”。

---

### 3. ZIP 模型导入时缺少进度反馈

| 文件 | 问题 |
|------|------|
| `ModelRepository.kt` | `extractZipModel()` 未更新 `_importProgress` |

**详情：**
目前导入单文件模型（GGUF 等）时，在流式拷贝过程中会计算 `totalRead / totalSize` 并更新 `_importProgress.value`。但在导入 `.zip` 模型包时（`extractZipModel()`），完全没有触发进度更新。这会导致用户在导入几 GB 的 ZIP 包时，UI 进度条处于无限加载状态（`-1f`），体验不佳。

**修复建议：**
在读取 `ZipInputStream` 拷贝文件块的循环内部，累加读取的字节数并除以总大小，或至少做规律的假步进（伪进度）以打破 UI 僵局。

---

## 🟢 性能与安全隐患（P2 — 质量提升）

### 4. LiteRtEngine.close() 线程安全隐患

| 文件 | 问题 |
|------|------|
| `LiteRtEngine.kt` | `close()` 未切换到 IO 线程 |

**详情：**
`NexaEngine.kt` 的 `close()` 方法聪明地使用了 `runBlocking(Dispatchers.IO)` 来执行 `performUnload()`，避免了 JNI 释放在主线程可能引起的卡顿。而 `LiteRtEngine` 的 `close()` 方法直接在调用方线程执行了 `conversation?.close()` 和 `engine?.close()`。如果触发该调用的来源不是 IO 线程，可能导致主线程阻塞（ANR）。

**修复建议：**
使用与 `NexaEngine` 相同的线程切换策略包裹 `close()` 逻辑。

---

### 5. ChatRoute 多端并发与状态机冲突

| 文件 | 问题 |
|------|------|
| `ChatRoute.kt` | `messages.last()` 的状态截断 |

**详情：**
路由为了适配底层模型的会话保持特性（Stateful），采取了 `val lastMessage = messages.last()` 策略，在发送给引擎前丢弃了请求带来的历史对话。这在单客户端环境下工作良好，但存在隐患：
1. 若有多个客户端同时接入 API，全局单例的 Engine 状态会发生上下文串位。
2. 用户若在客户端执行了“清空历史记录重新对话”，服务器端的 Engine 并不知道，依旧带着旧上下文。

**建议：**
鉴于端侧内存限制，强制保持 Stateful 模式是合理的折衷方案。但建议在收到新的会话周期（例如客户端传入新的 `system` prompt 或历史记录明显被清空重置）时，通过调用底层引擎的释放与重置方法清除上文（例如销毁旧的 Conversation 实例并新建）。

---

## 总结

相较于最初的代码基线，目前的 NeuroServe 已经达到了**生产可用 (Production-Ready)** 的成熟结构。早期的严重问题（如单体 God Object、网络锁死、鉴权黑洞等）已不复存在。后续维护只需集中于 **热重载失效修复 (P1-1)** 以及 **边缘特性的打磨 (P1-3, P2-5)**。
