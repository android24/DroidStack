<script setup lang="ts">
import { withBase } from 'vitepress'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

type Mode = 'cache' | 'memory' | 'race' | 'failure' | 'pattern'
type StageState = 'idle' | 'hit' | 'work' | 'skip' | 'error'
type CachePhase = 'idle' | 'lookup' | 'fetching' | 'decoding' | 'success' | 'cancelled'

interface Stage {
  name: string
  detail: string
  state: StageState
}

interface Product {
  id: string
  name: string
  image: string
}

interface PatternNode {
  id: string
  label: string
  responsibility: string
  pattern: string
  category: string
  isolatedChange: string
  coil: string
  glide: string
  verification: string
}

const products: Product[] = [
  {
    id: 'backpack',
    name: '森林背包',
    image: withBase('/images/chapter3/lab/product_backpack.jpg')
  },
  {
    id: 'headphones',
    name: '珊瑚耳机',
    image: withBase('/images/chapter3/lab/product_headphones.jpg')
  },
  {
    id: 'camera',
    name: '黄色相机',
    image: withBase('/images/chapter3/lab/product_camera.jpg')
  }
]

const mode = ref<Mode>('cache')
const selectedProductId = ref(products[0].id)
const memoryCacheEnabled = ref(true)
const diskCacheEnabled = ref(true)
const version = ref(1)
const targetSize = ref(144)
const transformation = ref('crop')
const latency = ref(800)
const memoryKeys = ref<string[]>([])
const dataDiskKeys = ref<string[]>([])
const resourceDiskKeys = ref<string[]>([])
const cacheRun = ref(0)
const cacheResult = ref('尚未执行请求')
const cacheSource = ref('-')
const cacheDuration = ref(0)
const cacheStages = ref<Stage[]>([])
const cachePhase = ref<CachePhase>('idle')
const cacheElapsed = ref(0)
const cacheExpectedDuration = ref(0)
const cachePendingSource = ref('-')
let cacheClock: ReturnType<typeof setInterval> | undefined
let cacheCompletion: ReturnType<typeof setTimeout> | undefined

const originalWidth = ref(4000)
const originalHeight = ref(3000)
const bitmapConfig = ref(4)
const decodedWidth = ref(144)
const decodedHeight = ref(144)
const decodedCanvas = ref<HTMLCanvasElement | null>(null)
const canvasReady = ref(false)

const guardOldRequest = ref(false)
const racePlayed = ref(false)
const raceRunning = ref(false)
const raceElapsed = ref(0)
const raceStep = ref(-1)
const raceGuardSnapshot = ref(false)
let raceClock: ReturnType<typeof setInterval> | undefined
let raceTimers: Array<ReturnType<typeof setTimeout>> = []

const failureType = ref('http404')
const retryEnabled = ref(false)
const failurePlayed = ref(false)

const patternNodes: PatternNode[] = [
  {
    id: 'request',
    label: 'Request',
    responsibility: '收集 data、尺寸、变换、缓存策略和生命周期信息，并冻结为一次稳定请求。',
    pattern: 'Builder',
    category: '创建型模式',
    isolatedChange: '隔离可选参数的组合、默认值合并和构建前校验。',
    coil: 'ImageRequest.Builder -> ImageRequest',
    glide: 'RequestBuilder + RequestOptions',
    verification: '修改 size 或 transformation，观察构建后的请求快照与缓存 Key 是否同步变化。'
  },
  {
    id: 'lifecycle',
    label: 'Scope',
    responsibility: '决定请求何时开始、暂停、取消，以及结果是否仍允许交付。',
    pattern: '生命周期所有权',
    category: '架构机制，不强行归入 GoF',
    isolatedChange: '隔离页面存活状态与后台工作的取消传播。',
    coil: 'ImageLoader.enqueue / Disposable / Compose painter state',
    glide: 'RequestManagerRetriever -> RequestManager',
    verification: '请求未完成时离开页面，观察执行任务是否取消、Target 是否停止接收结果。'
  },
  {
    id: 'chain',
    label: 'Chain',
    responsibility: '让请求依次经过多个处理节点，并允许某一节点修改、短路或继续执行。',
    pattern: 'Chain of Responsibility',
    category: '行为型模式',
    isolatedChange: '隔离日志、缓存、尺寸和执行策略等横向处理步骤。',
    coil: 'RealInterceptorChain -> Interceptor -> proceed()',
    glide: '多阶段 Engine / DecodeJob 流程，但不等同于 Interceptor Chain',
    verification: '在 Coil Interceptor 前后记录请求；不调用 proceed()，验证责任链在当前节点短路。'
  },
  {
    id: 'fetch',
    label: 'Fetch',
    responsibility: '根据 URL、文件、资源或业务模型选择数据获取实现，并返回编码数据。',
    pattern: 'Factory + Strategy',
    category: '创建型 + 行为型模式',
    isolatedChange: '隔离数据模型匹配、组件创建与具体获取算法。',
    coil: 'Fetcher.Factory 选择 Fetcher',
    glide: 'ModelLoaderFactory / ModelLoader -> DataFetcher',
    verification: '同一页面分别加载 drawable 和 HTTPS URL，记录最终选择的 Fetcher / DataFetcher。'
  },
  {
    id: 'decode',
    label: 'Decode',
    responsibility: '识别输入格式，按目标尺寸把编码数据转换成可显示资源。',
    pattern: 'Factory + Strategy',
    category: '创建型 + 行为型模式',
    isolatedChange: '隔离格式探测、Decoder 选择和不同像素生成算法。',
    coil: 'Decoder.Factory 选择 Decoder',
    glide: 'Registry 选择 ResourceDecoder',
    verification: '分别输入 JPEG 与不支持格式，在组件选择处观察命中哪个 Decoder 或为何没有候选。'
  },
  {
    id: 'target',
    label: 'Target',
    responsibility: '保存请求身份、接收状态与结果，并决定是否还能更新当前 UI。',
    pattern: 'Observer + State Machine',
    category: '行为模式与状态约束',
    isolatedChange: '隔离异步结果通知、合法状态迁移和 UI 交付方式。',
    coil: 'EventListener / ImageResult / AsyncImagePainter.State',
    glide: 'SingleRequest state -> Target / RequestListener',
    verification: '复用同一 ImageView 后让旧请求晚到，检查请求身份与状态为何阻止旧结果交付。'
  }
]

const selectedPatternId = ref(patternNodes[0].id)
const selectedPattern = computed(
  () => patternNodes.find((node) => node.id === selectedPatternId.value) ?? patternNodes[0]
)

const selectedProduct = computed(
  () => products.find((product) => product.id === selectedProductId.value) ?? products[0]
)

const resourceKey = computed(() =>
  `${selectedProduct.value.id}:v${version.value}:${targetSize.value}px:${transformation.value}`
)
const dataKey = computed(
  () => `https://img.example/${selectedProduct.value.id}.jpg:v${version.value}`
)

const decodedBytes = computed(
  () => decodedWidth.value * decodedHeight.value * bitmapConfig.value
)
const originalBytes = computed(
  () => originalWidth.value * originalHeight.value * bitmapConfig.value
)
const memoryRatio = computed(() =>
  originalBytes.value === 0 ? 0 : decodedBytes.value / originalBytes.value
)

const formattedDecodedMemory = computed(() => formatBytes(decodedBytes.value))
const formattedOriginalMemory = computed(() => formatBytes(originalBytes.value))

const cachePreviewStyle = computed(() => ({
  objectFit: transformation.value === 'none' ? 'contain' : 'cover',
  borderRadius: transformation.value === 'round-16' ? '16px' : '0',
  filter: transformation.value === 'blur-12' ? 'blur(5px)' : 'none'
}))

const isCacheRunning = computed(() =>
  ['lookup', 'fetching', 'decoding'].includes(cachePhase.value)
)

const cacheProgress = computed(() => {
  if (!isCacheRunning.value || cacheExpectedDuration.value <= 0) {
    return cachePhase.value === 'success' ? 100 : 0
  }
  return Math.min(100, (cacheElapsed.value / cacheExpectedDuration.value) * 100)
})

const cacheStatusLabel = computed(() => {
  const labels: Record<CachePhase, string> = {
    idle: '占位图',
    lookup: '查找缓存',
    fetching: '网络加载中',
    decoding: '解码中',
    success: cacheSource.value,
    cancelled: '已取消'
  }
  return labels[cachePhase.value]
})

const cacheLiveDetail = computed(() => {
  if (cachePhase.value === 'fetching') {
    return `图片仍未交付：${cacheElapsed.value} / ${cacheExpectedDuration.value} ms`
  }
  if (cachePhase.value === 'lookup') return `正在从 ${cachePendingSource.value} 读取资源`
  if (cachePhase.value === 'decoding') return '已获得编码数据，正在生成目标尺寸 Bitmap'
  return cacheResult.value
})

const raceFinalProduct = computed(() => {
  if (!racePlayed.value) return products[2]
  if (raceStep.value === 0) return products[0]
  if (raceStep.value === 1) return products[2]
  if (raceStep.value === 2) return products[1]
  return raceGuardSnapshot.value ? products[1] : products[0]
})

const raceTargetOwner = computed(() =>
  !racePlayed.value
    ? '尚未绑定列表项'
    : raceStep.value >= 1
      ? 'row-35 · 商品 B'
      : 'row-12 · 商品 A'
)

const raceTargetStatus = computed(() => {
  if (!racePlayed.value) return '等待播放'
  if (raceStep.value === 0) return 'A 请求已启动'
  if (raceStep.value === 1) return 'ImageView 已复用，等待 B'
  if (raceStep.value === 2) return 'B 已正确显示'
  return raceGuardSnapshot.value ? 'A 已取消，保持 B' : 'A 晚到并错误覆盖 B'
})

const raceAProgress = computed(() => {
  if (!racePlayed.value) return 0
  if (raceGuardSnapshot.value && raceElapsed.value >= 500) return (500 / 2400) * 100
  return Math.min(100, (raceElapsed.value / 2400) * 100)
})

const raceBProgress = computed(() => {
  if (raceElapsed.value < 500) return 0
  return Math.min(100, ((raceElapsed.value - 500) / 700) * 100)
})

const failureVisual = computed(() => {
  if (!failurePlayed.value) {
    return { status: '等待请求', detail: '当前图片作为 placeholder', state: 'idle' }
  }
  if (failureType.value === 'timeout' && retryEnabled.value) {
    return { status: '重试成功', detail: '退避后重新获取并交付图片', state: 'success' }
  }
  const labels: Record<string, { status: string; detail: string }> = {
    http404: { status: 'HTTP 404', detail: 'Fetcher 失败，Decoder 不执行' },
    timeout: { status: '网络超时', detail: '没有可交付的编码数据' },
    corrupt: { status: '解码失败', detail: '获得字节，但无法生成 Bitmap' },
    unsupported: { status: '格式不支持', detail: '没有 Decoder 能处理该内容' }
  }
  return { ...labels[failureType.value], state: 'error' }
})

const raceTimeline = computed(() => {
  const safe = racePlayed.value ? raceGuardSnapshot.value : guardOldRequest.value
  return [
    { time: '0 ms', event: 'row-12 绑定背包，Request-A 开始', state: 'A' },
    {
      time: '500 ms',
      event: safe
        ? '快速滚动：同一 ImageView 改绑 row-35，并取消 Request-A'
        : '快速滚动：同一 ImageView 改绑 row-35，但 Request-A 仍在后台运行',
      state: 'B'
    },
    { time: '1200 ms', event: 'Request-B 返回，耳机显示在 row-35', state: 'B' },
    {
      time: '2400 ms',
      event: safe
        ? 'Request-A 已取消，不再交付；row-35 保持耳机'
        : 'Request-A 晚到，把 row-35 的耳机错误覆盖成背包',
      state: safe ? 'safe' : 'wrong'
    }
  ]
})

const failureStages = computed<Stage[]>(() => {
  if (!failurePlayed.value) return []

  if (failureType.value === 'http404') {
    return [
      stage('Request', '请求建立', 'work'),
      stage('Memory / Disk', '未命中', 'skip'),
      stage('Fetcher', 'HTTP 404，不应盲目重试', 'error'),
      stage('Decoder', '没有输入数据', 'skip'),
      stage('Target', '显示 error，记录 http 类错误', 'work')
    ]
  }

  if (failureType.value === 'timeout') {
    return [
      stage('Request', '请求建立', 'work'),
      stage('Memory / Disk', '未命中', 'skip'),
      stage(
        'Fetcher',
        retryEnabled.value ? '首次超时，退避后重试一次' : '网络超时，直接失败',
        'error'
      ),
      stage('Decoder', retryEnabled.value ? '重试成功，解码图片' : '没有输入数据', retryEnabled.value ? 'work' : 'skip'),
      stage('Target', retryEnabled.value ? '交付重试结果' : '显示 error', 'work')
    ]
  }

  if (failureType.value === 'corrupt') {
    return [
      stage('Request', '请求建立', 'work'),
      stage('Fetcher', 'HTTP 200，获得损坏字节', 'hit'),
      stage('Data Disk', '原始字节可能已写入缓存', 'work'),
      stage('Decoder', '无法识别图片内容', 'error'),
      stage('Target', '显示 error，记录 decode 类错误', 'work')
    ]
  }

  return [
    stage('Request', '请求建立', 'work'),
    stage('Fetcher', '成功获得编码数据', 'hit'),
    stage('Decoder Factory', '没有组件支持该格式', 'error'),
    stage('Decoder', '没有可执行策略', 'skip'),
    stage('Target', '显示 fallback / error，记录 unsupported', 'work')
  ]
})

function stage(name: string, detail: string, state: StageState): Stage {
  return { name, detail, state }
}

function formatBytes(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)} MiB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KiB`
  return `${bytes} B`
}

function has(list: string[], key: string): boolean {
  return list.includes(key)
}

function add(list: string[], key: string): string[] {
  return has(list, key) ? list : [...list, key]
}

function stopCacheTimers() {
  if (cacheClock) clearInterval(cacheClock)
  if (cacheCompletion) clearTimeout(cacheCompletion)
  cacheClock = undefined
  cacheCompletion = undefined
}

function resetCachePresentation(message: string) {
  stopCacheTimers()
  cachePhase.value = 'idle'
  cacheElapsed.value = 0
  cacheExpectedDuration.value = 0
  cachePendingSource.value = '-'
  cacheDuration.value = 0
  cacheSource.value = '-'
  cacheStages.value = []
  cacheResult.value = message
}

function runCacheRequest() {
  if (isCacheRunning.value) return

  const currentResourceKey = resourceKey.value
  const currentDataKey = dataKey.value
  const decodeCost = Math.max(6, Math.round((targetSize.value * targetSize.value) / 6000))
  let source = 'NETWORK'
  let duration = latency.value + 360 + decodeCost
  let result = '全部未命中，执行网络获取、解码和缓存写入'
  let stages: Stage[] = []

  if (memoryCacheEnabled.value && has(memoryKeys.value, currentResourceKey)) {
    source = 'MEMORY'
    duration = 180
    result = '命中完整资源，不再读取磁盘、网络和解码'
    stages = [
      stage('Size + Key', currentResourceKey, 'work'),
      stage('Memory', '命中', 'hit'),
      stage('Disk', '跳过', 'skip'),
      stage('Fetcher', '跳过', 'skip'),
      stage('Decoder', '跳过', 'skip'),
      stage('Target', '交付结果', 'work')
    ]
  } else if (diskCacheEnabled.value && has(resourceDiskKeys.value, currentResourceKey)) {
    source = 'RESOURCE_DISK'
    duration = 520
    result = '命中已变换资源，读取磁盘后直接交付'
    stages = [
      stage('Size + Key', currentResourceKey, 'work'),
      stage('Memory', '未命中', 'skip'),
      stage('Resource Disk', '命中', 'hit'),
      stage('Fetcher', '跳过', 'skip'),
      stage('Decoder', '跳过', 'skip'),
      stage('Target', '交付结果', 'work')
    ]
  } else if (diskCacheEnabled.value && has(dataDiskKeys.value, currentDataKey)) {
    source = 'DATA_DISK'
    duration = 680 + decodeCost
    result = '原始数据命中，但尺寸或变换不同，需要重新解码'
    stages = [
      stage('Size + Key', currentResourceKey, 'work'),
      stage('Memory', '未命中', 'skip'),
      stage('Resource Disk', '未命中', 'skip'),
      stage('Data Disk', '命中', 'hit'),
      stage('Decoder', `解码到 ${targetSize.value}px`, 'work'),
      stage('Target', '交付结果', 'work')
    ]
  } else {
    stages = [
      stage('Size + Key', currentResourceKey, 'work'),
      stage('Memory', '未命中', 'skip'),
      stage('Resource Disk', '未命中', 'skip'),
      stage('Data Disk', '未命中', 'skip'),
      stage('Fetcher', `真实等待 ${latency.value} ms`, 'work'),
      stage('Decoder', `解码到 ${targetSize.value}px`, 'work'),
      stage('Target', '交付结果', 'work')
    ]
  }

  cacheRun.value += 1
  cacheSource.value = '-'
  cachePendingSource.value = source
  cacheExpectedDuration.value = duration
  cacheElapsed.value = 0
  cacheDuration.value = 0
  cacheResult.value = result
  cacheStages.value = stages
  cachePhase.value = source === 'NETWORK'
    ? 'fetching'
    : source === 'DATA_DISK'
      ? 'decoding'
      : 'lookup'

  const startedAt = Date.now()
  cacheClock = setInterval(() => {
    cacheElapsed.value = Math.min(duration, Date.now() - startedAt)
  }, 40)

  cacheCompletion = setTimeout(() => {
    stopCacheTimers()
    cacheElapsed.value = duration
    cacheDuration.value = duration
    cacheSource.value = source
    cachePhase.value = 'success'

    if (source === 'NETWORK' && diskCacheEnabled.value) {
      dataDiskKeys.value = add(dataDiskKeys.value, currentDataKey)
      resourceDiskKeys.value = add(resourceDiskKeys.value, currentResourceKey)
    }
    if (source === 'DATA_DISK' && diskCacheEnabled.value) {
      resourceDiskKeys.value = add(resourceDiskKeys.value, currentResourceKey)
    }
    if (memoryCacheEnabled.value) {
      memoryKeys.value = add(memoryKeys.value, currentResourceKey)
    }
  }, duration)
}

function cancelCacheRequest() {
  if (!isCacheRunning.value) return
  stopCacheTimers()
  cacheDuration.value = cacheElapsed.value
  cacheSource.value = 'CANCELLED'
  cachePhase.value = 'cancelled'
  cacheResult.value = 'Target 离开页面，请求在图片交付前被取消，结果不会写入当前 Target'
  cacheStages.value = [
    ...cacheStages.value.filter((item) => item.name !== 'Target'),
    stage('Cancel', `${cacheElapsed.value} ms 时取消`, 'error'),
    stage('Target', '不交付旧结果', 'skip')
  ]
}

function clearMemory() {
  if (isCacheRunning.value) return
  memoryKeys.value = []
  resetCachePresentation('内存缓存已清空，磁盘缓存仍保留')
}

function clearAllCaches() {
  if (isCacheRunning.value) return
  memoryKeys.value = []
  dataDiskKeys.value = []
  resourceDiskKeys.value = []
  resetCachePresentation('内存与磁盘缓存均已清空')
}

function selectProduct(product: Product) {
  if (isCacheRunning.value) return
  selectedProductId.value = product.id
  resetCachePresentation(`已切换为${product.name}，它拥有独立的数据 Key 与资源 Key`)
  failurePlayed.value = false
}

function playRace() {
  stopRaceTimers()
  racePlayed.value = true
  raceRunning.value = true
  raceElapsed.value = 0
  raceStep.value = 0
  raceGuardSnapshot.value = guardOldRequest.value
  const startedAt = Date.now()

  raceClock = setInterval(() => {
    raceElapsed.value = Math.min(2400, Date.now() - startedAt)
  }, 40)

  raceTimers = [
    setTimeout(() => { raceStep.value = 1 }, 500),
    setTimeout(() => { raceStep.value = 2 }, 1200),
    setTimeout(() => {
      raceStep.value = 3
      raceElapsed.value = 2400
      raceRunning.value = false
      if (raceClock) clearInterval(raceClock)
      raceClock = undefined
    }, 2400)
  ]
}

function stopRaceTimers() {
  if (raceClock) clearInterval(raceClock)
  raceTimers.forEach((timer) => clearTimeout(timer))
  raceClock = undefined
  raceTimers = []
  raceRunning.value = false
}

function playFailure() {
  failurePlayed.value = true
}

function renderDecodedCanvas() {
  if (!canvasReady.value || !decodedCanvas.value) return

  const canvas = decodedCanvas.value
  const width = Math.max(1, Math.min(800, decodedWidth.value))
  const height = Math.max(1, Math.min(800, decodedHeight.value))
  const image = new Image()
  image.onload = () => {
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) return
    context.imageSmoothingEnabled = true
    context.drawImage(image, 0, 0, width, height)
  }
  image.src = selectedProduct.value.image
}

onMounted(() => {
  const requestedMode = new URLSearchParams(window.location.search).get('lab')
  if (['cache', 'memory', 'race', 'failure', 'pattern'].includes(requestedMode ?? '')) {
    mode.value = requestedMode as Mode
  }
  canvasReady.value = true
  renderDecodedCanvas()
})

watch(
  [mode, selectedProductId, decodedWidth, decodedHeight],
  () => nextTick(renderDecodedCanvas)
)

watch(mode, () => {
  if (mode.value !== 'cache' && isCacheRunning.value) cancelCacheRequest()
  if (mode.value !== 'race' && raceRunning.value) stopRaceTimers()
})

onBeforeUnmount(() => {
  stopCacheTimers()
  stopRaceTimers()
})
</script>

<template>
  <section class="pipeline-lab" aria-label="图片加载管线交互实验">
    <div class="mode-tabs" role="tablist" aria-label="选择实验">
      <button type="button" role="tab" :aria-selected="mode === 'cache'" :class="{ active: mode === 'cache' }" @click="mode = 'cache'">
        缓存管线
      </button>
      <button type="button" role="tab" :aria-selected="mode === 'memory'" :class="{ active: mode === 'memory' }" @click="mode = 'memory'">
        Bitmap 内存
      </button>
      <button type="button" role="tab" :aria-selected="mode === 'race'" :class="{ active: mode === 'race' }" @click="mode = 'race'">
        列表竞态
      </button>
      <button type="button" role="tab" :aria-selected="mode === 'failure'" :class="{ active: mode === 'failure' }" @click="mode = 'failure'">
        失败链
      </button>
      <button type="button" role="tab" :aria-selected="mode === 'pattern'" :class="{ active: mode === 'pattern' }" @click="mode = 'pattern'">
        设计模式
      </button>
    </div>

    <div v-if="mode !== 'race' && mode !== 'pattern'" class="product-picker" aria-label="选择实验图片">
      <span>实验图片</span>
      <button
        v-for="product in products"
        :key="product.id"
        type="button"
        :disabled="isCacheRunning"
        :aria-pressed="selectedProductId === product.id"
        :class="{ selected: selectedProductId === product.id }"
        @click="selectProduct(product)"
      >
        <img :src="product.image" :alt="product.name">
        <strong>{{ product.name }}</strong>
      </button>
    </div>

    <div v-if="mode === 'cache'" class="lab-content">
      <fieldset class="controls-grid" :disabled="isCacheRunning">
        <label class="switch-control">
          <input v-model="memoryCacheEnabled" type="checkbox">
          <span>内存缓存</span>
        </label>
        <label class="switch-control">
          <input v-model="diskCacheEnabled" type="checkbox">
          <span>磁盘缓存</span>
        </label>
        <label>
          图片版本
          <select v-model="version">
            <option :value="1">v1</option>
            <option :value="2">v2</option>
            <option :value="3">v3</option>
          </select>
        </label>
        <label>
          目标尺寸
          <select v-model="targetSize">
            <option :value="72">72 px</option>
            <option :value="144">144 px</option>
            <option :value="600">600 px</option>
            <option :value="4000">原始宽度</option>
          </select>
        </label>
        <label>
          图片变换
          <select v-model="transformation">
            <option value="none">无</option>
            <option value="crop">CenterCrop</option>
            <option value="round-16">圆角 16 px</option>
            <option value="blur-12">模糊 12 px</option>
          </select>
        </label>
        <label>
          网络延迟：{{ latency }} ms
          <input v-model.number="latency" type="range" min="0" max="3000" step="200">
        </label>
      </fieldset>

      <div class="action-row">
        <button v-if="!isCacheRunning" type="button" class="primary-action" @click="runCacheRequest">
          执行第 {{ cacheRun + 1 }} 次请求
        </button>
        <button v-else type="button" class="cancel-action" @click="cancelCacheRequest">
          取消当前请求
        </button>
        <button type="button" :disabled="isCacheRunning" @click="clearMemory">杀进程效果</button>
        <button type="button" :disabled="isCacheRunning" @click="clearAllCaches">清空全部缓存</button>
      </div>

      <figure class="image-result cache-preview" :data-phase="cachePhase">
        <div class="image-frame cache-image-frame">
          <img
            :src="selectedProduct.image"
            :alt="`${selectedProduct.name}缓存实验结果`"
            :style="cachePreviewStyle"
            class="preview-image"
            :class="{ 'is-ready': cachePhase === 'success' }"
          >
          <div v-if="cachePhase !== 'success'" class="loading-visual" aria-live="polite">
            <span v-if="isCacheRunning" class="loading-spinner" aria-hidden="true"></span>
            <strong>{{ cacheStatusLabel }}</strong>
            <span v-if="isCacheRunning">{{ cacheElapsed }} ms</span>
          </div>
          <span class="status-badge">{{ cacheStatusLabel }}</span>
          <span class="size-badge">{{ targetSize }} px · {{ transformation }}</span>
          <div v-if="isCacheRunning" class="transfer-progress" aria-hidden="true">
            <span :style="{ width: `${cacheProgress}%` }"></span>
          </div>
        </div>
        <figcaption>
          <strong>{{ selectedProduct.name }}</strong>
          <span>{{ cacheLiveDetail }}</span>
          <span v-if="cachePendingSource === 'NETWORK' && isCacheRunning" class="latency-note">
            调高“网络延迟”后，占位图会保持更久，清晰图片只在请求完成后出现。
          </span>
        </figcaption>
      </figure>

      <div class="metrics-row" aria-live="polite">
        <div><span>数据来源</span><strong>{{ cacheSource }}</strong></div>
        <div><span>{{ isCacheRunning ? '已等待' : '实际等待' }}</span><strong>{{ isCacheRunning ? cacheElapsed : cacheDuration }} ms</strong></div>
        <div><span>资源 Key</span><strong>{{ resourceKey }}</strong></div>
      </div>

      <ol v-if="cacheStages.length" class="pipeline-flow">
        <li v-for="item in cacheStages" :key="`${cacheRun}-${item.name}`" :data-state="item.state">
          <strong>{{ item.name }}</strong>
          <span>{{ item.detail }}</span>
        </li>
      </ol>
      <p class="result-line">{{ cacheResult }}</p>
    </div>

    <div v-else-if="mode === 'memory'" class="lab-content">
      <div class="controls-grid memory-controls">
        <label>原图宽度 <input v-model.number="originalWidth" type="number" min="1" max="12000"></label>
        <label>原图高度 <input v-model.number="originalHeight" type="number" min="1" max="12000"></label>
        <label>解码宽度 <input v-model.number="decodedWidth" type="number" min="1" max="12000"></label>
        <label>解码高度 <input v-model.number="decodedHeight" type="number" min="1" max="12000"></label>
        <label>
          Bitmap Config
          <select v-model="bitmapConfig">
            <option :value="4">ARGB_8888 · 4 B/px</option>
            <option :value="2">RGB_565 · 2 B/px</option>
            <option :value="8">RGBA_F16 · 8 B/px</option>
          </select>
        </label>
      </div>

      <div class="memory-visuals">
        <figure class="image-result">
          <div class="image-frame square-frame">
            <img :src="selectedProduct.image" :alt="`${selectedProduct.name}原始图片`">
            <span class="status-badge">原图</span>
          </div>
          <figcaption>
            <strong>{{ originalWidth }} x {{ originalHeight }}</strong>
            <span>{{ formattedOriginalMemory }}</span>
          </figcaption>
        </figure>
        <figure class="image-result">
          <div class="image-frame square-frame decoded-frame">
            <canvas ref="decodedCanvas" aria-label="目标尺寸 Bitmap 放大预览"></canvas>
            <span class="status-badge">解码后放大</span>
          </div>
          <figcaption>
            <strong>{{ decodedWidth }} x {{ decodedHeight }}</strong>
            <span>{{ formattedDecodedMemory }}</span>
          </figcaption>
        </figure>
      </div>

      <div class="metrics-row">
        <div><span>原尺寸解码</span><strong>{{ formattedOriginalMemory }}</strong></div>
        <div><span>目标尺寸解码</span><strong>{{ formattedDecodedMemory }}</strong></div>
        <div><span>保留比例</span><strong>{{ (memoryRatio * 100).toFixed(2) }}%</strong></div>
      </div>

      <div class="memory-bar" aria-label="目标尺寸占原始尺寸的内存比例">
        <span :style="{ width: `${Math.max(1, Math.min(100, memoryRatio * 100))}%` }"></span>
      </div>
      <p class="result-line">
        计算：{{ decodedWidth }} x {{ decodedHeight }} x {{ bitmapConfig }} B = {{ decodedBytes.toLocaleString() }} B
      </p>
    </div>

    <div v-else-if="mode === 'race'" class="lab-content">
      <div class="race-explainer">
        <strong>场景：快速滚动让同一个 ImageView 被复用</strong>
        <span>它先属于 row-12 的背包，500 ms 后改为 row-35 的耳机。问题不是谁先请求，而是谁在回调发生时仍然拥有这个 Target。</span>
      </div>

      <div class="race-stage" :data-state="racePlayed ? (raceStep >= 3 ? (raceGuardSnapshot ? 'safe' : 'wrong') : 'running') : 'idle'">
        <div class="phone-list" aria-label="被复用的列表图片槽位">
          <div class="phone-bar"><span></span><span></span><span></span></div>
          <div class="list-row faded-row"><i></i><span></span><span></span></div>
          <div class="list-row active-row">
            <div class="reused-target">
              <img :src="raceFinalProduct.image" :alt="`复用后的 ImageView 当前显示${raceFinalProduct.name}`">
              <span>同一个 ImageView</span>
            </div>
            <div class="row-copy">
              <strong>{{ raceTargetOwner }}</strong>
              <span>{{ raceTargetStatus }}</span>
            </div>
          </div>
          <div class="list-row faded-row"><i></i><span></span><span></span></div>
        </div>

        <div class="request-lanes" aria-label="两个异步请求的执行进度">
          <div class="request-lane lane-a" :data-cancelled="raceGuardSnapshot && raceElapsed >= 500">
            <div class="lane-head">
              <img :src="products[0].image" alt="Request-A 森林背包">
              <span><strong>Request-A</strong><small>背包 · 慢请求 2400 ms</small></span>
            </div>
            <div class="lane-track"><span :style="{ width: `${raceAProgress}%` }"></span></div>
            <em v-if="raceGuardSnapshot && raceElapsed >= 500">Target 复用时已取消</em>
            <em v-else-if="raceElapsed >= 2400">{{ raceGuardSnapshot ? '未交付' : '晚到并交付' }}</em>
            <em v-else>{{ Math.min(raceElapsed, 2400) }} / 2400 ms</em>
          </div>
          <div class="request-lane lane-b">
            <div class="lane-head">
              <img :src="products[1].image" alt="Request-B 珊瑚耳机">
              <span><strong>Request-B</strong><small>耳机 · 500 ms 时发起，700 ms 后返回</small></span>
            </div>
            <div class="lane-track"><span :style="{ width: `${raceBProgress}%` }"></span></div>
            <em v-if="raceElapsed < 500">尚未发起</em>
            <em v-else-if="raceElapsed < 1200">{{ raceElapsed - 500 }} / 700 ms</em>
            <em v-else>已返回并交付耳机</em>
          </div>
        </div>
      </div>

      <label class="switch-control race-guard">
        <input v-model="guardOldRequest" type="checkbox" :disabled="raceRunning">
        <span>安全模式：Target 复用时取消 Request-A</span>
      </label>
      <div class="action-row">
        <button type="button" class="primary-action" :disabled="raceRunning" @click="playRace">
          {{ racePlayed ? '重新播放' : '播放滚动与回调' }}
        </button>
      </div>

      <ol class="timeline race-timeline">
        <li
          v-for="(item, index) in raceTimeline"
          :key="item.time"
          :data-active="racePlayed && index <= raceStep"
          :data-current="racePlayed && index === raceStep"
        >
          <time>{{ item.time }}</time>
          <span>{{ item.event }}</span>
        </li>
      </ol>
      <p class="race-verdict" :data-state="racePlayed && raceStep >= 3 ? (raceGuardSnapshot ? 'safe' : 'wrong') : 'pending'">
        <strong v-if="!racePlayed">先预测：</strong>
        <strong v-else-if="raceStep < 3">播放中：</strong>
        <strong v-else>{{ raceGuardSnapshot ? '正确：' : '错位：' }}</strong>
        <span v-if="!racePlayed">row-35 最后应该显示耳机。旧的背包请求还能不能修改它？</span>
        <span v-else-if="raceStep < 3">观察 Target 的所有者和两条请求的返回顺序。</span>
        <span v-else-if="raceGuardSnapshot">ImageView 已属于 B，A 被取消，因此耳机保持不变。</span>
        <span v-else>ImageView 已属于 B，但旧请求 A 仍写入同一个控件，所以耳机被背包覆盖。</span>
      </p>
    </div>

    <div v-else-if="mode === 'failure'" class="lab-content">
      <div class="controls-grid failure-controls">
        <label>
          注入故障
          <select v-model="failureType" @change="failurePlayed = false">
            <option value="http404">HTTP 404</option>
            <option value="timeout">网络超时</option>
            <option value="corrupt">HTTP 200 + 损坏图片</option>
            <option value="unsupported">不支持的图片格式</option>
          </select>
        </label>
        <label class="switch-control">
          <input v-model="retryEnabled" type="checkbox" :disabled="failureType !== 'timeout'">
          <span>超时后退避重试一次</span>
        </label>
      </div>
      <div class="action-row">
        <button type="button" class="primary-action" @click="playFailure">执行失败请求</button>
      </div>
      <figure class="image-result failure-preview" :data-state="failureVisual.state">
        <div class="image-frame">
          <img :src="selectedProduct.image" :alt="`${selectedProduct.name}失败实验画面`">
          <span v-if="failurePlayed" class="failure-mark" aria-hidden="true">{{ failureVisual.state === 'error' ? '!' : '✓' }}</span>
          <span class="status-badge">{{ failureVisual.status }}</span>
        </div>
        <figcaption>
          <strong>{{ selectedProduct.name }}</strong>
          <span>{{ failureVisual.detail }}</span>
        </figcaption>
      </figure>
      <ol v-if="failureStages.length" class="pipeline-flow">
        <li v-for="item in failureStages" :key="item.name" :data-state="item.state">
          <strong>{{ item.name }}</strong>
          <span>{{ item.detail }}</span>
        </li>
      </ol>
      <p v-else class="empty-state">选择故障后执行，观察错误在哪个阶段产生、后续阶段是否跳过。</p>
    </div>

    <div v-else class="lab-content pattern-lab">
      <div class="pattern-scene">
        <figure class="pattern-product">
          <img :src="selectedProduct.image" :alt="`${selectedProduct.name}设计模式实验图片`">
          <figcaption>
            <strong>同一张商品图</strong>
            <span>点击一次请求中的不同阶段</span>
          </figcaption>
        </figure>

        <ol class="pattern-flow" aria-label="图片请求设计模式管线">
          <li v-for="node in patternNodes" :key="node.id">
            <button
              type="button"
              :aria-pressed="selectedPatternId === node.id"
              :class="{ selected: selectedPatternId === node.id }"
              @click="selectedPatternId = node.id"
            >
              <strong>{{ node.label }}</strong>
              <span>{{ node.pattern }}</span>
            </button>
          </li>
        </ol>
      </div>

      <section class="pattern-inspector" aria-live="polite">
        <header>
          <span>{{ selectedPattern.category }}</span>
          <h3>{{ selectedPattern.label }} · {{ selectedPattern.pattern }}</h3>
          <p>{{ selectedPattern.responsibility }}</p>
        </header>

        <dl>
          <div>
            <dt>隔离的变化</dt>
            <dd>{{ selectedPattern.isolatedChange }}</dd>
          </div>
          <div>
            <dt>Coil</dt>
            <dd><code>{{ selectedPattern.coil }}</code></dd>
          </div>
          <div>
            <dt>Glide</dt>
            <dd><code>{{ selectedPattern.glide }}</code></dd>
          </div>
          <div class="verification-row">
            <dt>源码验证点</dt>
            <dd>{{ selectedPattern.verification }}</dd>
          </div>
        </dl>
      </section>

      <p class="pattern-warning">
        判断顺序：先找变化，再看对象如何协作，最后才给模式命名。框架没有义务完全照搬教科书结构。
      </p>
    </div>
  </section>
</template>

<style scoped>
.pipeline-lab {
  margin: 24px 0 32px;
  color: var(--vp-c-text-1);
}

.mode-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  border-bottom: 1px solid var(--vp-c-divider);
}

button,
select,
input {
  font: inherit;
}

button {
  border: 1px solid var(--vp-c-divider);
  border-radius: 6px;
  background: var(--vp-c-bg);
  color: var(--vp-c-text-1);
  padding: 7px 12px;
  cursor: pointer;
}

button:hover {
  border-color: var(--vp-c-brand-1);
}

button:focus-visible,
select:focus-visible,
input:focus-visible {
  outline: 2px solid var(--vp-c-brand-1);
  outline-offset: 2px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.mode-tabs button {
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 4px 4px 0 0;
}

.mode-tabs button.active {
  border-bottom-color: var(--vp-c-brand-1);
  color: var(--vp-c-brand-1);
  background: var(--vp-c-bg-soft);
}

.lab-content {
  padding-top: 18px;
}

.product-picker {
  display: grid;
  grid-template-columns: auto repeat(3, minmax(0, 1fr));
  gap: 8px;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--vp-c-divider);
}

.product-picker > span {
  color: var(--vp-c-text-2);
  font-size: 13px;
}

.product-picker button {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  min-width: 0;
  padding: 5px;
  text-align: left;
}

.product-picker button.selected {
  border-color: var(--vp-c-brand-1);
  background: var(--vp-c-bg-soft);
}

.product-picker img {
  width: 42px;
  height: 42px;
  object-fit: cover;
}

.product-picker strong {
  overflow-wrap: anywhere;
  font-size: 13px;
  font-weight: 500;
}

.controls-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 18px;
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

.controls-grid label,
.switch-control {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--vp-c-text-2);
  font-size: 14px;
}

.switch-control {
  flex-direction: row;
  align-items: center;
  align-self: end;
  min-height: 38px;
}

.controls-grid .switch-control {
  flex-direction: row;
}

select,
input[type='number'] {
  width: 100%;
  min-width: 0;
  border: 1px solid var(--vp-c-divider);
  border-radius: 6px;
  background: var(--vp-c-bg);
  color: var(--vp-c-text-1);
  padding: 7px 9px;
  box-sizing: border-box;
}

input[type='range'] {
  width: 100%;
  accent-color: var(--vp-c-brand-1);
}

input[type='checkbox'] {
  accent-color: var(--vp-c-brand-1);
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 18px 0;
}

.primary-action {
  border-color: var(--vp-c-brand-1);
  background: var(--vp-c-brand-1);
  color: var(--vp-c-white);
}

.cancel-action {
  border-color: var(--vp-c-danger-1);
  color: var(--vp-c-danger-1);
}

.image-result {
  display: grid;
  grid-template-columns: minmax(220px, 1.35fr) minmax(180px, 1fr);
  min-width: 0;
  margin: 16px 0;
  border: 1px solid var(--vp-c-divider);
  border-radius: 6px;
  overflow: hidden;
  background: var(--vp-c-bg-soft);
}

.image-frame {
  position: relative;
  min-width: 0;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: var(--vp-c-bg-alt);
}

.image-frame > img,
.image-frame > canvas {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cache-image-frame .preview-image {
  transition: filter 220ms ease, opacity 220ms ease, transform 220ms ease;
}

.cache-image-frame .preview-image:not(.is-ready) {
  filter: blur(18px) brightness(0.42) !important;
  opacity: 0.72;
  transform: scale(1.08);
}

.loading-visual {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 8px;
  color: #fff;
  text-align: center;
}

.loading-visual strong,
.loading-visual span {
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.7);
}

.loading-spinner {
  width: 30px;
  height: 30px;
  border: 3px solid rgba(255, 255, 255, 0.34);
  border-top-color: #fff;
  border-radius: 50%;
  animation: pipeline-spin 700ms linear infinite;
}

.transfer-progress {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 2;
  height: 5px;
  background: rgba(255, 255, 255, 0.2);
}

.transfer-progress span {
  display: block;
  height: 100%;
  background: #55c48f;
  transition: width 40ms linear;
}

.latency-note {
  padding-top: 8px;
  border-top: 1px solid var(--vp-c-divider);
  color: var(--vp-c-text-1) !important;
}

@keyframes pipeline-spin {
  to { transform: rotate(360deg); }
}

.image-result figcaption {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  min-width: 0;
  padding: 16px;
}

.image-result figcaption strong,
.image-result figcaption span {
  display: block;
}

.image-result figcaption strong {
  font-size: 16px;
}

.image-result figcaption span {
  color: var(--vp-c-text-2);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.status-badge,
.size-badge {
  position: absolute;
  z-index: 1;
  padding: 4px 7px;
  background: rgba(20, 24, 22, 0.82);
  color: #fff;
  font-size: 12px;
  line-height: 1.2;
}

.status-badge {
  top: 8px;
  left: 8px;
}

.size-badge {
  right: 8px;
  bottom: 8px;
}

.memory-visuals {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.memory-visuals .image-result {
  display: flex;
  flex-direction: column;
  margin: 0;
}

.square-frame {
  aspect-ratio: 1;
}

.decoded-frame canvas {
  image-rendering: pixelated;
}

.race-explainer {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 14px;
  padding-left: 12px;
  border-left: 3px solid var(--vp-c-brand-1);
}

.race-explainer span {
  color: var(--vp-c-text-2);
  font-size: 13px;
}

.race-stage {
  display: grid;
  grid-template-columns: minmax(220px, 0.82fr) minmax(300px, 1.4fr);
  gap: 16px;
  padding: 14px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 6px;
  background: var(--vp-c-bg-soft);
}

.race-stage[data-state='wrong'] {
  border-color: var(--vp-c-danger-1);
}

.race-stage[data-state='safe'] {
  border-color: var(--vp-c-green-1);
}

.phone-list {
  overflow: hidden;
  border: 1px solid var(--vp-c-divider);
  border-radius: 6px;
  background: var(--vp-c-bg);
}

.phone-bar {
  display: flex;
  gap: 4px;
  padding: 7px;
  border-bottom: 1px solid var(--vp-c-divider);
}

.phone-bar span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--vp-c-text-3);
}

.list-row {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 10px;
  min-height: 76px;
  padding: 8px;
  border-bottom: 1px solid var(--vp-c-divider);
}

.list-row:last-child {
  border-bottom: 0;
}

.active-row {
  background: color-mix(in srgb, var(--vp-c-brand-1) 8%, transparent);
}

.reused-target {
  position: relative;
  width: 76px;
  height: 76px;
  overflow: hidden;
  border: 2px solid var(--vp-c-brand-1);
  background: var(--vp-c-bg-alt);
}

.reused-target img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.reused-target span {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 2px;
  background: rgba(20, 24, 22, 0.8);
  color: #fff;
  font-size: 9px;
  text-align: center;
}

.row-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.row-copy span {
  margin-top: 4px;
  color: var(--vp-c-text-2);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.faded-row {
  grid-template-columns: 58px minmax(0, 1fr);
  min-height: 58px;
  opacity: 0.38;
}

.faded-row i,
.faded-row span {
  display: block;
  border-radius: 3px;
  background: var(--vp-c-divider);
}

.faded-row span:last-child {
  align-self: center;
  width: 60%;
  height: 8px;
}

.request-lanes {
  display: grid;
  align-content: center;
  gap: 18px;
  min-width: 0;
}

.request-lane {
  min-width: 0;
  padding: 10px;
  border-left: 3px solid var(--vp-c-brand-1);
  background: var(--vp-c-bg);
}

.request-lane[data-cancelled='true'] {
  border-left-color: var(--vp-c-text-3);
  opacity: 0.64;
}

.lane-head {
  display: flex;
  gap: 9px;
  align-items: center;
}

.lane-head img {
  width: 44px;
  height: 44px;
  object-fit: cover;
}

.lane-head strong,
.lane-head small {
  display: block;
}

.lane-head small,
.request-lane em {
  color: var(--vp-c-text-2);
  font-size: 12px;
  font-style: normal;
}

.lane-track {
  height: 7px;
  margin: 10px 0 5px;
  overflow: hidden;
  border-radius: 3px;
  background: var(--vp-c-divider);
}

.lane-track span {
  display: block;
  height: 100%;
  background: var(--vp-c-brand-1);
  transition: width 40ms linear;
}

.lane-b {
  border-left-color: #55c48f;
}

.lane-b .lane-track span {
  background: #55c48f;
}

.race-guard {
  margin-top: 14px;
}

.failure-preview[data-state='error'] img {
  filter: grayscale(0.7) brightness(0.45);
}

.failure-mark {
  position: absolute;
  top: 50%;
  left: 50%;
  display: grid;
  width: 52px;
  height: 52px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: rgba(179, 38, 30, 0.9);
  color: #fff;
  place-items: center;
  font-size: 28px;
  font-weight: 700;
  transform: translate(-50%, -50%);
}

.failure-preview[data-state='success'] .failure-mark {
  background: rgba(24, 121, 78, 0.9);
}

.metrics-row {
  display: grid;
  grid-template-columns: minmax(120px, 0.7fr) minmax(120px, 0.7fr) minmax(220px, 2fr);
  gap: 1px;
  margin: 16px 0;
  background: var(--vp-c-divider);
  border: 1px solid var(--vp-c-divider);
  border-radius: 6px;
  overflow: hidden;
}

.metrics-row > div {
  min-width: 0;
  padding: 12px;
  background: var(--vp-c-bg-soft);
}

.metrics-row span,
.metrics-row strong {
  display: block;
}

.metrics-row span {
  color: var(--vp-c-text-2);
  font-size: 13px;
}

.metrics-row strong {
  margin-top: 3px;
  overflow-wrap: anywhere;
  font-weight: 500;
}

.pipeline-flow {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(112px, 1fr));
  gap: 8px;
  padding: 0;
  margin: 18px 0;
  list-style: none;
}

.pipeline-flow li {
  min-width: 0;
  border-top: 3px solid var(--vp-c-divider);
  padding: 9px 8px;
  background: var(--vp-c-bg-soft);
}

.pipeline-flow li[data-state='hit'] {
  border-top-color: var(--vp-c-green-1);
}

.pipeline-flow li[data-state='work'] {
  border-top-color: var(--vp-c-brand-1);
}

.pipeline-flow li[data-state='error'] {
  border-top-color: var(--vp-c-danger-1);
}

.pipeline-flow li[data-state='skip'] {
  opacity: 0.58;
}

.pipeline-flow strong,
.pipeline-flow span {
  display: block;
}

.pipeline-flow strong {
  font-weight: 500;
}

.pipeline-flow span {
  margin-top: 4px;
  color: var(--vp-c-text-2);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.result-line,
.empty-state {
  margin: 12px 0 0;
  color: var(--vp-c-text-2);
}

.memory-controls {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.memory-bar {
  height: 18px;
  margin-top: 18px;
  background: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-divider);
  border-radius: 4px;
  overflow: hidden;
}

.memory-bar span {
  display: block;
  height: 100%;
  background: var(--vp-c-brand-1);
  transition: width 180ms ease;
}

.timeline {
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
  border-left: 2px solid var(--vp-c-divider);
}

.timeline li {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr);
  gap: 12px;
  padding: 8px 0 8px 14px;
}

.timeline time {
  color: var(--vp-c-brand-1);
  font-variant-numeric: tabular-nums;
}

.race-timeline li {
  opacity: 0.42;
  transition: opacity 160ms ease, background 160ms ease;
}

.race-timeline li[data-active='true'] {
  opacity: 1;
}

.race-timeline li[data-current='true'] {
  background: var(--vp-c-bg-soft);
}

.race-verdict {
  display: flex;
  gap: 6px;
  margin: 14px 0 0;
  padding: 10px 12px;
  border-left: 3px solid var(--vp-c-divider);
  background: var(--vp-c-bg-soft);
}

.race-verdict[data-state='safe'] {
  border-left-color: var(--vp-c-green-1);
}

.race-verdict[data-state='wrong'] {
  border-left-color: var(--vp-c-danger-1);
}

.failure-controls {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.pattern-scene {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
}

.pattern-product {
  margin: 0;
  min-width: 0;
  background: var(--vp-c-bg-soft);
}

.pattern-product img {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
}

.pattern-product figcaption {
  padding: 8px 9px;
}

.pattern-product strong,
.pattern-product span {
  display: block;
}

.pattern-product span {
  color: var(--vp-c-text-2);
  font-size: 12px;
}

.pattern-flow {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 7px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.pattern-flow li {
  min-width: 0;
}

.pattern-flow button {
  width: 100%;
  height: 100%;
  min-height: 70px;
  border-top: 3px solid var(--vp-c-divider);
  text-align: left;
}

.pattern-flow button.selected {
  border-color: var(--vp-c-brand-1);
  border-top-color: var(--vp-c-brand-1);
  background: var(--vp-c-bg-soft);
}

.pattern-flow strong,
.pattern-flow span {
  display: block;
  overflow-wrap: anywhere;
}

.pattern-flow span {
  margin-top: 4px;
  color: var(--vp-c-text-2);
  font-size: 12px;
}

.pattern-inspector {
  margin-top: 16px;
  border-left: 3px solid var(--vp-c-brand-1);
  background: var(--vp-c-bg-soft);
  padding: 14px 16px;
}

.pattern-inspector header > span {
  color: var(--vp-c-brand-1);
  font-size: 12px;
  font-weight: 600;
}

.pattern-inspector h3 {
  margin: 3px 0 5px;
  border: 0;
  padding: 0;
  font-size: 18px;
}

.pattern-inspector header p {
  margin: 0;
  color: var(--vp-c-text-2);
}

.pattern-inspector dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  margin: 14px 0 0;
  background: var(--vp-c-divider);
}

.pattern-inspector dl > div {
  min-width: 0;
  background: var(--vp-c-bg);
  padding: 10px;
}

.pattern-inspector .verification-row {
  grid-column: 1 / -1;
}

.pattern-inspector dt {
  color: var(--vp-c-text-2);
  font-size: 12px;
}

.pattern-inspector dd {
  margin: 3px 0 0;
  overflow-wrap: anywhere;
}

.pattern-inspector code {
  white-space: normal;
}

.pattern-warning {
  margin: 12px 0 0;
  color: var(--vp-c-text-2);
  font-size: 13px;
}

@media (max-width: 760px) {
  .controls-grid,
  .memory-controls,
  .failure-controls,
  .metrics-row {
    grid-template-columns: 1fr;
  }

  .switch-control {
    align-self: start;
  }

  .timeline li {
    grid-template-columns: 64px minmax(0, 1fr);
  }

  .product-picker {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .product-picker > span {
    grid-column: 1 / -1;
  }

  .product-picker button {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    text-align: center;
  }

  .product-picker img {
    width: 100%;
    height: auto;
    aspect-ratio: 1;
  }

  .image-result {
    grid-template-columns: 1fr;
  }

  .memory-visuals,
  .race-stage {
    grid-template-columns: 1fr;
  }

  .pattern-scene {
    grid-template-columns: 1fr;
  }

  .pattern-product {
    display: grid;
    grid-template-columns: 104px minmax(0, 1fr);
  }

  .pattern-product img {
    height: 88px;
    aspect-ratio: auto;
  }

  .pattern-flow,
  .pattern-inspector dl {
    grid-template-columns: 1fr;
  }

  .pattern-flow button {
    min-height: 0;
  }

  .pattern-inspector .verification-row {
    grid-column: auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .memory-bar span,
  .cache-image-frame .preview-image,
  .transfer-progress span,
  .lane-track span,
  .race-timeline li {
    transition: none;
  }

  .loading-spinner {
    animation: none;
  }
}
</style>
