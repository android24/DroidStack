# 第 3 章：图片加载框架

从本章开始，课程进入第二阶段：数据获取与处理框架。

这一章不再把图片加载框架讲成一组抽象概念，而是围绕一个真实业务场景展开：**商品列表 + 商品详情页**。

在这个场景中，首页需要展示大量商品封面，详情页需要展示大图，用户会快速滑动列表、进入详情、返回列表、弱网重试、刷新数据。图片加载框架必须解决的不只是“显示一张图”，还包括缓存、解码、生命周期、列表复用、请求取消、错误状态和工程封装。

本章会重点结合两个真实框架：

- **Coil**：现代 Kotlin / Compose 项目中的优先选择；
- **Glide**：传统 View / RecyclerView 项目中非常成熟的选择。

Picasso、Fresco 和 Universal Image Loader 会作为历史框架和选型对比出现，不再平均用力展开。

## 3.1 本章学习目标

学习完本章后，你应该能够：

- 在 Compose 项目中使用 Coil 加载图片；
- 在传统 View / RecyclerView 项目中使用 Glide 加载图片；
- 说清楚 `AsyncImage`、`ImageRequest`、`ImageLoader`、`Glide.with()`、`RequestBuilder`、`Target` 等核心 API 的作用；
- 理解内存缓存、磁盘缓存、网络请求和 Bitmap 解码如何影响图片加载性能；
- 解释 RecyclerView / LazyColumn 中图片错位、闪烁、重复请求的原因；
- 根据项目类型在 Coil、Glide、Picasso、Fresco 之间做出合理选择；
- 设计一个项目级图片加载封装，而不是让业务代码到处散落框架调用；
- 从实际 API 反推图片加载框架的内部结构和源码阅读主线。

## 3.2 业务场景：商品列表与详情页

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

商品列表页有几个典型要求：

- 列表首屏图片要尽快出现；
- 快速滑动时不能卡顿；
- item 复用时不能显示错图；
- 图片未加载完成前要有占位图；
- 图片失败时要有错误图或重试入口；
- 返回列表时最好命中缓存；
- 商品封面更新后不能继续显示旧图。

商品详情页还有额外要求：

- 大图不能直接按原图尺寸解码；
- 需要根据屏幕宽度裁剪或缩放；
- 可能需要加载缩略图后再加载高清图；
- 失败要能够重试；
- 页面退出后请求要取消。

如果自己手写，业务代码很快会变成这样：

```text
下载图片
  -> 切线程
  -> 解析 Bitmap 尺寸
  -> 采样解码
  -> 做内存缓存
  -> 做磁盘缓存
  -> 切回主线程
  -> 判断 View 是否被复用
  -> 判断页面是否销毁
  -> 设置 ImageView 或 Compose State
```

图片加载框架的意义，就是把这些重复复杂度收敛到稳定 API 和可复用机制中。

## 3.3 Coil：Compose 项目的图片加载主线

Coil 是 Kotlin-first 的图片加载框架，名字来自 Coroutine Image Loader。它和 Compose、协程、OkHttp、Ktor 等现代 Android 技术栈配合自然。

在 Compose 项目中，优先从 Coil 开始讲，因为它能直接体现现代 Android 的状态驱动 UI 思路。

### 3.3.1 依赖接入

在真实项目中，建议把版本放到 Version Catalog 或统一依赖管理文件里。下面只展示依赖形态，具体版本以项目锁定版本和官方文档为准。

```kotlin
dependencies {
    implementation("io.coil-kt.coil3:coil-compose:<coil-version>")
    implementation("io.coil-kt.coil3:coil-network-okhttp:<coil-version>")
}
```

需要特别注意：Coil 3.x 默认不强制包含网络加载能力。也就是说，如果要加载 `https://...` 图片，需要额外接入 OkHttp 或 Ktor 网络模块。

### 3.3.2 最小使用：AsyncImage

商品列表中最小的 Compose 图片加载代码可以这样写：

```kotlin
@Composable
fun ProductCover(
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
    )
}
```

这段代码背后并不只是“设置图片 URL”。`AsyncImage` 会执行异步图片请求，并根据 Compose 组件约束和 `ContentScale` 尽量加载合适尺寸的图片。

这也是为什么在大多数 Compose 场景中，应该优先使用 `AsyncImage`，而不是一上来就使用更底层的 painter。

### 3.3.3 加入占位图、错误图和请求配置

真实业务不会只写一个 URL。商品封面通常需要占位图、错误图、淡入动画和固定裁剪策略。

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
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
    )
}
```

这里有几个点值得拆开看：

| 代码 | 解决的问题 |
| --- | --- |
| `ImageRequest.Builder` | 把一次图片加载从简单 URL 提升为可配置请求 |
| `data(product.coverUrl)` | 指定图片来源 |
| `crossfade(true)` | 非内存缓存命中时增加过渡效果 |
| `memoryCacheKey` / `diskCacheKey` | 把商品更新时间纳入缓存 key，避免图片更新后继续命中旧缓存 |
| `placeholder` | 网络或解码未完成时保持布局稳定 |
| `error` | 弱网或 URL 异常时给用户明确反馈 |
| `contentScale` | 控制图片如何适配容器 |

这就是从“能显示图片”走向“工程可用”的第一步。

### 3.3.4 LazyColumn 中的商品列表

把图片放到列表里时，重点不是多写几行 UI，而是保证 item 尺寸稳定、key 稳定、图片请求稳定。

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
            ProductRow(
                product = product,
                onClick = { onClick(product) }
            )
        }
    }
}

@Composable
fun ProductRow(
    product: Product,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProductCover(
            product = product,
            modifier = Modifier.size(72.dp)
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = product.name,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
```

这里最重要的不是 `Row`，而是这些工程细节：

- `items(key = { it.id })` 让 Compose 更稳定地识别列表 item；
- `Modifier.size(72.dp)` 给图片稳定尺寸，减少重新测量和加载原图的机会；
- `ProductCover` 内部统一配置占位图、错误图、裁剪和缓存 key；
- 业务层不直接到处拼 Coil 参数，后续替换策略更容易。

### 3.3.5 需要观察加载状态时

如果页面需要根据图片加载状态展示骨架屏、重试按钮或统计埋点，可以使用 `rememberAsyncImagePainter` 观察状态。

```kotlin
@Composable
fun ProductCoverWithState(
    product: Product,
    modifier: Modifier = Modifier
) {
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(product.coverUrl)
            .crossfade(true)
            .build()
    )

    val state by painter.state.collectAsState()

    Box(modifier = modifier.aspectRatio(1f)) {
        Image(
            painter = painter,
            contentDescription = product.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        when (state) {
            is AsyncImagePainter.State.Loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            is AsyncImagePainter.State.Error -> {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> Unit
        }
    }
}
```

这类写法适合需要明确状态控制的地方。普通列表图片优先使用 `AsyncImage`，因为它更直接，也更容易让框架根据布局约束加载合适尺寸。

### 3.3.6 配置全局 ImageLoader

如果项目需要统一 OkHttp、缓存、日志、鉴权或监控，就不应该在每个 `AsyncImage` 里重复写配置，而应该配置全局 `ImageLoader`。

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

这个配置对应了图片加载框架的几个核心部件：

| 配置 | 对应框架能力 |
| --- | --- |
| `ImageLoader.Builder` | 创建应用级图片加载器 |
| `memoryCache` | 配置解码后图片的内存缓存 |
| `diskCache` | 配置磁盘缓存目录和大小 |
| `components` | 注册网络、解码、数据获取等组件 |
| `OkHttpNetworkFetcherFactory` | 使用 OkHttp 获取网络图片 |
| `Interceptor` | 统一添加 Header、日志或监控 |

对于大型项目，`ImageLoader` 也可以通过 Hilt 注入。这样测试时可以替换成假的图片加载器，截图测试和 UI 测试也更稳定。

## 3.4 Glide：传统 View 项目的图片加载主线

Glide 是 Android 传统 View 项目中非常成熟的图片加载框架。它特别强调列表滚动性能、生命周期绑定、资源复用和多级缓存。

如果项目主要由 XML、Fragment、RecyclerView、ImageView 组成，Glide 依然是非常稳的选择。

### 3.4.1 最小使用

```kotlin
Glide.with(fragment)
    .load(product.coverUrl)
    .into(imageView)
```

这段代码中最关键的是 `Glide.with(fragment)`。

它不只是为了拿到 `Context`，还会让请求跟随 `Fragment` 生命周期。当 Fragment 销毁时，Glide 可以自动清理相关请求和资源，避免页面消失后仍然回调旧 View。

### 3.4.2 常用请求配置

真实商品封面通常会加上占位图、错误图、裁剪、缓存策略和版本签名。

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

这些配置分别解决：

| 配置 | 解决的问题 |
| --- | --- |
| `placeholder` | 加载前保持布局稳定 |
| `error` | 加载失败时展示明确状态 |
| `centerCrop` | 商品图按容器裁剪 |
| `signature(ObjectKey(...))` | 商品图更新后改变缓存 key |
| `diskCacheStrategy` | 控制磁盘缓存策略 |
| `into(imageView)` | 绑定目标 View，并让 Glide 管理旧请求 |

### 3.4.3 RecyclerView 中的正确写法

在 RecyclerView 中，重点是：**每次绑定都要给复用的 ImageView 发起新请求，或者显式清理旧请求**。

```kotlin
class ProductAdapter(
    private val fragment: Fragment
) : ListAdapter<Product, ProductViewHolder>(ProductDiffCallback) {

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = getItem(position)
        holder.bind(fragment, product)
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

为什么要这样写？

- `into(binding.coverImage)` 会覆盖这个 ImageView 上一次的图片请求；
- `clear(binding.coverImage)` 可以在 item 回收时取消不再需要的请求；
- 如果某些 item 不需要加载图片，也应该 clear 后再设置本地 Drawable；
- 否则旧请求可能晚于新绑定返回，造成图片错位。

Glide 对 RecyclerView 的处理已经很成熟，但前提是业务代码遵守“复用 View 必须重新绑定或清理”的规则。

### 3.4.4 详情页缩略图与大图

商品详情页常见需求是先显示缩略图，再加载高清图。

```kotlin
Glide.with(fragment)
    .load(product.detailImageUrl)
    .thumbnail(
        Glide.with(fragment)
            .load(product.coverUrl)
            .centerCrop()
    )
    .placeholder(R.drawable.ic_product_placeholder)
    .error(R.drawable.ic_product_error)
    .fitCenter()
    .into(binding.detailImage)
```

这类写法把列表封面和详情大图串起来，能减少详情页白屏感。

### 3.4.5 后台线程获取 Bitmap

有些业务不是把图片显示到 `ImageView`，而是需要拿到 Bitmap 做分享、通知栏、截图合成或本地处理。

```kotlin
val futureTarget = Glide.with(context)
    .asBitmap()
    .load(product.detailImageUrl)
    .submit(width, height)

val bitmap = futureTarget.get()

Glide.with(context).clear(futureTarget)
```

这类代码必须放在后台线程，并且使用完成后要 clear。否则容易阻塞主线程或持有不必要资源。

## 3.5 同一个业务，用 Coil 和 Glide 怎么选

现在回到商品列表这个场景。

| 问题 | Coil 方案 | Glide 方案 |
| --- | --- | --- |
| Compose 列表 | `AsyncImage` + `ImageRequest` | 可用 Compose 集成，但不是最自然选择 |
| RecyclerView 列表 | 可用 ImageView target | `Glide.with(fragment).load(url).into(imageView)` 非常成熟 |
| 生命周期绑定 | 跟随 Compose 和请求作用域 | `with(Activity/Fragment/View)` 体系成熟 |
| 缓存配置 | 通过 `ImageLoader` 配置 memory/disk/network | 通过 Glide 默认多级缓存和 `AppGlideModule` 配置 |
| 网络栈 | Coil 3.x 需要单独接入 OkHttp/Ktor network artifact | 默认有网络栈，也可接入 OkHttp/Volley |
| Kotlin 友好性 | 很高 | 可用 Kotlin，但 API 风格偏传统 |
| 存量项目 | 新项目优势明显 | 存量 View 项目优势明显 |
| 学习成本 | 低到中 | 中 |

推荐选择：

- 新建 Compose 项目：优先 Coil；
- 传统 View / RecyclerView 项目：优先 Glide；
- 已大量使用 Glide 的老项目：继续规范 Glide，不要为了追新立刻迁移；
- 准备从 View 迁移到 Compose 的项目：允许 Coil 和 Glide 在过渡期共存，但要收敛到统一封装；
- 课程综合项目：如果主线是 Compose + Kotlin + Jetpack，图片框架建议使用 Coil。

## 3.6 从 API 反推框架内部结构

看过真实 API 后，再回到原理就更自然。

### 3.6.1 Coil 的请求链路

```text
AsyncImage
  -> ImageRequest
  -> ImageLoader
  -> Interceptor Chain
  -> MemoryCache
  -> DiskCache
  -> Fetcher
  -> Decoder
  -> Painter / Compose UI
```

对应到代码：

- `AsyncImage(model = ...)` 是 Compose 层入口；
- `ImageRequest.Builder` 描述一次请求；
- `ImageLoader` 执行请求并管理缓存、网络、解码；
- `components` 注册网络获取、解码和扩展能力；
- `MemoryCache` / `DiskCache` 控制缓存；
- `Painter` 把最终结果交给 Compose 绘制。

### 3.6.2 Glide 的请求链路

```text
Glide.with(fragment)
  -> RequestManager
  -> RequestBuilder
  -> Request
  -> Engine
  -> Active Resources
  -> Memory Cache
  -> Resource Disk Cache
  -> Data Disk Cache
  -> ModelLoader / DataFetcher
  -> Decoder
  -> Target(ImageView)
```

对应到代码：

- `Glide.with(fragment)` 创建与生命周期绑定的 `RequestManager`；
- `.load(url)` 创建请求模型；
- `.placeholder()`、`.centerCrop()`、`.signature()` 修改请求选项；
- `.into(imageView)` 绑定目标 View；
- `clear(imageView)` 取消请求并释放目标资源。

这样读源码时就不会从类名海洋里迷路。先找到入口 API，再沿着请求链路往下看。

## 3.7 缓存：不要只背“内存缓存和磁盘缓存”

图片缓存不是一句“有缓存”就能讲清楚。不同框架的缓存层次和 key 设计会影响真实效果。

### 3.7.1 Glide 的多级缓存

Glide 默认会按多层缓存查找资源：

```text
Active Resources
  -> Memory Cache
  -> Resource Disk Cache
  -> Data Disk Cache
  -> Original Source
```

这意味着：

- 如果图片正显示在另一个 View 中，可以直接复用 active resource；
- 如果最近加载过，可以从 memory cache 命中；
- 如果解码和变换结果写入过磁盘，可以从 resource disk cache 命中；
- 如果原始数据写入过磁盘，可以从 data disk cache 重新解码；
- 全部失败才回到网络、文件或 Uri 等原始来源。

### 3.7.2 缓存 key 不只是 URL

对于 Glide，缓存 key 至少会包含 model，也会受签名、尺寸、变换、选项和数据类型影响。对于 Coil，同样可以通过 memory/disk cache key 或请求参数影响缓存结果。

商品图更新就是典型例子：

```kotlin
// Coil
.memoryCacheKey("product_cover_${product.id}_${product.updatedAt}")
.diskCacheKey("product_cover_${product.id}_${product.updatedAt}")

// Glide
.signature(ObjectKey(product.updatedAt))
```

这比“清空全部缓存”更可控。清空全部缓存既影响性能，也可能影响其他页面。

### 3.7.3 不要随意跳过缓存

跳过缓存通常只适合非常特殊的图片，例如验证码、实时监控画面、一次性临时图片。

```kotlin
Glide.with(fragment)
    .load(captchaUrl)
    .skipMemoryCache(true)
    .diskCacheStrategy(DiskCacheStrategy.NONE)
    .into(binding.captchaImage)
```

普通商品图、头像、文章封面不应该随意关闭缓存。缓存命中往往是列表流畅度的关键。

## 3.8 解码与尺寸：为什么要给图片稳定尺寸

图片内存占用和像素尺寸强相关。

```text
4000 x 3000 x 4 bytes = 48,000,000 bytes
```

一张 4000 x 3000 的 ARGB_8888 图片大约需要 45.8 MB。列表里如果直接解码原图，很快就会出现卡顿或内存压力。

因此图片加载代码要尽量让框架知道目标尺寸：

```kotlin
// Compose
Modifier.size(72.dp)

// Glide
.override(144, 144)
```

多数情况下，`ImageView` 尺寸、Compose 约束和 `ContentScale` 已经能帮助框架推断目标尺寸。但如果目标尺寸不稳定、布局先 wrap_content 后异步变化，就容易造成过大解码或重复请求。

## 3.9 生命周期与列表复用

图片请求必须跟随 UI 生命周期。

| 场景 | 错误做法 | 推荐做法 |
| --- | --- | --- |
| Fragment 中加载图片 | 使用 applicationContext 直接发请求 | `Glide.with(fragment)` 或 Compose 中使用 Coil |
| RecyclerView item 被复用 | 旧请求不取消 | 新请求 `into(imageView)` 或 `clear(imageView)` |
| Compose 列表 | item 没有稳定 key，图片尺寸不稳定 | `items(key = ...)` + 稳定尺寸 + 封装后的 `AsyncImage` |
| 页面销毁 | 请求继续持有旧 View | 绑定页面生命周期，让框架自动取消 |
| 自定义 Target | 回调里持有 View 不释放 | 在清理回调里移除引用 |

图片错位的根本原因不是“图片框架不可靠”，而是异步请求结果和被复用的 UI 目标失去了对应关系。

## 3.10 项目级封装：别让框架调用散落一地

业务代码中到处直接写 Coil 或 Glide 参数，会带来几个问题：

- 占位图、错误图风格不统一；
- CDN 尺寸参数散落；
- 缓存 key 策略不统一；
- 失败埋点和耗时统计不好做；
- 将来迁移框架成本很高；
- 测试时很难替换图片加载行为。

### 3.10.1 Compose 项目的 Coil 封装

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

业务代码变成：

```kotlin
AppImage(
    url = product.coverUrl,
    contentDescription = product.name,
    cacheKey = "product_cover_${product.id}_${product.updatedAt}",
    modifier = Modifier.size(72.dp)
)
```

这层封装不应该遮蔽所有 Coil 能力，只负责沉淀项目公共约定。

### 3.10.2 View 项目的 Glide 封装

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

业务代码变成：

```kotlin
binding.coverImage.loadProductCover(fragment, product)
```

封装层适合放：

- 默认占位图和错误图；
- 商品封面、头像、详情大图等常见样式；
- CDN 尺寸参数；
- 缓存版本规则；
- 失败日志和埋点；
- 测试替换入口。

封装层不适合把框架所有 API 原样包一遍。那样只是制造另一套更难维护的 API。

## 3.11 Picasso、Fresco 与历史框架怎么讲

课程不需要把所有图片框架讲到同样深度。

| 框架 | 课程定位 | 讲解重点 |
| --- | --- | --- |
| Coil | 主讲 | Compose、Kotlin、ImageRequest、ImageLoader、缓存和网络组件 |
| Glide | 主讲 | View、RecyclerView、生命周期、缓存、Target、资源复用 |
| Picasso | 了解 | API 简洁、早期项目维护、为什么现代新项目不常优先选 |
| Fresco | 了解 | 图片管线、大图、渐进式图片、接入和学习成本 |
| Universal Image Loader | 历史 | 早期图片加载方案和技术演进 |

Picasso 可以用来说明“简单 API 的价值”，但现代项目更常在 Coil 和 Glide 之间选择。

Fresco 可以用来说明“图片管线设计”的复杂性，但普通业务项目不一定需要这种重量级方案。

## 3.12 源码阅读入口

源码阅读不要从所有类开始，而要从业务代码入口开始。

### 3.12.1 Coil 源码主线

从这段代码开始：

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(product.coverUrl)
        .build(),
    contentDescription = product.name
)
```

优先看：

```text
AsyncImage
  -> rememberAsyncImagePainter
  -> ImageRequest
  -> ImageLoader
  -> Interceptor Chain
  -> MemoryCache
  -> Fetcher
  -> Decoder
```

阅读目标：

- `AsyncImage` 如何把 Compose 约束传给请求；
- `ImageRequest` 如何描述数据源、尺寸、缓存和回调；
- `ImageLoader` 如何调度请求；
- network component 如何接入 OkHttp 或 Ktor；
- 缓存命中和失败结果如何返回。

### 3.12.2 Glide 源码主线

从这段代码开始：

```kotlin
Glide.with(fragment)
    .load(product.coverUrl)
    .centerCrop()
    .into(binding.coverImage)
```

优先看：

```text
Glide.with()
  -> RequestManager
  -> RequestBuilder
  -> SingleRequest
  -> Engine
  -> DecodeJob
  -> DataFetcher
  -> Resource
  -> Target
```

阅读目标：

- `Glide.with(fragment)` 如何绑定生命周期；
- `RequestBuilder` 如何收集请求配置；
- `Engine` 如何协调缓存和解码；
- `Target` 如何接收结果和清理资源；
- RecyclerView 复用时请求如何被覆盖或取消。

## 3.13 常见错误与修正

| 错误 | 后果 | 修正 |
| --- | --- | --- |
| Compose 列表中图片没有固定尺寸 | 可能重复测量、加载过大图片 | 给图片明确 `size`、`aspectRatio` 或稳定约束 |
| Coil 3.x 只加 `coil-compose` 就加载网络图 | 网络 URL 无法正常加载 | 额外接入 `coil-network-okhttp` 或 Ktor 网络模块 |
| RecyclerView 中某些 item 不加载图片却不 clear | 旧图片请求可能回写 | 在分支中调用 `Glide.with(fragment).clear(imageView)` |
| 用 applicationContext 加载页面图片 | 生命周期不匹配 | 使用 Activity、Fragment、View 或 Compose 作用域 |
| 商品图更新后只清空全部缓存 | 性能差、影响面大 | 用 cache key 或 `signature` 表达版本 |
| 验证码也走默认缓存 | 显示旧验证码 | 对实时图片关闭磁盘和内存缓存 |
| 自定义 Target 不释放资源 | 泄漏或显示旧内容 | 在清理回调中移除引用 |
| 封装层过度抽象 | 特殊场景难处理 | 只封装项目约定，保留框架扩展能力 |

## 3.14 本章小结

这一章的重点不是“图片加载原理背诵”，而是把原理落到真实框架。

你需要记住：

- Coil 的主线是 `AsyncImage -> ImageRequest -> ImageLoader -> Components -> Cache / Fetcher / Decoder`；
- Glide 的主线是 `Glide.with -> RequestManager -> RequestBuilder -> Engine -> Cache / DataFetcher / Decoder -> Target`；
- Compose 新项目优先考虑 Coil，传统 View 和 RecyclerView 项目优先考虑 Glide；
- 图片加载性能主要受尺寸、缓存、解码、列表复用和生命周期影响；
- 缓存 key 应该表达图片版本，而不是一出问题就清空全部缓存；
- 项目中需要轻量封装图片加载公共规则，但不能把框架能力完全包死。

## 3.15 思考题

1. 为什么 Compose 列表中推荐优先使用 `AsyncImage`？
2. Coil 3.x 加载网络图片时，为什么需要额外接入 network artifact？
3. `Glide.with(fragment)` 和 `Glide.with(context)` 在生命周期语义上有什么区别？
4. RecyclerView 中图片错位的根本原因是什么？
5. 商品封面更新后，Coil 和 Glide 分别可以如何改变缓存 key？
6. 为什么验证码、商品封面、用户头像不能使用同一套缓存策略？
7. 项目级图片封装应该包含哪些规则？哪些规则应该留给业务自己决定？

## 3.16 课后练习

基于本章商品列表场景完成两个小练习。

### 练习一：Compose + Coil 商品列表

实现一个 `ProductListScreen`：

- 使用 `LazyColumn` 展示商品列表；
- 使用 `AsyncImage` 或封装后的 `AppImage` 展示封面；
- 添加占位图和错误图；
- 使用商品 `id + updatedAt` 作为缓存 key；
- 图片尺寸保持稳定；
- 点击商品进入详情页并展示大图。

### 练习二：RecyclerView + Glide 商品列表

实现一个 `ProductAdapter`：

- 在 `onBindViewHolder` 中加载商品封面；
- 使用 `placeholder`、`error`、`centerCrop`；
- 使用 `signature(ObjectKey(product.updatedAt))` 控制缓存版本；
- 在 `onViewRecycled` 中 clear 图片请求；
- 对比快速滑动时有无 clear 的表现差异。

完成练习后，再回头看本章的源码主线。此时你看到的就不再是一堆类名，而是一条从业务代码进入框架内部的路径。

## 3.17 官方参考资料

后续写示例项目或更新课程代码时，优先以官方文档为准：

- Coil 官方文档：<https://coil-kt.github.io/coil/>
- Coil Compose 文档：<https://coil-kt.github.io/coil/compose/>
- Coil ImageLoader 文档：<https://coil-kt.github.io/coil/image_loaders/>
- Glide 官方文档：<https://bumptech.github.io/glide/>
- Glide 缓存文档：<https://bumptech.github.io/glide/doc/caching.html>
