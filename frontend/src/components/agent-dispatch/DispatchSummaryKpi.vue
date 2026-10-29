<script setup lang="ts">
import { computed } from 'vue'

import { formatSignedNumber, formatValue } from '@/components/agent-dispatch/agentDispatchMeta'
import type { DispatchMetricValue, DispatchSummary } from '@/types/system'

const props = defineProps<{
  summary: DispatchSummary
}>()

const cards = computed(() => [
  props.summary.plannedTargetPower,
  props.summary.actualPower,
  props.summary.trackingErrorRate,
  props.summary.revenueImprovement,
  props.summary.curtailmentReduction,
  props.summary.reserveSatisfactionRate,
  props.summary.executionSuccessRate
])

function displayValue(metric: DispatchMetricValue): string {
  if (!metric.available) {
    return '--'
  }
  return typeof metric.value === 'number' ? formatSignedNumber(metric.value, 1) : formatValue(metric.value, 1)
}

function cardClass(metric: DispatchMetricValue): string {
  if (!metric.available) {
    return 'is-unavailable'
  }
  if (metric.higherIsBetter === null || metric.trendDirection === 'UNKNOWN') {
    return 'is-neutral'
  }
  if (metric.higherIsBetter) {
    return metric.trendDirection === 'UP' ? 'is-positive' : metric.trendDirection === 'DOWN' ? 'is-negative' : 'is-neutral'
  }
  return metric.trendDirection === 'DOWN' ? 'is-positive' : metric.trendDirection === 'UP' ? 'is-negative' : 'is-neutral'
}
</script>

<template>
  <section class="dispatch-kpi">
    <header class="dispatch-kpi__header">
      <div>
        <p>调度结果总览</p>
        <h3>实时 KPI</h3>
      </div>
      <div class="dispatch-kpi__plan">
        <span>计划版本</span>
        <strong>{{ summary.planVersion ?? '--' }}</strong>
      </div>
    </header>

    <div class="dispatch-kpi__grid">
      <article v-for="metric in cards" :key="metric.label" :class="cardClass(metric)">
        <span>{{ metric.label }}</span>
        <strong>{{ displayValue(metric) }}</strong>
        <small>{{ metric.unit }}</small>
      </article>
    </div>
  </section>
</template>

<style scoped>
.dispatch-kpi {
  display: grid;
  gap: 8px;
}

.dispatch-kpi__header,
.dispatch-kpi__grid article {
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(7, 30, 110, 0.94), rgba(4, 16, 63, 0.98));
}

.dispatch-kpi__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 12px;
}

.dispatch-kpi__header p,
.dispatch-kpi__header h3 {
  margin: 0;
}

.dispatch-kpi__header p,
.dispatch-kpi__plan span {
  color: #92dbff;
  font-size: 12px;
}

.dispatch-kpi__header h3 {
  margin-top: 4px;
  font-size: 18px;
}

.dispatch-kpi__plan {
  display: grid;
  align-content: center;
  justify-items: end;
}

.dispatch-kpi__plan strong {
  margin-top: 4px;
  font-size: 15px;
}

.dispatch-kpi__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
}

.dispatch-kpi__grid article {
  display: grid;
  gap: 3px;
  padding: 10px 10px 8px;
  min-height: 88px;
}

.dispatch-kpi__grid span,
.dispatch-kpi__grid small {
  color: #96dcff;
  font-size: 12px;
}

.dispatch-kpi__grid strong {
  font-size: 17px;
  line-height: 1.05;
}

.dispatch-kpi__grid article.is-positive strong {
  color: #76ff7d;
}

.dispatch-kpi__grid article.is-negative strong {
  color: #ff845f;
}

.dispatch-kpi__grid article.is-neutral strong {
  color: #effaff;
}

.dispatch-kpi__grid article.is-unavailable strong {
  color: #9bc4dd;
}

@media (max-width: 1280px) {
  .dispatch-kpi__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .dispatch-kpi__header {
    flex-direction: column;
    align-items: stretch;
  }

  .dispatch-kpi__plan {
    justify-items: start;
  }

  .dispatch-kpi__grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
