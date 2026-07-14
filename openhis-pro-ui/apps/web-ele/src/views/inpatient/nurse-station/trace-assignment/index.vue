<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type { PatientTraceAssignmentQueueRowDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-trace-assignment.types';

import { computed, onMounted, ref, watch } from 'vue';

import {
  ElButton,
  ElEmpty,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  httpListPatientTraceAssignmentGroups,
  httpPagePatientsPendingTraceAssignment,
  httpSavePatientTraceAssignment,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-trace-assignment.http';
import InpatientPatientInfoHeader from '#/components/inpatient/patient-info-header.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import TraceAssignmentScanDialog from './components/trace-assignment-scan-dialog.vue';
import {
  buildNurseStationStickyPatientInfoFromModulePatient,
  buildNurseStationStickyPatientInfoPlaceholder,
} from '../components/build-nurse-station-sticky-patient-info';
import {
  buildSavePatientTraceAssignmentRequest,
  formatTraceAssignmentQuantity,
  hasTraceAssignmentChanges,
  mapGroupsToDrafts,
  type TraceAssignmentGroupDraft,
} from './services/map-nurse-station-trace-assignment';

interface Props {
  patients?: NurseStationModulePatient[];
}

defineOptions({
  name: 'InpatientNurseStationTraceAssignmentModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

const PENDING_PAGE_SIZE = 20;

const pendingKeyword = ref('');
const pendingPageNo = ref(1);
const pendingRows = ref<PatientTraceAssignmentQueueRowDto[]>([]);
const pendingTotal = ref(0);
const queueEncounterId = ref('');
const groupDrafts = ref<TraceAssignmentGroupDraft[]>([]);
const scanDialogVisible = ref(false);

const pendingRequest = useRequest(httpPagePatientsPendingTraceAssignment, { manual: true });
const groupsRequest = useRequest(httpListPatientTraceAssignmentGroups, { manual: true });
const saveRequest = useRequest(httpSavePatientTraceAssignment, { manual: true });

/** 追溯分配仅支持左侧单选患者 */
const canOperateTraceAssignment = computed(() => props.patients.length === 1);

/** 左侧选中患者的就诊上下文 */
const selectedPatientContext = computed(() => {
  if (!canOperateTraceAssignment.value) {
    return null;
  }
  const patient = props.patients[0];
  if (!patient) {
    return null;
  }
  const encounterId = preserveApiIdForRequest(patient.id);
  if (!encounterId) {
    return null;
  }
  return { encounterId, patient };
});

/** 当前操作的就诊 ID：优先左侧选中，其次待办队列点选 */
const effectiveEncounterId = computed(() => {
  if (selectedPatientContext.value?.encounterId) {
    return selectedPatientContext.value.encounterId;
  }
  return queueEncounterId.value;
});

/** 顶部患者信息条 */
const stickyPatientInfo = computed(() => {
  if (selectedPatientContext.value) {
    return buildNurseStationStickyPatientInfoFromModulePatient(
      selectedPatientContext.value.patient,
    );
  }
  const queueRow = pendingRows.value.find(
    (row) => preserveApiIdForRequest(row.encounterId) === effectiveEncounterId.value,
  );
  if (queueRow) {
    return {
      bedNo: queueRow.bedName ?? '—',
      diagnosis: `待分配 ${queueRow.pendingGroupCount ?? 0} 组`,
      name: queueRow.patientName ?? '—',
      inpatientNo: queueRow.encounterBusNo ?? '—',
      insuranceType: '—',
    };
  }
  if (props.patients.length > 1) {
    return buildNurseStationStickyPatientInfoPlaceholder({
      diagnosis: `当前已选 ${props.patients.length} 位患者（请单选后分配追溯码）`,
      name: '请选择单一患者',
    });
  }
  return null;
});

const effectivePatientName = computed(() => stickyPatientInfo.value?.name ?? '');

const groupsLoading = computed(() => groupsRequest.loading.value);

const pageLoading = computed(
  () => pendingRequest.loading.value || groupsRequest.loading.value || saveRequest.loading.value,
);

const pendingCountText = computed(() => {
  if (pendingTotal.value <= 0) {
    return '暂无待分配患者';
  }
  return `待分配 ${pendingTotal.value} 人`;
});

const groupCountText = computed(() => {
  if (!effectiveEncounterId.value) {
    return '';
  }
  if (groupDrafts.value.length <= 0) {
    return '暂无药批汇总行';
  }
  return `共 ${groupDrafts.value.length} 组`;
});

const pendingPageCount = computed(() =>
  Math.max(1, Math.ceil(pendingTotal.value / PENDING_PAGE_SIZE)),
);

/** 加载待追溯分配患者队列 */
async function loadPendingQueue() {
  const res = await pendingRequest.run({
    pageNo: pendingPageNo.value,
    pageSize: PENDING_PAGE_SIZE,
    keyword: pendingKeyword.value.trim() || undefined,
  });
  pendingRows.value = res.content ?? [];
  pendingTotal.value = res.totalCount ?? 0;
}

/** 加载当前就诊的药批汇总组 */
async function loadAssignmentGroups() {
  const encounterId = effectiveEncounterId.value;
  if (!encounterId) {
    groupDrafts.value = [];
    return;
  }
  const res = await groupsRequest.run({ encounterId });
  groupDrafts.value = mapGroupsToDrafts(res.groups ?? []);
}

/** 待办队列行点击后切换当前就诊 */
function handlePendingRowClick(row: PatientTraceAssignmentQueueRowDto) {
  const encounterId = preserveApiIdForRequest(row.encounterId);
  if (!encounterId) {
    return;
  }
  queueEncounterId.value = encounterId;
  void loadAssignmentGroups();
}

/** 判断待办行是否为当前操作就诊 */
function isPendingRowActive(row: PatientTraceAssignmentQueueRowDto): boolean {
  return preserveApiIdForRequest(row.encounterId) === effectiveEncounterId.value;
}

/** 待办队列表格行样式 */
function resolvePendingRowClassName({ row }: { row: PatientTraceAssignmentQueueRowDto }): string {
  return isPendingRowActive(row) ? 'trace-queue-row--active' : '';
}

/** 待办队列关键字搜索 */
function handlePendingSearch() {
  pendingPageNo.value = 1;
  void loadPendingQueue();
}

/** 待办队列翻页 */
function handlePendingPageChange(pageNo: number) {
  pendingPageNo.value = pageNo;
  void loadPendingQueue();
}

/** 打开批量扫码弹窗 */
function handleOpenScanDialog() {
  if (!effectiveEncounterId.value) {
    ElMessage.warning('请先选择患者或从待办队列点选');
    return;
  }
  if (groupDrafts.value.length === 0) {
    ElMessage.warning('当前患者无汇总核发后的待分配药品');
    return;
  }
  scanDialogVisible.value = true;
}

/** 应用扫码匹配结果到编辑态 */
function handleScanApplied(nextDrafts: TraceAssignmentGroupDraft[]) {
  groupDrafts.value = nextDrafts;
}

/** 保存追溯码分配到发放行 */
async function handleSaveAssignment() {
  const encounterId = effectiveEncounterId.value;
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (!hasTraceAssignmentChanges(groupDrafts.value)) {
    ElMessage.warning('请录入或扫描追溯码后再保存');
    return;
  }
  try {
    await ElMessageBox.confirm('确认保存当前患者的追溯码分配？', '保存追溯码', {
      type: 'warning',
      confirmButtonText: '确认保存',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  const body = buildSavePatientTraceAssignmentRequest(encounterId, groupDrafts.value);
  if (body.groups.length === 0) {
    ElMessage.warning('没有可保存的汇总组');
    return;
  }
  const res = await saveRequest.run(body);
  ElMessage.success(`追溯码分配已保存（${res.savedGroupCount ?? body.groups.length} 组）`);
  await Promise.all([loadAssignmentGroups(), loadPendingQueue()]);
}

/** 分配状态标签类型 */
function resolveAssignmentTagType(status?: string): 'info' | 'success' | 'warning' {
  if (status === 'ASSIGNED') {
    return 'success';
  }
  if (status === 'PENDING') {
    return 'warning';
  }
  return 'info';
}

watch(
  () => selectedPatientContext.value?.encounterId,
  (encounterId) => {
    if (encounterId) {
      queueEncounterId.value = '';
      void loadAssignmentGroups();
      return;
    }
    if (!queueEncounterId.value) {
      groupDrafts.value = [];
    }
  },
);

onMounted(async () => {
  await loadPendingQueue();
  if (effectiveEncounterId.value) {
    await loadAssignmentGroups();
  }
});
</script>

<template>
  <div class="trace-assignment-module flex h-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
    <InpatientPatientInfoHeader :patient-info="stickyPatientInfo" />

    <div
      v-loading="pageLoading"
      class="trace-assignment-module__workspace flex min-h-0 flex-1 flex-col overflow-hidden rounded-md bg-[#eef9fd]"
    >
      <section
        class="trace-assignment-toolbar flex w-full flex-wrap items-center justify-between gap-2 border-b border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-bg-color)] px-2 py-1.5"
      >
        <div class="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-2">
          <span class="shrink-0 text-sm text-[var(--el-text-color-regular)]">待分配患者</span>
          <ElInput
            v-model="pendingKeyword"
            class="trace-assignment-toolbar__keyword"
            clearable
            placeholder="姓名/床号/就诊号"
            @keyup.enter="handlePendingSearch"
          />
          <ElButton plain @click="handlePendingSearch">查询</ElButton>
          <ElButton plain @click="loadPendingQueue">刷新</ElButton>
        </div>
        <div class="flex flex-wrap items-center gap-1">
          <span class="mr-1 shrink-0 text-xs text-[var(--el-text-color-secondary)]">
            {{ effectiveEncounterId ? groupCountText : pendingCountText }}
          </span>
          <ElButton
            type="primary"
            :disabled="!effectiveEncounterId || groupDrafts.length === 0"
            @click="handleOpenScanDialog"
          >
            批量扫码
          </ElButton>
          <ElButton
            type="success"
            :disabled="!effectiveEncounterId"
            :loading="saveRequest.loading.value"
            @click="handleSaveAssignment"
          >
            保存分配
          </ElButton>
        </div>
      </section>

      <section class="trace-assignment-content min-h-0 flex-1 overflow-auto p-2.5">
        <div
          class="trace-assignment-card overflow-hidden rounded-lg border border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-bg-color)]"
        >
          <div class="trace-assignment-card__head">
            <SectionTitle title="待分配患者队列" />
          </div>
          <div class="trace-assignment-card__body">
            <ElTable
              :data="pendingRows"
              class="order-table order-table--horizontal-only trace-queue-table"
              stripe
              highlight-current-row
              :row-class-name="resolvePendingRowClassName"
              @row-click="handlePendingRowClick"
            >
              <ElTableColumn prop="bedName" label="床号" width="88" align="center" />
              <ElTableColumn
                prop="patientName"
                label="患者"
                min-width="120"
                show-overflow-tooltip
              />
              <ElTableColumn prop="pendingGroupCount" label="待分配组" width="96" align="center" />
              <ElTableColumn label="操作" width="80" align="center">
                <template #default="{ row }">
                  <ElButton link type="primary" @click.stop="handlePendingRowClick(row)">
                    {{ isPendingRowActive(row) ? '当前' : '选择' }}
                  </ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
            <div
              v-if="pendingTotal > PENDING_PAGE_SIZE"
              class="trace-assignment-card__pager flex items-center justify-end gap-2"
            >
              <ElButton
                size="small"
                plain
                :disabled="pendingPageNo <= 1"
                @click="handlePendingPageChange(pendingPageNo - 1)"
              >
                上一页
              </ElButton>
              <span class="text-xs text-[var(--el-text-color-secondary)]">
                {{ pendingPageNo }} / {{ pendingPageCount }}
              </span>
              <ElButton
                size="small"
                plain
                :disabled="pendingPageNo >= pendingPageCount"
                @click="handlePendingPageChange(pendingPageNo + 1)"
              >
                下一页
              </ElButton>
            </div>
          </div>
        </div>

        <div
          v-if="effectiveEncounterId"
          class="trace-assignment-card mt-2.5 overflow-hidden rounded-lg border border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-bg-color)]"
        >
          <div class="trace-assignment-card__head trace-assignment-card__head--split">
            <SectionTitle title="药批汇总" />
            <ElButton link type="primary" :loading="groupsLoading" @click="loadAssignmentGroups">
              刷新明细
            </ElButton>
          </div>
          <div class="trace-assignment-card__body">
            <ElTable
              v-if="groupDrafts.length > 0 || groupsLoading"
              v-loading="groupsLoading"
              :data="groupDrafts"
              class="order-table order-table--horizontal-only trace-group-table"
              stripe
            >
              <ElTableColumn
                prop="group.summaryNo"
                label="汇总单号"
                min-width="120"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="group.medicationName"
                label="药品"
                min-width="140"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="group.specification"
                label="规格"
                min-width="100"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="group.lotNumber"
                label="批号"
                width="110"
                show-overflow-tooltip
              />
              <ElTableColumn label="发药量" width="100" align="center">
                <template #default="{ row }">
                  {{ formatTraceAssignmentQuantity(row.group) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="状态" width="88" align="center">
                <template #default="{ row }">
                  <ElTag
                    :type="resolveAssignmentTagType(row.group.assignmentStatus)"
                    size="small"
                    effect="light"
                  >
                    {{ row.group.assignmentStatus_enumText ?? row.group.assignmentStatus ?? '—' }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="group.tracePoolPreview"
                label="码池摘要"
                min-width="120"
                show-overflow-tooltip
              />
              <ElTableColumn label="追溯码" min-width="220">
                <template #default="{ row }">
                  <ElInput
                    v-model="row.traceNo"
                    clearable
                    size="small"
                    placeholder="扫码或手工录入（逗号分隔）"
                  />
                </template>
              </ElTableColumn>
            </ElTable>
            <div
              v-else
              class="trace-assignment-card__empty py-8 text-center text-sm text-[var(--el-text-color-secondary)]"
            >
              暂无汇总核发后的待分配药品
            </div>
          </div>
        </div>

        <div
          v-else-if="props.patients.length > 1"
          class="trace-assignment-empty mt-2.5 flex min-h-[240px] items-center justify-center rounded-lg border border-dashed border-[var(--el-border-color)] bg-[var(--el-bg-color)]"
        >
          <ElEmpty description="追溯码分配仅支持单选患者，或在上方待办队列中选择" />
        </div>

        <div
          v-else
          class="trace-assignment-empty mt-2.5 flex min-h-[240px] items-center justify-center rounded-lg border border-dashed border-[var(--el-border-color)] bg-[var(--el-bg-color)]"
        >
          <ElEmpty description="请在左侧选择患者，或从待办队列点选待分配患者" />
        </div>
      </section>
    </div>

    <TraceAssignmentScanDialog
      v-if="effectiveEncounterId"
      v-model="scanDialogVisible"
      :encounter-id="effectiveEncounterId"
      :drafts="groupDrafts"
      :patient-name="effectivePatientName"
      @applied="handleScanApplied"
    />
  </div>
</template>

<style scoped lang="scss">
@use '../components/nurse-station-patient-order-list.scss';

.trace-assignment-module :deep(.inpatient-patient-info-header) {
  flex-shrink: 0;
  margin-bottom: 0;
}

.trace-assignment-toolbar__keyword {
  width: 200px;
}

.trace-assignment-card__head {
  padding: 10px 12px 0;
}

.trace-assignment-card__head--split {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.trace-assignment-card__body {
  padding: 8px 10px 10px;
}

.trace-assignment-card__pager {
  padding-top: 8px;
}

.trace-queue-table :deep(.trace-queue-row--active > td.el-table__cell) {
  background: var(--el-color-primary-light-9) !important;
}

.trace-group-table :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
}
</style>
