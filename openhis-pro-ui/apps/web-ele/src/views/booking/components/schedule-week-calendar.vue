<script setup lang="ts">
import type {
  ScheduleCalendarCellDto,
  ScheduleCalendarDayDto,
} from '#/api/booking/outpatient-schedule';

import {
  dayLabel,
  formatScheduleStatus,
  scheduleStatusClass,
} from '#/api/booking/booking-constants';

defineProps<{
  days: ScheduleCalendarDayDto[];
  loading?: boolean;
}>();

const emit = defineEmits<{
  select: [payload: { date: string; schedule: ScheduleCalendarCellDto }];
}>();

function cellKey(dayDate: string | undefined, schedule: ScheduleCalendarCellDto) {
  if (schedule.scheduleId) {
    return `${dayDate}-${schedule.scheduleId}`;
  }
  return `${dayDate}-tpl-${schedule.dayScheduleTemplateId ?? schedule.weekScheduleTemplateId ?? schedule.name}`;
}

function handleSelect(date: string | undefined, schedule: ScheduleCalendarCellDto) {
  if (!date) return;
  emit('select', { date, schedule });
}
</script>

<template>
  <div v-loading="loading" class="schedule-week-calendar">
    <div class="schedule-week-calendar__grid">
      <div v-for="day in days" :key="day.date" class="schedule-week-calendar__day">
        <header class="schedule-week-calendar__day-title">
          {{ dayLabel(day.date || '') }}
        </header>
        <div class="schedule-week-calendar__cells">
          <button
            v-for="schedule in day.schedules || []"
            :key="cellKey(day.date, schedule)"
            type="button"
            class="schedule-week-calendar__cell"
            :class="[
              scheduleStatusClass(schedule.statusEnum),
              schedule.bookable === false ? 'is-template-readonly' : '',
            ]"
            @click="handleSelect(day.date, schedule)"
          >
            <span class="schedule-week-calendar__cell-name">{{ schedule.name || '未命名' }}</span>
            <span class="schedule-week-calendar__cell-status">
              {{ formatScheduleStatus(schedule.statusEnum) }}
              <template v-if="schedule.fromTemplate && schedule.bookable"> · 可约</template>
              <template v-if="schedule.substituteDisplay">
                · {{ schedule.substituteDisplay }}</template
              >
            </span>
          </button>
          <div v-if="!day.schedules?.length" class="schedule-week-calendar__empty">—</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.schedule-week-calendar__grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
  min-height: 320px;
}

.schedule-week-calendar__day {
  display: flex;
  flex-direction: column;
  min-height: 280px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.schedule-week-calendar__day-title {
  padding: 8px 10px;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.schedule-week-calendar__cells {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
}

.schedule-week-calendar__cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  text-align: left;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 6px;
}

.schedule-week-calendar__cell.is-draft {
  background: var(--el-fill-color-light);
}

.schedule-week-calendar__cell.is-active {
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-5);
}

.schedule-week-calendar__cell.is-inactive {
  background: var(--el-color-danger-light-9);
  opacity: 0.85;
}

.schedule-week-calendar__cell.is-template {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
  border-style: dashed;
}

.schedule-week-calendar__cell.is-template-readonly {
  opacity: 0.75;
}

.schedule-week-calendar__cell-name {
  font-size: 13px;
  font-weight: 600;
}

.schedule-week-calendar__cell-status {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.schedule-week-calendar__empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-placeholder);
}
</style>
