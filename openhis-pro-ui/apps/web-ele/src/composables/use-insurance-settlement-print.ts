import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { usePrintJob } from '#/composables/use-print-job';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';

const DEFAULT_TEMPLATE_CODE = 'insurance_settlement';
const DATASET_CODE = 'insurance_settlement';
const TARGET_TYPE = 'TEMPLATE';

export interface InsuranceSettlementPrintParams {
  paymentId: string;
  encounterId: string;
  patientId: string;
  contractDisplay?: string;
  templateCode?: string;
  jobName?: string;
}

function buildBizRef(params: InsuranceSettlementPrintParams): Record<string, unknown> {
  const bizRef: Record<string, unknown> = {
    paymentId: params.paymentId,
    encounterId: params.encounterId,
    patientId: params.patientId,
  };
  if (params.contractDisplay?.trim()) {
    bizRef.contractDisplay = params.contractDisplay.trim();
  }
  return bizRef;
}

function buildPreviewParams(params: InsuranceSettlementPrintParams): Record<string, unknown> {
  const previewParams: Record<string, unknown> = {
    paymentId: params.paymentId,
    encounterId: params.encounterId,
    patientId: params.patientId,
  };
  if (params.contractDisplay?.trim()) {
    previewParams.contractDisplay = params.contractDisplay.trim();
  }
  return previewParams;
}

export function useInsuranceSettlementPrint() {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);

  async function printInsuranceSettlement(params: InsuranceSettlementPrintParams): Promise<void> {
    const paymentId = params.paymentId?.trim();
    const encounterId = params.encounterId?.trim();
    const patientId = params.patientId?.trim();
    if (!paymentId) {
      ElMessage.warning('缺少结算单，无法打印医保结算单');
      return;
    }
    if (!encounterId || !patientId) {
      ElMessage.warning('请先选择患者');
      return;
    }

    const templateCode = params.templateCode?.trim() || DEFAULT_TEMPLATE_CODE;
    const clientJobId = crypto.randomUUID();
    const normalized = { ...params, paymentId, encounterId, patientId };
    const bizRef = buildBizRef(normalized);
    const previewParams = buildPreviewParams(normalized);

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
          errorMessage: '医保结算单 PDF 为空',
          parentEventId: previewEventId,
        });
        throw new Error('医保结算单 PDF 生成失败');
      }

      await runPrintJob({
        pdfBlob: blob,
        clientJobId,
        jobName: params.jobName ?? '医保结算单',
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
    printInsuranceSettlement,
  };
}
