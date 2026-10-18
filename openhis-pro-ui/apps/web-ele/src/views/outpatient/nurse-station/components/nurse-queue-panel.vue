<script setup lang="ts">
import type { NursePatient } from './types';

import type {
  OutpatientPatientCardPayload,
  PatientCardPatient,
} from '#/components/outpatient/patient-card.types';
import type { OptionDto } from '#/api/outpatient-registration';

import { RotateCw, Search } from '@zxplat/icons';

import { ElButton, ElCheckbox, ElDatePicker, ElInput, ElSelect, ElOption } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import PatientCard from '#/components/outpatient/patient-card.vue';
import { buildDefaultDateRangeShortcuts } from '#/utils/format/date-picker-shortcuts';

import { toPatientCardPatient } from './nurse-patient-map';

defineOptions({
  name: 'NurseQueuePanel',
});

const props = withDefaults(defineProps<Props>(), {
  queueLoading: false,
  orgOptions: () => [],
});

const emit = defineEmits<{
  orgChange: [orgId: string];
  pendingDeviceOnlyChange: [checked: boolean];
  queueDateRangeChange: [range: [string, string] | null];
  refresh: [];
  selectPatient: [patient: { id: string }];
}>();

interface Props {
  filteredPatients: NursePatient[];
  /** 开立科室下拉（空 value 表示「全部」由父级用空串表达） */
  orgOptions?: OptionDto[];
  queueLoading?: boolean;
  selectedPatientId: string;
}

const keyword = defineModel<string>('keyword', { required: true });
const queueDateRange = defineModel<[string, string] | null>('queueDateRange', { required: true });
/** 开立科室：空串=全部 */
const orgId = defineModel<string>('orgId', { required: true });
/** 仅看待发耗材患者 */
const pendingDeviceOnly = defineModel<boolean>('pendingDeviceOnly', { required: true });

/** 区间查询：展开面板默认快捷时间范围 */
const dateRangeShortcuts = buildDefaultDateRangeShortcuts();

/** 通知父级重新拉取队列（由父级控制 loading） */
const handleRefresh = () => {
  emit('refresh');
};

/** 开立科室变更：通知父级立即重查（默认全部） */
function handleOrgChange(value: string | number | boolean | undefined) {
  // 清空下拉时按「全部科室」重查
  let next = '';
  if (value !== undefined && value !== null) {
    next = String(value);
  }
  orgId.value = next;
  emit('orgChange', next);
}

/** 仅待发耗材开关：通知父级立即重查 */
function handlePendingDeviceOnlyChange(value: boolean | string | number) {
  const checked = value === true;
  pendingDeviceOnly.value = checked;
  emit('pendingDeviceOnlyChange', checked);
}

/** 日期范围变更：空值交给父级回落到当天 */
function handleQueueDateRangeChange(value: [string, string] | null | undefined) {
  emit('queueDateRangeChange', value ?? null);
}

/** 点选队列卡片：住院用行主键，门诊用就诊主键 */
function onPatientCardSelect(card: PatientCardPatient) {
  // 住院卡片主键是病区行 id，门诊卡片主键是 encounterId
  if ('patientType' in card && card.patientType === 'inpatient') {
    emit('selectPatient', { id: card.id });
    return;
  }
  emit('selectPatient', { id: (card as OutpatientPatientCardPayload).encounterId });
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <div class="mb-1.5 flex flex-col gap-1.5 px-2 pt-1.5">
      <SectionTitle title="患者队列" />
      <ElSelect
        :model-value="orgId || undefined"
        clearable
        filterable
        placeholder="开立科室（全部）"
        class="w-full"
        @change="handleOrgChange"
        @clear="handleOrgChange(undefined)"
      >
        <ElOption
          v-for="opt in props.orgOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </ElSelect>
      <ElInput v-model="keyword" clearable placeholder="姓名 / 排队号" class="flex-1">
        <template #prefix>
          <Search class="h-4 w-4" />
        </template>
      </ElInput>
      <div class="flex min-w-0 items-center justify-between gap-1.5">
        <ElCheckbox
          :model-value="pendingDeviceOnly"
          class="nurse-pending-device-filter min-w-0"
          @change="handlePendingDeviceOnlyChange"
        >
          仅待发耗材
        </ElCheckbox>
        <ElButton type="default" class="shrink-0 !px-2" @click="handleRefresh">
          <RotateCw class="h-4 w-4" :class="{ 'animate-spin': props.queueLoading }" />
        </ElButton>
      </div>
      <div class="nurse-daterange-row w-full min-w-0">
        <ElDatePicker
          v-model="queueDateRange"
          class="nurse-daterange w-full"
          type="daterange"
          unlink-panels
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          :shortcuts="dateRangeShortcuts"
          @change="handleQueueDateRangeChange"
        />
      </div>
    </div>
    <div class="queue-list min-h-0 flex-1 space-y-1.5 overflow-y-auto px-2 pb-1">
      <template v-if="filteredPatients.length === 0">
        <div class="text-text-sub py-8 text-center text-sm">暂无匹配患者</div>
      </template>
      <template v-else>
        <PatientCard
          v-for="patient in filteredPatients"
          :key="patient.id"
          variant="nurse"
          :patient="toPatientCardPatient(patient)"
          :selected="patient.id === selectedPatientId"
          @select="onPatientCardSelect"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
.nurse-daterange-row :deep(.el-date-editor.el-date-editor--daterange) {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  --el-date-editor-daterange-width: 100%;
}

.nurse-daterange-row :deep(.el-range-input) {
  font-size: 14px;
}

.nurse-daterange-row :deep(.el-range-separator) {
  flex: none;
  width: auto;
  padding: 0 2px;
  font-size: 14px;
}
</style>
