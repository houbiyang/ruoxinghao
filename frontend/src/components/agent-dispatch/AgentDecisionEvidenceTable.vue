<script setup lang="ts">
import { computed } from 'vue'

import { decisionRowModeLabel, formatConfidencePercent } from '@/components/agent-dispatch/agentDispatchMeta'
import type { AgentDecisionSummary } from '@/types/system'

const props = defineProps<{
  summary: AgentDecisionSummary
}>()

const hasRows = computed(() => props.summary.rows.length > 0)
</script>

<template>
  <section class="decision-panel">
    <header class="decision-panel__header">
      <div>
        <p>多智能体决策证据</p>
        <h3>协作证据链</h3>
      </div>
      <div class="decision-panel__mode">
        <span>模式</span>
        <strong>{{ summary.modeLabel }}</strong>
      </div>
    </header>

    <div v-if="hasRows" class="decision-table">
      <div class="decision-table__head">
        <span>智能体</span>
        <span>可用状态</span>
        <span>置信度</span>
        <span>建议动作</span>
        <span>最终裁决</span>
        <span>进入裁决</span>
      </div>
      <article v-for="row in summary.rows" :key="row.agentType" class="decision-table__row">
        <strong>{{ row.agentName }}</strong>
        <span>{{ decisionRowModeLabel(row) }}</span>
        <span>{{ formatConfidencePercent(row.confidence, 0) }}</span>
        <span>{{ row.suggestedAction }}</span>
        <span>{{ row.finalDecision }}</span>
        <span :class="{ 'is-adopted': row.adopted, 'is-rejected': !row.adopted }">{{ row.adopted ? '采纳' : '未采纳' }}</span>
      </article>
    </div>
    <div v-else class="decision-panel__empty">当前没有本轮智能体记录</div>

    <footer class="decision-panel__footer">
      <span>裁决级别：{{ summary.safetyDecisionLevel }}</span>
      <span>综合裁决：{{ summary.overallDecision }}</span>
      <span>安全门：{{ summary.safetyGatePassed === null ? '--' : summary.safetyGatePassed ? '通过' : '拒绝' }}</span>
      <span>协作状态：{{ summary.ruleFallbackActive ? '规则保守模式' : '结构化协同' }}</span>
      <span>回退原因：{{ summary.fallbackReason ?? '--' }}</span>
    </footer>
  </section>
</template>

<style scoped>
.decision-panel {
  display: grid;
  gap: 8px;
}

.decision-panel__header,
.decision-table,
.decision-panel__footer,
.decision-panel__empty {
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(6, 28, 104, 0.94), rgba(3, 16, 62, 0.98));
}

.decision-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 12px;
}

.decision-panel__header p,
.decision-panel__header h3 {
  margin: 0;
}

.decision-panel__header p,
.decision-panel__mode span {
  color: #92dbff;
  font-size: 12px;
}

.decision-panel__header h3 {
  margin-top: 4px;
  font-size: 18px;
}

.decision-panel__mode {
  display: grid;
  align-content: center;
  justify-items: end;
}

.decision-panel__mode strong {
  margin-top: 6px;
  color: #62ffb6;
  font-size: 15px;
}

.decision-table {
  overflow: hidden;
}

.decision-table__head,
.decision-table__row {
  display: grid;
  grid-template-columns: 1.05fr 0.74fr 0.56fr 1.45fr 0.7fr 0.5fr;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
}

.decision-table__head {
  color: #9ddfff;
  font-size: 12px;
  border-bottom: 1px solid rgba(72, 224, 255, 0.18);
  background: rgba(5, 29, 101, 0.56);
}

.decision-table__row + .decision-table__row {
  border-top: 1px solid rgba(72, 224, 255, 0.1);
}

.decision-table__row strong {
  color: #f3fbff;
  font-size: 13px;
}

.decision-table__row span {
  color: #9ddfff;
  font-size: 12px;
}

.decision-table__row span.is-adopted {
  color: #78ff7d;
  font-weight: 700;
}

.decision-table__row span.is-rejected {
  color: #ff8862;
  font-weight: 700;
}

.decision-panel__footer,
.decision-panel__empty {
  padding: 12px 14px;
}

.decision-panel__footer {
  display: grid;
  gap: 6px;
  color: #9ddfff;
}

.decision-panel__empty {
  color: #9ddfff;
  text-align: center;
}

@media (max-width: 1200px) {
  .decision-table__head {
    display: none;
  }

  .decision-table__row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .decision-panel__header {
    flex-direction: column;
    align-items: stretch;
  }

  .decision-panel__mode {
    justify-items: start;
  }
}
</style>
