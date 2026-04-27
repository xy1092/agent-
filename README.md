# AgentOne

**Android 端私有 AI Agent Workspace**

AgentOne 是一个运行在 Android 设备上的本地优先、可控、可扩展的 AI Agent 应用。用户通过自己的 API Key 接入多种大模型，在聊天中驱动 Agent 调用工具完成任务。

## 已实现功能 (v0.1.0)

- **多 Provider / 多模型接入**：支持 OpenAI、Anthropic (Claude)、Gemini、OpenRouter、自定义 OpenAI-compatible 端点，以及内置 FakeProvider 用于开发测试
- **聊天与会话管理**：新建/删除/置顶会话，流式输出，停止/重新生成，消息持久化
- **Agent Runtime**：多轮工具调用循环，最大步数保护，中断与取消，运行日志
- **工具系统**：
  - 文件工具：读/写/列表/搜索/创建笔记
  - 浏览器工具：打开 URL、提取页面文本
  - 日历工具：列出/创建/更新日历事件
  - 提醒工具：创建/列出/完成提醒
  - 记忆工具：保存/搜索/删除长期记忆
- **工具审批**：低/中/高风险分级，可配置自动审批策略
- **数据持久化**：Room 数据库 + DataStore
- **安全存储**：API Key 使用 Android Keystore + EncryptedSharedPreferences 加密存储
- **浅色/深色主题**：跟随系统，支持 Material 3 Dynamic Color
- **Agent 日志抽屉**：实时查看工具执行步骤

## 未实现功能（首版边界）

- 本地大模型推理
- Root / Accessibility 跨应用自动点击
- 系统级静默控制
- Google Tasks / Google Drive 深度集成
- 向量数据库与 embedding 检索
- 复杂网页自动化 DOM 操作

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 导航 | Navigation Compose |
| 架构 | ViewModel + Coroutines/Flow |
| 数据库 | Room |
| 偏好存储 | DataStore |
| 网络 | OkHttp |
| 序列化 | Kotlinx Serialization |
| 安全 | Android Keystore + EncryptedSharedPreferences |
| 后台任务 | WorkManager |
| 浏览器 | WebView |
| 日期时间 | java.time |

**最低 SDK**：29 (Android 10)
**Target SDK**：35 (Android 15)

## 运行方式

### 前提条件

- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 17
- Android SDK 35

### 构建步骤

```bash
# 克隆项目
git clone <repo-url>
cd AgentOne

# 构建 Debug APK
./gradlew assembleDebug

# 运行测试
./gradlew test

# 安装到设备
./gradlew installDebug
```

或在 Android Studio 中直接打开项目目录，Sync Gradle 后 Run。

### 运行配置（可选 Gradle wrapper）

项目包含 Gradle wrapper 配置 (gradle 8.9)，可以直接使用：
```bash
# 如果没有 gradlew 脚本，生成 wrapper
gradle wrapper

# 然后使用
./gradlew assembleDebug
```

## API Key 配置方式

1. 首次启动进入引导页，选择 Provider 并输入 API Key
2. 或在 **Settings** 页面为各 Provider 分别配置 API Key
3. API Key 使用 Android Keystore 加密存储，不会明文落盘
4. **FakeProvider** 不需要 API Key，可用于开发测试

### 支持的 Provider 及默认模型

| Provider | 默认模型 | API Key 获取 |
|----------|---------|-------------|
| OpenAI | gpt-4o, gpt-4o-mini, gpt-4-turbo | https://platform.openai.com |
| Anthropic | claude-sonnet-4-6, claude-opus-4-7, claude-haiku-4-5 | https://console.anthropic.com |
| Gemini | gemini-2.0-flash, gemini-2.0-pro, gemini-2.5-pro | https://aistudio.google.com |
| OpenRouter | openai/gpt-4o, anthropic/claude-sonnet-4-6 | https://openrouter.ai |
| OpenAI Compatible | 自定义 | 自建端点 |
| Fake | fake-v1 | 无需 Key |

## 权限说明

| 权限 | 用途 | 必需 |
|------|------|------|
| INTERNET | API 调用、网页浏览 | 是 |
| READ_CALENDAR | 读取日历事件 | 可选 |
| WRITE_CALENDAR | 创建/修改日历事件 | 可选 |
| POST_NOTIFICATIONS | 提醒通知 (Android 13+) | 可选 |
| FOREGROUND_SERVICE | 长任务前台执行 | 是 |

## 首版限制

- 最大 8 步工具调用循环，超限自动终止并输出摘要
- 不执行 HIGH 风险工具
- 文件操作仅限文本文件
- 网页内容提取限制 20,000 字符
- 大文件读取限制 50,000 字符
- 记忆搜索为关键词匹配，非语义搜索

## 后续 Roadmap

### v0.2
- 更完善的 Provider 适配（thinking mode、prompt caching）
- 文件 diff / patch
- 会话导出
- 浏览器选中文本上下文

### v0.3
- Shell 沙箱
- Skills 系统
- 分享到应用 / 从文件打开

### v0.4
- Health Connect
- 更智能记忆检索
- 实验性 Accessibility 自动化

## 项目结构

```
app/
  src/main/java/dev/agentone/
    AgentOneApp.kt          # Application
    MainActivity.kt          # 入口 Activity + 导航
    core/
      model/                 # 数据模型 (Room Entities)
      database/              # Room DAOs + Database
      providers/             # Provider 抽象与实现
      agent/                 # AgentRuntime + AgentEvent
      tools/                 # Tool 接口 + 5 个工具实现
      prompt/                # PromptBuilder
      security/              # SecurityManager
    ui/
      theme/                 # Material 3 主题
      pages/                 # 8 个功能页面
    navigation/              # 导航图定义
  src/test/                  # 单元测试
```

## License

MIT
