import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { usePrintJob } from '#/composables/use-print-job';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';

const DEFAULT_TEMPLATE_CODE = 'order_execute_sheet';
const DATASET_CODE = 'order_execute_sheet';
const TARGET_TYPE = 'TEMPLATE';

export interface OrderExecuteSheetPrintParams {
  encounterId: string;
  executeTab: string;
  therapyType: string;
  executionDeadline: string;
  listRowKeys?: string[];
  templateCode?: string;
  jobName?: string;
}

function buildBizRef(params: OrderExecuteSheetPrintParams): Record<string, unknown> {
  const bizRef: Record<string, unknown> = {
    encounterId: params.encounterId,
    executeTab: params.executeTab,
    therapyType: params.therapyType,
    executionDeadline: params.executionDeadline,
  };
  if (params.listRowKeys?.length) {
    bizRef.listRowKeys = params.listRowKeys;
  }
  return bizRef;
}

export function useOrderExecuteSheetPrint() {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);

  async function printOrderExecuteSheet(params: OrderExecuteSheetPrintParams): Promise<void> {
    const encounterId = params.encounterId?.trim();
    const executionDeadline = params.executionDeadline?.trim();
    if (!encounterId) {
      ElMessage.warning('缺少就诊信息，无法打印医嘱执行单');
      return;
    }
    if (!executionDeadline) {
      ElMessage.warning('请先选择截止时间');
      return;
    }

    const templateCode = params.templateCode?.trim() || DEFAULT_TEMPLATE_CODE;
    const clientJobId = crypto.randomUUID();
    const bizRef = buildBizRef({
      ...params,
      encounterId,
      executionDeadline,
      executeTab: params.executeTab?.trim() || 'pending',
      therapyType: params.therapyType?.trim() || 'all',
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
            executeTab: bizRef.executeTab,
            therapyType: bizRef.therapyType,
            executionDeadline,
            listRowKeys: params.listRowKeys,
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
        throw new Error('医嘱执行单 PDF 生成失败');
      }

      await runPrintJob({
        pdfBlob: blob,
        clientJobId,
        jobName: params.jobName ?? '医嘱执行单',
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
    printOrderExecuteSheet,
  };
}
