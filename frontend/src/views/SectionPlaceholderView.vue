<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const pageTitle = computed(() => String(route.meta.title ?? '功能页面'))
const pageOverview = computed(() => String(route.meta.overview ?? ''))
const modules = computed(() => ((route.meta.modules as string[] | undefined) ?? []))
</script>

<template>
  <section class="placeholder-screen page-shell">
    <header class="placeholder-screen__header">
      <div>
        <h2>{{ pageTitle }}</h2>
        <p>{{ pageOverview }}</p>
      </div>
      <span>第一阶段骨架页面</span>
    </header>

    <section class="placeholder-screen__panel">
      <article v-for="moduleName in modules" :key="moduleName" class="placeholder-screen__card">
        <h3>{{ moduleName }}</h3>
        <p>本区域将承接该模块的真实数据契约、图表与控制交互，当前只保留比赛演示结构。</p>
      </article>
    </section>
  </section>
</template>

<style scoped>
.placeholder-screen {
  display: grid;
  gap: 16px;
}

.placeholder-screen__header,
.placeholder-screen__panel {
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.8), rgba(4, 24, 95, 0.84));
}

.placeholder-screen__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 20px;
}

.placeholder-screen__header h2,
.placeholder-screen__header p {
  margin: 0;
}

.placeholder-screen__header h2 {
  font-size: 28px;
}

.placeholder-screen__header p {
  margin-top: 8px;
  color: #a8ddff;
  line-height: 1.7;
}

.placeholder-screen__header span {
  color: #62ffb6;
  white-space: nowrap;
}

.placeholder-screen__panel {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  padding: 18px;
}

.placeholder-screen__card {
  padding: 16px;
  border: 1px solid rgba(72, 224, 255, 0.18);
  border-radius: 6px;
  background: rgba(4, 29, 111, 0.54);
}

.placeholder-screen__card h3,
.placeholder-screen__card p {
  margin: 0;
}

.placeholder-screen__card p {
  margin-top: 8px;
  color: #b3e8ff;
  line-height: 1.7;
  font-size: 13px;
}

@media (max-width: 768px) {
  .placeholder-screen__header {
    flex-direction: column;
  }
}
</style>
