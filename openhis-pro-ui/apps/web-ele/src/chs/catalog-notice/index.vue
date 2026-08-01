<script setup lang="ts">
import type {
  ComplianceIssueRow,
  GetApplyNoticeDocProgressResult,
  GetComplianceReportResult,
  GetConfirmNoticeScanImportProgressResult,
  GetScanNoticeCanonicalDirProgressResult,
  NoticeDocRow,
  NoticeLineRow,
  NoticeScanItem,
  NoticeTodoRow,
  NoticeTodoSummary,
  PreviewNoticeApplyResult,
  ScanNoticeCanonicalDirResult,
} from '#/chs/api/catalog-notice';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElAlert,
  ElButton,
  ElDialog,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElProgress,
  ElSelect,
  ElSpace,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import {
  closeNoticeTodoApi,
  downloadNoticePackApi,
  getApplyNoticeDocProgressApi,
  getComplianceReportApi,
  getConfirmNoticeScanImportProgressApi,
  getScanNoticeCanonicalDirProgressApi,
  loadImportJobId,
  pageNoticeDocsApi,
  pageNoticeLinesApi,
  pageNoticeTodosApi,
  previewNoticeApplyApi,
  resumeConfirmNoticeScanImportApi,
  rerunNoticeComplianceApi,
  saveImportJobId,
  startApplyNoticeDocApi,
  startApplyNoticeDocsApi,
  startConfirmNoticeScanImportApi,
  startScanNoticeCanonicalDirApi,
} from '#/chs/api/catalog-notice';
import {
  formatNoticeAction,
  formatNoticeActionSummary,
  formatNoticeApplyStatus,
  formatNoticeCategory,
  formatNoticeDocKind,
  NOTICE_CATEGORY_FILTER_OPTIONS,
  NOTICE_DOC_KIND_FILTER_OPTIONS,
} from '#/chs/constants/catalog-notice-labels';

defineOptions({ name: 'ChsCatalogNoticePage' });

const DOC_KIND_OPTIONS = NOTICE_DOC_KIND_FILTER_OPTIONS;
const CATEGORY_OPTIONS = NOTICE_CATEGORY_FILTER_OPTIONS;

const APPLY_STATUS_OPTIONS = [
  { label: '全部', value: '' },
  { label: '未应用', value: 'NOT_APPLIED' },
  { label: '部分应用', value: 'PARTIAL' },
  { label: '应用中', value: 'APPLYING' },
  { label: '已应用', value: 'APPLIED' },
  { label: '已撤销', value: 'REVOKED' },
];

const scanLoading = ref(false);
const confirmLoading = ref(false);
/** 正在单文件入库的子目录名（用于行按钮 loading） */
const importingPackDir = ref<string | null>(null);
const scanResult = ref<ScanNoticeCanonicalDirResult | null>(null);
const selectedPackDirs = ref<string[]>([]);
const scanTableRef = ref<InstanceType<typeof ElTable>>();
const scanProgress = ref<GetScanNoticeCanonicalDirProgressResult | null>(null);
const importProgress = ref<GetConfirmNoticeScanImportProgressResult | null>(null);

const scanProgressPercent = computed(() => {
  const p = scanProgress.value?.percent;
  if (typeof p === 'number' && Number.isFinite(p)) {
    return Math.min(100, Math.max(0, Math.round(p)));
  }
  return 0;
});

const scanProgressText = computed(() => {
  const p = scanProgress.value;
  if (!p) return '';
  const cur = p.currentPackDir ? `：${p.currentPackDir}` : '';
  if (p.total != null && p.total > 0) {
    return `${p.processed ?? 0}/${p.total}${cur}`;
  }
  return p.message || '扫描中…';
});

const importProgressPercent = computed(() => {
  const p = importProgress.value?.percent;
  if (typeof p === 'number' && Number.isFinite(p)) {
    return Math.min(100, Math.max(0, Math.round(p)));
  }
  return 0;
});

const importProgressText = computed(() => {
  const p = importProgress.value;
  if (!p) return '';
  const cur = p.currentPackDir ? `：${p.currentPackDir}` : '';
  if (p.total != null && p.total > 0) {
    return `${p.processed ?? 0}/${p.total}${cur}`;
  }
  return p.message || '入库中…';
});

const importResumable = computed(() => !!importProgress.value?.resumable);

const importProgressBarStatus = computed(() => {
  const status = importProgress.value?.status;
  if (status === 'FAILED') return 'exception';
  if (status === 'SUCCEEDED') return 'success';
  return undefined;
});

const docKeyword = ref('');
const docKind = ref('');
const docCategory = ref('');
const docApplyStatus = ref('');
const docPage = ref(1);
const docSize = ref(20);
const docTotal = ref(0);
const docRows = ref<NoticeDocRow[]>([]);
const docLoading = ref(false);
const docTableRef = ref<InstanceType<typeof ElTable>>();
const selectedDocs = ref<NoticeDocRow[]>([]);

const lineDrawerVisible = ref(false);
const currentDoc = ref<NoticeDocRow | null>(null);
const linePage = ref(1);
const lineSize = ref(20);
const lineTotal = ref(0);
const lineRows = ref<NoticeLineRow[]>([]);
const lineLoading = ref(false);
const downloadLoading = ref(false);

const previewVisible = ref(false);
const previewLoading = ref(false);
const previewDoc = ref<NoticeDocRow | null>(null);
const previewResult = ref<PreviewNoticeApplyResult | null>(null);
const applyLoadingDocId = ref<number | string | null>(null);
const applyProgress = ref<GetApplyNoticeDocProgressResult | null>(null);

const applyProgressPercent = computed(() => {
  const p = applyProgress.value?.percent;
  if (typeof p === 'number' && Number.isFinite(p)) {
    return Math.min(100, Math.max(0, Math.round(p)));
  }
  return 0;
});

const applyProgressText = computed(() => {
  const p = applyProgress.value;
  if (!p) return '';
  const docPart =
    p.docTotal != null && p.docTotal > 1 ? `文件 ${p.docProcessed ?? 0}/${p.docTotal} · ` : '';
  if (p.total != null && p.total > 0) {
    return `${docPart}${p.processed ?? 0}/${p.total} 行`;
  }
  return p.message || '应用中…';
});

const canBatchApply = computed(() => {
  if (applyLoadingDocId.value != null) return false;
  return selectedDocs.value.some((row) => canApplyDocRow(row));
});

/** 主工作区：扫描 / 已入库 / 催办合规，避免三块纵向堆出整页滚动条 */
const mainTab = ref<'scan' | 'docs' | 'ops'>('scan');
const bottomTab = ref<'todos' | 'compliance'>('todos');

const todoPage = ref(1);
const todoSize = ref(20);
const todoTotal = ref(0);
const todoRows = ref<NoticeTodoRow[]>([]);
const todoSummary = ref<NoticeTodoSummary>({});
const todoLoading = ref(false);
const closeTodoVisible = ref(false);
const closeTodoRow = ref<NoticeTodoRow | null>(null);
const closeReason = ref('');
const closeTodoLoading = ref(false);

const complianceLoading = ref(false);
const complianceRerunLoading = ref(false);
const complianceReport = ref<GetComplianceReportResult | null>(null);

const scanSummary = computed(() => {
  const r = scanResult.value;
  if (!r) return '';
  return `NEW ${r.newCount ?? 0} / 重复 ${r.duplicateCount ?? 0} / 失败 ${r.failedCount ?? 0}`;
});

const canConfirm = computed(() => {
  if (!scanResult.value?.scanToken) return false;
  if (selectedPackDirs.value.length > 0) return true;
  return (scanResult.value.newCount ?? 0) > 0;
});

const complianceIssues = computed<ComplianceIssueRow[]>(() =>
  Array.isArray(complianceReport.value?.issues) ? complianceReport.value!.issues! : [],
);

function scanStatusTag(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'NEW') return 'success';
  if (status === 'DUPLICATE') return 'warning';
  if (status === 'FAILED') return 'danger';
  return 'info';
}

function scanStatusLabel(status?: string) {
  if (status === 'NEW') return '待入库';
  if (status === 'DUPLICATE') return '已存在';
  if (status === 'FAILED') return '失败';
  return status || '—';
}

function canApplyDocRow(row: NoticeDocRow) {
  if (row.id == null) return false;
  const status = row.applyStatus || 'NOT_APPLIED';
  return status === 'NOT_APPLIED' || status === 'PARTIAL' || status === 'APPLYING';
}

function canApplyDoc(row: NoticeDocRow) {
  if (applyLoadingDocId.value != null) return false;
  return canApplyDocRow(row);
}

function selectableDocRow(row: NoticeDocRow) {
  return canApplyDocRow(row);
}

function handleDocSelectionChange(rows: NoticeDocRow[]) {
  selectedDocs.value = Array.isArray(rows) ? rows : [];
}

function sleep(ms: number) {
  return new Promise<void>((resolve) => {
    setTimeout(resolve, ms);
  });
}

/** 进度轮询间隔：过密会触发全局限流 429（默认约 100 次/分钟）。 */
const PROGRESS_POLL_INTERVAL_MS = 2000;

function isTooManyRequestsError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false;
  const anyErr = error as {
    status?: number;
    response?: { status?: number };
    message?: string;
  };
  if (anyErr.status === 429 || anyErr.response?.status === 429) return true;
  const msg = typeof anyErr.message === 'string' ? anyErr.message : '';
  return msg.includes('429') || /too many requests/i.test(msg);
}

async function pollScanUntilDone(jobId: string): Promise<ScanNoticeCanonicalDirResult> {
  // 最多约 30 分钟（与后端会话 TTL 同量级）
  const maxRounds = 900;
  for (let i = 0; i < maxRounds; i++) {
    try {
      const progress = await getScanNoticeCanonicalDirProgressApi(jobId);
      scanProgress.value = progress;
      const status = progress.status || '';
      if (status === 'SUCCEEDED') {
        if (!progress.result?.scanToken) {
          throw new Error('扫描完成但未返回结果，请重试');
        }
        return progress.result;
      }
      if (status === 'FAILED') {
        throw new Error(progress.message || '扫描失败');
      }
    } catch (error) {
      if (isTooManyRequestsError(error)) {
        await sleep(PROGRESS_POLL_INTERVAL_MS * 2);
        continue;
      }
      throw error;
    }
    await sleep(PROGRESS_POLL_INTERVAL_MS);
  }
  throw new Error('扫描超时，请稍后重试');
}

async function handleScan() {
  scanLoading.value = true;
  selectedPackDirs.value = [];
  scanTableRef.value?.clearSelection();
  scanProgress.value = {
    status: 'PENDING',
    percent: 0,
    processed: 0,
    total: 0,
    message: '启动扫描…',
  };
  try {
    const started = await startScanNoticeCanonicalDirApi({});
    const jobId = started.jobId;
    if (!jobId) {
      throw new Error('未返回扫描任务 ID');
    }
    scanResult.value = await pollScanUntilDone(String(jobId));
    scanProgress.value = {
      ...scanProgress.value,
      status: 'SUCCEEDED',
      percent: 100,
      message: '扫描完成',
      currentPackDir: undefined,
    };
    ElMessage.success(`扫描完成：${scanSummary.value}`);
  } catch (error) {
    const message = error instanceof Error ? error.message : '扫描失败';
    ElMessage.error(message);
    if (scanProgress.value) {
      scanProgress.value = {
        ...scanProgress.value,
        status: 'FAILED',
        message,
      };
    }
  } finally {
    scanLoading.value = false;
  }
}

function handleScanSelectionChange(rows: NoticeScanItem[]) {
  selectedPackDirs.value = rows
    .filter((row) => row.status === 'NEW' && row.packDir)
    .map((row) => String(row.packDir));
}

function selectableScanRow(row: NoticeScanItem) {
  return row.status === 'NEW';
}

async function pollImportUntilSettled(
  jobId: string,
): Promise<GetConfirmNoticeScanImportProgressResult> {
  const maxRounds = 1800;
  for (let i = 0; i < maxRounds; i++) {
    try {
      const progress = await getConfirmNoticeScanImportProgressApi(jobId);
      importProgress.value = progress;
      const status = progress.status || '';
      if (status === 'SUCCEEDED') {
        return progress;
      }
      if (status === 'FAILED') {
        // 可续跑：停在此处交给用户点「继续入库」；不可续跑则抛错
        if (progress.resumable) {
          return progress;
        }
        throw new Error(firstImportFailureMessage(progress) || progress.message || '入库失败');
      }
    } catch (error) {
      if (isTooManyRequestsError(error)) {
        await sleep(PROGRESS_POLL_INTERVAL_MS * 2);
        continue;
      }
      throw error;
    }
    await sleep(PROGRESS_POLL_INTERVAL_MS);
  }
  throw new Error('入库超时，若任务仍在跑可刷新页面后点「继续入库」');
}

function firstImportFailureMessage(progress: GetConfirmNoticeScanImportProgressResult) {
  for (const row of progress.results ?? []) {
    if (row.status === 'FAILED' && row.message) {
      return row.message;
    }
  }
  return undefined;
}

async function finishImportSuccess(
  progress: GetConfirmNoticeScanImportProgressResult,
  mode: 'batch' | 'single' = 'batch',
) {
  saveImportJobId(null);
  const imported = progress.importedCount ?? 0;
  const skipped = progress.skippedCount ?? 0;
  const failed = progress.failedCount ?? 0;
  if (imported + skipped === 0 && failed > 0) {
    throw new Error(firstImportFailureMessage(progress) || progress.message || '入库失败');
  }
  if (failed > 0) {
    ElMessage.warning(`入库 ${imported}，跳过 ${skipped}，失败 ${failed}`);
  } else {
    ElMessage.success(`入库 ${imported}，跳过 ${skipped}`);
  }
  await loadDocs();
  await loadTodos();
  if (mode === 'single') {
    markScanItemsImported(progress);
    return;
  }
  mainTab.value = 'docs';
  scanResult.value = null;
}

/** 单文件入库后就地改为 DUPLICATE，避免整表重扫 */
function markScanItemsImported(progress: GetConfirmNoticeScanImportProgressResult) {
  const result = scanResult.value;
  if (!result?.items?.length) return;

  const docIdByDir = new Map<string, number | string>();
  const importedDirs = new Set<string>();
  for (const row of progress.results ?? []) {
    if (!row.packDir) continue;
    if (row.status === 'IMPORTED' || row.status === 'SKIPPED') {
      importedDirs.add(String(row.packDir));
      if (row.docId != null) {
        docIdByDir.set(String(row.packDir), row.docId);
      }
    }
  }
  if (importedDirs.size === 0) return;

  const items = result.items.map((item) => {
    const dir = item.packDir ? String(item.packDir) : '';
    if (!dir || !importedDirs.has(dir)) {
      return item;
    }
    return {
      ...item,
      status: 'DUPLICATE' as const,
      existingDocId: docIdByDir.get(dir) ?? item.existingDocId,
      message: rowMessageForImported(item.message),
    };
  });

  let newCount = 0;
  let duplicateCount = 0;
  let failedCount = 0;
  for (const item of items) {
    if (item.status === 'NEW') newCount += 1;
    else if (item.status === 'DUPLICATE') duplicateCount += 1;
    else if (item.status === 'FAILED') failedCount += 1;
  }

  scanResult.value = {
    ...result,
    items,
    newCount,
    duplicateCount,
    failedCount,
  };
  selectedPackDirs.value = selectedPackDirs.value.filter((d) => !importedDirs.has(d));
}

function rowMessageForImported(prev?: string) {
  if (prev && prev.includes('已入库')) return prev;
  return '已入库';
}

async function runImportJob(jobId: string, mode: 'batch' | 'single' = 'batch') {
  saveImportJobId(jobId);
  confirmLoading.value = true;
  try {
    const progress = await pollImportUntilSettled(jobId);
    if (progress.status === 'SUCCEEDED') {
      await finishImportSuccess(progress, mode);
      return;
    }
    ElMessage.warning(
      progress.message || `入库未完成，剩余 ${progress.remainingCount ?? 0} 个包，可点「继续入库」`,
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : '入库失败';
    ElMessage.error(message);
  } finally {
    confirmLoading.value = false;
    importingPackDir.value = null;
  }
}

async function startImportJob(packDirs: string[] | undefined, mode: 'batch' | 'single') {
  const token = scanResult.value?.scanToken;
  if (!token) {
    ElMessage.warning('请先重新扫描');
    return;
  }
  if (confirmLoading.value) {
    ElMessage.warning('已有入库任务进行中，请稍候或点「继续入库」');
    return;
  }

  importProgress.value = {
    status: 'PENDING',
    percent: 0,
    processed: 0,
    total: 0,
    message: mode === 'single' ? '启动单文件入库…' : '启动入库…',
  };
  try {
    const started = await startConfirmNoticeScanImportApi({
      scanToken: token,
      packDirs,
    });
    const jobId = started.jobId;
    if (!jobId) {
      throw new Error('未返回入库任务 ID');
    }
    await runImportJob(String(jobId), mode);
  } catch (error) {
    const message = error instanceof Error ? error.message : '入库失败';
    ElMessage.error(message);
    confirmLoading.value = false;
    importingPackDir.value = null;
  }
}

async function handleConfirmImport() {
  const packDirs = [...selectedPackDirs.value];
  const countHint = packDirs.length > 0 ? `勾选的 ${packDirs.length} 个 NEW 包` : '全部 NEW 包';
  try {
    await ElMessageBox.confirm(`确认将${countHint}入库？此操作写入数据库。`, '确认入库', {
      type: 'warning',
      confirmButtonText: '确认入库',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  await startImportJob(packDirs.length > 0 ? packDirs : undefined, 'batch');
}

/** 单个标准包入库 */
async function handleImportOne(row: NoticeScanItem) {
  if (row.status !== 'NEW' || !row.packDir) {
    ElMessage.warning('仅待入库（NEW）可单独入库');
    return;
  }
  const packDir = String(row.packDir);
  const title = row.title || packDir;
  try {
    await ElMessageBox.confirm(`确认将「${title}」单独入库？`, '单文件入库', {
      type: 'warning',
      confirmButtonText: '入库',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  importingPackDir.value = packDir;
  await startImportJob([packDir], 'single');
}

function canImportOne(row: NoticeScanItem) {
  return row.status === 'NEW' && !!row.packDir && !scanLoading.value && !confirmLoading.value;
}

async function handleResumeImport() {
  const jobId = importProgress.value?.jobId || loadImportJobId();
  if (!jobId) {
    ElMessage.warning('没有可继续的入库任务，请重新扫描后确认入库');
    return;
  }
  importProgress.value = {
    ...(importProgress.value || {}),
    jobId,
    status: 'PENDING',
    message: '续跑排队中…',
  };
  try {
    const started = await resumeConfirmNoticeScanImportApi(String(jobId));
    await runImportJob(String(started.jobId || jobId), 'batch');
  } catch (error) {
    const message = error instanceof Error ? error.message : '续跑失败';
    ElMessage.error(message);
    // 任务已丢：清本地标记，引导重新扫描（已入库包不会重复插入）
    if (message.includes('不存在') || message.includes('过期')) {
      saveImportJobId(null);
      importProgress.value = null;
    }
  }
}

async function resumeImportJobIfAny() {
  const jobId = loadImportJobId();
  if (!jobId) return;
  try {
    const progress = await getConfirmNoticeScanImportProgressApi(jobId);
    importProgress.value = progress;
    if (progress.status === 'SUCCEEDED') {
      await finishImportSuccess(progress);
      return;
    }
    if (progress.status === 'RUNNING' || progress.status === 'PENDING') {
      mainTab.value = 'scan';
      ElMessage.info('检测到未完成的入库任务，继续跟踪进度…');
      await runImportJob(jobId, 'batch');
      return;
    }
    if (progress.resumable) {
      mainTab.value = 'scan';
      ElMessage.warning(
        progress.message || `入库未完成，剩余 ${progress.remainingCount ?? 0} 个，可点「继续入库」`,
      );
    } else {
      saveImportJobId(null);
    }
  } catch {
    // 服务端无此任务：清掉本地 ID，避免反复打扰
    saveImportJobId(null);
  }
}

async function loadDocs() {
  docLoading.value = true;
  try {
    const page = await pageNoticeDocsApi({
      page: docPage.value,
      size: docSize.value,
      keyword: docKeyword.value || undefined,
      docKind: docKind.value || undefined,
      category: docCategory.value || undefined,
      applyStatus: docApplyStatus.value || undefined,
    });
    docTotal.value = page.total;
    docRows.value = page.records;
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载文件列表失败';
    ElMessage.error(message);
  } finally {
    docLoading.value = false;
  }
}

function handleDocSearch() {
  docPage.value = 1;
  void loadDocs();
}

function handleDocPageChange(page: number) {
  docPage.value = page;
  void loadDocs();
}

function handleDocSizeChange(size: number) {
  docSize.value = size;
  docPage.value = 1;
  void loadDocs();
}

async function openLineDrawer(row: NoticeDocRow) {
  currentDoc.value = row;
  lineDrawerVisible.value = true;
  linePage.value = 1;
  await loadLines();
}

async function loadLines() {
  const docId = currentDoc.value?.id;
  if (docId == null) return;
  lineLoading.value = true;
  try {
    const page = await pageNoticeLinesApi({
      docId,
      page: linePage.value,
      size: lineSize.value,
    });
    lineTotal.value = page.total;
    lineRows.value = page.records;
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载明细失败';
    ElMessage.error(message);
  } finally {
    lineLoading.value = false;
  }
}

function handleLinePageChange(page: number) {
  linePage.value = page;
  void loadLines();
}

function handleLineSizeChange(size: number) {
  lineSize.value = size;
  linePage.value = 1;
  void loadLines();
}

async function handleDownloadPack(row: NoticeDocRow) {
  if (row.id == null) return;
  downloadLoading.value = true;
  try {
    const pack = await downloadNoticePackApi(row.id);
    if (!pack.contentBase64) {
      ElMessage.error('标准包内容为空');
      return;
    }
    const binary = atob(pack.contentBase64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    const blob = new Blob([bytes], { type: 'application/zip' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = pack.fileName || `notice-${row.id}.zip`;
    a.click();
    URL.revokeObjectURL(url);
  } catch (error) {
    const message = error instanceof Error ? error.message : '下载失败';
    ElMessage.error(message);
  } finally {
    downloadLoading.value = false;
  }
}

async function handlePreviewApply(row: NoticeDocRow) {
  if (row.id == null) return;
  previewDoc.value = row;
  previewResult.value = null;
  previewVisible.value = true;
  previewLoading.value = true;
  try {
    previewResult.value = await previewNoticeApplyApi({ docId: row.id });
  } catch (error) {
    const message = error instanceof Error ? error.message : '预览失败';
    ElMessage.error(message);
    previewVisible.value = false;
  } finally {
    previewLoading.value = false;
  }
}

async function pollApplyUntilSettled(jobId: string): Promise<GetApplyNoticeDocProgressResult> {
  const maxRounds = 1800;
  for (let i = 0; i < maxRounds; i++) {
    try {
      const progress = await getApplyNoticeDocProgressApi(jobId);
      applyProgress.value = progress;
      const status = progress.status || '';
      if (status === 'SUCCEEDED') {
        return progress;
      }
      if (status === 'FAILED') {
        throw new Error(progress.message || '应用失败');
      }
    } catch (error) {
      if (isTooManyRequestsError(error)) {
        await sleep(PROGRESS_POLL_INTERVAL_MS * 2);
        continue;
      }
      throw error;
    }
    await sleep(PROGRESS_POLL_INTERVAL_MS);
  }
  throw new Error('应用超时，请刷新后查看文件是否已变为「已应用」');
}

/** 请求层失败时常抛 response.data（非 Error），需抠 message，避免只显示兜底「应用失败」。 */
function resolveNoticeUiError(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  if (error && typeof error === 'object') {
    const msg = (error as { message?: unknown }).message;
    if (typeof msg === 'string' && msg.trim()) {
      return msg;
    }
  }
  if (typeof error === 'string' && error.trim()) {
    return error;
  }
  return fallback;
}

async function handleApplyDoc(row: NoticeDocRow) {
  if (row.id == null) return;
  try {
    await ElMessageBox.confirm(
      `确认应用文件「${row.title || row.id}」？将分批写入 overlay / 回写 / 未收费同步。中断后可对「部分应用」续跑。`,
      '确认应用',
      {
        type: 'warning',
        confirmButtonText: '确认应用',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return;
  }

  applyLoadingDocId.value = row.id;
  applyProgress.value = {
    status: 'PENDING',
    percent: 0,
    processed: 0,
    total: Number(row.lineCount) || 0,
    message: '启动应用…',
  };
  try {
    const started = await startApplyNoticeDocApi({ docId: row.id, confirm: true });
    const jobId = started.jobId;
    if (!jobId) {
      throw new Error('未返回应用任务 ID');
    }
    const progress = await pollApplyUntilSettled(String(jobId));
    ElMessage.success(
      `已应用 ${progress.appliedLineCount ?? 0} 行，失败 ${progress.failedLineCount ?? 0}，回写 ${progress.writebackCount ?? 0}，未收费同步 ${progress.syncedUnchargedCount ?? 0}`,
    );
    previewVisible.value = false;
    selectedDocs.value = [];
    docTableRef.value?.clearSelection();
    await loadDocs();
    await loadTodos();
    await loadCompliance();
  } catch (error) {
    ElMessage.error(resolveNoticeUiError(error, '应用失败'));
    await loadDocs();
  } finally {
    applyLoadingDocId.value = null;
    applyProgress.value = null;
  }
}

async function handleBatchApplyDocs() {
  const targets = selectedDocs.value.filter((row) => canApplyDocRow(row));
  if (targets.length === 0) {
    ElMessage.warning('请勾选未应用或部分应用的文件');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认批量应用已勾选的 ${targets.length} 个文件？将按文件串行、文件内分批落库。`,
      '批量应用',
      {
        type: 'warning',
        confirmButtonText: '确认应用',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return;
  }

  const docIds = targets.map((row) => row.id!).filter((id) => id != null);
  applyLoadingDocId.value = '__batch__';
  applyProgress.value = {
    status: 'PENDING',
    percent: 0,
    processed: 0,
    total: targets.reduce((sum, row) => sum + (Number(row.lineCount) || 0), 0),
    docTotal: targets.length,
    docProcessed: 0,
    message: '启动批量应用…',
  };
  try {
    const started = await startApplyNoticeDocsApi({ docIds, confirm: true });
    const jobId = started.jobId;
    if (!jobId) {
      throw new Error('未返回应用任务 ID');
    }
    const progress = await pollApplyUntilSettled(String(jobId));
    ElMessage.success(
      `批量应用完成：${progress.appliedLineCount ?? 0} 行，失败 ${progress.failedLineCount ?? 0}，回写 ${progress.writebackCount ?? 0}`,
    );
    selectedDocs.value = [];
    docTableRef.value?.clearSelection();
    await loadDocs();
    await loadTodos();
    await loadCompliance();
  } catch (error) {
    ElMessage.error(resolveNoticeUiError(error, '批量应用失败'));
    await loadDocs();
  } finally {
    applyLoadingDocId.value = null;
    applyProgress.value = null;
  }
}

async function loadTodos() {
  todoLoading.value = true;
  try {
    const page = await pageNoticeTodosApi({
      page: todoPage.value,
      size: todoSize.value,
      status: 'OPEN',
    });
    todoSummary.value = page.summary ?? {};
    todoTotal.value = page.total;
    todoRows.value = page.records;
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载待办失败';
    ElMessage.error(message);
  } finally {
    todoLoading.value = false;
  }
}

function handleTodoPageChange(page: number) {
  todoPage.value = page;
  void loadTodos();
}

function handleTodoSizeChange(size: number) {
  todoSize.value = size;
  todoPage.value = 1;
  void loadTodos();
}

function openCloseTodo(row: NoticeTodoRow) {
  closeTodoRow.value = row;
  closeReason.value = '';
  closeTodoVisible.value = true;
}

async function handleCloseTodo() {
  const todoId = closeTodoRow.value?.id;
  if (todoId == null) return;
  const reason = closeReason.value.trim();
  if (!reason) {
    ElMessage.warning('请填写关闭原因');
    return;
  }
  closeTodoLoading.value = true;
  try {
    const result = await closeNoticeTodoApi({ todoId, closeReason: reason });
    ElMessage.success(`待办已关闭（${result.status || 'CLOSED'}）`);
    closeTodoVisible.value = false;
    await loadTodos();
  } catch (error) {
    const message = error instanceof Error ? error.message : '关闭待办失败';
    ElMessage.error(message);
  } finally {
    closeTodoLoading.value = false;
  }
}

async function loadCompliance() {
  complianceLoading.value = true;
  try {
    complianceReport.value = await getComplianceReportApi({});
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载合规报告失败';
    ElMessage.error(message);
  } finally {
    complianceLoading.value = false;
  }
}

async function handleRerunCompliance() {
  try {
    await ElMessageBox.confirm(
      '确认重新执行合规校验？将覆盖最新报告并重算 OPEN 待办。',
      '重验合规',
      {
        type: 'warning',
        confirmButtonText: '开始重验',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return;
  }

  complianceRerunLoading.value = true;
  try {
    const result = await rerunNoticeComplianceApi({});
    ElMessage.success(`重验完成：报告 ${result.reportId ?? '—'}，问题数 ${result.issueCount ?? 0}`);
    await loadCompliance();
    await loadTodos();
  } catch (error) {
    const message = error instanceof Error ? error.message : '重验失败';
    ElMessage.error(message);
  } finally {
    complianceRerunLoading.value = false;
  }
}

function handleBottomTabChange(name: string | number) {
  if (name === 'todos') {
    void loadTodos();
  } else if (name === 'compliance') {
    void loadCompliance();
  }
}

function handleMainTabChange(name: string | number) {
  if (name === 'docs') {
    void loadDocs();
  } else if (name === 'ops') {
    if (bottomTab.value === 'compliance') {
      void loadCompliance();
    } else {
      void loadTodos();
    }
  }
}

onMounted(() => {
  void loadDocs();
  void loadTodos();
  // 先接续未完成入库，再默认扫一次
  void (async () => {
    await resumeImportJobIfAny();
    if (!confirmLoading.value) {
      void handleScan();
    }
  })();
});
</script>

<template>
  <Page
    title="医保文件管理"
    auto-content-height
    content-class="chs-notice-page !overflow-hidden flex h-full min-h-0 flex-col !p-2 !pb-2"
  >
    <ElAlert
      class="shrink-0"
      type="info"
      :closable="false"
      show-icon
      :title="
        scanResult?.scanRoot
          ? `扫描根：${scanResult.scanRoot}`
          : '请配置 openhis.chs.notice.canonical-root 后在「扫描入库」中重新扫描'
      "
    />

    <ElTabs
      v-model="mainTab"
      class="chs-notice-tabs min-h-0 flex-1"
      @tab-change="handleMainTabChange"
    >
      <ElTabPane label="扫描入库" name="scan" lazy>
        <div class="chs-notice-panel">
          <div class="flex shrink-0 flex-wrap items-center justify-between gap-2">
            <span v-if="scanResult" class="text-muted-foreground text-sm">{{ scanSummary }}</span>
            <span v-else class="text-muted-foreground text-sm"
              >扫描标准落地包 → 行内「入库」或勾选后批量确认</span
            >
            <ElSpace>
              <ElButton
                type="primary"
                :loading="scanLoading"
                :disabled="confirmLoading"
                @click="handleScan"
              >
                重新扫描
              </ElButton>
              <ElButton
                type="success"
                :loading="confirmLoading"
                :disabled="!canConfirm || scanLoading || confirmLoading"
                @click="handleConfirmImport"
              >
                确认入库
              </ElButton>
              <ElButton
                v-if="importResumable"
                type="warning"
                :loading="confirmLoading"
                @click="handleResumeImport"
              >
                继续入库
              </ElButton>
            </ElSpace>
          </div>

          <div
            v-if="scanLoading || (scanProgress && scanProgress.status === 'FAILED')"
            class="shrink-0 rounded border border-[var(--el-border-color)] px-3 py-2"
          >
            <div class="mb-1 flex flex-wrap items-center justify-between gap-2 text-sm">
              <span>{{ scanProgress?.message || '扫描中…' }}</span>
              <span class="text-muted-foreground">{{ scanProgressText }}</span>
            </div>
            <ElProgress
              :percentage="scanProgressPercent"
              :status="scanProgress?.status === 'FAILED' ? 'exception' : undefined"
              :stroke-width="14"
              :striped="scanLoading"
              striped-flow
            />
          </div>

          <div
            v-if="
              confirmLoading ||
              importResumable ||
              (importProgress &&
                (importProgress.status === 'RUNNING' || importProgress.status === 'FAILED'))
            "
            class="shrink-0 rounded border border-[var(--el-border-color)] px-3 py-2"
          >
            <div class="mb-1 flex flex-wrap items-center justify-between gap-2 text-sm">
              <span>
                {{ importProgress?.message || '入库中…' }}
                <template v-if="importProgress">
                  （成功 {{ importProgress.importedCount ?? 0 }} / 跳过
                  {{ importProgress.skippedCount ?? 0 }} / 失败
                  {{ importProgress.failedCount ?? 0 }}）
                </template>
              </span>
              <span class="text-muted-foreground">{{ importProgressText }}</span>
            </div>
            <ElProgress
              :percentage="importProgressPercent"
              :status="importProgressBarStatus"
              :stroke-width="14"
              :striped="confirmLoading"
              striped-flow
            />
          </div>

          <div class="chs-notice-panel__table">
            <ElTable
              ref="scanTableRef"
              v-loading="scanLoading"
              height="100%"
              :data="scanResult?.items ?? []"
              border
              stripe
              empty-text="尚未扫描；请点击「重新扫描」"
              row-key="packDir"
              @selection-change="handleScanSelectionChange"
            >
              <ElTableColumn type="selection" width="48" :selectable="selectableScanRow" />
              <ElTableColumn prop="packDir" label="子目录" min-width="180" show-overflow-tooltip />
              <ElTableColumn prop="title" label="标题" min-width="160" show-overflow-tooltip />
              <ElTableColumn label="种类" width="110">
                <template #default="{ row }">
                  {{ formatNoticeDocKind(row.docKind) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="品类" width="90">
                <template #default="{ row }">
                  {{ formatNoticeCategory(row.category) }}
                </template>
              </ElTableColumn>
              <ElTableColumn prop="noticeDate" label="通知日" width="110" />
              <ElTableColumn prop="lineCount" label="行数" width="70" />
              <ElTableColumn label="动作" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ formatNoticeActionSummary(row) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="状态" width="100">
                <template #default="{ row }">
                  <ElTag :type="scanStatusTag(row.status)" size="small">
                    {{ scanStatusLabel(row.status) }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="message" label="说明" min-width="140" show-overflow-tooltip />
              <ElTableColumn label="操作" width="90" fixed="right">
                <template #default="{ row }">
                  <ElButton
                    link
                    type="success"
                    :loading="importingPackDir === row.packDir"
                    :disabled="!canImportOne(row)"
                    @click.stop="handleImportOne(row)"
                  >
                    入库
                  </ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </div>
        </div>
      </ElTabPane>

      <ElTabPane label="已入库文件" name="docs">
        <div class="chs-notice-panel">
          <ElForm class="chs-notice-query shrink-0" inline @submit.prevent="handleDocSearch">
            <ElFormItem label="关键字">
              <ElInput
                v-model="docKeyword"
                clearable
                placeholder="标题/文件名"
                style="width: 160px"
                @keyup.enter="handleDocSearch"
              />
            </ElFormItem>
            <ElFormItem label="种类">
              <ElSelect v-model="docKind" clearable style="width: 130px">
                <ElOption
                  v-for="opt in DOC_KIND_OPTIONS"
                  :key="opt.value || 'all'"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="品类">
              <ElSelect v-model="docCategory" clearable style="width: 120px">
                <ElOption
                  v-for="opt in CATEGORY_OPTIONS"
                  :key="opt.value || 'all'"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="应用状态">
              <ElSelect v-model="docApplyStatus" clearable style="width: 120px">
                <ElOption
                  v-for="opt in APPLY_STATUS_OPTIONS"
                  :key="opt.value || 'all'"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" @click="handleDocSearch">查询</ElButton>
              <ElButton
                type="warning"
                class="ml-2"
                :disabled="!canBatchApply"
                :loading="applyLoadingDocId === '__batch__'"
                @click="handleBatchApplyDocs"
              >
                批量应用
              </ElButton>
            </ElFormItem>
          </ElForm>

          <div
            v-if="applyLoadingDocId != null || (applyProgress && applyProgress.status === 'FAILED')"
            class="mb-2 shrink-0 rounded border border-[var(--el-border-color)] px-3 py-2"
          >
            <div class="mb-1 flex flex-wrap items-center justify-between gap-2 text-sm">
              <span>{{ applyProgress?.message || '应用中…' }}</span>
              <span class="text-muted-foreground">{{ applyProgressText }}</span>
            </div>
            <ElProgress
              :percentage="applyProgressPercent"
              :status="applyProgress?.status === 'FAILED' ? 'exception' : undefined"
              :stroke-width="14"
              :striped="applyLoadingDocId != null"
              striped-flow
            />
          </div>

          <div class="chs-notice-panel__table">
            <ElTable
              ref="docTableRef"
              v-loading="docLoading"
              height="100%"
              :data="docRows"
              border
              stripe
              empty-text="暂无已入库文件"
              @selection-change="handleDocSelectionChange"
              @row-click="openLineDrawer"
            >
              <ElTableColumn type="selection" width="48" :selectable="selectableDocRow" />
              <ElTableColumn prop="id" label="ID" width="80" />
              <ElTableColumn prop="title" label="标题" min-width="160" show-overflow-tooltip />
              <ElTableColumn
                prop="sourceFilename"
                label="原文件名"
                min-width="140"
                show-overflow-tooltip
              />
              <ElTableColumn label="种类" width="110">
                <template #default="{ row }">
                  {{ formatNoticeDocKind(row.docKind) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="品类" width="90">
                <template #default="{ row }">
                  {{ formatNoticeCategory(row.category) }}
                </template>
              </ElTableColumn>
              <ElTableColumn prop="noticeDate" label="通知日" width="110" />
              <ElTableColumn prop="lineCount" label="行数" width="70" />
              <ElTableColumn prop="parseStatus" label="解析" width="90" />
              <ElTableColumn label="应用" width="110">
                <template #default="{ row }">
                  {{ formatNoticeApplyStatus(row.applyStatus) }}
                </template>
              </ElTableColumn>
              <ElTableColumn prop="importedAt" label="入库时间" width="170" />
              <ElTableColumn label="操作" width="220" fixed="right">
                <template #default="{ row }">
                  <ElButton
                    link
                    type="primary"
                    :disabled="!canApplyDoc(row)"
                    @click.stop="handlePreviewApply(row)"
                  >
                    预览应用
                  </ElButton>
                  <ElButton
                    link
                    type="warning"
                    :loading="applyLoadingDocId === row.id"
                    :disabled="!canApplyDoc(row)"
                    @click.stop="handleApplyDoc(row)"
                  >
                    应用
                  </ElButton>
                  <ElButton
                    link
                    type="primary"
                    :loading="downloadLoading"
                    @click.stop="handleDownloadPack(row)"
                  >
                    下载
                  </ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </div>
          <div class="flex shrink-0 justify-end">
            <ElPagination
              v-model:current-page="docPage"
              v-model:page-size="docSize"
              :total="docTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @current-change="handleDocPageChange"
              @size-change="handleDocSizeChange"
            />
          </div>
        </div>
      </ElTabPane>

      <ElTabPane label="催办与合规" name="ops">
        <ElTabs
          v-model="bottomTab"
          class="chs-notice-tabs chs-notice-tabs--nested h-full min-h-0"
          @tab-change="handleBottomTabChange"
        >
          <ElTabPane label="待办" name="todos">
            <div class="chs-notice-panel">
              <div class="flex shrink-0 flex-wrap items-center gap-4 text-sm">
                <span>
                  停用 OPEN：
                  <strong>{{ todoSummary.stopOpenCount ?? 0 }}</strong>
                </span>
                <span>
                  支付类别 OPEN：
                  <strong>{{ todoSummary.payClassOpenCount ?? 0 }}</strong>
                </span>
                <ElButton size="small" @click="loadTodos">刷新</ElButton>
              </div>
              <div class="chs-notice-panel__table">
                <ElTable
                  v-loading="todoLoading"
                  height="100%"
                  :data="todoRows"
                  border
                  stripe
                  empty-text="暂无 OPEN 待办"
                >
                  <ElTableColumn prop="id" label="ID" width="80" />
                  <ElTableColumn prop="todoType" label="类型" width="120" />
                  <ElTableColumn
                    prop="hilistCode"
                    label="目录编码"
                    min-width="140"
                    show-overflow-tooltip
                  />
                  <ElTableColumn
                    prop="projectCode"
                    label="项目代码"
                    min-width="120"
                    show-overflow-tooltip
                  />
                  <ElTableColumn
                    prop="productName"
                    label="名称"
                    min-width="160"
                    show-overflow-tooltip
                  />
                  <ElTableColumn prop="effectiveDate" label="生效日" width="110" />
                  <ElTableColumn prop="docId" label="文件ID" width="90" />
                  <ElTableColumn prop="status" label="状态" width="90" />
                  <ElTableColumn label="操作" width="100" fixed="right">
                    <template #default="{ row }">
                      <ElButton link type="danger" @click="openCloseTodo(row)">关闭</ElButton>
                    </template>
                  </ElTableColumn>
                </ElTable>
              </div>
              <div class="flex shrink-0 justify-end">
                <ElPagination
                  v-model:current-page="todoPage"
                  v-model:page-size="todoSize"
                  :total="todoTotal"
                  :page-sizes="[10, 20, 50]"
                  layout="total, sizes, prev, pager, next"
                  background
                  @current-change="handleTodoPageChange"
                  @size-change="handleTodoSizeChange"
                />
              </div>
            </div>
          </ElTabPane>

          <ElTabPane label="合规报告" name="compliance">
            <div class="chs-notice-panel">
              <div class="flex shrink-0 flex-wrap items-center justify-between gap-2">
                <div class="min-w-0 flex-1 text-sm">
                  <span class="mr-4">最近校验：{{ complianceReport?.ranAt || '—' }}</span>
                  <span class="text-muted-foreground line-clamp-2 break-all">
                    摘要：{{ complianceReport?.summaryJson || '暂无' }}
                  </span>
                </div>
                <ElSpace class="shrink-0">
                  <ElButton size="small" :loading="complianceLoading" @click="loadCompliance">
                    刷新
                  </ElButton>
                  <ElButton
                    type="primary"
                    size="small"
                    :loading="complianceRerunLoading"
                    @click="handleRerunCompliance"
                  >
                    手动重验
                  </ElButton>
                </ElSpace>
              </div>
              <div class="chs-notice-panel__table">
                <ElTable
                  v-loading="complianceLoading || complianceRerunLoading"
                  height="100%"
                  :data="complianceIssues"
                  border
                  stripe
                  empty-text="暂无合规问题"
                >
                  <ElTableColumn prop="code" label="代码" width="140" show-overflow-tooltip />
                  <ElTableColumn prop="severity" label="级别" width="90" />
                  <ElTableColumn
                    prop="hilistCode"
                    label="目录编码"
                    min-width="140"
                    show-overflow-tooltip
                  />
                  <ElTableColumn
                    prop="projectCode"
                    label="项目代码"
                    min-width="120"
                    show-overflow-tooltip
                  />
                  <ElTableColumn
                    prop="message"
                    label="说明"
                    min-width="240"
                    show-overflow-tooltip
                  />
                </ElTable>
              </div>
            </div>
          </ElTabPane>
        </ElTabs>
      </ElTabPane>
    </ElTabs>

    <ElDrawer
      v-model="lineDrawerVisible"
      :title="currentDoc ? `明细 · ${currentDoc.title || currentDoc.id}` : '明细'"
      size="70%"
      destroy-on-close
    >
      <ElTable v-loading="lineLoading" :data="lineRows" border stripe height="70vh">
        <ElTableColumn label="动作" width="100">
          <template #default="{ row }">
            {{ formatNoticeAction(row.action) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="hilistCode" label="目录编码" min-width="140" show-overflow-tooltip />
        <ElTableColumn prop="projectCode" label="项目代码" min-width="120" show-overflow-tooltip />
        <ElTableColumn prop="hilistName" label="名称" min-width="160" show-overflow-tooltip />
        <ElTableColumn prop="chrgitmLv" label="等级" width="70" />
        <ElTableColumn prop="beginDate" label="起日" width="110" />
        <ElTableColumn prop="endDate" label="止日" width="110" />
        <ElTableColumn prop="sheetName" label="Sheet" width="120" show-overflow-tooltip />
        <ElTableColumn prop="rowNo" label="行号" width="70" />
        <ElTableColumn prop="warning" label="警告" min-width="120" show-overflow-tooltip />
      </ElTable>
      <div class="mt-3 flex justify-end">
        <ElPagination
          v-model:current-page="linePage"
          v-model:page-size="lineSize"
          :total="lineTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="handleLinePageChange"
          @size-change="handleLineSizeChange"
        />
      </div>
    </ElDrawer>

    <ElDialog
      v-model="previewVisible"
      :title="previewDoc ? `应用预览 · ${previewDoc.title || previewDoc.id}` : '应用预览'"
      width="720px"
      destroy-on-close
    >
      <div v-loading="previewLoading" class="flex flex-col gap-3">
        <div class="flex flex-wrap gap-4 text-sm">
          <span>Overlay 变更：{{ previewResult?.overlayUpsertCount ?? 0 }}</span>
          <span>回写：{{ previewResult?.writebackCount ?? 0 }}</span>
          <span>未收费同步：{{ previewResult?.unchargedSyncCount ?? 0 }}</span>
        </div>
        <ElTable
          :data="previewResult?.samples ?? []"
          border
          stripe
          max-height="360"
          empty-text="无样例"
        >
          <ElTableColumn label="动作" width="100">
            <template #default="{ row }">
              {{ formatNoticeAction(row.action) }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="hilistCode" label="目录编码" min-width="130" show-overflow-tooltip />
          <ElTableColumn
            prop="projectCode"
            label="项目代码"
            min-width="120"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="effectSummary"
            label="影响摘要"
            min-width="200"
            show-overflow-tooltip
          />
        </ElTable>
      </div>
      <template #footer>
        <ElButton @click="previewVisible = false">关闭</ElButton>
        <ElButton
          type="warning"
          :disabled="!previewDoc || !canApplyDoc(previewDoc)"
          :loading="previewDoc != null && applyLoadingDocId === previewDoc.id"
          @click="previewDoc && handleApplyDoc(previewDoc)"
        >
          确认应用
        </ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="closeTodoVisible" title="关闭待办" width="480px" destroy-on-close>
      <ElForm label-width="80px" @submit.prevent="handleCloseTodo">
        <ElFormItem label="待办">
          <span class="text-sm">
            {{ closeTodoRow?.todoType }} · {{ closeTodoRow?.hilistCode || closeTodoRow?.id }}
          </span>
        </ElFormItem>
        <ElFormItem label="关闭原因" required>
          <ElInput
            v-model="closeReason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写关闭原因（必填）"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="closeTodoVisible = false">取消</ElButton>
        <ElButton type="danger" :loading="closeTodoLoading" @click="handleCloseTodo">
          确认关闭
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>

<style scoped>
.chs-notice-page {
  gap: 0.5rem;
}

.chs-notice-tabs {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.chs-notice-tabs :deep(.el-tabs__header) {
  flex-shrink: 0;
  margin-bottom: 8px;
}

.chs-notice-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.chs-notice-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.chs-notice-tabs--nested {
  height: 100%;
}

.chs-notice-panel {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  gap: 0.5rem;
}

.chs-notice-panel__table {
  min-height: 0;
  flex: 1;
}

.chs-notice-query :deep(.el-form-item) {
  margin-bottom: 0;
}
</style>
