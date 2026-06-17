import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import type { LogEventRequest } from '#/api/report/report-print-log';

import {
  assertPrintCapability,
  formatPrintClientError,
  PrintClientError,
  silentPrintPdf,
} from '#/utils/printClient';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';

/** 写入 {@code reportPrintLog/logEvent} 时 PRINT 事件共用的业务上下文 */
export type ReportPrintLogContext = Pick<
  LogEventRequest,
  | 'targetType'
  | 'targetCode'
  | 'templateVersion'
  | 'datasetCode'
  | 'bizRef'
  | 'contentHash'
  | 'contentType'
  | 'parentEventId'
  | 'clientJobId'
  | 'printerName'
>;

export interface RunPrintJobOptions {
  pdfBlob: Blob;
  printer?: string;
  copies?: number;
  jobName?: string;
  clientJobId?: string;
  /** Win 不可用时 fallback 浏览器打印对话框 */
  onFallbackBrowser?: (blob: Blob) => void;
  /** 传入时自动写 PRINT 日志（与 preview 的 PREVIEW 通过 parentEventId 串联） */
  printLog?: ReportPrintLogContext;
  /** 额外回调（在服务端 logEvent 之后触发） */
  onPrintEvent?: (payload: {
    eventType: 'PRINT';
    status: 'SUCCESS' | 'FAILED';
    errorCode?: string;
    errorMessage?: string;
    winDurationMs?: number;
  }) => void | Promise<void>;
}

export function usePrintJob() {
  const isPrinting = ref(false);

  async function runPrintJob(options: RunPrintJobOptions): Promise<void> {
    if (isPrinting.value) {
      ElMessage.warning('正在打印…');
      return;
    }

    isPrinting.value = true;
    const clientJobId = options.clientJobId ?? crypto.randomUUID();
    const startedAt = performance.now();
    const copies = options.copies ?? 1;

    try {
      await assertPrintCapability();
      const winStarted = performance.now();
      await silentPrintPdf(options.pdfBlob, {
        printer: options.printer,
        copies,
        jobName: options.jobName,
        clientJobId,
      });
      const winDurationMs = Math.round(performance.now() - winStarted);

      if (options.printLog) {
        await writeReportPrintLog({
          ...options.printLog,
          eventType: 'PRINT',
          status: 'SUCCESS',
          copies,
          winDurationMs,
          printerName: options.printer ?? options.printLog.printerName,
          clientJobId: options.printLog.clientJobId ?? clientJobId,
        });
      }

      await options.onPrintEvent?.({
        eventType: 'PRINT',
        status: 'SUCCESS',
        winDurationMs,
      });

      ElMessage.success(`打印已提交（${Math.round(performance.now() - startedAt)} ms）`);
    } catch (error) {
      const winDurationMs = Math.round(performance.now() - startedAt);
      const errorCode = error instanceof PrintClientError ? error.code : 'PRINT_FAILED';
      const errorMessage = formatPrintClientError(error);

      if (options.printLog) {
        await writeReportPrintLog({
          ...options.printLog,
          eventType: 'PRINT',
          status: 'FAILED',
          copies: 0,
          winDurationMs,
          errorCode,
          errorMessage,
          clientJobId: options.printLog.clientJobId ?? clientJobId,
        });
      }

      await options.onPrintEvent?.({
        eventType: 'PRINT',
        status: 'FAILED',
        errorCode,
        errorMessage,
        winDurationMs,
      });

      if (
        options.onFallbackBrowser &&
        error instanceof PrintClientError &&
        (error.code === 'OFFLINE' || error.code === 'NO_PRINT_CAPABILITY')
      ) {
        ElMessage.warning('已通过浏览器打印对话框出纸，与静默打印样式可能略有差异');
        options.onFallbackBrowser(options.pdfBlob);
        return;
      }

      ElMessage.error(errorMessage);
      throw error;
    } finally {
      isPrinting.value = false;
    }
  }

  return {
    isPrinting,
    runPrintJob,
  };
}

export { printPdfFromBlob };
