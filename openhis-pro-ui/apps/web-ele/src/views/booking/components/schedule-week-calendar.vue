<script setup lang="ts">
import type {
  ScheduleCalendarCellDto,
  ScheduleCalendarDayDto,
} from '#/api/booking/outpatient-schedule';

import { computed } from 'vue';

import {
  dayLabel,
  formatIsoWeekLabel,
  formatScheduleStatus,
  scheduleStatusClass,
  todayLocalDateIso,
  weekRangeAround,
  weekRangeLabel,
} from '#/api/booking/booking-constants';
import { schedulePractitionerLabel } from '#/views/booking/components/schedule-actor-labels';
import type { BookingOptionDto } from '#/api/booking/booking-types';

const props = defineProps<{
  days: ScheduleCalendarDayDto[];
  loading?: boolean;
  /** 锚点日：双击日期列后更新，用于高亮与新建日程默认日期 */
  anchorDate?: string;
  practitioners?: BookingOptionDto[];
}>();

const emit = defineEmits<{
  select: [payload: { date: string; schedule: ScheduleCalendarCellDto }];
  create: [payload: { date: string }];
}>();

function isToday(date?: string) {
  return Boolean(date && date === todayLocalDateIso());
}

function isAnchor(date?: string) {
  return Boolean(date && props.anchorDate && date === props.anchorDate);
}

const weekdayHeaders = ['一', '二', '三', '四', '五', '六', '日'];

const anchorMonday = computed(() => {
  if (!props.anchorDate) {
    return props.days[0]?.date ?? '';
  }
  return weekRangeAround(props.anchorDate).rangeStart;
});

const weekRows = computed(() => {
  const list = props.days ?? [];
  const rows: Array<{
    key: string;
    weekDays: ScheduleCalendarDayDto[];
    label: string;
    rangeLabel: string;
    isAnchorWeek: boolean;
  }> = [];
  for (let index = 0; index < list.length; index += 7) {
    const weekDays = list.slice(index, index + 7);
    const monday = weekDays[0]?.date ?? `${index}`;
    const isAnchorWeek = monday === anchorMonday.value;
    rows.push({
      key: monday,
      weekDays,
      label: monday ? formatIsoWeekLabel(monday) : '',
      rangeLabel: monday ? weekRangeLabel(monday) : '',
      isAnchorWeek,
    });
  }
  return rows;
});

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

function handleDayDblClick(date: string | undefined, event: MouseEvent) {
  if (!date) {
    return;
  }
  const target = event.target as HTMLElement | null;
  if (target?.closest('.schedule-week-calendar__cell')) {
    return;
  }
  emit('create', { date });
}

function weekBadge(isAnchorWeek: boolean) {
  return isAnchorWeek ? '选中周' : '次周';
}
</script>

<template>
  <div v-loading="loading" class="schedule-week-calendar">
    <div class="schedule-week-calendar__weekday-header">
      <div
        v-for="label in weekdayHeaders"
        :key="label"
        class="schedule-week-calendar__weekday-header-cell"
      >
        周{{ label }}
      </div>
    </div>

    <div class="schedule-week-calendar__weeks">
      <section
        v-for="week in weekRows"
        :key="week.key"
        class="schedule-week-calendar__week-row"
        :class="{
          'is-anchor-week': week.isAnchorWeek,
          'is-preview': !week.isAnchorWeek,
        }"
      >
        <header class="schedule-week-calendar__week-banner">
          <span class="schedule-week-calendar__week-title">{{ week.label }}</span>
          <span class="schedule-week-calendar__week-range">{{ week.rangeLabel }}</span>
          <span
            class="schedule-week-calendar__week-badge"
            :class="week.isAnchorWeek ? 'is-anchor-week' : 'is-preview'"
          >
            {{ weekBadge(week.isAnchorWeek) }}
          </span>
        </header>

        <div class="schedule-week-calendar__grid">
          <div
            v-for="day in week.weekDays"
            :key="day.date"
            class="schedule-week-calendar__day"
            :class="{ 'is-anchor': isAnchor(day.date) }"
            @dblclick="handleDayDblClick(day.date, $event)"
          >
            <header class="schedule-week-calendar__day-title">
              <span class="schedule-week-calendar__day-label">{{ dayLabel(day.date || '') }}</span>
              <span v-if="isToday(day.date)" class="schedule-week-calendar__today-tag">今天</span>
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
                <span class="schedule-week-calendar__cell-name">{{
                  schedule.name || '未命名'
                }}</span>
                <span
                  v-if="schedulePractitionerLabel(schedule, practitioners)"
                  class="schedule-week-calendar__cell-practitioner"
                >
                  {{ schedulePractitionerLabel(schedule, practitioners) }}
                </span>
                <span v-else class="schedule-week-calendar__cell-practitioner is-empty"
                  >未指定医师</span
                >
                <span class="schedule-week-calendar__cell-status">
                  <span
                    v-if="schedule.fromTemplate || schedule.statusEnum === 'FROM_TEMPLATE'"
                    class="schedule-week-calendar__preview-tag"
                  >
                    预览
                  </span>
                  {{ formatScheduleStatus(schedule.statusEnum) }}
                  <template v-if="schedule.fromTemplate && schedule.bookable"> · 可约</template>
                  <template v-if="schedule.substituteDisplay">
                    · {{ schedule.substituteDisplay }}</template
                  >
                </span>
              </button>
              <div
                v-if="!day.schedules?.length"
                class="schedule-week-calendar__empty"
                title="双击空白处新建日程"
              >
                —
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.schedule-week-calendar {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.schedule-week-calendar__weekday-header {
  display: grid;
  flex-shrink: 0;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 8px;
}

.schedule-week-calendar__weekday-header-cell {
  padding: 6px 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.schedule-week-calendar__weeks {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}

.schedule-week-calendar__week-row {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 8px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
}

.schedule-week-calendar__week-row.is-anchor-week {
  border-color: var(--el-color-primary-light-5);
  box-shadow: inset 0 0 0 1px var(--el-color-primary-light-7);
}

.schedule-week-calendar__week-row.is-preview {
  opacity: 0.88;
  background: var(--el-fill-color-blank);
}

.schedule-week-calendar__week-banner {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.schedule-week-calendar__week-title {
  font-size: 14px;
  font-weight: 700;
}

.schedule-week-calendar__week-range {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.schedule-week-calendar__week-badge {
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 999px;
}

.schedule-week-calendar__week-badge.is-anchor-week {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.schedule-week-calendar__week-badge.is-preview {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.schedule-week-calendar__grid {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
  min-height: 0;
}

.schedule-week-calendar__day {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.schedule-week-calendar__day.is-anchor {
  border-color: var(--el-color-primary);
  box-shadow: inset 0 0 0 1px var(--el-color-primary-light-7);
}

.schedule-week-calendar__day-title {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.schedule-week-calendar__day-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.schedule-week-calendar__today-tag {
  flex-shrink: 0;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 500;
  line-height: 18px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 999px;
}

.schedule-week-calendar__cells {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  max-height: 280px;
  padding: 8px;
  overflow-y: auto;
}

.schedule-week-calendar__week-row.is-preview .schedule-week-calendar__cells {
  max-height: 240px;
}

.schedule-week-calendar__cell {
  display: flex;
  flex-shrink: 0;
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
  border-color: var(--el-border-color);
  border-style: solid;
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

.schedule-week-calendar__cell-practitioner {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.schedule-week-calendar__cell-practitioner.is-empty {
  color: var(--el-text-color-placeholder);
}

.schedule-week-calendar__cell-status {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.schedule-week-calendar__preview-tag {
  padding: 0 4px;
  font-size: 10px;
  line-height: 16px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 3px;
}

.schedule-week-calendar__empty {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  color: var(--el-text-color-placeholder);
}
</style>
