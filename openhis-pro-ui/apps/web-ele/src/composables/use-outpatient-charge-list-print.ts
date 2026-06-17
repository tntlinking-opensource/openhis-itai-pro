import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { usePrintJob } from '#/composables/use-print-job';
import { getCachedPrintTemplateCode, PRINT_BINDING } from '#/utils/report/print-binding-store';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';

const DEFAULT_TEMPLATE_CODE = 'outpatient_charge_list';
const DATASET_CODE = 'outpatient_charge_list';
const TARGET_TYPE = 'TEMPLATE';

export interface OutpatientChargeListPrintParams {
  paymentId: string;
  encounterId: string;
  chargeItemIds?: string[];
  templateCode?: string;
  jobName?: string;
}

function buildBizRef(params: OutpatientChargeListPrintParams): Record<string, unknown> {
  const bizRef: Record<string, unknown> = {
    paymentId: params.paymentId,
    encounterId: params.encounterId,
  };
  if (params.chargeItemIds?.length) {
    bizRef.chargeItemIds = params.chargeItemIds;
  }
  return bizRef;
}

export function useOutpatientChargeListPrint() {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);

  async function printOutpatientChargeList(params: OutpatientChargeListPrintParams): Promise<void> {
    const paymentId = params.paymentId?.trim();
    const encounterId = params.encounterId?.trim();
    if (!paymentId) {
      ElMessage.warning('缺少支付单号，无法打印费用清单');
      return;
    }
    if (!encounterId) {
      ElMessage.warning('缺少就诊信息，无法打印费用清单');
      return;
    }

    const templateCode =
      params.templateCode?.trim() ||
      getCachedPrintTemplateCode(PRINT_BINDING.OUTPATIENT_CHARGE_LIST_PRINT, DEFAULT_TEMPLATE_CODE);
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
          errorMessage: '门诊费用清单 PDF 为空',
          parentEventId: previewEventId,
        });
        ElMessage.error('门诊费用清单 PDF 生成失败');
        return;
      }

      await runPrintJob({
        pdfBlob: blob,
        clientJobId,
        jobName: params.jobName ?? '门诊费用清单',
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
    printOutpatientChargeList,
  };
}
