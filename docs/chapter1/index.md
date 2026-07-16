# 第 1 章：Android 开源框架全景

欢迎来到 DroidStack 的第一章。

这门课程不是一份“热门框架 API 清单”，而是希望帮助你建立一张 Android 应用开发的工程能力地图：一个完整 App 为什么需要图片加载、网络通信、数据解析、异步调度、依赖注入、本地存储、后台任务、分页、导航、测试和性能工具？这些框架分别解决什么问题？它们又如何组合成一套可维护的工程架构？

本章先不深入某个具体框架，而是从全局视角回答三个问题：

1. Android 项目为什么需要开源框架；
2. Android 开源生态经历了怎样的演进；
3. 一个现代 Android 项目通常由哪些框架能力组成。

因此，本章的重点不是记住每个框架的 API，也不是马上判断哪个框架“最好”，而是先建立一张地图。后续章节会沿着这张地图逐步深入每个问题域。

本章分为八个部分：

| 小节 | 主题 | 内容 |
| --- | --- | --- |
| [第 1.1 节：本章学习目标](#chapter1-goals) | 学习目标 | 明确本章要建立的全局认知和后续学习准备 |
| [第 1.2 - 1.3 节：为什么需要框架](#chapter1-why) | 问题与概念 | 解释 Android 项目的典型工程问题，并区分框架、库、组件、SDK 与 Jetpack |
| [第 1.4 节：Android 开源生态的演进](#chapter1-evolution) | 技术演进 | 从 Java、Kotlin、Jetpack 到 Compose，理解框架生态如何变化 |
| [第 1.5 - 1.6 节：问题域与框架地图](#chapter1-map) | 全景地图 | 按图片、网络、异步、依赖注入、存储、测试等问题域建立课程地图 |
| [第 1.7 - 1.8 节：Jetpack 与框架替代](#chapter1-jetpack) | 生态关系 | 理解第三方框架、官方组件和历史框架之间的关系 |
| [第 1.9 - 1.10 节：学习深度分层](#chapter1-depth) | 学习层次 | 区分会使用、懂原理、能选型，以及主讲、维护、了解三类深度 |
| [第 1.11 节：完整项目中的框架组合](#chapter1-composition) | 工程组合 | 看一个现代 Android 项目中各类框架如何协作 |
| [第 1.12 - 1.13 节：小结与思考题](#chapter1-summary) | 复盘练习 | 通过总结和问题检查是否真正建立了框架全景 |

推荐阅读方式：先顺序读完本章，建立框架全景；之后学习每个具体框架章节时，再回到 [第 1.5 节](#chapter1-map) 和 [第 1.6 节](#chapter1-map)，确认它属于哪个问题域、处在架构中的哪个位置。

<a id="chapter1-goals"></a>

## 1.1 本章学习目标

学习完本章后，你应该能够：

- 区分框架、库、组件、SDK、Jetpack 组件等概念；
- 说清楚 Android 开源框架解决的主要问题域；
- 理解 Java 时代、Kotlin 时代、Jetpack 时代、Compose 时代的技术演进关系；
- 知道哪些框架属于“现代项目推荐栈”，哪些属于“存量项目维护栈”，哪些属于“历史演进了解栈”；
- 初步建立一套现代 Android 应用的分层架构和框架地图；
- 为后续学习图片、网络、异步、依赖注入、数据存储、测试和性能框架做好准备。

<a id="chapter1-why"></a>

## 1.2 为什么 Android 项目需要开源框架

一个真实 Android 应用并不只是几个页面的拼接。随着业务复杂度提升，项目会持续遇到这些问题：

- 图片从哪里来，如何下载、解码、缓存、取消和复用；
- 网络请求如何复用连接、处理超时、统一鉴权和刷新 Token；
- 服务端返回的数据如何安全、稳定地转换为 Kotlin 对象；
- 多个异步任务如何调度，如何避免回调地狱和线程泄漏；
- 页面状态如何流动，如何避免 UI 与业务逻辑互相缠绕；
- Repository、UseCase、ViewModel、数据库、网络服务之间如何创建和注入；
- 数据如何落地到本地数据库，如何迁移版本；
- 后台同步、上传、下载、重试任务如何可靠执行；
- 长列表如何分页、缓存和离线优先；
- 应用如何测试、调试、发现内存泄漏和性能瓶颈。

如果每个项目都从零开始解决这些问题，会带来大量重复劳动，也容易产生隐藏缺陷。开源框架的价值就在于：它们把某一类通用问题抽象成稳定的 API、可复用的实现和可讨论的工程规范。

例如：

- Coil 和 Glide 解决图片加载链路中的下载、解码、缓存、生命周期和复用问题；
- OkHttp 解决 HTTP 连接、拦截器、缓存、超时、重试和连接池问题；
- Retrofit 解决接口声明、动态代理、请求构建和响应转换问题；
- Coroutines 和 Flow 解决异步任务、数据流和结构化并发问题；
- Hilt 解决 Android 组件中的依赖创建、作用域和注入问题；
- Room 和 DataStore 解决本地数据持久化问题；
- WorkManager 解决可延迟、可约束、需要可靠执行的后台任务问题；
- Paging 3 解决大数据列表的分页加载、本地缓存和网络同步问题；
- LeakCanary、Timber、Macrobenchmark 等工具帮助项目发现泄漏、日志和性能问题。

也就是说，学习开源框架的目的不是背 API，而是理解一类工程问题的成熟解法。

## 1.3 框架、库、组件与 SDK

在 Android 项目中，我们经常混用“框架”“库”“组件”“SDK”这些词。它们没有绝对边界，但可以这样理解。

| 名称 | 关注点 | 典型例子 | 特点 |
| --- | --- | --- | --- |
| 库 Library | 提供一组可调用能力 | OkHttp、Moshi、Kotlinx Serialization | 你主动调用它，它解决相对明确的问题 |
| 框架 Framework | 提供一套组织代码的方式和运行规则 | Retrofit、Hilt、Room、WorkManager、Paging | 它往往定义生命周期、扩展点或约定，你按它的规则接入 |
| 组件 Component | 可组合的功能单元 | ViewModel、Navigation、DataStore | 更强调在应用架构中的职责边界 |
| SDK | 面向某个平台或服务的完整接入包 | 地图 SDK、支付 SDK、推送 SDK | 通常包含 API、资源、文档、配置和平台服务 |
| Jetpack | Android 官方提供的一组库和架构组件 | ViewModel、Room、WorkManager、Navigation、Paging | 强调生命周期、安全性、兼容性和推荐实践 |

有些技术既可以被称为库，也可以被称为框架。例如 Retrofit 从使用方式看是网络库，但它通过注解、动态代理、CallAdapter、Converter 建立了一套接口请求模型，所以在课程中会把它作为“接口代理与网络处理框架”来学习。

不要过度纠结名称，更重要的是理解它在工程中的职责。

<a id="chapter1-evolution"></a>

## 1.4 Android 开源生态的演进

Android 开源框架的发展大致可以分为四个阶段。

### 1.4.1 Java 与传统 View 时代

早期 Android 项目主要使用 Java、XML 布局、Activity、Fragment、ListView/RecyclerView 和大量手写工具类。常见框架包括：

- 图片加载：Universal Image Loader、Picasso、Glide、Fresco；
- 网络请求：HttpURLConnection、Volley、OkHttp、Retrofit；
- 数据解析：Gson、Jackson；
- 异步编程：Thread、Handler、AsyncTask、Executor、RxJava；
- 依赖注入：Dagger 2、RoboGuice；
- 事件通信：EventBus、Otto；
- View 绑定：ButterKnife；
- 数据库：SQLite、GreenDAO、Realm。

这个阶段的特点是第三方框架非常活跃，解决了大量 Android 原生 API 使用成本高、样板代码多、生命周期复杂的问题。

但随着项目规模变大，这一阶段也暴露出一些问题：回调层级越来越深，生命周期管理容易出错，工具类和单例类容易失控，框架之间缺少统一的架构约束。这些问题推动 Android 开发进入更重视语言能力和响应式思想的阶段。

### 1.4.2 Kotlin 与响应式时代

Kotlin 成为 Android 官方支持语言后，项目逐渐开始使用更现代的语言能力：

- 空安全减少 `NullPointerException`；
- 扩展函数减少工具类堆积；
- 数据类简化模型定义；
- 高阶函数让回调和 DSL 更自然；
- 协程让异步代码接近顺序代码；
- Flow 提供异步数据流能力。

这一阶段，RxJava 仍然在大量存量项目中存在，但新项目逐渐更多采用 Coroutines + Flow。框架设计也开始更重视 Kotlin 友好性，例如 Coil、Kotlinx Serialization、Koin、Ktor Client 等。

Kotlin 带来的变化不只是语法更简洁，它改变了框架 API 的设计方式。越来越多框架开始提供挂起函数、DSL、扩展函数、不可变数据模型和类型安全配置，Android 工程也逐渐从“回调驱动”转向“协程与数据流驱动”。

### 1.4.3 Jetpack 与官方架构时代

Jetpack 的出现让 Android 官方开始系统性提供应用架构组件：

- ViewModel 负责持有和管理界面状态；
- Lifecycle 让组件具备生命周期感知能力；
- Room 提供 SQLite 之上的类型安全访问层；
- WorkManager 提供可靠后台任务调度；
- Navigation 统一页面导航与返回栈；
- Paging 处理分页加载和本地缓存；
- DataStore 替代部分 SharedPreferences 场景；
- Hilt 基于 Dagger 提供 Android 依赖注入标准方案。

这个阶段的关键词是“官方推荐架构”。第三方框架并没有消失，但项目选型会更多考虑是否与 Jetpack、生命周期、协程和测试体系配合良好。

Jetpack 的意义在于，它把很多过去依赖第三方或团队自研的基础能力标准化了。开发者不再只是在“找一个能用的库”，而是在官方架构建议下组合 UI、状态、数据、任务调度和测试能力。

### 1.4.4 Compose 与声明式 UI 时代

Jetpack Compose 改变了 Android UI 的组织方式。传统 View 系统中，开发者通常操作控件对象；Compose 中，UI 是状态的函数，界面由状态驱动重组。

这带来了一些新的框架组合方式：

- UI 层：Compose、Navigation Compose；
- 状态管理：ViewModel、StateFlow、Compose State；
- 图片加载：Coil Compose；
- 异步与数据流：Coroutines、Flow；
- 依赖注入：Hilt；
- 数据层：Retrofit、OkHttp、Room、DataStore、Paging。

在 Compose 项目中，框架不再只是“工具”，而是共同服务于单向数据流和状态驱动 UI。

这也是现代 Android 框架学习的核心变化：我们不再孤立地学习某个库，而是理解它如何参与状态流动、生命周期、依赖管理和分层架构。

<a id="chapter1-map"></a>

## 1.5 Android 项目的典型问题域

本课程会按“问题域”组织，而不是按“框架名称”简单罗列。

| 问题域 | 主要框架 | 需要理解的核心问题 |
| --- | --- | --- |
| 图片加载 | Coil、Glide、Picasso、Fresco | 下载、解码、压缩、缓存、生命周期、列表复用 |
| 网络通信 | OkHttp、Retrofit、Ktor Client | 连接池、拦截器、超时、重试、接口代理、错误处理 |
| 数据解析 | Kotlinx Serialization、Moshi、Gson | 反射、代码生成、泛型、多态、接口兼容 |
| 异步编程 | Coroutines、Flow、RxJava、Handler | 线程切换、结构化并发、数据流、取消、异常处理 |
| 依赖注入 | Hilt、Dagger、Koin、手动 DI | 依赖图、作用域、生命周期、代码生成、测试替换 |
| 本地存储 | Room、DataStore、SQLite、SharedPreferences | 事务、迁移、类型安全、Flow 查询、一致性 |
| 后台任务 | WorkManager、JobScheduler、Service | 约束条件、可靠执行、重试、任务链、后台限制 |
| 分页同步 | Paging 3、RemoteMediator | 分页策略、加载状态、本地缓存、离线优先 |
| 架构状态 | ViewModel、StateFlow、Compose State | UI State、UI Event、单一可信数据源、单向数据流 |
| 页面导航 | Navigation、Navigation Compose | 返回栈、参数传递、Deep Link、多模块导航 |
| 测试质量 | JUnit、MockK、Robolectric、Espresso、Turbine | 单元测试、集成测试、UI 测试、协程与 Flow 测试 |
| 调试性能 | Timber、LeakCanary、StrictMode、Macrobenchmark | 日志、泄漏、卡顿、启动、包体积、基准测试 |

按照问题域学习的好处是：即使未来某个具体框架被替代，你仍然能迁移已有知识。例如 Glide、Picasso、Coil 都属于图片加载问题域，它们实现细节不同，但都绕不开请求模型、缓存、解码、生命周期和列表复用。

## 1.6 现代 Android 应用的框架地图

一个现代 Android 应用可以粗略分为 UI 层、领域层、数据层和基础设施层。

```text
UI 层
├── Compose / XML View
├── ViewModel
├── UiState / UiEvent
├── Navigation / Navigation Compose
└── StateFlow / Compose State

领域层
├── UseCase
├── DomainModel
├── Repository Interface
└── Coroutines / Flow

数据层
├── Repository Implementation
├── RemoteDataSource
│   ├── Retrofit
│   ├── OkHttp
│   └── Kotlinx Serialization / Moshi
└── LocalDataSource
    ├── Room
    └── DataStore

基础设施层
├── Hilt
├── WorkManager
├── Paging 3
├── Coil
├── Timber
├── LeakCanary
└── 测试与性能工具
```

这些层并不是为了制造复杂度，而是为了让职责更清晰：

- UI 层负责展示状态和发送用户意图；
- 领域层负责表达业务规则和用例；
- 数据层负责协调网络、本地数据库和缓存；
- 基础设施层为整个应用提供依赖注入、任务调度、图片加载、日志、测试和性能能力。

小项目可以简化层级，大项目则需要更清晰的边界。课程后续会不断回到这个地图，把每个框架放回它所属的位置。

<a id="chapter1-jetpack"></a>

## 1.7 第三方框架与 Jetpack 的关系

学习 Android 开源框架时，一个常见误区是把“第三方框架”和“官方 Jetpack”看成对立关系。

更准确的理解是：

- Jetpack 提供官方推荐的基础能力和架构方向；
- 第三方框架补充 Jetpack 没覆盖、覆盖不够深或生态更成熟的领域；
- 一个现代项目通常同时使用 Jetpack 和第三方框架。

例如：

| 场景 | 常见组合 |
| --- | --- |
| 页面状态管理 | ViewModel + StateFlow + Compose State |
| 网络数据获取 | Retrofit + OkHttp + Kotlinx Serialization |
| 本地缓存 | Room + DataStore |
| 分页列表 | Paging 3 + Room + RemoteMediator |
| 图片展示 | Coil + Compose |
| 依赖注入 | Hilt + ViewModel + Repository |
| 后台同步 | WorkManager + Hilt + Repository |
| 测试 | JUnit + MockK + Turbine + MockWebServer |

Jetpack 让应用架构更标准化，第三方框架让具体问题的实现更成熟。优秀的 Android 工程不是二选一，而是理解边界后进行组合。

## 1.8 为什么有些框架会被替代

Android 生态中有很多曾经流行、后来逐渐淡出的框架，例如 AsyncTask、ButterKnife、EventBus、Universal Image Loader、RoboGuice、Otto 等。

框架被替代通常有几类原因：

- Android 官方提供了更稳定的替代方案；
- Kotlin 或 Compose 改变了原来的开发方式；
- 框架没有很好适配生命周期、协程、模块化或测试；
- 维护活跃度下降，Issue 和版本更新变少；
- 新框架在性能、API 设计、类型安全或构建速度上更适合现代项目；
- 原框架解决的问题已经被语言能力或平台能力吸收。

例如：

- AsyncTask 被协程、线程池和 Jetpack 生命周期方案替代；
- ButterKnife 被 ViewBinding、DataBinding 和 Compose 替代；
- EventBus 在很多场景中被 ViewModel、SharedFlow、Repository 状态流等更显式的通信方式替代；
- SharedPreferences 的部分场景被 DataStore 替代；
- Gson 在 Kotlin 项目中常被 Moshi 或 Kotlinx Serialization 替代。

这并不意味着历史框架“不值得学”。恰恰相反，理解它们为什么流行、为什么被替代，可以帮助我们看清 Android 技术演进的方向。

<a id="chapter1-depth"></a>

## 1.9 框架学习的三个层次

学习一个框架，可以分成三个层次。

### 第一层：会使用

能够完成常见功能，例如：

- 用 Coil 加载网络图片；
- 用 Retrofit 定义接口并发起请求；
- 用 Room 保存本地数据；
- 用 Hilt 注入 Repository；
- 用 WorkManager 执行后台同步任务。

这一层解决“能不能做出来”的问题。

### 第二层：懂原理

能够理解框架背后的核心机制，例如：

- 图片框架如何做内存缓存和磁盘缓存；
- OkHttp 拦截器链如何工作；
- Retrofit 为什么可以把接口方法变成 HTTP 请求；
- 协程如何处理取消和异常；
- Hilt 如何通过编译期代码生成依赖图；
- Room 如何生成 DAO 实现；
- Paging 如何协调网络与数据库。

这一层解决“为什么这样做”的问题。

### 第三层：能选型

能够结合项目情况做判断，例如：

- 新项目该优先选 Coil 还是 Glide；
- 存量 RxJava 项目是否需要迁移到 Coroutines + Flow；
- 简单项目是否一定要引入完整 Clean Architecture；
- DataStore 是否适合替代所有 SharedPreferences；
- 后台任务该用 WorkManager、Service 还是 AlarmManager；
- 团队是否需要对 Retrofit、Room、Hilt 做统一封装。

这一层解决“在真实工程中怎么取舍”的问题。

本课程希望你最终走到第三层。

## 1.10 本课程的学习深度分层

Android 生态很大，本课程不会把所有框架都讲到同样深度。我们会按照“是否适合现代项目主线”“是否常见于存量项目”“是否主要用于理解历史演进”来安排学习深度。

| 学习深度 | 框架范围 | 学习目标 |
| --- | --- | --- |
| 深入掌握 | Coil 或 Glide、OkHttp、Retrofit、Kotlinx Serialization 或 Moshi、Coroutines、Flow、Hilt、Room、DataStore、WorkManager、Paging 3、ViewModel、Navigation、Android 测试体系 | 能在现代项目中独立使用，理解核心原理，并能做基本封装与选型 |
| 能够维护 | RxJava、Dagger 2、Gson、LiveData、Picasso、Volley、EventBus、SharedPreferences、SQLite | 能读懂存量项目代码，知道常见坑点，并能判断是否需要迁移 |
| 拓展了解 | Universal Image Loader、AsyncTask、xUtils、Afinal、RoboGuice、ButterKnife、Otto、IntentService、Loaders、插件化与热修复框架 | 理解 Android 技术演进和框架替代原因，不作为新项目默认选择 |

不是所有框架都值得用同样深度学习。课程会把重点放在现代项目真正高频、长期有效的能力上。

<a id="chapter1-composition"></a>

## 1.11 一个完整项目中的框架组合

假设我们要实现一个“Android 开源框架技术商城”综合项目，它可能包含：

- 用户登录；
- 首页商品列表；
- 网络分页；
- 图片加载；
- 商品搜索；
- 商品详情；
- 收藏；
- 离线缓存；
- 后台同步；
- 上传和下载；
- 多页面导航；
- 网络异常恢复；
- 主题配置；
- 日志和性能检测；
- 单元测试和 UI 测试。

对应的框架组合可以是：

下面这张组合图不是要求你在第一章就掌握所有技术，而是提前展示这些框架最终会如何汇合到一个完整工程中。

```text
UI
├── Jetpack Compose
├── Navigation Compose
└── ViewModel

异步与状态
├── Coroutines
├── Flow
├── StateFlow
└── SharedFlow

网络
├── OkHttp
├── Retrofit
└── Kotlinx Serialization

本地
├── Room
└── DataStore

分页与后台任务
├── Paging 3
├── RemoteMediator
└── WorkManager

图片
└── Coil

依赖注入
└── Hilt

测试
├── JUnit
├── MockK
├── Turbine
├── MockWebServer
└── Compose UI Test

调试与性能
├── Timber
├── LeakCanary
├── Chucker
└── Macrobenchmark
```

最终架构可以简化为：

```text
Compose UI
    ↓
ViewModel
    ↓
UseCase
    ↓
Repository
    ├── RemoteDataSource
    │      ├── Retrofit
    │      ├── OkHttp
    │      └── Kotlinx Serialization
    │
    └── LocalDataSource
           ├── Room
           └── DataStore

横向基础设施：
Hilt + Coroutines + Flow + WorkManager + Coil + Paging 3
```

这个综合项目会贯穿后续章节，帮助我们把单个框架知识串成完整工程能力。

<a id="chapter1-summary"></a>

## 1.12 本章小结

本章我们建立了 Android 开源框架的整体视角：

- 开源框架的核心价值，是为通用工程问题提供成熟解法；
- 学习框架不应停留在 API，而要理解问题域、核心原理、源码主线和选型逻辑；
- Android 开源生态经历了 Java、Kotlin、Jetpack、Compose 等阶段；
- 现代 Android 工程通常由 UI 层、领域层、数据层和基础设施层共同组成；
- Jetpack 与第三方框架不是对立关系，而是互补关系；
- 有些框架被替代，是语言、平台、架构和社区共同演进的结果；
- 本课程会围绕图片、网络、数据解析、异步、依赖注入、存储、后台任务、分页、架构、测试和性能展开。

下一章我们将讨论：如何系统学习和评价一个开源框架。你会学习从 README、Sample、模块结构、核心入口类、调用链、测试和 Issue 中快速抓住一个框架的主线。

## 1.13 思考题

1. 你现在维护或学习过的 Android 项目中，用到了哪些开源框架？它们分别解决什么问题？
2. 如果不用图片加载框架，手写一个网络图片列表会遇到哪些问题？
3. 为什么 Retrofit 适合和 OkHttp 搭配使用？它们的职责边界是什么？
4. RxJava、Coroutines、Flow 分别适合解决什么类型的异步问题？
5. 一个小型练习项目是否一定需要 Hilt、UseCase、Repository、Room、Paging 全套架构？为什么？
