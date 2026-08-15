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
import { useRequest } from '#/hooks/use-request';

import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import { nurseStationPatientListPanelContextKey } from '../components/patient-list-panel.context';
import NurseStationPatientOrderCollapseTitle from '../components/nurse-station-patient-order-collapse-title.vue';

import {
  filterAuditIndexByProjectTypes,
  indexAuditGroupsByEncounterId,
  mapTableRowsToWriteItems,
  mergeAuditPatientGroups,
  NURSE_STATION_ORDER_AUDIT_PROJECT_TYPES,
  type NurseStationOrderAuditProjectType,
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

/** 项目类型多选选项（value=code，label 仅展示；含须校对的特殊诊疗） */
const projectTypeOptions: Array<{ label: string; value: NurseStationOrderAuditProjectType }> = [
  { label: '西药', value: 'west' },
  { label: '中药', value: 'tcm' },
  { label: '检验', value: 'lab' },
  { label: '检查', value: 'exam' },
  { label: '治疗', value: 'treatment' },
  { label: '嘱托', value: 'entrust' },
  { label: '护理', value: 'nursing' },
  { label: '转科', value: 'transfer' },
  { label: '出院', value: 'discharge' },
];

const listPanelContext = inject(nurseStationPatientListPanelContextKey, null);

const statusTabs: Array<{ label: string; value: AuditStatus }> = [
  { label: '未校对', value: 'pending' },
  { label: '已校对', value: 'passed' },
];

const activeStatus = ref<AuditStatus>('pending');
/** 项目类型多选；默认不选（空选按全选拉取并过滤） */
const selectedProjectTypes = ref<NurseStationOrderAuditProjectType[]>([]);
const activeTimeType = ref<TimeTypeFilter>('all');
const selectedAll = ref(false);
const selectedRowKeys = ref<Set<string>>(new Set());
const ordersByEncounterId = ref<Map<string, NurseStationOrderAuditTableRow[]>>(new Map());
/** 列表拉取 loading（可并行 west/tcm 两次请求） */
const listFetching = ref(false);

const verifyRequest = useRequest(httpVerifyNurseStationOrders, { manual: true });
const rejectRequest = useRequest(httpRejectNurseStationOrders, { manual: true });

const encounterIds = computed(() =>
  props.patients.map((p) => String(p.id).trim()).filter((id) => id.length > 0),
);

/** 未校对 Tab 才允许批量校对 */
const verifyActionsEnabled = computed(() => activeStatus.value === 'pending');

/**
 * 行勾选：pending / passed 均可勾选。
 * 停嘱待校须可勾选以便「停嘱校对」。
 */
const rowSelectionEnabled = computed(
  () => activeStatus.value === 'pending' || activeStatus.value === 'passed',
);

/**
 * 取消校对按钮：
 * - passed：有停嘱校对人 → 取消停嘱校对；否则取消开嘱校对
 * - pending：仅当勾选含开嘱待校行
 */
const rejectActionsEnabled = computed(() => {
  if (activeStatus.value === 'passed') {
    return true;
  }
  if (activeStatus.value !== 'pending') {
    return false;
  }
  return collectSelectedRows().some((r) => r.stopAuditPending !== true);
});

/** 已校对 Tab：同时展示开嘱校对人与停嘱校对人 */
const showCheckerColumns = computed(() => activeStatus.value === 'passed');

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
  [encounterIds, activeStatus, selectedProjectTypes, activeTimeType],
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

/** 按当前患者与筛选条件加载医嘱校对列表 */
async function loadAuditList() {
  selectedRowKeys.value = new Set();
  selectedAll.value = false;
  ordersByEncounterId.value = new Map();

  if (encounterIds.value.length === 0) {
    return;
  }

  // 空选按全选；决定需打 west（西药+诊疗）/ tcm（中药方）哪个后端 Tab
  const selected =
    selectedProjectTypes.value.length > 0
      ? selectedProjectTypes.value
      : NURSE_STATION_ORDER_AUDIT_PROJECT_TYPES;
  const needTcm = selected.includes('tcm');
  const needWest = selected.some((t) => t !== 'tcm');

  const base = {
    encounterIds: encounterIds.value,
    auditTab: activeStatus.value,
    therapyType: activeTimeType.value,
  };

  listFetching.value = true;
  try {
    const tasks: Array<ReturnType<typeof httpListNurseStationOrderAudit>> = [];
    if (needWest) {
      tasks.push(httpListNurseStationOrderAudit({ ...base, medicineCategoryTab: 'west' }));
    }
    if (needTcm) {
      tasks.push(httpListNurseStationOrderAudit({ ...base, medicineCategoryTab: 'tcm' }));
    }
    if (tasks.length === 0) {
      return;
    }
    const results = await Promise.all(tasks);
    const mergedGroups = mergeAuditPatientGroups(results.map((res) => res.groups));
    const indexed = indexAuditGroupsByEncounterId(mergedGroups);
    ordersByEncounterId.value = filterAuditIndexByProjectTypes(indexed, selected);
    selectAllVisibleOrderRows();
  } finally {
    listFetching.value = false;
  }
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

/** 校对成功后刷新列表、队列与出 Tab 床位图 */
async function afterWriteSuccess(message: string) {
  ElMessage.success(message);
  selectedRowKeys.value = new Set();
  selectedAll.value = false;
  await loadAuditList();
  if (listPanelContext?.refreshWorkbenchAfterEncounterFlowChange) {
    await listPanelContext.refreshWorkbenchAfterEncounterFlowChange();
  } else {
    await listPanelContext?.refreshPatientFilters();
  }
}

/** 批量校对通过（开嘱校对或停嘱校对，由后端按状态分支） */
async function handleVerifyOrders() {
  if (!verifyActionsEnabled.value) return;
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

/**
 * 是否已停嘱校对行（已校对 Tab 内：STOPPED 且非停嘱待校）。
 *
 * @param row 表格行
 */
function isStopCheckedAuditRow(row: NurseStationOrderAuditTableRow): boolean {
  if (row.stopAuditPending === true) {
    return false;
  }
  const status = String(row.statusEnum ?? '')
    .trim()
    .toUpperCase();
  if (status === 'STOPPED' || row.halted === true) {
    return true;
  }
  // 停嘱校对人有值亦视为已停嘱校对
  return Boolean(row.stopCheckerName && row.stopCheckerName !== '—');
}

/**
 * 退回：已校对且已停嘱 → 取消停嘱校对（确认框）；否则开嘱退回草稿（原因可选）。
 */
async function handleRejectOrders() {
  if (!rejectActionsEnabled.value) return;
  const rows = collectSelectedRows();
  if (!assertBatchWithinLimit(rows.length)) return;

  // pending 上误勾停嘱待校：剔除后仅退回开嘱待校行
  const rejectRows =
    activeStatus.value === 'pending' ? rows.filter((r) => r.stopAuditPending !== true) : rows;
  if (rejectRows.length === 0) {
    ElMessage.warning('停嘱待校医嘱请先「校对通过」，不可退回');
    return;
  }
  if (!assertBatchWithinLimit(rejectRows.length)) return;

  // 已校对：STOPPED → 取消停嘱校对；VERIFIED → 退回草稿
  if (activeStatus.value === 'passed') {
    const stopCheckedRows = rejectRows.filter((r) => isStopCheckedAuditRow(r));
    const openCheckedRows = rejectRows.filter((r) => !isStopCheckedAuditRow(r));
    // 已停嘱校对：确认框取消停嘱校对（不填原因）
    if (stopCheckedRows.length > 0) {
      try {
        await ElMessageBox.confirm(
          `确认取消已选 ${stopCheckedRows.length} 条医嘱的停嘱校对？取消后医生可取消停嘱。`,
          '取消停嘱校对',
          { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
        );
      } catch {
        return;
      }
      const res = await rejectRequest.run({ items: mapTableRowsToWriteItems(stopCheckedRows) });
      const n = res.processedCount ?? stopCheckedRows.length;
      await afterWriteSuccess(`已取消 ${n} 条停嘱校对`);
    }
    // 仅开嘱校对：退回草稿
    if (openCheckedRows.length > 0) {
      let reason = '';
      try {
        const { value } = await ElMessageBox.prompt('请输入退回原因（可选）', '退回', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputPlaceholder: '退回原因',
        });
        reason = String(value ?? '').trim();
      } catch {
        return;
      }
      const res = await rejectRequest.run({
        items: mapTableRowsToWriteItems(openCheckedRows),
        reason: reason || undefined,
      });
      const n = res.processedCount ?? openCheckedRows.length;
      await afterWriteSuccess(`已退回 ${n} 条医嘱`);
    }
    return;
  }

  let reason = '';
  try {
    const { value } = await ElMessageBox.prompt('请输入退回原因（可选）', '退回', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '退回原因',
    });
    reason = String(value ?? '').trim();
  } catch {
    return;
  }

  const res = await rejectRequest.run({
    items: mapTableRowsToWriteItems(rejectRows),
    reason: reason || undefined,
  });
  const n = res.processedCount ?? rejectRows.length;
  await afterWriteSuccess(`已退回 ${n} 条医嘱`);
}
</script>

<template>
  <div
    v-loading="listFetching || verifyRequest.loading.value || rejectRequest.loading.value"
    class="audit-module flex h-full min-h-0 w-full flex-1 flex-col overflow-hidden rounded-md bg-[#eef9fd]"
  >
    <section
      class="audit-toolbar flex w-full flex-nowrap items-center justify-between gap-x-3 border-b border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-bg-color)] px-2 py-1.5"
    >
      <div class="flex min-w-0 flex-nowrap items-center gap-x-3 overflow-x-auto">
        <ElRadioGroup v-model="activeStatus" class="audit-filter-segment shrink-0">
          <ElRadioButton v-for="status in statusTabs" :key="status.value" :label="status.value">
            {{ status.label }}
          </ElRadioButton>
        </ElRadioGroup>
        <span class="audit-toolbar__divider" aria-hidden="true"></span>
        <span class="shrink-0 text-sm text-[var(--el-text-color-regular)]">项目类型</span>
        <ElCheckboxGroup
          v-model="selectedProjectTypes"
          class="audit-project-type-checkboxes inline-flex min-w-0 flex-nowrap items-center gap-x-3"
        >
          <ElCheckbox
            v-for="opt in projectTypeOptions"
            :key="opt.value"
            :value="opt.value"
            :label="opt.value"
          >
            {{ opt.label }}
          </ElCheckbox>
        </ElCheckboxGroup>
        <span class="audit-toolbar__divider" aria-hidden="true"></span>
        <ElRadioGroup
          v-model="activeTimeType"
          class="audit-time-type-radio inline-flex shrink-0 items-center gap-3"
        >
          <ElRadio label="all">全部</ElRadio>
          <ElRadio label="long">长期</ElRadio>
          <ElRadio label="temp">临时</ElRadio>
        </ElRadioGroup>
        <ElButton plain @click="handleRunFilterQuery">查询</ElButton>
      </div>
      <div class="flex shrink-0 flex-nowrap items-center gap-2">
        <span class="shrink-0 text-xs text-[var(--el-text-color-secondary)]">
          {{ cardCountText }}
        </span>
        <label class="flex shrink-0 items-center gap-1 text-xs text-[var(--el-text-color-regular)]">
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
        <span class="audit-toolbar__divider" aria-hidden="true"></span>
        <ElButton type="primary" :disabled="!verifyActionsEnabled" @click="handleVerifyOrders">
          校对通过
        </ElButton>
        <ElButton type="danger" plain :disabled="!rejectActionsEnabled" @click="handleRejectOrders">
          退回
        </ElButton>
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
                    :disabled="!rowSelectionEnabled"
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
                  <div class="nurse-order-content-wrap">
                    <div class="nurse-order-content-cell">
                      <AdviceOrderDisplayCell
                        :row-key="row.id"
                        :content="auditOrderDisplayedContent(row)"
                        :dosage-text="row.dosage"
                        :extension="row.extension"
                        :group-items="row.groupItems"
                      />
                    </div>
                    <span
                      v-if="row.skinTestFlag"
                      class="skin-test-pill"
                      title="该药品需皮试，校对后请在皮试管理登记结果"
                    >
                      皮试
                    </span>
                    <span
                      v-if="row.stopAuditPending"
                      class="stop-audit-pill"
                      title="医生已停嘱，待护士停嘱校对"
                    >
                      停嘱待校
                    </span>
                    <span v-else-if="row.halted && row.stopTime" class="halt-pill" title="已停嘱">
                      停
                    </span>
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="showCheckerColumns"
                prop="performerCheckName"
                align="center"
                label="校对人"
                min-width="96"
              />
              <ElTableColumn
                v-if="showCheckerColumns"
                prop="stopCheckerName"
                align="center"
                label="停嘱校对人"
                min-width="108"
              >
                <template #default="{ row }">
                  {{ row.stopCheckerName || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn align="center" label="开立/停嘱" min-width="200">
                <template #default="{ row }">
                  <div class="start-stop-cell">
                    <div class="start-stop-line start-stop-line--start">
                      <span class="start-stop-tag">开</span>
                      <span class="start-stop-text">{{ row.executeTime }}</span>
                    </div>
                    <div
                      v-if="row.stopTime"
                      class="start-stop-line start-stop-line--stop"
                      title="停嘱时间 / 停嘱医生"
                    >
                      <span class="start-stop-tag">停</span>
                      <span class="start-stop-text">{{ row.stopTimeDisplay }}</span>
                    </div>
                  </div>
                </template>
              </ElTableColumn>
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

.audit-toolbar__divider {
  display: inline-block;
  width: 1px;
  height: 20px;
  margin: 0 2px;
  background: var(--el-border-color-lighter);
  flex-shrink: 0;
}

/** 状态/药类 Segment：与执行页一致，选中用浅底描边，避免实心蓝抢主操作 */
.audit-filter-segment :deep(.el-radio-button__inner) {
  color: var(--el-text-color-regular);
  background-color: var(--el-fill-color-blank);
  border-color: var(--el-border-color);
  box-shadow: none !important;
}

.audit-filter-segment :deep(.el-radio-button.is-active .el-radio-button__inner) {
  color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
  box-shadow: none !important;
}

.audit-filter-segment :deep(.el-radio-button:not(.is-active) .el-radio-button__inner:hover) {
  color: var(--el-color-primary);
}

.audit-time-type-radio :deep(.el-radio) {
  margin-right: 0;
  height: auto;
  align-items: center;
}

.audit-project-type-checkboxes :deep(.el-checkbox) {
  margin-right: 0;
  height: auto;
  align-items: center;
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

/** 停嘱待校醒目标识：与开嘱待校行区分 */
.stop-audit-pill {
  flex-shrink: 0;
  height: 20px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--el-color-danger);
  color: var(--el-color-white);
  font-size: 12px;
  line-height: 20px;
  text-align: center;
  white-space: nowrap;
  font-weight: 600;
}

.skin-test-pill {
  flex-shrink: 0;
  height: 20px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--el-color-warning);
  color: var(--el-color-white);
  font-size: 12px;
  line-height: 20px;
  text-align: center;
  white-space: nowrap;
}

/* 开立/停嘱分行；停嘱行用危险色加粗醒目 */
.start-stop-cell {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  text-align: left;
  line-height: 1.35;
}

.start-stop-line {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  font-size: 13px;
}

.start-stop-tag {
  flex-shrink: 0;
  min-width: 16px;
  height: 16px;
  padding: 0 3px;
  border-radius: 3px;
  font-size: 11px;
  line-height: 16px;
  text-align: center;
  font-weight: 600;
}

.start-stop-line--start {
  color: var(--el-text-color-regular);
}

.start-stop-line--start .start-stop-tag {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
}

.start-stop-line--stop {
  color: var(--el-color-danger);
  font-weight: 600;
}

.start-stop-line--stop .start-stop-tag {
  color: var(--el-color-white);
  background: var(--el-color-danger);
}

.start-stop-text {
  white-space: nowrap;
}
</style>
