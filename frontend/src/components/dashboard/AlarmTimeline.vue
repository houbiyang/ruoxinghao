<script setup lang="ts">
import type { AlarmEvent } from '@/types/system'

defineProps<{
  events: AlarmEvent[]
}>()
</script>

<template>
  <div class="alarm-list">
    <article
      v-for="eventItem in events"
      :key="eventItem.eventId"
      class="alarm-item"
      :data-level="eventItem.level"
    >
      <div class="alarm-item__time">{{ eventItem.timestamp }}</div>
      <div class="alarm-item__content">
        <div class="alarm-item__header">
          <strong>{{ eventItem.title }}</strong>
          <span>{{ eventItem.level }}</span>
        </div>
        <p>{{ eventItem.description }}</p>
      </div>
    </article>
  </div>
</template>

<style scoped>
.alarm-list {
  display: grid;
  gap: 14px;
}

.alarm-item {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid rgba(222, 198, 150, 0.12);
}

.alarm-item:last-child {
  border-bottom: 0;
}

.alarm-item__time {
  color: var(--color-gold-400);
  font-size: 12px;
  line-height: 1.8;
}

.alarm-item__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.alarm-item__header span {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
}

.alarm-item[data-level='提示'] .alarm-item__header span {
  background: rgba(53, 214, 166, 0.12);
  color: var(--color-cyan-400);
}

.alarm-item[data-level='预警'] .alarm-item__header span {
  background: rgba(245, 215, 110, 0.14);
  color: var(--color-yellow-500);
}

.alarm-item[data-level='严重'] .alarm-item__header span {
  background: rgba(239, 100, 87, 0.14);
  color: var(--color-red-500);
}

.alarm-item__content p {
  margin: 8px 0 0;
  color: var(--color-text-300);
  line-height: 1.7;
  font-size: 13px;
}
</style>
