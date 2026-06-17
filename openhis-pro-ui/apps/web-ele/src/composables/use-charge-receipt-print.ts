import { ref } from 'vue';

import { usePrintJob } from '#/composables/use-print-job';

export interface ChargeReceiptPrintParams {
  paymentId: string;
  encounterId: string;
  chargeItemIds?: string[];
  templateCode?: string;
  jobName?: string;
  /** 为 false 时不弹确认框，直接打印 */
  confirmBeforePrint?: boolean;
}

export function useChargeReceiptPrint() {
  const { isPrinting } = usePrintJob();
  const previewLoading = ref(false);

  /** 默认不打印；待打印插件稳定后恢复预览与静默打印逻辑 */
  async function printChargeReceipt(_params: ChargeReceiptPrintParams): Promise<void> {
    void _params;
  }

  return {
    isPrinting,
    previewLoading,
    printChargeReceipt,
  };
}

/* --- 待恢复：门诊收费凭条打印 ---
import { printPdfFromBlob } from '@vben/utils';

import { ElMessage, ElMessageBox } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';

const DEFAULT_TEMPLATE_CODE = 'charge_receipt';
const DATASET_CODE = 'charge_receipt';
const TARGET_TYPE = 'TEMPLATE';

function buildBizRef(params: ChargeReceiptPrintParams) {
  const bizRef: Record<string, unknown> = {
    paymentId: params.paymentId,
    encounterId: params.encounterId,
  };
  if (params.chargeItemIds?.length) {
    bizRef.chargeItemIds = params.chargeItemIds;
  }
  return bizRef;
}

async function printChargeReceiptImpl(
  params: ChargeReceiptPrintParams,
  runPrintJob: ReturnType<typeof usePrintJob>['runPrintJob'],
  previewLoading: { value: boolean },
): Promise<void> {
  const paymentId = params.paymentId?.trim();
  const encounterId = params.encounterId?.trim();
  if (!paymentId) {
    ElMessage.warning('缺少支付单号，无法打印收费凭条');
    return;
  }
  if (!encounterId) {
    ElMessage.warning('缺少就诊信息，无法打印收费凭条');
    return;
  }

  if (params.confirmBeforePrint !== false) {
    try {
      await ElMessageBox.confirm('是否打印门诊收费凭条？', '收费成功', {
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
        errorMessage: '收费凭条 PDF 为空',
        parentEventId: previewEventId,
      });
      ElMessage.error('收费凭条 PDF 生成失败');
      return;
    }

    await runPrintJob({
      pdfBlob: blob,
      clientJobId,
      jobName: params.jobName ?? '门诊收费凭条',
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
