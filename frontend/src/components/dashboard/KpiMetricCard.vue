<script setup lang="ts">
defineProps<{
  label: string
  value: string
  description: string
  accent: string
}>()
</script>

<template>
  <article class="kpi-card" :style="{ '--accent-color': accent }">
    <p class="kpi-card__label">{{ label }}</p>
    <strong class="kpi-card__value">{{ value }}</strong>
    <p class="kpi-card__description">{{ description }}</p>
  </article>
</template>

<style scoped>
.kpi-card {
  padding: 18px 18px 16px;
  border: 1px solid rgba(222, 198, 150, 0.18);
  border-radius: 24px 8px 24px 8px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent),
    linear-gradient(135deg, color-mix(in srgb, var(--accent-color) 16%, transparent), rgba(16, 22, 32, 0.72));
  box-shadow: 0 16px 28px rgba(4, 8, 14, 0.24);
}

.kpi-card__label,
.kpi-card__description {
  margin: 0;
}

.kpi-card__label {
  color: var(--color-text-300);
  font-size: 13px;
}

.kpi-card__value {
  display: block;
  margin-top: 10px;
  font-size: 30px;
  color: var(--accent-color);
}

.kpi-card__description {
  margin-top: 8px;
  color: var(--color-text-500);
  line-height: 1.6;
  font-size: 12px;
}
</style>
