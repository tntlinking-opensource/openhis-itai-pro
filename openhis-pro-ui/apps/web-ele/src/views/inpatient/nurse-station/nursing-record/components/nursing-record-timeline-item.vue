<script setup lang="ts">
import type { NursingRecordViewRow } from './nursing-record.types';

import NursingRecordBodySection from './nursing-record-body-section.vue';
import NursingRecordRowActions from './nursing-record-row-actions.vue';

interface Props {
  index: number;
  row: NursingRecordViewRow;
  total: number;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  add: [row: NursingRecordViewRow];
  cancel: [row: NursingRecordViewRow];
  copy: [row: NursingRecordViewRow];
  delete: [row: NursingRecordViewRow];
  edit: [row: NursingRecordViewRow];
  save: [row: NursingRecordViewRow];
}>();

const consciousnessOptions = ['清醒', '嗜睡', '模糊', '谵妄', '昏睡', '昏迷'].map((item) => ({
  label: item,
  value: item,
}));

/** 将记录字段转成展示文案，空值统一显示占位。 */
function toDisplayValue(value?: string) {
  return value && value.length > 0 ? value : '-';
}
/** 组合完整时间文案（年月日 时分），用于时间轴头部主显示。 */
function formatRecordDateTime(row: NursingRecordViewRow) {
  const dateText = toDisplayValue(row.recordDate);
  const timeText = toDisplayValue(row.recordTime);
  if (dateText === '-' && timeText === '-') {
    return '-';
  }
  if (dateText === '-') {
    return timeText;
  }
  if (timeText === '-') {
    return dateText;
  }
  return `${dateText} ${timeText}`;
}
</script>

<template>
  <div class="timeline-item">
    <div class="timeline-item__axis">
      <div class="timeline-item__dot"></div>
      <div v-if="props.index !== props.total - 1" class="timeline-item__line"></div>
    </div>

    <div class="timeline-item__card">
      <div class="timeline-card__header">
        <div class="timeline-card__time-group">
          <span class="timeline-card__time">{{ formatRecordDateTime(props.row) }}</span>
        </div>
        <div class="timeline-card__header-fields">
          <template v-if="props.row.isEditing">
            <ElDatePicker
              v-model="props.row.recordDate"
              value-format="YYYY-MM-DD"
              format="YYYY-MM-DD"
              class="!w-[140px]"
            />
            <ElTimePicker
              v-model="props.row.recordTime"
              value-format="HH:mm"
              format="HH:mm"
              class="!w-[120px]"
            />
            <ElSelect
              v-model="props.row.consciousness"
              :options="consciousnessOptions"
              class="!w-[120px]"
            />
            <ElInput v-model="props.row.nurseSignature" placeholder="护士签名" class="!w-[140px]" />
            <NursingRecordRowActions
              editing
              @add="emit('add', props.row)"
              @cancel="emit('cancel', props.row)"
              @copy="emit('copy', props.row)"
              @delete="emit('delete', props.row)"
              @edit="emit('edit', props.row)"
              @save="emit('save', props.row)"
            />
          </template>
          <template v-else>
            <span class="timeline-chip">{{ toDisplayValue(props.row.consciousness) }}</span>
            <span class="timeline-chip">签名：{{ toDisplayValue(props.row.nurseSignature) }}</span>
            <NursingRecordRowActions
              @add="emit('add', props.row)"
              @copy="emit('copy', props.row)"
              @delete="emit('delete', props.row)"
              @edit="emit('edit', props.row)"
            />
          </template>
        </div>
      </div>

      <NursingRecordBodySection :row="props.row" />
    </div>
  </div>
</template>

<style scoped>
.timeline-item {
  display: flex;
  align-items: stretch;
  gap: 6px;
}

.timeline-item__axis {
  display: flex;
  width: 12px;
  flex-direction: column;
  align-items: center;
  flex: 0 0 auto;
}

.timeline-item__dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--el-color-primary);
  margin-top: 8px;
}

.timeline-item__line {
  width: 2px;
  flex: 1;
  margin-top: 2px;
  background: rgb(64 158 255 / 25%);
}

.timeline-item__card {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-light);
  background: #fff;
  padding: 6px 8px;
}

.timeline-card__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 6px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
  padding-bottom: 6px;
}

.timeline-card__time-group {
  display: flex;
  min-width: 96px;
  flex-direction: column;
}

.timeline-card__time {
  color: var(--el-color-primary);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.timeline-card__header-fields {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 6px;
}

.timeline-chip {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  border-radius: 11px;
  background: #f1f5f9;
  color: var(--el-text-color-primary);
  font-size: 14px;
  padding: 0 8px;
}
</style>
