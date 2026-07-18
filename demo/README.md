# DroidStack 章节 Demo

`demo/` 是课程的 Android 示例工程目录。文档负责解释问题、原理和选型；Demo 负责复现故障、观察调用结果和验证结论。

## 目录约定

```text
demo/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── chapter03-image-pipeline/
├── chapter04-network-pipeline/        # 后续章节新增
└── chapterXX-topic-name/
```

每章 Demo 使用 `chapterNN-topic-name` 命名。默认一个章节对应一个 Android Application 模块；如果一章需要多个独立应用，可以在章节目录下再拆分子模块。

每个章节目录至少包含：

- `README.md`：实验目标、运行入口、操作步骤和预期结果；
- `src/main`：可运行示例；
- 可重复的故障注入方式；
- 日志或状态观测入口；
- 与课程章节的双向链接；
- 验收清单。

## 已接入 Demo

| 章节 | 模块 | 内容 | 状态 |
| --- | --- | --- | --- |
| 第 3 章 | [`chapter03-image-pipeline`](./chapter03-image-pipeline/) | Coil、Glide、缓存版本、目标尺寸、加载来源、列表竞态 | `assembleDebug` 已通过 |

## 运行方式

用 Android Studio 打开仓库中的 `demo/` 目录，而不是仓库根目录。命令行构建：

```bash
cd demo
./gradlew :chapter03-image-pipeline:assembleDebug
```

当前 Demo 基线环境：

```text
JDK 17
Android SDK 35
Gradle 8.7
Android Gradle Plugin 8.6.1
minSdk 23
```

依赖版本统一维护在 [`gradle/libs.versions.toml`](./gradle/libs.versions.toml)。章节正文讲稳定原理，Demo 版本升级应单独提交，并记录行为变化。

## 新增章节 Demo

1. 在 `demo/` 下创建 `chapterNN-topic-name/`；
2. 在 `settings.gradle.kts` 中注册模块；
3. 在本文件“已接入 Demo”表中登记；
4. 在对应课程章节中添加 Demo 源码链接；
5. 提供至少一个正常路径、一个失败路径和一个可观察实验；
6. 完成模块构建后再把状态标记为“可运行源码”。

不要把 API 展示页当作完整 Demo。每个 Demo 都应该让读者改变条件、预测结果、观察证据并解释原因。
