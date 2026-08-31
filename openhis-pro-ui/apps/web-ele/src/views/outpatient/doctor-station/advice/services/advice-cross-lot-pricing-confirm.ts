import type { SignDoctorStationAdviceBatchRequest } from '#/api/doctor-station/advice.types';
import type { SignDoctorStationAdviceBatchResponse } from '#/api/doctor-station/advice.types';

import { ElMessageBox } from 'element-plus';

import { httpSignDoctorStationAdviceBatch } from '#/api/doctor-station/advice.http';

const CROSS_LOT_PRICING_CONFIRM_FALLBACK =
  '当前药品需跨批次取用，合计金额可能与开立预览不一致，是否确认签发？';

/**
 * 是否为药耗 FIFO 跨批次金额需确认业务码（EX-ORD-310 / BR-ORD-035）。
 *
 * @param error 捕获异常
 */
export function isCrossLotPricingConfirmError(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false;
  }
  const code = (error as { errorCode?: unknown }).errorCode;
  return code === 'EX-ORD-310';
}

/**
 * 从异常对象取可读文案。
 *
 * @param error 捕获异常
 * @param fallback 缺省文案
 */
export function resolveCrossLotPricingConfirmMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'message' in error) {
    const msg = String((error as { message?: unknown }).message || '').trim();
    if (msg) {
      return msg;
    }
  }
  return fallback;
}

/**
 * IF-DOR-15 签发：遇 EX-ORD-310 弹确认后带标志重试一次；取消则 AbortError。
 *
 * @param body 签发请求
 */
export async function signDoctorStationAdviceBatchWithCrossLotPricingConfirm(
  body: SignDoctorStationAdviceBatchRequest,
): Promise<SignDoctorStationAdviceBatchResponse> {
  try {
    return await httpSignDoctorStationAdviceBatch(body);
  } catch (error: unknown) {
    if (!isCrossLotPricingConfirmError(error)) {
      throw error;
    }
    const confirmMsg = resolveCrossLotPricingConfirmMessage(
      error,
      CROSS_LOT_PRICING_CONFIRM_FALLBACK,
    );
    try {
      await ElMessageBox.confirm(confirmMsg, '跨批次取用确认', {
        type: 'warning',
        confirmButtonText: '确认签发',
        cancelButtonText: '取消',
        dangerouslyUseHTMLString: false,
      });
    } catch {
      // 医生取消：静默中止，外层不再 toast「签发失败」
      return Promise.reject(new DOMException('AbortError', 'AbortError'));
    }
    // 确认后带标志重试；事务已回滚，本批允许跨批落发放与分段挂费
    return await httpSignDoctorStationAdviceBatch({
      ...body,
      confirmCrossLotPricing: true,
    });
  }
}
