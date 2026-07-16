# 第 3 章：图片加载框架

第一章建立了 Android 开源框架全景，第二章给出了一套学习和评价开源框架的方法。本章开始进入第一个具体问题域：图片加载。

先不要急着问“Coil 怎么用”“Glide 怎么用”。如果你是业务开发者，更容易先遇到这些现场：

```text
现场一：商品列表滑得很快，图片突然串到别的商品上
现场二：详情页加载一张大图，页面开始卡顿甚至 OOM
现场三：商品图已经换了，用户看到的还是旧图
现场四：Fragment 已经销毁，图片请求还在回调旧 View
现场五：网络弱一点，列表到处白屏，失败状态也不清楚
```

这些问题看起来都是“图片没显示好”，但背后其实是 Android 工程中最典型的一组问题：异步、缓存、解码、生命周期、UI 复用、状态回写和框架选型。

所以这一章会把图片加载框架当成一次完整的框架分析训练。你不是来背 API 的，而是来拆一条请求链：

```text
一张图片 URL
  -> 如何变成一次请求
  -> 如何命中缓存
  -> 如何下载原始数据
  -> 如何按目标尺寸解码
  -> 如何安全回到 UI
  -> 如何在页面销毁或列表复用时取消
  -> 如何通过设计模式把这些复杂度组织起来
```

学完本章，你应该能够回答：

- 图片加载框架解决了什么工程问题；
- Coil、Glide 这类框架的核心抽象是什么；
- 一次图片请求从 URL 到屏幕的主流程是什么；
- 这些流程背后使用了哪些经典设计模式；
- Coil、Glide、Picasso、Fresco 的优劣势和适用场景是什么；
- 如何通过实验验证缓存、解码、列表复用和生命周期；
- 如何在真实项目中做封装、选型和治理。

本章分为九个部分：

| 小节 | 主题 | 内容 |
| --- | --- | --- |
| [第 3.1 节：本章学习路线](#chapter3-method) | 任务地图 | 先把故障现场、框架能力和第二章方法论对齐 |
| [第 3.2 节：业务场景](#chapter3-problem) | 问题入口 | 用商品列表和详情页说明图片加载为什么不是一个简单 API |
| [第 3.3 - 3.5 节：从 API 到框架角色](#chapter3-usage) | 上手与识图 | 用 Coil 和 Glide 的最小代码识别 Request、Loader、Cache、Fetcher、Decoder、Target |
| [第 3.6 节：请求主流程](#chapter3-flow) | 主线拆解 | 分析一次图片请求从 URL 到屏幕的抽象流程、Coil 流程和 Glide 流程 |
| [第 3.7 节：四个核心原理](#chapter3-principle) | 原理拆解 | 解释缓存、解码、生命周期和列表复用为什么决定图片体验 |
| [第 3.8 节：设计模式地图](#chapter3-patterns) | 模式映射 | 把 Builder、Strategy、Factory、Adapter、Observer 等模式放回真实框架流程 |
| [第 3.9 - 3.10 节：扩展点与源码主线](#chapter3-source) | 深入框架 | 明确哪些点可以扩展，以及应该从哪里读 Coil、Glide 源码 |
| [第 3.11 - 3.12 节：同类比较与工程选型](#chapter3-selection) | 框架决策 | 比较 Coil、Glide、Picasso、Fresco 的优劣势和适用场景 |
| [第 3.13 - 3.19 节：封装、实验、练习与资料](#chapter3-practice) | 可玩实践 | 通过封装、缓存侦探、图片错位、大图内存账本等实验完成闭环 |

推荐阅读方式：第一次阅读时，不要跳过 [第 3.1 节](#chapter3-method) 和 [第 3.2 节](#chapter3-problem)，它们负责建立问题感；复习时可以直接跳到 [主流程](#chapter3-flow)、[设计模式](#chapter3-patterns)、[同类比较](#chapter3-selection) 和 [可玩实验](#chapter3-practice)，检查自己是否真的理解了框架。

<a id="chapter3-method"></a>

## 3.1 本章学习路线：从故障现场到框架能力

本章的主线不是“先介绍框架，再讲一点原理”，而是从真实故障倒推框架为什么这样设计。

| 你看到的现象 | 背后的工程问题 | 本章要学的框架能力 |
| --- | --- | --- |
| 列表图片错位 | 异步请求和 View 复用发生竞态 | Target 绑定、请求取消、生命周期管理 |
| 大图导致卡顿或 OOM | 原图解码过大，目标尺寸不明确 | 尺寸计算、采样解码、Bitmap 内存估算 |
| 图片更新后仍显示旧图 | 缓存 key 设计不完整 | 内存缓存、磁盘缓存、signature、cache key |
| 页面销毁后还在回调 | 请求没有跟随页面生命周期 | `Glide.with(fragment)`、Compose 作用域 |
| 弱网下白屏和闪烁 | 加载状态没有被明确表达 | placeholder、error、Loading / Success / Error |
| 项目里到处直接写框架 API | 缺少统一约定，迁移困难 | 轻量封装、日志监控、测试替换 |

这条路线会对应到第二章的方法论：

| 第二章方法 | 本章对应内容 |
| --- | --- |
| 它解决什么问题 | 图片下载、解码、缓存、生命周期、列表复用、请求取消、弱网错误 |
| 它提供什么核心抽象 | `ImageRequest`、`ImageLoader`、`RequestManager`、`RequestBuilder`、`Fetcher`、`Decoder`、`Target` |
| 它如何接入工程 | Coil Compose、Coil ImageLoader、Glide.with、RecyclerView、全局封装 |
| 它的边界在哪里 | 图片框架负责加载与显示，不负责业务缓存策略、数据同步和页面架构设计 |
| 最小 Demo | 商品列表封面图加载 |
| 调用链 / 主流程 | Coil 请求链路、Glide 请求链路、抽象图片加载流水线 |
| 扩展点 | Coil Components、Fetcher、Decoder；Glide ModelLoader、DataFetcher、Transformation、RequestListener |
| 常见错误 | 图片错位、缓存不更新、大图 OOM、生命周期不匹配、封装过度 |
| 源码入口 | 从 `AsyncImage` / `Glide.with()` 开始，而不是从工具类开始 |
| 同类比较 | Coil、Glide、Picasso、Fresco、Universal Image Loader |
| 工程选型 | 按功能匹配、工程匹配、团队匹配、迁移成本判断 |

你可以把本章看成一个小型闯关：

```text
先发现问题
  -> 写出最小代码
  -> 画出主流程
  -> 找到核心角色
  -> 解释关键原理
  -> 标注设计模式
  -> 比较同类框架
  -> 做实验验证
  -> 回到工程封装
```

本章的目标不是“学完就会背所有 API”，而是让你能用第二章的方法分析任何一个图片加载框架。

<a id="chapter3-problem"></a>

## 3.2 业务场景：一个商品列表会怎样暴露图片问题

本章只用一个贯穿案例：商品列表和商品详情页。它足够简单，所有人都能理解；也足够真实，几乎能暴露图片加载框架要解决的全部关键问题。

```kotlin
data class Product(
    val id: String,
    val name: String,
    val coverUrl: String,
    val detailImageUrl: String,
    val updatedAt: Long
)
```

列表页需要显示几十甚至上百张商品封面，详情页需要显示高清大图。用户会快速滑动、进入详情、返回列表、刷新数据，网络也可能很慢或失败。

如果不使用框架，最小实现看起来很简单：

```text
URL
  -> 开后台线程下载图片
  -> 解码 Bitmap
  -> 切回主线程
  -> 设置到 ImageView 或 Compose UI
```

但真实项目会立刻遇到这些问题：

| 问题 | 具体表现 | 框架要解决什么 |
| --- | --- | --- |
| 主线程阻塞 | 下载或解码放在主线程导致卡顿 | 线程调度 |
| 大图内存高 | 原图 4000 x 3000 直接解码导致内存暴涨 | 采样解码、尺寸控制 |
| 重复加载 | 列表滑动时反复下载同一张图 | 内存缓存、磁盘缓存 |
| 图片错位 | RecyclerView 复用后旧请求回写新 item | 请求绑定、取消、目标校验 |
| 页面销毁后回调 | Fragment 销毁后仍然更新旧 View | 生命周期感知 |
| 图片更新不及时 | 商品图更新后仍命中旧缓存 | 缓存 key、signature |
| 弱网体验差 | 白屏、闪烁、失败无反馈 | 占位图、错误图、重试 |
| 格式多样 | WebP、GIF、SVG、视频帧处理不同 | 解码器和组件扩展 |

读到这里，可以先记住一个判断：图片加载框架不是“把 URL 塞进 ImageView”的工具，而是一个围绕异步请求、缓存、解码、生命周期和 UI 状态构建的小型调度系统。

这也是图片加载框架的职责边界：

```text
图片框架负责：
图片请求、缓存、解码、变换、生命周期、显示目标和错误状态

图片框架不负责：
业务数据同步、商品更新策略、页面架构、Repository 设计、接口分页
```

<a id="chapter3-usage"></a>

## 3.3 最小使用：先用两段代码打开请求链

先看最小代码，因为读源码、画流程、识别设计模式都要从入口 API 开始。

### 3.3.1 Coil：Compose 中的最小使用

```kotlin
@Composable
fun ProductCover(
    product: Product,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = product.coverUrl,
        contentDescription = product.name,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
    )
}
```

`AsyncImage` 是 Coil 在 Compose 中最常用的入口。它不仅接收 URL，还会执行异步请求，并根据 composable 的约束和 `ContentScale` 尽量加载合适尺寸的图片。

这段最小代码背后隐藏的流程是：

```text
ProductCover
  -> AsyncImage
  -> ImageRequest
  -> ImageLoader
  -> Cache / Fetcher / Decoder
  -> Painter State
  -> Compose UI
```

### 3.3.2 Glide：View 中的最小使用

```kotlin
Glide.with(fragment)
    .load(product.coverUrl)
    .into(binding.coverImage)
```

`Glide.with(fragment)` 不只是拿一个 `Context`，更重要的是创建一个和 Fragment 生命周期绑定的请求管理入口。页面销毁时，Glide 可以自动清理相关请求和资源。

这段最小代码背后隐藏的流程是：

```text
ViewHolder.bind
  -> Glide.with(fragment)
  -> RequestManager
  -> RequestBuilder
  -> Engine
  -> Cache / ModelLoader / DataFetcher / Decoder
  -> Target(ImageView)
```

到这里，学习目标不是“会复制代码”，而是知道后续源码阅读应该从 `AsyncImage` 和 `Glide.with()` 开始。

## 3.4 核心抽象：从 API 背后识别框架角色

现在再回头看核心抽象，就不会像背概念。图片加载框架虽然实现各不相同，但通常都会围绕下面这些角色组织。

| 抽象角色 | 解决的问题 | Coil 中的对应 | Glide 中的对应 | 常见设计思想 |
| --- | --- | --- | --- | --- |
| Request | 描述一次图片请求 | `ImageRequest` | `RequestBuilder` / `SingleRequest` | Builder、Command |
| Loader / Engine | 统一调度加载流程 | `ImageLoader` | `Engine` | Facade、Mediator |
| Request Manager | 管理请求生命周期 | Compose 作用域 / `ImageLoader` | `RequestManager` | Lifecycle-aware |
| Cache | 复用加载结果 | `MemoryCache`、`DiskCache` | Active Resource、Memory Cache、Disk Cache | LRU、Strategy |
| Fetcher | 获取原始数据 | Fetcher / Network Fetcher | `DataFetcher` | Strategy、Factory |
| Model Adapter | 适配不同数据源 | Components | `ModelLoader` | Adapter、Factory |
| Decoder | 解码图片数据 | Decoder | Decoder | Strategy |
| Transformation | 裁剪、圆角、模糊 | Transformation | Transformation | Strategy、Decorator |
| Target / State | 接收加载结果 | Painter State | `Target`、`ImageViewTarget` | Observer、Callback |

这张表是阅读图片框架源码的地图。后面看到新类时，可以先问：它属于 Request、Engine、Cache、Fetcher、Decoder、Transformation，还是 Target？

## 3.5 工程接入：框架如何进入真实项目

### 3.5.1 Coil 的工程接入

真实项目中建议把依赖版本放到 Version Catalog。下面只展示依赖形态，具体版本以项目锁定版本和官方文档为准。

```kotlin
dependencies {
    implementation("io.coil-kt.coil3:coil-compose:<coil-version>")
    implementation("io.coil-kt.coil3:coil-network-okhttp:<coil-version>")
}
```

Coil 3.x 的网络加载能力按组件接入。如果要加载 `https://...` 图片，需要接入 OkHttp 或 Ktor 网络模块。Coil 官方文档也建议大多数 Compose 场景优先使用 `AsyncImage`，因为它能根据组件约束确定加载尺寸。

项目级配置可以通过全局 `ImageLoader` 完成：

```kotlin
class DroidStackApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            OkHttpClient.Builder()
                                .addInterceptor(ImageHeaderInterceptor())
                                .build()
                        }
                    )
                )
            }
            .build()
    }
}
```

这段配置说明 Coil 的工程接入不是“到处写 `AsyncImage`”，而是把缓存、网络和扩展组件统一收敛到应用级 `ImageLoader`。

### 3.5.2 Glide 的工程接入

Glide 的典型接入方式是在 Activity、Fragment、View 或 ViewHolder 中通过 `Glide.with(...)` 创建请求。

```kotlin
Glide.with(fragment)
    .load(product.coverUrl)
    .placeholder(R.drawable.ic_product_placeholder)
    .error(R.drawable.ic_product_error)
    .centerCrop()
    .signature(ObjectKey(product.updatedAt))
    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
    .into(binding.coverImage)
```

这些配置不是装饰性写法，而是分别解决工程问题：

| 配置 | 工程含义 |
| --- | --- |
| `placeholder` | 加载前保持布局稳定 |
| `error` | 加载失败后有明确反馈 |
| `centerCrop` | 按封面容器裁剪 |
| `signature(ObjectKey(...))` | 商品图更新后改变缓存签名 |
| `diskCacheStrategy` | 控制磁盘缓存策略 |
| `into(imageView)` | 绑定目标，并让 Glide 管理旧请求 |

Glide 官方文档也强调，在 RecyclerView 这类可复用 View 中，要么对同一个 View 发起新请求，要么显式 `clear()`，否则旧请求可能晚于新绑定返回并写回旧图片。

<a id="chapter3-flow"></a>

## 3.6 主流程：从 URL 到屏幕

本节是本章的核心之一。主流程不是源码细节，而是你阅读源码前必须画出来的路径。

### 3.6.1 抽象主流程

无论 Coil、Glide 还是其他图片框架，抽象流程都可以先画成这样：

```text
输入数据 URL / Uri / File / Resource
  -> 构建 Image Request
  -> 绑定生命周期和显示目标
  -> 查询内存缓存
  -> 查询磁盘缓存
  -> 获取原始数据
  -> 解码成图片对象
  -> 按目标尺寸采样
  -> 执行图片变换
  -> 更新 UI 或返回错误
  -> 请求完成、取消或清理
```

### 3.6.2 Coil 主流程

```text
AsyncImage
  -> rememberAsyncImagePainter
  -> ImageRequest
  -> ImageLoader
  -> Interceptor Chain
  -> MemoryCache
  -> DiskCache
  -> Fetcher
  -> Decoder
  -> Transformation
  -> Painter State
  -> Compose Recomposition
```

逐层理解：

| 阶段 | 职责 | 观察点 |
| --- | --- | --- |
| `AsyncImage` | Compose 层入口 | 是否传入稳定 model 和稳定尺寸 |
| `ImageRequest` | 请求描述 | URL、缓存 key、尺寸、监听器 |
| `ImageLoader` | 请求执行器 | 缓存、网络、组件是否统一配置 |
| Interceptor Chain | 请求流水线 | 日志、监控、改写请求从哪里插入 |
| Memory / Disk Cache | 缓存复用 | 返回列表是否命中缓存 |
| Fetcher | 数据获取 | 网络、文件、资源如何统一成数据源 |
| Decoder | 图片解码 | 是否按目标尺寸解码 |
| Transformation | 图片变换 | 圆角、裁剪是否影响缓存 key |
| Painter State | UI 状态 | Loading、Success、Error 如何反馈 UI |

### 3.6.3 Glide 主流程

```text
Glide.with(fragment)
  -> RequestManager
  -> RequestBuilder
  -> SingleRequest
  -> Engine
  -> Active Resources
  -> Memory Cache
  -> Resource Disk Cache
  -> Data Disk Cache
  -> ModelLoader
  -> DataFetcher
  -> Decoder
  -> Transformation
  -> Target(ImageView)
```

逐层理解：

| 阶段 | 职责 | 观察点 |
| --- | --- | --- |
| `Glide.with()` | 创建生命周期感知入口 | 传 Fragment、Activity、View 还是 Context |
| `RequestManager` | 管理请求集合 | 页面 start/stop/destroy 如何影响请求 |
| `RequestBuilder` | 收集请求配置 | placeholder、error、transform、signature |
| `SingleRequest` | 表示单次请求 | 状态如何从等待到成功或失败 |
| `Engine` | 调度缓存、加载、解码 | 为什么 Engine 是核心调度层 |
| Active / Memory / Disk Cache | 多级缓存 | 结果从哪一层命中 |
| `ModelLoader` | 适配数据模型 | URL、File、Uri 如何转成可读取数据 |
| `DataFetcher` | 读取原始数据 | 网络、文件、ContentProvider 如何读取 |
| `Decoder` | 解码资源 | 图片格式、尺寸如何处理 |
| `Target` | 接收结果 | 如何更新 ImageView 或自定义目标 |

主流程对比可以看出：Coil 更适合从 Compose 状态模型进入，Glide 更适合从 View 和生命周期管理进入。

<a id="chapter3-principle"></a>

## 3.7 原理：缓存、解码、生命周期、列表复用

### 3.7.1 缓存原理

图片缓存至少要回答三个问题：

1. 缓存什么；
2. 用什么 key；
3. 什么时候失效。

缓存来源通常是：

```text
内存缓存
  -> 磁盘缓存
  -> 网络 / 文件 / Uri 原始来源
```

缓存 key 不能只看 URL。同一个 URL 在列表页、详情页、头像场景下可能有不同尺寸和变换方式。

```kotlin
// Coil：把商品更新时间放进缓存 key
.memoryCacheKey("product_cover_${product.id}_${product.updatedAt}")
.diskCacheKey("product_cover_${product.id}_${product.updatedAt}")

// Glide：用 signature 表示资源版本
.signature(ObjectKey(product.updatedAt))
```

### 3.7.2 解码原理

一张 4000 x 3000 的 ARGB_8888 图片大约需要：

```text
4000 x 3000 x 4 bytes = 48,000,000 bytes
```

约 45.8 MB。列表封面如果只需要 72dp，却按原图解码，会浪费大量内存。

所以图片加载时要尽量让框架知道目标尺寸：

```kotlin
// Compose
Modifier.size(72.dp)

// Glide
.override(144, 144)
```

解码不是孤立动作，它受目标尺寸、缩放模式、设备密度、图片格式、Bitmap 配置共同影响。

### 3.7.3 生命周期原理

图片请求不能脱离页面生命周期。

```text
页面可见：可以启动或恢复请求
页面不可见：可以暂停低优先级请求
页面销毁：必须取消请求并释放目标
```

Glide 通过 `Glide.with(fragment)` 绑定生命周期；Coil 在 Compose 中会跟随 composable 的进入、离开和请求状态变化。

### 3.7.4 列表复用原理

图片错位的本质是异步请求和 UI 目标复用之间的竞态。

```text
时间 1：ImageView 绑定商品 A，发起请求 A
时间 2：ImageView 被复用给商品 B，发起请求 B
时间 3：请求 B 返回，显示 B
时间 4：请求 A 返回，如果没有取消或校验，就把 A 覆盖到 B 上
```

框架要做的是：

- 为目标对象记录当前请求；
- 新请求覆盖旧请求；
- item 回收时取消请求；
- 返回结果前确认目标仍然有效；
- 页面销毁时统一清理请求。

业务代码要做的是：

- RecyclerView 中每次绑定都发起新请求；
- 不需要图片的分支要 `clear(imageView)`；
- Compose 列表使用稳定 key 和稳定尺寸；
- 不手写失控的异步回调直接设置图片。

<a id="chapter3-patterns"></a>

## 3.8 设计模式：从 API 看框架如何组织复杂度

本节把你上面提到的“经典设计模式”显式放进框架主流程里。

| 设计模式 / 思想 | Coil 中的体现 | Glide 中的体现 | 解决的问题 |
| --- | --- | --- | --- |
| Facade | `AsyncImage`、`ImageLoader` | `Glide.with()`、`Engine` | 用简单入口隐藏复杂流程 |
| Builder | `ImageRequest.Builder`、`ImageLoader.Builder` | `RequestBuilder`、`RequestOptions` | 构建复杂请求配置 |
| Strategy | Fetcher、Decoder、Transformation、Cache | ModelLoader、DataFetcher、Decoder、Transformation、DiskCacheStrategy | 替换数据获取、解码、变换、缓存策略 |
| Factory | Component 注册、Fetcher Factory | ModelLoaderFactory、DataFetcher Factory | 根据数据类型创建处理器 |
| Chain of Responsibility | Interceptor Chain | 请求加载和解码的多阶段链路 | 拆分请求处理流程 |
| Adapter | 不同数据源适配成统一请求数据 | ModelLoader 将 URL、Uri、File 适配为 DataFetcher | 屏蔽数据源差异 |
| Observer / Callback | Painter State、Listener | Target、RequestListener | 将异步结果通知 UI 或业务 |
| Decorator | 多个 Transformation 组合 | RequestOptions、Transformation 组合 | 在不改主流程的情况下增强行为 |
| State Machine | Loading / Success / Error | SingleRequest 状态流转 | 管理异步请求状态 |
| Lifecycle-aware | Compose 作用域和请求生命周期 | RequestManager 绑定 Activity / Fragment / View | 跟随页面生死暂停、恢复、取消请求 |

这些模式不是为了考试背诵，而是用来解释框架为什么拆成这么多对象。

例如：

```text
Builder 让请求配置可读
Facade 让业务入口简单
Strategy 让缓存、解码、变换可替换
Factory 让框架根据数据类型创建处理器
Chain 让加载流程可以分阶段扩展
Observer / Target 让异步结果回到 UI
Lifecycle-aware 让请求跟随页面生死
```

## 3.9 扩展点：从使用框架到驾驭框架

第二章强调，扩展点能反映框架作者认为哪些部分应该开放。

| 扩展点 | Coil | Glide | 用途 |
| --- | --- | --- | --- |
| 网络获取 | OkHttp / Ktor network component | OkHttp、Volley、默认网络栈 | 统一 Header、鉴权、日志、监控 |
| 数据来源 | Fetcher | ModelLoader + DataFetcher | 支持自定义 Uri、加密文件、业务资源 |
| 解码 | Decoder | Decoder | 支持 SVG、GIF、视频帧、特殊格式 |
| 图片变换 | Transformation | Transformation | 圆角、头像、模糊、水印 |
| 缓存 | MemoryCache / DiskCache | DiskCacheStrategy、Memory Cache | 控制缓存大小、缓存版本、缓存策略 |
| 结果监听 | Listener / Painter State | RequestListener、Target | 统计耗时、失败率、埋点 |
| 测试替换 | 自定义 / fake ImageLoader | fake loader、测试封装 | 截图测试和 UI 测试稳定化 |

扩展点学习时要问：

- 它在哪个主流程节点被调用；
- 它运行在哪个线程；
- 它是否影响缓存 key；
- 它失败时错误如何传递；
- 它是否容易测试和替换。

<a id="chapter3-source"></a>

## 3.10 源码主线：怎么读，不从哪里读

源码阅读最容易迷路，所以第三章必须继续贯彻第二章的建议：从入口 API 开始，沿着主流程读。

### 3.10.1 Coil 源码阅读路线

入口：

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(product.coverUrl)
        .build(),
    contentDescription = product.name
)
```

阅读路线：

```text
AsyncImage
  -> rememberAsyncImagePainter
  -> AsyncImagePainter State
  -> ImageRequest
  -> ImageLoader
  -> Interceptor
  -> MemoryCache
  -> Fetcher
  -> Decoder
```

阅读问题：

- `AsyncImage` 如何把 Compose 约束交给请求；
- `ImageRequest` 如何保存请求参数；
- `ImageLoader` 如何组织缓存、网络、解码组件；
- Interceptor Chain 中每一层做什么；
- Fetcher / Decoder 为什么要抽象成可替换组件；
- Loading / Success / Error 如何驱动 UI。

### 3.10.2 Glide 源码阅读路线

入口：

```kotlin
Glide.with(fragment)
    .load(product.coverUrl)
    .centerCrop()
    .into(binding.coverImage)
```

阅读路线：

```text
Glide.with()
  -> RequestManagerRetriever
  -> RequestManager
  -> RequestBuilder
  -> SingleRequest
  -> Engine
  -> EngineJob / DecodeJob
  -> ModelLoader
  -> DataFetcher
  -> Decoder
  -> Target
```

阅读问题：

- `Glide.with(fragment)` 如何拿到生命周期；
- `RequestManager` 管理了什么；
- `RequestBuilder` 如何收集请求配置；
- `Engine` 为什么是核心调度层；
- 多级缓存在哪些阶段查找；
- `Target` 如何接收成功、失败和清理事件。

<a id="chapter3-selection"></a>

## 3.11 同类框架比较：优劣势和适用场景

第二章要求从功能匹配、工程匹配和团队匹配三个层面做选型。图片加载框架可以这样比较。

| 维度 | Coil | Glide | Picasso | Fresco |
| --- | --- | --- | --- | --- |
| 推荐深度 | 主讲 | 主讲 | 了解 / 维护 | 了解 / 特殊场景 |
| 主要入口 | `AsyncImage`、`ImageRequest`、`ImageLoader` | `Glide.with()`、`RequestBuilder`、`Target` | `Picasso.get().load()` | Drawee、ImagePipeline |
| 技术风格 | Kotlin-first、Compose 友好 | View 体系成熟、能力完整 | API 简洁 | 图片管线复杂 |
| Compose 支持 | 自然 | 可用但不是最自然 | 不适合作为新 Compose 首选 | 较弱 |
| RecyclerView 成熟度 | 可用 | 非常成熟 | 可用但能力较简单 | 可用但接入重 |
| 缓存体系 | 易理解，依赖 ImageLoader 配置 | 多级缓存成熟，细节较多 | 相对简单 | 管线化缓存复杂 |
| 扩展能力 | Components、Fetcher、Decoder | ModelLoader、DataFetcher、Transformation | 扩展能力有限 | 扩展能力强但学习成本高 |
| 学习成本 | 低到中 | 中 | 低 | 高 |
| 存量项目 | 新项目更常见 | 大量存量项目 | 早期项目维护 | 特定历史项目 |
| 适合场景 | Kotlin / Compose 新项目 | XML View、RecyclerView、复杂图片场景 | 简单历史项目 | 大图、特殊图片管线 |
| 主要风险 | 需要理解 Coil 3 网络组件和 ImageLoader 配置 | API 和缓存细节较多，迁移成本高 | 现代生态优势不足 | 接入和维护成本较高 |

结论不是“谁最好”，而是：

- 新建 Kotlin + Compose 项目：优先 Coil；
- XML View / RecyclerView 存量项目：优先 Glide；
- 已大量使用 Glide 的项目：先规范使用，不要无理由迁移；
- 只维护少量 Picasso 老代码：不一定立即迁移；
- 特殊大图或复杂图片管线：评估 Fresco 或框架自定义能力。

## 3.12 工程选型：按第二章矩阵做判断

### 3.12.1 功能匹配

| 功能问题 | 需要确认 |
| --- | --- |
| 是否支持网络、本地、Uri、资源图片 | 商品图、头像、本地相册是否都覆盖 |
| 是否支持占位图、错误图、fallback | 弱网体验是否完整 |
| 是否支持 GIF、SVG、视频帧 | 业务是否需要 |
| 是否支持请求取消和生命周期 | 页面销毁、列表复用是否安全 |
| 是否支持 Compose / RecyclerView | UI 技术栈是否匹配 |
| 是否支持缓存配置 | 头像、商品图、验证码是否能区分策略 |

### 3.12.2 工程匹配

| 工程问题 | 判断方式 |
| --- | --- |
| 是否容易统一封装 | 能否沉淀 `AppImage` / `loadProductCover()` |
| 是否容易接入日志和监控 | 能否统计加载耗时、失败率、来源 |
| 是否影响构建和包体积 | 依赖是否过重 |
| 是否适合多模块 | 图片封装层放在哪个模块 |
| 是否容易测试 | 能否替换 fake loader |
| 是否容易迁移 | 业务代码是否被框架 API 污染 |

### 3.12.3 团队匹配

| 团队问题 | 判断方式 |
| --- | --- |
| 团队是否熟悉 Compose | 熟悉则 Coil 成本更低 |
| 团队是否维护大量 RecyclerView | Glide 经验更有价值 |
| 团队是否能理解缓存和生命周期 | 不理解时先规范使用，再谈复杂扩展 |
| 是否有时间迁移老项目 | 没有就不要为了追新重写 |
| 是否需要课程演示现代技术栈 | Coil 更适合作为课程综合项目默认栈 |

<a id="chapter3-practice"></a>

## 3.13 工程封装：既统一，又不要包死

封装的目的不是把框架 API 全部包一层，而是统一项目约定。

### 3.13.1 Compose 项目的轻量封装

```kotlin
@Composable
fun AppImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cacheKey: String? = url,
    contentScale: ContentScale = ContentScale.Crop
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .apply {
                if (cacheKey != null) {
                    memoryCacheKey(cacheKey)
                    diskCacheKey(cacheKey)
                }
            }
            .crossfade(true)
            .build(),
        placeholder = painterResource(R.drawable.ic_image_placeholder),
        error = painterResource(R.drawable.ic_image_error),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}
```

业务代码：

```kotlin
AppImage(
    url = product.coverUrl,
    contentDescription = product.name,
    cacheKey = "product_cover_${product.id}_${product.updatedAt}",
    modifier = Modifier.size(72.dp)
)
```

### 3.13.2 View 项目的轻量封装

```kotlin
fun ImageView.loadProductCover(
    fragment: Fragment,
    product: Product
) {
    Glide.with(fragment)
        .load(product.coverUrl)
        .placeholder(R.drawable.ic_product_placeholder)
        .error(R.drawable.ic_product_error)
        .centerCrop()
        .signature(ObjectKey(product.updatedAt))
        .into(this)
}
```

适合封装：

- 默认占位图；
- 默认错误图；
- 常见图片样式；
- CDN 参数；
- 缓存版本规则；
- 失败日志和监控；
- 测试替换入口。

不适合封装：

- 为了统一而复制框架所有 API；
- 屏蔽特殊场景必须使用的能力；
- 把复杂业务缓存策略塞进图片框架封装；
- 让业务无法传入自定义监听器、变换或尺寸策略。

## 3.14 可玩实验：让框架知识可验证

第二章说重要框架要做小规模验证。本章把验证设计成几个可玩的实验。

### 实验一：缓存侦探

目标：观察内存缓存、磁盘缓存和网络请求的区别。

玩法：

1. 第一次进入商品列表，记录加载耗时；
2. 返回后再次进入，观察是否明显变快；
3. 修改 `updatedAt`，观察是否重新加载；
4. 临时关闭磁盘缓存或内存缓存，比较滚动体验；
5. 给 OkHttp 加日志拦截器，观察是否真的发起网络请求。

要回答：

- 哪些图片来自内存；
- 哪些来自磁盘；
- 哪些重新走网络；
- 缓存 key 改变后发生了什么。

### 实验二：制造图片错位

目标：理解 RecyclerView 图片错位。

玩法：

1. 自己写一个延迟 2 秒返回的假图片加载器；
2. 快速滑动 RecyclerView；
3. 不做 `clear` 和目标校验，观察错位；
4. 加上 `clear(imageView)` 或 request tag 校验，再观察结果。

要回答：

- 错位发生在哪个时间点；
- 框架如何避免旧请求写回新目标；
- 为什么列表复用是图片框架必须关心的问题。

### 实验三：大图内存账本

目标：理解尺寸与内存。

玩法：

1. 准备一张 4000 x 3000 图片；
2. 分别以原图、宽 1080、宽 300 的方式加载；
3. 观察内存占用和加载速度；
4. 在 Glide 中使用 `.override()`，在 Compose 中使用固定 `Modifier.size()`；
5. 对比有无稳定尺寸的差异。

要回答：

- 为什么同一张图在不同目标尺寸下内存不同；
- 框架如何推断目标尺寸；
- `wrap_content` 为什么可能带来问题。

### 实验四：设计模式寻宝

目标：把设计模式和真实 API 对上。

把下面这些 API 标注为 Builder、Facade、Strategy、Factory、Observer、Adapter、Chain of Responsibility 或 Lifecycle-aware：

```text
AsyncImage
ImageRequest.Builder
ImageLoader
components { add(...) }
Fetcher
Decoder
Transformation
Glide.with(fragment)
RequestManager
RequestBuilder
ModelLoader
DataFetcher
Target
RequestListener
DiskCacheStrategy
```

### 实验五：画出请求流水线

目标：把 API、源码和运行时行为串起来。

最终产物可以类似：

```text
Glide.with(fragment) [Facade + Lifecycle-aware]
  -> RequestManager [Mediator]
  -> RequestBuilder [Builder]
  -> SingleRequest [State Machine]
  -> Engine [Coordinator]
  -> ModelLoader [Adapter / Factory]
  -> DataFetcher [Strategy]
  -> Decoder [Strategy]
  -> Transformation [Strategy / Decorator]
  -> Target [Observer]
```

## 3.15 常见错误与修正

| 错误 | 后果 | 修正 |
| --- | --- | --- |
| 只会复制 `AsyncImage` 或 `Glide.with()` | 会用但不会分析框架 | 回到核心抽象和主流程 |
| 缓存 key 只看 URL | 商品图更新后仍显示旧图 | 加入版本、尺寸、变换等信息 |
| Compose 列表中图片没有稳定尺寸 | 可能加载过大图片或重复请求 | 使用稳定 `Modifier.size()` / `aspectRatio` |
| RecyclerView 某些分支不 clear | 旧请求可能回写 | 不加载图片时调用 `clear(imageView)` |
| 用 applicationContext 加载页面图片 | 生命周期不匹配 | 使用 Activity、Fragment、View 或 Compose 作用域 |
| 过度封装框架 API | 特殊场景无法处理 | 只封装项目约定，保留扩展能力 |
| 选型只看流行度 | 引入不适合团队的框架 | 按功能、工程、团队、迁移成本评估 |

<a id="chapter3-summary"></a>

## 3.16 本章小结

本章是第二章方法论在“图片加载框架”问题域中的第一次完整落地。

你需要掌握的不是某一个 API，而是一套分析方式：

- 先问图片框架解决什么问题；
- 再识别 Request、Loader、Cache、Fetcher、Decoder、Transformation、Target 等核心抽象；
- 然后画出 Coil 和 Glide 的主流程；
- 接着把 Builder、Facade、Strategy、Factory、Chain、Observer、Adapter、Lifecycle-aware 等设计模式放回流程里；
- 最后用功能匹配、工程匹配、团队匹配和迁移成本比较 Coil、Glide、Picasso、Fresco。

本章核心记忆：

```text
Coil:
AsyncImage -> ImageRequest -> ImageLoader -> Interceptor -> Cache -> Fetcher -> Decoder -> Painter State

Glide:
Glide.with -> RequestManager -> RequestBuilder -> SingleRequest -> Engine -> Cache -> ModelLoader -> DataFetcher -> Decoder -> Target
```

## 3.17 思考题

1. 为什么本章要先讲问题背景，而不是直接讲 Coil API？
2. `ImageRequest.Builder` 和 `RequestBuilder` 分别体现了什么 Builder 思想？
3. `AsyncImage` 和 `Glide.with()` 为什么可以看作 Facade？
4. 图片框架中哪些模块使用了 Strategy？哪些使用了 Factory？
5. Coil 和 Glide 的主流程有什么共同点和差异？
6. RecyclerView 图片错位的根因是什么？
7. 为什么缓存 key 不能只看 URL？
8. 在你的项目中，图片封装层应该统一哪些规则？哪些能力应该保留给业务？
9. 如果一个老项目已经大量使用 Glide，是否应该为了 Compose 迁移到 Coil？为什么？

## 3.18 课后练习

### 练习一：按第二章模板写框架笔记

以 Coil 或 Glide 为对象，按下面模板写一页学习笔记：

```text
它解决什么问题：
核心抽象：
最小使用：
主流程：
扩展点：
设计模式：
常见错误：
源码入口：
适用场景：
```

### 练习二：实现商品列表图片加载

任选 Compose + Coil 或 RecyclerView + Glide：

- 展示商品封面；
- 添加占位图和错误图；
- 使用 `id + updatedAt` 控制缓存版本；
- 固定图片尺寸；
- 记录成功、失败、耗时；
- 观察返回列表后的缓存命中情况。

### 练习三：设计模式标注

把下面 API 和设计模式对应起来：

```text
AsyncImage
ImageRequest.Builder
ImageLoader
Fetcher
Decoder
Transformation
Glide.with(fragment)
RequestManager
RequestBuilder
ModelLoader
DataFetcher
Target
DiskCacheStrategy
```

### 练习四：选型矩阵

为你的项目写一个图片框架选型矩阵，至少包含：

- UI 技术栈；
- Kotlin / Compose 支持；
- RecyclerView 成熟度；
- 缓存能力；
- 扩展能力；
- 团队熟悉度；
- 迁移成本；
- 最终选择理由。

## 3.19 官方参考资料

后续写示例项目或更新课程代码时，优先以官方文档为准：

- Coil 官方文档：<https://coil-kt.github.io/coil/>
- Coil Compose 文档：<https://coil-kt.github.io/coil/compose/>
- Coil ImageLoader 文档：<https://coil-kt.github.io/coil/image_loaders/>
- Glide 官方文档：<https://bumptech.github.io/glide/>
- Glide 缓存文档：<https://bumptech.github.io/glide/doc/caching.html>
