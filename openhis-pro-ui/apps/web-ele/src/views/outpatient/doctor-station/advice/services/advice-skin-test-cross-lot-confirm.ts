import type { SaveDoctorStationAdviceDraftBatchResponse } from '#/api/doctor-station/advice.types';

import { ElMessageBox } from 'element-plus';

import type { SaveDoctorStationAdviceDraftBatchRequest } from '#/api/doctor-station/advice.types';

import { httpSaveDoctorStationAdviceDraftBatch } from '#/api/doctor-station/advice.http';

const SKIN_TEST_CROSS_LOT_CONFIRM_FALLBACK =
  '单独批号数量不够本次就诊使用，跨批号可能需要多次皮试，是否确认开立？';

/**
 * 是否为门诊皮试跨批需确认业务码（EX-ORD-309 / BR-ORD-034）。
 *
 * @param error 捕获异常
 */
export function isSkinTestCrossLotConfirmError(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false;
  }
  const code = (error as { errorCode?: unknown }).errorCode;
  return code === 'EX-ORD-309';
}

/**
 * 从异常对象取可读文案。
 *
 * @param error 捕获异常
 * @param fallback 缺省文案
 */
export function resolveSkinTestCrossLotConfirmMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'message' in error) {
    const msg = String((error as { message?: unknown }).message || '').trim();
    if (msg) {
      return msg;
    }
  }
  return fallback;
}

/**
 * IF-DOR-14 暂存：遇 EX-ORD-309 弹确认后带标志重试一次。
 *
 * @param body 暂存请求
 */
export async function saveDoctorStationAdviceDraftBatchWithSkinTestCrossLotConfirm(
  body: SaveDoctorStationAdviceDraftBatchRequest,
): Promise<SaveDoctorStationAdviceDraftBatchResponse> {
  try {
    return await httpSaveDoctorStationAdviceDraftBatch(body);
  } catch (error: unknown) {
    if (!isSkinTestCrossLotConfirmError(error)) {
      throw error;
    }
    const confirmMsg = resolveSkinTestCrossLotConfirmMessage(
      error,
      SKIN_TEST_CROSS_LOT_CONFIRM_FALLBACK,
    );
    try {
      await ElMessageBox.confirm(confirmMsg, '皮试跨批号确认', {
        type: 'warning',
        confirmButtonText: '确认开立',
        cancelButtonText: '取消',
      });
    } catch {
      return Promise.reject(new DOMException('AbortError', 'AbortError'));
    }
    return await httpSaveDoctorStationAdviceDraftBatch({
      ...body,
      confirmCrossLotSkinTest: true,
    });
  }
}
