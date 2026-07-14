import { defineConfig } from 'vitepress'
// https://vitepress.dev/reference/site-config

// 1. 获取环境变量并判断
// 如果环境变量 EDGEONE 等于 '1'，说明在 EdgeOne 环境，使用根路径 '/'
// 否则默认是 GitHub Pages 环境，使用仓库子路径 '/DroidStack/'
const isEdgeOne = process.env.EDGEONE === '1'
const baseConfig = isEdgeOne ? '/' : '/DroidStack/'

export default defineConfig({
  lang: 'zh-CN',
  title: 'DroidStack',
  description: 'Android 开源框架全景：原理、选型与工程实践',
  base: baseConfig,
  markdown: {
    math: true
  },
  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config
    logo: '/datawhale-logo.png',
    nav: [
      { text: '开始学习', link: '/chapter1/' },
      { text: 'GitHub', link: 'https://github.com/android24/DroidStack' },
    ],
    search: {
      provider: 'local',
      options: {
        translations: {
          button: {
            buttonText: '搜索文档',
            buttonAriaLabel: '搜索文档'
          },
          modal: {
            noResultsText: '无法找到相关结果',
            resetButtonTitle: '清除查询条件',
            footer: {
              selectText: '选择',
              navigateText: '切换'
            }
          }
        }
      }
    },
    sidebar: [
      {
        text: '第一阶段：认识 Android 开源框架',
        items: [
          { text: '第1章：Android 开源框架全景', link: '/chapter1/' },
          {
            text: '第2章：如何学习和评价一个开源框架',
            link: '/chapter2/',
            items: [
              { text: '第2.1节：如何学习一个开源框架', link: '/chapter2/chapter2_1' },
              { text: '第2.2节：框架评价与选型维度', link: '/chapter2/chapter2_2' }
            ]
          }
        ]
      },
      {
        text: '第二阶段：数据获取与处理框架',
        items: [
          { text: '第3章：图片加载框架', link: '/chapter3/' },
          { text: '第4章：网络请求底层框架（建设中）' },
          { text: '第5章：接口代理与网络处理框架（建设中）' },
          { text: '第6章：JSON 序列化与数据转换（建设中）' }
        ]
      },
      {
        text: '第三阶段：线程、数据流与依赖管理',
        items: [
          { text: '第7章：Android 异步编程发展史（建设中）' },
          { text: '第8章：RxJava 与响应式编程（建设中）' },
          { text: '第9章：Kotlin Coroutines 与 Flow（建设中）' },
          { text: '第10章：依赖注入框架（建设中）' }
        ]
      },
      {
        text: '第四阶段：本地数据、后台任务与架构',
        items: [
          { text: '第11章：本地数据存储框架（建设中）' },
          { text: '第12章：后台任务与任务调度（建设中）' },
          { text: '第13章：分页、缓存与离线优先架构（建设中）' },
          { text: '第14章：Android 架构框架与状态管理（建设中）' },
          { text: '第15章：导航、组件通信与生命周期（建设中）' }
        ]
      },
      {
        text: '第五阶段：工程质量与综合实践',
        items: [
          { text: '第16章：Android 测试框架（建设中）' },
          { text: '第17章：调试、日志与性能框架（建设中）' },
          { text: '第18章：综合项目与框架选型（建设中）' }
        ]
      }
    ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/android24/DroidStack' }
    ],

    editLink: {
      pattern: 'https://github.com/android24/DroidStack/blob/main/docs/:path'
    },

    footer: {
      message: '<a href="https://github.com/android24/DroidStack" target="_blank">DroidStack on GitHub</a>',
      copyright: '本作品采用 <a href="http://creativecommons.org/licenses/by-nc-sa/4.0/" target="_blank">知识共享署名-非商业性使用-相同方式共享 4.0 国际许可协议（CC BY-NC-SA 4.0）</a> 进行许可'
    }
  }
})
