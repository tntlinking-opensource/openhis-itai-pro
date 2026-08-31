<script setup lang="ts">
import type { DeptSummarySectionDto, MasterPendingRowDto } from '#/api/peis/master-review';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { RotateCw } from '@vben/icons';

import { useDebounceFn } from '@vueuse/core';
import {
  httpCompleteMasterReview,
  httpLoadMasterSummary,
  httpSaveMasterDraft,
  httpSearchPendingMaster,
} from '#/api/peis/master-review';
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  getDateRangeQuick,
  getLocalDayRangeDateTimeStrings,
} from '#/utils/format/date-range-quick';
import {
  ElButton,
  ElDatePicker,
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
import '../dept-desk/dept-desk-panel.css';

defineOptions({ name: 'PeisMasterReviewDesk' });

const CATEGORY_PROJECT_CONCLUSION = 'dept_conclusion';
type CenterFilterTab = 'all' | 'abnormal' | 'incomplete';

const router = useRouter();

const keyword = ref('');
const groupFilter = ref<'all' | 'group' | 'personal'>('all');
const dateRange = ref<[string, string]>(
  getDateRangeQuick({ strategy: 'calendar-day' }) as [string, string],
);
const filtersReady = ref(false);
const queuePager = reactive({ pageNo: 1, pageSize: 50 });
const queueRecords = ref<MasterPendingRowDto[]>([]);
const queueTotal = ref(0);
const queueLoading = ref(false);
const queueTableRef = ref<InstanceType<typeof ElTable> | null>(null);
const projectTableRef = ref<InstanceType<typeof ElTable> | null>(null);
const observationScrollRef = ref<HTMLElement | null>(null);

const selectedEncounterId = ref('');
const selectedRow = ref<MasterPendingRowDto | null>(null);
const summaryLoading = ref(false);
const deptSections = ref<DeptSummarySectionDto[]>([]);
const reportStatusEnum = ref('');
const summaryText = ref('');
const conclusionText = ref('');
const draftBaseline = reactive({ summary: '', conclusion: '' });
const patientSummary = reactive({
  patientName: '',
  busNo: '',
  patientAge: undefined as number | undefined,
  genderEnum: '',
  groupFlag: undefined as boolean | undefined,
  deptCompleteRatio: '',
});
const savingDraft = ref(false);
const completing = ref(false);
const centerFilterTab = ref<CenterFilterTab>('all');
const selectedProjectId = ref('');

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

const interpretationLabel: Record<string, string> = {
  N: '正常',
  H: '偏高',
  L: '偏低',
  A: '异常',
};

const interpretationTagType: Record<string, '' | 'danger' | 'info' | 'success' | 'warning'> = {
  N: 'success',
  H: 'danger',
  L: 'warning',
  A: 'danger',
};

const genderLabel: Record<string, string> = {
  MALE: '男',
  FEMALE: '女',
  M: '男',
  F: '女',
};

const reportStatusLabel: Record<string, string> = {
  preliminary: '草稿',
  final: '已定稿',
};

function filterRegularObservations(observations: MasterObservationRowDto[] | undefined) {
  return (observations ?? []).filter((row) => row.categoryCode !== CATEGORY_PROJECT_CONCLUSION);
}

function filterConclusionObservations(observations: MasterObservationRowDto[] | undefined) {
  return (observations ?? []).filter((row) => row.categoryCode === CATEGORY_PROJECT_CONCLUSION);
}

function isAbnormalInterpretation(code?: null | string) {
  if (!code) return false;
  const normalized = code.trim().toUpperCase();
  return normalized !== 'N' && normalized !== '';
}

function sectionHasAbnormal(section: DeptSummarySectionDto) {
  return filterRegularObservations(section.observations).some((row) =>
    isAbnormalInterpretation(row.interpretationCode),
  );
}

function sectionIsIncomplete(section: DeptSummarySectionDto) {
  const status = section.execStatusEnum ?? '';
  return status === 'abandoned' || status === 'deferred' || status === 'pending';
}

function projectBlockId(serviceRequestId: string) {
  return `master-review-project-${serviceRequestId}`;
}

function formatGender(code?: null | string) {
  if (!code) return '—';
  return genderLabel[code] ?? code;
}

function formatObservationValue(row: MasterObservationRowDto) {
  if (row.valueString?.trim()) {
    return row.valueString;
  }
  if (row.valueQuantity != null && row.valueQuantity !== '') {
    return String(row.valueQuantity);
  }
  return '—';
}

const isReadonly = computed(() => reportStatusEnum.value === 'final');

const isDraftDirty = computed(
  () =>
    summaryText.value !== draftBaseline.summary ||
    conclusionText.value !== draftBaseline.conclusion,
);

const filteredSections = computed(() => {
  if (centerFilterTab.value === 'abnormal') {
    return deptSections.value.filter((section) => sectionHasAbnormal(section));
  }
  if (centerFilterTab.value === 'incomplete') {
    return deptSections.value.filter((section) => sectionIsIncomplete(section));
  }
  return deptSections.value;
});

const abnormalCount = computed(
  () => deptSections.value.filter((section) => sectionHasAbnormal(section)).length,
);

const incompleteCount = computed(
  () => deptSections.value.filter((section) => sectionIsIncomplete(section)).length,
);

function syncDraftBaseline() {
  draftBaseline.summary = summaryText.value;
  draftBaseline.conclusion = conclusionText.value;
}

function resetWorkspace() {
  selectedEncounterId.value = '';
  selectedRow.value = null;
  deptSections.value = [];
  reportStatusEnum.value = '';
  summaryText.value = '';
  conclusionText.value = '';
  draftBaseline.summary = '';
  draftBaseline.conclusion = '';
  patientSummary.patientName = '';
  patientSummary.busNo = '';
  patientSummary.patientAge = undefined;
  patientSummary.genderEnum = '';
  patientSummary.groupFlag = undefined;
  patientSummary.deptCompleteRatio = '';
  centerFilterTab.value = 'all';
  selectedProjectId.value = '';
  queueTableRef.value?.setCurrentRow(undefined);
  projectTableRef.value?.setCurrentRow(undefined);
}

function scrollToProjectBlock(serviceRequestId: string) {
  const container = observationScrollRef.value;
  const block = document.getElementById(projectBlockId(serviceRequestId));
  if (!container || !block) return;
  const top = block.offsetTop - container.offsetTop;
  container.scrollTo({ top: Math.max(top - 4, 0), behavior: 'smooth' });
}

function selectDefaultProject() {
  const sections = filteredSections.value;
  const target = sections.find((section) => section.serviceRequestId) ?? sections[0];
  if (target?.serviceRequestId) {
    handleSelectProject(String(target.serviceRequestId), { scroll: true });
  } else {
    selectedProjectId.value = '';
    projectTableRef.value?.setCurrentRow(undefined);
  }
}

function handleSelectProject(serviceRequestId: string, options?: { scroll?: boolean }) {
  selectedProjectId.value = serviceRequestId;
  const row = filteredSections.value.find(
    (section) => String(section.serviceRequestId) === serviceRequestId,
  );
  nextTick(() => {
    if (row) {
      projectTableRef.value?.setCurrentRow(row);
    }
    if (options?.scroll) {
      scrollToProjectBlock(serviceRequestId);
    }
  });
}

const debouncedLoadQueue = useDebounceFn(() => {
  queuePager.pageNo = 1;
  void loadQueue();
}, 300);

function formatMasterQueueStatus(row: MasterPendingRowDto) {
  const ratio = row.deptCompleteRatio?.trim();
  if (ratio) {
    return `待总检·分科 ${ratio}`;
  }
  return '待总检';
}

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
    const res = await httpSearchPendingMaster({
      pageNo: queuePager.pageNo,
      pageSize: queuePager.pageSize,
      keyword: keyword.value.trim() || undefined,
      startDate,
      endDate,
      groupFlag: groupFilter.value === 'all' ? undefined : groupFilter.value === 'group',
    });
    const parsed = parsePagedRowsFromApiData(res);
    queueRecords.value = parsed.content as MasterPendingRowDto[];
    queueTotal.value = parsed.totalCount;

    if (selectedEncounterId.value) {
      const stillThere = queueRecords.value.some(
        (row) => String(row.encounterId) === selectedEncounterId.value,
      );
      if (!stillThere) {
        resetWorkspace();
      } else {
        const fresh = queueRecords.value.find(
          (row) => String(row.encounterId) === selectedEncounterId.value,
        );
        if (fresh) {
          selectedRow.value = fresh;
          nextTick(() => queueTableRef.value?.setCurrentRow(fresh));
        }
      }
    }
  } finally {
    if (!silent) {
      queueLoading.value = false;
    }
  }
}

async function handleSelectRow(row: MasterPendingRowDto) {
  selectedEncounterId.value = String(row.encounterId);
  selectedRow.value = row;
  centerFilterTab.value = 'all';
  summaryLoading.value = true;
  try {
    const res = await httpLoadMasterSummary({ encounterId: row.encounterId });
    deptSections.value = res.deptSections ?? [];
    reportStatusEnum.value = res.reportStatusEnum ?? '';
    summaryText.value = res.summaryText ?? '';
    conclusionText.value = res.conclusionText ?? '';
    syncDraftBaseline();
    patientSummary.patientName = res.patientName ?? row.patientName ?? '—';
    patientSummary.busNo = res.busNo ?? row.busNo ?? '—';
    patientSummary.patientAge = res.patientAge ?? row.patientAge ?? undefined;
    patientSummary.genderEnum = res.genderEnum ?? row.genderEnum ?? '';
    patientSummary.groupFlag = res.groupFlag ?? row.groupFlag ?? undefined;
    patientSummary.deptCompleteRatio = res.deptCompleteRatio ?? row.deptCompleteRatio ?? '—';
    selectDefaultProject();
    nextTick(() => queueTableRef.value?.setCurrentRow(row));
  } finally {
    summaryLoading.value = false;
  }
}

async function handleSaveDraft() {
  if (!selectedRow.value || isReadonly.value) {
    return;
  }
  if (!conclusionText.value.trim()) {
    ElMessage.warning('请填写总检结论');
    return;
  }
  savingDraft.value = true;
  try {
    const res = await httpSaveMasterDraft({
      encounterId: selectedRow.value.encounterId,
      summaryText: summaryText.value.trim() || undefined,
      conclusionText: conclusionText.value.trim(),
    });
    reportStatusEnum.value = res.statusEnum ?? reportStatusEnum.value;
    syncDraftBaseline();
    ElMessage.success('总检综述已保存');
  } finally {
    savingDraft.value = false;
  }
}

async function selectNextPatient(completedEncounterId: string) {
  await loadQueue({ silent: true });
  const next =
    queueRecords.value.find((row) => String(row.encounterId) !== completedEncounterId) ??
    queueRecords.value[0];
  if (next) {
    await handleSelectRow(next);
  } else {
    resetWorkspace();
  }
}

async function handleComplete() {
  if (!selectedRow.value || isReadonly.value) {
    return;
  }
  if (!conclusionText.value.trim()) {
    ElMessage.warning('请先填写总检结论');
    return;
  }
  completing.value = true;
  const completedId = String(selectedRow.value.encounterId);
  try {
    await httpSaveMasterDraft({
      encounterId: selectedRow.value.encounterId,
      summaryText: summaryText.value.trim() || undefined,
      conclusionText: conclusionText.value.trim(),
    });
    await httpCompleteMasterReview({
      encounterId: selectedRow.value.encounterId,
    });
    ElMessage.success('总检已完成');
    resetWorkspace();
    await selectNextPatient(completedId);
  } finally {
    completing.value = false;
  }
}

function handleSummarizeFromDept() {
  if (isReadonly.value) return;
  const lines: string[] = [];
  for (const section of deptSections.value) {
    const conclusions = filterConclusionObservations(section.observations);
    for (const row of conclusions) {
      const text = row.valueString?.trim();
      if (!text) continue;
      const prefix = section.activityDisplay?.trim();
      lines.push(prefix ? `${prefix}：${text}` : text);
    }
  }
  if (lines.length === 0) {
    ElMessage.warning('暂无项目结论可汇总');
    return;
  }
  summaryText.value = lines.join('\n');
}

function handlePreviewReport() {
  if (!selectedRow.value) return;
  router.push({
    path: '/peis/report/print',
    query: {
      encounterId: String(selectedRow.value.encounterId),
      autoPreview: '1',
      patientName: patientSummary.patientName,
      busNo: patientSummary.busNo,
    },
  });
}

watch(centerFilterTab, () => {
  selectDefaultProject();
});

watch([keyword, dateRange, groupFilter], () => {
  if (!filtersReady.value) return;
  debouncedLoadQueue();
});

onMounted(async () => {
  await loadQueue();
  filtersReady.value = true;
});
</script>

<template>
  <div class="dept-desk-page master-review-page flex h-full min-h-0 flex-col overflow-hidden">
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
            <SectionTitle title="待总检患者" />
            <div class="dept-desk-filter-toolbar mt-2">
              <SelectFuzzyInput
                v-model="keyword"
                placeholder="姓名 / 流水号"
                full-width
                @search="loadQueue"
              />
              <ElSelect v-model="groupFilter" placeholder="体检类型" class="w-full">
                <ElOption label="全部类型" value="all" />
                <ElOption label="团检" value="group" />
                <ElOption label="个检" value="personal" />
              </ElSelect>
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
              ref="queueTableRef"
              :data="queueRecords"
              highlight-current-row
              height="100%"
              size="default"
              class="dept-desk-side-table dept-desk-patient-table dept-desk-table-header-nowrap"
              row-key="encounterId"
              @row-click="handleSelectRow"
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
                  <span class="dept-desk-patient-status">{{ formatMasterQueueStatus(row) }}</span>
                </template>
              </ElTableColumn>
              <template #empty>
                <div class="py-6 text-sm text-[var(--el-text-color-placeholder)]">
                  暂无待总检患者
                  <div class="mt-2 text-xs leading-relaxed">
                    请确认日期含登记日；在检体检患者即可进入总检
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
          v-loading="summaryLoading"
          element-loading-background="transparent"
        >
          <SectionTitle class="shrink-0" title="分科结果汇总" />

          <template v-if="!selectedRow">
            <div class="flex min-h-0 flex-1 items-center justify-center">
              <ElEmpty description="请从左侧选择待总检患者" />
            </div>
          </template>

          <template v-else>
            <div class="patient-info-bar dept-desk-patient-bar shrink-0">
              <div class="patient-info-grid">
                <div class="patient-info-item">
                  <span class="patient-info-label">姓名</span>
                  <span class="patient-info-value dept-desk-patient-bar__emphasis">
                    {{ patientSummary.patientName }}
                  </span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">流水号</span>
                  <span class="patient-info-value">{{ patientSummary.busNo }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">性别/年龄</span>
                  <span class="patient-info-value">
                    {{ formatGender(patientSummary.genderEnum) }} /
                    {{ patientSummary.patientAge ?? '—' }}
                  </span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">类型</span>
                  <span class="patient-info-value">
                    {{ patientSummary.groupFlag ? '团检' : '个检' }}
                  </span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">分科完成</span>
                  <span class="patient-info-value dept-desk-patient-bar__emphasis">
                    {{ patientSummary.deptCompleteRatio }}
                  </span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">报告态</span>
                  <span class="patient-info-value">
                    {{ reportStatusLabel[reportStatusEnum] ?? reportStatusEnum ?? '未录入' }}
                  </span>
                </div>
              </div>
            </div>

            <div class="master-review-filter-tabs flex shrink-0 flex-wrap gap-2">
              <ElButton
                size="small"
                :type="centerFilterTab === 'all' ? 'primary' : 'default'"
                @click="centerFilterTab = 'all'"
              >
                全部
              </ElButton>
              <ElButton
                size="small"
                :type="centerFilterTab === 'abnormal' ? 'primary' : 'default'"
                @click="centerFilterTab = 'abnormal'"
              >
                异常
                <span v-if="abnormalCount > 0">（{{ abnormalCount }}）</span>
              </ElButton>
              <ElButton
                size="small"
                :type="centerFilterTab === 'incomplete' ? 'primary' : 'default'"
                @click="centerFilterTab = 'incomplete'"
              >
                未检/弃检
                <span v-if="incompleteCount > 0">（{{ incompleteCount }}）</span>
              </ElButton>
            </div>

            <div class="dept-desk-center-split min-h-0 flex-1 overflow-hidden">
              <div class="dept-desk-center-projects flex h-full min-h-0 flex-col overflow-hidden">
                <div class="dept-desk-center-projects__title">检查项目</div>
                <p class="dept-desk-center-projects__hint">点击定位结果</p>
                <div class="dept-desk-center-projects__scroll min-h-0 flex-1 overflow-hidden">
                  <ElTable
                    ref="projectTableRef"
                    :data="filteredSections"
                    highlight-current-row
                    height="100%"
                    size="default"
                    class="dept-desk-side-table dept-desk-project-table"
                    row-key="serviceRequestId"
                    @row-click="
                      (row) =>
                        row.serviceRequestId &&
                        handleSelectProject(String(row.serviceRequestId), { scroll: true })
                    "
                  >
                    <ElTableColumn label="项目" min-width="120" show-overflow-tooltip>
                      <template #default="{ row }">
                        {{ row.activityDisplay || '未命名项目' }}
                      </template>
                    </ElTableColumn>
                    <ElTableColumn label="状态" width="68" align="center">
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
                        当前筛选下暂无项目
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
                  <template v-if="filteredSections.length > 0">
                    <div
                      v-for="section in filteredSections"
                      :key="String(section.serviceRequestId ?? 'orphan')"
                      :id="
                        section.serviceRequestId
                          ? projectBlockId(String(section.serviceRequestId))
                          : undefined
                      "
                      class="dept-desk-obs-block"
                      :class="{
                        'dept-desk-obs-block--active':
                          selectedProjectId === String(section.serviceRequestId ?? ''),
                      }"
                    >
                      <div class="dept-desk-obs-block__head">
                        <span class="dept-desk-obs-block__name">
                          {{ section.activityDisplay || '未命名项目' }}
                        </span>
                        <ElTag
                          v-if="section.execStatusEnum"
                          size="default"
                          :type="execStatusTagType[section.execStatusEnum] ?? 'info'"
                          effect="plain"
                        >
                          {{ execStatusLabel[section.execStatusEnum] ?? section.execStatusEnum }}
                        </ElTag>
                        <span
                          v-if="sectionHasAbnormal(section)"
                          class="text-xs font-medium text-[var(--el-color-danger)]"
                        >
                          有异常
                        </span>
                      </div>

                      <ElTable
                        v-if="filterRegularObservations(section.observations).length > 0"
                        :data="filterRegularObservations(section.observations)"
                        size="default"
                        class="dept-desk-obs-table"
                        :row-class-name="
                          ({ row }) =>
                            isAbnormalInterpretation(row.interpretationCode)
                              ? 'master-review-row--abnormal'
                              : ''
                        "
                      >
                        <ElTableColumn label="小项" min-width="140" show-overflow-tooltip>
                          <template #default="{ row }">
                            <span class="dept-desk-obs-name">
                              {{ row.definitionDisplay || row.categoryCode || '—' }}
                            </span>
                          </template>
                        </ElTableColumn>
                        <ElTableColumn label="结果" min-width="160" show-overflow-tooltip>
                          <template #default="{ row }">
                            {{ formatObservationValue(row) }}
                          </template>
                        </ElTableColumn>
                        <ElTableColumn label="解读" width="88" align="center">
                          <template #default="{ row }">
                            <ElTag
                              v-if="row.interpretationCode"
                              size="small"
                              :type="
                                interpretationTagType[row.interpretationCode] ??
                                (isAbnormalInterpretation(row.interpretationCode)
                                  ? 'danger'
                                  : 'info')
                              "
                              effect="plain"
                            >
                              {{
                                interpretationLabel[row.interpretationCode] ??
                                row.interpretationCode
                              }}
                            </ElTag>
                            <span v-else>—</span>
                          </template>
                        </ElTableColumn>
                      </ElTable>

                      <div
                        v-if="filterConclusionObservations(section.observations).length > 0"
                        class="master-review-conclusion-block border-t border-[var(--el-border-color-lighter)] px-3 py-2"
                      >
                        <div class="mb-1 text-sm font-semibold text-[var(--el-text-color-primary)]">
                          项目结论
                        </div>
                        <div
                          v-for="row in filterConclusionObservations(section.observations)"
                          :key="String(row.id ?? row.definitionId)"
                          class="text-sm leading-relaxed text-[var(--el-text-color-regular)]"
                        >
                          {{ row.valueString || '—' }}
                        </div>
                      </div>

                      <div
                        v-if="
                          filterRegularObservations(section.observations).length === 0 &&
                          filterConclusionObservations(section.observations).length === 0
                        "
                        class="dept-desk-obs-block__empty text-sm text-[var(--el-text-color-placeholder)]"
                      >
                        该项目暂无结果数据
                      </div>
                    </div>
                  </template>
                  <ElEmpty
                    v-else-if="!summaryLoading"
                    description="当前筛选下暂无分科结果"
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
          <SectionTitle class="shrink-0" title="总检录入" />

          <template v-if="!selectedRow">
            <div class="flex min-h-0 flex-1 items-center justify-center">
              <ElEmpty description="请选择就诊后录入总检综述与结论" :image-size="72" />
            </div>
          </template>

          <template v-else>
            <div class="dept-desk-entry-form flex min-h-0 flex-1 flex-col gap-3">
              <div class="flex items-center justify-between gap-2">
                <div class="dept-desk-entry-form__title">综述</div>
                <ElButton
                  link
                  type="primary"
                  :disabled="isReadonly"
                  @click="handleSummarizeFromDept"
                >
                  从项目结论汇总
                </ElButton>
              </div>
              <ElInput
                v-model="summaryText"
                type="textarea"
                :rows="5"
                :disabled="isReadonly"
                placeholder="综述（可选，将出现在报告前言）"
              />

              <div class="dept-desk-entry-form__title">总检结论</div>
              <ElInput
                v-model="conclusionText"
                type="textarea"
                :rows="8"
                class="min-h-0 flex-1"
                :disabled="isReadonly"
                placeholder="总检结论（必填）"
              />
            </div>
          </template>
        </div>
      </template>

      <template #footer>
        <div class="flex w-full flex-wrap items-center justify-between gap-2">
          <span class="text-sm text-[var(--el-text-color-secondary)]">
            <template v-if="selectedRow && isDraftDirty">
              {{ patientSummary.patientName }} · 综述/结论未保存
            </template>
            <template v-else-if="selectedRow">
              {{ patientSummary.patientName }} · {{ patientSummary.busNo }}
              <span v-if="reportStatusEnum">
                · {{ reportStatusLabel[reportStatusEnum] ?? reportStatusEnum }}
              </span>
            </template>
            <template v-else>请从左侧选择待总检患者</template>
          </span>
          <div class="flex flex-wrap gap-2">
            <ElButton size="default" :loading="queueLoading" @click="loadQueue">刷新</ElButton>
            <ElButton
              size="default"
              :disabled="!selectedRow || isReadonly"
              :loading="savingDraft"
              @click="handleSaveDraft"
            >
              保存草稿
            </ElButton>
            <ElButton size="default" :disabled="!selectedRow" @click="handlePreviewReport">
              预览报告
            </ElButton>
            <ElButton
              type="primary"
              size="default"
              :disabled="!selectedRow || isReadonly"
              :loading="completing"
              @click="handleComplete"
            >
              总检完成
            </ElButton>
          </div>
        </div>
      </template>
    </StationTriPaneLayout>
  </div>
</template>

<style scoped>
:deep(.master-review-row--abnormal > td) {
  background-color: var(--el-color-danger-light-9) !important;
}

:deep(.el-table .is-selected-row > td) {
  background-color: var(--el-color-primary-light-9) !important;
}
</style>
