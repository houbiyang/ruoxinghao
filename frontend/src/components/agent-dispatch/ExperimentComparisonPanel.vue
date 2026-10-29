<script setup lang="ts">
import { comparisonTrendClass, formatSignedNumber, formatValue } from '@/components/agent-dispatch/agentDispatchMeta'
import type { DispatchComparisonSummary } from '@/types/system'

const props = defineProps<{
  comparison: DispatchComparisonSummary
}>()

function ratioWidth(beforeValue: number | null, afterValue: number | null, currentValue: number | null): string {
  if (beforeValue === null || afterValue === null || currentValue === null) {
    return '0%'
  }
  const baseline = Math.max(Math.abs(beforeValue), Math.abs(afterValue), 1)
  return `${Math.max(8, Math.min(100, (Math.abs(currentValue) / baseline) * 100))}%`
}
</script>

<template>
  <section class="comparison-panel">
    <header class="comparison-panel__header">
      <div>
        <p>实验结果对比</p>
        <h3>{{ comparison.scenarioName ?? '暂无对比' }}</h3>
      </div>
      <div class="comparison-panel__run">
        <span>Run ID</span>
        <strong>{{ comparison.runId ?? '--' }}</strong>
      </div>
    </header>

    <div class="comparison-panel__list">
      <article v-for="metric in comparison.metrics" :key="metric.metricCode" :class="comparisonTrendClass(metric)">
        <div class="comparison-panel__name">
          <strong>{{ metric.metricName }}</strong>
          <small>{{ metric.unit }}</small>
        </div>
        <div class="comparison-panel__values">
          <div class="comparison-panel__value-group">
            <span>调度前</span>
            <strong>{{ formatValue(metric.beforeValue, 1) }}</strong>
            <div class="comparison-panel__bar">
              <i class="comparison-panel__bar-fill comparison-panel__bar-fill--before" :style="{ width: ratioWidth(metric.beforeValue, metric.afterValue, metric.beforeValue) }" />
            </div>
          </div>
          <b>→</b>
          <div class="comparison-panel__value-group">
            <span>调度后</span>
            <strong>{{ formatValue(metric.afterValue, 1) }}</strong>
            <div class="comparison-panel__bar">
              <i class="comparison-panel__bar-fill comparison-panel__bar-fill--after" :style="{ width: ratioWidth(metric.beforeValue, metric.afterValue, metric.afterValue) }" />
            </div>
          </div>
        </div>
        <div class="comparison-panel__improvement">
          <i>{{ metric.improved === null ? '暂无对比' : metric.improved ? '改善' : '恶化' }}</i>
          <strong>{{ metric.improvementValue === null ? '--' : formatSignedNumber(Number(metric.improvementValue), 1) }}</strong>
          <span>{{ metric.improvementRate === null ? '--' : `${metric.improvementRate.toFixed(1)}%` }}</span>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.comparison-panel {
  display: grid;
  gap: 8px;
}

.comparison-panel__header,
.comparison-panel__list article {
  border: 1px solid rgba(72, 224, 255, 0.24);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(6, 28, 104, 0.94), rgba(3, 16, 62, 0.98));
}

.comparison-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 12px;
}

.comparison-panel__header p,
.comparison-panel__header h3 {
  margin: 0;
}

.comparison-panel__header p,
.comparison-panel__run span {
  color: #92dbff;
  font-size: 12px;
}

.comparison-panel__header h3 {
  margin-top: 4px;
  font-size: 18px;
}

.comparison-panel__run {
  display: grid;
  align-content: center;
  justify-items: end;
}

.comparison-panel__run strong {
  margin-top: 6px;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.comparison-panel__list {
  display: grid;
  gap: 5px;
}

.comparison-panel__list article {
  display: grid;
  grid-template-columns: 1fr 1.65fr 0.75fr;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
}

.comparison-panel__name {
  display: grid;
  gap: 6px;
}

.comparison-panel__name strong {
  font-size: 13px;
}

.comparison-panel__name small,
.comparison-panel__values span,
.comparison-panel__value-group strong,
.comparison-panel__improvement i,
.comparison-panel__improvement span {
  color: #95dcff;
  font-style: normal;
}

.comparison-panel__values {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.comparison-panel__values b {
  color: #48e0ff;
  font-size: 16px;
}

.comparison-panel__value-group {
  display: grid;
  gap: 4px;
}

.comparison-panel__value-group strong {
  color: #f1fbff;
  font-size: 15px;
  line-height: 1;
}

.comparison-panel__bar {
  position: relative;
  height: 10px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(7, 25, 92, 0.9);
  border: 1px solid rgba(66, 205, 255, 0.15);
}

.comparison-panel__bar-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.comparison-panel__bar-fill--before {
  background: linear-gradient(90deg, rgba(47, 163, 255, 0.46), #45b0ff);
}

.comparison-panel__bar-fill--after {
  background: linear-gradient(90deg, rgba(120, 255, 119, 0.46), #72ff79);
}

.comparison-panel__improvement {
  display: grid;
  justify-items: end;
  gap: 3px;
}

.comparison-panel__improvement strong {
  font-size: 15px;
}

.comparison-panel__list article.is-improved .comparison-panel__improvement strong {
  color: #70ff79;
}

.comparison-panel__list article.is-worsened .comparison-panel__improvement strong {
  color: #ff8c65;
}

.comparison-panel__list article.is-neutral .comparison-panel__improvement strong {
  color: #effaff;
}

@media (max-width: 1200px) {
  .comparison-panel__list article {
    grid-template-columns: 1fr;
    justify-items: start;
  }

  .comparison-panel__improvement {
    justify-content: flex-start;
  }
}
</style>
