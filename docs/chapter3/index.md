# 第 3 章：图片加载框架

从本章开始，课程进入第二阶段：数据获取与处理框架。

图片加载框架不能只学 API，也不能只背“内存缓存、磁盘缓存、解码”这些概念。一个真正合格的框架课程，应该让学习者能回答四类问题：

1. **主流程是什么**：从 `url` 到屏幕，中间经过哪些对象和阶段；
2. **设计模式在哪里**：Builder、Facade、Strategy、Chain of Responsibility、Observer、Factory 等模式分别藏在哪些 API 和源码结构里；
3. **工程问题怎么解决**：列表错位、生命周期泄漏、缓存失效、大图 OOM、弱网失败如何落到框架机制；
4. **如何动手验证**：通过小实验观察缓存命中、请求取消、图片错位、尺寸解码和框架扩展点。

本章会以“商品列表 + 商品详情页”为场景，同时拆解两个主流框架：

- **Coil**：现代 Kotlin / Compose 项目的代表；
- **Glide**：传统 View / RecyclerView 项目的成熟代表。

Picasso、Fresco 和 Universal Image Loader 会作为历史框架与选型背景出现，不平均展开。

## 3.1 本章的学习方式

本章不按“先介绍 Coil，再介绍 Glide，再做对比”的普通教程方式展开，而是用一套更适合框架学习的五层解剖法。

```text
业务层：商品列表为什么需要图片加载框架
  -> API 层：Coil / Glide 代码如何写
  -> 流程层：一次图片请求经过哪些步骤
  -> 设计层：这些步骤背后用了哪些设计模式
  -> 工程层：如何封装、调试、选型和验证
```

学习完本章后，你应该能够：

- 画出 Coil 和 Glide 的图片加载主流程；
- 说清楚 `AsyncImage`、`ImageRequest`、`ImageLoader`、`Glide.with()`、`RequestManager`、`RequestBuilder`、`Engine`、`Target` 的职责；
- 识别图片加载框架中的 Builder、Facade、Strategy、Factory、Observer、Chain of Responsibility、Lifecycle-aware 等设计思想；
- 解释内存缓存、磁盘缓存、网络获取、解码、变换和目标显示之间的关系；
- 通过实验复现和修复 RecyclerView 图片错位、缓存不更新、大图占用过高等问题；
- 根据 Compose / View、Kotlin / Java、新项目 / 老项目等条件进行框架选型；
- 设计一个不会把框架包死的项目级图片加载封装。

## 3.2 游戏地图：商品图片加载任务

先定义一个贯穿本章的业务模型：

```kotlin
data class Product(
    val id: String,
    val name: String,
    val coverUrl: String,
    val detailImageUrl: String,
    val updatedAt: Long
)
```

商品列表页需要展示商品封面：

- 首屏图片要尽快出现；
- 快速滑动不能卡顿；
- item 复用时不能显示错图；
- 图片加载失败要有错误态；
- 返回列表时应该尽量命中缓存；
- 商品封面更新后不能继续显示旧图。

商品详情页需要展示高清大图：

- 不能直接按原图尺寸解码；
- 可以先显示缩略图，再加载高清图；
- 页面退出后请求要取消；
- 失败后要支持重试；
- 大图加载不能拖垮内存。

这就是本章的“闯关任务”：

```text
第 1 关：用 Coil / Glide 把图片显示出来
第 2 关：追踪一次图片请求的完整流程
第 3 关：识别流程中使用的设计模式
第 4 关：制造列表错位、缓存失效、大图问题并修复
第 5 关：设计项目级封装和选型规则
```

## 3.3 先做一个最小图片加载器

在学习 Coil 和 Glide 之前，先用一个极简模型理解图片加载框架到底在解决什么。

如果不使用框架，最小流程大概是：

```text
URL
  -> 后台线程下载 bytes
  -> 根据目标尺寸解码 Bitmap
  -> 切回主线程
  -> 设置到 ImageView 或 Compose State
```

但真实项目马上会要求：

```text
URL
  -> 判断内存缓存
  -> 判断磁盘缓存
  -> 网络下载
  -> 解码与采样
  -> 图片变换
  -> 判断请求是否取消
  -> 判断目标 View 是否复用
  -> 判断页面生命周期是否有效
  -> 显示结果或错误态
```

如果把它抽象成框架对象，通常会得到下面这组角色：

| 角色 | 职责 | 常见设计思想 |
| --- | --- | --- |
| Request | 描述一次图片请求 | Builder、Value Object |
| Loader / Engine | 调度请求主流程 | Facade、Coordinator |
| Cache | 保存和复用结果 | Strategy、LRU |
| Fetcher | 从网络、文件、资源获取数据 | Strategy、Factory |
| Decoder | 把数据解码成图片对象 | Strategy、Adapter |
| Transformation | 裁剪、圆角、模糊等变换 | Strategy、Decorator |
| Target | 接收最终结果 | Observer、Callback |
| Lifecycle | 暂停、恢复、取消请求 | Observer、Lifecycle-aware |

这张表是本章的核心地图。后面看 Coil、Glide、Picasso、Fresco，都可以把它们放回这组角色里。

## 3.4 Coil：从 Compose API 进入框架

Coil 是 Kotlin-first 的图片加载框架，适合现代 Kotlin / Compose 项目。它的名字来自 Coroutine Image Loader，天然和协程、Compose、OkHttp / Ktor 等生态比较贴近。

### 3.4.1 依赖接入

真实项目中建议把版本放到 Version Catalog。下面只展示依赖形态，具体版本以项目锁定版本和官方文档为准。

```kotlin
dependencies {
    implementation("io.coil-kt.coil3:coil-compose:<coil-version>")
    implementation("io.coil-kt.coil3:coil-network-okhttp:<coil-version>")
}
```

需要注意：Coil 3.x 网络加载能力是按组件接入的。如果要加载 `https://...` 图片，需要接入 OkHttp 或 Ktor 网络模块。

### 3.4.2 最小 API：AsyncImage

商品列表中最简单的封面加载代码：

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

这段代码看起来只有一个 URL，但背后已经包含：

```text
Compose 约束
  -> 创建图片请求
  -> 执行异步加载
  -> 命中缓存或网络获取
  -> 解码图片
  -> 把结果交给 Painter 绘制
```

`AsyncImage` 可以看作 Coil 给 Compose 层提供的 **Facade**：业务代码不用直接面对缓存、解码、线程切换和目标回调。

### 3.4.3 可配置请求：ImageRequest

真实业务不能只传 URL。商品图需要占位图、错误图、淡入动画和缓存版本。

```kotlin
@Composable
fun ProductCover(
    product: Product,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(product.coverUrl)
            .crossfade(true)
            .memoryCacheKey("product_cover_${product.id}_${product.updatedAt}")
            .diskCacheKey("product_cover_${product.id}_${product.updatedAt}")
            .build(),
        placeholder = painterResource(R.drawable.ic_product_placeholder),
        error = painterResource(R.drawable.ic_product_error),
        contentDescription = product.name,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
    )
}
```

这里出现了第一个明确的设计模式：**Builder**。

| API | 模式 | 作用 |
| --- | --- | --- |
| `ImageRequest.Builder` | Builder | 分步骤构建复杂请求 |
| `data()` | Builder 配置项 | 指定图片来源 |
| `crossfade()` | Builder 配置项 | 指定过渡效果 |
| `memoryCacheKey()` | Builder 配置项 | 指定内存缓存 key |
| `diskCacheKey()` | Builder 配置项 | 指定磁盘缓存 key |
| `build()` | Builder 终点 | 生成不可变请求对象 |

为什么这里适合 Builder？

因为一次图片请求包含 URL、目标尺寸、缓存 key、生命周期、占位图、错误图、变换、监听器等大量可选配置。如果使用一个超长构造函数，API 会非常难用，也不利于默认值管理。

### 3.4.4 商品列表：让请求、尺寸和 key 稳定

```kotlin
@Composable
fun ProductList(
    products: List<Product>,
    onClick: (Product) -> Unit
) {
    LazyColumn {
        items(
            items = products,
            key = { it.id }
        ) { product ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(product) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductCover(product = product)
                Spacer(Modifier.width(12.dp))
                Text(product.name)
            }
        }
    }
}
```

这里有三个工程要点：

- `items(key = { it.id })` 让列表 item 身份稳定；
- `Modifier.size(72.dp)` 让图片目标尺寸稳定；
- `product.id + updatedAt` 进入缓存 key，让商品图更新可控。

如果去掉稳定尺寸，框架可能无法准确推断目标大小；如果去掉稳定 key，列表状态和图片请求更容易发生错配。

### 3.4.5 全局 ImageLoader：框架的可插拔核心

如果项目需要统一缓存、网络、日志、鉴权、监控，就应该配置全局 `ImageLoader`。

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

这段配置对应几种设计思想：

| 代码 | 设计思想 | 说明 |
| --- | --- | --- |
| `ImageLoader.Builder` | Builder | 创建复杂的应用级加载器 |
| `components { add(...) }` | Registry / Factory | 注册可替换组件 |
| `OkHttpNetworkFetcherFactory` | Factory | 创建网络数据获取器 |
| `MemoryCache` / `DiskCache` | Strategy | 替换缓存策略 |
| OkHttp `Interceptor` | Chain of Responsibility | 请求经过一组拦截器处理 |

这比“会写 `AsyncImage`”更重要。学习框架时要问：哪些部分是固定主流程，哪些部分被设计成可替换扩展点？

## 3.5 Coil 主流程：一次请求如何跑完

把 Coil 的主流程画成一条可追踪路径：

```text
AsyncImage
  -> rememberAsyncImagePainter
  -> ImageRequest
  -> ImageLoader
  -> Interceptor Chain
  -> MemoryCache
  -> Fetcher
  -> Decoder
  -> Transformation
  -> Painter State
  -> Compose Recomposition
```

逐层看：

| 阶段 | 做什么 | 你应该观察什么 |
| --- | --- | --- |
| `AsyncImage` | Compose 层入口 | 是否传入稳定尺寸和稳定 model |
| `ImageRequest` | 请求描述 | URL、缓存 key、尺寸、监听器是否正确 |
| `ImageLoader` | 调度请求 | 全局缓存和网络组件是否统一 |
| Interceptor Chain | 串联加载过程 | 是否能插入日志、监控、改写请求 |
| MemoryCache | 查内存 | 返回列表时是否快速命中 |
| Fetcher | 获取数据 | 网络、文件、资源分别怎么取 |
| Decoder | 解码图片 | 是否按目标尺寸解码 |
| Transformation | 图片变换 | 圆角、裁剪是否影响缓存 key |
| Painter State | 结果状态 | Loading、Success、Error 如何反馈 UI |

这里最值得注意的是 **Chain of Responsibility**。图片请求不是一个巨大函数从头跑到尾，而是被拆成多个处理节点。每个节点处理自己的一段逻辑，再把请求交给下一个节点。

## 3.6 Glide：从 View API 进入框架

Glide 是传统 Android View 项目中非常成熟的图片加载框架。它的优势是生命周期绑定、RecyclerView 列表复用、多级缓存、资源复用和丰富的图片处理能力。

### 3.6.1 最小 API：Glide.with

```kotlin
Glide.with(fragment)
    .load(product.coverUrl)
    .into(binding.coverImage)
```

这里的 `Glide.with(fragment)` 很关键。它不是简单拿 `Context`，而是在创建一个和 `Fragment` 生命周期关联的 `RequestManager`。

这背后体现的是 **Lifecycle-aware** 设计：

```text
Fragment onStart  -> 恢复请求
Fragment onStop   -> 暂停请求
Fragment onDestroy -> 清理请求
```

所以在页面图片加载中，优先传入 `Fragment`、`Activity` 或 `View`，不要随手传 `applicationContext`。

### 3.6.2 RequestBuilder：链式构建请求

商品封面通常这样写：

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

这段 API 同样大量使用 Builder / Fluent Interface 思想。

| API | 角色 |
| --- | --- |
| `Glide.with(fragment)` | 创建生命周期感知的 RequestManager |
| `.load(url)` | 指定 Model |
| `.placeholder()` | 设置加载前状态 |
| `.error()` | 设置失败状态 |
| `.centerCrop()` | 设置变换策略 |
| `.signature()` | 修改缓存签名 |
| `.diskCacheStrategy()` | 设置磁盘缓存策略 |
| `.into(imageView)` | 设置 Target 并启动请求 |

### 3.6.3 RecyclerView 中的请求取消

RecyclerView 是图片加载最容易出错的地方。

```kotlin
class ProductAdapter(
    private val fragment: Fragment
) : ListAdapter<Product, ProductViewHolder>(ProductDiffCallback) {

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(fragment, getItem(position))
    }

    override fun onViewRecycled(holder: ProductViewHolder) {
        holder.clear(fragment)
    }
}

class ProductViewHolder(
    private val binding: ItemProductBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(fragment: Fragment, product: Product) {
        binding.nameText.text = product.name

        Glide.with(fragment)
            .load(product.coverUrl)
            .placeholder(R.drawable.ic_product_placeholder)
            .error(R.drawable.ic_product_error)
            .centerCrop()
            .signature(ObjectKey(product.updatedAt))
            .into(binding.coverImage)
    }

    fun clear(fragment: Fragment) {
        Glide.with(fragment).clear(binding.coverImage)
    }
}
```

这里的核心不是代码写法，而是异步模型：

```text
第 1 次绑定：ImageView -> 商品 A -> 请求 A
快速滑动复用：ImageView -> 商品 B -> 请求 B
如果请求 A 晚于请求 B 返回，就可能把 A 显示到 B 的位置
```

Glide 通过 `into(imageView)` 和 `clear(imageView)` 管理目标对象上的旧请求。业务代码要做的是：每次绑定时重新发起请求，不需要图片时清理请求。

### 3.6.4 详情页缩略图与大图

```kotlin
Glide.with(fragment)
    .load(product.detailImageUrl)
    .thumbnail(
        Glide.with(fragment)
            .load(product.coverUrl)
            .centerCrop()
    )
    .fitCenter()
    .placeholder(R.drawable.ic_product_placeholder)
    .error(R.drawable.ic_product_error)
    .into(binding.detailImage)
```

这里体现的是 **组合请求**：先用列表封面降低白屏感，再加载详情大图。

## 3.7 Glide 主流程：从 with 到 Target

Glide 的主流程可以这样追踪：

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

逐层看：

| 阶段 | 做什么 | 常见设计思想 |
| --- | --- | --- |
| `Glide.with()` | 创建请求管理器 | Facade、Lifecycle-aware |
| `RequestManager` | 管理请求集合 | Mediator / Coordinator |
| `RequestBuilder` | 构建请求参数 | Builder、Fluent Interface |
| `SingleRequest` | 表示一次请求 | Command / State Machine |
| `Engine` | 调度缓存、解码、加载 | Facade、Coordinator |
| Active Resources | 复用正在使用的资源 | Cache |
| Memory Cache | 复用最近解码结果 | LRU Cache |
| Disk Cache | 复用磁盘资源或原始数据 | Strategy |
| `ModelLoader` | 把 model 转成可加载数据 | Adapter、Factory |
| `DataFetcher` | 读取网络、文件、Uri 数据 | Strategy |
| `Decoder` | 解码图片 | Strategy |
| `Transformation` | 裁剪、圆角等处理 | Strategy、Decorator |
| `Target` | 接收结果并更新 UI | Observer、Callback |

这样一来，Glide 就不是一串神秘类名，而是一条清晰的请求流水线。

## 3.8 图片框架里的设计模式地图

本节是本章的核心。学习图片框架，不只是“知道它做了缓存”，而是要能看出它如何组织复杂度。

| 设计模式 / 思想 | Coil 中的体现 | Glide 中的体现 | 解决的问题 |
| --- | --- | --- | --- |
| Facade | `AsyncImage`、`ImageLoader` | `Glide.with()`、`Engine` | 用简单入口隐藏复杂流程 |
| Builder | `ImageRequest.Builder`、`ImageLoader.Builder` | `RequestBuilder`、`RequestOptions` | 构建包含大量可选参数的请求 |
| Strategy | `Fetcher`、`Decoder`、`Transformation`、Cache | `ModelLoader`、`DataFetcher`、`Decoder`、`Transformation`、`DiskCacheStrategy` | 将可变算法抽成可替换策略 |
| Factory | Component 注册、Fetcher Factory | ModelLoaderFactory、DataFetcher 相关工厂 | 根据数据类型创建合适处理器 |
| Chain of Responsibility | Interceptor Chain | 请求/解码链路中的多阶段处理 | 把大流程拆成多个节点 |
| Observer / Callback | Painter State、listener | Target、RequestListener | 加载结果通知 UI 或业务 |
| Adapter | 不同数据源适配成统一请求数据 | ModelLoader 把 URL、Uri、File 适配成 DataFetcher | 屏蔽数据源差异 |
| Decorator | Transformation 叠加处理 | RequestOptions、Transformation 组合 | 在不改主流程的情况下增强行为 |
| State Machine | Loading / Success / Error | SingleRequest 状态流转 | 管理异步请求状态 |
| Lifecycle-aware | Compose 作用域与请求生命周期 | RequestManager 绑定 Activity / Fragment / View | 页面销毁时取消和清理请求 |

把这张表和源码主流程放在一起看，框架设计就有层次了：

```text
Facade 负责给业务一个简单入口
Builder 负责构造复杂请求
Strategy / Factory 负责可扩展组件
Chain 负责把加载流程串起来
Cache 负责复用资源
Observer / Target 负责把结果交回 UI
Lifecycle-aware 负责请求生死
```

## 3.9 主流程对比：Coil 与 Glide

### 3.9.1 同一张商品图的两条路径

Coil 适合从 Compose 进入：

```text
ProductCover(product)
  -> AsyncImage
  -> ImageRequest
  -> ImageLoader
  -> MemoryCache
  -> Fetcher
  -> Decoder
  -> Painter State
  -> Compose UI
```

Glide 适合从 ImageView 进入：

```text
ViewHolder.bind(product)
  -> Glide.with(fragment)
  -> RequestBuilder
  -> SingleRequest
  -> Engine
  -> Memory / Disk Cache
  -> ModelLoader / DataFetcher
  -> Decoder
  -> Target(ImageView)
```

### 3.9.2 框架差异不是“谁更强”

| 维度 | Coil | Glide |
| --- | --- | --- |
| UI 入口 | Compose 友好，`AsyncImage` 自然 | View / RecyclerView 成熟 |
| 语言风格 | Kotlin-first | Java 时代成熟，Kotlin 可用 |
| 请求模型 | `ImageRequest` 清晰 | `RequestBuilder` 链式成熟 |
| 生命周期 | 跟随 Compose 与请求作用域 | `RequestManager` 生命周期模型非常成熟 |
| 缓存理解 | 容易从 ImageLoader 配置切入 | 多级缓存体系更复杂但能力强 |
| 源码学习 | 适合现代框架入口学习 | 适合学习大型成熟框架的工程复杂度 |
| 推荐场景 | Kotlin + Compose 新项目 | XML View / RecyclerView 存量项目 |

课程中可以把 Coil 作为“现代 Android 框架设计”的样本，把 Glide 作为“成熟工程框架设计”的样本。

## 3.10 缓存与解码：从问题倒推机制

### 3.10.1 为什么缓存 key 不能只看 URL

同一个 URL 可能有不同使用方式：

```text
列表页：72dp 正方形封面，centerCrop，圆角
详情页：屏幕宽度大图，fitCenter，无圆角
头像：48dp 圆形裁剪
```

如果缓存 key 只看 URL，就可能命中错误尺寸或错误变换结果。真实缓存 key 通常会受这些因素影响：

- URL / Uri / File；
- 图片尺寸；
- 缩放模式；
- Transformation；
- 解码配置；
- 签名或版本；
- 额外请求参数。

商品图片更新时，不要粗暴清空全部缓存，而应该修改版本签名：

```kotlin
// Coil
.memoryCacheKey("product_cover_${product.id}_${product.updatedAt}")
.diskCacheKey("product_cover_${product.id}_${product.updatedAt}")

// Glide
.signature(ObjectKey(product.updatedAt))
```

### 3.10.2 为什么稳定尺寸很重要

一张 4000 x 3000 的 ARGB_8888 图片大约需要：

```text
4000 x 3000 x 4 bytes = 48,000,000 bytes
```

也就是约 45.8 MB。列表封面如果只需要 72dp，却按原图解码，就会浪费大量内存。

所以业务代码应该尽量让框架知道目标尺寸：

```kotlin
// Compose
Modifier.size(72.dp)

// Glide
.override(144, 144)
```

设计上，这属于 **Strategy + Context**：同样的解码策略，需要结合目标尺寸、缩放模式、设备密度和图片格式做决策。

### 3.10.3 为什么列表会错位

错位不是玄学，而是异步请求和 UI 复用之间的竞态。

```text
时间 1：ImageView 绑定商品 A，发起请求 A
时间 2：列表滑动，ImageView 被复用给商品 B，发起请求 B
时间 3：请求 B 返回，显示 B
时间 4：请求 A 才返回，如果没有校验或取消，就把 A 覆盖到 B 上
```

框架的解决方式：

- 为目标对象记录当前请求；
- 新请求覆盖旧请求；
- item 回收时取消请求；
- 结果返回前确认目标仍然有效；
- 页面销毁时统一清理请求。

这就是为什么 `into(imageView)`、`clear(imageView)`、`Glide.with(fragment)`、Compose 稳定 key 都不是细枝末节，而是主流程正确性的组成部分。

## 3.11 可玩实验：把框架拆开看

这一章建议配套几个小实验，而不是只读文字。

### 实验一：缓存侦探

目标：观察内存缓存、磁盘缓存和网络请求的区别。

玩法：

1. 第一次进入商品列表，记录图片加载耗时；
2. 返回再进入，观察是否明显变快；
3. 修改 `updatedAt`，观察缓存是否重新加载；
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
- wrap_content 为什么可能带来问题。

### 实验四：设计模式寻宝

目标：在真实框架 API 中识别设计模式。

给学习者一组卡片：

```text
Builder
Facade
Strategy
Factory
Observer
Chain of Responsibility
Adapter
Lifecycle-aware
```

让他们把卡片贴到这些 API 上：

```text
AsyncImage
ImageRequest.Builder
ImageLoader
components { add(...) }
Fetcher
Decoder
Transformation
Glide.with(fragment)
RequestBuilder
ModelLoader
DataFetcher
Target
RequestListener
DiskCacheStrategy
```

这个实验的价值是：设计模式不是孤立背诵，而是帮助我们理解框架为什么这样拆对象。

### 实验五：画出请求流水线

目标：把 API、源码和运行时行为串起来。

玩法：

1. 选择 Coil 或 Glide；
2. 从一行加载代码开始；
3. 画出 8 到 12 个关键节点；
4. 标出每个节点的职责；
5. 标出节点背后的设计模式；
6. 标出可以扩展或替换的位置。

最终产物应该类似：

```text
Glide.with(fragment) [Facade + Lifecycle]
  -> RequestBuilder [Builder]
  -> SingleRequest [State Machine]
  -> Engine [Coordinator]
  -> ModelLoader [Adapter / Factory]
  -> DataFetcher [Strategy]
  -> Decoder [Strategy]
  -> Transformation [Strategy / Decorator]
  -> Target [Observer]
```

这比单纯读源码更有效。

## 3.12 源码阅读路线

源码阅读不要从所有类开始。每个框架都要从“业务入口”开始。

### 3.12.1 Coil 阅读路线

入口代码：

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(product.coverUrl)
        .build(),
    contentDescription = product.name
)
```

建议路线：

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

- `AsyncImage` 如何把 Compose 约束传给请求；
- `ImageRequest` 如何保存请求参数；
- `ImageLoader` 如何组织组件；
- Interceptor Chain 的每一层在做什么；
- Fetcher / Decoder 为什么要抽象成可替换组件；
- Loading / Success / Error 如何驱动 Compose UI。

### 3.12.2 Glide 阅读路线

入口代码：

```kotlin
Glide.with(fragment)
    .load(product.coverUrl)
    .centerCrop()
    .into(binding.coverImage)
```

建议路线：

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
- 多级缓存是在哪些阶段查找的；
- `Target` 如何接收成功、失败和清理事件。

## 3.13 工程封装：既统一，又不要包死

业务代码到处写 Coil / Glide 参数，会带来这些问题：

- 占位图和错误图风格不统一；
- 缓存 key 规则不统一；
- CDN 尺寸参数散落；
- 图片加载失败不好统一监控；
- 将来迁移框架成本高；
- 测试时难以替换图片加载行为。

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

好的封装只沉淀项目约定：

- 默认占位图；
- 默认错误图；
- 常见图片样式；
- 缓存版本规则；
- CDN 参数；
- 失败日志；
- 测试替换入口。

不应该把框架所有 API 原样包一遍，也不应该把特殊场景的扩展能力藏起来。

## 3.14 选型：不要把框架当信仰

| 项目情况 | 推荐选择 | 原因 |
| --- | --- | --- |
| 新建 Kotlin + Compose 项目 | Coil | Compose 入口自然，Kotlin 友好 |
| XML + RecyclerView 存量项目 | Glide | 生命周期、列表复用、多级缓存成熟 |
| 已大量使用 Glide 的项目 | 继续规范 Glide | 迁移成本可能高于收益 |
| 课程综合项目 | Coil | 更贴近现代 Android 技术栈 |
| 特殊大图、复杂图片管线 | 评估 Fresco 或自定义能力 | 普通项目未必需要 |
| 简单历史项目维护 | Picasso 只作为了解或维护 | 新项目通常不优先选择 |

选型时要问：

- UI 是 Compose 还是 View；
- 项目是新项目还是老项目；
- 团队是否熟悉 Kotlin / 协程；
- 是否需要大量 RecyclerView 图片优化；
- 是否需要 GIF、SVG、视频帧、大图；
- 是否需要统一监控、鉴权、CDN 参数；
- 迁移成本是否可控。

## 3.15 本章小结

图片加载框架的核心不是“显示图片”，而是把复杂异步流程拆成可组合、可扩展、可取消、可缓存、可感知生命周期的对象协作。

本章要记住几条主线：

- Coil 主流程：`AsyncImage -> ImageRequest -> ImageLoader -> Interceptor -> Cache -> Fetcher -> Decoder -> Painter State`；
- Glide 主流程：`Glide.with -> RequestManager -> RequestBuilder -> SingleRequest -> Engine -> Cache -> ModelLoader -> DataFetcher -> Decoder -> Target`；
- Builder 解决复杂请求构建；
- Facade 隐藏复杂加载流程；
- Strategy / Factory 让数据获取、解码、变换、缓存可替换；
- Chain of Responsibility 把请求处理拆成流水线；
- Observer / Target 把异步结果交回 UI；
- Lifecycle-aware 让请求跟随页面生死；
- 可玩实验比单纯背概念更重要。

## 3.16 思考题

1. 为什么 `ImageRequest.Builder` 和 `RequestBuilder` 都适合用 Builder 模式？
2. `AsyncImage` 和 `Glide.with()` 分别体现了什么 Facade 思想？
3. 图片框架中哪些部分属于 Strategy？哪些属于 Factory？
4. RecyclerView 图片错位的本质是什么？框架如何避免它？
5. 为什么缓存 key 不能只使用 URL？
6. Coil 和 Glide 的主流程有哪些相同点和不同点？
7. 项目级封装应该统一哪些规则？哪些能力应该保留给业务直接使用？
8. 如果你要设计一个简化版图片框架，第一版会保留哪些模块？

## 3.17 课后练习

### 练习一：画流程图

任选 Coil 或 Glide，从一行图片加载代码开始，画出主流程，并标出每个节点的职责和设计模式。

### 练习二：设计模式标注

把下面这些 API 分别标注为 Builder、Facade、Strategy、Factory、Observer、Adapter、Chain of Responsibility 或 Lifecycle-aware：

```text
AsyncImage
ImageRequest.Builder
ImageLoader
Fetcher
Decoder
Transformation
Glide.with(fragment)
RequestBuilder
ModelLoader
DataFetcher
Target
DiskCacheStrategy
```

### 练习三：缓存实验

实现商品列表图片加载，分别观察：

- 第一次进入列表；
- 返回后再次进入列表；
- 修改 `updatedAt` 后再次进入；
- 关闭磁盘缓存后再次进入。

记录每种情况下网络请求、加载速度和 UI 体验的变化。

### 练习四：错位实验

手写一个延迟返回的假图片加载器，在 RecyclerView 中故意制造图片错位，再用请求取消或目标校验修复。

### 练习五：封装评审

设计一个 `AppImage` 或 `loadProductCover()` 封装，并回答：

- 它统一了哪些项目规则；
- 它保留了哪些框架扩展能力；
- 如果未来从 Glide 迁移到 Coil，哪些业务代码会受影响；
- 如何在测试中替换图片加载行为。

## 3.18 官方参考资料

后续写示例项目或更新课程代码时，优先以官方文档为准：

- Coil 官方文档：<https://coil-kt.github.io/coil/>
- Coil Compose 文档：<https://coil-kt.github.io/coil/compose/>
- Coil ImageLoader 文档：<https://coil-kt.github.io/coil/image_loaders/>
- Glide 官方文档：<https://bumptech.github.io/glide/>
- Glide 缓存文档：<https://bumptech.github.io/glide/doc/caching.html>
