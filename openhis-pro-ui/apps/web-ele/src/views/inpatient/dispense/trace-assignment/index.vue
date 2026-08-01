<script setup lang="ts">
/**
 * 住院追溯码分配（发药管理）
 * 主类型：T1 工作站（左患者多选队列 + 右追溯码明细）
 * 含「已分配改码」：分配错误后经更正弹窗改码（接口预留）
 */
import type { PatientTraceAssignmentQueueRowDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-trace-assignment.types';

import { computed, onMounted, ref, watch } from 'vue';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import {
  httpListPatientTraceAssignmentGroups,
  httpPagePatientsAssignedTraceAssignment,
  httpPagePatientsPendingTraceAssignment,
  httpResolveTraceCodesForPatient,
  httpRevisePatientTraceAssignment,
  httpSavePatientTraceAssignment,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-trace-assignment.http';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { TRACE_BATCH_RESOLVE_MAX, dedupeTraceCodes, extractTraceCodes } from '#/utils/trace';

import TraceAssignmentDetailPanel from './components/trace-assignment-detail-panel.vue';
import TraceAssignmentQueuePanel from './components/trace-assignment-queue-panel.vue';
import type { TraceAssignmentWorkspaceMode } from './components/trace-assignment-queue-panel.vue';
import TraceAssignmentReviseDialog from './components/trace-assignment-revise-dialog.vue';
import TraceAssignmentScanDialog from './components/trace-assignment-scan-dialog.vue';
import {
  applyTraceMatchPreviews,
  buildGroupedTraceAssignmentRows,
  buildSavePatientTraceAssignmentRequest,
  collectGroupKeys,
  hasTraceAssignmentChanges,
  mapGroupsToDrafts,
  type TraceAssignmentDisplayRow,
  type TraceAssignmentGroupDraft,
  type TraceAssignmentGroupDraftWithPatient,
} from './services/map-inpatient-trace-assignment';

defineOptions({
  name: 'InpatientTraceAssignment',
});

const PENDING_PAGE_SIZE = 50;

const workspaceMode = ref<TraceAssignmentWorkspaceMode>('assign');
const pendingKeyword = ref('');
const pendingPageNo = ref(1);
const pendingRows = ref<PatientTraceAssignmentQueueRowDto[]>([]);
const pendingTotal = ref(0);
/** 左侧勾选的就诊 ID */
const selectedEncounterIds = ref<string[]>([]);
/** 右侧操作焦点就诊（扫码/上传/更正按此患者） */
const focusEncounterId = ref('');
/** 按就诊缓存草稿，切换勾选时保留已录入追溯码 */
const draftsByEncounterId = ref<Record<string, TraceAssignmentGroupDraft[]>>({});
const scanDialogVisible = ref(false);
const reviseDialogVisible = ref(false);
const reviseTargetDraft = ref<TraceAssignmentGroupDraft | null>(null);
const uploadInputRef = ref<HTMLInputElement | null>(null);

const pendingRequest = useRequest(httpPagePatientsPendingTraceAssignment, { manual: true });
const assignedRequest = useRequest(httpPagePatientsAssignedTraceAssignment, { manual: true });
const groupsRequest = useRequest(httpListPatientTraceAssignmentGroups, { manual: true });
const saveRequest = useRequest(httpSavePatientTraceAssignment, { manual: true });
const reviseRequest = useRequest(httpRevisePatientTraceAssignment, { manual: true });
const resolveRequest = useRequest(httpResolveTraceCodesForPatient, { manual: true });

/** 队列行按就诊 ID 索引 */
const pendingRowByEncounterId = computed(() => {
  const map = new Map<string, PatientTraceAssignmentQueueRowDto>();
  for (const row of pendingRows.value) {
    const id = preserveApiIdForRequest(row.encounterId);
    if (id) {
      map.set(id, row);
    }
  }
  return map;
});

/** 当前模式对应状态过滤 */
const statusFilter = computed(() =>
  workspaceMode.value === 'revise' ? ('ASSIGNED' as const) : ('PENDING' as const),
);

/** 右侧展示：按模式过滤后成组 */
const displayTableRows = computed(() => {
  const meta: Record<string, { patientName: string; bedName?: string }> = {};
  for (const encounterId of selectedEncounterIds.value) {
    const queueRow = pendingRowByEncounterId.value.get(encounterId);
    meta[encounterId] = {
      patientName: queueRow?.patientName ?? '—',
      bedName: queueRow?.bedName,
    };
  }
  return buildGroupedTraceAssignmentRows(
    selectedEncounterIds.value,
    draftsByEncounterId.value,
    meta,
    statusFilter.value,
  );
});

/** 已选患者药批组数（不含间隔行） */
const displayGroupCount = computed(
  () => displayTableRows.value.filter((row) => row.rowKind === 'body').length,
);

/** 焦点患者草稿（按模式过滤，供更正等单人操作） */
const focusDrafts = computed(() => {
  const id = focusEncounterId.value;
  if (!id) {
    return [] as TraceAssignmentGroupDraft[];
  }
  const all = draftsByEncounterId.value[id] ?? [];
  const filter = statusFilter.value;
  return all.filter((d) => String(d.group.assignmentStatus ?? '') === filter);
});

/** 批量扫码用：已选全部患者的待分配草稿 */
const selectedPendingDrafts = computed((): TraceAssignmentGroupDraftWithPatient[] => {
  const rows: TraceAssignmentGroupDraftWithPatient[] = [];
  for (const encounterId of selectedEncounterIds.value) {
    const drafts = (draftsByEncounterId.value[encounterId] ?? []).filter(
      (d) => String(d.group.assignmentStatus ?? '') === 'PENDING',
    );
    if (drafts.length === 0) {
      continue;
    }
    const queueRow = pendingRowByEncounterId.value.get(encounterId);
    const patientName = queueRow?.patientName ?? '—';
    const bedName = queueRow?.bedName;
    for (const draft of drafts) {
      rows.push({
        ...draft,
        encounterId,
        patientName,
        bedName,
      });
    }
  }
  return rows;
});

const focusPatientName = computed(() => {
  const row = pendingRowByEncounterId.value.get(focusEncounterId.value);
  return row?.patientName ?? '';
});

const queueLoading = computed(() => pendingRequest.loading.value || assignedRequest.loading.value);

const pageLoading = computed(
  () =>
    queueLoading.value ||
    groupsRequest.loading.value ||
    saveRequest.loading.value ||
    reviseRequest.loading.value ||
    resolveRequest.loading.value,
);

const groupCountText = computed(() => {
  if (selectedEncounterIds.value.length === 0) {
    return workspaceMode.value === 'revise' ? '请勾选已分配患者改码' : '请勾选左侧患者';
  }
  const suffix = workspaceMode.value === 'revise' ? '已分配组' : '组';
  return `已选 ${selectedEncounterIds.value.length} 人 · 共 ${displayGroupCount.value} ${suffix}`;
});

/** 队列刷新后同步勾选与焦点 */
function pruneSelectionByQueue() {
  const alive = new Set(
    pendingRows.value
      .map((row) => preserveApiIdForRequest(row.encounterId))
      .filter((id): id is string => Boolean(id)),
  );
  selectedEncounterIds.value = selectedEncounterIds.value.filter((id) => alive.has(id));
  if (focusEncounterId.value && !alive.has(focusEncounterId.value)) {
    focusEncounterId.value = selectedEncounterIds.value[0] ?? '';
  }
}

/** 加载待分配患者队列 */
async function loadPendingQueue() {
  const res = await pendingRequest.run({
    pageNo: pendingPageNo.value,
    pageSize: PENDING_PAGE_SIZE,
    keyword: pendingKeyword.value.trim() || undefined,
  });
  pendingRows.value = res.content ?? [];
  pendingTotal.value = res.totalCount ?? 0;
  pruneSelectionByQueue();
}

/** 加载已分配可改码患者队列 */
async function loadAssignedQueue() {
  const res = await assignedRequest.run({
    pageNo: pendingPageNo.value,
    pageSize: PENDING_PAGE_SIZE,
    keyword: pendingKeyword.value.trim() || undefined,
  });
  pendingRows.value = (res.content ?? []).map((row) => ({
    encounterId: row.encounterId,
    encounterBusNo: row.encounterBusNo,
    patientId: row.patientId,
    patientName: row.patientName,
    bedName: row.bedName,
    assignedGroupCount: row.assignedGroupCount,
  }));
  pendingTotal.value = res.totalCount ?? 0;
  pruneSelectionByQueue();
}

/** 按当前模式加载左侧队列 */
async function loadQueue() {
  if (workspaceMode.value === 'revise') {
    await loadAssignedQueue();
    return;
  }
  await loadPendingQueue();
}

/** 拉取单个就诊的药批汇总（已有缓存则跳过） */
async function ensureDraftsForEncounter(encounterId: string, force = false) {
  if (!force && draftsByEncounterId.value[encounterId]) {
    return;
  }
  const res = await groupsRequest.run({ encounterId });
  draftsByEncounterId.value = {
    ...draftsByEncounterId.value,
    [encounterId]: mapGroupsToDrafts(res.groups ?? []),
  };
}

/** 为当前勾选患者补齐草稿 */
async function loadDraftsForSelection(force = false) {
  const ids = selectedEncounterIds.value;
  if (ids.length === 0) {
    return;
  }
  await Promise.all(ids.map((id) => ensureDraftsForEncounter(id, force)));
}

/** 左侧勾选变化 */
function handleSelectionChange(encounterIds: string[]) {
  selectedEncounterIds.value = encounterIds;
  if (encounterIds.length === 0) {
    focusEncounterId.value = '';
    return;
  }
  if (!encounterIds.includes(focusEncounterId.value)) {
    focusEncounterId.value = encounterIds[encounterIds.length - 1] ?? '';
  }
  void loadDraftsForSelection(false);
}

/** 左侧单击设焦点患者 */
function handleFocusPatient(encounterId: string) {
  focusEncounterId.value = encounterId;
  if (!selectedEncounterIds.value.includes(encounterId)) {
    selectedEncounterIds.value = [...selectedEncounterIds.value, encounterId];
  }
  void ensureDraftsForEncounter(encounterId, false);
}

/** 查询患者队列 */
function handleQueueSearch() {
  pendingPageNo.value = 1;
  void loadQueue();
}

/** 刷新队列并强制重载已选明细 */
async function handleRefreshAll() {
  await loadQueue();
  await loadDraftsForSelection(true);
}

/** 右侧单元格追溯码变更写回缓存 */
function handleTraceNoUpdate(row: TraceAssignmentGroupDraftWithPatient, value: string) {
  const list = draftsByEncounterId.value[row.encounterId];
  if (!list) {
    return;
  }
  const key = String(row.group.groupKey ?? '').trim();
  draftsByEncounterId.value = {
    ...draftsByEncounterId.value,
    [row.encounterId]: list.map((draft) => {
      if (String(draft.group.groupKey ?? '').trim() !== key) {
        return draft;
      }
      return { ...draft, traceNo: value };
    }),
  };
}

/** 打开批量扫码（已选全部患者的待分配组） */
function handleOpenScanDialog() {
  if (workspaceMode.value === 'revise') {
    ElMessage.info('改码请点击行内「更正」');
    return;
  }
  if (selectedEncounterIds.value.length === 0) {
    ElMessage.warning('请先勾选患者');
    return;
  }
  if (selectedPendingDrafts.value.length === 0) {
    ElMessage.warning('已选患者无待分配药品');
    return;
  }
  scanDialogVisible.value = true;
}

/** 扫码结果写回全部已选患者草稿 */
function handleScanApplied(nextDrafts: TraceAssignmentGroupDraftWithPatient[]) {
  // 按就诊拆分写回，保留同就诊下其他状态组
  const byEncounter = new Map<string, TraceAssignmentGroupDraftWithPatient[]>();
  for (const draft of nextDrafts) {
    const list = byEncounter.get(draft.encounterId) ?? [];
    list.push(draft);
    byEncounter.set(draft.encounterId, list);
  }
  let nextMap = { ...draftsByEncounterId.value };
  for (const [encounterId, applied] of byEncounter) {
    const existing = nextMap[encounterId] ?? [];
    const nextByKey = new Map(
      applied.map((d) => [String(d.group.groupKey ?? '').trim(), d] as const),
    );
    nextMap = {
      ...nextMap,
      [encounterId]: existing.map((draft) => {
        const key = String(draft.group.groupKey ?? '').trim();
        const hit = nextByKey.get(key);
        if (!hit) {
          return draft;
        }
        return { group: hit.group, traceNo: hit.traceNo };
      }),
    };
  }
  draftsByEncounterId.value = nextMap;
}

/** 触发本地码文件选择 */
function handleUploadClick() {
  if (workspaceMode.value === 'revise') {
    ElMessage.info('改码请使用行内「更正」');
    return;
  }
  if (!focusEncounterId.value) {
    ElMessage.warning('请先勾选一位患者作为上传目标（可多选，上传按焦点患者解析）');
    return;
  }
  if (focusDrafts.value.length === 0) {
    ElMessage.warning('当前焦点患者无待分配药品');
    return;
  }
  uploadInputRef.value?.click();
}

/** 读取上传的码文件并解析匹配到焦点患者 */
async function handleUploadFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) {
    return;
  }
  const encounterId = focusEncounterId.value;
  if (!encounterId) {
    return;
  }
  let text = '';
  try {
    text = await file.text();
  } catch {
    ElMessage.error('读取文件失败');
    return;
  }
  const { ok, bad } = extractTraceCodes(text);
  if (bad.length > 0) {
    ElMessage.warning(`存在非法追溯码：${bad[0]}`);
    return;
  }
  const traceCodes = dedupeTraceCodes(ok);
  if (traceCodes.length === 0) {
    ElMessage.warning('文件中未识别到有效追溯码');
    return;
  }
  if (traceCodes.length > TRACE_BATCH_RESOLVE_MAX) {
    ElMessage.warning(`单次最多解析 ${TRACE_BATCH_RESOLVE_MAX} 个追溯码`);
    return;
  }
  const drafts = focusDrafts.value;
  const groupKeys = collectGroupKeys(drafts);
  if (groupKeys.length === 0) {
    ElMessage.warning('当前患者无待分配汇总组');
    return;
  }
  try {
    const res = await resolveRequest.run({
      encounterId,
      traceCodes,
      groupKeys,
    });
    const matches = res.matches ?? [];
    const fails = res.failItems ?? [];
    if (matches.length === 0 && fails.length > 0) {
      ElMessage.warning('追溯码均未能匹配，请核对码池与药品批号');
      return;
    }
    const queueRow = pendingRowByEncounterId.value.get(encounterId);
    const updated = applyTraceMatchPreviews(drafts, matches).map((draft) => ({
      ...draft,
      encounterId,
      patientName: queueRow?.patientName ?? '—',
      bedName: queueRow?.bedName,
    }));
    handleScanApplied(updated);
    if (fails.length > 0) {
      ElMessage.warning(`已匹配部分码，另有 ${fails.length} 个未能匹配`);
      return;
    }
    ElMessage.success(`已从文件导入并匹配 ${matches.length} 组`);
  } catch {
    ElMessage.error('追溯码解析失败，请稍后重试');
  }
}

/** 保存已选患者的追溯码分配（仅待分配模式） */
async function handleSaveAssignment() {
  if (workspaceMode.value !== 'assign') {
    return;
  }
  const ids = selectedEncounterIds.value;
  if (ids.length === 0) {
    ElMessage.warning('请先勾选患者');
    return;
  }
  const dirtyIds = ids.filter((id) => {
    const drafts = (draftsByEncounterId.value[id] ?? []).filter(
      (d) => String(d.group.assignmentStatus ?? '') === 'PENDING',
    );
    return hasTraceAssignmentChanges(drafts);
  });
  if (dirtyIds.length === 0) {
    ElMessage.warning('请录入或扫描追溯码后再保存');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认保存已选 ${dirtyIds.length} 位患者的追溯码分配？`,
      '保存追溯码',
      {
        type: 'warning',
        confirmButtonText: '确认保存',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return;
  }
  let savedTotal = 0;
  for (const encounterId of dirtyIds) {
    const drafts = (draftsByEncounterId.value[encounterId] ?? []).filter(
      (d) => String(d.group.assignmentStatus ?? '') === 'PENDING',
    );
    const body = buildSavePatientTraceAssignmentRequest(encounterId, drafts);
    if (body.groups.length === 0) {
      continue;
    }
    const res = await saveRequest.run(body);
    savedTotal += res.savedGroupCount ?? body.groups.length;
  }
  ElMessage.success(`追溯码分配已保存（${savedTotal} 组）`);
  for (const id of dirtyIds) {
    const { [id]: _removed, ...rest } = draftsByEncounterId.value;
    draftsByEncounterId.value = rest;
  }
  await Promise.all([loadQueue(), loadDraftsForSelection(true)]);
}

/** 行内打开更正弹窗 */
function handleOpenRevise(row: TraceAssignmentDisplayRow) {
  focusEncounterId.value = row.encounterId;
  reviseTargetDraft.value = {
    group: row.group,
    traceNo: row.traceNo,
  };
  reviseDialogVisible.value = true;
}

/** 提交单组更正 */
async function handleReviseConfirm(payload: {
  groupKey: string;
  previousTraceNo: string;
  traceNo: string;
}) {
  const encounterId = focusEncounterId.value;
  if (!encounterId) {
    return;
  }
  const drafts = draftsByEncounterId.value[encounterId] ?? [];
  const hit = drafts.find((d) => String(d.group.groupKey ?? '').trim() === payload.groupKey);
  if (!hit) {
    ElMessage.warning('未找到待更正汇总组');
    return;
  }
  const dispenseIds = (hit.group.dispenseIds ?? [])
    .map((id) => preserveApiIdForRequest(id))
    .filter((id): id is string => Boolean(id));
  if (dispenseIds.length === 0) {
    ElMessage.warning('发放行主键缺失，无法更正');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认将「${hit.group.medicationName ?? '药品'}」追溯码更正为新码？`,
      '确认更正',
      {
        type: 'warning',
        confirmButtonText: '确认更正',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return;
  }
  try {
    const res = await reviseRequest.run({
      encounterId,
      groups: [
        {
          groupKey: payload.groupKey,
          dispenseIds,
          previousTraceNo: payload.previousTraceNo,
          traceNo: payload.traceNo,
        },
      ],
    });
    // 本地同步写回，避免再拉一次前界面过期
    draftsByEncounterId.value = {
      ...draftsByEncounterId.value,
      [encounterId]: drafts.map((draft) => {
        if (String(draft.group.groupKey ?? '').trim() !== payload.groupKey) {
          return draft;
        }
        return {
          group: {
            ...draft.group,
            assignedTraceNo: payload.traceNo,
            assignmentStatus: 'ASSIGNED',
            assignmentStatus_enumText: '已分配',
          },
          traceNo: payload.traceNo,
        };
      }),
    };
    ElMessage.success(`追溯码已更正（${res.revisedGroupCount ?? 1} 组）`);
    await loadQueue();
  } catch {
    ElMessage.error('更正失败：接口尚未开通或请求异常，请稍后重试');
  }
}

watch(focusEncounterId, (id) => {
  if (id) {
    void ensureDraftsForEncounter(id, false);
  }
});

watch(workspaceMode, async () => {
  selectedEncounterIds.value = [];
  focusEncounterId.value = '';
  draftsByEncounterId.value = {};
  pendingPageNo.value = 1;
  await loadQueue();
});

onMounted(async () => {
  await loadQueue();
});
</script>

<template>
  <StationTriPaneLayout
    class="trace-assignment-page"
    :show-right="false"
    :show-footer="true"
    left-track="320px"
    left-track-lg="340px"
    center-track="minmax(0, 1fr)"
  >
    <template #left>
      <TraceAssignmentQueuePanel
        v-model:keyword="pendingKeyword"
        v-model:workspace-mode="workspaceMode"
        :patients="pendingRows"
        :selected-encounter-ids="selectedEncounterIds"
        :loading="queueLoading"
        @search="handleQueueSearch"
        @refresh="handleRefreshAll"
        @selection-change="handleSelectionChange"
        @focus="handleFocusPatient"
      />
    </template>

    <template #center>
      <div
        v-loading="pageLoading"
        class="trace-assignment-center flex h-full min-h-0 flex-col overflow-hidden"
      >
        <div
          class="trace-assignment-center__toolbar flex flex-wrap items-center justify-between gap-2 border-b border-solid border-[var(--el-border-color-lighter)] px-2 py-1.5"
        >
          <div class="flex min-w-0 items-center gap-2">
            <SectionTitle :title="workspaceMode === 'revise' ? '已分配改码' : '追溯码信息'" />
            <span class="text-xs text-[var(--el-text-color-secondary)]">
              {{ groupCountText }}
            </span>
            <span v-if="focusPatientName" class="text-xs text-[var(--el-text-color-regular)]">
              焦点：{{ focusPatientName }}
            </span>
          </div>
          <div class="flex flex-wrap items-center gap-1">
            <template v-if="workspaceMode === 'assign'">
              <ElButton
                plain
                :disabled="selectedPendingDrafts.length === 0"
                @click="handleUploadClick"
              >
                上传
              </ElButton>
              <ElButton
                type="primary"
                :disabled="selectedPendingDrafts.length === 0"
                @click="handleOpenScanDialog"
              >
                批量扫码
              </ElButton>
              <ElButton
                type="success"
                :disabled="selectedEncounterIds.length === 0"
                :loading="saveRequest.loading.value"
                @click="handleSaveAssignment"
              >
                保存分配
              </ElButton>
            </template>
            <template v-else>
              <span class="mr-1 text-xs text-[var(--el-text-color-secondary)]">
                点击行内「更正」修改错误追溯码
              </span>
            </template>
          </div>
        </div>

        <div
          class="trace-assignment-center__body min-h-0 flex-1 overflow-hidden px-2.5 pb-2.5 pt-2"
        >
          <TraceAssignmentDetailPanel
            :rows="displayTableRows"
            :loading="groupsRequest.loading.value"
            :focus-encounter-id="focusEncounterId"
            :empty-selected="selectedEncounterIds.length === 0"
            :workspace-mode="workspaceMode"
            @focus="handleFocusPatient"
            @update-trace-no="handleTraceNoUpdate"
            @revise="handleOpenRevise"
          />
        </div>
      </div>
    </template>

    <template #footer>
      <div
        class="flex w-full items-center justify-between gap-2 px-2 text-xs text-[var(--el-text-color-secondary)]"
      >
        <span>队列共 {{ pendingTotal }} 人</span>
        <span v-if="workspaceMode === 'assign'">扫码作用于已选全部患者</span>
        <span v-else-if="focusPatientName">更正作用于焦点患者：{{ focusPatientName }}</span>
      </div>
    </template>

    <template #drawer>
      <input
        ref="uploadInputRef"
        type="file"
        accept=".txt,.csv,text/plain"
        class="hidden"
        @change="handleUploadFileChange"
      />
      <TraceAssignmentScanDialog
        v-if="workspaceMode === 'assign'"
        v-model="scanDialogVisible"
        :drafts="selectedPendingDrafts"
        :selected-patient-count="selectedEncounterIds.length"
        @applied="handleScanApplied"
      />
      <TraceAssignmentReviseDialog
        v-model="reviseDialogVisible"
        :encounter-id="focusEncounterId"
        :patient-name="focusPatientName"
        :draft="reviseTargetDraft"
        @confirm="handleReviseConfirm"
      />
    </template>
  </StationTriPaneLayout>
</template>

<style scoped>
.trace-assignment-center__toolbar :deep(.section-title) {
  margin-bottom: 0;
}
</style>
