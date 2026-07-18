<script setup lang="ts">
import { computed, ref } from 'vue'

type Mode = 'cache' | 'memory' | 'race' | 'failure'
type StageState = 'idle' | 'hit' | 'work' | 'skip' | 'error'

interface Stage {
  name: string
  detail: string
  state: StageState
}

const mode = ref<Mode>('cache')
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

const originalWidth = ref(4000)
const originalHeight = ref(3000)
const bitmapConfig = ref(4)
const decodedWidth = ref(144)
const decodedHeight = ref(144)

const guardOldRequest = ref(false)
const racePlayed = ref(false)

const failureType = ref('http404')
const retryEnabled = ref(false)
const failurePlayed = ref(false)

const resourceKey = computed(() =>
  `product-42:v${version.value}:${targetSize.value}px:${transformation.value}`
)
const dataKey = computed(() => `https://img.example/42.jpg:v${version.value}`)

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

const raceTimeline = computed(() => {
  const final = guardOldRequest.value ? '商品 B' : '商品 A（错位）'
  return [
    { time: '0 ms', event: 'Target 绑定商品 A，Request-A 开始', owner: 'A' },
    { time: '300 ms', event: 'Target 被复用给商品 B，Request-B 开始', owner: 'B' },
    { time: '800 ms', event: 'Request-B 返回，显示商品 B', owner: 'B' },
    {
      time: '2000 ms',
      event: guardOldRequest.value
        ? 'Request-A 返回，但身份校验失败，结果被丢弃'
        : 'Request-A 返回并覆盖 Target，发生错位',
      owner: guardOldRequest.value ? 'B' : 'A'
    },
    { time: '最终', event: `Target 显示：${final}`, owner: final }
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
      stage('Decoder', retryEnabled.value ? '重试成功后才可能进入' : '没有输入数据', 'skip'),
      stage('Target', retryEnabled.value ? '展示重试结果或最终错误' : '显示 error', 'work')
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

function runCacheRequest() {
  cacheRun.value += 1
  const currentResourceKey = resourceKey.value
  const currentDataKey = dataKey.value
  const decodeCost = Math.max(6, Math.round((targetSize.value * targetSize.value) / 6000))

  if (memoryCacheEnabled.value && has(memoryKeys.value, currentResourceKey)) {
    cacheSource.value = 'MEMORY'
    cacheDuration.value = 4
    cacheResult.value = '命中完整资源，不再读取磁盘、网络和解码'
    cacheStages.value = [
      stage('Size + Key', currentResourceKey, 'work'),
      stage('Memory', '命中', 'hit'),
      stage('Disk', '跳过', 'skip'),
      stage('Fetcher', '跳过', 'skip'),
      stage('Decoder', '跳过', 'skip'),
      stage('Target', '交付结果', 'work')
    ]
    return
  }

  if (diskCacheEnabled.value && has(resourceDiskKeys.value, currentResourceKey)) {
    cacheSource.value = 'RESOURCE_DISK'
    cacheDuration.value = 24
    cacheResult.value = '命中已变换资源，读取磁盘后直接交付'
    if (memoryCacheEnabled.value) memoryKeys.value = add(memoryKeys.value, currentResourceKey)
    cacheStages.value = [
      stage('Size + Key', currentResourceKey, 'work'),
      stage('Memory', '未命中', 'skip'),
      stage('Resource Disk', '命中', 'hit'),
      stage('Fetcher', '跳过', 'skip'),
      stage('Decoder', '跳过', 'skip'),
      stage('Target', '交付结果', 'work')
    ]
    return
  }

  if (diskCacheEnabled.value && has(dataDiskKeys.value, currentDataKey)) {
    cacheSource.value = 'DATA_DISK'
    cacheDuration.value = 28 + decodeCost
    cacheResult.value = '原始数据命中，但尺寸或变换不同，需要重新解码'
    if (diskCacheEnabled.value) {
      resourceDiskKeys.value = add(resourceDiskKeys.value, currentResourceKey)
    }
    if (memoryCacheEnabled.value) memoryKeys.value = add(memoryKeys.value, currentResourceKey)
    cacheStages.value = [
      stage('Size + Key', currentResourceKey, 'work'),
      stage('Memory', '未命中', 'skip'),
      stage('Resource Disk', '未命中', 'skip'),
      stage('Data Disk', '命中', 'hit'),
      stage('Decoder', `解码到 ${targetSize.value}px`, 'work'),
      stage('Target', '交付结果', 'work')
    ]
    return
  }

  cacheSource.value = 'NETWORK'
  cacheDuration.value = latency.value + 42 + decodeCost
  cacheResult.value = '全部未命中，执行网络获取、解码和缓存写入'
  if (diskCacheEnabled.value) {
    dataDiskKeys.value = add(dataDiskKeys.value, currentDataKey)
    resourceDiskKeys.value = add(resourceDiskKeys.value, currentResourceKey)
  }
  if (memoryCacheEnabled.value) memoryKeys.value = add(memoryKeys.value, currentResourceKey)
  cacheStages.value = [
    stage('Size + Key', currentResourceKey, 'work'),
    stage('Memory', '未命中', 'skip'),
    stage('Resource Disk', '未命中', 'skip'),
    stage('Data Disk', '未命中', 'skip'),
    stage('Fetcher', `网络 ${latency.value} ms`, 'work'),
    stage('Decoder', `解码到 ${targetSize.value}px`, 'work'),
    stage('Target', '交付结果', 'work')
  ]
}

function clearMemory() {
  memoryKeys.value = []
  cacheResult.value = '内存缓存已清空，磁盘缓存仍保留'
  cacheSource.value = '-'
  cacheStages.value = []
}

function clearAllCaches() {
  memoryKeys.value = []
  dataDiskKeys.value = []
  resourceDiskKeys.value = []
  cacheResult.value = '内存与磁盘缓存均已清空'
  cacheSource.value = '-'
  cacheStages.value = []
}

function playRace() {
  racePlayed.value = true
}

function playFailure() {
  failurePlayed.value = true
}
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
    </div>

    <div v-if="mode === 'cache'" class="lab-content">
      <div class="controls-grid">
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
          <input v-model="latency" type="range" min="0" max="3000" step="200">
        </label>
      </div>

      <div class="action-row">
        <button type="button" class="primary-action" @click="runCacheRequest">
          执行第 {{ cacheRun + 1 }} 次请求
        </button>
        <button type="button" @click="clearMemory">杀进程效果</button>
        <button type="button" @click="clearAllCaches">清空全部缓存</button>
      </div>

      <div class="metrics-row" aria-live="polite">
        <div><span>数据来源</span><strong>{{ cacheSource }}</strong></div>
        <div><span>模拟耗时</span><strong>{{ cacheDuration }} ms</strong></div>
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
      <label class="switch-control">
        <input v-model="guardOldRequest" type="checkbox">
        <span>启用 Target 身份校验与旧请求清理</span>
      </label>
      <div class="action-row">
        <button type="button" class="primary-action" @click="playRace">播放竞态时间线</button>
      </div>
      <ol v-if="racePlayed" class="timeline">
        <li v-for="item in raceTimeline" :key="item.time">
          <time>{{ item.time }}</time>
          <span>{{ item.event }}</span>
        </li>
      </ol>
      <p v-else class="empty-state">先预测最终会显示 A 还是 B，再播放时间线。</p>
    </div>

    <div v-else class="lab-content">
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
      <ol v-if="failureStages.length" class="pipeline-flow">
        <li v-for="item in failureStages" :key="item.name" :data-state="item.state">
          <strong>{{ item.name }}</strong>
          <span>{{ item.detail }}</span>
        </li>
      </ol>
      <p v-else class="empty-state">选择故障后执行，观察错误在哪个阶段产生、后续阶段是否跳过。</p>
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

.controls-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 18px;
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

.failure-controls {
  grid-template-columns: repeat(2, minmax(0, 1fr));
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
}

@media (prefers-reduced-motion: reduce) {
  .memory-bar span {
    transition: none;
  }
}
</style>
