import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { usePrintJob } from '#/composables/use-print-job';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';
import type { SpecimenBarcodePrintTarget } from '#/utils/report/nurse-station-print-targets';

const DEFAULT_TEMPLATE_CODE = 'bedside_specimen_barcode';
const DATASET_CODE = 'bedside_specimen_barcode';
const TARGET_TYPE = 'TEMPLATE';

export interface BedsideSpecimenBarcodePrintParams {
  encounterId: string;
  targets: SpecimenBarcodePrintTarget[];
  templateCode?: string;
  jobName?: string;
}

function buildBizRef(
  encounterId: string,
  target: SpecimenBarcodePrintTarget,
): Record<string, unknown> {
  return {
    encounterId,
    serviceRequestId: target.serviceRequestId,
  };
}

export function useBedsideSpecimenBarcodePrint() {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);

  async function printSingleTarget(
    encounterId: string,
    target: SpecimenBarcodePrintTarget,
    templateCode: string,
    jobName: string,
  ): Promise<void> {
    const clientJobId = crypto.randomUUID();
    const bizRef = buildBizRef(encounterId, target);
    const previewStarted = performance.now();
    let previewEventId: number | undefined;

    let result: PreviewResultDto;
    try {
      result = await reportDesignPreviewApi({
        templateCode,
        useSampleData: false,
        params: {
          encounterId,
          serviceRequestId: target.serviceRequestId,
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
        errorMessage: '采血条码 PDF 为空',
        parentEventId: previewEventId,
      });
      throw new Error('采血条码 PDF 生成失败');
    }

    await runPrintJob({
      pdfBlob: blob,
      clientJobId,
      jobName,
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
  }

  async function printBedsideSpecimenBarcodes(
    params: BedsideSpecimenBarcodePrintParams,
  ): Promise<void> {
    const encounterId = params.encounterId?.trim();
    if (!encounterId) {
      ElMessage.warning('缺少就诊信息，无法打印采血条码');
      return;
    }
    if (!params.targets?.length) {
      ElMessage.warning('没有可打印的采血/检验医嘱');
      return;
    }

    const templateCode = params.templateCode?.trim() || DEFAULT_TEMPLATE_CODE;
    previewLoading.value = true;
    try {
      for (let i = 0; i < params.targets.length; i += 1) {
        const target = params.targets[i]!;
        const suffix = params.targets.length > 1 ? ` (${i + 1}/${params.targets.length})` : '';
        await printSingleTarget(
          encounterId,
          target,
          templateCode,
          `${params.jobName ?? '采血条码'}${suffix}`,
        );
      }
    } finally {
      previewLoading.value = false;
    }
  }

  return {
    isPrinting,
    previewLoading,
    printBedsideSpecimenBarcodes,
  };
}
