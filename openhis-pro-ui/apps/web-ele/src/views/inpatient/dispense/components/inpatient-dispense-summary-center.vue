<script setup lang="ts">
import type { InpatientPharmacySummarySlipHeaderDto } from '#/api/inpatient-pharmacy-dispense/inpatient-pharmacy-summary-dispense.types';

import type {
  SummaryDispensePatientGroup,
  SummaryMergedLineDisplayRow,
} from '../services/map-summary-dispense-ui';

import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElEmpty,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

import {
  formatSummaryApplyTime,
  formatSummaryMergedLineQuantity,
  formatSummaryPatientDispenseLineText,
} from '../services/map-summary-dispense-ui';

defineOptions({
  name: 'InpatientDispenseSummaryCenter',
});

interface Props {
  batchSubmitting?: boolean;
  detailLoading?: boolean;
  dispenserOptions: { label: string; value: string }[];
  header: InpatientPharmacySummarySlipHeaderDto | null;
  mergedLines: SummaryMergedLineDisplayRow[];
  patientGroups: SummaryDispensePatientGroup[];
  readOnly?: boolean;
  receiverOptions: { label: string; value: string }[];
  selectedSummaryNo: null | string;
  traceCodeByLineId: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), {
  batchSubmitting: false,
  detailLoading: false,
  readOnly: false,
});

const emit = defineEmits<{
  batchDispense: [];
  batchReject: [];
  batchTraceScan: [];
  /** 打印当前汇总领药单（待核发 / 已发药均可） */
  printSummary: [];
}>();

const selectedDispenserId = defineModel<string | undefined>('selectedDispenserId', {
  required: true,
});
const selectedReceiverId = defineModel<string | undefined>('selectedReceiverId', {
  required: true,
});
/** 核对发药成功后是否自动打印汇总单（与父级双向绑定）。 */
const autoPrintEnabled = defineModel<boolean>('autoPrintEnabled', {
  default: false,
});
/** 当前向上展开的患者 key */
const expandedPatientKey = ref<null | string>(null);

/** 当前展开患者的药品明细 */
const expandedPatientGroup = computed(() => {
  const key = expandedPatientKey.value;
  if (!key) return null;
  return props.patientGroups.find((g) => g.groupKey === key) ?? null;
});

/** 展开区药品文案（药名+数量，顿号连接） */
const expandedDrugText = computed(() => {
  const items = expandedPatientGroup.value?.items ?? [];
  if (items.length === 0) return '';
  return items.map((item) => formatSummaryPatientDispenseLineText(item)).join('、');
});

/** 切换汇总单时收起患者展开区 */
watch(
  () => props.selectedSummaryNo,
  () => {
    expandedPatientKey.value = null;
  },
);

/** 患者列表变化时校验展开项是否仍存在 */
watch(
  () => props.patientGroups,
  (groups) => {
    const key = expandedPatientKey.value;
    if (!key) return;
    if (!groups.some((g) => g.groupKey === key)) {
      expandedPatientKey.value = null;
    }
  },
);

/** 床号+姓名展示 */
function buildCompactPatientLabel(bedDisplay: string, patientName: string): string {
  const bed = String(bedDisplay ?? '')
    .trim()
    .replace(/号床$/, '床');
  const name = String(patientName ?? '').trim();
  if (bed && name) return `${bed}·${name}`;
  return bed || name || '—';
}

/** 点击患者条：同项收起，异项切换展开 */
function togglePatientExpand(groupKey: string) {
  expandedPatientKey.value = expandedPatientKey.value === groupKey ? null : groupKey;
}

/** 单头摘要文案 */
const headerSummaryText = computed(() => {
  const h = props.header;
  if (!h) return '';
  const parts = [
    h.busNo ? `单号 ${h.busNo}` : '',
    h.applicantDepartmentName ? `申领科室 ${h.applicantDepartmentName}` : '',
    h.applicantName ? `申请人 ${h.applicantName}` : '',
    h.applyTime ? formatSummaryApplyTime(h.applyTime) : '',
    h.purposeLocationName ? h.purposeLocationName : '',
  ].filter(Boolean);
  return parts.join(' · ');
});
</script>

<template>
  <div v-loading="props.detailLoading" class="flex h-full min-h-0 flex-col overflow-hidden">
    <div
      class="dispense-summary-toolbar flex flex-col gap-2 border-b border-solid border-[var(--el-border-color-lighter)] px-3 pb-2 pt-2"
    >
      <SectionTitle title="汇总单明细" class="!mb-0 shrink-0" />
      <div class="flex w-full min-w-0 flex-wrap items-center gap-x-2 gap-y-2">
        <div class="flex flex-wrap items-center gap-1">
          <ElButton
            type="primary"
            :disabled="props.readOnly || !props.selectedSummaryNo || props.batchSubmitting"
            @click="emit('batchDispense')"
          >
            核对发药
          </ElButton>
          <ElButton
            type="danger"
            plain
            :disabled="props.readOnly || !props.selectedSummaryNo || props.batchSubmitting"
            @click="emit('batchReject')"
          >
            驳回
          </ElButton>
          <ElButton
            type="primary"
            plain
            :disabled="
              props.readOnly || !props.selectedSummaryNo || props.patientGroups.length === 0
            "
            @click="emit('batchTraceScan')"
          >
            扫追溯码
          </ElButton>
          <ElButton
            type="primary"
            plain
            :disabled="!props.selectedSummaryNo || props.batchSubmitting"
            @click="emit('printSummary')"
          >
            打印汇总单
          </ElButton>
        </div>
        <div class="flex shrink-0 items-center gap-1.5 whitespace-nowrap">
          <span class="text-xs text-[var(--el-text-color-secondary)]">调配药师</span>
          <ElSelect
            v-model="selectedDispenserId"
            placeholder="请选择调配药师"
            clearable
            filterable
            class="!w-40 shrink-0"
          >
            <ElOption
              v-for="opt in props.dispenserOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </div>
        <div class="flex shrink-0 items-center gap-1.5 whitespace-nowrap">
          <span class="text-xs text-[var(--el-text-color-secondary)]">领药人</span>
          <ElSelect
            v-model="selectedReceiverId"
            placeholder="请选择领药人"
            clearable
            filterable
            class="!w-40 shrink-0"
          >
            <ElOption
              v-for="opt in props.receiverOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </div>
        <div
          class="flex shrink-0 items-center gap-1.5 whitespace-nowrap"
          title="开启后，核对发药成功会自动打印当前汇总单"
        >
          <span class="text-xs text-[var(--el-text-color-regular)]">发药后自动打印汇总单</span>
          <ElSwitch v-model="autoPrintEnabled" inline-prompt active-text="开" inactive-text="关" />
        </div>
      </div>
      <p
        v-if="headerSummaryText"
        class="truncate text-xs text-[var(--el-text-color-regular)]"
        :title="headerSummaryText"
      >
        {{ headerSummaryText }}
      </p>
    </div>

    <div v-if="!props.selectedSummaryNo" class="flex min-h-0 flex-1 items-center justify-center">
      <ElEmpty description="请从左侧选择汇总单" />
    </div>

    <div v-else class="flex min-h-0 flex-1 flex-col overflow-hidden px-3 pb-2 pt-2">
      <section class="summary-merged-section flex min-h-0 flex-1 flex-col overflow-hidden">
        <div class="summary-merged-table-wrap min-h-0 flex-1">
          <ElTable
            :data="props.mergedLines"
            class="summary-merged-table dispense-rx-grouped-table w-full min-w-0"
            border
            stripe
            height="100%"
            empty-text="暂无药品汇总"
          >
            <ElTableColumn
              type="index"
              label="序号"
              width="56"
              align="center"
              header-align="center"
            />
            <ElTableColumn
              prop="medicationName"
              label="项目名称"
              min-width="160"
              align="center"
              header-align="center"
              show-overflow-tooltip
            />
            <ElTableColumn label="数量" min-width="100" align="center" header-align="center">
              <template #default="{ row }">
                {{ formatSummaryMergedLineQuantity(row) }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="specificationText"
              label="规格"
              min-width="110"
              align="center"
              header-align="center"
              show-overflow-tooltip
            />
            <ElTableColumn
              label="批次号"
              min-width="100"
              align="center"
              header-align="center"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.lotNumberText?.trim() || '—' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="单价" min-width="88" align="center" header-align="center">
              <template #default="{ row }">
                {{
                  row.unitPrice != null && row.unitPrice !== ''
                    ? formatMoneyDisplay(row.unitPrice)
                    : '—'
                }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="总价" min-width="88" align="center" header-align="center">
              <template #default="{ row }">
                {{
                  row.totalPrice != null && row.totalPrice !== ''
                    ? formatMoneyDisplay(row.totalPrice)
                    : '—'
                }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              label="发放库房"
              min-width="120"
              align="center"
              header-align="center"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{
                  row.purposeLocationName?.trim() ||
                  props.header?.purposeLocationName?.trim() ||
                  '—'
                }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="追溯码" min-width="88" align="center" header-align="center">
              <template #default>—</template>
            </ElTableColumn>
            <ElTableColumn
              prop="manufacturerName"
              label="厂家"
              min-width="140"
              align="center"
              header-align="center"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.manufacturerName?.trim() || '—' }}
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </section>

      <section class="summary-patient-dock shrink-0">
        <div v-if="expandedDrugText" class="patient-drug-panel" :title="expandedDrugText">
          {{ expandedDrugText }}
        </div>

        <div class="patient-bar">
          <span class="patient-bar__title">患者发放明细</span>
          <span v-if="props.patientGroups.length === 0" class="patient-bar__empty">
            暂无发放明细
          </span>
          <div v-else class="patient-bar__list">
            <button
              v-for="group in props.patientGroups"
              :key="group.groupKey"
              type="button"
              class="patient-tag"
              :class="{ 'patient-tag--active': expandedPatientKey === group.groupKey }"
              @click="togglePatientExpand(group.groupKey)"
            >
              {{ buildCompactPatientLabel(group.bedDisplay, group.patientName) }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.summary-merged-table-wrap {
  min-height: 200px;
}

:deep(.summary-merged-table.el-table) {
  --el-table-header-bg-color: var(--el-fill-color-light);
}

:deep(.summary-merged-table .el-table__header .cell) {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.summary-patient-dock {
  margin-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.patient-drug-panel {
  max-height: 48px;
  overflow-y: auto;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
  padding: 5px 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  word-break: break-all;
}

.patient-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 34px;
  padding: 5px 0;
}

.patient-bar__title {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.patient-bar__empty {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.patient-bar__list {
  display: flex;
  flex: 1;
  min-width: 0;
  gap: 6px;
  overflow-x: auto;
  scrollbar-width: thin;
}

.patient-tag {
  flex-shrink: 0;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-bg-color);
  padding: 2px 8px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-primary);
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease,
    color 0.15s ease;
}

.patient-tag:hover {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.patient-tag--active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
</style>
