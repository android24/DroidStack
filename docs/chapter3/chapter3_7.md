<script setup>
import ImagePipelineLab from '../.vitepress/theme/components/ImagePipelineLab.vue'
</script>

# 第 3.7 节：Image Pipeline Demo Lab

3.0 至 3.6 负责建立原理、比较框架并定义综合作业，第 3.7 节负责把其中已经实现的能力变成可观察结果。这里先用交互模拟器验证缓存、解码、列表竞态、失败链和设计模式，再到主工程 [`demo/chapter03-image-pipeline`](https://github.com/android24/DroidStack/tree/main/demo/chapter03-image-pipeline) 运行 Coil、Glide 和竞态复现源码。

## 3.7.1 实验规则：先预测，再执行

每轮实验都按同一套步骤进行：

```text
写下预测
  -> 修改一个变量
  -> 执行请求
  -> 观察主流程与结果来源
  -> 对照答案
  -> 用自己的话解释原因
```

一次只修改一个变量。否则看到结果变化时，无法判断究竟是版本、尺寸、变换还是缓存策略造成的。

## 3.7.2 交互实验台

<ImagePipelineLab />

这不是只计算一个“模拟耗时”的表单。网络请求会真实等待滑块指定的时间：等待期间只能看到模糊占位图、进度和持续增加的耗时，请求完成后清晰图片才会交付。列表竞态也会按真实先后顺序播放 ImageView 复用、B 返回、A 晚到三个关键瞬间。

实验台仍然只保留图片框架最核心的决策，不冒充真实网络和完整框架实现。它负责把因果关系演清楚；其中 Coil、Glide、404 和 Target 竞态可以继续到 [`demo/chapter03-image-pipeline`](https://github.com/android24/DroidStack/tree/main/demo/chapter03-image-pipeline) 验证，超时、损坏数据、100 条列表等内容属于 [3.6 综合作业](./chapter3_6.md#_3-6-6-综合作业蓝图-image-pipeline-lab) 的扩展任务。

## 3.7.3 挑战一：让同一 URL 连续走四条不同路径

目标：先亲眼看到网络延迟怎样影响占位图和图片交付，再证明“URL 相同”不能决定缓存命中。

### 操作

1. 点击“清空全部缓存”，把网络延迟调到 `3000 ms`，执行请求并观察图片何时从模糊占位图变清晰；
2. 不修改任何参数，立即执行第二次请求，对比内存命中与 3 秒网络等待；
3. 点击“杀进程效果”，再执行一次，观察 Resource Disk 是否仍然存在；
4. 把尺寸改成 `600 px`，再执行一次；
5. 把版本改成 `v2`，再执行一次；
6. 在一次 3 秒网络请求尚未完成时点击“取消当前请求”，确认旧结果不会继续交付。

### 执行前先填写

| 请求 | 你的预测 | 实际来源 | 为什么 |
| --- | --- | --- | --- |
| 第一次（3 秒延迟） |  |  |  |
| 第二次（参数不变） |  |  |  |
| 清内存后 |  |  |  |
| 修改尺寸后 |  |  |  |
| 修改版本后 |  |  |  |

<details>
<summary>展开参考推理</summary>

- 第一次全部未命中，模糊占位图至少保持设定的网络延迟，Fetcher 完成后才能解码和交付；
- 第二次完整资源 Key 相同，命中内存；
- 清内存后，变换后的资源仍在磁盘，命中 Resource Disk；
- 修改尺寸后，完整资源 Key 改变，但原始数据 Key 不变，因此可能命中 Data Disk 并重新解码；
- 修改版本后，原始数据与资源身份都改变，再次进入网络。

</details>

## 3.7.4 挑战二：把 45.8 MiB 变成几十 KiB

目标：建立“文件大小不等于 Bitmap 内存”的直觉。

### 操作

1. 打开“Bitmap 内存”；
2. 保持原图 `4000 x 3000`、`ARGB_8888`；
3. 依次输入解码尺寸 `4000 x 3000`、`1080 x 810`、`400 x 300`、`144 x 144`；
4. 再把 Config 改成 `RGB_565` 和 `RGBA_F16`；
5. 记录每次的目标内存与保留比例。

### 必须解释

```text
为什么磁盘上的 JPEG 只有几 MB，完整解码却接近 45.8 MiB？
为什么固定列表封面尺寸既降低内存，又提高缓存和 Pool 的复用概率？
为什么不能为了省内存，所有图片都强制使用 RGB_565？
```

<details>
<summary>展开参考推理</summary>

绘制使用的是解码后像素，常见 ARGB_8888 每像素占 4 字节。固定目标尺寸让框架在解码前完成采样，避免先分配原图像素再缩小。RGB_565 虽然减半内存，但颜色精度降低且不支持透明度，不能脱离图片质量要求全局使用。

</details>

## 3.7.5 挑战三：亲手制造一次图片错位

目标：理解列表错图不是“网络太慢”本身造成的，而是旧请求在 ImageView 已被复用后仍然拥有写入权。

实验中只有一个图片控件，但它会先后服务两个列表项：

```text
0 ms       ImageView 属于 row-12，开始加载背包 A（慢）
500 ms     用户快速滚动，同一个 ImageView 改属于 row-35，开始加载耳机 B（快）
1200 ms    B 先返回，row-35 正确显示耳机
2400 ms    A 后返回；若旧请求没取消，它会把耳机错误覆盖成背包
```

### 操作

1. 打开“列表竞态”；
2. 关闭安全模式，先预测 `row-35` 最后显示背包还是耳机；
3. 点击“播放滚动与回调”，同时观察 ImageView 所属行、两条请求进度和下方时间线；
4. 确认 `1200 ms` 时耳机已经正确出现，但 `2400 ms` 时又被旧背包覆盖；
5. 打开安全模式重新播放，观察 ImageView 复用时 Request-A 被取消，耳机保持不变。

<details>
<summary>展开参考推理</summary>

Request-B 虽然后发起，却先返回。真正的问题发生在 Request-A 最后完成时：回调只记得“把背包写进这个 ImageView”，却不知道这个控件已经属于 row-35。图片框架通常会在新的请求绑定 Target 时取消旧请求，并在交付前再次校验请求身份；两道防线共同阻止 A 覆盖 B。

</details>

## 3.7.6 挑战四：判断错误应该在哪里结束

目标：补全成功、失败、取消三条请求路径。

分别注入四种错误：

| 故障 | 产生阶段 | 是否进入 Decoder | 默认是否值得重试 |
| --- | --- | --- | --- |
| HTTP 404 | Fetcher | 否 | 通常否 |
| 网络超时 | Fetcher | 否 | 满足次数和退避约束时可以 |
| HTTP 200 + 损坏图片 | Decoder | 是，并在这里失败 | 通常不盲目重试同一内容 |
| 不支持的格式 | Decoder Factory / 组件选择 | 无可用 Decoder | 应增加组件或换格式 |

实验时先选择错误，再预测哪些阶段会被跳过。尤其观察“HTTP 成功”为什么不等于“图片请求成功”。

### 错误分类模型

```text
Transport Error
  DNS、连接、TLS、超时

Protocol Error
  HTTP 401、404、429、5xx

Data Error
  空响应、截断文件、损坏编码

Capability Error
  没有支持该格式的 Decoder

Lifecycle Result
  请求被取消或 Target 已失效，它不是业务失败
```

重试必须针对错误类型：404 重试十次仍然是 404；超时可能因临时网络恢复而成功；损坏数据如果已经进入磁盘缓存，还需要处理缓存污染，而不只是重新执行 Decoder。

## 3.7.7 挑战五：从一次请求中找出设计模式

目标：不背模式名称，而是说明每个模式隔离了什么变化，并把它定位到 Coil、Glide 的真实对象。

### 操作

1. 打开“设计模式”；
2. 依次点击 Request、Scope、执行链、Fetch、Decode 和 Target；
3. 先预测当前阶段最可能使用什么模式，再查看结果；
4. 对比 Coil 和 Glide：相同问题是否使用了相同结构；
5. 任选一个节点，按“源码验证点”在锁定版本中设置断点。

必须避免两种错误：不要把所有异步回调都简单叫 Observer，也不要因为 Glide 有多阶段加载就把它等同于 Coil 的 Interceptor Chain。模式用来解释对象协作，不能反过来强迫源码符合模式名称。

## 3.7.8 把模拟实验迁移到 Android

交互模拟器提供的是模型，Android Demo 为其中一部分机制提供真实证据。配套源码位于 [`demo/chapter03-image-pipeline`](https://github.com/android24/DroidStack/tree/main/demo/chapter03-image-pipeline)，当前包含 Coil Lab、Glide Lab 和 Race Lab 三个入口。Demo 内置背包、耳机和相机三张图片：它们同时充当本地资源、网络模型的视觉参照、请求占位图与竞态结果，因此修改缓存 Key、目标尺寸或 Scale 都能直接从画面得到反馈。

```bash
cd demo
./gradlew :chapter03-image-pipeline:assembleDebug
```

下面的状态和事件模型同时也是继续扩展 Demo 时的约定。

### 页面状态

```kotlin
data class LabSettings(
    val memoryCacheEnabled: Boolean = true,
    val diskCacheEnabled: Boolean = true,
    val imageVersion: Int = 1,
    val targetSizePx: Int = 144,
    val networkDelayMs: Long = 800,
    val failureMode: FailureMode = FailureMode.None,
    val transformation: LabTransformation = LabTransformation.Crop
)
```

### 必须记录的事件

```kotlin
data class ImageTrace(
    val requestId: String,
    val cacheKey: String,
    val targetSize: String,
    val stage: String,
    val dataSource: String?,
    val elapsedMs: Long,
    val result: String
)
```

### Coil 观测入口

```kotlin
val request = ImageRequest.Builder(context)
    .data(product.coverUrl)
    .memoryCacheKey("product:${product.id}:${settings.imageVersion}")
    .diskCacheKey("product:${product.id}:${settings.imageVersion}")
    .listener(
        onStart = { trace("START") },
        onSuccess = { _, result ->
            trace("SUCCESS", result.dataSource.name)
        },
        onError = { _, result ->
            trace("ERROR", result.throwable::class.simpleName)
        },
        onCancel = { trace("CANCEL") }
    )
    .build()
```

真实项目还可以增加 Interceptor 记录请求前后耗时；网络延迟和 HTTP 错误应由测试服务器或测试网络层注入，不能在生产代码中到处 `delay()`。

### Glide 观测入口

```kotlin
Glide.with(imageView)
    .load(product.coverUrl)
    .signature(ObjectKey(settings.imageVersion))
    .listener(object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>?,
            isFirstResource: Boolean
        ): Boolean {
            trace("ERROR", e?.rootCauses?.joinToString { it.javaClass.simpleName })
            return false
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>?,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            trace("SUCCESS", dataSource.name)
            return false
        }
    })
    .into(imageView)
```

Listener 用于观测结果，不应承担修改业务状态、发起无限重试或直接管理缓存的职责。

## 3.7.9 当前材料验收清单

- [ ] Web 实验台能观察网络等待、内存/磁盘命中、尺寸变化和取消；
- [ ] Web 实验台能计算 4000 x 3000 与 144 x 144 的内存差异；
- [ ] Web 与 Race Lab 都能复现不安全错图，并用 Target 所有权阻止覆盖；
- [ ] Web 实验台能区分 404、超时、损坏图片和不支持格式；
- [ ] 设计模式观察器能把阶段映射到模式、真实对象和源码验证点；
- [ ] Android Demo 能运行 Coil Lab、Glide Lab 和 Race Lab；
- [ ] Android Demo 能验证本地/网络、版本、尺寸、Crop/Fit 和 404；
- [ ] 每个已完成实验都有预测、实际结果和原因记录。

尚未列入当前 Demo 验收的 100 条列表、详情页、20% 失败率、真实超时与损坏数据注入，继续按 [3.6 综合作业蓝图](./chapter3_6.md#_3-6-6-综合作业蓝图-image-pipeline-lab) 实现。

## 3.7.10 图片格式扩展关卡

完成主实验后，可以逐步增加：

| 关卡 | 新问题 | 需要观察 |
| --- | --- | --- |
| EXIF 方向 | 文件宽高与显示方向不一致 | 旋转发生在何处，目标尺寸怎样计算 |
| GIF / 动图 | 一张图片包含多帧 | 帧缓存、播放生命周期和内存峰值 |
| SVG | 输入不是像素图 | Decoder 如何把矢量内容栅格化 |
| 视频帧 | 数据源不是普通图片 | ModelLoader / Fetcher / Decoder 如何组合 |
| 预加载 | 图片尚未进入视口 | 预加载距离、目标尺寸和浪费请求 |
| 缩略图协调 | 先低清后高清 | 两个请求如何协调，谁能覆盖谁 |

这些关卡不应一次全部塞进主流程。每增加一种格式，仍然沿用第二章的方法：先问问题，再找抽象、主流程、扩展点和边界。

## 3.7.11 本章总结

第三章从“为什么框架会变化”出发，最终落到一条可以预测、观察和验证的图片请求管线：

```text
历史压力提出问题
  -> Request 描述需求
  -> Size 与 Key 确定资源身份
  -> Cache / Fetcher / Decoder 完成数据处理
  -> Lifecycle / Target 管理所有权
  -> State 表达成功、失败与取消
  -> Factory、Strategy、Chain 等模式隔离变化
  -> 日志、断点和实验验证判断
  -> 项目约束决定框架选型
```

完成本章不等于记住 Coil 或 Glide 的全部 API，而是能够面对一张“加载慢、占内存、显示错误或无法更新”的图片时，沿着主流程提出可验证的问题，并用缓存来源、目标尺寸、请求身份、生命周期和错误阶段找到证据。

下一章进入网络请求底层框架。图片管线中的 Fetcher 会成为新的观察入口：图片框架把网络当作一种数据来源，而 OkHttp 等网络框架还要继续处理连接、协议、拦截器、缓存、重试与安全。

[<- 上一节：3.6 框架比较、工程选型与综合作业设计](./chapter3_6.md) | [返回第三章总览](./index.md)
