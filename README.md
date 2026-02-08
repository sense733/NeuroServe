# NeuroServe

![Android](https://img.shields.io/badge/Platform-Android-green)
![NPU](https://img.shields.io/badge/Acceleration-Snapdragon%20NPU-blue)
![Status](https://img.shields.io/badge/Status-WIP-orange)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

**English** | [简体中文](README_zh-CN.md)

**Android On-Device LLM Server**

NeuroServe is a system-level AI inference service running on Android. It leverages the **Snapdragon 8 Elite (SM8750) Hexagon NPU** via Google's **LiteRT** (formerly TensorFlow Lite) to provide local Large Language Model (LLM) inference. It exposes an **OpenAI-compatible HTTP API**, allowing other applications (like Chatbox, Tasker, or Obsidian) to use on-device AI capabilities without relying on cloud services.

## Key Features

- [✔] **Qualcomm Hexagon NPU Acceleration**: Powered by LiteRT and QNN Delegate for high-performance, low-power inference.
- [✔] **OpenAI-compatible API**: Standard `/v1/chat/completions` endpoint with **SSE Streaming** support.
- [✔] **Background Service**: Runs as a foreground service with **WakeLock** to ensure uninterrupted inference even when the screen is off.
- [✔] **Internationalization**: Full English and Simplified Chinese support.
- [ ] **Multi-Model Support**: (Planned) Hot-swapping different quantized models.
- [ ] **Multi-Modal**: (Planned) Whisper ASR and Stable Diffusion image generation.

## Tech Stack

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Material3)
*   **Server Engine**: Ktor Server (Netty)
*   **Dependency Injection**: Hilt
*   **Inference Engine**: LiteRT (Google AI Edge) + QNN

## Getting Started

### Prerequisites

*   **Android Studio**: Ladybug Feature Drop or newer (recommended).
*   **Device**: Android device with Qualcomm Snapdragon SoC (8 Elite recommended for NPU acceleration).

### Compilation

1.  Clone this repository.
2.  Open in Android Studio.
3.  Sync Gradle project.
4.  Build and Run on your device.

### Importing Models

NeuroServe requires **.tflite** models (dynamic quantized or specific NPU quantized).

**Method 1: In-App Import (Recommended)**
1.  Launch NeuroServe.
2.  Go to the **Model Hub** tab.
3.  Click the **+ (Import)** button.
4.  Select a `.tflite` model file from your device storage.
5.  The model will be copied to the app's internal storage and ready for use.

**Method 2: Manual Placement**
*   Copy your `.tflite` models to the app's internal files directory: `/data/data/com.neuroserve/files/models/` (Root access required).

## API Usage

Once the server is running (default port 8000), you can make requests:

```bash
curl http://<DEVICE_IP>:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <API_KEY>" \
  -d '{
    "model": "qwen2.5-3b",
    "messages": [{"role": "user", "content": "Hello!"}],
    "stream": true
  }'
```

## Disclaimer

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
