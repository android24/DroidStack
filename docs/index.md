---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: "DroidStack"
  text: "Android 开源框架全景"
  tagline: 从问题场景、核心原理、源码主线到工程选型，系统梳理现代 Android 应用层开源框架。
  image:
    src: /learning.GIF
    alt: DroidStack
  actions:
    - theme: brand
      text: 开始学习
      link: /chapter1/
    - theme: alt
      text: 查看目录
      link: /chapter2/
    - theme: alt
      text: GitHub
      link: https://github.com/android24/DroidStack

features:
  - title: 框架全景
    details: 覆盖图片加载、网络请求、数据解析、异步编程、依赖注入、数据存储、后台任务、分页、测试与性能优化。
  - title: 原理选型
    details: 不停留在 API 使用，重点理解缓存、线程调度、动态代理、代码生成、依赖图、数据流与生命周期。
  - title: 工程实践
    details: 通过综合项目串联 Coil、OkHttp、Retrofit、Coroutines、Flow、Hilt、Room、DataStore、WorkManager 与 Paging 3。
---

## 课程定位

DroidStack 是一门面向现代 Android 应用开发的开源课程。它不把重点放在逐个背 API，而是围绕 Android 应用中的典型问题域，讲清楚框架解决的问题、核心原理、源码主线、同类对比和工程选型。

这门课适合已经掌握 Android 基础开发，希望系统梳理主流开源框架、理解框架背后工程思想，并能在真实项目中做技术选型的学习者。

## 课程主线

```text
问题场景
  -> 原生实现
  -> 框架解决方案
  -> 核心源码
  -> 同类对比
  -> 工程选型
  -> 综合架构
```

## 推荐学习路径

1. 先建立 Android 开源框架全景与评价维度；
2. 再学习图片、网络、接口代理和数据解析等数据获取框架；
3. 继续深入 RxJava、Coroutines、Flow 和 Hilt 等线程、数据流与依赖管理能力；
4. 最后通过 Room、DataStore、WorkManager、Paging、Navigation、测试与性能工具完成综合工程实践。

## 已完成章节

| 章节 | 内容 | 状态 |
| --- | --- | --- |
| [第 1 章：Android 开源框架全景](/chapter1/) | 建立课程全景、问题域、生态演进与现代 Android 框架地图 | 已完成 |
| [第 2 章：如何学习和评价一个开源框架](/chapter2/) | 学习框架的方法、源码阅读路径、评价维度、选型矩阵与框架治理 | 已完成 |
| [第 3 章：图片加载框架](/chapter3/) | 商品列表场景、Coil 与 Glide 主流程、设计模式、缓存解码、列表复用与可玩实验 | 已完成 |

## 课程阶段

| 阶段 | 主题 | 说明 |
| --- | --- | --- |
| 第一阶段 | 认识 Android 开源框架 | 建立全景地图，掌握学习框架与评价框架的方法 |
| 第二阶段 | 数据获取与处理框架 | 学习图片、网络、接口代理和数据解析框架 |
| 第三阶段 | 线程、数据流与依赖管理 | 学习异步编程、响应式数据流和依赖注入 |
| 第四阶段 | 本地数据、后台任务与架构 | 学习存储、任务调度、分页、导航和状态管理 |
| 第五阶段 | 工程质量与综合实践 | 学习测试、调试、性能优化和综合项目选型 |
