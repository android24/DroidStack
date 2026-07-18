<script setup>
import ImagePipelineLab from '../.vitepress/theme/components/ImagePipelineLab.vue'
</script>

# 第 3.7 节：Image Pipeline Demo Lab

前六节负责建立原理，第 3.7 节负责把判断变成可观察结果。这里先用交互模拟器快速验证缓存、解码、列表竞态和失败链，再到主工程 [`demo/chapter03-image-pipeline`](https://github.com/android24/DroidStack/tree/main/demo/chapter03-image-pipeline) 运行 Coil、Glide 和竞态复现源码。

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

模拟器刻意保留了图片框架最核心的决策，不模拟具体框架的全部实现。它帮助建立假设；最终结论仍要在 Coil、Glide 和真实 Android 设备上验证。

## 3.7.3 挑战一：让同一 URL 连续走四条不同路径

目标：证明“URL 相同”不能决定缓存命中。

### 操作

1. 打开内存缓存和磁盘缓存，版本选 `v1`、尺寸选 `144 px`、变换选 `CenterCrop`；
2. 连续执行两次请求；
3. 点击“杀进程效果”，再执行一次；
4. 把尺寸改成 `600 px`，再执行一次；
5. 把版本改成 `v2`，再执行一次。

### 执行前先填写

| 请求 | 你的预测 | 实际来源 | 为什么 |
| --- | --- | --- | --- |
| 第一次 |  |  |  |
| 第二次 |  |  |  |
| 清内存后 |  |  |  |
| 修改尺寸后 |  |  |  |
| 修改版本后 |  |  |  |

<details>
<summary>展开参考推理</summary>

- 第一次全部未命中，进入网络；
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

目标：把“偶发错图”还原成确定的竞态。

### 操作

1. 打开“列表竞态”；
2. 不启用身份校验，先预测最终显示 A 还是 B；
3. 播放时间线；
4. 启用 Target 身份校验与旧请求清理；
5. 再播放一次，比较 `2000 ms` 时发生的变化。

<details>
<summary>展开参考推理</summary>

Request-B 虽然后发起，却先返回；Request-A 最后返回。如果没有取消和身份校验，A 会覆盖 B。启用所有权校验后，Request-A 已经不再拥有 Target，因此晚到结果必须被丢弃。

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

## 3.7.7 把模拟实验迁移到 Android

交互模拟器提供的是模型，Android Demo 要提供真实证据。配套源码位于 [`demo/chapter03-image-pipeline`](https://github.com/android24/DroidStack/tree/main/demo/chapter03-image-pipeline)，包含 Coil Lab、Glide Lab 和 Race Lab 三个入口。Demo 内置背包、耳机和相机三张图片：它们同时充当本地资源、网络模型的视觉参照、请求占位图与竞态结果，因此每次修改缓存 Key、目标尺寸或 Scale 都能直接从画面得到反馈。

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

## 3.7.8 Demo 验收清单

- [ ] 第一次加载、内存命中、磁盘命中和网络加载都能被明确观察；
- [ ] 修改尺寸、变换和版本后，能够解释 Key 与命中层变化；
- [ ] 4000 x 3000 与 144 x 144 的内存差异能够计算和测量；
- [ ] 快速滚动不会让旧请求覆盖新 item；
- [ ] 404、超时、损坏图片和不支持格式被分类到正确阶段；
- [ ] 页面离开后能观察到取消或旧结果不再交付；
- [ ] 日志不包含 token、完整敏感 URL 或用户隐私；
- [ ] 每个实验都有预测、实际结果和原因记录。

## 3.7.9 图片格式扩展关卡

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

[<- 上一节：3.6 框架比较、工程选型与综合实验](./chapter3_6.md) | [返回第三章总览](./index.md)
