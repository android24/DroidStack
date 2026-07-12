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
      { text: 'GitHub', link: 'https://github.com/android24/DroidStack' },
      { text: 'PDF版本下载', link: 'https://github.com/android24/DroidStack/releases' },
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
        items: [
          { text: '第1章：Android 开源框架全景', link: '/chapter1/' },
          {
            text: '第2章：如何学习和评价一个开源框架',
            items: [
              { text: '第2.1节：如何学习一个开源框架', link: '/chapter2/chapter2_1' },
              { text: '第2.2节：框架评价与选型维度', link: '/chapter2/chapter2_2' }
            ]
          }
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
      message: '<a href="https://beian.miit.gov.cn/" target="_blank">京ICP备2026002630号-1</a> | <a href="https://beian.mps.gov.cn/#/query/webSearch?code=11010602202215" rel="noreferrer" target="_blank">京公网安备11010602202215号</a>',
      copyright: '本作品采用 <a href="http://creativecommons.org/licenses/by-nc-sa/4.0/" target="_blank">知识共享署名-非商业性使用-相同方式共享 4.0 国际许可协议（CC BY-NC-SA 4.0）</a> 进行许可'
    }
  }
})
