import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { usePrintJob } from '#/composables/use-print-job';
import type { TreatmentExecutePrintTarget } from '#/utils/report/nurse-station-print-targets';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';

export type ServiceApplyFormScope = 'exam' | 'lab' | 'procedure';

const TEMPLATE_BY_SCOPE: Record<ServiceApplyFormScope, string> = {
  lab: 'lab_apply_form',
  exam: 'exam_apply_form',
  procedure: 'procedure_apply_form',
};

const DATASET_BY_SCOPE: Record<ServiceApplyFormScope, string> = {
  lab: 'lab_apply_form',
  exam: 'exam_apply_form',
  procedure: 'procedure_apply_form',
};

const JOB_NAME_BY_SCOPE: Record<ServiceApplyFormScope, string> = {
  lab: '检验申请单',
  exam: '检查申请单',
  procedure: '治疗/处置申请单',
};

const TARGET_TYPE = 'TEMPLATE';

export interface ServiceApplyFormPrintParams {
  encounterId: string;
  target: TreatmentExecutePrintTarget;
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

export function useServiceApplyFormPrint(scope: ServiceApplyFormScope) {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);
  const templateCode = TEMPLATE_BY_SCOPE[scope];
  const datasetCode = DATASET_BY_SCOPE[scope];

  async function printServiceApplyForm(params: ServiceApplyFormPrintParams): Promise<void> {
    const encounterId = params.encounterId?.trim();
    if (!encounterId) {
      ElMessage.warning('缺少就诊信息，无法打印申请单');
      return;
    }
    if (!params.target?.serviceRequestIds?.length) {
      ElMessage.warning(`没有可打印的${JOB_NAME_BY_SCOPE[scope]}`);
      return;
    }

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
          datasetCode,
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
        datasetCode,
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
        throw new Error('申请单 PDF 生成失败');
      }

      await runPrintJob({
        pdfBlob: blob,
        clientJobId,
        jobName: params.jobName ?? JOB_NAME_BY_SCOPE[scope],
        onFallbackBrowser: printPdfFromBlob,
        printLog: {
          targetType: TARGET_TYPE,
          targetCode: templateCode,
          datasetCode,
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
    printServiceApplyForm,
    datasetCode,
  };
}

export function useLabApplyFormPrint() {
  return useServiceApplyFormPrint('lab');
}

export function useExamApplyFormPrint() {
  return useServiceApplyFormPrint('exam');
}

export function useProcedureApplyFormPrint() {
  return useServiceApplyFormPrint('procedure');
}
