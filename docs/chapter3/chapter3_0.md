<script setup>
import FrameworkEvolutionTimeline from '../.vitepress/theme/components/FrameworkEvolutionTimeline.vue'
</script>

# 第 3.0 节：从 Universal Image Loader 到 Coil

Android 图片加载框架的演进，通常可以从早期广泛使用的 **Universal Image Loader（UIL）** 讲起。从 UIL 到 Picasso、Glide、Fresco，再到 Coil，变化的并不只是 API 从 Java 换成 Kotlin，也不是新框架简单淘汰旧框架。

真正变化的是 Android 应用不断提出新的要求：

```text
先把图片异步加载出来
  -> 在列表中稳定、快速地加载
  -> 控制 Bitmap 内存与页面生命周期
  -> 支持 GIF、视频帧、渐进图和自定义数据源
  -> 适应 Kotlin、协程与声明式 UI
  -> 在 Compose Multiplatform 中复用同一套图片管线
```

这一节不是框架编年表，而是第三章的“问题来源图”。后面学习的请求管线、缓存、解码、生命周期和设计模式，都是这段演进留下来的答案。

先不要急着记年份。点击下面五个阶段，只观察三件事：当时新增了什么工程压力、框架把什么抽象成稳定对象、主流程因此怎样变化。

<FrameworkEvolutionTimeline />

## 3.0.1 框架出现以前：每个项目都有一个自己的 ImageLoader

早期 Android 项目经常直接组合这些能力：

```text
URL
  -> Thread / AsyncTask
  -> HttpURLConnection
  -> byte[] / InputStream
  -> BitmapFactory.decodeStream()
  -> Handler 切回主线程
  -> ImageView.setImageBitmap()
```

这条链路能显示图片，却很快暴露出系统性问题：

- 相同 URL 被重复下载；
- 原图直接解码导致 OOM；
- ListView 复用 View 后出现错图；
- Activity 退出后任务仍然回调；
- 内存缓存、磁盘缓存和线程池散落在业务代码中；
- 每个项目都重新处理重试、占位图、失败图和取消。

因此，第一代框架要解决的问题非常直接：**把每个项目重复编写的下载、线程、解码与缓存代码收拢成一个可复用组件。**

## 3.0.2 2011：UIL 把“可配置加载器”带进项目

Universal Image Loader 的核心形态是一个全局 `ImageLoader`，配合完整的初始化配置和每次显示选项：

```java
ImageLoader imageLoader = ImageLoader.getInstance();
imageLoader.displayImage(imageUrl, imageView, displayOptions);
```

它一次性提供了多线程加载、下载器、解码器、内存缓存、磁盘缓存、加载监听和进度监听。对当时的 Android 项目而言，这意味着图片加载第一次从零散工具代码变成了可以统一配置的基础设施。

UIL 解决的是：

```text
不要让每个页面重新管理 Thread + Handler + BitmapFactory + Cache
```

它的历史价值很高，但时代边界也很明显：

- 配置中心偏全局，页面与请求的责任边界不够突出；
- 回调和 View 身份需要开发者保持警惕；
- 生命周期通常需要在页面代码中主动衔接；
- 功能集中在一个大型加载器中，扩展点与模块边界没有后来框架清晰。

UIL 官方仓库把自己称为现代图片库的“祖先”，并明确记录维护周期为 `2011-11-27` 到 `2015-11-27`。这也是一个重要提醒：**框架选型不仅看能力，还要看维护状态。**

## 3.0.3 2013：Picasso 把一次加载变成一条请求声明

Picasso 让最常见的图片加载收敛成一句流畅 API：

```java
Picasso.get()
    .load(imageUrl)
    .resize(144, 144)
    .centerCrop()
    .into(imageView);
```

表面变化是代码更短，深层变化是：**框架的中心从“配置一个全局加载器”转向“描述一次完整请求”。**

Picasso 把下面这些行为纳入请求模型：

- 自动处理 Adapter 中的 `ImageView` 复用与旧请求取消；
- 等待 View 测量完成后再确定目标尺寸；
- 合并相同 Key 的并发请求；
- 在主线程之外调度下载与解码；
- 用占位图、错误图和调试色带反馈状态与数据来源。

Picasso 1.0 在 2013 年发布；同年的 2.0 已经加入专用调度线程、请求合并、根据目标尺寸避免解码超大图片等能力。它回答的问题是：

```text
图片加载不只是“调用一个工具”，而是创建一个有身份、有尺寸、可取消的请求
```

这条思想会直接延续到本章的 [3.1 请求管线](./chapter3_1.md)、[3.2 缓存 Key](./chapter3_2.md) 和 [3.4 Target 所有权](./chapter3_4.md)。

## 3.0.4 2014 前后：Glide 把目标从“加载成功”改成“滚动流畅”

当图片越来越多地出现在 Feed、图库和瀑布流中，问题不再只是能否加载，而是：

```text
连续滚动几十张图片时，能否少分配、少 GC、少卡顿，并及时停止无用请求？
```

Glide 因此把设计重心放在媒体管线和资源效率上：

- 根据 Target 尺寸自动降采样；
- 通过内存缓存、磁盘缓存与 Bitmap Pool 减少重复工作；
- 复用 Bitmap、字节数组等资源，降低 GC 与堆碎片；
- 支持图片、GIF 和视频帧，而不只把输入看成静态 Bitmap；
- 用 `Glide.with(activityOrFragment)` 建立 RequestManager 与生命周期的关系；
- 通过 ModelLoader、Decoder、Encoder 等组件支持新的模型和媒体格式。

```java
Glide.with(fragment)
    .load(imageUrl)
    .centerCrop()
    .into(imageView);
```

这里最值得注意的不是链式 API，而是 `with(fragment)`：调用者不仅提供 Context，还提供了请求的生命周期所有者。Glide 官方把平滑列表滚动、自动降采样、资源池和生命周期集成都列为核心设计重点。

Glide 解决的是：

```text
一次请求不能只对最终图片负责，还要对执行成本、资源复用和生命周期负责
```

这正是 [3.3 Bitmap 内存](./chapter3_3.md) 与 [3.4 生命周期和资源回收](./chapter3_4.md) 的历史来源。

## 3.0.5 2015：Fresco 把“图片管线”本身变成产品能力

Facebook 在 2015 年发布 Fresco 时，面对的是图片密集型应用、低内存设备和慢网络。Fresco 没有只强调一个更短的 `load()` API，而是明确提出：**它不只是 Loader，而是一条 Image Pipeline。**

它重点处理：

- 编码数据、解码结果与磁盘数据的多级缓存；
- 渐进式 JPEG，让慢网络用户在下载完成前先看到逐步清晰的内容；
- 旧 Android 版本上的特殊内存管理；
- 用引用计数控制共享图片资源的生命周期；
- 用 DraweeHierarchy、DraweeController 和 DraweeView 管理占位、失败、渐变与离屏释放。

Fresco 的变化可以概括为：

```text
图片加载已经复杂到不能只围绕 ImageView 设计，必须显式建模整条流水线和资源所有权
```

代价也很明确：更多概念、更强的 View 体系介入和更高的接入理解成本。因此 Fresco 不是 Glide 的“下一代替代品”，而是针对重图片场景发展出的另一条分支。

## 3.0.6 2019 至今：Coil 跟随 Kotlin、协程与 Compose 改变边界

Coil 的名字来自 **Coroutine Image Loader**。它没有发明缓存、尺寸解析或请求取消，而是把成熟的图片管线重新放进现代 Kotlin 技术栈：

```kotlin
imageView.load(imageUrl) {
    crossfade(true)
}
```

在 Compose 中，请求状态又从 View 回调变成声明式 UI 状态：

```kotlin
AsyncImage(
    model = imageUrl,
    contentDescription = null
)
```

这次变化由宿主生态推动：

- Kotlin 成为 Android 主流语言，需要更自然的 DSL、空安全和扩展函数；
- 协程提供结构化并发、挂起和取消传播；
- Compose 不再以 `ImageView` 作为唯一 Target，需要 Painter、State 与重组语义；
- 应用希望自行选择 OkHttp 或 Ktor，而不是让图片库绑定唯一网络实现；
- Kotlin Multiplatform 和 Compose Multiplatform 要求管线不再只围绕 Android 类设计。

Coil 1.0 于 2020 年发布，1.3 在 2021 年加入 Compose 支持；Coil 3.0 于 2024 年发布并完整支持 Compose Multiplatform，同时把网络能力拆成可选择的 OkHttp、Ktor 模块。

Coil 回答的是：

```text
成熟图片管线怎样融入 Kotlin、结构化并发、声明式 UI 与多平台工程
```

## 3.0.7 到底是什么推动框架持续变化

框架并不是因为“旧 API 不够时髦”而变化。每一轮变化背后都有可观察的工程压力。

| 变化驱动力 | 早期问题 | 框架设计如何响应 | 对应后文 |
| --- | --- | --- | --- |
| 业务规模 | 单页几张图片变成无限列表和图库 | 请求合并、优先级、预加载、取消 | [3.1](./chapter3_1.md) |
| 缓存正确性 | 只按 URL 缓存，更新和变换容易错乱 | 数据 Key、资源 Key、多级缓存 | [3.2](./chapter3_2.md) |
| 设备与媒体 | 小内存设备开始显示高清图、GIF 和视频帧 | 降采样、Bitmap Pool、渐进解码、格式组件 | [3.3](./chapter3_3.md) |
| UI 模型 | ListView 复用、Fragment 生命周期、Compose 重组 | Target 身份、生命周期取消、Painter State | [3.4](./chapter3_4.md) |
| 扩展需求 | 网络栈、数据源和格式越来越多 | Factory、Strategy、Registry、Interceptor | [3.5](./chapter3_5.md) |
| 工程生态 | Java 回调转向 Kotlin、协程与多平台 | DSL、挂起、结构化并发、模块化网络 | [3.6](./chapter3_6.md) |

可以把历史压缩成六次关注点迁移：

```text
UIL       统一能力：不要重复造下载、线程、缓存
Picasso   请求语义：一次加载要有尺寸、身份与取消
Glide     资源效率：列表要流畅，资源要复用，生命周期要接管
Fresco    管线治理：重图片场景需要渐进显示和显式资源管理
Coil 1/2  Kotlin 化：协程、DSL、轻量组件与 Compose 状态
Coil 3    平台解耦：网络可插拔，图片管线走向 Compose Multiplatform
```

## 3.0.8 同一个商品封面，API 为什么越来越接近 UI 语义

把同一需求放进不同年代，可以看见抽象中心的移动。

### UIL：告诉全局加载器显示图片

```java
ImageLoader.getInstance().displayImage(url, imageView, options);
```

中心对象是 `ImageLoader`，大量行为来自全局配置和 `DisplayImageOptions`。

### Picasso：描述一次目标明确的请求

```java
Picasso.get().load(url).resize(144, 144).centerCrop().into(imageView);
```

中心对象变成请求：数据、尺寸、变换和 Target 被放在同一条声明中。

### Glide：让请求进入生命周期感知的资源引擎

```java
Glide.with(fragment).load(url).override(144, 144).centerCrop().into(imageView);
```

请求不仅描述结果，还进入生命周期、缓存、解码、任务共享和资源复用系统。

### Coil：让图片成为声明式 UI 状态的一部分

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalPlatformContext.current)
        .data(url)
        .size(144, 144)
        .build(),
    contentDescription = null
)
```

Target 不再必然是 `ImageView`；请求输入、Painter 状态、布局约束和组合生命周期共同决定结果。

## 3.0.9 这不是一条“后者全面胜过前者”的排行榜

技术史最容易犯的错误，是把演进写成下面这样：

```text
UIL < Picasso < Glide < Fresco < Coil
```

真实情况更接近不同约束下的分支选择：

- Picasso 强调简单、可读和常见图片请求；
- Glide 强调 Android View 体系、复杂媒体、生命周期和资源效率；
- Fresco 强调重图片业务、渐进式管线和显式内存治理；
- Coil 强调 Kotlin、协程、Compose、可插拔组件和多平台；
- 存量项目的迁移成本、团队经验与已有扩展同样属于选型条件。

因此，“现在有 Coil，是否所有项目都应立刻迁移”不是一个正确问题。更好的问题是：

```text
当前项目的 UI 技术栈、媒体类型、扩展需求、性能风险和维护周期是什么？
候选框架的设计重心是否与这些约束一致？
迁移能够消除什么真实问题，又会引入什么成本？
```

## 3.0.10 历史侦探：从四段日志判断框架关注点

假设同一张商品封面加载失败，四个项目留下了不同日志：

```text
项目 A：ImageLoaderConfiguration / DisplayImageOptions / MemoryCache
项目 B：RequestCreator / Dispatcher / BitmapHunter / Target
项目 C：RequestManager / EngineJob / DecodeJob / BitmapPool
项目 D：ImageRequest / Interceptor / Fetcher / Decoder / AsyncImagePainter
```

不要只回答框架名称，还要解释它们的关注点：

| 项目 | 更可能对应 | 从命名看出的设计重心 |
| --- | --- | --- |
| A | UIL | 全局配置、显示选项和统一缓存 |
| B | Picasso | 请求调度、任务执行与 Target |
| C | Glide | 生命周期入口、共享任务、解码阶段与资源池 |
| D | Coil | 不可变请求、责任链、组件选择与 Compose 状态 |

这个练习会在 [3.5 设计模式与源码追踪](./chapter3_5.md) 中继续：类名不是用来背诵的，它们暴露了框架如何拆分责任。

## 3.0.11 变化很多，但主流程从未消失

无论 API 看起来多么不同，一次图片请求仍然绕不开：

```text
描述请求
  -> 确定尺寸与缓存 Key
  -> 查找内存和磁盘
  -> 获取编码数据
  -> 解码与变换
  -> 校验 Target / UI 身份
  -> 交付结果或结束失败、取消分支
  -> 管理资源所有权
```

所以本章不会按 UIL、Picasso、Glide、Fresco、Coil 各写一份 API 手册。接下来的章节按稳定问题域组织，再把不同框架放到同一条主流程上比较。

## 3.0.12 本节检查点

读完后，尝试不看正文回答：

1. UIL 的历史贡献为什么不能只概括为“支持缓存”？
2. Picasso 把什么从全局配置推进到了单次请求语义？
3. Glide 为什么需要生命周期和 Bitmap Pool，而不只是更大的缓存？
4. Fresco 为什么称自己为 Pipeline，而不只是 Loader？
5. Coil 的变化中，哪些来自图片领域，哪些来自 Kotlin、Compose 与多平台生态？
6. 为什么“新框架一定全面优于旧框架”是错误的选型方法？

<details>
<summary>展开检查答案</summary>

1. UIL 的贡献是把下载、调度、解码、缓存和回调收拢为统一基础设施，而不只是增加一个缓存容器；
2. Picasso 把尺寸、变换、Target、取消和请求合并推进到一次 Request 的语义中；
3. Glide 面对的是连续列表中的分配、GC、生命周期和大块像素复用问题，单纯扩大缓存并不能解决这些问题；
4. Fresco 显式建模多级缓存、Producer、渐进显示和资源所有权，所以强调的是可治理 Pipeline；
5. 缓存、解码、尺寸属于图片领域；协程取消、Compose State、可插拔网络和多平台边界来自 Kotlin 与 UI 生态；
6. 框架设计重心不同，存量扩展、团队经验、迁移成本和业务约束都会改变最终选择。

</details>

## 3.0.13 官方资料

- [Universal Image Loader 官方仓库](https://github.com/nostra13/Android-Universal-Image-Loader)
- [Picasso 官方介绍](https://square.github.io/picasso/)与 [Picasso Changelog](https://github.com/square/picasso/blob/master/CHANGELOG.md)
- [Glide 官方文档](https://bumptech.github.io/glide/)
- [Fresco 发布说明](https://engineering.fb.com/2015/03/26/android/introducing-fresco-a-new-image-library-for-android/)与 [Image Pipeline 文档](https://frescolib.org/docs/intro-image-pipeline.html)
- [Coil 官方介绍](https://coil-kt.github.io/coil/)、[Changelog](https://coil-kt.github.io/coil/changelog/)与 [Coil 3 升级说明](https://coil-kt.github.io/coil/upgrading_to_coil3/)

[返回第三章总览](./index.md) | [继续：3.1 请求管线与并发调度 ->](./chapter3_1.md)
