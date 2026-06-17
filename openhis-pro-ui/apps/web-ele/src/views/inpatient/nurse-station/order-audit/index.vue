<script setup lang="ts">
import type { NurseStationOrderAuditTab } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-audit.types';

import { computed, inject, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  httpListNurseStationOrderAudit,
  httpRejectNurseStationOrders,
  httpVerifyNurseStationOrders,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-audit.http';
import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';
import { adviceTherapyTypeClass } from '#/views/inpatient/doctor-station/advice/services/advice-list';
import { useBedsideSpecimenBarcodePrint } from '#/composables/use-bedside-specimen-barcode-print';
import { useIvInfusionLabelPrint } from '#/composables/use-iv-infusion-label-print';
import { useNursePrescriptionPrint } from '#/composables/use-nurse-prescription-print';
import { useTreatmentExecuteSheetPrint } from '#/composables/use-treatment-execute-sheet-print';
import { useRequest } from '#/hooks/use-request';
import {
  groupInpatientAuditInfusionLabelPrintTargets,
  groupInpatientAuditMedicationRequestIds,
  groupInpatientAuditSpecimenBarcodePrintTargets,
  groupInpatientAuditTreatmentExecutePrintTargets,
} from '#/utils/report/inpatient-order-audit-print-targets';

import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import { nurseStationPatientListPanelContextKey } from '../components/patient-list-panel.context';
import NurseStationPatientOrderCollapseTitle from '../components/nurse-station-patient-order-collapse-title.vue';

import {
  indexAuditGroupsByEncounterId,
  mapTableRowsToWriteItems,
  type NurseStationOrderAuditTableRow,
} from './services/map-nurse-station-order-audit';

type AuditStatus = NurseStationOrderAuditTab;
type TimeTypeFilter = 'all' | 'long' | 'temp';

interface PatientOrderCard {
  patient: NurseStationModulePatient;
  orders: NurseStationOrderAuditTableRow[];
}

interface Props {
  patients?: NurseStationModulePatient[];
}

defineOptions({
  name: 'NurseStationOrderAudit',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

const WRITE_BATCH_LIMIT = 50;

const listPanelContext = inject(nurseStationPatientListPanelContextKey, null);

const statusTabs: Array<{ label: string; value: AuditStatus }> = [
  { label: '未校对', value: 'pending' },
  { label: '已校对', value: 'passed' },
  { label: '已停止', value: 'stopped' },
  { label: '已作废', value: 'suspended' },
];

const activeStatus = ref<AuditStatus>('pending');
const activeTimeType = ref<TimeTypeFilter>('all');
const selectedAll = ref(false);
const selectedRowKeys = ref<Set<string>>(new Set());
const ordersByEncounterId = ref<Map<string, NurseStationOrderAuditTableRow[]>>(new Map());

const listRequest = useRequest(httpListNurseStationOrderAudit, { manual: true });
const verifyRequest = useRequest(httpVerifyNurseStationOrders, { manual: true });
const rejectRequest = useRequest(httpRejectNurseStationOrders, { manual: true });
const { printIvInfusionLabels } = useIvInfusionLabelPrint();
const { printBedsideSpecimenBarcodes } = useBedsideSpecimenBarcodePrint();
const { printTreatmentExecuteSheet } = useTreatmentExecuteSheetPrint();
const { printNursePrescriptions } = useNursePrescriptionPrint();
const printActionLoading = ref(false);

const encounterIds = computed(() =>
  props.patients.map((p) => String(p.id).trim()).filter((id) => id.length > 0),
);

/** 未校对 Tab 才允许批量校对/退回 */
const writeActionsEnabled = computed(() => activeStatus.value === 'pending');

/** 将接口分组与左侧已选患者合并为折叠卡片数据 */
const visibleCards = computed<PatientOrderCard[]>(() => {
  const index = ordersByEncounterId.value;
  return props.patients.map((patient) => {
    const encounterId = String(patient.id).trim();
    return {
      patient,
      orders: index.get(encounterId) ?? [],
    };
  });
});

const cardCountText = computed(() => `${visibleCards.value.length}位患者`);
const expandedPatientIds = ref<string[]>([]);

/** 当前 Tab 下全部医嘱行（用于全选） */
const allVisibleOrderRows = computed(() => {
  const rows: NurseStationOrderAuditTableRow[] = [];
  for (const card of visibleCards.value) {
    rows.push(...card.orders);
  }
  return rows;
});

/** 行勾选变化时回写全选状态（仅上行，避免与全选开关互相清空） */
watch(
  selectedRowKeys,
  (keys) => {
    const total = allVisibleOrderRows.value.length;
    selectedAll.value = total > 0 && keys.size === total;
  },
  { deep: true },
);

/** 勾选当前 Tab 下全部医嘱行 */
function selectAllVisibleOrderRows() {
  selectedRowKeys.value = new Set(allVisibleOrderRows.value.map((row) => row.id));
}

/** 全选开关：打开则选中当前列表全部行，关闭则清空勾选 */
function handleSelectedAllChange(on: boolean) {
  if (!on) {
    selectedRowKeys.value = new Set();
    return;
  }
  selectAllVisibleOrderRows();
}

/** 患者或筛选变化时默认展开全部卡片 */
watch(
  visibleCards,
  (cards) => {
    expandedPatientIds.value = cards.map((item) => item.patient.id);
  },
  { immediate: true },
);

/** 筛选或患者变化时自动拉取列表 */
watch(
  [encounterIds, activeStatus, activeTimeType],
  () => {
    void loadAuditList();
  },
  { immediate: true },
);

/** 普通场景下主文案：优先 content */
function auditOrderDisplayedContent(item: NurseStationOrderAuditTableRow): string {
  const c = item.content?.trim();
  if (c && c.length > 0) return item.content;
  return item.typeLabel;
}

/** 判断某行是否已勾选 */
function isRowSelected(row: NurseStationOrderAuditTableRow): boolean {
  return selectedRowKeys.value.has(row.id);
}

/** 切换单行勾选 */
function toggleRowSelection(row: NurseStationOrderAuditTableRow, checked: boolean) {
  const next = new Set(selectedRowKeys.value);
  if (checked) {
    next.add(row.id);
  } else {
    next.delete(row.id);
  }
  selectedRowKeys.value = next;
}

/** 收集当前勾选的表格行 */
function collectSelectedRows(): NurseStationOrderAuditTableRow[] {
  const keySet = selectedRowKeys.value;
  return allVisibleOrderRows.value.filter((row) => keySet.has(row.id));
}

/** 按当前患者与 Tab 条件加载医嘱校对列表 */
async function loadAuditList() {
  selectedRowKeys.value = new Set();
  selectedAll.value = false;
  ordersByEncounterId.value = new Map();

  if (encounterIds.value.length === 0) {
    return;
  }

  const res = await listRequest.run({
    encounterIds: encounterIds.value,
    auditTab: activeStatus.value,
    therapyType: activeTimeType.value,
  });
  ordersByEncounterId.value = indexAuditGroupsByEncounterId(res.groups);
  selectAllVisibleOrderRows();
}

/** 工具栏「查询」：显式刷新列表 */
function handleRunFilterQuery() {
  void loadAuditList();
}

/** 校验批量条数并提示 */
function assertBatchWithinLimit(count: number): boolean {
  if (count <= 0) {
    ElMessage.warning('请先勾选医嘱');
    return false;
  }
  if (count > WRITE_BATCH_LIMIT) {
    ElMessage.warning(`单次最多处理 ${WRITE_BATCH_LIMIT} 条医嘱`);
    return false;
  }
  return true;
}

/** 校对成功后刷新列表与左侧待校对计数 */
async function afterWriteSuccess(message: string) {
  ElMessage.success(message);
  selectedRowKeys.value = new Set();
  selectedAll.value = false;
  await loadAuditList();
  await listPanelContext?.refreshPatientFilters();
}

/** 批量校对通过 */
async function handleVerifyOrders() {
  if (!writeActionsEnabled.value) return;
  const rows = collectSelectedRows();
  if (!assertBatchWithinLimit(rows.length)) return;

  try {
    await ElMessageBox.confirm(`确认校对通过已选 ${rows.length} 条医嘱？`, '校对通过', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  const res = await verifyRequest.run({ items: mapTableRowsToWriteItems(rows) });
  const n = res.processedCount ?? rows.length;
  await afterWriteSuccess(`已成功校对 ${n} 条医嘱`);
}

/** 批量退回（可选统一原因） */
async function handleRejectOrders() {
  if (!writeActionsEnabled.value) return;
  const rows = collectSelectedRows();
  if (!assertBatchWithinLimit(rows.length)) return;

  let reason = '';
  try {
    const { value } = await ElMessageBox.prompt('请输入退回原因（可选）', '退回医嘱', {
      confirmButtonText: '确定退回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '退回原因',
    });
    reason = String(value ?? '').trim();
  } catch {
    return;
  }

  const res = await rejectRequest.run({
    items: mapTableRowsToWriteItems(rows),
    reason: reason || undefined,
  });
  const n = res.processedCount ?? rows.length;
  await afterWriteSuccess(`已成功退回 ${n} 条医嘱`);
}

function resolveSelectedRowKeys(): Set<string> | undefined {
  if (selectedRowKeys.value.size === 0) {
    return undefined;
  }
  return selectedRowKeys.value;
}

async function handlePrintOrderAudit(label: string) {
  const cards = visibleCards.value.filter((card) => card.orders.length > 0);
  if (!cards.length) {
    ElMessage.warning('当前没有可打印的医嘱');
    return;
  }
  const rowFilter = resolveSelectedRowKeys();

  printActionLoading.value = true;
  try {
    if (label === '打印瓶贴') {
      let totalGroups = 0;
      for (const card of cards) {
        const groups = groupInpatientAuditInfusionLabelPrintTargets(card.orders, rowFilter);
        if (!groups.length) continue;
        await printIvInfusionLabels({
          encounterId: card.patient.id,
          groups,
          jobName: `输液瓶贴-${card.patient.name}`,
        });
        totalGroups += groups.length;
      }
      if (!totalGroups) {
        ElMessage.warning('没有可打印的药品医嘱');
        return;
      }
      ElMessage.success(`已发送 ${totalGroups} 张输液瓶贴`);
      return;
    }

    if (label === '打印采血码') {
      let totalTargets = 0;
      for (const card of cards) {
        const targets = groupInpatientAuditSpecimenBarcodePrintTargets(card.orders, rowFilter);
        if (!targets.length) continue;
        await printBedsideSpecimenBarcodes({
          encounterId: card.patient.id,
          targets,
          jobName: `采血条码-${card.patient.name}`,
        });
        totalTargets += targets.length;
      }
      if (!totalTargets) {
        ElMessage.warning('没有可打印的采血/检验医嘱');
        return;
      }
      ElMessage.success(`已发送 ${totalTargets} 张采血条码`);
      return;
    }

    if (label === '打印处置单') {
      let printed = 0;
      for (const card of cards) {
        const target = groupInpatientAuditTreatmentExecutePrintTargets(card.orders, rowFilter);
        if (!target) continue;
        await printTreatmentExecuteSheet({
          encounterId: card.patient.id,
          target,
          jobName: `处置执行单-${card.patient.name}`,
        });
        printed += 1;
      }
      if (!printed) {
        ElMessage.warning('没有可打印的处置医嘱');
        return;
      }
      ElMessage.success(`已发送 ${printed} 份处置执行单`);
      return;
    }

    if (label === '打印处方单') {
      let totalPrescriptions = 0;
      for (const card of cards) {
        const medicationRequestIds = groupInpatientAuditMedicationRequestIds(
          card.orders,
          rowFilter,
        );
        if (!medicationRequestIds.length) continue;
        const count = await printNursePrescriptions({
          encounterId: card.patient.id,
          medicationRequestIds,
        });
        totalPrescriptions += count;
      }
      if (!totalPrescriptions) {
        ElMessage.warning('没有可打印的药品处方');
        return;
      }
      ElMessage.success(`已发送 ${totalPrescriptions} 份处方单`);
    }
  } catch {
    // composable 已记录日志
  } finally {
    printActionLoading.value = false;
  }
}
</script>

<template>
  <div
    v-loading="
      listRequest.loading.value ||
      verifyRequest.loading.value ||
      rejectRequest.loading.value ||
      printActionLoading
    "
    class="audit-module flex h-full min-h-0 w-full flex-1 flex-col overflow-hidden rounded-md bg-[#eef9fd]"
  >
    <section
      class="flex w-full flex-wrap items-center justify-between gap-2 border-b border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-bg-color)] px-2 py-1.5"
    >
      <div class="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-2">
        <ElRadioGroup v-model="activeStatus" class="audit-status-radio shrink-0">
          <ElRadioButton v-for="status in statusTabs" :key="status.value" :label="status.value">
            {{ status.label }}
          </ElRadioButton>
        </ElRadioGroup>
        <ElRadioGroup
          v-model="activeTimeType"
          class="audit-time-type-radio inline-flex shrink-0 flex-wrap items-center gap-3"
        >
          <ElRadio label="all">全部</ElRadio>
          <ElRadio label="long">长期</ElRadio>
          <ElRadio label="temp">临时</ElRadio>
        </ElRadioGroup>
        <ElButton plain @click="handleRunFilterQuery">查询</ElButton>
      </div>
      <div class="flex flex-wrap items-center gap-1">
        <span class="mr-1 shrink-0 text-xs text-[var(--el-text-color-secondary)]">
          {{ cardCountText }}
        </span>
        <label
          class="mr-1 flex shrink-0 items-center gap-1 text-xs text-[var(--el-text-color-regular)]"
        >
          全选
          <ElSwitch
            :model-value="selectedAll"
            :disabled="allVisibleOrderRows.length === 0"
            inline-prompt
            active-text=" "
            inactive-text=" "
            @update:model-value="handleSelectedAllChange"
          />
        </label>
        <ElButton type="primary" :disabled="!writeActionsEnabled" @click="handleVerifyOrders">
          校对通过
        </ElButton>
        <ElButton type="danger" plain :disabled="!writeActionsEnabled" @click="handleRejectOrders">
          退回
        </ElButton>
        <ElButton plain @click="handlePrintOrderAudit('打印瓶贴')">打印瓶贴</ElButton>
        <ElButton plain @click="handlePrintOrderAudit('打印采血码')">打印采血码</ElButton>
        <ElButton plain @click="handlePrintOrderAudit('打印处置单')">打印处置单</ElButton>
        <ElButton plain @click="handlePrintOrderAudit('打印处方单')">打印处方单</ElButton>
      </div>
    </section>

    <section v-if="visibleCards.length > 0" class="min-h-0 flex-1 overflow-auto p-2.5">
      <ElCollapse v-model="expandedPatientIds" class="patient-collapse">
        <ElCollapseItem
          v-for="card in visibleCards"
          :key="card.patient.id"
          :name="card.patient.id"
          class="patient-order-card mb-2.5 overflow-hidden rounded-lg border border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-bg-color)] last:mb-0"
        >
          <template #title>
            <NurseStationPatientOrderCollapseTitle :patient="card.patient" />
          </template>

          <div class="card-table-wrap">
            <ElTable
              v-if="card.orders.length > 0"
              :data="card.orders"
              class="order-table order-table--horizontal-only"
              stripe
              row-key="id"
            >
              <ElTableColumn align="center" width="54">
                <template #default="{ row }">
                  <ElCheckbox
                    :model-value="isRowSelected(row)"
                    :disabled="!writeActionsEnabled"
                    @update:model-value="(v: boolean) => toggleRowSelection(row, v)"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn align="center" label="类型" min-width="108">
                <template #default="{ row }">
                  <span>{{ row.orderKindLabel }}</span>
                  <template v-if="row.therapyLabel">
                    <span class="ip-advice-type-sep"> / </span>
                    <span
                      class="ip-advice-therapy-text"
                      :class="adviceTherapyTypeClass(row.therapyTypeCode)"
                    >
                      {{ row.therapyLabel }}
                    </span>
                  </template>
                </template>
              </ElTableColumn>
              <ElTableColumn label="医嘱内容" min-width="360">
                <template #default="{ row }">
                  <div class="order-time">
                    <AdviceOrderDisplayCell
                      :row-key="row.id"
                      :content="auditOrderDisplayedContent(row)"
                      :dosage-text="row.dosage"
                      :extension="row.extension"
                    />
                    <span v-if="row.halted && row.stopTime" class="halt-pill" title="已停嘱">
                      停
                    </span>
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="executor" align="center" label="执行科室" min-width="100" />
              <ElTableColumn prop="executeTime" align="center" label="签发时间" min-width="110" />
              <ElTableColumn prop="doctorName" align="center" label="开立医生" min-width="96" />
            </ElTable>
            <div v-else class="py-6 text-center text-sm text-[var(--el-text-color-secondary)]">
              当前筛选下暂无医嘱
            </div>
          </div>
        </ElCollapseItem>
      </ElCollapse>
    </section>
    <section v-else class="flex min-h-0 min-w-0 flex-1 items-center justify-center bg-[#eef9fd]">
      <ElEmpty description="请先选择患者" />
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '../components/nurse-station-patient-order-list.scss';

.audit-time-type-radio :deep(.el-radio) {
  margin-right: 0;
  height: auto;
  align-items: center;
}

.order-time {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.halt-pill {
  height: 20px;
  min-width: 20px;
  border-radius: 4px;
  background: var(--el-color-danger);
  color: var(--el-color-white);
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}
</style>
