<script setup lang="ts">
import type { DateRangeQuickSpec } from '#/utils/format/date-range-quick';

import type { PatientItemDto } from '../services';

import { computed, ref } from 'vue';

import { ElButton, ElDatePicker } from 'element-plus';

import SelectTable from '#/components/select-table.vue';
import { getDateRangeQuick } from '#/utils/format/date-range-quick';

defineOptions({
  name: 'RegisterTopToolbar',
});

interface Props {
  cardReadDisabled?: Partial<Record<'id' | 'yb-card' | 'yb-ec', boolean>>;
  cardReadLoading?: Partial<Record<'id' | 'yb-card' | 'yb-ec', boolean>>;
  dateRange?: [string, string];
  patientSelectColumns: Array<{ label: string; prop: string; width?: number }>;
  queryPatientOptions: (params: {
    keyword?: string;
    pageNo?: number;
    pageSize?: number;
  }) => Promise<{ content: PatientItemDto[]; totalElements: number }>;
  selectedPatientId: string;
  selectedPatientLabel: string;
  /** 是否展示日期筛选（历史区已独立时可关闭） */
  showDateFilter?: boolean;
  /** inline：嵌入表单区；patient：患者信息区内，样式与下方表单同步 */
  variant?: 'bar' | 'inline' | 'patient';
}

const props = withDefaults(defineProps<Props>(), {
  cardReadDisabled: () => ({}),
  cardReadLoading: () => ({}),
  dateRange: undefined,
  showDateFilter: false,
  variant: 'inline',
});

const emit = defineEmits<{
  clear: [];
  dateRangeChange: [[string, string]];
  patientChange: [value: string | string[]];
  readIdentity: [source: 'id' | 'yb-card' | 'yb-ec'];
}>();

const selectedPatientIdModel = defineModel<string>('selectedPatientId', { required: true });

const activeQuick = ref<'calendar-day' | 'calendar-month' | 'calendar-week'>('calendar-day');

/** 顶栏日期快捷选择 */
function applyDateRangeQuick(spec: DateRangeQuickSpec) {
  activeQuick.value = spec.strategy;
  const range = getDateRangeQuick(spec) as [string, string];
  emit('dateRangeChange', range);
}

/** 日期范围手动变更 */
function handleDateRangeChange(value: [string, string] | null) {
  if (!value) {
    return;
  }
  activeQuick.value = 'calendar-day';
  emit('dateRangeChange', value);
}

/** 读卡/扫码按钮是否禁用 */
function isReadDisabled(source: 'id' | 'yb-card' | 'yb-ec'): boolean {
  return Boolean(props.cardReadDisabled?.[source]);
}

/** 读卡/扫码按钮是否加载中 */
function isReadLoading(source: 'id' | 'yb-card' | 'yb-ec'): boolean {
  return Boolean(props.cardReadLoading?.[source]);
}

const identityButtons = computed(() => [
  { key: 'yb-card' as const, label: '医保卡' },
  { key: 'id' as const, label: '身份证' },
  { key: 'yb-ec' as const, label: '医保码' },
]);
</script>

<template>
  <div v-if="variant === 'patient'" class="register-top-bar register-top-bar--patient shrink-0">
    <div class="register-top-bar__search-wrap">
      <SelectTable
        v-model="selectedPatientIdModel"
        class="register-top-bar__search"
        placeholder="姓名/病历号/证件号"
        :columns="patientSelectColumns"
        :remote-method="queryPatientOptions"
        value-key="patientId"
        label-key="name"
        :selected-label="selectedPatientLabel"
        @change="(value) => emit('patientChange', value)"
      />
    </div>
    <div class="register-top-bar__actions">
      <ElButton class="register-top-bar__clear" link type="info" @click="emit('clear')">
        清空
      </ElButton>
      <span class="register-top-bar__divider" aria-hidden="true" />
      <div class="register-top-bar__identity-group">
        <ElButton
          v-for="item in identityButtons"
          :key="item.key"
          class="register-top-bar__identity-btn"
          :loading="isReadLoading(item.key)"
          :disabled="isReadDisabled(item.key)"
          @click="emit('readIdentity', item.key)"
        >
          {{ item.label }}
        </ElButton>
      </div>
    </div>
  </div>

  <div
    v-else
    class="register-top-bar shrink-0"
    :class="{ 'register-top-bar--inline': variant === 'inline' }"
  >
    <div class="register-top-bar__search-wrap">
      <SelectTable
        v-model="selectedPatientIdModel"
        class="register-top-bar__search"
        placeholder="搜索患者（姓名/病历号/证件号）"
        :columns="patientSelectColumns"
        :remote-method="queryPatientOptions"
        value-key="patientId"
        label-key="name"
        :selected-label="selectedPatientLabel"
        @change="(value) => emit('patientChange', value)"
      />
    </div>

    <div class="register-top-bar__actions">
      <ElButton class="register-top-bar__clear" link type="info" @click="emit('clear')">
        清空
      </ElButton>

      <span class="register-top-bar__divider" aria-hidden="true" />

      <div class="register-top-bar__identity-group">
        <ElButton
          v-for="item in identityButtons"
          :key="item.key"
          class="register-top-bar__identity-btn"
          :loading="isReadLoading(item.key)"
          :disabled="isReadDisabled(item.key)"
          @click="emit('readIdentity', item.key)"
        >
          {{ item.label }}
        </ElButton>
      </div>
    </div>

    <div
      v-if="showDateFilter && dateRange"
      class="register-top-bar__date ml-auto flex flex-wrap items-center gap-2"
    >
      <ElDatePicker
        :model-value="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        class="register-top-bar__date-picker"
        @update:model-value="handleDateRangeChange"
      />
      <div class="register-top-bar__quick">
        <button
          type="button"
          class="register-top-bar__quick-btn"
          :class="{ 'is-active': activeQuick === 'calendar-day' }"
          @click="applyDateRangeQuick({ strategy: 'calendar-day' })"
        >
          今天
        </button>
        <button
          type="button"
          class="register-top-bar__quick-btn"
          :class="{ 'is-active': activeQuick === 'calendar-week' }"
          @click="applyDateRangeQuick({ strategy: 'calendar-week' })"
        >
          本周
        </button>
        <button
          type="button"
          class="register-top-bar__quick-btn"
          :class="{ 'is-active': activeQuick === 'calendar-month' }"
          @click="applyDateRangeQuick({ strategy: 'calendar-month' })"
        >
          本月
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-top-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 12px;
  align-items: center;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.register-top-bar--inline {
  flex-wrap: nowrap;
  gap: 6px;
  align-items: center;
  width: 100%;
  min-height: 36px;
  padding-bottom: 0;
  border-bottom: none;
}

.register-top-bar--patient {
  display: flex;
  flex-wrap: nowrap;
  gap: 6px;
  align-items: center;
  width: 100%;
  min-height: 0;
  padding-bottom: 0;
  border-bottom: none;
}

.register-top-bar--patient .register-top-bar__search-wrap {
  flex: 1 1 auto;
  min-width: 0;
}

.register-top-bar--patient .register-top-bar__actions {
  flex-shrink: 0;
}

.register-top-bar--inline .register-top-bar__search-wrap {
  flex: 1 1 auto;
  min-width: 160px;
}

.register-top-bar__search-wrap {
  flex: 1 1 280px;
  min-width: 240px;
  background: transparent;
}

.register-top-bar__search {
  width: 100%;
}

.register-top-bar__search-wrap :deep(.el-input__wrapper) {
  min-height: 32px;
  background: var(--el-fill-color-blank);
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
}

.register-top-bar--patient .register-top-bar__identity-btn {
  height: 28px;
}

.register-top-bar--patient .register-top-bar__identity-group {
  border-radius: 4px;
}

.register-top-bar__actions {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
}

.register-top-bar__clear {
  padding: 0 4px;
  font-size: 12px;
}

.register-top-bar__divider {
  flex-shrink: 0;
  width: 1px;
  height: 16px;
  background: var(--el-border-color-lighter);
}

.register-top-bar__identity-group {
  display: flex;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.register-top-bar__identity-btn {
  height: 28px;
  padding: 0 10px;
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-blank);
  border: none;
  border-radius: 0;
}

.register-top-bar__identity-btn + .register-top-bar__identity-btn {
  border-left: 1px solid var(--el-border-color);
}

.register-top-bar__identity-btn:hover,
.register-top-bar__identity-btn:focus {
  color: var(--el-color-primary);
  background: var(--el-fill-color-light);
}

.register-top-bar__date-picker {
  width: 260px;
  max-width: 260px;
}

.register-top-bar__quick {
  display: flex;
  gap: 6px;
}

.register-top-bar__quick-btn {
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 500;
  color: var(--el-color-primary);
  cursor: pointer;
  background: var(--el-fill-color);
  border: none;
  border-radius: 20px;
  transition: background-color 0.2s ease;
}

.register-top-bar__quick-btn.is-active {
  color: #fff;
  background: var(--el-color-primary);
}

@media (max-width: 1679px) {
  .register-top-bar__search-wrap :deep(.el-input__wrapper) {
    min-height: var(--form-control-height);
    height: var(--form-control-height);
    border-radius: var(--form-control-radius);
    box-shadow: 0 0 0 1px hsl(var(--form-control-border)) inset;
  }

  .register-top-bar--patient .register-top-bar__identity-btn {
    height: var(--form-control-height);
    border-radius: var(--form-control-radius);
  }

  .register-top-bar--patient .register-top-bar__identity-group {
    border-radius: var(--form-control-radius);
  }
}
</style>
