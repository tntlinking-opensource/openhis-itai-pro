import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { usePrintJob } from '#/composables/use-print-job';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';

const TARGET_TYPE = 'REPORT';

export interface ReportCClassPdfParams {
  pdfTemplateCode: string;
  reportCode?: string;
  reportName?: string;
  params?: Record<string, unknown>;
}

export function useReportCClassPdf() {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);
  const previewBlob = ref<Blob | null>(null);

  async function renderPdfBlob(params: ReportCClassPdfParams): Promise<Blob | null> {
    const templateCode = params.pdfTemplateCode?.trim();
    if (!templateCode) {
      ElMessage.warning('当前报表未配置 PDF 模板');
      return null;
    }

    const previewParams: Record<string, unknown> = {
      ...(params.params ?? {}),
      templateCode,
    };
    if (params.reportCode?.trim()) {
      previewParams.reportCode = params.reportCode.trim();
    }

    previewLoading.value = true;
    try {
      const result: PreviewResultDto = await reportDesignPreviewApi({
        templateCode,
        useSampleData: false,
        params: previewParams,
      });
      const blob = previewBase64ToBlob(result);
      previewBlob.value = blob;
      return blob;
    } finally {
      previewLoading.value = false;
    }
  }

  async function previewPdf(params: ReportCClassPdfParams): Promise<Blob | null> {
    return renderPdfBlob(params);
  }

  async function downloadPdf(params: ReportCClassPdfParams, fileName?: string): Promise<void> {
    const blob = await renderPdfBlob(params);
    if (!blob) {
      return;
    }
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName?.trim() || `${params.reportCode ?? 'report'}.pdf`;
    link.click();
    URL.revokeObjectURL(url);
  }

  async function printPdf(params: ReportCClassPdfParams): Promise<void> {
    const templateCode = params.pdfTemplateCode?.trim();
    if (!templateCode) {
      ElMessage.warning('当前报表未配置 PDF 模板');
      return;
    }

    const clientJobId = crypto.randomUUID();
    const bizRef: Record<string, unknown> = {
      templateCode,
      reportCode: params.reportCode,
      ...(params.params ?? {}),
    };

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
            ...(params.params ?? {}),
            templateCode,
            reportCode: params.reportCode,
          },
        });
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        await writeReportPrintLog({
          eventType: 'PREVIEW',
          targetType: TARGET_TYPE,
          targetCode: params.reportCode ?? templateCode,
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
        targetCode: params.reportCode ?? templateCode,
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
      previewBlob.value = blob;

      await runPrintJob({
        pdfBlob: blob,
        clientJobId,
        jobName: params.reportName ?? params.reportCode ?? templateCode,
        onFallbackBrowser: printPdfFromBlob,
        printLog: {
          targetType: TARGET_TYPE,
          targetCode: params.reportCode ?? templateCode,
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
    previewBlob,
    previewPdf,
    downloadPdf,
    printPdf,
  };
}
