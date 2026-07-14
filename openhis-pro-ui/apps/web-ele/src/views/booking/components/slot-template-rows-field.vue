<script setup lang="ts">
import type { SlotProvisionTemplateItemDto } from '#/api/booking/schedule-template';

import { createDefaultSlotRow } from '#/views/booking/components/slot-template-rows.util';
import { ElButton, ElFormItem, ElInput, ElInputNumber } from 'element-plus';

defineOptions({ name: 'SlotTemplateRowsField' });

withDefaults(
  defineProps<{
    /** 只读展示（新建排班选中放号时段时） */
    readonly?: boolean;
    /** 表单项标签，空则不包 ElFormItem */
    label?: string;
  }>(),
  {
    readonly: false,
    label: '时段',
  },
);

const rows = defineModel<SlotProvisionTemplateItemDto[]>({ default: () => [] });

/** 追加一行默认时段 */
function addRow() {
  rows.value = [...rows.value, createDefaultSlotRow()];
}

/** 删除指定行 */
function removeRow(index: number) {
  rows.value = rows.value.filter((_, rowIndex) => rowIndex !== index);
}
</script>

<template>
  <ElFormItem v-if="label" :label="label">
    <div class="slot-template-rows">
      <div v-for="(row, index) in rows" :key="index" class="slot-template-rows__row">
        <template v-if="readonly">
          <span class="slot-template-rows__time">{{ row.startTime || '—' }}</span>
          <span class="slot-template-rows__sep">—</span>
          <span class="slot-template-rows__time">{{ row.endTime || '—' }}</span>
          <span class="slot-template-rows__capacity">（{{ row.capacity ?? 1 }}号）</span>
        </template>
        <template v-else>
          <ElInput v-model="row.startTime" placeholder="08:00" style="width: 88px" />
          <span class="slot-template-rows__sep">—</span>
          <ElInput v-model="row.endTime" placeholder="12:00" style="width: 88px" />
          <ElInputNumber v-model="row.capacity" :min="1" placeholder="容量" />
          <ElButton link type="danger" @click="removeRow(index)">删</ElButton>
        </template>
      </div>
      <ElButton v-if="!readonly" link type="primary" @click="addRow">+ 添加时段</ElButton>
      <p v-if="readonly && !rows.length" class="slot-template-rows__empty">暂无时段行</p>
    </div>
  </ElFormItem>
  <div v-else class="slot-template-rows">
    <div v-for="(row, index) in rows" :key="index" class="slot-template-rows__row">
      <template v-if="readonly">
        <span class="slot-template-rows__time">{{ row.startTime || '—' }}</span>
        <span class="slot-template-rows__sep">—</span>
        <span class="slot-template-rows__time">{{ row.endTime || '—' }}</span>
        <span class="slot-template-rows__capacity">（{{ row.capacity ?? 1 }}号）</span>
      </template>
      <template v-else>
        <ElInput v-model="row.startTime" placeholder="08:00" style="width: 88px" />
        <span class="slot-template-rows__sep">—</span>
        <ElInput v-model="row.endTime" placeholder="12:00" style="width: 88px" />
        <ElInputNumber v-model="row.capacity" :min="1" placeholder="容量" />
        <ElButton link type="danger" @click="removeRow(index)">删</ElButton>
      </template>
    </div>
    <ElButton v-if="!readonly" link type="primary" @click="addRow">+ 添加时段</ElButton>
  </div>
</template>

<style scoped>
.slot-template-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.slot-template-rows__row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.slot-template-rows__sep {
  color: var(--el-text-color-secondary);
}

.slot-template-rows__time {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.slot-template-rows__capacity {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.slot-template-rows__empty {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
