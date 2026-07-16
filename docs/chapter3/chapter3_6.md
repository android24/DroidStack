# 第 3.6 节：框架比较、工程选型与综合实验

理解原理之后，选型不应变成“新项目用 Coil、老项目用 Glide”的口号。本节把业务需求映射到框架能力，并用一个可验收的综合实验结束第三章。

## 3.6.1 先比较设计重心

| 框架 | 设计重心 | 优势 | 代价与边界 |
| --- | --- | --- | --- |
| Coil 3.x | Kotlin、协程、Compose / Compose Multiplatform | API 与现代 Kotlin UI 自然结合，组件管线清晰 | 网络模块与多平台组件需按项目配置，传统 View 存量经验不如 Glide 普遍 |
| Glide 5.x | Android 媒体加载、View / 列表性能、缓存与资源复用 | View 体系成熟，ModelLoader、Decoder、Target 等扩展能力完整 | 内部体系较大，缓存、Target 与生命周期细节学习成本更高 |
| Picasso | 简单图片加载 API | 上手直接，维护旧项目容易 | 现代 Compose 和复杂媒体管线不是主要优势 |
| Fresco | 独立图片管线、Drawee 体系 | 适合已有 Fresco 基础或特殊管线需求 | 接入模型较重，与普通 ImageView / Compose 心智不同 |

框架版本会变化，选型时应重新核对最低 Android / Java / Kotlin 要求、维护状态、迁移指南和已知问题。本表比较的是长期设计重心，不代替版本验证。

## 3.6.2 按场景比较，而不是按功能数量比较

### 场景 A：全新 Compose 应用

优先考察：

- 是否能从布局约束推导请求尺寸；
- Loading / Success / Error 是否自然映射为 Compose State；
- 协程取消是否与 composition 作用域一致；
- 截图测试能否替换 ImageLoader；
- 是否需要 Compose Multiplatform。

Coil 通常更贴合这一组条件。

### 场景 B：大型 XML + RecyclerView 存量项目

优先考察：

- Target 与 View 复用是否成熟；
- 列表预加载与固定尺寸策略；
- Bitmap Pool 和资源复用；
- 已有 `AppGlideModule`、ModelLoader 和监控扩展；
- 迁移会影响多少页面。

已有 Glide 体系时，继续治理往往比全面迁移更有价值。

### 场景 C：同一业务模型有加密文件、网络和离线包三种来源

优先考察 Mapper / ModelLoader、Fetcher、Decoder 和 Keyer 是否能让三种来源进入同一请求管线。此时扩展模型比 API 简洁度更重要。

### 场景 D：图片内容敏感，不能长期落盘

重点不在框架名称，而在：

- 能否按请求关闭磁盘缓存；
- 缓存目录与清理策略是否合规；
- URL、Header、Key 是否泄露敏感信息；
- 日志和监控是否脱敏；
- 内存结果何时释放。

## 3.6.3 选型矩阵

先给各维度设置权重，再评分。示例：

| 维度 | 权重 | Coil | Glide | 评分依据 |
| --- | ---: | ---: | ---: | --- |
| Compose 体验 | 25 | 5 | 3 | 目标项目 90% 页面使用 Compose |
| View / RecyclerView | 15 | 4 | 5 | 仍有两个复杂列表模块 |
| 自定义数据源 | 15 | 5 | 5 | 两者都可扩展 |
| 团队经验 | 15 | 3 | 5 | 团队已有 Glide 组件 |
| 测试替换 | 10 | 5 | 4 | 需要稳定截图测试 |
| 迁移成本 | 20 | 2 | 5 | 当前已有大量 Glide 调用 |

评分本身不是答案。必须写出证据：现有调用数量、关键扩展、性能实验、构建影响和团队维护能力。不同项目即使分数相同，也可能因为不可妥协条件做出不同选择。

## 3.6.4 轻量封装应该统一什么

Compose 项目可以提供语义化入口：

```kotlin
@Composable
fun ProductImage(
    product: Product,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(product.coverUrl)
            .memoryCacheKey("product:${product.id}:${product.imageVersion}")
            .diskCacheKey("product:${product.id}:${product.imageVersion}")
            .build(),
        contentDescription = product.name,
        placeholder = painterResource(R.drawable.ic_image_placeholder),
        error = painterResource(R.drawable.ic_image_error),
        contentScale = contentScale,
        modifier = modifier
    )
}
```

View 项目可以提供场景扩展：

```kotlin
fun ImageView.loadProductCover(product: Product) {
    Glide.with(this)
        .load(product.coverUrl)
        .signature(ObjectKey(product.imageVersion))
        .placeholder(R.drawable.ic_image_placeholder)
        .error(R.drawable.ic_image_error)
        .centerCrop()
        .into(this)
}
```

适合统一：

- 缓存版本规则；
- 默认占位图与错误图；
- CDN 尺寸参数；
- 监控字段和脱敏规则；
- 商品图、头像等稳定场景语义；
- 测试替换入口。

不适合封死：

- 所有缓存策略；
- 所有 Transformation；
- 特殊 Target 和 Listener；
- GIF、SVG、视频帧等差异能力；
- 框架的完整高级 API。

## 3.6.5 观测能力必须进入工程设计

图片“加载慢”如果没有数据，只能靠猜。建议记录：

```text
业务场景：product_list / product_detail / avatar
请求身份：脱敏 model + version
目标尺寸：width x height
结果来源：memory / disk / network / local
耗时分段：size / fetch / decode / transform / total
结果：success / error / cancelled
错误类别：http / timeout / decode / unsupported / oom-risk
```

观测不等于把完整 URL 和 token 写进日志。生产日志必须脱敏，并控制采样率。

## 3.6.6 综合实验：Image Pipeline Lab

最终实验不是再写一遍 `AsyncImage`，而是完成一个可观察、可故障注入的商品图片实验室。

### 功能要求

1. 商品列表展示 100 条数据，图片尺寸固定；
2. 详情页加载更高分辨率图片；
3. 支持内存、磁盘、网络三种来源标记；
4. 可以修改 `imageVersion` 验证缓存失效；
5. 可以注入 200 ms、1 s、3 s 网络延迟；
6. 可以让指定请求失败或返回损坏图片；
7. 可以切换目标尺寸和 Transformation；
8. 展示请求耗时、来源、输出尺寸和估算内存；
9. 快速滚动时不得出现图片错位；
10. 离开页面后旧请求不得回写已销毁 UI。

### 实验面板

```text
[内存缓存 开/关] [磁盘缓存 开/关]
[网络延迟 0 / 200 / 1000 / 3000 ms]
[目标尺寸 72 / 144 / 600 / ORIGINAL]
[变换 NONE / CROP / ROUND / BLUR]
[失败率 0% / 20% / 100%]

当前请求：cover:42:7
数据来源：MEMORY
输出尺寸：144 x 144
估算内存：81 KiB
总耗时：4 ms
```

### 五轮验证

| 轮次 | 操作 | 应证明的机制 |
| --- | --- | --- |
| 1 | 首次进入、返回、杀进程后重进 | 网络、内存、磁盘缓存差异 |
| 2 | URL 不变，只修改 version | Key 版本化失效 |
| 3 | 原图与 144 px 目标对比 | 采样和内存差异 |
| 4 | 3 s 延迟下快速滚动 | Target 绑定与取消 |
| 5 | 修改尺寸和圆角 | 变换结果与缓存 Key 关系 |

## 3.6.7 验收标准

| 维度 | 基础完成 | 真正掌握 |
| --- | --- | --- |
| 主流程 | 能画出组件顺序 | 能解释每个分支的输入、决策与输出 |
| 缓存 | 能看到命中来源 | 能预测 Key 变化后命中哪一层 |
| 内存 | 会背 4 bytes / pixel | 能用实际尺寸估算并解释峰值 |
| 生命周期 | 不出现明显错图 | 能画竞态时间线并解释取消传播 |
| 设计模式 | 能列模式名称 | 能指出模式隔离的变化和运行时对象 |
| 源码 | 打开过仓库 | 用假设、断点和日志验证一条调用链 |
| 选型 | 说出推荐框架 | 用项目证据、权重和边界说明选择 |

## 3.6.8 常见错误复盘

| 现象 | 不充分的回答 | 应继续追查 |
| --- | --- | --- |
| 图片不更新 | “缓存问题” | Key 哪一部分没变，命中了哪一层 |
| 列表错图 | “异步问题” | 哪个 Request 在什么时间覆盖了哪个 Target |
| 图片 OOM | “原图太大” | 实际解码尺寸、Config、并发和峰值对象 |
| 离页仍请求 | “生命周期没处理” | 作用域传入什么，取消传播停在哪一层 |
| 首屏慢 | “网络慢” | fetch、decode、transform 哪一段慢，是否可预加载 |
| 框架难迁移 | “封装不够” | 业务污染点、扩展点和测试替换边界 |

## 3.6.9 本章总结

图片加载框架可以压缩成一条主线：

```text
Request 描述需求
  -> Lifecycle / Target 建立所有权
  -> Size 决定资源边界
  -> Key 决定缓存身份
  -> Cache 避免重复成本
  -> Fetcher 获取编码数据
  -> Decoder 生成像素资源
  -> Transformation 改变结果
  -> State / Target 安全更新 UI
  -> Clear / Cancel 终止任务并释放所有权
```

真正可迁移的知识不是某个 API，而是：用身份解决缓存，用尺寸控制内存，用所有权处理生命周期，用状态机约束异步，用可替换策略扩展管线，用实验验证源码判断。

## 3.6.10 官方资料

- [Coil 官方文档](https://coil-kt.github.io/coil/)
- [Coil Compose](https://coil-kt.github.io/coil/compose/)
- [Coil Image Loaders](https://coil-kt.github.io/coil/image_loaders/)
- [Coil Extending the Image Pipeline](https://coil-kt.github.io/coil/image_pipeline/)
- [Coil GitHub](https://github.com/coil-kt/coil)
- [Glide 官方文档](https://bumptech.github.io/glide/)
- [Glide Caching](https://bumptech.github.io/glide/doc/caching.html)
- [Glide Targets](https://bumptech.github.io/glide/doc/targets.html)
- [Glide Resource Reuse](https://bumptech.github.io/glide/doc/resourcereuse.html)
- [Glide GitHub](https://github.com/bumptech/glide)

[<- 上一节：3.5 设计模式与源码追踪](./chapter3_5.md) | [返回第三章总览](./index.md)
