<script setup lang="ts">
import type {
  ObservationRowDto,
  PendingDeskRowDto,
  SaveObservationRequest,
} from '#/api/peis/dept-desk';
import type { PeisCommonResultDto } from '#/api/peis/diagnosis-config';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { RotateCw } from '@vben/icons';

import { useDebounceFn } from '@vueuse/core';
import { searchCommonResultApi } from '#/api/peis/diagnosis-config';
import { searchObservationDefApi } from '#/api/peis/catalog-config';
import {
  httpBatchSaveObservations,
  httpLoadObservationsByServiceRequest,
  httpSaveProjectConclusion,
  httpSaveObservation,
  httpSearchPendingDeptDesk,
} from '#/api/peis/dept-desk';
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  getDateRangeQuick,
  getLocalDayRangeDateTimeStrings,
} from '#/utils/format/date-range-quick';
import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElDivider,
  ElDropdown,
  ElDropdownItem,
  ElEmpty,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import '../../outpatient/charge/charge-workbench-panel.css';
import './dept-desk-panel.css';

defineOptions({ name: 'PeisDeptExamDesk' });

const CATEGORY_PROJECT_CONCLUSION = 'dept_conclusion';

interface PatientQueueGroup {
  encounterId: string;
  patientId: string;
  patientName: string;
  busNo: string;
  patientAge?: null | number;
  groupFlag?: boolean | null;
  encounterStatusEnum?: null | string;
  pendingCount: number;
  projects: PendingDeskRowDto[];
}

const encounterStatusLabel: Record<string, string> = {
  IN_PROGRESS: '在检',
  PLANNED: '已登记',
  REGISTERED: '已到检',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  ON_HOLD: '暂停',
};

interface ProjectObservationBundle {
  project: PendingDeskRowDto;
  observations: ObservationRowDto[];
}

interface ObservationDraft {
  valueString: string;
  interpretationCode: string;
}

const keyword = ref('');
const dateRange = ref<[string, string]>(
  getDateRangeQuick({ strategy: 'calendar-day' }) as [string, string],
);
const filtersReady = ref(false);
const queuePager = reactive({ pageNo: 1, pageSize: 100 });
const queueRecords = ref<PendingDeskRowDto[]>([]);
const queueTotal = ref(0);
const queueLoading = ref(false);
const patientTableRef = ref<InstanceType<typeof ElTable> | null>(null);
const projectTableRef = ref<InstanceType<typeof ElTable> | null>(null);
const observationScrollRef = ref<HTMLElement | null>(null);

const selectedPatientKey = ref('');
const selectedProjectId = ref('');
/** 选中患者后固定项目列表，避免保存确认后待检队列刷新导致小项区被卸载。 */
const activeEncounterProjects = ref<PendingDeskRowDto[]>([]);

const projectObservationBundles = ref<ProjectObservationBundle[]>([]);
const observationLoading = ref(false);
const selectedObservationKey = ref('');

const draftMap = reactive<Record<string, ObservationDraft>>({});
const commonResultsCache = ref<Record<string, PeisCommonResultDto[]>>({});

const editingServiceRequestId = ref('');
const editDefinitionId = ref('');
const saving = ref(false);

const conclusionDefinitionId = ref('');
const conclusionText = ref('');
const savingConclusion = ref(false);
let conclusionDefinitionResolveToken = 0;

const execStatusLabel: Record<string, string> = {
  pending: '待检',
  checked: '已检',
  abandoned: '弃检',
  deferred: '延检',
};

const execStatusTagType: Record<string, '' | 'danger' | 'info' | 'success' | 'warning'> = {
  pending: 'warning',
  checked: 'success',
  abandoned: 'info',
  deferred: 'danger',
};

const observationStatusLabel: Record<string, string> = {
  preliminary: '已保存',
  final: '已确认',
  amended: '已修订',
  cancelled: '已作废',
};

const observationStatusTagType: Record<string, '' | 'danger' | 'info' | 'success' | 'warning'> = {
  preliminary: 'warning',
  final: 'success',
  amended: 'warning',
  cancelled: 'info',
};

const interpretationOptions = [
  { label: '正常 N', value: 'N' },
  { label: '偏高 H', value: 'H' },
  { label: '偏低 L', value: 'L' },
  { label: '异常 A', value: 'A' },
];

function filterRegularObservations(observations: ObservationRowDto[]) {
  return observations.filter((row) => row.categoryCode !== CATEGORY_PROJECT_CONCLUSION);
}

function filterConclusionObservations(observations: ObservationRowDto[]) {
  return observations.filter((row) => row.categoryCode === CATEGORY_PROJECT_CONCLUSION);
}

function projectBlockId(serviceRequestId: string) {
  return `dept-desk-project-${serviceRequestId}`;
}

function observationRowKey(serviceRequestId: string, definitionId: string) {
  return `${serviceRequestId}:${definitionId}`;
}

function initDraftMap(bundles: ProjectObservationBundle[]) {
  for (const key of Object.keys(draftMap)) {
    delete draftMap[key];
  }
  for (const bundle of bundles) {
    const serviceRequestId = String(bundle.project.serviceRequestId ?? '');
    if (!serviceRequestId) continue;
    for (const row of filterRegularObservations(bundle.observations)) {
      const definitionId = String(row.definitionId);
      draftMap[observationRowKey(serviceRequestId, definitionId)] = {
        valueString: row.valueString ?? '',
        interpretationCode: row.interpretationCode ?? '',
      };
    }
  }
}

function getDraft(serviceRequestId: string, definitionId: string): ObservationDraft {
  const key = observationRowKey(serviceRequestId, definitionId);
  if (!draftMap[key]) {
    draftMap[key] = { valueString: '', interpretationCode: '' };
  }
  return draftMap[key];
}

const patientGroups = computed<PatientQueueGroup[]>(() => {
  const map = new Map<string, PatientQueueGroup>();
  for (const row of queueRecords.value) {
    const encounterId = String(row.encounterId ?? '');
    if (!encounterId) continue;
    let group = map.get(encounterId);
    if (!group) {
      group = {
        encounterId,
        patientId: String(row.patientId ?? ''),
        patientName: row.patientName?.trim() || '—',
        busNo: row.busNo?.trim() || '—',
        patientAge: row.patientAge ?? undefined,
        groupFlag: row.groupFlag ?? undefined,
        encounterStatusEnum: row.encounterStatusEnum ?? undefined,
        pendingCount: 0,
        projects: [],
      };
      map.set(encounterId, group);
    }
    group.projects.push(row);
    if (row.execStatusEnum === 'pending') {
      group.pendingCount += 1;
    }
  }
  return Array.from(map.values()).sort((a, b) => {
    const nameCmp = a.patientName.localeCompare(b.patientName, 'zh-CN');
    if (nameCmp !== 0) return nameCmp;
    return a.busNo.localeCompare(b.busNo, 'zh-CN');
  });
});

function formatPatientExamStatus(row: PatientQueueGroup) {
  const statusText =
    encounterStatusLabel[row.encounterStatusEnum ?? ''] ?? row.encounterStatusEnum ?? '在检';
  if (row.pendingCount > 0) {
    return `${statusText}·${row.pendingCount}项待检`;
  }
  return statusText;
}

const selectedPatient = computed(() =>
  patientGroups.value.find((item) => item.encounterId === selectedPatientKey.value),
);

const patientProjects = computed(() => {
  if (!selectedPatientKey.value) return [];
  if (activeEncounterProjects.value.length > 0) {
    return activeEncounterProjects.value;
  }
  return selectedPatient.value?.projects ?? [];
});

const selectedRow = computed(
  () =>
    patientProjects.value.find((row) => row.serviceRequestId === selectedProjectId.value) ?? null,
);

const selectedBundle = computed(
  () =>
    projectObservationBundles.value.find(
      (bundle) => String(bundle.project.serviceRequestId) === selectedProjectId.value,
    ) ?? null,
);

const conclusionObservations = computed(() =>
  filterConclusionObservations(selectedBundle.value?.observations ?? []),
);

const totalRegularObservationCount = computed(() =>
  projectObservationBundles.value.reduce(
    (sum, bundle) => sum + filterRegularObservations(bundle.observations).length,
    0,
  ),
);

const dirtyObservationCount = computed(() => {
  let count = 0;
  for (const bundle of projectObservationBundles.value) {
    const serviceRequestId = String(bundle.project.serviceRequestId ?? '');
    if (!serviceRequestId) continue;
    for (const row of filterRegularObservations(bundle.observations)) {
      if (isObservationDraftDirty(row, serviceRequestId)) {
        count += 1;
      }
    }
  }
  return count;
});

function setActiveEncounterProjects(projects: PendingDeskRowDto[]) {
  activeEncounterProjects.value = projects.map((row) => ({ ...row }));
}

function syncActiveEncounterProjectsFromQueue(encounterId: string) {
  const group = patientGroups.value.find((item) => item.encounterId === encounterId);
  if (!group) return;
  const queueBySrId = new Map(group.projects.map((row) => [String(row.serviceRequestId), row]));
  const merged: PendingDeskRowDto[] = [];
  const seen = new Set<string>();
  for (const row of activeEncounterProjects.value) {
    const srId = String(row.serviceRequestId ?? '');
    if (!srId) continue;
    seen.add(srId);
    const fresh = queueBySrId.get(srId);
    merged.push(fresh ?? row);
  }
  for (const row of group.projects) {
    const srId = String(row.serviceRequestId ?? '');
    if (!srId || seen.has(srId)) continue;
    merged.push(row);
    seen.add(srId);
  }
  activeEncounterProjects.value = merged;
}

const debouncedLoadQueue = useDebounceFn(() => {
  queuePager.pageNo = 1;
  void loadQueue();
}, 300);

async function loadQueue(options?: { silent?: boolean }) {
  const silent = options?.silent ?? false;
  if (!silent) {
    queueLoading.value = true;
  }
  try {
    const range = dateRange.value;
    let startDate: string | undefined;
    let endDate: string | undefined;
    if (range?.[0] && range?.[1]) {
      startDate = getLocalDayRangeDateTimeStrings(range[0]).startDate;
      endDate = getLocalDayRangeDateTimeStrings(range[1]).endDate;
    }
    const res = await httpSearchPendingDeptDesk({
      pageNo: queuePager.pageNo,
      pageSize: queuePager.pageSize,
      keyword: keyword.value.trim() || undefined,
      startDate,
      endDate,
    });
    const parsed = parsePagedRowsFromApiData(res);
    queueRecords.value = parsed.content as PendingDeskRowDto[];
    queueTotal.value = parsed.totalCount;

    if (selectedPatientKey.value) {
      const stillThere = patientGroups.value.some(
        (item) => item.encounterId === selectedPatientKey.value,
      );
      if (!stillThere) {
        resetWorkspace();
      } else {
        syncActiveEncounterProjectsFromQueue(selectedPatientKey.value);
        if (!silent) {
          await loadAllObservationsForProjects(activeEncounterProjects.value);
          if (selectedProjectId.value) {
            const stillProject = patientProjects.value.some(
              (row) => row.serviceRequestId === selectedProjectId.value,
            );
            if (!stillProject) {
              selectDefaultProject();
            } else {
              syncConclusionFromProjectId(selectedProjectId.value);
              nextTick(() => {
                const row = patientProjects.value.find(
                  (item) => item.serviceRequestId === selectedProjectId.value,
                );
                if (row) {
                  projectTableRef.value?.setCurrentRow(row);
                }
              });
            }
          }
        }
      }
    }
  } finally {
    if (!silent) {
      queueLoading.value = false;
    }
  }
}

function resetWorkspace() {
  selectedPatientKey.value = '';
  selectedProjectId.value = '';
  activeEncounterProjects.value = [];
  projectObservationBundles.value = [];
  selectedObservationKey.value = '';
  editingServiceRequestId.value = '';
  editDefinitionId.value = '';
  commonResultsCache.value = {};
  for (const key of Object.keys(draftMap)) {
    delete draftMap[key];
  }
  conclusionDefinitionId.value = '';
  conclusionText.value = '';
  patientTableRef.value?.setCurrentRow(undefined);
  projectTableRef.value?.setCurrentRow(undefined);
}

function syncConclusionFromBundle(bundle: ProjectObservationBundle | null) {
  if (!bundle) {
    conclusionDefinitionId.value = '';
    conclusionText.value = '';
    return;
  }
  const conclusions = filterConclusionObservations(bundle.observations);
  if (conclusions.length > 0) {
    const first = conclusions[0];
    conclusionDefinitionId.value = String(first.definitionId);
    conclusionText.value = first.valueString ?? '';
    return;
  }
  const template = bundle.observations.find(
    (row) => row.categoryCode === CATEGORY_PROJECT_CONCLUSION,
  );
  conclusionDefinitionId.value = template?.definitionId ? String(template.definitionId) : '';
  conclusionText.value = '';
}

async function resolveConclusionDefinitionId(bundle: ProjectObservationBundle | null) {
  syncConclusionFromBundle(bundle);
  if (conclusionDefinitionId.value || !bundle) {
    return;
  }
  const token = ++conclusionDefinitionResolveToken;
  try {
    const res = await searchObservationDefApi({
      categoryCode: CATEGORY_PROJECT_CONCLUSION,
      pageNo: 1,
      pageSize: 20,
    });
    if (token !== conclusionDefinitionResolveToken) {
      return;
    }
    const record = res.content?.[0];
    const definitionId = record?.definitionId;
    if (definitionId) {
      conclusionDefinitionId.value = String(definitionId);
    }
  } catch {
    // 后端会自动补项目结论模板；仍失败时保持禁用并提示刷新
  }
}

async function syncConclusionFromProjectId(serviceRequestId: string) {
  const bundle = projectObservationBundles.value.find(
    (item) => String(item.project.serviceRequestId) === serviceRequestId,
  );
  await resolveConclusionDefinitionId(bundle ?? null);
}

function scrollToProjectBlock(serviceRequestId: string) {
  const container = observationScrollRef.value;
  const block = document.getElementById(projectBlockId(serviceRequestId));
  if (!container || !block) return;
  const top = block.offsetTop - container.offsetTop;
  container.scrollTo({ top: Math.max(top - 4, 0), behavior: 'smooth' });
}

function selectDefaultProject() {
  const projects = patientProjects.value;
  const pending = projects.filter((row) => row.execStatusEnum === 'pending');
  const target = pending[0] ?? projects[0];
  if (target?.serviceRequestId) {
    handleSelectProject(target, { scroll: true });
  } else {
    selectedProjectId.value = '';
    projectTableRef.value?.setCurrentRow(undefined);
    syncConclusionFromBundle(null);
  }
}

async function handleSelectProject(row: PendingDeskRowDto, options?: { scroll?: boolean }) {
  if (!row.serviceRequestId) return;
  selectedProjectId.value = String(row.serviceRequestId);
  await syncConclusionFromProjectId(selectedProjectId.value);
  nextTick(() => {
    projectTableRef.value?.setCurrentRow(row);
    if (options?.scroll) {
      scrollToProjectBlock(selectedProjectId.value);
    }
  });
}

async function handleSelectPatient(patient: PatientQueueGroup) {
  selectedPatientKey.value = patient.encounterId;
  setActiveEncounterProjects(patient.projects);
  await loadAllObservationsForProjects(activeEncounterProjects.value);
  selectDefaultProject();
}

async function loadAllObservationsForProjects(
  projects: PendingDeskRowDto[],
  options?: { preserveFocus?: boolean; silent?: boolean },
) {
  const silent = options?.silent ?? false;
  const preserveFocus = options?.preserveFocus ?? false;
  if (!silent) {
    observationLoading.value = true;
  }
  if (!preserveFocus) {
    selectedObservationKey.value = '';
    editingServiceRequestId.value = '';
    editDefinitionId.value = '';
    commonResultsCache.value = {};
    conclusionDefinitionId.value = '';
    conclusionText.value = '';
  }
  try {
    const bundles = await Promise.all(
      projects.map(async (project) => {
        const serviceRequestId = String(project.serviceRequestId ?? '');
        if (!serviceRequestId) {
          return { project, observations: [] as ObservationRowDto[] };
        }
        const res = await httpLoadObservationsByServiceRequest({
          serviceRequestId,
          activityId: project.activityId ?? undefined,
        });
        return {
          project,
          observations: res.observations ?? [],
        };
      }),
    );
    projectObservationBundles.value = bundles;
    initDraftMap(bundles);
    if (selectedProjectId.value) {
      await syncConclusionFromProjectId(selectedProjectId.value);
    }
  } finally {
    if (!silent) {
      observationLoading.value = false;
    }
  }
}

async function ensureCommonResults(definitionId: string) {
  if (!definitionId || commonResultsCache.value[definitionId]) return;
  const res = await searchCommonResultApi({
    observationDefinitionId: definitionId,
    pageNo: 1,
    pageSize: 100,
  });
  commonResultsCache.value[definitionId] = res.records ?? [];
}

function focusObservationRow(bundle: ProjectObservationBundle, row: ObservationRowDto) {
  if (row.categoryCode === CATEGORY_PROJECT_CONCLUSION) {
    editingServiceRequestId.value = String(bundle.project.serviceRequestId);
    conclusionDefinitionId.value = String(row.definitionId);
    conclusionText.value = row.valueString ?? '';
    return;
  }
  const serviceRequestId = String(bundle.project.serviceRequestId);
  const definitionId = String(row.definitionId);
  editingServiceRequestId.value = serviceRequestId;
  editDefinitionId.value = definitionId;
  selectedObservationKey.value = String(row.id ?? definitionId);
  void ensureCommonResults(definitionId);
}

function applyCommonResultInline(
  bundle: ProjectObservationBundle,
  row: ObservationRowDto,
  commonResult: PeisCommonResultDto,
) {
  const serviceRequestId = String(bundle.project.serviceRequestId);
  const definitionId = String(row.definitionId);
  const draft = getDraft(serviceRequestId, definitionId);
  draft.valueString = commonResult.valueString ?? '';
  draft.interpretationCode = commonResult.interpretationCode ?? '';
  focusObservationRow(bundle, row);
}

function collectDirtySavePayloads(finalizeFlag: boolean): SaveObservationRequest[] {
  const payloads: SaveObservationRequest[] = [];
  for (const bundle of projectObservationBundles.value) {
    const serviceRequestId = String(bundle.project.serviceRequestId ?? '');
    if (!serviceRequestId) continue;
    for (const row of filterRegularObservations(bundle.observations)) {
      const definitionId = String(row.definitionId);
      if (!isObservationDraftDirty(row, serviceRequestId)) continue;
      const draft = getDraft(serviceRequestId, definitionId);
      payloads.push({
        serviceRequestId,
        definitionId,
        valueString: draft.valueString,
        interpretationCode: draft.interpretationCode || undefined,
        finalizeFlag,
      });
    }
  }
  return payloads;
}

async function reloadCurrentPatientObservations(options?: {
  preserveFocus?: boolean;
  silent?: boolean;
}) {
  if (!selectedPatientKey.value || activeEncounterProjects.value.length === 0) return;
  syncActiveEncounterProjectsFromQueue(selectedPatientKey.value);
  await loadAllObservationsForProjects(activeEncounterProjects.value, {
    silent: options?.silent ?? true,
    preserveFocus: options?.preserveFocus ?? true,
  });
  if (selectedProjectId.value) {
    nextTick(() => {
      const row = patientProjects.value.find(
        (item) => item.serviceRequestId === selectedProjectId.value,
      );
      if (row) {
        projectTableRef.value?.setCurrentRow(row);
      }
    });
  }
}

async function saveObservationsSequentially(payloads: SaveObservationRequest[]) {
  let savedCount = 0;
  for (const payload of payloads) {
    await httpSaveObservation(payload);
    savedCount += 1;
  }
  return savedCount;
}

async function handleBatchSave(finalizeFlag: boolean) {
  const payloads = collectDirtySavePayloads(finalizeFlag);
  if (payloads.length === 0) {
    ElMessage.warning('没有待保存的修改');
    return;
  }
  saving.value = true;
  try {
    let count = payloads.length;
    try {
      const res = await httpBatchSaveObservations({ items: payloads });
      count = res.successCount ?? payloads.length;
    } catch {
      count = await saveObservationsSequentially(payloads);
    }
    ElMessage.success(finalizeFlag ? `已批量确认 ${count} 条小项` : `已批量保存 ${count} 条小项`);
    await reloadCurrentPatientObservations({ silent: true, preserveFocus: true });
    await loadQueue({ silent: true });
  } finally {
    saving.value = false;
  }
}

async function handleSaveConclusion() {
  if (!selectedRow.value || !conclusionDefinitionId.value || !conclusionText.value.trim()) {
    ElMessage.warning('请填写项目结论');
    return;
  }
  savingConclusion.value = true;
  try {
    await httpSaveProjectConclusion({
      serviceRequestId: selectedRow.value.serviceRequestId,
      definitionId: conclusionDefinitionId.value,
      valueString: conclusionText.value.trim(),
    });
    ElMessage.success('项目结论已保存');
    await reloadCurrentPatientObservations({ silent: true, preserveFocus: true });
  } finally {
    savingConclusion.value = false;
  }
}

function isObservationDraftDirty(row: ObservationRowDto, serviceRequestId: string): boolean {
  const definitionId = String(row.definitionId);
  const draft = getDraft(serviceRequestId, definitionId);
  const savedValue = row.valueString ?? '';
  const savedInterp = row.interpretationCode ?? '';
  return draft.valueString !== savedValue || draft.interpretationCode !== savedInterp;
}

function resolveObservationStatus(
  row: ObservationRowDto,
  serviceRequestId: string,
): { text: string; type: '' | 'danger' | 'info' | 'success' | 'warning' } {
  const status = row.statusEnum ?? '';
  const draft = getDraft(serviceRequestId, String(row.definitionId));
  const hasLocalInput = draft.valueString.trim().length > 0 || draft.interpretationCode.length > 0;
  const dirty = isObservationDraftDirty(row, serviceRequestId);

  if (!status) {
    if (hasLocalInput) {
      return { text: '待保存', type: 'warning' };
    }
    return { text: '未录入', type: 'info' };
  }

  if (dirty) {
    return { text: '待保存', type: 'warning' };
  }

  return {
    text: observationStatusLabel[status] ?? status,
    type: observationStatusTagType[status] ?? 'info',
  };
}

watch([keyword, dateRange], () => {
  if (!filtersReady.value) return;
  debouncedLoadQueue();
});

onMounted(async () => {
  await loadQueue();
  filtersReady.value = true;
});
</script>

<template>
  <div class="dept-desk-page flex h-full min-h-0 flex-col overflow-hidden">
    <StationTriPaneLayout
      class="!h-full min-h-0 flex-1"
      left-track="minmax(320px, 0.34fr)"
      left-track-lg="minmax(340px, 0.32fr)"
      center-track="minmax(0, 1fr)"
      right-track="minmax(260px, 0.28fr)"
      right-track-lg="minmax(280px, 0.26fr)"
    >
      <template #left>
        <div
          class="flex h-full min-h-0 flex-col gap-2 overflow-hidden p-2"
          v-loading="queueLoading"
        >
          <div class="shrink-0 px-0.5 pt-0.5">
            <SectionTitle title="待检患者" />
            <div class="dept-desk-filter-toolbar mt-2">
              <SelectFuzzyInput
                v-model="keyword"
                placeholder="姓名 / 流水号"
                full-width
                @search="loadQueue"
              />
              <div class="flex min-w-0 items-center gap-1.5">
                <ElDatePicker
                  v-model="dateRange"
                  type="daterange"
                  value-format="YYYY-MM-DD"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  unlink-panels
                  clearable
                  class="dept-desk-filter-toolbar__date min-w-0 flex-1"
                />
                <ElButton
                  type="default"
                  class="!shrink-0 !px-2"
                  title="刷新队列"
                  :loading="queueLoading"
                  @click="loadQueue"
                >
                  <RotateCw class="h-4 w-4" />
                </ElButton>
              </div>
            </div>
          </div>

          <div class="dept-desk-queue-scroll min-h-0 flex-1 overflow-hidden px-0.5">
            <ElTable
              ref="patientTableRef"
              :data="patientGroups"
              highlight-current-row
              height="100%"
              size="default"
              class="dept-desk-side-table dept-desk-patient-table dept-desk-table-header-nowrap"
              row-key="encounterId"
              @row-click="handleSelectPatient"
            >
              <ElTableColumn prop="patientName" label="姓名" min-width="96" show-overflow-tooltip />
              <ElTableColumn label="年龄" width="56" align="center">
                <template #default="{ row }">
                  {{ row.patientAge ?? '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="类型" width="64" align="center">
                <template #default="{ row }">
                  <ElTag size="small" :type="row.groupFlag ? 'success' : 'info'" effect="plain">
                    {{ row.groupFlag ? '团检' : '个检' }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn label="检查状态" min-width="132">
                <template #default="{ row }">
                  <span class="dept-desk-patient-status">{{ formatPatientExamStatus(row) }}</span>
                </template>
              </ElTableColumn>
              <template #empty>
                <div class="py-6 text-sm text-[var(--el-text-color-placeholder)]">
                  暂无待检患者
                  <div class="mt-2 text-xs leading-relaxed">
                    请确认日期含登记日，个检须已完成收费结算
                  </div>
                </div>
              </template>
            </ElTable>
          </div>

          <div class="shrink-0 pt-1">
            <ElPagination
              v-model:current-page="queuePager.pageNo"
              v-model:page-size="queuePager.pageSize"
              :disabled="queueLoading"
              :page-sizes="[50, 100, 200]"
              :total="queueTotal"
              background
              layout="total, sizes, prev, pager, next"
              small
              @current-change="loadQueue"
              @size-change="loadQueue"
            />
          </div>
        </div>
      </template>

      <template #center>
        <div
          class="flex h-full min-h-0 flex-col gap-2 overflow-hidden p-2"
          v-loading="observationLoading"
          element-loading-background="transparent"
        >
          <SectionTitle class="shrink-0" title="检查小项" />

          <template v-if="!selectedPatient">
            <div class="flex min-h-0 flex-1 items-center justify-center">
              <ElEmpty description="请从左侧选择待检患者" />
            </div>
          </template>

          <template v-else>
            <div class="patient-info-bar dept-desk-patient-bar shrink-0">
              <div class="patient-info-grid">
                <div class="patient-info-item">
                  <span class="patient-info-label">姓名</span>
                  <span class="patient-info-value dept-desk-patient-bar__emphasis">
                    {{ selectedPatient.patientName }}
                  </span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">流水号</span>
                  <span class="patient-info-value">{{ selectedPatient.busNo }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">待检项目</span>
                  <span class="patient-info-value">{{ selectedPatient.pendingCount }} 项</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">小项合计</span>
                  <span class="patient-info-value dept-desk-patient-bar__emphasis">
                    {{ totalRegularObservationCount }} 项
                  </span>
                </div>
              </div>
            </div>

            <div class="dept-desk-center-split min-h-0 flex-1 overflow-hidden">
              <div class="dept-desk-center-projects flex h-full min-h-0 flex-col overflow-hidden">
                <div class="dept-desk-center-projects__title">检查项目</div>
                <p class="dept-desk-center-projects__hint">点击定位小项</p>
                <div class="dept-desk-center-projects__scroll min-h-0 flex-1 overflow-hidden">
                  <ElTable
                    ref="projectTableRef"
                    :data="patientProjects"
                    highlight-current-row
                    height="100%"
                    size="default"
                    class="dept-desk-side-table dept-desk-project-table dept-desk-table-header-nowrap"
                    row-key="serviceRequestId"
                    @row-click="(row) => handleSelectProject(row, { scroll: true })"
                  >
                    <ElTableColumn
                      prop="activityDisplay"
                      label="项目"
                      min-width="160"
                      show-overflow-tooltip
                    />
                    <ElTableColumn label="状态" width="72" align="center">
                      <template #default="{ row }">
                        <ElTag
                          v-if="row.execStatusEnum"
                          size="default"
                          :type="execStatusTagType[row.execStatusEnum] ?? 'info'"
                          effect="plain"
                        >
                          {{ execStatusLabel[row.execStatusEnum] ?? row.execStatusEnum }}
                        </ElTag>
                        <span v-else>—</span>
                      </template>
                    </ElTableColumn>
                    <template #empty>
                      <div class="py-4 text-sm text-[var(--el-text-color-placeholder)]">
                        暂无检查项目
                      </div>
                    </template>
                  </ElTable>
                </div>
              </div>

              <div class="dept-desk-center-observations min-h-0 flex-1 overflow-hidden">
                <div
                  ref="observationScrollRef"
                  class="dept-desk-observation-scroll min-h-0 flex-1 overflow-y-auto"
                >
                  <template v-if="projectObservationBundles.length > 0">
                    <div
                      v-for="bundle in projectObservationBundles"
                      :key="bundle.project.serviceRequestId"
                      :id="projectBlockId(String(bundle.project.serviceRequestId))"
                      class="dept-desk-obs-block"
                      :class="{
                        'dept-desk-obs-block--active':
                          selectedProjectId === String(bundle.project.serviceRequestId),
                      }"
                    >
                      <div class="dept-desk-obs-block__head">
                        <span class="dept-desk-obs-block__name">
                          {{ bundle.project.activityDisplay || '项目' }}
                        </span>
                        <ElTag
                          v-if="bundle.project.execStatusEnum"
                          size="default"
                          :type="execStatusTagType[bundle.project.execStatusEnum] ?? 'info'"
                          effect="plain"
                        >
                          {{
                            execStatusLabel[bundle.project.execStatusEnum] ??
                            bundle.project.execStatusEnum
                          }}
                        </ElTag>
                        <span class="dept-desk-obs-block__count">
                          {{ filterRegularObservations(bundle.observations).length }} 项
                        </span>
                      </div>

                      <ElTable
                        v-if="filterRegularObservations(bundle.observations).length > 0"
                        :data="filterRegularObservations(bundle.observations)"
                        size="default"
                        class="dept-desk-obs-table dept-desk-table-header-nowrap"
                        :show-header="true"
                        :row-class-name="
                          ({ row }) =>
                            String(row.id ?? row.definitionId) === selectedObservationKey
                              ? 'is-selected-row'
                              : ''
                        "
                        @row-click="(row) => focusObservationRow(bundle, row)"
                      >
                        <ElTableColumn
                          prop="definitionDisplay"
                          label="小项"
                          min-width="120"
                          show-overflow-tooltip
                        >
                          <template #default="{ row }">
                            <span class="dept-desk-obs-name">{{
                              row.definitionDisplay || '—'
                            }}</span>
                          </template>
                        </ElTableColumn>
                        <ElTableColumn label="结果" min-width="180">
                          <template #default="{ row }">
                            <div class="dept-desk-result-cell">
                              <ElInput
                                v-model="
                                  getDraft(
                                    String(bundle.project.serviceRequestId),
                                    String(row.definitionId),
                                  ).valueString
                                "
                                size="default"
                                placeholder="录入结果"
                                class="dept-desk-inline-input"
                                @focus="focusObservationRow(bundle, row)"
                                @click.stop
                              />
                              <ElDropdown
                                v-if="commonResultsCache[String(row.definitionId)]?.length"
                                trigger="click"
                                @click.stop
                                @visible-change="
                                  (visible) =>
                                    visible && ensureCommonResults(String(row.definitionId))
                                "
                              >
                                <ElButton
                                  link
                                  type="primary"
                                  class="dept-desk-common-btn"
                                  @click.stop
                                >
                                  常用
                                </ElButton>
                                <template #dropdown>
                                  <ElDropdownItem
                                    v-for="item in commonResultsCache[String(row.definitionId)]"
                                    :key="item.id"
                                    @click="applyCommonResultInline(bundle, row, item)"
                                  >
                                    {{ item.display }}
                                  </ElDropdownItem>
                                </template>
                              </ElDropdown>
                            </div>
                          </template>
                        </ElTableColumn>
                        <ElTableColumn
                          label="解读"
                          width="120"
                          align="center"
                          class-name="dept-desk-interpret-col"
                        >
                          <template #default="{ row }">
                            <ElSelect
                              v-model="
                                getDraft(
                                  String(bundle.project.serviceRequestId),
                                  String(row.definitionId),
                                ).interpretationCode
                              "
                              size="default"
                              placeholder="选择"
                              clearable
                              class="dept-desk-inline-select"
                              @focus="focusObservationRow(bundle, row)"
                              @click.stop
                              @change="focusObservationRow(bundle, row)"
                            >
                              <ElOption
                                v-for="item in interpretationOptions"
                                :key="item.value"
                                :label="item.label"
                                :value="item.value"
                              />
                            </ElSelect>
                          </template>
                        </ElTableColumn>
                        <ElTableColumn
                          label="状态"
                          width="88"
                          align="center"
                          class-name="dept-desk-status-cell"
                        >
                          <template #default="{ row }">
                            <ElTag
                              size="small"
                              class="dept-desk-status-tag"
                              :type="
                                resolveObservationStatus(
                                  row,
                                  String(bundle.project.serviceRequestId),
                                ).type
                              "
                              effect="plain"
                            >
                              {{
                                resolveObservationStatus(
                                  row,
                                  String(bundle.project.serviceRequestId),
                                ).text
                              }}
                            </ElTag>
                          </template>
                        </ElTableColumn>
                      </ElTable>
                      <div
                        v-else
                        class="dept-desk-obs-block__empty text-sm text-[var(--el-text-color-placeholder)]"
                      >
                        该项目未配置小项，请在体检目录为项目绑定观察小项
                      </div>
                    </div>
                  </template>
                  <ElEmpty
                    v-else-if="!observationLoading"
                    description="暂无小项数据"
                    class="py-8"
                  />
                </div>
              </div>
            </div>
          </template>
        </div>
      </template>

      <template #right>
        <div class="flex h-full min-h-0 flex-col gap-2 overflow-hidden p-2">
          <SectionTitle class="shrink-0" title="项目结论" />

          <template v-if="!selectedRow">
            <div class="flex min-h-0 flex-1 items-center justify-center">
              <ElEmpty description="选择患者与检查项目后填写结论" :image-size="72" />
            </div>
          </template>

          <template v-else>
            <ElAlert
              type="info"
              :closable="false"
              show-icon
              class="shrink-0 !py-2"
              :title="`当前项目：${selectedRow.activityDisplay || '—'}`"
            />

            <div
              v-if="conclusionObservations.length > 0"
              class="min-h-0 shrink-0 overflow-hidden"
              style="max-height: 38%"
            >
              <ElTable
                :data="conclusionObservations"
                size="default"
                class="dept-desk-side-table"
                max-height="100%"
                highlight-current-row
                @row-click="(row) => focusObservationRow(selectedBundle!, row)"
              >
                <ElTableColumn
                  prop="definitionDisplay"
                  label="结论项"
                  min-width="100"
                  show-overflow-tooltip
                />
                <ElTableColumn
                  prop="valueString"
                  label="内容"
                  min-width="120"
                  show-overflow-tooltip
                />
              </ElTable>
            </div>

            <ElDivider v-if="conclusionObservations.length > 0" class="!my-1" />

            <div class="dept-desk-entry-form min-h-0 flex-1">
              <div class="dept-desk-entry-form__title">填写结论</div>
              <div class="flex h-full min-h-0 flex-col gap-2">
                <ElInput
                  v-model="conclusionText"
                  type="textarea"
                  :rows="10"
                  class="min-h-0 flex-1"
                  placeholder="请输入本项目结论"
                />
                <ElButton
                  type="success"
                  class="shrink-0 self-end"
                  :loading="savingConclusion"
                  :disabled="!conclusionDefinitionId"
                  @click="handleSaveConclusion"
                >
                  保存项目结论
                </ElButton>
                <p
                  v-if="!conclusionDefinitionId"
                  class="text-xs leading-relaxed text-[var(--el-text-color-placeholder)]"
                >
                  正在加载项目结论模板，请稍候或刷新页面后重试。
                </p>
              </div>
            </div>
          </template>
        </div>
      </template>

      <template #footer>
        <div class="flex w-full flex-wrap items-center justify-between gap-2">
          <span class="text-sm text-[var(--el-text-color-secondary)]">
            <template v-if="selectedPatient && dirtyObservationCount > 0">
              {{ selectedPatient.patientName }} · {{ dirtyObservationCount }} 条小项待保存
            </template>
            <template v-else-if="selectedPatient && selectedRow">
              {{ selectedPatient.patientName }} · {{ selectedRow.activityDisplay || '—' }}
            </template>
            <template v-else-if="selectedPatient">
              {{ selectedPatient.patientName }} · 在表格中直接录入小项结果
            </template>
            <template v-else>请从左侧选择待检患者</template>
          </span>
          <div class="flex flex-wrap gap-2">
            <ElButton
              size="default"
              :disabled="dirtyObservationCount === 0"
              :loading="saving"
              @click="handleBatchSave(false)"
            >
              批量保存
              <span v-if="dirtyObservationCount > 0">（{{ dirtyObservationCount }}）</span>
            </ElButton>
            <ElButton
              type="primary"
              size="default"
              :disabled="dirtyObservationCount === 0"
              :loading="saving"
              @click="handleBatchSave(true)"
            >
              批量确认
              <span v-if="dirtyObservationCount > 0">（{{ dirtyObservationCount }}）</span>
            </ElButton>
          </div>
        </div>
      </template>
    </StationTriPaneLayout>
  </div>
</template>

<style scoped>
:deep(.el-table .is-selected-row > td) {
  background-color: var(--el-color-primary-light-9) !important;
}
</style>
