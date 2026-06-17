import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage, ElMessageBox } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { usePrintJob } from '#/composables/use-print-job';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';

const DEFAULT_TEMPLATE_CODE = 'outpatient_refund_receipt';
const DATASET_CODE = 'outpatient_refund_receipt';
const TARGET_TYPE = 'TEMPLATE';

export interface OutpatientRefundReceiptPrintParams {
  paymentId: string;
  encounterId: string;
  originalPaymentId?: string;
  chargeItemIds?: string[];
  templateCode?: string;
  jobName?: string;
  confirmBeforePrint?: boolean;
}

function buildBizRef(params: OutpatientRefundReceiptPrintParams): Record<string, unknown> {
  const bizRef: Record<string, unknown> = {
    paymentId: params.paymentId,
    encounterId: params.encounterId,
  };
  if (params.originalPaymentId?.trim()) {
    bizRef.originalPaymentId = params.originalPaymentId.trim();
  }
  if (params.chargeItemIds?.length) {
    bizRef.chargeItemIds = params.chargeItemIds;
  }
  return bizRef;
}

export function useOutpatientRefundReceiptPrint() {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);

  async function printOutpatientRefundReceipt(
    params: OutpatientRefundReceiptPrintParams,
  ): Promise<void> {
    const paymentId = params.paymentId?.trim();
    const encounterId = params.encounterId?.trim();
    if (!paymentId) {
      ElMessage.warning('缺少退费支付单号，无法打印退费凭条');
      return;
    }
    if (!encounterId) {
      ElMessage.warning('缺少就诊信息，无法打印退费凭条');
      return;
    }

    if (params.confirmBeforePrint !== false) {
      try {
        await ElMessageBox.confirm('是否打印门诊退费凭条？', '退费成功', {
          confirmButtonText: '打印',
          cancelButtonText: '暂不打印',
          type: 'info',
        });
      } catch {
        return;
      }
    }

    const templateCode = params.templateCode?.trim() || DEFAULT_TEMPLATE_CODE;
    const clientJobId = crypto.randomUUID();
    const bizRef = buildBizRef({ ...params, paymentId, encounterId });

    previewLoading.value = true;
    const previewStarted = performance.now();
    let previewEventId: number | undefined;

    try {
      let result: PreviewResultDto;
      try {
        result = await reportDesignPreviewApi({
          templateCode,
          useSampleData: false,
          params: {
            paymentId,
            encounterId,
            originalPaymentId: params.originalPaymentId,
            chargeItemIds: params.chargeItemIds,
          },
        });
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        await writeReportPrintLog({
          eventType: 'PREVIEW',
          targetType: TARGET_TYPE,
          targetCode: templateCode,
          datasetCode: DATASET_CODE,
          bizRef,
          copies: 0,
          status: 'FAILED',
          clientJobId,
          renderDurationMs: Math.round(performance.now() - previewStarted),
          errorCode: 'PREVIEW_FAILED',
          errorMessage: message,
        });
        throw error;
      }

      const renderDurationMs = Math.round(performance.now() - previewStarted);
      previewEventId = await writeReportPrintLog({
        eventType: 'PREVIEW',
        targetType: TARGET_TYPE,
        targetCode: templateCode,
        datasetCode: DATASET_CODE,
        bizRef,
        copies: 0,
        status: 'SUCCESS',
        clientJobId,
        contentHash: result.contentHash,
        contentType: result.contentType ?? 'application/pdf',
        renderDurationMs,
      });

      const blob = previewBase64ToBlob(result);
      if (!blob) {
        await writeReportPrintLog({
          eventType: 'PREVIEW',
          targetType: TARGET_TYPE,
          targetCode: templateCode,
          datasetCode: DATASET_CODE,
          bizRef,
          copies: 0,
          status: 'FAILED',
          clientJobId,
          contentHash: result.contentHash,
          renderDurationMs,
          errorCode: 'PREVIEW_EMPTY_BODY',
          errorMessage: '退费凭条 PDF 为空',
          parentEventId: previewEventId,
        });
        ElMessage.error('退费凭条 PDF 生成失败');
        return;
      }

      await runPrintJob({
        pdfBlob: blob,
        clientJobId,
        jobName: params.jobName ?? '门诊退费凭条',
        onFallbackBrowser: printPdfFromBlob,
        printLog: {
          targetType: TARGET_TYPE,
          targetCode: templateCode,
          datasetCode: DATASET_CODE,
          bizRef,
          contentHash: result.contentHash,
          contentType: result.contentType ?? 'application/pdf',
          parentEventId: previewEventId,
          clientJobId,
        },
      });
    } finally {
      previewLoading.value = false;
    }
  }

  return {
    isPrinting,
    previewLoading,
    printOutpatientRefundReceipt,
  };
}
