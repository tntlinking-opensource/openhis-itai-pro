import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { usePrintJob } from '#/composables/use-print-job';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';
import type { InfusionLabelPrintGroup } from '#/utils/report/iv-infusion-label-print';

const DEFAULT_TEMPLATE_CODE = 'iv_infusion_label';
const DATASET_CODE = 'iv_infusion_label';
const TARGET_TYPE = 'TEMPLATE';

export interface IvInfusionLabelPrintParams {
  encounterId: string;
  groups: InfusionLabelPrintGroup[];
  templateCode?: string;
  jobName?: string;
  labelScope?: 'infusion' | 'injection' | 'nebulization' | 'oral' | 'skin_test';
}

function buildBizRef(encounterId: string, group: InfusionLabelPrintGroup): Record<string, unknown> {
  const bizRef: Record<string, unknown> = {
    encounterId,
    medicationRequestIds: group.medicationRequestIds,
  };
  if (group.groupId) {
    bizRef.groupId = group.groupId;
  }
  return bizRef;
}

export function useIvInfusionLabelPrint() {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);

  async function printSingleGroup(
    encounterId: string,
    group: InfusionLabelPrintGroup,
    templateCode: string,
    jobName: string,
    labelScope?: IvInfusionLabelPrintParams['labelScope'],
  ): Promise<void> {
    const clientJobId = crypto.randomUUID();
    const bizRef = buildBizRef(encounterId, group);
    const previewStarted = performance.now();
    let previewEventId: number | undefined;

    const previewParams: Record<string, unknown> = {
      encounterId,
      medicationRequestIds: group.medicationRequestIds,
    };
    if (group.groupId) {
      previewParams.groupId = group.groupId;
    }
    if (labelScope) {
      previewParams.labelScope = labelScope;
    }

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
        errorMessage: '输液瓶贴 PDF 为空',
        parentEventId: previewEventId,
      });
      throw new Error('输液瓶贴 PDF 生成失败');
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

  async function printIvInfusionLabels(params: IvInfusionLabelPrintParams): Promise<void> {
    const encounterId = params.encounterId?.trim();
    if (!encounterId) {
      ElMessage.warning('缺少就诊信息，无法打印输液瓶贴');
      return;
    }
    if (!params.groups?.length) {
      ElMessage.warning('没有可打印的输液组');
      return;
    }

    const templateCode = params.templateCode?.trim() || DEFAULT_TEMPLATE_CODE;
    previewLoading.value = true;
    try {
      for (let i = 0; i < params.groups.length; i += 1) {
        const group = params.groups[i]!;
        const suffix = params.groups.length > 1 ? ` (${i + 1}/${params.groups.length})` : '';
        await printSingleGroup(
          encounterId,
          group,
          templateCode,
          `${params.jobName ?? '输液瓶贴'}${suffix}`,
          params.labelScope,
        );
      }
    } finally {
      previewLoading.value = false;
    }
  }

  return {
    isPrinting,
    previewLoading,
    printIvInfusionLabels,
  };
}
