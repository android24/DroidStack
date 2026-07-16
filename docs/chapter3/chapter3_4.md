# 第 3.4 节：生命周期、列表复用与资源回收

图片错位不是 RecyclerView 的“偶发现象”，而是两个时间轴没有被正确关联：异步请求按自己的速度完成，View 却会被快速复用给新的数据。

## 3.4.1 用时间线复现错位

假设同一个 `ImageView` 先后绑定商品 A 与 B：

```text
t0  View 绑定 A，发起 Request-A
t1  用户快速滑动，View 被回收
t2  同一个 View 绑定 B，发起 Request-B
t3  Request-B 命中缓存，先显示 B
t4  Request-A 网络返回，晚到结果再次写入 View
t5  B 的位置显示成 A，发生错位
```

根因不是“异步太慢”，而是 Request-A 返回时没有验证：这个 Target 是否仍然属于 A。

## 3.4.2 框架如何把请求绑定到 Target

成熟框架会在 Target 或 View 关联位置记录当前 Request。概念上类似：

```kotlin
fun into(target: Target) {
    target.currentRequest?.clear()
    target.currentRequest = newRequest
    newRequest.begin()
}
```

新请求进入同一 Target 时，旧请求先被解除。结果回调也会检查请求状态，只有当前仍有效的请求才能更新目标。

这说明 `.into(imageView)` 不只是“最后把图设进去”，它同时建立了请求所有权。

## 3.4.3 为什么每次 `onBindViewHolder` 都要覆盖旧状态

错误写法：

```kotlin
if (product.coverUrl != null) {
    Glide.with(fragment)
        .load(product.coverUrl)
        .into(holder.cover)
}
```

当新 item 没有 URL 时，分支什么都不做，复用 View 会继续显示旧 item 的图片。

更完整的绑定：

```kotlin
fun bind(product: Product) {
    val imageView = binding.cover

    if (product.coverUrl.isBlank()) {
        Glide.with(imageView).clear(imageView)
        imageView.setImageResource(R.drawable.ic_product_placeholder)
        return
    }

    Glide.with(imageView)
        .load(product.coverUrl)
        .placeholder(R.drawable.ic_product_placeholder)
        .error(R.drawable.ic_product_error)
        .signature(ObjectKey(product.imageVersion))
        .into(imageView)
}
```

每次绑定都必须完整描述当前 item 的状态：有图、无图、加载中和失败，不能依赖 View 上一次留下的内容。

## 3.4.4 `onViewRecycled` 是否一定要 clear

新的请求进入同一 `ImageView` 时，Glide 会清理旧请求，因此正常绑定路径不必靠 `onViewRecycled` 才能防错位。但在以下情况显式清理仍有价值：

- item 被回收后长时间不再绑定；
- 使用自定义 Target；
- 某些分支不会发起新请求；
- 希望尽快释放资源或停止无意义工作。

```kotlin
override fun onViewRecycled(holder: ProductViewHolder) {
    Glide.with(holder.itemView).clear(holder.binding.cover)
    super.onViewRecycled(holder)
}
```

关键不是机械记住“必须写 clear”，而是确保 Target 的旧所有权一定会被新状态覆盖或显式解除。

## 3.4.5 `Glide.with(...)` 传入什么会改变生命周期

`Glide.with(fragment)` 的意义不只是获得 Context。它通过 RequestManager 把一组请求和 Fragment 生命周期关联：

```text
Fragment / Activity start
  -> 允许或恢复请求

Fragment / Activity stop
  -> 暂停适当请求

Fragment / Activity destroy
  -> 清理关联请求和 Target
```

如果传入 Application Context，请求管理器没有页面级生命周期可跟随，页面离开不会自动得到同样的停止与清理语义。页面图片应优先使用最接近真实 UI 生命周期的 Fragment、Activity 或 View 入口。

## 3.4.6 Compose 中没有 RecyclerView，为什么仍有身份问题

Compose 不复用传统 ViewHolder，但异步结果仍必须和当前 composition 中的 model 对应。需要关注三件事：

### model 是否稳定

每次重组都创建语义不同或包含随机字段的 model，会导致请求重启或缓存失效。数据类应正确表达内容身份。

### Lazy 列表 key 是否稳定

```kotlin
LazyColumn {
    items(
        items = products,
        key = { it.id }
    ) { product ->
        ProductRow(product)
    }
}
```

稳定 key 帮助 Compose 在列表变化时保持 item 身份。它不是图片缓存 Key，但两者都依赖“什么算同一个对象”的设计。

### 请求是否跟随 composable 离开

图片 painter / request 的执行应和 composition 作用域关联。Composable 离开、model 改变时，旧请求应停止影响当前 UI。不要在 composable 中另开一个无生命周期约束的全局协程下载 Bitmap 后直接回写状态。

## 3.4.7 生命周期和取消不是同一个概念

生命周期提供“何时应该改变请求状态”的信号；取消是具体执行动作。

```text
Lifecycle destroy（信号）
  -> RequestManager / Painter 发现作用域结束
  -> Request.clear / Job.cancel（动作）
  -> Fetch、Decode、Callback 停止或丢弃结果（后果）
```

框架还可能在 stop 时暂停请求，在 destroy 时彻底清理。暂停允许之后恢复，取消通常结束当前执行，两者语义不同。

## 3.4.8 资源回收的核心是所有权

一张图片可能同时被两个 ImageView 显示，也可能仍在内存缓存中。框架不能在第一个 View 清理时就释放像素。

Glide 的资源管理可以用引用关系理解：

```text
Target-A 使用资源 -> 引用 +1
Target-B 使用资源 -> 引用 +1
Target-A clear    -> 引用 -1
Target-B clear    -> 引用 -1
引用归零          -> 可进入缓存、Pool 或释放
```

实际实现细节以版本源码为准，但核心约束不变：只有确认没有消费者后，资源才可以安全复用。业务代码私自持有或 recycle 框架资源，会破坏这套所有权协议。

## 3.4.9 加载状态也需要完整生命周期

图片不是只有成功状态：

```text
Empty -> Loading -> Success
                 -> Error
Loading -> Cancelled / Cleared
Success -> Cleared
```

UI 应为各状态定义行为：

| 状态 | UI 行为 | 注意点 |
| --- | --- | --- |
| Empty | 初始占位 | 避免布局跳变 |
| Loading | 占位或轻量进度 | 列表中避免大量动画指示器 |
| Success | 展示结果 | 是否需要过渡效果 |
| Error | 错误图或重试入口 | 区分无数据与加载失败 |
| Cleared | 解除旧结果 | 不能继续持有已归还资源 |

“页面已经销毁但还在回调”通常就是状态机与生命周期脱节。

## 3.4.10 制造错位实验

先写一个故意有问题的教学加载器：

```kotlin
fun ImageView.loadBadly(url: String, scope: CoroutineScope) {
    scope.launch {
        val delayMs = if (url.endsWith("A.jpg")) 2_000L else 200L
        delay(delayMs)
        val bitmap = fetchAndDecode(url)
        setImageBitmap(bitmap)
    }
}
```

快速复用同一个 ImageView 加载 A、B，A 会晚到并覆盖 B。

再加入最小身份校验：

```kotlin
fun ImageView.loadWithIdentity(url: String, scope: CoroutineScope) {
    tag = url
    scope.launch {
        val bitmap = fetchAndDecode(url)
        if (tag == url) {
            setImageBitmap(bitmap)
        }
    }
}
```

这只能演示“结果校验”，还没有请求取消、缓存、尺寸解析和资源释放。随后换成 Coil 或 Glide，对比成熟框架还完成了哪些工作。

### 实验必须记录的时间线

```text
view identity
item identity
request identity
start time
completion time
cancel / clear time
whether result was delivered
```

只有画出时间线，才能把“偶尔错图”还原成确定的竞态条件。

## 3.4.11 本节检查点

1. 为什么旧请求即使不能立刻停止网络，也必须阻止结果回写？
2. `.into(imageView)` 除了显示图片，还建立了什么关系？
3. 无图片分支为什么也必须主动覆盖旧 View 状态？
4. Compose 的 item key 与图片缓存 Key 有什么共同点和区别？
5. 为什么框架管理的 Bitmap 不能由业务代码随意 recycle？

[<- 上一节：3.3 解码、尺寸与 Bitmap 内存](./chapter3_3.md) | [继续：3.5 设计模式与源码追踪 ->](./chapter3_5.md)
