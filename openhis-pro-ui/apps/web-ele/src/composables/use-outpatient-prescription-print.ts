import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { usePrintJob } from '#/composables/use-print-job';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';
import { resolveOutpatientPrescriptionTemplateCode } from '#/utils/report/outpatient-prescription-print-config';

const DATASET_CODE = 'outpatient_prescription';
const TARGET_TYPE = 'TEMPLATE';

export interface OutpatientPrescriptionPrintParams {
  encounterId: string;
  prescriptionNo?: string;
  prescriptionGroupId?: string;
  medicationRequestIds?: string[];
  westernOnly?: boolean;
  tcmOnly?: boolean;
  narcoticOnly?: boolean;
  dispenseStatusEnumIn?: string[];
  templateCode?: string;
  jobName?: string;
}

function buildBizRef(params: OutpatientPrescriptionPrintParams) {
  const bizRef: Record<string, unknown> = {
    encounterId: params.encounterId,
  };
  if (params.prescriptionNo) {
    bizRef.prescriptionNo = params.prescriptionNo;
  }
  if (params.prescriptionGroupId) {
    bizRef.prescriptionGroupId = params.prescriptionGroupId;
  }
  if (params.medicationRequestIds?.length) {
    bizRef.medicationRequestIds = params.medicationRequestIds;
  }
  return bizRef;
}

export function useOutpatientPrescriptionPrint() {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);

  async function printPrescription(params: OutpatientPrescriptionPrintParams): Promise<void> {
    const encounterId = params.encounterId?.trim();
    if (!encounterId) {
      ElMessage.warning('请先选择患者');
      return;
    }
    const prescriptionNo = params.prescriptionNo?.trim();
    const prescriptionGroupId = params.prescriptionGroupId?.trim();
    const medicationRequestIds =
      params.medicationRequestIds?.map((id) => id.trim()).filter(Boolean) ?? [];
    if (!prescriptionNo && !prescriptionGroupId && !medicationRequestIds.length) {
      ElMessage.warning('请先选择处方');
      return;
    }

    const templateCode = resolveOutpatientPrescriptionTemplateCode({
      templateCode: params.templateCode,
      narcoticOnly: params.narcoticOnly,
      tcmOnly: params.tcmOnly,
    });
    const clientJobId = crypto.randomUUID();
    const bizRef = buildBizRef({
      ...params,
      encounterId,
      prescriptionNo,
      prescriptionGroupId,
    });

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
            prescriptionNo: prescriptionNo || undefined,
            prescriptionGroupId: prescriptionGroupId || undefined,
            medicationRequestIds: medicationRequestIds.length ? medicationRequestIds : undefined,
            westernOnly: params.westernOnly,
            tcmOnly: params.tcmOnly,
            dispenseStatusEnumIn: params.dispenseStatusEnumIn,
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
          errorMessage: '预览 PDF 为空',
          parentEventId: previewEventId,
        });
        ElMessage.error('处方 PDF 生成失败');
        return;
      }

      await runPrintJob({
        pdfBlob: blob,
        clientJobId,
        jobName: params.jobName ?? '门诊处方',
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
    printPrescription,
  };
}
