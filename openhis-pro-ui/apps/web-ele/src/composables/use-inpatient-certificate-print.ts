import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { usePrintJob } from '#/composables/use-print-job';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';

export interface InpatientCertificatePrintParams {
  encounterId: string;
  templateCode: string;
  datasetCode: string;
  jobName?: string;
}

export function useInpatientCertificatePrint() {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);

  async function printInpatientCertificate(params: InpatientCertificatePrintParams): Promise<void> {
    const encounterId = params.encounterId?.trim();
    const templateCode = params.templateCode?.trim();
    if (!encounterId) {
      ElMessage.warning('缺少就诊信息，无法打印');
      return;
    }
    if (!templateCode) {
      ElMessage.warning('缺少打印模板');
      return;
    }

    const clientJobId = crypto.randomUUID();
    const bizRef = { encounterId };
    previewLoading.value = true;
    const previewStarted = performance.now();
    let previewEventId: number | undefined;

    try {
      let result: PreviewResultDto;
      try {
        result = await reportDesignPreviewApi({
          templateCode,
          useSampleData: false,
          params: { encounterId },
        });
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        await writeReportPrintLog({
          eventType: 'PREVIEW',
          targetType: 'TEMPLATE',
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
        targetType: 'TEMPLATE',
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
          targetType: 'TEMPLATE',
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
    printInpatientCertificate,
  };
}

export function useAdmissionCertificatePrint() {
  const base = useInpatientCertificatePrint();

  async function printAdmissionCertificate(params: {
    encounterId: string;
    jobName?: string;
  }): Promise<void> {
    await base.printInpatientCertificate({
      encounterId: params.encounterId,
      templateCode: 'admission_certificate',
      datasetCode: 'admission_certificate',
      jobName: params.jobName ?? '入院证',
    });
  }

  return {
    ...base,
    printAdmissionCertificate,
  };
}

export function useDischargeCertificatePrint() {
  const base = useInpatientCertificatePrint();

  async function printDischargeCertificate(params: {
    encounterId: string;
    jobName?: string;
  }): Promise<void> {
    await base.printInpatientCertificate({
      encounterId: params.encounterId,
      templateCode: 'discharge_certificate',
      datasetCode: 'discharge_certificate',
      jobName: params.jobName ?? '出院证',
    });
  }

  return {
    ...base,
    printDischargeCertificate,
  };
}
