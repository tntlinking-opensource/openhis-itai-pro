<script setup lang="ts">
import type { CatalogDownloadCursor, CatalogDownloadPreview } from '#/chs/api';
import type { ChargeType } from '#/api/core/common';
import type {
  Catalog1305VerBranch,
  CatalogFileDownloadInfno,
} from '#/chs/constants/catalog-download';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@zxplat/common-ui';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElCheckbox,
  ElCol,
  ElCollapse,
  ElCollapseItem,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElProgress,
  ElRadioButton,
  ElRadioGroup,
  ElRow,
  ElSelect,
  ElSteps,
  ElStep,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
} from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import { listDownloadCursorsApi, previewCatalogDownloadApi } from '#/chs/api';
import { useCatalogDownloadProgress } from '#/chs/composables/use-catalog-download-progress';
import {
  CATALOG_1305_VER_BRANCH_OPTIONS,
  CATALOG_FILE_DOWNLOAD_INFNO_OPTIONS,
  buildCatalogPipelineOverview,
  buildCatalogVersionLoopSteps,
  catalogDownloadHint,
  defaultCatalogStartVer,
  matches1305Branch,
} from '#/chs/constants/catalog-download';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import AppDialog from '#/components/app-dialog.vue';
import { useRequest } from '#/hooks/use-request';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';

defineOptions({ name: 'ChsCatalogDownloadPage' });

const route = useRoute();

const contractBusNo = ref<string>();
const infno = ref<CatalogFileDownloadInfno>('1301');
const verBranch = ref<Catalog1305VerBranch>('F002');
/** 仅探测：有值时才传 maxVersions；正式下载不传（无轮次上限） */
const previewMaxRounds = ref<number | undefined>(undefined);
/** incremental = 沿用游标；full = 全量重拉 */
const syncMode = ref<'incremental' | 'full'>('incremental');
const clearZipCache = ref(false);
const helpOpen = ref<string[]>([]);

const chargeTypeOptions = ref<ChargeType[]>([]);
const downloadCursors = ref<CatalogDownloadCursor[]>([]);
const versionPreview = ref<CatalogDownloadPreview | null>(null);
const progressDialogVisible = ref(false);

const {
  status: downloadStatus,
  running: downloadRunning,
  overallPercent,
  totalVersions,
  processedVersions,
  upsertedRows,
  batchPhases,
  detailLine,
  summary: downloadSummary,
  resultVersions,
  errorMessage: downloadError,
  resetProgress,
  runDownload,
} = useCatalogDownloadProgress();

const forceStartFromZero = computed(() => syncMode.value === 'full');

const infnoLabel = computed(
  () =>
    CATALOG_FILE_DOWNLOAD_INFNO_OPTIONS.find((item) => item.value === infno.value)?.label ??
    infno.value,
);

/** 同 infno 最新游标；1305 仅取当前分支（F002/F003） */
const currentCursor = computed(() => {
  const rows = downloadCursors.value.filter((row) => {
    if (row.infno !== infno.value) return false;
    if (infno.value === '1305') {
      return matches1305Branch(row.localVer, verBranch.value);
    }
    return true;
  });
  if (rows.length === 0) return undefined;
  return rows.reduce((latest, row) => {
    if (!latest) return row;
    return (row.lastSuccessAt ?? '') > (latest.lastSuccessAt ?? '') ? row : latest;
  });
});

const downloadHint = computed(() => catalogDownloadHint(infno.value));
const pipelineOverview = computed(() => buildCatalogPipelineOverview(infno.value));
const versionLoopSteps = computed(() => buildCatalogVersionLoopSteps(infno.value));

const effectiveStartVer = computed(() => {
  if (forceStartFromZero.value) {
    return defaultCatalogStartVer(infno.value, verBranch.value);
  }
  return (
    currentCursor.value?.localVer?.trim() || defaultCatalogStartVer(infno.value, verBranch.value)
  );
});

const effectiveStartVerShort = computed(() => {
  const ver = effectiveStartVer.value;
  return ver.length > 28 ? `${ver.slice(0, 12)}…${ver.slice(-10)}` : ver;
});

const pendingVersionCount = computed(() => versionPreview.value?.pendingVersionCount ?? 0);
const previewHasMore = computed(() => Boolean(versionPreview.value?.hasMore));
const previewStartVer = computed(
  () => versionPreview.value?.startLocalVerShort ?? versionPreview.value?.startLocalVer ?? '—',
);

const mirrorRowCount = computed(() => Number(currentCursor.value?.localCatalogRowCount ?? 0));

const versionPreviewHint = computed(() => {
  if (!versionPreview.value) {
    return '尚未探测。探测只预览 ZIP 数量、不入库；正式同步请点「开始下载」。';
  }
  const n = pendingVersionCount.value;
  if (n === 0) {
    if (!currentCursor.value) {
      return '探测 0 包且尚无游标：请「开始下载」全量拉取；若仍为 0，查服务端日志或平台是否开通该目录。';
    }
    return '探测无新包：本地游标已是最新，一般无需再下（可取消全量重拉后增量同步）。';
  }
  const more = previewHasMore.value ? '；已达探测轮次上限' : '';
  return `预计 ${n} 个 ZIP，起点 ${previewStartVer.value}${more}。探测不入库，请点「开始下载」。`;
});

const startVerHint = computed(() => {
  if (forceStartFromZero.value) {
    return clearZipCache.value
      ? '将忽略游标并从规范初始 ver 重拉；同时清空本地 ZIP 后重新 9102。'
      : '将忽略游标并从规范初始 ver 重拉；已有 ZIP 仍可读本地缓存。';
  }
  if (!currentCursor.value) {
    return `当前接口尚无游标，将从 ${defaultCatalogStartVer(infno.value, verBranch.value)} 开始（种子导入后若仍无，请点刷新）。`;
  }
  return '沿用最新游标做增量；已缓存 ZIP 不会重复 9102。';
});

const show1305Branch = computed(() => infno.value === '1305');

const filteredCursors = computed(() =>
  downloadCursors.value
    .filter((row) => row.infno === infno.value)
    .sort((a, b) => (b.lastSuccessAt ?? '').localeCompare(a.lastSuccessAt ?? '')),
);

const emptyCursorHint = computed(() => {
  if (infno.value === '1305') {
    return `${verBranch.value} 分支暂无游标，将从 ${defaultCatalogStartVer(infno.value, verBranch.value)} 开始`;
  }
  return '暂无游标。若刚用种子工具导入，请点「刷新」；仍无则需本接口下载入库。';
});

const activePipelineStep = computed(() => {
  if (downloadRunning.value) {
    const active = batchPhases.find((p) => p.status === 'active');
    if (active?.key === 'collect' || active?.key === 'collect_done') return 0;
    if (active?.key === 'parse') return 1;
    if (active?.key === 'import') return 2;
  }
  if (downloadStatus.value === 'success') return 3;
  return -1;
});

const { run: runListCursors, loading: cursorLoading } = useRequest(listDownloadCursorsApi, {
  manual: true,
  onSuccess: (rows) => {
    downloadCursors.value = rows ?? [];
  },
});

const { run: runPreview, loading: previewLoading } = useRequest(previewCatalogDownloadApi, {
  manual: true,
  showErrorMessage: true,
  onSuccess: (data) => {
    versionPreview.value = data ?? null;
  },
});

const { run: runSignIn, loading: signInLoading } = useRequest(ensureInsuranceSignIn, {
  manual: true,
  showErrorMessage: true,
  onSuccess: (signNo) => {
    ElMessage.success(`签到成功：${signNo ?? ''}`);
  },
});

function formatCount(n: number | undefined | null) {
  return Number(n ?? 0).toLocaleString();
}

function formatSyncTime(row: CatalogDownloadCursor) {
  const raw = row.lastSuccessAt?.trim();
  if (!raw) return '—';
  return raw.replace('T', ' ').slice(0, 19);
}

function requireContractBusNo(): string | undefined {
  const busNo = contractBusNo.value;
  if (busNo === undefined || busNo === null || String(busNo).trim() === '') {
    ElMessage.warning('请先选择医保契约');
    return undefined;
  }
  return String(busNo).trim();
}

async function loadChargeTypes() {
  try {
    const all = await getChargeTypeListApi();
    const eligible = filterInsuranceChargeTypes(all);
    chargeTypeOptions.value = eligible;
    contractBusNo.value = resolveDefaultInsuranceBusNo(eligible, contractBusNo.value);
  } catch {
    chargeTypeOptions.value = [];
    contractBusNo.value = undefined;
  }
}

async function refreshCursors() {
  const busNo = requireContractBusNo();
  if (!busNo) return;
  await runListCursors(busNo);
}

async function refreshOnContextChange() {
  versionPreview.value = null;
  if (!downloadRunning.value) {
    resetProgress();
  }
  // 切换接口时默认回到增量，避免误勾全量
  syncMode.value = 'incremental';
  clearZipCache.value = false;
  if (contractBusNo.value) {
    await refreshCursors();
  } else {
    downloadCursors.value = [];
  }
}

async function handlePreviewVersions() {
  const busNo = requireContractBusNo();
  if (!busNo) return;
  try {
    await runSignIn(busNo);
  } catch {
    return;
  }
  versionPreview.value = null;
  await runPreview({
    contractBusNo: busNo,
    infno: infno.value,
    maxVersions:
      previewMaxRounds.value != null && previewMaxRounds.value > 0
        ? previewMaxRounds.value
        : undefined,
    forceStartFromZero: forceStartFromZero.value,
    clearZipCache: clearZipCache.value,
    verBranch: show1305Branch.value ? verBranch.value : undefined,
  });
}

async function handleSignIn() {
  const busNo = requireContractBusNo();
  if (!busNo) return;
  await runSignIn(busNo);
}

async function handleStartDownload() {
  const busNo = requireContractBusNo();
  if (!busNo) return;

  if (forceStartFromZero.value) {
    try {
      await ElMessageBox.confirm(
        [
          `将全量重拉 ${infnoLabel.value}（忽略现有游标）。`,
          `起点 ver：${effectiveStartVer.value}`,
          '种子导入后的院通常应使用「增量同步」，全量重拉可能耗时很长。',
          '确定继续？',
        ].join('\n'),
        '确认全量重拉',
        { type: 'warning', confirmButtonText: '全量重拉', cancelButtonText: '取消' },
      );
    } catch {
      return;
    }
  } else {
    const previewHint =
      pendingVersionCount.value > 0
        ? `探测约 ${pendingVersionCount.value} 个 ZIP 包。`
        : '未探测或探测为 0，将按版本链拉取直至平台无新包。';
    try {
      await ElMessageBox.confirm(
        [
          `${infnoLabel.value}：拉取 → 解析 → 入库`,
          `起点 ver：${effectiveStartVer.value}`,
          previewHint,
          '过程可能数分钟，请勿关闭页面。',
        ].join('\n'),
        '确认开始下载',
        { type: 'info', confirmButtonText: '开始下载', cancelButtonText: '取消' },
      );
    } catch {
      return;
    }
  }

  try {
    await runSignIn(busNo);
  } catch {
    return;
  }

  resetProgress(Math.max(pendingVersionCount.value, 1));
  progressDialogVisible.value = true;

  await runDownload({
    contractBusNo: busNo,
    infno: infno.value,
    forceStartFromZero: forceStartFromZero.value,
    clearZipCache: clearZipCache.value,
    verBranch: show1305Branch.value ? verBranch.value : undefined,
    expectedVersionCount: Math.max(pendingVersionCount.value, 1),
  });

  await refreshCursors();
  await handlePreviewVersions();
}

function closeProgressDialog() {
  if (downloadRunning.value) {
    ElMessage.warning('下载进行中，请等待完成');
    return;
  }
  progressDialogVisible.value = false;
}

function isLatestCursor(row: CatalogDownloadCursor) {
  return currentCursor.value?.id != null && row.id === currentCursor.value.id;
}

watch(contractBusNo, () => {
  void refreshOnContextChange();
});

watch(infno, () => {
  void refreshOnContextChange();
});

watch(verBranch, () => {
  void refreshOnContextChange();
});

watch(previewMaxRounds, () => {
  versionPreview.value = null;
});

watch(syncMode, (mode) => {
  versionPreview.value = null;
  if (mode !== 'full') clearZipCache.value = false;
});

onMounted(async () => {
  const queryBusNo = route.query.contractBusNo;
  if (typeof queryBusNo === 'string' && queryBusNo.trim()) {
    contractBusNo.value = queryBusNo.trim();
  }
  await loadChargeTypes();
  if (contractBusNo.value) await refreshCursors();
});
</script>

<template>
  <Page auto-content-height>
    <div class="catalog-dl flex h-full min-h-0 flex-col gap-3 p-2">
      <!-- 顶栏：契约 + 接口 + 操作 -->
      <div
        class="flex shrink-0 flex-col gap-3 rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-900"
      >
        <div class="flex flex-wrap items-end gap-3">
          <div class="min-w-[200px] flex-1">
            <div class="mb-1 text-xs text-gray-500">医保契约</div>
            <ElSelect v-model="contractBusNo" placeholder="请选择契约" filterable class="w-full">
              <ElOption
                v-for="item in chargeTypeOptions"
                :key="item.busNo"
                :label="item.busNo ? `${item.label}（${item.busNo}）` : item.label"
                :value="item.busNo!"
              />
            </ElSelect>
          </div>
          <div class="min-w-[180px] flex-1">
            <div class="mb-1 text-xs text-gray-500">下载接口</div>
            <ElSelect v-model="infno" class="w-full">
              <ElOption
                v-for="item in CATALOG_FILE_DOWNLOAD_INFNO_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </div>
          <div v-if="show1305Branch" class="min-w-[140px]">
            <div class="mb-1 text-xs text-gray-500">1305 分支</div>
            <ElSelect v-model="verBranch" class="w-full">
              <ElOption
                v-for="item in CATALOG_1305_VER_BRANCH_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </div>
          <div class="flex flex-wrap gap-2 pb-0.5">
            <ElButton plain :loading="signInLoading" @click="handleSignIn">签到</ElButton>
            <ElButton plain :loading="previewLoading" @click="handlePreviewVersions">
              探测
            </ElButton>
            <ElButton plain :loading="cursorLoading" @click="refreshCursors">刷新</ElButton>
            <ElButton
              type="primary"
              :loading="downloadRunning"
              :disabled="!contractBusNo"
              @click="handleStartDownload"
            >
              开始下载
            </ElButton>
          </div>
        </div>

        <div
          class="flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-gray-100 pt-3 dark:border-gray-800"
        >
          <ElRadioGroup v-model="syncMode" size="small">
            <ElRadioButton value="incremental">增量同步</ElRadioButton>
            <ElRadioButton value="full">全量重拉</ElRadioButton>
          </ElRadioGroup>
          <ElCheckbox v-if="syncMode === 'full'" v-model="clearZipCache" size="small" class="!mr-0">
            同时清空 ZIP 缓存
          </ElCheckbox>
          <div class="flex min-w-0 flex-1 flex-wrap items-baseline gap-2 text-sm">
            <span class="shrink-0 text-xs text-gray-500">起点</span>
            <ElTooltip :content="effectiveStartVer" placement="top">
              <code class="text-primary truncate font-mono text-sm">{{
                effectiveStartVerShort
              }}</code>
            </ElTooltip>
            <span class="text-xs text-gray-500">{{ startVerHint }}</span>
          </div>
          <ElTag v-if="downloadRunning" type="warning" effect="dark" size="small">
            下载进行中
          </ElTag>
          <ElTag
            v-else-if="downloadStatus === 'success'"
            type="success"
            effect="plain"
            size="small"
          >
            上次成功
          </ElTag>
        </div>
      </div>

      <ElAlert
        v-if="syncMode === 'full'"
        type="warning"
        :closable="false"
        show-icon
        class="shrink-0"
        title="全量重拉会忽略游标，种子落库后的新院请优先用「增量同步」。"
      />

      <ElRow :gutter="12" class="min-h-0 flex-1">
        <ElCol :xs="24" :lg="9" class="mb-3 lg:mb-0">
          <div class="flex h-full flex-col gap-3">
            <ElCard shadow="never" class="shrink-0">
              <template #header>
                <div class="flex items-center justify-between">
                  <span class="font-medium">{{ infnoLabel }}</span>
                  <ElTag v-if="currentCursor" size="small" type="success">已有游标</ElTag>
                  <ElTag v-else size="small" type="info">无游标</ElTag>
                </div>
              </template>

              <div class="grid grid-cols-2 gap-3">
                <div>
                  <div class="text-xs text-gray-500">本地镜像行数</div>
                  <div class="mt-1 text-2xl font-semibold tabular-nums">
                    {{ formatCount(mirrorRowCount) }}
                  </div>
                </div>
                <div>
                  <div class="text-xs text-gray-500">探测 ZIP 包数</div>
                  <div class="mt-1 text-2xl font-semibold tabular-nums">
                    {{ pendingVersionCount }}
                  </div>
                </div>
              </div>

              <div
                v-if="currentCursor"
                class="mt-3 rounded-md bg-gray-50 px-3 py-2 text-sm dark:bg-gray-800/60"
              >
                <div class="mb-1 flex flex-wrap items-center gap-2">
                  <span class="text-xs text-gray-500">最新 local_ver</span>
                  <ElTooltip :content="currentCursor.localVer || '0'" placement="top">
                    <code class="font-mono text-xs">{{
                      currentCursor.localVerShort || currentCursor.localVer || '0'
                    }}</code>
                  </ElTooltip>
                </div>
                <div class="text-xs text-gray-500">
                  最近同步：{{ formatSyncTime(currentCursor) }}
                  <template v-if="currentCursor.lastFileQuryNo">
                    · ZIP
                    <ElTag
                      v-if="currentCursor.lastZipCached"
                      type="success"
                      size="small"
                      class="ml-1"
                    >
                      已缓存
                    </ElTag>
                    <ElTag v-else size="small" class="ml-1">未缓存</ElTag>
                  </template>
                </div>
              </div>
              <ElEmpty v-else class="!py-4" :description="emptyCursorHint" :image-size="48" />

              <p class="mb-0 mt-3 text-xs leading-5 text-gray-500">{{ versionPreviewHint }}</p>
            </ElCard>

            <ElCard shadow="never" class="pipeline-overview min-h-0 flex-1">
              <template #header>
                <span class="font-medium">下载流水线</span>
              </template>
              <ElSteps
                :active="activePipelineStep"
                direction="vertical"
                finish-status="success"
                class="pipeline-steps"
              >
                <ElStep
                  v-for="(step, idx) in pipelineOverview"
                  :key="idx"
                  :title="step.title"
                  :description="step.desc"
                />
              </ElSteps>
              <p class="mb-0 mt-2 text-xs leading-5 text-gray-500">{{ downloadHint }}</p>
            </ElCard>

            <ElAlert
              v-if="downloadSummary && downloadStatus === 'success' && !downloadRunning"
              type="success"
              :closable="true"
              show-icon
              class="shrink-0"
            >
              <template #title>
                上次完成：{{ downloadSummary.totalVersionCount }} 包 /
                {{ formatCount(downloadSummary.totalUpsert) }} 行
              </template>
            </ElAlert>
          </div>
        </ElCol>

        <ElCol :xs="24" :lg="15">
          <div class="flex h-full flex-col gap-3">
            <ElCard shadow="never" class="min-h-0 flex-1">
              <template #header>
                <div class="flex flex-wrap items-center justify-between gap-2">
                  <span class="font-medium">游标历史 · {{ infnoLabel }}</span>
                  <span class="text-xs text-gray-400">
                    共 {{ filteredCursors.length }} 条（切换接口自动刷新）
                  </span>
                </div>
              </template>
              <ElTable
                v-loading="cursorLoading"
                :data="filteredCursors"
                border
                stripe
                empty-text="暂无该接口的游标记录"
                max-height="420"
                size="small"
              >
                <ElTableColumn label="" width="56" align="center">
                  <template #default="{ row }">
                    <ElTag v-if="isLatestCursor(row)" type="primary" size="small">当前</ElTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="local_ver" min-width="150">
                  <template #default="{ row }">
                    <ElTooltip :content="row.localVer || '0'" placement="top">
                      <code class="font-mono text-xs">{{
                        row.localVerShort || row.localVer || '0'
                      }}</code>
                    </ElTooltip>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="镜像行数" width="100" align="right">
                  <template #default="{ row }">
                    {{ formatCount(row.localCatalogRowCount) }}
                  </template>
                </ElTableColumn>
                <ElTableColumn label="同步时间" min-width="160">
                  <template #default="{ row }">{{ formatSyncTime(row) }}</template>
                </ElTableColumn>
                <ElTableColumn label="ZIP" width="80" align="center">
                  <template #default="{ row }">
                    <ElTag v-if="row.lastZipCached" type="success" size="small">缓存</ElTag>
                    <ElTag v-else-if="row.lastFileQuryNo" size="small">无</ElTag>
                    <span v-else>—</span>
                  </template>
                </ElTableColumn>
              </ElTable>
            </ElCard>

            <ElCard v-if="versionPreview?.versions?.length" shadow="never" class="shrink-0">
              <template #header>
                <div class="flex flex-wrap items-center gap-2">
                  <span class="font-medium">版本链预览</span>
                  <ElTag size="small">起点 {{ previewStartVer }}</ElTag>
                  <ElTag size="small" type="warning">{{ pendingVersionCount }} 包</ElTag>
                </div>
              </template>
              <ElTable :data="versionPreview.versions" border size="small" max-height="220">
                <ElTableColumn prop="index" label="#" width="44" align="center" />
                <ElTableColumn label="请求 ver" min-width="130">
                  <template #default="{ row }">
                    <ElTooltip v-if="row.requestVer" :content="row.requestVer" placement="top">
                      <code class="text-xs">{{ row.requestVerShort || row.requestVer }}</code>
                    </ElTooltip>
                    <span v-else>—</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="下一 ver" min-width="130">
                  <template #default="{ row }">
                    <ElTooltip
                      v-if="row.nextDownloadVer"
                      :content="row.nextDownloadVer"
                      placement="top"
                    >
                      <code class="text-xs">{{
                        row.nextDownloadVerShort || row.nextDownloadVer
                      }}</code>
                    </ElTooltip>
                    <span v-else class="text-gray-400">链结束</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn prop="dataCnt" label="平台行数" width="80" align="right" />
                <ElTableColumn label="ZIP" width="72" align="center">
                  <template #default="{ row }">
                    <ElTag v-if="row.zipFromCache" type="info" size="small">缓存</ElTag>
                    <ElTag v-else type="success" size="small">新下</ElTag>
                  </template>
                </ElTableColumn>
              </ElTable>
            </ElCard>

            <ElCollapse v-model="helpOpen" class="advanced-collapse shrink-0">
              <ElCollapseItem title="高级：探测轮次 / 阶段说明" name="advanced">
                <ElForm label-width="96px" label-position="left" class="px-1">
                  <ElFormItem label="探测轮次">
                    <ElInputNumber
                      v-model="previewMaxRounds"
                      :min="1"
                      :max="9999"
                      clearable
                      placeholder="不填=无上限"
                    />
                    <span class="ml-2 text-xs text-gray-500">仅影响「探测」，正式下载无上限</span>
                  </ElFormItem>
                  <ElFormItem label="阶段一循环">
                    <ol class="m-0 list-decimal pl-4 text-sm leading-6 text-gray-600">
                      <li v-for="step in versionLoopSteps" :key="step">{{ step }}</li>
                    </ol>
                  </ElFormItem>
                </ElForm>
              </ElCollapseItem>
            </ElCollapse>
          </div>
        </ElCol>
      </ElRow>
    </div>

    <AppDialog
      v-model="progressDialogVisible"
      title="目录下载进度"
      width="720px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      @close="closeProgressDialog"
    >
      <div class="flex flex-col gap-4">
        <div>
          <div class="mb-2 flex items-center justify-between text-sm">
            <span>
              总体进度
              <template v-if="totalVersions > 0">（约 {{ totalVersions }} 个 ZIP 包）</template>
            </span>
            <span class="font-medium">{{ overallPercent }}%</span>
          </div>
          <ElProgress
            :percentage="overallPercent"
            :status="
              downloadStatus === 'error'
                ? 'exception'
                : downloadStatus === 'success'
                  ? 'success'
                  : undefined
            "
            :stroke-width="14"
            striped
            striped-flow
          />
        </div>

        <ElAlert v-if="detailLine" :title="detailLine" type="info" :closable="false" show-icon />

        <div
          v-if="downloadStatus === 'running' && (upsertedRows > 0 || processedVersions > 0)"
          class="grid grid-cols-2 gap-3 rounded-lg border border-blue-100 bg-blue-50/60 p-3 text-sm md:grid-cols-3 dark:border-blue-900 dark:bg-blue-950/30"
        >
          <div>
            <span class="text-gray-500">已处理 ZIP</span>
            <div class="text-lg font-semibold">
              {{ processedVersions }} / {{ totalVersions || '—' }}
            </div>
          </div>
          <div>
            <span class="text-gray-500">已入库行数</span>
            <div class="text-lg font-semibold text-blue-700 dark:text-blue-300">
              {{ upsertedRows.toLocaleString() }}
            </div>
          </div>
          <div class="hidden md:block">
            <span class="text-gray-500">总体进度</span>
            <div class="text-lg font-semibold">{{ overallPercent }}%</div>
          </div>
        </div>

        <div class="rounded-lg border border-gray-200 p-4 dark:border-gray-700">
          <div class="mb-3 text-sm font-medium">执行阶段</div>
          <div v-for="phase in batchPhases" :key="phase.key" class="mb-4 last:mb-0">
            <div class="mb-1 flex items-center justify-between gap-2 text-sm">
              <span>{{ phase.label }}</span>
              <ElTag v-if="phase.status === 'active'" type="warning" size="small">进行中</ElTag>
              <ElTag v-else-if="phase.status === 'success'" type="success" size="small">完成</ElTag>
              <ElTag v-else-if="phase.status === 'exception'" type="danger" size="small"
                >失败</ElTag
              >
              <ElTag v-else size="small" type="info">等待</ElTag>
            </div>
            <p v-if="phase.detail" class="mb-2 text-xs leading-5 text-gray-500">
              {{ phase.detail }}
            </p>
            <ElProgress
              :percentage="phase.percent"
              :status="
                phase.status === 'exception'
                  ? 'exception'
                  : phase.status === 'success'
                    ? 'success'
                    : undefined
              "
              :stroke-width="8"
              :striped="phase.status === 'active'"
              :striped-flow="phase.status === 'active'"
            />
          </div>
        </div>

        <ElAlert
          v-if="downloadStatus === 'success' && downloadSummary"
          type="success"
          :closable="false"
          show-icon
        >
          <template #title>下载完成</template>
          <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm md:grid-cols-4">
            <div>ZIP 包：{{ downloadSummary.totalVersionCount }}</div>
            <div>入库行：{{ formatCount(downloadSummary.totalUpsert) }}</div>
            <div>游标快照：{{ downloadSummary.cursorSnapshotCount ?? 0 }}</div>
            <div>
              最新 ver：
              <code class="text-xs">{{ downloadSummary.localVer ?? '—' }}</code>
            </div>
          </div>
        </ElAlert>

        <ElTable
          v-if="downloadStatus === 'success' && resultVersions.length"
          :data="resultVersions"
          border
          size="small"
          max-height="200"
        >
          <ElTableColumn prop="index" label="#" width="40" align="center" />
          <ElTableColumn label="请求 ver" min-width="100">
            <template #default="{ row }">
              <span class="text-xs">{{ row.requestVerShort || row.requestVer }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="入库行" width="72" align="right">
            <template #default="{ row }">{{ row.parsedRowCount ?? '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="下一 ver" min-width="100">
            <template #default="{ row }">
              <span v-if="row.nextDownloadVer" class="text-xs">
                {{ row.nextDownloadVerShort || row.nextDownloadVer }}
              </span>
              <span v-else class="text-gray-400">结束</span>
            </template>
          </ElTableColumn>
        </ElTable>

        <ElAlert
          v-if="downloadStatus === 'error'"
          type="error"
          :closable="false"
          show-icon
          :title="downloadError || '下载失败'"
        />
      </div>

      <template #footer>
        <ElButton :disabled="downloadRunning" @click="closeProgressDialog">
          {{ downloadRunning ? '下载中…' : '关闭' }}
        </ElButton>
      </template>
    </AppDialog>
  </Page>
</template>

<style scoped>
.pipeline-steps :deep(.el-step__title) {
  font-size: 13px;
  line-height: 1.3;
}
.pipeline-steps :deep(.el-step__description) {
  font-size: 11px;
  line-height: 1.4;
}
.advanced-collapse {
  border: none;
}
.advanced-collapse :deep(.el-collapse-item__header) {
  height: 40px;
  font-size: 13px;
  color: #6b7280;
  background: transparent;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
</style>
