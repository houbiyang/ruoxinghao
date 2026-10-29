<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

const route = useRoute()

const navigationItems = [
  { name: '总览驾驶舱', path: '/' },
  { name: '场站实时监控', path: '/stations' },
  { name: '多智能体调度中心', path: '/agent-dispatch' },
  { name: '市场与电价', path: '/market-pricing' },
  { name: '实时注入', path: '/fault-lab' },
  { name: '实验结果对比', path: '/results' },
  { name: '系统配置', path: '/settings' }
]

const isNavigationActive = computed(() => {
  return (path: string) => {
    if (path === '/') {
      return route.path === '/'
    }

    return route.path === path || route.path.startsWith(`${path}/`)
  }
})

const isFullScreenRoute = computed(() => {
  return route.path === '/agent-dispatch'
    || route.path === '/market-pricing'
    || route.path === '/fault-lab'
    || route.path === '/results'
})
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--agent-dispatch': isFullScreenRoute }">
    <header class="app-topbar">
      <RouterLink class="app-topbar__brand" to="/" aria-label="返回总览驾驶舱">
        <span class="app-topbar__brand-mark">VPP</span>
        <span>
          <strong>新能源虚拟电厂</strong>
          <small>弱信号协同控制台</small>
        </span>
      </RouterLink>
      <nav class="app-topbar__nav" aria-label="主导航">
        <RouterLink
          v-for="item in navigationItems"
          :key="item.path"
          :to="item.path"
          class="app-topbar__link"
          :class="{ 'is-active': isNavigationActive(item.path) }"
          :aria-current="isNavigationActive(item.path) ? 'page' : undefined"
        >
          {{ item.name }}
        </RouterLink>
      </nav>
    </header>

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>
