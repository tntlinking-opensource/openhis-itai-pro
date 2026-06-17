import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { usePrintJob } from '#/composables/use-print-job';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';

const TARGET_TYPE = 'TEMPLATE';

export interface ReportTemplatePrintParams {
  templateCode: string;
  encounterId?: string;
  datasetCode?: string;
  params?: Record<string, unknown>;
  jobName?: string;
}

export function useReportTemplatePrint() {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);

  async function printReportTemplate(params: ReportTemplatePrintParams): Promise<void> {
    const templateCode = params.templateCode?.trim();
    if (!templateCode) {
      ElMessage.warning('缺少模板编码');
      return;
    }

    const encounterId = params.encounterId?.trim();
    const previewParams: Record<string, unknown> = {
      ...(params.params ?? {}),
    };
    if (encounterId) {
      previewParams.encounterId = encounterId;
    }
    previewParams.templateCode = templateCode;

    const clientJobId = crypto.randomUUID();
    const bizRef: Record<string, unknown> = {
      templateCode,
      ...(encounterId ? { encounterId } : {}),
      ...previewParams,
    };

    previewLoading.value = true;
    const previewStarted = performance.now();
    let previewEventId: number | undefined;

    try {
      let result: PreviewResultDto;
      try {
        result = await reportDesignPreviewApi({
          templateCode,
          useSampleData: !encounterId,
          params: previewParams,
        });
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        await writeReportPrintLog({
          eventType: 'PREVIEW',
          targetType: TARGET_TYPE,
          targetCode: templateCode,
          datasetCode: params.datasetCode,
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
        datasetCode: params.datasetCode,
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
        throw new Error('PDF 生成失败');
      }

      await runPrintJob({
        pdfBlob: blob,
        clientJobId,
        jobName: params.jobName ?? templateCode,
        onFallbackBrowser: printPdfFromBlob,
        printLog: {
          targetType: TARGET_TYPE,
          targetCode: templateCode,
          datasetCode: params.datasetCode,
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
    printReportTemplate,
  };
}
