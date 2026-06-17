<script setup lang="ts">
import type { CalendarExceptionMonthDayDto } from '#/api/booking/calendar-exception';

defineProps<{
  days: CalendarExceptionMonthDayDto[];
  loading?: boolean;
}>();

const emit = defineEmits<{
  selectDay: [date: string];
}>();

const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日'];

const typeClassMap: Record<string, string> = {
  CLOSED: 'is-closed',
  REDUCED: 'is-reduced',
  OVERRIDE: 'is-override',
  EXTRA: 'is-extra',
};

function dayNumber(date?: string) {
  if (!date) return '';
  return date.slice(8);
}

function badgeClass(type: string) {
  return typeClassMap[type] ?? '';
}

function handleSelect(day: CalendarExceptionMonthDayDto) {
  if (!day.date) return;
  emit('selectDay', day.date);
}
</script>

<template>
  <div v-loading="loading" class="calendar-exception-month">
    <div class="calendar-exception-month__weekdays">
      <div v-for="label in weekdayLabels" :key="label" class="calendar-exception-month__weekday">
        {{ label }}
      </div>
    </div>
    <div class="calendar-exception-month__grid">
      <button
        v-for="day in days"
        :key="day.date"
        type="button"
        class="calendar-exception-month__cell"
        :class="{ 'is-outside': day.inMonth === false }"
        @click="handleSelect(day)"
      >
        <span class="calendar-exception-month__date">{{ dayNumber(day.date) }}</span>
        <div class="calendar-exception-month__badges">
          <span
            v-for="type in day.exceptionTypes || []"
            :key="type"
            class="badge"
            :class="badgeClass(type)"
          >
            {{ type }}
          </span>
        </div>
      </button>
    </div>
  </div>
</template>

<style scoped>
.calendar-exception-month {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.calendar-exception-month__weekdays,
.calendar-exception-month__grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
}

.calendar-exception-month__weekday {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.calendar-exception-month__cell {
  min-height: 72px;
  padding: 6px;
  text-align: left;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.calendar-exception-month__cell.is-outside {
  opacity: 0.45;
}

.calendar-exception-month__date {
  font-size: 13px;
  font-weight: 600;
}

.calendar-exception-month__badges {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 4px;
}

.badge {
  display: inline-block;
  padding: 0 4px;
  font-size: 10px;
  line-height: 16px;
  border-radius: 3px;
}

.badge.is-closed {
  color: #fff;
  background: #f56c6c;
}

.badge.is-reduced {
  color: #fff;
  background: #e6a23c;
}

.badge.is-override {
  color: #fff;
  background: #409eff;
}

.badge.is-extra {
  color: #fff;
  background: #67c23a;
}
</style>
