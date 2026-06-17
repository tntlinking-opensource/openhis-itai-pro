import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { usePrintJob } from '#/composables/use-print-job';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';
import type { TreatmentExecutePrintTarget } from '#/utils/report/nurse-station-print-targets';

const DEFAULT_TEMPLATE_CODE = 'treatment_execute_sheet';
const DATASET_CODE = 'treatment_execute_sheet';
const TARGET_TYPE = 'TEMPLATE';

export interface TreatmentExecuteSheetPrintParams {
  encounterId: string;
  target: TreatmentExecutePrintTarget;
  templateCode?: string;
  jobName?: string;
}

function buildBizRef(
  encounterId: string,
  target: TreatmentExecutePrintTarget,
): Record<string, unknown> {
  return {
    encounterId,
    serviceRequestIds: target.serviceRequestIds,
  };
}

export function useTreatmentExecuteSheetPrint() {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);

  async function printTreatmentExecuteSheet(
    params: TreatmentExecuteSheetPrintParams,
  ): Promise<void> {
    const encounterId = params.encounterId?.trim();
    if (!encounterId) {
      ElMessage.warning('缺少就诊信息，无法打印处置执行单');
      return;
    }
    if (!params.target?.serviceRequestIds?.length) {
      ElMessage.warning('没有可打印的处置医嘱');
      return;
    }

    const templateCode = params.templateCode?.trim() || DEFAULT_TEMPLATE_CODE;
    const target = params.target;
    const clientJobId = crypto.randomUUID();
    const bizRef = buildBizRef(encounterId, target);
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
            encounterId,
            serviceRequestIds: target.serviceRequestIds,
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
          errorMessage: '处置执行单 PDF 为空',
          parentEventId: previewEventId,
        });
        throw new Error('处置执行单 PDF 生成失败');
      }

      await runPrintJob({
        pdfBlob: blob,
        clientJobId,
        jobName: params.jobName ?? '处置执行单',
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
    printTreatmentExecuteSheet,
  };
}
