import { ref } from 'vue';

import { usePrintJob } from '#/composables/use-print-job';

export interface OutpatientInvoicePrintParams {
  paymentId: string;
  encounterId: string;
  chargeItemIds?: string[];
  templateCode?: string;
  jobName?: string;
  confirmBeforePrint?: boolean;
}

export function useOutpatientInvoicePrint() {
  const { isPrinting } = usePrintJob();
  const previewLoading = ref(false);

  /** 默认不打印；待打印插件稳定后恢复预览与静默打印逻辑 */
  async function printOutpatientInvoice(_params: OutpatientInvoicePrintParams): Promise<void> {
    void _params;
  }

  return {
    isPrinting,
    previewLoading,
    printOutpatientInvoice,
  };
}

/* --- 待恢复：门诊收费票据联打印 ---
import { printPdfFromBlob } from '@vben/utils';

import { ElMessage, ElMessageBox } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';

const DEFAULT_TEMPLATE_CODE = 'outpatient_invoice_overlay';
const DATASET_CODE = 'outpatient_invoice_overlay';
const TARGET_TYPE = 'TEMPLATE';

function buildBizRef(params: OutpatientInvoicePrintParams): Record<string, unknown> {
  const bizRef: Record<string, unknown> = {
    paymentId: params.paymentId,
    encounterId: params.encounterId,
  };
  if (params.chargeItemIds?.length) {
    bizRef.chargeItemIds = params.chargeItemIds;
  }
  return bizRef;
}

async function printOutpatientInvoiceImpl(
  params: OutpatientInvoicePrintParams,
  runPrintJob: ReturnType<typeof usePrintJob>['runPrintJob'],
  previewLoading: { value: boolean },
): Promise<void> {
  const paymentId = params.paymentId?.trim();
  const encounterId = params.encounterId?.trim();
  if (!paymentId) {
    ElMessage.warning('缺少支付单号，无法打印门诊票据');
    return;
  }
  if (!encounterId) {
    ElMessage.warning('缺少就诊信息，无法打印门诊票据');
    return;
  }

  if (params.confirmBeforePrint !== false) {
    try {
      await ElMessageBox.confirm('是否打印门诊收费票据联？', '收费成功', {
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
        errorMessage: '门诊票据 PDF 为空',
        parentEventId: previewEventId,
      });
      throw new Error('门诊票据 PDF 生成失败');
    }

    await runPrintJob({
      pdfBlob: blob,
      clientJobId,
      jobName: params.jobName ?? '门诊收费票据',
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
--- */
