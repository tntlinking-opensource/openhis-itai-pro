import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { usePrintJob } from '#/composables/use-print-job';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';

const DEFAULT_TEMPLATE_CODE = 'inpatient_charge_summary';
const DATASET_CODE = 'inpatient_charge_summary';
const TARGET_TYPE = 'TEMPLATE';

export interface InpatientChargeSummaryPrintParams {
  encounterId: string;
  patientId: string;
  paymentId?: string;
  chargeItemIds?: string[];
  templateCode?: string;
  jobName?: string;
}

function buildBizRef(params: InpatientChargeSummaryPrintParams): Record<string, unknown> {
  const bizRef: Record<string, unknown> = {
    encounterId: params.encounterId,
    patientId: params.patientId,
  };
  if (params.paymentId?.trim()) {
    bizRef.paymentId = params.paymentId.trim();
  }
  if (params.chargeItemIds?.length) {
    bizRef.chargeItemIds = params.chargeItemIds;
  }
  return bizRef;
}

function buildPreviewParams(params: InpatientChargeSummaryPrintParams): Record<string, unknown> {
  const previewParams: Record<string, unknown> = {
    encounterId: params.encounterId,
    patientId: params.patientId,
  };
  if (params.paymentId?.trim()) {
    previewParams.paymentId = params.paymentId.trim();
  }
  if (params.chargeItemIds?.length) {
    previewParams.chargeItemIds = params.chargeItemIds;
  }
  return previewParams;
}

export function useInpatientChargeSummaryPrint() {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);

  async function printInpatientChargeSummary(
    params: InpatientChargeSummaryPrintParams,
  ): Promise<void> {
    const encounterId = params.encounterId?.trim();
    const patientId = params.patientId?.trim();
    if (!encounterId || !patientId) {
      ElMessage.warning('缺少就诊或患者信息，无法打印费用总清单');
      return;
    }

    const templateCode = params.templateCode?.trim() || DEFAULT_TEMPLATE_CODE;
    const clientJobId = crypto.randomUUID();
    const bizRef = buildBizRef({ ...params, encounterId, patientId });
    const previewParams = buildPreviewParams({ ...params, encounterId, patientId });

    previewLoading.value = true;
    const previewStarted = performance.now();
    let previewEventId: number | undefined;

    try {
      let result: PreviewResultDto;
      try {
        result = await reportDesignPreviewApi({
          templateCode,
          useSampleData: false,
          params: previewParams,
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
          errorMessage: '住院费用总清单 PDF 为空',
          parentEventId: previewEventId,
        });
        throw new Error('住院费用总清单 PDF 生成失败');
      }

      await runPrintJob({
        pdfBlob: blob,
        clientJobId,
        jobName: params.jobName ?? '住院费用总清单',
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
    printInpatientChargeSummary,
  };
}
