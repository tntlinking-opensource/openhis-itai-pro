<script setup lang="ts">
import type {
  ScheduleCalendarCellDto,
  ScheduleCalendarMonthDayDto,
  ScheduleMonthSummaryDto,
} from '#/api/booking/outpatient-schedule';

defineProps<{
  days: ScheduleCalendarMonthDayDto[];
  summary?: ScheduleMonthSummaryDto;
  loading?: boolean;
}>();

const emit = defineEmits<{
  selectDay: [payload: { date: string; schedules: ScheduleCalendarCellDto[] }];
}>();

const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日'];

function dayNumber(date?: string) {
  if (!date) return '';
  return date.slice(8);
}

function handleSelect(day: ScheduleCalendarMonthDayDto) {
  if (!day.date) return;
  emit('selectDay', { date: day.date, schedules: day.schedules ?? [] });
}
</script>

<template>
  <div v-loading="loading" class="schedule-month-calendar">
    <header v-if="summary" class="schedule-month-calendar__summary">
      <span>已发布 {{ summary.publishedDayCount ?? 0 }} 天</span>
      <span>草稿 {{ summary.draftDayCount ?? 0 }} 天</span>
      <span>未排 {{ summary.unscheduledDayCount ?? 0 }} 天</span>
      <span>已约 {{ summary.bookedAppointmentCount ?? 0 }} 条</span>
    </header>

    <div class="schedule-month-calendar__weekdays">
      <div v-for="label in weekdayLabels" :key="label" class="schedule-month-calendar__weekday">
        {{ label }}
      </div>
    </div>

    <div class="schedule-month-calendar__grid">
      <button
        v-for="day in days"
        :key="day.date"
        type="button"
        class="schedule-month-calendar__cell"
        :class="{ 'is-outside': day.inMonth === false }"
        @click="handleSelect(day)"
      >
        <span class="schedule-month-calendar__date">{{ dayNumber(day.date) }}</span>
        <div class="schedule-month-calendar__badges">
          <span v-if="day.activeCount" class="badge is-active">发 {{ day.activeCount }}</span>
          <span v-if="day.draftCount" class="badge is-draft">草 {{ day.draftCount }}</span>
          <span v-if="day.inactiveCount" class="badge is-inactive">停 {{ day.inactiveCount }}</span>
        </div>
      </button>
    </div>
  </div>
</template>

<style scoped>
.schedule-month-calendar {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.schedule-month-calendar__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 8px 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.schedule-month-calendar__weekdays {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
}

.schedule-month-calendar__weekday {
  padding: 6px 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.schedule-month-calendar__grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
  min-height: 420px;
}

.schedule-month-calendar__cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 72px;
  padding: 6px;
  text-align: left;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.schedule-month-calendar__cell.is-outside {
  opacity: 0.45;
  background: var(--el-fill-color-lighter);
}

.schedule-month-calendar__date {
  font-size: 13px;
  font-weight: 600;
}

.schedule-month-calendar__badges {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.badge {
  display: inline-block;
  padding: 0 4px;
  font-size: 11px;
  line-height: 18px;
  border-radius: 4px;
}

.badge.is-active {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.badge.is-draft {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.badge.is-inactive {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}
</style>
