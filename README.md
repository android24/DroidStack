<h1 align="center">DroidStack：Android 开源框架全景</h1>

> 系统讲解 Android 主流开源框架的核心原理、技术演进、选型方法与综合应用实践。

## 项目简介

DroidStack 是一门面向现代 Android 应用开发的开源课程，围绕图片加载、网络请求、数据解析、异步与响应式编程、依赖注入、本地存储、后台任务、分页、架构设计、测试与性能优化等关键问题域展开。

本课程不只介绍框架 API 的使用方式，而是采用「问题场景 -> 原生实现 -> 框架解决方案 -> 核心源码 -> 同类对比 -> 工程选型 -> 综合架构」的学习主线，帮助学习者建立 Android 应用层开源框架的完整知识地图。

## 课程目标

完成本课程后，学习者将能够：

- 建立 Android 主流开源框架的整体认知；
- 理解缓存、线程调度、动态代理、注解处理、依赖图、结构化并发、单向数据流等核心机制；
- 掌握 Coil、Glide、OkHttp、Retrofit、Coroutines、Flow、Hilt、Room、DataStore、WorkManager、Paging 3 等框架的典型使用方式；
- 能够阅读开源框架的核心源码调用链；
- 根据项目规模、团队能力和业务场景进行技术选型；
- 将多个框架组合成一套可维护、可测试、可扩展的现代 Android 工程架构。

## 项目受众

本课程适合以下学习者：

- 已掌握 Android 基础开发，希望系统梳理主流开源框架的学习者；
- 正在从 Java/传统 View 项目迁移到 Kotlin、Jetpack、Compose 技术栈的开发者；
- 需要维护包含 RxJava、Dagger、Gson、LiveData 等历史框架的 Android 工程开发者；
- 希望理解框架原理、源码设计和工程选型方法的中高级 Android 开发者；
- 正在设计 Android 课程、训练营或企业内部技术培训的讲师与团队负责人。

建议学习者具备 Kotlin、Android 四大组件、Activity/Fragment 生命周期、RecyclerView、基础网络请求和 Gradle 使用经验。

## 在线阅读

- GitHub 仓库：<https://github.com/android24/DroidStack>
- 在线文档：<https://android24.github.io/DroidStack/>（如部署路径调整，请以仓库 Pages 配置为准）

## 本地运行

本项目使用 VitePress 构建课程文档。

```bash
npm install
npm run docs:dev
```

构建静态文档：

```bash
npm run docs:build
```

本地预览构建结果：

```bash
npm run docs:preview
```

## 课程目录

课程规划为 5 个阶段、18 个章节，建议按阶段循序学习。

| 阶段 | 章节 | 主题 | 主要内容 | 状态 |
| --- | --- | --- | --- | --- |
| 第一阶段：认识 Android 开源框架 | [第 1 章](./docs/chapter1/) | Android 开源框架全景 | 框架、库、组件、SDK 的区别；Android 开源生态演进；现代项目框架地图 | ✅ |
| 第一阶段：认识 Android 开源框架 | [第 2 章](./docs/chapter2/) | 如何学习和评价一个开源框架 | README、Sample、模块结构、核心入口、调用链、Issue、社区活跃度与选型维度 | ✅ |
| 第二阶段：数据获取与处理框架 | [第 3 章](./docs/chapter3/) | 图片加载框架 | 商品列表场景；Coil 与 Glide 主流程、设计模式、缓存解码、列表复用与可玩实验 | ✅ |
| 第二阶段：数据获取与处理框架 | 第 4 章 | 网络请求底层框架 | OkHttp、HttpURLConnection、Volley、Ktor Client；连接池、拦截器、缓存、TLS、WebSocket | 🚧 |
| 第二阶段：数据获取与处理框架 | 第 5 章 | 接口代理与网络处理框架 | Retrofit、动态代理、ServiceMethod、CallAdapter、Converter、统一错误处理 | 🚧 |
| 第二阶段：数据获取与处理框架 | 第 6 章 | JSON 序列化与数据转换 | Kotlinx Serialization、Moshi、Gson、Jackson；反射、代码生成、泛型、多态与兼容性 | 🚧 |
| 第三阶段：线程、数据流与依赖管理 | 第 7 章 | Android 异步编程发展史 | Thread、Handler、AsyncTask、Executor、RxJava、Coroutines 的演进关系 | 🚧 |
| 第三阶段：线程、数据流与依赖管理 | 第 8 章 | RxJava 与响应式编程 | Observable、Single、Flowable、操作符、线程切换、背压、生命周期与存量项目维护 | 🚧 |
| 第三阶段：线程、数据流与依赖管理 | 第 9 章 | Kotlin Coroutines 与 Flow | suspend、Job、Dispatcher、结构化并发、Flow、StateFlow、SharedFlow、生命周期安全收集 | 🚧 |
| 第三阶段：线程、数据流与依赖管理 | 第 10 章 | 依赖注入框架 | 手动 DI、Dagger、Hilt、Koin；依赖图、作用域、组件生命周期、代码生成与测试替换 | 🚧 |
| 第四阶段：本地数据、后台任务与架构 | 第 11 章 | 本地数据存储框架 | SharedPreferences、DataStore、SQLite、Room、Realm；DAO、Migration、Transaction、Flow 查询 | 🚧 |
| 第四阶段：本地数据、后台任务与架构 | 第 12 章 | 后台任务与任务调度 | Service、JobScheduler、AlarmManager、WorkManager；约束条件、任务链、唯一任务、重试退避 | 🚧 |
| 第四阶段：本地数据、后台任务与架构 | 第 13 章 | 分页、缓存与离线优先架构 | Paging 3、RemoteMediator、Room、Retrofit、单一可信数据源、缓存同步与冲突处理 | 🚧 |
| 第四阶段：本地数据、后台任务与架构 | 第 14 章 | Android 架构框架与状态管理 | MVC、MVP、MVVM、MVI、UDF、ViewModel、UiState、Repository、UseCase、Clean Architecture | 🚧 |
| 第四阶段：本地数据、后台任务与架构 | 第 15 章 | 导航、组件通信与生命周期 | Navigation、FragmentManager、Deep Link、Back Stack、LiveData、StateFlow、SharedFlow、EventBus | 🚧 |
| 第五阶段：工程质量与综合实践 | 第 16 章 | Android 测试框架 | JUnit、MockK、Robolectric、Espresso、Compose UI Test、Turbine、MockWebServer、Hilt Test | 🚧 |
| 第五阶段：工程质量与综合实践 | 第 17 章 | 调试、日志与性能框架 | Timber、LeakCanary、StrictMode、Chucker、Android Profiler、Macrobenchmark、Baseline Profile | 🚧 |
| 第五阶段：工程质量与综合实践 | 第 18 章 | 综合项目与框架选型 | 用完整项目串联图片、网络、存储、分页、后台同步、导航、测试、调试与性能治理 | 🚧 |

## 学习路线

建议学习顺序如下：

```text
框架全景与评价方法
  -> 数据获取与处理：图片、网络、接口代理、序列化
  -> 并发与依赖管理：RxJava、Coroutines、Flow、Hilt
  -> 本地数据与应用架构：Room、DataStore、WorkManager、Paging、Navigation
  -> 工程质量：测试、调试、性能、综合项目
```

每个框架模块统一按照以下模板展开：

1. 问题背景：不用框架时会遇到什么问题；
2. 最小实现：用 Android 或 Kotlin 原生能力实现一个简化版本；
3. 核心原理：分析框架内部的线程、缓存、生命周期、代理、注解处理或代码生成机制；
4. 主流框架使用：完成一个真实业务功能；
5. 源码入口：阅读关键调用链，而不是无差别通读全部源码；
6. 同类对比：从功能、原理、性能、维护状态和适用场景比较框架；
7. 常见错误：总结真实项目中的坑点；
8. 企业实践：讨论封装、测试、迁移和治理方式；
9. 课后作业：实现简化框架或完成框架替换。

## 推荐综合项目

课程最终将通过一个完整 Android 应用串联全部框架能力。

```text
Compose UI
  -> ViewModel
  -> UseCase
  -> Repository
      -> RemoteDataSource
          -> Retrofit
          -> OkHttp
          -> Kotlinx Serialization
      -> LocalDataSource
          -> Room
          -> DataStore

横向基础设施：
Hilt + Coroutines + Flow + WorkManager + Coil + Paging 3
```

项目功能可包括登录、商品列表、网络分页、图片加载、搜索、详情、收藏、离线缓存、后台同步、上传下载、主题配置、网络异常恢复、日志和性能检测、单元测试与 UI 测试。

## 参与贡献

欢迎通过 Issue 或 Pull Request 参与课程共建。你可以贡献：

- 章节正文、示例代码和源码分析；
- 框架对比、迁移经验和真实项目踩坑记录；
- Android 新版本、Jetpack 新组件、Compose 生态变化的内容更新；
- 文档错别字、排版、链接和示例工程修复；
- 课后练习、面试题、实验任务和综合项目需求。

提交内容时建议保持课程统一主线：先讲问题，再讲原理，最后落到工程实践和选型判断。

## 许可证

<a rel="license" href="http://creativecommons.org/licenses/by-nc-sa/4.0/"><img alt="知识共享许可协议" style="border-width:0" src="https://img.shields.io/badge/license-CC%20BY--NC--SA%204.0-lightgrey" /></a>

本作品采用 <a rel="license" href="http://creativecommons.org/licenses/by-nc-sa/4.0/">知识共享署名-非商业性使用-相同方式共享 4.0 国际许可协议</a> 进行许可。
