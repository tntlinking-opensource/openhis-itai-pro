import type { ClientScanResultDto } from '#/api/payment/payment.types';

import { ElMessageBox } from 'element-plus';

/** 银联已成功但本地 settle 未完成（用户取消补结算） */
export class ScanSettleIncompleteError extends Error {
  readonly clientScanResult: ClientScanResultDto;

  constructor(message: string, clientScanResult: ClientScanResultDto) {
    super(message);
    this.name = 'ScanSettleIncompleteError';
    this.clientScanResult = clientScanResult;
  }
}

/**
 * 扫码后 settle：失败时提示补结算并重试一次（applyClientScanResult 幂等，不重复 C）。
 */
export async function settleWithScanRecovery<T>(
  clientScanResult: ClientScanResultDto,
  settle: () => Promise<T>,
): Promise<T> {
  try {
    return await settle();
  } catch (error) {
    const message = error instanceof Error ? error.message : '结算失败';
    try {
      await ElMessageBox.confirm(
        `${message}。银联扣款已成功，是否立即补结算？（不会重复扣款）`,
        '补结算',
        {
          confirmButtonText: '补结算',
          cancelButtonText: '取消',
          type: 'warning',
        },
      );
      return await settle();
    } catch {
      throw new ScanSettleIncompleteError(message, clientScanResult);
    }
  }
}
