# NeuroServe

![Android](https://img.shields.io/badge/Platform-Android-green)
![NPU](https://img.shields.io/badge/Acceleration-Snapdragon%20NPU-blue)
![Status](https://img.shields.io/badge/Status-WIP-orange)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

[English](README.md) | **简体中文**

**Android 端侧 LLM 推理服务**

NeuroServe 是一个运行在 Android 平台上的系统级 AI 推理服务。它利用 Google **LiteRT** 驱动 **Snapdragon 8 Elite (SM8750) Hexagon NPU**，提供本地大语言模型 (LLM) 推理能力。它暴露一个 **OpenAI 兼容的 HTTP API**，允许其他应用（如 Chatbox, Tasker, Obsidian）无需依赖云端服务即可使用端侧 AI 能力。

## 主要特性

- [✔] **Qualcomm Hexagon NPU 加速**: 基于 LiteRT 和 QNN Delegate，实现高性能、低功耗推理。
- [✔] **OpenAI 兼容 API**: 标准 `/v1/chat/completions` 接口，支持 **SSE 流式输出**。
- [✔] **后台服务**: 作为前台服务运行，配合 **WakeLock** 确保熄屏状态下推理不中断。
- [✔] **国际化**: 全面支持英文和简体中文。
- [ ] **多模型支持**: (计划中) 热切换不同量化模型。
- [ ] **多模态**: (计划中) Whisper 语音识别和 Stable Diffusion 图像生成。

## 技术栈

*   **语言**: Kotlin
*   **UI 框架**: Jetpack Compose (Material3)
*   **服务端**: Ktor Server (Netty)
*   **依赖注入**: Hilt
*   **推理引擎**: LiteRT (Google AI Edge) + QNN

## 快速开始

### 前置要求

*   **Android Studio**: Ladybug Feature Drop 或更新版本 (推荐)。
*   **设备**: 搭载 Qualcomm Snapdragon SoC 的 Android 设备 (推荐 8 Elite 以获得最佳 NPU 性能)。

### 编译指南

1.  克隆本仓库。
2.  使用 Android Studio 打开项目。
3.  同步 Gradle 项目 (Sync Gradle)。
4.  连接设备并运行 (Build and Run)。

### 导入模型

NeuroServe 需要 **.tflite** 格式的模型文件 (动态量化或 NPU 专用量化)。

**方法 1: App 内导入 (推荐)**
1.  启动 NeuroServe。
2.  切换到 **模型库 (Model Hub)** 标签页。
3.  点击 **+ (导入)** 按钮。
4.  从设备存储中选择 `.tflite` 模型文件。
5.  模型将被复制到应用私有目录并准备就绪。

**方法 2: 手动放置**
*   将 `.tflite` 模型文件复制到应用的内部文件目录: `/data/data/com.neuroserve/files/models/` (需要 Root 权限)。

## API 使用

服务启动后 (默认端口 8000)，您可以发起请求:

```bash
curl http://<DEVICE_IP>:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <API_KEY>" \
  -d '{
    "model": "qwen2.5-3b",
    "messages": [{"role": "user", "content": "你好!"}],
    "stream": true
  }'
```

## 免责声明

## 开源协议

本项目采用 Apache License 2.0 协议。详情请参阅 [LICENSE](LICENSE) 文件。
