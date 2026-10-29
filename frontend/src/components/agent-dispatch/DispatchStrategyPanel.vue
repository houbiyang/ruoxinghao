<script setup lang="ts">
import { computed } from 'vue'

import { strategyItemClass, strategyStatusLabelMap } from '@/components/agent-dispatch/agentDispatchMeta'
import { controlModeLabelMap } from '@/mock/systemData'
import type { AgentDispatchStrategy, DispatchStrategyItem } from '@/types/system'

const props = defineProps<{
  strategy: AgentDispatchStrategy
  highlightedResourceIds: string[]
}>()

const emit = defineEmits<{
  'focus-resources': [resourceIds: string[]]
  'clear-focus': []
}>()

const groups = computed(() => [
  props.strategy.loadSideActions,
  props.strategy.generationSideActions,
  props.strategy.storageActions,
  props.strategy.gridActions
])

function handleSelect(item: DispatchStrategyItem): void {
  emit('focus-resources', item.affectedResourceIds)
}

function isHighlighted(item: DispatchStrategyItem): boolean {
  return item.affectedResourceIds.some((resourceId) => props.highlightedResourceIds.includes(resourceId))
}
</script>

<template>
  <section class="dispatch-panel">
    <header class="dispatch-panel__header">
      <div>
        <p>调度策略面板</p>
        <h3>{{ strategy.currentStrategyTitle }}</h3>
      </div>
      <div class="dispatch-panel__mode">
        <span>模式</span>
        <strong>{{ controlModeLabelMap[strategy.strategyMode] ?? strategy.strategyMode }}</strong>
      </div>
    </header>

    <div class="dispatch-panel__groups">
      <article v-for="(group, index) in groups" :key="group.groupType" class="dispatch-group">
        <header class="dispatch-group__header">
          <div>
            <span>{{ String(index + 1).padStart(2, '0') }}</span>
            <div class="dispatch-group__title-copy">
              <strong>{{ group.groupTitle }}</strong>
              <em>{{ group.groupSubtitle }}</em>
            </div>
          </div>
          <small>▸</small>
        </header>

        <div v-if="group.items.length > 0" class="dispatch-group__items">
          <button
            v-for="item in group.items"
            :key="item.strategyCode"
            type="button"
            class="dispatch-item"
            :class="[...strategyItemClass(item.status, item.adoptedBySafetyGate), { 'is-highlighted': isHighlighted(item) }]"
            @click="handleSelect(item)"
          >
            <div class="dispatch-item__title">
              <strong>{{ item.strategyName }}</strong>
              <span>{{ strategyStatusLabelMap[item.status] }}</span>
            </div>
            <div class="dispatch-item__meta">
              <b>{{ item.targetDeltaPower === null ? '--' : `${item.targetDeltaPower > 0 ? '+' : ''}${item.targetDeltaPower.toFixed(1)}` }} {{ item.unit }}</b>
              <i>{{ item.adoptedBySafetyGate ? '安全门采纳' : '未采纳' }}</i>
            </div>
            <p>{{ item.reason }}</p>
            <small>影响资源：{{ item.affectedResourceIds.join('、') || '无' }}</small>
          </button>
        </div>

        <div v-else class="dispatch-group__empty">当前无调度动作</div>
      </article>
    </div>

    <button type="button" class="dispatch-panel__reset" @click="emit('clear-focus')">取消高亮</button>
  </section>
</template>

<style scoped>
.dispatch-panel {
  display: grid;
  height: 100%;
  gap: 8px;
}

.dispatch-panel__header,
.dispatch-group,
.dispatch-panel__reset {
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(8, 30, 112, 0.92), rgba(3, 16, 67, 0.96)),
    linear-gradient(90deg, rgba(63, 173, 255, 0.08), transparent);
  box-shadow: inset 0 0 18px rgba(63, 173, 255, 0.08);
}

.dispatch-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 14px;
}

.dispatch-panel__header p,
.dispatch-panel__header h3 {
  margin: 0;
}

.dispatch-panel__header p {
  color: #8fdfff;
  font-size: 12px;
}

.dispatch-panel__header h3 {
  margin-top: 3px;
  font-size: 18px;
  letter-spacing: 0.06em;
}

.dispatch-panel__mode {
  display: grid;
  align-content: center;
  justify-items: end;
  color: #9ad8ff;
  font-size: 12px;
}

.dispatch-panel__mode strong {
  margin-top: 4px;
  color: #62ffb6;
  font-size: 14px;
}

.dispatch-panel__groups {
  display: grid;
  gap: 10px;
}

.dispatch-group {
  padding: 8px;
}

.dispatch-group__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.dispatch-group__header div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dispatch-group__title-copy {
  display: grid;
  gap: 2px;
}

.dispatch-group__header span {
  color: #62ffb6;
  font-size: 20px;
  font-style: italic;
  font-weight: 700;
}

.dispatch-group__header strong {
  font-size: 15px;
}

.dispatch-group__header em {
  color: #69ff7d;
  font-style: normal;
  font-size: 12px;
}

.dispatch-group__header small {
  color: #8fdfff;
  font-size: 13px;
}

.dispatch-group__items {
  display: grid;
  gap: 8px;
}

.dispatch-item {
  display: grid;
  gap: 6px;
  width: 100%;
  padding: 9px 10px;
  text-align: left;
  border: 1px solid rgba(72, 224, 255, 0.18);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(4, 24, 91, 0.92), rgba(4, 20, 68, 0.98));
  color: #edf9ff;
}

.dispatch-item.is-highlighted {
  border-color: rgba(255, 211, 108, 0.72);
  box-shadow: inset 0 0 18px rgba(255, 211, 108, 0.16);
}

.dispatch-item.is-executing,
.dispatch-item.is-adopted {
  border-color: rgba(98, 255, 182, 0.44);
}

.dispatch-item.is-rejected {
  border-color: rgba(239, 100, 87, 0.46);
}

.dispatch-item.is-standby,
.dispatch-item.is-candidate {
  border-color: rgba(160, 211, 255, 0.26);
}

.dispatch-item__title,
.dispatch-item__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dispatch-item__title span {
  color: #62ffb6;
  font-size: 10px;
}

.dispatch-item__meta b {
  color: #89ff71;
  font-size: 15px;
}

.dispatch-item__meta i,
.dispatch-item p,
.dispatch-item small {
  color: #9bd7ff;
  font-style: normal;
  font-size: 12px;
}

.dispatch-item p {
  margin: 0;
  line-height: 1.35;
}

.dispatch-group__empty {
  padding: 18px 12px;
  color: #8fdfff;
  text-align: center;
}

.dispatch-panel__reset {
  padding: 10px 12px;
  border-radius: 8px;
  color: #d9f6ff;
}

@media (max-width: 1200px) {
  .dispatch-panel__header {
    flex-direction: column;
    align-items: stretch;
  }

  .dispatch-panel__mode {
    justify-items: start;
  }
}
</style>
