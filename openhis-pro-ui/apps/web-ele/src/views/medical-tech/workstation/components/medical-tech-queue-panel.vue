<script setup lang="ts">
import type {
  MedicalTechEncounterScope,
  MedicalTechExecutionQueueFilter,
  MedicalTechInpatientStayFilter,
  MedicalTechPatientQueueItemDto,
} from '#/api/medical-tech/medical-tech-execution.types';
import type {
  OutpatientPatientCardPayload,
  PatientCardPatient,
} from '#/components/outpatient/patient-card.types';

import { Search } from '@zxplat/icons';

import {
  ElButton,
  ElCheckbox,
  ElDatePicker,
  ElInput,
  ElOption,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import PatientCard from '#/components/outpatient/patient-card.vue';
import { buildDefaultDateRangeShortcuts } from '#/utils/format/date-picker-shortcuts';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { toMedicalTechPatientCardPayload } from '../medical-tech-patient-map';

defineOptions({
  name: 'MedicalTechQueuePanel',
});

interface Props {
  /** 就诊类别（门诊/住院） */
  encounterScope: MedicalTechEncounterScope;
  /** 队列执行态（待执行/已执行） */
  executionQueueFilter: MedicalTechExecutionQueueFilter;
  /** 住院在院态筛选（仅住院展示） */
  inpatientStayFilter: MedicalTechInpatientStayFilter;
  /** 当前日期范围内的全部队列行 */
  patients: MedicalTechPatientQueueItemDto[];
  queueLoading?: boolean;
  /** 当前选中就诊主键 */
  selectedPatientId: string;
}

const props = withDefaults(defineProps<Props>(), {
  queueLoading: false,
});

const emit = defineEmits<{
  encounterScopeChange: [scope: MedicalTechEncounterScope];
  executionQueueFilterChange: [filter: MedicalTechExecutionQueueFilter];
  inpatientStayFilterChange: [filter: MedicalTechInpatientStayFilter];
  pendingDeviceOnlyChange: [checked: boolean];
  queueDateRangeChange: [range: [string, string] | null];
  refresh: [];
  search: [];
  selectPatient: [patient: { id: string }];
}>();

const keyword = defineModel<string>('keyword', { required: true });
const queueDateRange = defineModel<[string, string] | null>('queueDateRange', { required: true });
/** 仅看待发耗材患者 */
const pendingDeviceOnly = defineModel<boolean>('pendingDeviceOnly', { required: true });

/** 区间查询：展开面板默认快捷时间范围 */
const dateRangeShortcuts = buildDefaultDateRangeShortcuts();

/** 通知父级重新拉取队列 */
function handleRefresh(event?: Event) {
  event?.preventDefault?.();
  event?.stopPropagation?.();
  emit('refresh');
}

/** 关键字回车/清空：通知父级重查 */
function handleSearch() {
  emit('search');
}

/** 就诊类别单选变更 */
function handleEncounterScopeChange(value: string | number | boolean | undefined) {
  const next = String(value ?? '');
  if (next !== 'OUTPATIENT' && next !== 'INPATIENT') {
    return;
  }
  if (next === props.encounterScope) {
    return;
  }
  emit('encounterScopeChange', next);
}

/** 待执行/已执行分桶变更：同值不回传 */
function handleExecutionQueueFilterChange(value: string | number | boolean | undefined) {
  const next = String(value ?? '');
  if (next !== 'PENDING' && next !== 'EXECUTED') {
    return;
  }
  if (next === props.executionQueueFilter) {
    return;
  }
  emit('executionQueueFilterChange', next);
}

/** 住院在院态单选变更：同值不回传，避免误清选中 */
function handleInpatientStayFilterChange(value: string | number | boolean | undefined) {
  const next = String(value ?? '');
  if (next !== 'IN_HOSPITAL' && next !== 'NOT_IN_HOSPITAL') {
    return;
  }
  if (next === props.inpatientStayFilter) {
    return;
  }
  emit('inpatientStayFilterChange', next);
}

/** 仅待发耗材开关：通知父级立即重查 */
function handlePendingDeviceOnlyChange(value: boolean | string | number) {
  const checked = value === true;
  pendingDeviceOnly.value = checked;
  emit('pendingDeviceOnlyChange', checked);
}

/** 日期范围变更：空值交给父级处理 */
function handleQueueDateRangeChange(value: [string, string] | null | undefined) {
  emit('queueDateRangeChange', value ?? null);
}

/** 点选队列卡片：门诊、住院都用同一张门诊卡片，回传就诊主键 */
function onPatientCardSelect(card: PatientCardPatient) {
  const outpatient = card as OutpatientPatientCardPayload;
  const id = preserveApiIdForRequest(outpatient.encounterId) ?? '';
  if (!id) {
    return;
  }
  emit('selectPatient', { id });
}

/** 队列行映射为门诊患者卡片载荷，住院同样走这套样式 */
function toCardPatient(row: MedicalTechPatientQueueItemDto): OutpatientPatientCardPayload {
  return toMedicalTechPatientCardPayload(row);
}

/** 行是否选中 */
function isRowSelected(row: MedicalTechPatientQueueItemDto): boolean {
  const id = preserveApiIdForRequest(row.encounterId) ?? '';
  return Boolean(id) && id === props.selectedPatientId;
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <div class="mt-queue-filters mb-1.5 flex flex-col gap-1.5 px-2 pt-1.5">
      <SectionTitle title="患者队列" />
      <ElRadioGroup
        :model-value="props.encounterScope"
        class="mt-scope-buttons"
        @change="handleEncounterScopeChange"
      >
        <ElRadioButton value="OUTPATIENT">门诊</ElRadioButton>
        <ElRadioButton value="INPATIENT">住院</ElRadioButton>
      </ElRadioGroup>
      <!-- 仅门诊按就诊开始日筛；住院在院跨日，不展示日期 -->
      <div v-if="props.encounterScope === 'OUTPATIENT'" class="mt-daterange-row w-full min-w-0">
        <ElDatePicker
          v-model="queueDateRange"
          class="mt-daterange w-full"
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
      <ElInput
        v-model="keyword"
        clearable
        placeholder="姓名 / 业务号"
        class="flex-1"
        @clear="handleSearch"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <Search class="h-4 w-4" />
        </template>
      </ElInput>
      <!-- 姓名下方：先执行态圆点一行，再「辅筛 + 刷新」一行，侧栏窄宽不挤折 -->
      <ElRadioGroup
        :model-value="props.executionQueueFilter"
        class="mt-execution-filter-dots"
        @change="handleExecutionQueueFilterChange"
      >
        <ElRadio value="PENDING">待执行</ElRadio>
        <ElRadio value="EXECUTED">已执行</ElRadio>
      </ElRadioGroup>
      <div class="flex min-w-0 items-center justify-between gap-1.5">
        <div v-if="props.encounterScope === 'INPATIENT'" class="w-[108px] shrink-0">
          <ElSelect
            :model-value="props.inpatientStayFilter"
            class="w-full"
            @change="handleInpatientStayFilterChange"
          >
            <ElOption label="在院" value="IN_HOSPITAL" />
            <ElOption label="不在院" value="NOT_IN_HOSPITAL" />
          </ElSelect>
        </div>
        <ElCheckbox
          v-else
          :model-value="pendingDeviceOnly"
          class="min-w-0"
          @change="handlePendingDeviceOnlyChange"
        >
          仅待发耗材
        </ElCheckbox>
        <ElButton
          type="default"
          native-type="button"
          class="shrink-0 !px-2"
          :loading="props.queueLoading"
          @click.stop="handleRefresh"
        >
          刷新
        </ElButton>
      </div>
    </div>

    <div class="queue-list min-h-0 flex-1 space-y-1.5 overflow-y-auto px-2 pb-1">
      <template v-if="patients.length === 0">
        <div class="text-text-sub py-8 text-center text-sm">暂无匹配患者</div>
      </template>
      <template v-else>
        <PatientCard
          v-for="row in patients"
          :key="row.encounterId"
          variant="nurse"
          :patient="toCardPatient(row)"
          :selected="isRowSelected(row)"
          @select="onPatientCardSelect"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
.mt-execution-filter-dots {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: flex-start;
  gap: 20px;
  height: auto;
}

.mt-execution-filter-dots :deep(.el-radio) {
  margin-right: 0;
  height: auto;
}

.mt-execution-filter-dots :deep(.el-radio__label) {
  padding-left: 6px;
  font-size: 13px;
}

.mt-daterange-row :deep(.el-date-editor.el-date-editor--daterange) {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  --el-date-editor-daterange-width: 100%;
}

.mt-daterange-row :deep(.el-range-input) {
  font-size: 14px;
}

.mt-daterange-row :deep(.el-range-separator) {
  flex: none;
  width: auto;
  padding: 0 2px;
  font-size: 14px;
}
</style>
