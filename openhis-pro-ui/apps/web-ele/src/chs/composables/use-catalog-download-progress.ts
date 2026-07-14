import type {
  CatalogDownloadPhaseSummary,
  CatalogDownloadVersionItem,
  CatalogDownloadJobProgress,
  StartCatalogDownloadParams,
} from '#/chs/api';
import type { CatalogDownloadPhaseKey } from '#/chs/constants/catalog-download';

import { reactive, ref } from 'vue';

import { getCatalogDownloadJobProgressApi, startCatalogDownloadApi } from '#/chs/api';
import { CATALOG_DOWNLOAD_PHASES } from '#/chs/constants/catalog-download';

export type CatalogDownloadRunStatus = 'idle' | 'running' | 'success' | 'error';

export interface CatalogDownloadSummary {
  totalUpsert: number;
  totalVersionCount: number;
  localVer?: string;
  latestCursorId?: number | string;
  cursorSnapshotCount?: number;
  lastFileQuryNo?: string;
  lastFilename?: string;
  hasMore: boolean;
  stoppedByLimit: boolean;
  phaseSummaries?: CatalogDownloadPhaseSummary[];
}

function createPhaseState() {
  return CATALOG_DOWNLOAD_PHASES.map((item) => ({
    key: item.key,
    label: item.label,
    detail: item.detail,
    status: 'wait' as 'active' | 'exception' | 'success' | 'wait',
    percent: 0,
  }));
}

/** 阶段一拉取 ZIP 期间的估算进度（服务端同步阻塞，无真实进度）。 */
function startCollectPhaseTicker(options: {
  phases: ReturnType<typeof createPhaseState>;
  expectedVersionCount: number;
  onTick?: () => void;
  onDetail?: (line: string) => void;
}) {
  const { phases, expectedVersionCount, onTick, onDetail } = options;
  let versionRound = 0;
  let phasePercent = 6;
  let tailTicks = 0;

  const timer = window.setInterval(() => {
    const collect = phases.find((p) => p.key === 'collect');
    if (!collect) return;

    for (const phase of phases) {
      if (phase.key !== 'collect' && phase.key !== 'collect_done') {
        phase.status = 'wait';
        phase.percent = 0;
      }
    }

    collect.status = 'active';
    const total = Math.max(expectedVersionCount, 1);

    if (versionRound < total) {
      versionRound += 1;
      phasePercent = Math.round((versionRound / total) * 100);
      onDetail?.(`第 ${versionRound}/${total} 个版本：查版本 → 9102 下 ZIP → 写入本地缓存`);
    } else {
      tailTicks += 1;
      phasePercent = Math.min(99, Math.max(phasePercent, 90) + 1);
      onDetail?.(
        tailTicks >= 3
          ? 'ZIP 拉取收尾中（服务端处理中，请稍候）…'
          : `已预计 ${total} 个版本，服务端可能仍在拉取更多 ZIP…`,
      );
    }

    collect.percent = phasePercent;
    onTick?.();
  }, 320);

  return () => window.clearInterval(timer);
}

function markCollectPhasesDone(phases: ReturnType<typeof createPhaseState>) {
  for (const phase of phases) {
    if (phase.key === 'collect' || phase.key === 'collect_done') {
      phase.status = 'success';
      phase.percent = 100;
    }
  }
}

function applyServerImportProgress(
  phases: ReturnType<typeof createPhaseState>,
  progress: CatalogDownloadJobProgress,
) {
  markCollectPhasesDone(phases);

  const parsePhase = phases.find((p) => p.key === 'parse');
  const importPhase = phases.find((p) => p.key === 'import');
  const pct = progress.percent ?? 0;
  const finished = progress.finished === true;

  if (parsePhase) {
    parsePhase.status = finished || pct > 0 ? 'success' : 'active';
    parsePhase.percent = finished ? 100 : Math.min(100, pct);
  }
  if (importPhase) {
    if (finished && progress.success) {
      importPhase.status = 'success';
      importPhase.percent = 100;
    } else if (progress.phase === 'failed') {
      importPhase.status = 'exception';
      importPhase.percent = pct;
    } else {
      importPhase.status = 'active';
      importPhase.percent = pct;
    }
  }
}

function applyServerPhaseSummaries(
  phases: ReturnType<typeof createPhaseState>,
  summaries?: CatalogDownloadPhaseSummary[],
) {
  if (!summaries?.length) return;
  const byKey = new Map(summaries.map((s) => [s.phaseKey, s]));
  for (const phase of phases) {
    if (phase.key === 'collect_done') continue;
    const server = byKey.get(phase.key);
    if (server?.detail) {
      phase.detail = `${server.title}：${server.detail}（${server.metric}）`;
    }
  }
}

function markPhasesException(phases: ReturnType<typeof createPhaseState>, activeKey?: string) {
  for (const phase of phases) {
    if (phase.key === activeKey) {
      phase.status = 'exception';
      return;
    }
    if (phase.status === 'active') {
      phase.status = 'exception';
      return;
    }
  }
  const last = phases[phases.length - 1];
  if (last) last.status = 'exception';
}

function sleep(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

export function useCatalogDownloadProgress() {
  const status = ref<CatalogDownloadRunStatus>('idle');
  const running = ref(false);
  const overallPercent = ref(0);
  const totalVersions = ref(0);
  const processedVersions = ref(0);
  const upsertedRows = ref(0);
  const parsedRows = ref(0);
  const currentPhaseKey = ref<CatalogDownloadPhaseKey>('collect');
  const detailLine = ref('');
  const batchPhases = reactive(createPhaseState());
  const summary = ref<CatalogDownloadSummary | null>(null);
  const resultVersions = ref<CatalogDownloadVersionItem[]>([]);
  const errorMessage = ref('');

  let stopCollectTicker: (() => void) | null = null;

  function resetProgress(expectedVersionCount = 0) {
    status.value = 'idle';
    overallPercent.value = 0;
    totalVersions.value = expectedVersionCount;
    processedVersions.value = 0;
    upsertedRows.value = 0;
    parsedRows.value = 0;
    currentPhaseKey.value = 'collect';
    detailLine.value = '';
    summary.value = null;
    resultVersions.value = [];
    errorMessage.value = '';
    const fresh = createPhaseState();
    batchPhases.splice(0, batchPhases.length, ...fresh);
  }

  function stopCollectTickerSafe() {
    stopCollectTicker?.();
    stopCollectTicker = null;
  }

  function syncOverallFromPhases(importPercent: number) {
    // 拉取约占 35%，入库约占 65%
    const collectDone = 35;
    const importPart = Math.round((importPercent / 100) * 65);
    overallPercent.value = Math.min(99, collectDone + importPart);
  }

  async function pollImportJob(jobId: string) {
    while (true) {
      const progress = await getCatalogDownloadJobProgressApi(jobId);
      totalVersions.value = Number(progress.totalVersions ?? totalVersions.value);
      processedVersions.value = Number(progress.processedVersions ?? 0);
      upsertedRows.value = Number(progress.upsertedRows ?? 0);
      parsedRows.value = Number(progress.parsedRows ?? 0);
      detailLine.value =
        progress.detailMessage ??
        `已入库 ${upsertedRows.value} 行（${processedVersions.value}/${totalVersions.value} 包）`;

      applyServerImportProgress(batchPhases, progress);
      const active = batchPhases.find((p) => p.status === 'active');
      if (active) {
        currentPhaseKey.value = active.key as CatalogDownloadPhaseKey;
      }

      const importPct = progress.percent ?? 0;
      syncOverallFromPhases(importPct);

      if (progress.finished) {
        if (progress.success && progress.result) {
          const result = progress.result;
          overallPercent.value = 100;
          detailLine.value = '全部阶段已完成';
          applyServerPhaseSummaries(batchPhases, result.phaseSummaries);

          const versionCount = Number(
            result.totalVersionCount ?? result.versionBatches ?? totalVersions.value,
          );
          totalVersions.value = versionCount;
          resultVersions.value = result.versions ?? [];
          summary.value = {
            totalUpsert: Number(result.upsertCount ?? upsertedRows.value),
            totalVersionCount: versionCount,
            localVer: result.localVer,
            latestCursorId: result.latestCursorId,
            cursorSnapshotCount: result.cursorSnapshotCount,
            lastFileQuryNo: result.lastFileQuryNo,
            lastFilename: result.lastFilename,
            hasMore: false,
            stoppedByLimit: false,
            phaseSummaries: result.phaseSummaries,
          };
          status.value = 'success';
        } else {
          markPhasesException(batchPhases, 'import');
          status.value = 'error';
          errorMessage.value = progress.errorMessage ?? '目录入库失败';
          detailLine.value = errorMessage.value;
        }
        return;
      }

      await sleep(1200);
    }
  }

  async function runDownload(
    params: StartCatalogDownloadParams & {
      maxBatchLimit?: number;
      expectedVersionCount?: number;
    },
  ) {
    if (running.value) return;
    running.value = true;
    const expected = params.expectedVersionCount ?? params.maxBatchLimit ?? 0;
    resetProgress(expected);
    status.value = 'running';
    currentPhaseKey.value = 'collect';
    detailLine.value = '准备开始…';

    stopCollectTickerSafe();
    stopCollectTicker = startCollectPhaseTicker({
      phases: batchPhases,
      expectedVersionCount: expected,
      onDetail: (line) => {
        detailLine.value = line;
      },
      onTick: () => {
        const active = batchPhases.find((p) => p.status === 'active');
        if (active) {
          currentPhaseKey.value = active.key as CatalogDownloadPhaseKey;
        }
        const collectPct = batchPhases.find((p) => p.key === 'collect')?.percent ?? 0;
        overallPercent.value = Math.min(34, Math.round((collectPct / 100) * 35));
      },
    });

    try {
      const start = await startCatalogDownloadApi({
        contractBusNo: params.contractBusNo,
        contractId: params.contractId,
        infno: params.infno,
        forceStartFromZero: params.forceStartFromZero,
        clearZipCache: params.clearZipCache,
        startLocalVer: params.startLocalVer,
        verBranch: params.verBranch,
        maxVersions: 0,
      });

      stopCollectTickerSafe();
      markCollectPhasesDone(batchPhases);
      overallPercent.value = 35;

      const versionCount = Number(start.totalVersions ?? expected);
      totalVersions.value = versionCount;
      detailLine.value =
        start.message ?? `ZIP 拉取完成（${versionCount} 包），正在解压、解析并入库…`;

      if (!start.jobId) {
        overallPercent.value = 100;
        for (const phase of batchPhases) {
          phase.status = 'success';
          phase.percent = 100;
        }
        status.value = 'success';
        detailLine.value =
          start.message ?? '平台未返回可入库的 ZIP（无新版本或未拉到文件包）；游标与镜像行不会更新';
        summary.value = {
          totalUpsert: 0,
          totalVersionCount: 0,
          hasMore: false,
          stoppedByLimit: false,
        };
        return;
      }

      await pollImportJob(start.jobId);
    } catch (error) {
      stopCollectTickerSafe();
      markPhasesException(batchPhases, currentPhaseKey.value);
      status.value = 'error';
      errorMessage.value = error instanceof Error ? error.message : '目录下载失败';
      overallPercent.value = Math.max(overallPercent.value, 5);
      detailLine.value = errorMessage.value;
    } finally {
      stopCollectTickerSafe();
      running.value = false;
    }
  }

  return {
    status,
    running,
    overallPercent,
    totalVersions,
    processedVersions,
    upsertedRows,
    parsedRows,
    currentPhaseKey,
    detailLine,
    batchPhases,
    summary,
    resultVersions,
    errorMessage,
    resetProgress,
    runDownload,
  };
}
