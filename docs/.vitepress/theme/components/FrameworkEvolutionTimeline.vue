<script setup lang="ts">
import { withBase } from 'vitepress'
import { computed, ref } from 'vue'

interface Era {
  id: string
  year: string
  framework: string
  focus: string
  pressure: string
  abstraction: string
  pipeline: string[]
  result: string
}

const eras: Era[] = [
  {
    id: 'uil',
    year: '2011',
    framework: 'UIL',
    focus: '统一加载能力',
    pressure: '下载、线程、缓存和回调散落在每个页面中。',
    abstraction: '一个全局可配置 ImageLoader',
    pipeline: ['displayImage', '线程池', '下载器', '缓存', 'ImageView'],
    result: '项目不必再重复拼装 Thread、Handler、BitmapFactory 和 Cache。'
  },
  {
    id: 'picasso',
    year: '2013',
    framework: 'Picasso',
    focus: '建立请求语义',
    pressure: '列表复用要求每次加载拥有尺寸、身份、取消和结果目标。',
    abstraction: '一条可读、可取消的 Request',
    pipeline: ['load', 'resize', 'Dispatcher', 'Hunter', 'Target'],
    result: '框架中心从全局配置转向一次有身份的图片请求。'
  },
  {
    id: 'glide',
    year: '2014',
    framework: 'Glide',
    focus: '提高资源效率',
    pressure: 'Feed、图库和动图让解码、GC、滚动与生命周期成为瓶颈。',
    abstraction: '生命周期感知的媒体请求与资源引擎',
    pipeline: ['RequestManager', 'Engine', 'DecodeJob', 'BitmapPool', 'Target'],
    result: '一次请求开始同时对像素内存、复用效率和页面生命周期负责。'
  },
  {
    id: 'fresco',
    year: '2015',
    framework: 'Fresco',
    focus: '显式治理管线',
    pressure: '图片密集业务需要渐进显示、多级缓存和明确的资源所有权。',
    abstraction: 'Image Pipeline + Drawee',
    pipeline: ['Controller', 'Producer', 'Encoded Cache', 'Decode', 'Drawee'],
    result: '图片加载不再只是 Loader，而是可以分阶段观测和治理的 Pipeline。'
  },
  {
    id: 'coil',
    year: '2019+',
    framework: 'Coil',
    focus: '融入现代 Kotlin UI',
    pressure: '协程、Compose 与多平台要求请求取消、状态和组件摆脱 View 限制。',
    abstraction: '不可变 ImageRequest + 可插拔组件',
    pipeline: ['ImageRequest', 'Interceptor', 'Fetcher', 'Decoder', 'Painter State'],
    result: '成熟图片管线进入结构化并发、声明式 UI 和 Compose Multiplatform。'
  }
]

const selectedId = ref(eras[0].id)
const selectedEra = computed(() => eras.find((era) => era.id === selectedId.value) ?? eras[0])
const productImage = withBase('/images/chapter3/lab/product_backpack.jpg')
</script>

<template>
  <section class="evolution-lab" aria-label="图片加载框架演进轨道">
    <div class="era-tabs" role="tablist" aria-label="选择框架时代">
      <button
        v-for="era in eras"
        :key="era.id"
        type="button"
        role="tab"
        :aria-selected="selectedId === era.id"
        :class="{ active: selectedId === era.id }"
        @click="selectedId = era.id"
      >
        <span>{{ era.year }}</span>
        <strong>{{ era.framework }}</strong>
      </button>
    </div>

    <div class="era-stage">
      <figure>
        <img :src="productImage" :alt="`${selectedEra.framework} 时代的商品图片加载示意`">
        <figcaption>{{ selectedEra.focus }}</figcaption>
      </figure>

      <div class="era-story" aria-live="polite">
        <p class="pressure"><span>工程压力</span><strong>{{ selectedEra.pressure }}</strong></p>
        <p><span>核心抽象</span><strong>{{ selectedEra.abstraction }}</strong></p>
        <ol class="era-pipeline" aria-label="该时代的代表性主流程">
          <li v-for="step in selectedEra.pipeline" :key="step">{{ step }}</li>
        </ol>
        <p class="result"><span>这次变化带来了什么</span><strong>{{ selectedEra.result }}</strong></p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.evolution-lab {
  margin: 24px 0 30px;
  border-top: 1px solid var(--vp-c-divider);
  border-bottom: 1px solid var(--vp-c-divider);
  padding: 16px 0;
}

.era-tabs {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 4px;
}

button {
  min-width: 0;
  border: 0;
  border-bottom: 3px solid var(--vp-c-divider);
  border-radius: 4px 4px 0 0;
  background: var(--vp-c-bg-soft);
  color: var(--vp-c-text-2);
  padding: 9px 6px;
  cursor: pointer;
  font: inherit;
}

button:hover,
button.active {
  border-bottom-color: var(--vp-c-brand-1);
  color: var(--vp-c-text-1);
}

button:focus-visible {
  outline: 2px solid var(--vp-c-brand-1);
  outline-offset: 2px;
}

button span,
button strong {
  display: block;
  overflow-wrap: anywhere;
}

button span {
  font-size: 12px;
}

button strong {
  margin-top: 2px;
  font-size: 14px;
  font-weight: 600;
}

.era-stage {
  display: grid;
  grid-template-columns: minmax(150px, 0.72fr) minmax(0, 2fr);
  gap: 20px;
  padding-top: 18px;
}

figure {
  margin: 0;
  min-width: 0;
}

figure img {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
}

figcaption {
  border-left: 3px solid var(--vp-c-brand-1);
  background: var(--vp-c-bg-soft);
  padding: 8px 10px;
  font-weight: 600;
}

.era-story {
  min-width: 0;
}

.era-story p {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  gap: 10px;
  margin: 0;
  padding: 9px 0;
}

.era-story p + p {
  border-top: 1px solid var(--vp-c-divider);
}

.era-story p span {
  color: var(--vp-c-text-2);
  font-size: 13px;
}

.era-story p strong {
  font-weight: 500;
}

.era-pipeline {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px;
  margin: 12px 0;
  padding: 0;
  list-style: none;
}

.era-pipeline li {
  position: relative;
  min-width: 0;
  border-top: 3px solid var(--vp-c-brand-1);
  background: var(--vp-c-bg-soft);
  padding: 9px 6px;
  overflow-wrap: anywhere;
  text-align: center;
  font-size: 12px;
}

.era-pipeline li:not(:last-child)::after {
  content: '>';
  position: absolute;
  top: 50%;
  right: -7px;
  z-index: 1;
  color: var(--vp-c-text-3);
  transform: translateY(-50%);
}

.result {
  border-top: 1px solid var(--vp-c-divider);
}

@media (max-width: 680px) {
  .era-tabs {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .era-stage {
    grid-template-columns: 1fr;
  }

  figure {
    display: grid;
    grid-template-columns: 112px minmax(0, 1fr);
  }

  figure img {
    height: 92px;
    aspect-ratio: auto;
  }

  .era-pipeline {
    grid-template-columns: 1fr;
  }

  .era-pipeline li {
    text-align: left;
  }

  .era-pipeline li:not(:last-child)::after {
    content: 'v';
    top: auto;
    right: 10px;
    bottom: -9px;
    transform: none;
  }
}
</style>
