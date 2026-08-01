import type { InstitutionCatalogMatchRow } from '#/chs/api/catalog-workbench';

import { ref } from 'vue';

import { revokePlatformCatalogMatch3302Api } from '#/chs/api/catalog-workbench';

export type BatchRevokeRowStatus = 'pending' | 'running' | 'success' | 'failed' | 'skipped';

export interface BatchRevokeRowResult {
  row: InstitutionCatalogMatchRow;
  status: BatchRevokeRowStatus;
  message?: string;
}

/**
 * 平台已对照镜像逐条 3302 撤销（含院内停用/孤儿）。
 */
export function useCatalogMappingBatchRevoke() {
  const running = ref(false);
  const cancelRequested = ref(false);
  const total = ref(0);
  const processed = ref(0);
  const successCount = ref(0);
  const failCount = ref(0);
  const currentName = ref('');
  const percent = ref(0);
  const results = ref<BatchRevokeRowResult[]>([]);
  const detailLine = ref('');

  function reset() {
    running.value = false;
    cancelRequested.value = false;
    total.value = 0;
    processed.value = 0;
    successCount.value = 0;
    failCount.value = 0;
    currentName.value = '';
    percent.value = 0;
    results.value = [];
    detailLine.value = '';
  }

  function requestCancel() {
    cancelRequested.value = true;
    detailLine.value = '正在取消（当前条完成后停止）…';
  }

  async function revokeOne(
    contractBusNo: string,
    row: InstitutionCatalogMatchRow,
  ): Promise<BatchRevokeRowResult> {
    if (row.id === undefined || row.id === null || String(row.id).trim() === '') {
      return { row, status: 'skipped', message: '缺少镜像主键' };
    }
    try {
      const result = await revokePlatformCatalogMatch3302Api({
        contractBusNo,
        matchId: row.id,
      });
      if (result.allSuccess) {
        return { row, status: 'success', message: result.summary };
      }
      return {
        row,
        status: 'failed',
        message: result.summary ?? '3302 撤销失败',
      };
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '3302 撤销失败';
      return { row, status: 'failed', message };
    }
  }

  async function runSequentialRevoke(contractBusNo: string, rows: InstitutionCatalogMatchRow[]) {
    if (running.value) return;
    if (!contractBusNo) {
      throw new Error('请选择医保契约');
    }
    const targets = rows.filter((r) => r.id !== undefined && r.id !== null);
    if (targets.length === 0) {
      throw new Error('请选择要撤销的平台已对照行');
    }

    reset();
    running.value = true;
    cancelRequested.value = false;
    total.value = targets.length;
    detailLine.value = '准备逐条撤销 3302…';

    for (const row of targets) {
      if (cancelRequested.value) {
        results.value.push({ row, status: 'skipped', message: '用户取消' });
        break;
      }
      currentName.value = row.medinsListName ?? row.medinsListCodg ?? String(row.id);
      detailLine.value = `正在撤销：${currentName.value}（${processed.value + 1}/${total.value}）`;
      const item = await revokeOne(contractBusNo, row);
      results.value.push(item);
      processed.value += 1;
      if (item.status === 'success') {
        successCount.value += 1;
      } else if (item.status === 'failed') {
        failCount.value += 1;
      }
      percent.value = Math.round((processed.value / total.value) * 100);
    }

    if (cancelRequested.value) {
      detailLine.value = `已取消：成功 ${successCount.value}，失败 ${failCount.value}`;
    } else {
      detailLine.value = `完成：成功 ${successCount.value}，失败 ${failCount.value}`;
    }
    running.value = false;
    return {
      successCount: successCount.value,
      failCount: failCount.value,
      results: results.value,
    };
  }

  return {
    running,
    cancelRequested,
    total,
    processed,
    successCount,
    failCount,
    currentName,
    percent,
    results,
    detailLine,
    reset,
    requestCancel,
    runSequentialRevoke,
  };
}
