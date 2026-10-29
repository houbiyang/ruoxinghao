<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { linkStateMetaMap } from '@/mock/systemData'
import { LinkState } from '@/types/system'

import type { LinkSwitchLogEntry } from './linkQualityUtils'

const props = defineProps<{
  entries: LinkSwitchLogEntry[]
  stationScopeLabel: string
}>()

const selectedState = ref<'all' | LinkState>('all')

const filteredEntries = computed(() => {
  return props.entries.filter((entry) => {
    return selectedState.value === 'all' || entry.currentState === selectedState.value
  })
})

watch(
  () => props.entries,
  () => {
    selectedState.value = 'all'
  }
)

function formatDateTime(timestamp: string): string {
  return new Date(timestamp).toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <section class="link-switch-log">
    <header class="link-switch-log__header">
      <div>
        <span>链路切换日志</span>
        <h3>{{ stationScopeLabel }}</h3>
      </div>
      <div class="link-switch-log__filters">
        <label>
          <span>状态筛选</span>
          <select v-model="selectedState">
            <option value="all">全部状态</option>
            <option v-for="state in Object.values(LinkState)" :key="state" :value="state">
              {{ state }} {{ linkStateMetaMap[state].label }}
            </option>
          </select>
        </label>
      </div>
    </header>

    <div v-if="filteredEntries.length > 0" class="link-switch-log__list">
      <article
        v-for="entry in filteredEntries"
        :key="entry.entryId"
        class="link-switch-log__item"
        :style="{ '--state-color': linkStateMetaMap[entry.currentState].color }"
      >
        <div class="link-switch-log__main">
          <div>
            <span>{{ formatDateTime(entry.timestamp) }}</span>
            <strong>{{ entry.stationName }}</strong>
          </div>
          <b>{{ entry.transitionLabel }}</b>
        </div>

        <div class="link-switch-log__meta">
          <p>
            <span>当前状态</span>
            <strong>{{ entry.currentState }} {{ linkStateMetaMap[entry.currentState].label }}</strong>
          </p>
          <p>
            <span>通信指标</span>
            <strong>{{ entry.latency }} ms / {{ entry.packetLossRate }}%</strong>
          </p>
          <p>
            <span>在线状态</span>
            <strong>{{ entry.online ? '在线' : '离线' }}</strong>
          </p>
          <p>
            <span>记录类型</span>
            <strong>{{ entry.transitionKind }}</strong>
          </p>
        </div>

        <p class="link-switch-log__title">{{ entry.eventTitle || '无事件标题，保留为状态采样记录。' }}</p>
      </article>
    </div>
    <p v-else class="link-switch-log__empty">当前筛选条件下没有链路切换或采样记录。</p>
  </section>
</template>

<style scoped>
.link-switch-log {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid rgba(72, 224, 255, 0.24);
  background: linear-gradient(180deg, rgba(8, 34, 128, 0.82), rgba(4, 24, 95, 0.88));
}

.link-switch-log__header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
}

.link-switch-log__header h3,
.link-switch-log__header p {
  margin: 0;
}

.link-switch-log__header span {
  color: #62ffb6;
  font-size: 13px;
}

.link-switch-log__filters {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.link-switch-log__filters label {
  display: grid;
  gap: 6px;
}

.link-switch-log__filters select {
  border: 1px solid rgba(72, 224, 255, 0.25);
  background: rgba(0, 22, 76, 0.72);
  color: #ffffff;
  padding: 8px 10px;
}

.link-switch-log__list {
  display: grid;
  gap: 10px;
  max-height: 420px;
  overflow-y: auto;
  padding-right: 4px;
}

.link-switch-log__item {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--state-color) 54%, transparent);
  border-left: 4px solid var(--state-color);
  background: rgba(4, 29, 111, 0.44);
}

.link-switch-log__main {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.link-switch-log__main span {
  display: block;
  color: #9ddfff;
  font-size: 12px;
}

.link-switch-log__main strong {
  display: block;
  margin-top: 5px;
}

.link-switch-log__main b {
  color: var(--state-color);
}

.link-switch-log__meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.link-switch-log__meta p,
.link-switch-log__title {
  margin: 0;
}

.link-switch-log__meta p {
  padding: 9px 10px;
  border: 1px solid rgba(72, 224, 255, 0.12);
  background: rgba(5, 20, 85, 0.42);
}

.link-switch-log__meta span {
  display: block;
  color: #9ddfff;
  font-size: 12px;
}

.link-switch-log__meta strong {
  display: block;
  margin-top: 6px;
}

.link-switch-log__title {
  color: #d8f6ff;
  line-height: 1.6;
}

.link-switch-log__empty {
  margin: 0;
  color: #9ddfff;
}

@media (max-width: 960px) {
  .link-switch-log__header {
    display: grid;
  }

  .link-switch-log__meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .link-switch-log__meta {
    grid-template-columns: 1fr;
  }

  .link-switch-log__main {
    display: grid;
  }
}
</style>
