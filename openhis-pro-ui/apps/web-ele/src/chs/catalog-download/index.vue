<script setup lang="ts">
import type { CatalogDownloadCursor, CatalogDownloadPreview } from '#/chs/api';
import type { ChargeType } from '#/api/core/common';
import type {
  Catalog1305VerBranch,
  CatalogFileDownloadInfno,
} from '#/chs/constants/catalog-download';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElCheckbox,
  ElCol,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElProgress,
  ElRow,
  ElSelect,
  ElStatistic,
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
const forceStartFromZero = ref(false);
const clearZipCache = ref(false);

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

const infnoLabel = computed(
  () =>
    CATALOG_FILE_DOWNLOAD_INFNO_OPTIONS.find((item) => item.value === infno.value)?.label ??
    infno.value,
);

/** 同 infno 最新游标快照 */
const currentCursor = computed(() => {
  const rows = downloadCursors.value.filter((row) => row.infno === infno.value);
  if (rows.length === 0) return undefined;
  return rows.reduce((latest, row) => {
    if (!latest) return row;
    return (row.lastSuccessAt ?? '') > (latest.lastSuccessAt ?? '') ? row : latest;
  });
});

const downloadHint = computed(() => catalogDownloadHint(infno.value));

const pipelineOverview = computed(() => buildCatalogPipelineOverview(infno.value));

const versionLoopSteps = computed(() => buildCatalogVersionLoopSteps(infno.value));

/** 本次下载/探测实际使用的 data.ver 起点 */
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

const versionPreviewHint = computed(() => {
  if (!versionPreview.value) {
    return '可选：探测仅预览阶段一 ZIP 数量，不入库；正式同步请点「开始下载」（需已签到）';
  }
  const n = pendingVersionCount.value;
  if (n === 0) {
    if (!currentCursor.value) {
      return '探测 0 包且尚无游标：尚未成功入库过。请点「开始下载」全量拉取；若仍为 0，查服务端 [catalog-dl][phase1] 日志或平台是否开通该目录';
    }
    return '探测未拉到新版本包（本地已有游标，平台可能已无增量；增量同步可取消「全量重拉」）';
  }
  const more = previewHasMore.value ? '；已达本次探测轮次上限，可加大轮次或无上限重探' : '';
  return `预计 ${n} 个 ZIP 包，起点 ver=${previewStartVer.value}${more}；探测不入库，请点「开始下载」`;
});

const startVerHint = computed(() => {
  if (forceStartFromZero.value) {
    const cacheHint = clearZipCache.value
      ? '；将清空本地 ZIP 缓存并重新 9102 下载'
      : '；已下载 ZIP 将直接读本地缓存（跳过 9102）';
    return `将忽略游标，${infno.value} 从 ver=${effectiveStartVer.value} 发起${cacheHint}`;
  }
  if (!currentCursor.value) {
    return `无历史游标，等同从 ver=${defaultCatalogStartVer(infno.value, verBranch.value)} 开始；已缓存 ZIP 不会重复 9102`;
  }
  return `沿用游标最新 local_ver，仅拉取增量包；已下载 ZIP 读本地缓存`;
});

const show1305Branch = computed(() => infno.value === '1305');

const filteredCursors = computed(() =>
  downloadCursors.value
    .filter((row) => row.infno === infno.value)
    .sort((a, b) => (b.lastSuccessAt ?? '').localeCompare(a.lastSuccessAt ?? '')),
);

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

  const previewHint =
    pendingVersionCount.value > 0
      ? `探测约 ${pendingVersionCount.value} 个 ZIP 包。`
      : '未探测或探测为 0，将按版本链拉取直至平台无新包。';

  try {
    await ElMessageBox.confirm(
      [
        `${infnoLabel.value} 将按三阶段流水线执行（拉取 → 解析 → 入库）。`,
        `${infnoLabel.value} 起点 ver：${effectiveStartVer.value}`,
        previewHint,
        '过程可能数分钟，请勿关闭页面。',
      ].join('\n'),
      '确认开始下载',
      { type: 'warning', confirmButtonText: '开始下载', cancelButtonText: '取消' },
    );
  } catch {
    return;
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

watch(contractBusNo, (value) => {
  versionPreview.value = null;
  if (value) void refreshCursors();
  else downloadCursors.value = [];
});

watch(infno, () => {
  versionPreview.value = null;
  if (contractBusNo.value) void refreshCursors();
});

watch(previewMaxRounds, () => {
  versionPreview.value = null;
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
    <div class="flex flex-col gap-4 p-2">
      <!-- 流水线概览 -->
      <ElCard shadow="never" class="pipeline-overview">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <span class="text-base font-medium">目录下载流水线</span>
          <ElTag v-if="downloadRunning" type="warning" effect="dark">下载进行中</ElTag>
          <ElTag v-else-if="downloadStatus === 'success'" type="success" effect="plain">
            上次下载成功
          </ElTag>
        </div>
        <ElSteps :active="activePipelineStep" align-center finish-status="success" class="mb-2">
          <ElStep
            v-for="(step, idx) in pipelineOverview"
            :key="idx"
            :title="step.title"
            :description="step.desc"
          />
        </ElSteps>
        <p class="m-0 text-xs leading-5 text-gray-500">{{ downloadHint }}</p>
      </ElCard>

      <ElRow :gutter="16">
        <!-- 左侧：配置与操作 -->
        <ElCol :xs="24" :lg="15">
          <ElCard shadow="never" class="h-full">
            <template #header>
              <div class="flex flex-wrap items-center justify-between gap-2">
                <span class="font-medium">下载配置</span>
                <div class="flex flex-wrap gap-2">
                  <ElButton size="small" plain :loading="signInLoading" @click="handleSignIn">
                    签到
                  </ElButton>
                  <ElButton
                    size="small"
                    plain
                    :loading="previewLoading"
                    @click="handlePreviewVersions"
                  >
                    探测版本链
                  </ElButton>
                  <ElButton size="small" plain :loading="cursorLoading" @click="refreshCursors">
                    刷新
                  </ElButton>
                </div>
              </div>
            </template>

            <ElForm label-width="112px" label-position="left">
              <ElRow :gutter="16">
                <ElCol :span="24" :md="12">
                  <ElFormItem label="医保契约" required>
                    <ElSelect
                      v-model="contractBusNo"
                      placeholder="请选择"
                      filterable
                      class="w-full"
                    >
                      <ElOption
                        v-for="item in chargeTypeOptions"
                        :key="item.busNo"
                        :label="item.busNo ? `${item.label}（${item.busNo}）` : item.label"
                        :value="item.busNo!"
                      />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="24" :md="12">
                  <ElFormItem label="下载接口">
                    <ElSelect v-model="infno" class="w-full">
                      <ElOption
                        v-for="item in CATALOG_FILE_DOWNLOAD_INFNO_OPTIONS"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
              </ElRow>

              <ElFormItem v-if="show1305Branch" label="1305 分支">
                <ElSelect v-model="verBranch" class="w-full">
                  <ElOption
                    v-for="item in CATALOG_1305_VER_BRANCH_OPTIONS"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
              </ElFormItem>

              <ElFormItem label="下载起点">
                <div class="flex w-full flex-col gap-2">
                  <ElCheckbox v-model="forceStartFromZero">
                    全量重拉（忽略游标，使用规范初始 ver）
                  </ElCheckbox>
                  <ElCheckbox v-if="forceStartFromZero" v-model="clearZipCache" class="!ml-0">
                    清空本地 ZIP 缓存后重下（否则已下载包跳过 9102）
                  </ElCheckbox>
                  <div class="rounded-md bg-gray-50 px-3 py-2 dark:bg-gray-900">
                    <div class="flex flex-wrap items-baseline gap-2">
                      <span class="text-xs text-gray-500">实际起点</span>
                      <ElTooltip :content="effectiveStartVer" placement="top">
                        <code class="text-primary font-mono text-sm">{{
                          effectiveStartVerShort
                        }}</code>
                      </ElTooltip>
                    </div>
                    <p class="mb-0 mt-1 text-xs text-gray-500">{{ startVerHint }}</p>
                  </div>
                </div>
              </ElFormItem>

              <ElFormItem label="探测轮次">
                <ElInputNumber
                  v-model="previewMaxRounds"
                  :min="1"
                  :max="9999"
                  clearable
                  placeholder="不填=无上限"
                />
                <span class="ml-2 text-xs text-gray-500"
                  >仅探测可选；正式下载无上限，停链看平台</span
                >
              </ElFormItem>

              <ElFormItem label="阶段一循环">
                <ol class="m-0 list-decimal pl-4 text-sm leading-6 text-gray-600">
                  <li v-for="step in versionLoopSteps" :key="step">{{ step }}</li>
                </ol>
              </ElFormItem>

              <ElFormItem>
                <ElButton
                  type="primary"
                  size="large"
                  :loading="downloadRunning"
                  @click="handleStartDownload"
                >
                  开始下载
                </ElButton>
              </ElFormItem>
            </ElForm>
          </ElCard>
        </ElCol>

        <!-- 右侧：状态摘要 -->
        <ElCol :xs="24" :lg="9">
          <div class="flex flex-col gap-4">
            <ElCard shadow="never">
              <template #header>
                <span class="font-medium">当前状态</span>
              </template>
              <div class="grid grid-cols-2 gap-3">
                <ElStatistic title="探测 ZIP 包数" :value="pendingVersionCount" />
                <ElStatistic
                  title="本地镜像行数"
                  :value="currentCursor?.localCatalogRowCount ?? 0"
                />
              </div>
              <p class="mb-0 mt-3 text-xs text-gray-500">{{ versionPreviewHint }}</p>
            </ElCard>

            <ElCard shadow="never">
              <template #header>
                <div class="flex items-center justify-between">
                  <span class="font-medium">最新游标</span>
                  <ElTag v-if="currentCursor" size="small" type="info">
                    {{ currentCursor.infnoLabel || infnoLabel }}
                  </ElTag>
                </div>
              </template>
              <template v-if="currentCursor">
                <ElDescriptions :column="1" border size="small">
                  <ElDescriptionsItem label="local_ver">
                    <ElTooltip :content="currentCursor.localVer || '0'" placement="top">
                      <code class="font-mono text-xs">
                        {{ currentCursor.localVerShort || currentCursor.localVer || '0' }}
                      </code>
                    </ElTooltip>
                  </ElDescriptionsItem>
                  <ElDescriptionsItem label="最近同步">
                    {{ currentCursor.syncStatusText || '—' }}
                  </ElDescriptionsItem>
                  <ElDescriptionsItem label="末次 ZIP">
                    <ElTag v-if="currentCursor.lastZipCached" type="success" size="small">
                      已缓存
                    </ElTag>
                    <ElTag v-else-if="currentCursor.lastFileQuryNo" size="small">未缓存</ElTag>
                    <span v-else class="text-gray-400">—</span>
                  </ElDescriptionsItem>
                </ElDescriptions>
              </template>
              <ElEmpty v-else description="暂无游标，默认从 0 开始" :image-size="56" />
            </ElCard>

            <ElAlert
              v-if="downloadSummary && downloadStatus === 'success' && !downloadRunning"
              type="success"
              :closable="true"
              show-icon
            >
              <template #title>
                上次完成：{{ downloadSummary.totalVersionCount }} 包 /
                {{ downloadSummary.totalUpsert }} 行入库
              </template>
              <div class="text-xs">
                游标 ver {{ downloadSummary.localVer ?? '—' }}，快照
                {{ downloadSummary.cursorSnapshotCount ?? 0 }} 条
              </div>
            </ElAlert>
          </div>
        </ElCol>
      </ElRow>

      <!-- 探测：版本链预览 -->
      <ElCard v-if="versionPreview?.versions?.length" shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center gap-2">
            <span class="font-medium">版本链预览</span>
            <ElTag size="small">起点 {{ previewStartVer }}</ElTag>
            <ElTag size="small" type="warning">{{ pendingVersionCount }} 包</ElTag>
          </div>
        </template>
        <ElTable :data="versionPreview.versions" border size="small" max-height="280">
          <ElTableColumn prop="index" label="#" width="44" align="center" />
          <ElTableColumn label="请求 ver" min-width="130">
            <template #default="{ row }">
              <ElTooltip v-if="row.requestVer" :content="row.requestVer" placement="top">
                <code class="text-xs">{{ row.requestVerShort || row.requestVer }}</code>
              </ElTooltip>
              <span v-else>—</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="col82 版本号" min-width="130">
            <template #default="{ row }">
              <ElTooltip v-if="row.nextDownloadVer" :content="row.nextDownloadVer" placement="top">
                <code class="text-xs">{{ row.nextDownloadVerShort || row.nextDownloadVer }}</code>
              </ElTooltip>
              <span v-else class="text-gray-400">链结束</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="col83 包名" min-width="120">
            <template #default="{ row }">
              <ElTooltip
                v-if="row.txtVersionName || row.nextVersion"
                :content="row.txtVersionName || row.nextVersion"
                placement="top"
              >
                <span class="text-xs">{{
                  row.txtVersionNameShort || row.nextVersionShort || '—'
                }}</span>
              </ElTooltip>
              <span v-else>—</span>
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

      <!-- 游标历史（当前 infno） -->
      <ElCard shadow="never">
        <template #header>
          <span class="font-medium">游标历史 · {{ infnoLabel }}</span>
        </template>
        <ElTable
          v-loading="cursorLoading"
          :data="filteredCursors"
          border
          stripe
          empty-text="暂无该接口的游标记录"
          max-height="320"
          size="small"
        >
          <ElTableColumn label="" width="52" align="center">
            <template #default="{ row }">
              <ElTag v-if="isLatestCursor(row)" type="primary" size="small">当前</ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="id" label="快照 ID" width="88" align="right" />
          <ElTableColumn label="local_ver" min-width="140">
            <template #default="{ row }">
              <ElTooltip :content="row.localVer || '0'" placement="top">
                <code class="font-mono text-xs">{{
                  row.localVerShort || row.localVer || '0'
                }}</code>
              </ElTooltip>
            </template>
          </ElTableColumn>
          <ElTableColumn label="镜像行数" width="88" align="right">
            <template #default="{ row }">{{ row.localCatalogRowCount ?? 0 }}</template>
          </ElTableColumn>
          <ElTableColumn label="同步时间" min-width="140">
            <template #default="{ row }">{{ row.syncStatusText || '—' }}</template>
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
    </div>

    <!-- 进度弹窗 -->
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
            <div>入库行：{{ downloadSummary.totalUpsert }}</div>
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
.pipeline-overview :deep(.el-step__title) {
  font-size: 13px;
}
.pipeline-overview :deep(.el-step__description) {
  font-size: 11px;
}
</style>
