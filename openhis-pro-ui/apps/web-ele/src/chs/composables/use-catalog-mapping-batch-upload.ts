import type { PendingCatalogMappingRow } from '#/chs/api/catalog-workbench';

import { ref } from 'vue';

import {
  uploadActivityCatalog3301Api,
  uploadDeviceCatalog3301Api,
  uploadMedicationCatalog3301Api,
} from '#/chs/api';

export type BatchUploadRowStatus = 'pending' | 'running' | 'success' | 'failed' | 'skipped';

export interface BatchUploadRowResult {
  row: PendingCatalogMappingRow;
  status: BatchUploadRowStatus;
  message?: string;
}

export function useCatalogMappingBatchUpload() {
  const running = ref(false);
  const cancelRequested = ref(false);
  const total = ref(0);
  const processed = ref(0);
  const successCount = ref(0);
  const failCount = ref(0);
  const currentName = ref('');
  const percent = ref(0);
  const results = ref<BatchUploadRowResult[]>([]);
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

  async function uploadOne(
    contractBusNo: string,
    row: PendingCatalogMappingRow,
  ): Promise<BatchUploadRowResult> {
    const productId = row.productId;
    const listType = row.listType;
    try {
      let uploadResult;
      if (row.productTable === 'med_medication_definition') {
        uploadResult = await uploadMedicationCatalog3301Api({
          contractBusNo,
          medicationDefId: productId,
          listType,
        });
      } else if (row.productTable === 'wkf_activity_definition') {
        uploadResult = await uploadActivityCatalog3301Api({
          contractBusNo,
          activityDefId: productId,
          listType,
        });
      } else if (row.productTable === 'adm_device_definition') {
        uploadResult = await uploadDeviceCatalog3301Api({
          contractBusNo,
          deviceDefId: productId,
          listType,
        });
      } else {
        return { row, status: 'skipped', message: '未知产品类型' };
      }
      if (uploadResult.allSuccess) {
        return { row, status: 'success', message: uploadResult.summary };
      }
      return {
        row,
        status: 'failed',
        message: uploadResult.summary ?? '3301 上传失败',
      };
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : '3301 上传失败';
      return { row, status: 'failed', message };
    }
  }

  /**
   * 逐条调用 3301（平台不支持批量）；更新进度条。
   */
  async function runSequentialUpload(contractBusNo: string, rows: PendingCatalogMappingRow[]) {
    if (running.value) return;
    if (!contractBusNo) {
      throw new Error('请选择医保契约');
    }
    const pendingRows = rows.filter((r) => r.ybNo?.trim());
    if (pendingRows.length === 0) {
      throw new Error('没有可上传的记录（须已填写医保编码）');
    }

    reset();
    running.value = true;
    cancelRequested.value = false;
    total.value = pendingRows.length;
    detailLine.value = '准备逐条上传 3301…';

    for (const row of pendingRows) {
      if (cancelRequested.value) {
        results.value.push({ row, status: 'skipped', message: '用户取消' });
        break;
      }
      currentName.value = row.name ?? row.busNo ?? String(row.productId);
      detailLine.value = `正在上传：${currentName.value}（${processed.value + 1}/${total.value}）`;
      const item = await uploadOne(contractBusNo, row);
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
    runSequentialUpload,
  };
}
