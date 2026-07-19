# 第 3.5 节：设计模式与源码追踪

只把 `Builder、Strategy、Factory` 填进表格，仍然没有理解设计模式。真正的判断标准是：它把哪一类变化隔离了，运行时有哪些对象参与，去掉这个模式后哪部分代码会开始膨胀。阅读前可以先到 [设计模式观察器](./chapter3_7.md?lab=pattern#_3-7-2-交互实验台) 点击 Request、Scope、Chain、Fetch、Decode 和 Target，带着运行时对象返回正文。

## 3.5.1 把模式放回一次请求

先看完整协作图：

```text
业务调用
  -> Facade 提供简单入口
  -> Builder 收集请求参数
  -> Lifecycle Owner 决定请求作用域
  -> Chain 让多个处理阶段依次执行
  -> Factory 根据 data / format 选择组件
  -> Adapter / Mapper 统一不同模型
  -> Strategy 执行获取、解码、变换和缓存策略
  -> State Machine 保存请求状态
  -> Observer / Target 接收异步结果
```

这张图比“一个 API 对应一个模式”更接近真实框架：同一个类可能承担多种设计职责，一个模式也可能由多个对象共同完成。

## 3.5.2 Builder：隔离可选参数的组合爆炸

一次请求可能包含 URL、尺寸、缩放、缓存策略、变换、placeholder、listener、headers 和生命周期。如果使用构造函数：

```kotlin
ImageRequest(url, width, height, crop, cache, transform, placeholder, ...)
```

参数顺序难读，也无法优雅表达“只配置其中三项”。Builder 把构建过程和最终请求分开：

```kotlin
val request = ImageRequest.Builder(context)
    .data(product.coverUrl)
    .size(144, 144)
    .crossfade(true)
    .listener(...)
    .build()
```

深入观察点：

- Builder 可以是可变的，构建出的 Request 应是稳定快照；
- 默认值通常在 build 前合并；
- 参数之间可能有约束，例如某些 Transformation 不支持硬件 Bitmap；
- Request 的稳定性直接影响缓存 Key 与请求比较。

所以 Builder 解决的不只是“链式调用好看”，而是复杂配置的构建、校验与冻结。

## 3.5.3 Facade：隐藏复杂度，但不消灭复杂度

`AsyncImage(...)` 和 `Glide.with(...).load(...).into(...)` 都提供了业务友好的门面。门面背后仍然存在缓存、调度和解码系统。

Facade 的边界是：

```text
对常见路径足够简单
对高级能力仍保留进入点
不把所有内部对象暴露给业务
不假装底层错误和状态不存在
```

项目封装 `AppImage` 时也在创建自己的 Facade。封装得太厚会把框架扩展点全部遮住，封装得太薄又无法统一版本 Key、占位图和监控。

## 3.5.4 Factory + Strategy：为什么经常一起出现

以数据获取为例：

```text
String URL -> Network Fetcher
File       -> File Fetcher
Uri        -> ContentResolver Fetcher
Resource   -> Resource Fetcher
```

Strategy 说明“有多种可替换获取算法”；Factory 说明“根据当前模型创建或选择哪一个策略”。

伪代码：

```kotlin
val factory = fetcherFactories.firstOrNull { it.handles(data) }
    ?: error("No fetcher for ${data::class}")

val fetcher = factory.create(data, options, imageLoader)
val source = fetcher.fetch()
```

如果只有 Strategy 没有 Factory，业务代码仍需要到处写 `when(data)`；如果只有 Factory 没有统一策略接口，创建出来的对象也无法被同一条管线调用。

同样组合还出现在 Decoder、Transformation、ModelLoader 等扩展点。

## 3.5.5 Adapter / Mapper：统一模型，不等于修改业务对象

Glide 的 `ModelLoader<Model, Data>`、Coil 的 Mapper 都在解决模型适配：把 URL、File、Uri 或自定义业务对象转换成后续组件能处理的数据。

```text
ProductImage
  -> Mapper / ModelLoader
  -> URL / InputStream / ByteBuffer / Source
  -> Decoder
```

Adapter 的价值是保持业务模型与底层读取协议解耦。业务对象不需要实现“打开网络流”这种基础设施职责。

## 3.5.6 Chain of Responsibility：Coil Interceptor 怎样继续或短路

Coil 的 Interceptor 设计受 OkHttp 启发。每个拦截器可以：

- 观察请求与结果；
- 修改请求后继续；
- 直接返回缓存结果并短路；
- 捕获失败并决定是否重试；
- 在调用前后统计耗时。

```kotlin
class TimingInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val start = System.nanoTime()
        return try {
            chain.proceed(chain.request)
        } finally {
            val elapsed = System.nanoTime() - start
            recordImageLoad(chain.request, elapsed)
        }
    }
}
```

`chain.proceed()` 是责任链的关键：调用它表示把控制权交给下一节点；不调用并直接返回，则表示当前节点处理完毕。

责任链的风险也很明确：顺序会影响语义，重复调用 `proceed()` 可能触发多次执行，吞掉异常会让上层误判成功。扩展时必须写清前置条件、后置条件和失败约定。

## 3.5.7 Observer、Target 与状态机如何协作

图片请求是异步的，调用者不能同步拿到最终结果。Target、Listener 或 Painter State 负责接收变化：

```text
Request State Machine
  WaitingForSize
  Running
  Complete
  Failed
  Cleared
       |
       v
Observer / Target / Painter State
       |
       v
ImageView 更新 / Compose 重组 / 监控埋点
```

Observer 解决“变化如何通知”，状态机解决“哪些变化合法”。例如已经 Cleared 的请求不能再次进入 Complete 并更新 UI。

## 3.5.8 Lifecycle-aware 不是 GoF 模式

生命周期感知是一种架构能力，不属于经典 GoF 设计模式。课程把它放入模式地图，是因为它同样组织对象协作，但不应为了凑模式名称，把所有机制都硬套成 Observer。

准确描述应是：

```text
RequestManager 观察生命周期事件
  -> 根据事件暂停、恢复或清理请求
  -> 请求状态机执行对应转换
```

其中可能使用观察者思想，但“Lifecycle-aware”描述的是更高层的行为约束。

## 3.5.9 Coil 源码怎样读到下一层

建议锁定项目实际使用版本的源码 tag，从一条最小请求开始：

```text
AsyncImage
  -> rememberAsyncImagePainter
  -> ImageRequest
  -> ImageLoader.execute / enqueue
  -> RealImageLoader
  -> Interceptor chain
  -> EngineInterceptor
  -> ComponentRegistry
  -> Mapper / Keyer / Fetcher / Decoder
  -> ImageResult
  -> AsyncImagePainter state
```

不要只“顺着 Ctrl+Click 看类”。每到一层记录四件事：

| 观察项 | 要回答的问题 |
| --- | --- |
| 输入 | 上一层传入了什么对象 |
| 决策 | 当前层根据什么条件分支 |
| 状态 | 哪些字段在这里改变 |
| 输出 | 下一层收到什么，异常如何传递 |

### 推荐断点任务

1. 在请求执行入口观察 Request 中的 data、size、cache policy；
2. 在内存缓存读取处记录实际 Key；
3. 在组件选择处观察哪个 Fetcher / Decoder Factory 返回支持；
4. 在结果构建处记录 data source；
5. 离开页面，观察取消从 painter 传播到执行任务。

## 3.5.10 Glide 源码怎样读到执行层

公共入口到执行层的主线通常是：

```text
Glide.with(...)
  -> RequestManagerRetriever
  -> RequestManager
  -> RequestBuilder.into(...)
  -> SingleRequest.begin()
  -> Engine.load(...)
  -> ActiveResources / MemoryCache
  -> EngineJob / DecodeJob
  -> ResourceCache / DataCache / Source
  -> Registry 中选择 ModelLoader 与 Decoder
  -> EngineResource
  -> SingleRequest.onResourceReady(...)
  -> Target.onResourceReady(...)
```

Glide 5 仍在演进，内部方法名与组织方式应以对应 release tag 为准。阅读时抓住稳定问题：谁管理生命周期、谁保存请求状态、谁做缓存决策、谁执行解码、谁拥有结果。

### 推荐断点任务

1. `RequestBuilder.into(...)`：观察 Target 与旧 Request 如何关联；
2. `SingleRequest.begin()`：观察状态检查和 placeholder；
3. `Engine.load(...)`：观察 Key、活跃资源、内存缓存和运行中任务；
4. `DecodeJob` 各阶段：观察 Resource Cache、Data Cache、Source 的切换；
5. `onResourceReady(...)`：观察结果为何能或不能交给 Target；
6. `clear(...)`：观察引用关系和资源释放路径。

## 3.5.11 一次源码实验必须有假设

不要漫无目的单步调试。先提出可证伪假设：

```text
假设：第二次加载相同 URL、尺寸和变换时，会在内存缓存阶段返回，
因此不会进入 Fetcher 和 Decoder。
```

然后设计验证：

1. 在缓存、Fetcher、Decoder 各下断点；
2. 第一次加载并记录调用；
3. 保持进程存活，第二次加载；
4. 修改尺寸或版本，再加载；
5. 比较断点路径和数据来源。

结论必须写成“哪些条件成立时会怎样”，而不是笼统地说“框架有缓存”。

## 3.5.12 模式地图

| 模式 / 思想 | Coil | Glide | 隔离的变化 |
| --- | --- | --- | --- |
| Builder | `ImageRequest.Builder`、`ImageLoader.Builder` | `RequestBuilder`、Options | 可选请求参数 |
| Facade | `AsyncImage`、`ImageLoader` | `Glide.with()` | 业务入口复杂度 |
| Factory | Fetcher / Decoder Factory | ModelLoader Factory、Registry | 组件选择与创建 |
| Strategy | Fetcher、Decoder、Transformation | DataFetcher、Decoder、Transformation | 获取、解码和像素算法 |
| Adapter / Mapper | Mapper、组件适配 | ModelLoader | 数据模型差异 |
| Chain of Responsibility | Interceptor Chain | 多阶段加载管线 | 阶段扩展与短路 |
| Observer / Callback | Listener、Painter State | Target、RequestListener | 异步结果通知 |
| State Machine | 请求与 painter 状态 | `SingleRequest` 状态 | 异步状态合法转换 |
| Resource Pool | 平台与实现相关复用 | Bitmap Pool | 大块像素内存复用 |

## 3.5.13 本节检查点

1. Builder 除了链式 API，还负责哪两个关键动作？
2. Factory 和 Strategy 为什么经常同时出现？
3. `chain.proceed()` 在责任链中意味着什么？
4. Observer 和状态机分别解决什么问题？
5. 为什么源码阅读必须先提出一个可证伪假设？

<details>
<summary>检查答案</summary>

1. Builder 负责组合、校验并冻结复杂请求参数；
2. Factory 决定选择谁，Strategy 负责具体执行；
3. `proceed()` 把控制权交给责任链下一节点，不调用则表示当前节点短路；
4. Observer 传递变化，状态机约束哪些状态转换合法；
5. 假设决定断点和观测条件，才能用结果证实或推翻判断。

</details>

[<- 上一节：3.4 生命周期、列表复用与资源回收](./chapter3_4.md) | [进入 Demo：设计模式观察器](./chapter3_7.md?lab=pattern#_3-7-2-交互实验台) | [继续：3.6 框架比较、工程选型与综合作业设计 ->](./chapter3_6.md)
