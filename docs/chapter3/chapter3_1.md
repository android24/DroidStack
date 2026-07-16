# 第 3.1 节：请求管线与并发调度

一行图片加载代码为什么能隐藏下载、缓存、解码、线程切换和 UI 更新？本节沿着一次真实请求向下追踪，直到每个核心角色都能放回调用链。

## 3.1.1 先明确要追踪的请求

Compose + Coil 的入口：

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(product.coverUrl)
        .memoryCacheKey("cover:${product.id}:${product.imageVersion}")
        .diskCacheKey("cover:${product.id}:${product.imageVersion}")
        .build(),
    contentDescription = product.name,
    contentScale = ContentScale.Crop,
    modifier = Modifier.size(72.dp)
)
```

View + Glide 的入口：

```kotlin
Glide.with(fragment)
    .load(product.coverUrl)
    .override(144, 144)
    .centerCrop()
    .signature(ObjectKey(product.imageVersion))
    .into(binding.coverImage)
```

两段代码都表达了五类信息：

| 信息 | 示例 | 后续影响 |
| --- | --- | --- |
| 数据模型 | URL | 选择哪个数据适配器与获取器 |
| 目标尺寸 | `72.dp` / `144 x 144` | 缓存 Key、采样比例和内存 |
| 变换 | `Crop` / `centerCrop()` | 输出像素与资源缓存 Key |
| 版本 | `imageVersion` | 缓存失效 |
| 目标与作用域 | Composable / Fragment + ImageView | 生命周期、取消和结果回写 |

这说明 Request 不是“一个 URL”，而是一张完整的执行说明书。

## 3.1.2 抽象请求管线

先不区分框架，一次请求可以拆成下面的状态流转：

```text
Created
  -> Attached to lifecycle and target
  -> Resolve size
  -> Build cache key
  -> Check memory cache
  -> Check disk cache
  -> Fetch source data
  -> Decode
  -> Transform
  -> Deliver result
  -> Complete

任意阶段都可能 -> Failed
等待或执行阶段都可能 -> Cancelled
```

这里有三个容易忽略的事实：

1. 尺寸必须在查找部分缓存前确定，因为尺寸可能参与 Key；
2. “取消”不是最后清理一下，而是要从 UI 目标一直传播到后台任务；
3. 成功结果返回前还要确认目标仍然属于这次请求。

## 3.1.3 核心角色不是名词表，而是责任边界

| 角色 | 持有什么 | 做什么决定 | 不应该负责什么 |
| --- | --- | --- | --- |
| Request | data、size、options、key、listener | 描述本次加载 | 不执行网络和解码 |
| Loader / Engine | 组件注册表、缓存、调度器 | 编排整条请求 | 不直接绘制 UI |
| Mapper / ModelLoader | 业务模型与数据类型规则 | 模型如何转成可读取数据 | 不解码像素 |
| Keyer | 模型与版本信息 | 如何生成稳定缓存标识 | 不决定缓存大小 |
| Fetcher / DataFetcher | 网络、文件或资源读取能力 | 原始字节从哪里来 | 不更新 ImageView |
| Decoder | 编码格式与目标尺寸 | 字节如何变成 Image / Bitmap | 不负责业务版本 |
| Transformation | 输入图片和输出规则 | 像素如何裁剪、圆角或模糊 | 不下载数据 |
| Target / Painter State | 当前请求与显示状态 | 结果显示到哪里 | 不拥有全局缓存 |

拆开这些角色的价值，是让“数据来源、解码格式、显示目标”可以独立变化。例如新增加密文件来源时，只需增加数据适配和读取能力，不必改动缓存、解码与 UI。

## 3.1.4 Coil 管线：组件如何被选中

Coil 官方把可扩展图片管线概括为五类组件：

```text
Interceptors -> Mappers -> Keyers -> Fetchers -> Decoders
```

把它放进完整请求后，可以理解为：

```text
AsyncImage / imageLoader.enqueue(request)
  -> ImageLoader 执行请求
  -> Interceptor 观察、改写、短路或重试
  -> Mapper 把自定义模型映射成框架认识的数据
  -> Keyer 为内存缓存生成稳定 Key
  -> 检查缓存
  -> Fetcher.Factory 判断是否能处理当前 data
  -> Fetcher 获取网络、文件或资源数据
  -> Decoder.Factory 判断是否支持当前数据格式
  -> Decoder 生成 Image
  -> Transformation 处理输出
  -> SuccessResult / ErrorResult
  -> AsyncImagePainter.State
```

选择组件时不是硬编码“URL 就调用某个类”，而是让注册表中的 Factory 依次判断是否支持当前数据和格式。这正是后面要讲的 Factory + Strategy：Factory 负责选，Strategy 负责做。

### 一个自定义模型如何进入管线

假设业务不希望 UI 直接传 URL，而是传 `ProductImage`：

```kotlin
data class ProductImage(
    val productId: String,
    val url: String,
    val version: Long
)
```

可以设计两个扩展：

```kotlin
class ProductImageMapper : Mapper<ProductImage, String> {
    override fun map(data: ProductImage, options: Options): String = data.url
}

class ProductImageKeyer : Keyer<ProductImage> {
    override fun key(data: ProductImage, options: Options): String {
        return "product:${data.productId}:${data.version}"
    }
}
```

Mapper 解决“怎样读取”，Keyer 解决“怎样识别”。如果只写 Mapper 而没有为自定义类型建立稳定 Key，网络能加载成功，但内存缓存行为可能不符合预期。

## 3.1.5 Glide 管线：请求与任务为什么分成两层

Glide 的关键链路可以先抓住下面这些对象：

```text
Glide.with(fragment)
  -> RequestManagerRetriever
  -> RequestManager
  -> RequestBuilder.into(imageView)
  -> SingleRequest
  -> Engine.load(...)
  -> Active Resources / Memory Cache
  -> EngineJob + DecodeJob
  -> Resource Cache / Data Cache / Source
  -> ModelLoader + DataFetcher
  -> Decoder + Transformation
  -> EngineResource
  -> SingleRequest.onResourceReady(...)
  -> Target.onResourceReady(...)
```

`SingleRequest` 和 `EngineJob` 看起来都表示“任务”，但责任不同：

- `SingleRequest` 面向调用者，保存 placeholder、error、Target 和请求状态；
- `EngineJob` 面向执行层，组织后台解码、取消和结果分发；
- `DecodeJob` 执行缓存查找、数据获取和解码阶段；
- `EngineResource` 包装结果并管理引用关系。

这种分层让一个执行结果可以服务请求回调，同时又不把 UI Target 交给底层解码器。

### 相同请求为什么可能共享正在执行的任务

Engine 会根据请求参数形成执行 Key，并记录正在运行的 Job。第二个等价请求到来时，如果已有可复用 Job，可以把新的回调加入该 Job；任务完成后再把结果分发给多个请求。这样做称为 in-flight request coalescing（飞行中请求合并）。

它和内存缓存不同：

```text
请求合并：结果还没生成，多个调用者等待同一个执行任务
内存缓存：结果已经生成，后续调用者直接读取结果
```

判断能否合并仍然依赖 Key。尺寸、变换或选项不同，通常不能视为完全相同的执行结果。

## 3.1.6 线程切换发生在哪里

图片请求至少跨越 UI 与后台执行两个区域：

```text
主线程
  构建请求 -> 绑定 Target -> 设置 placeholder -> 获取/等待目标尺寸

后台线程或挂起执行
  磁盘读取 -> 网络获取 -> 图片头解析 -> 解码 -> 像素变换 -> 写磁盘缓存

主线程
  校验请求仍有效 -> 更新 Painter State / Target -> 触发重组或重绘
```

内存缓存查询通常非常快，但磁盘、网络和大部分解码工作不能阻塞主线程。框架的价值不只是“用了线程池或协程”，而是把每个阶段放到合适执行环境，并让取消和异常能够跨阶段传播。

## 3.1.7 取消是怎样贯穿整条链路的

考虑用户在网络下载到 40% 时离开页面：

```text
Composable 离开 / Fragment 销毁 / 新请求进入同一 ImageView
  -> 当前 Request 标记取消或被清除
  -> 从 Target / Painter 解除绑定
  -> 从执行 Job 移除对应回调
  -> 没有其他消费者时取消 Fetch / Decode
  -> 禁止旧结果再次回写 UI
  -> 释放或归还已经获得的资源
```

“停止网络”只是取消的一部分。即使底层 I/O 不能立刻停下，结果回写前的有效性检查仍必须阻止旧结果污染新 UI。

## 3.1.8 用一个最小加载器验证理解

下面是教学用伪代码，重点是角色与顺序，不是生产实现：

```kotlin
suspend fun execute(request: ImageRequest): ImageResult {
    request.target.showLoading()

    val size = sizeResolver.resolve(request.target)
    val key = keyer.key(request.data, size, request.transformations)

    memoryCache[key]?.let { return deliverIfActive(request, it) }

    val encoded = diskCache[key]
        ?: fetcherFor(request.data).fetch(request.data)

    ensureActive()
    val decoded = decoderFor(encoded).decode(encoded, size)
    val transformed = request.transformations.fold(decoded) { image, transform ->
        transform.apply(image)
    }

    memoryCache[key] = transformed
    return deliverIfActive(request, transformed)
}
```

阅读这段代码时要主动寻找缺失项：磁盘缓存应该缓存原始数据还是变换结果？失败如何分类？多个相同请求如何合并？资源谁来释放？这些问题正是成熟框架比教学 Demo 复杂的原因。

## 3.1.9 本节检查点

读完后，尝试不看正文回答：

1. 为什么 Request 不能只保存 URL？
2. Factory 和 Strategy 在 Fetcher 选择过程中分别负责什么？
3. Glide 为什么同时需要 `SingleRequest`、`EngineJob` 和 `DecodeJob`？
4. 请求合并和内存缓存有什么本质区别？
5. 一次取消要影响哪些阶段，为什么只取消网络还不够？

[返回第三章总览](./index.md) | [继续：3.2 缓存系统与缓存 Key ->](./chapter3_2.md)

