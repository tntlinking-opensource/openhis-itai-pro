import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import type { PreviewResultDto } from '#/api/report/report-design';

import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { usePrintJob } from '#/composables/use-print-job';
import { getCachedPrintTemplateCode, PRINT_BINDING } from '#/utils/report/print-binding-store';
import { writeReportPrintLog } from '#/utils/report/write-report-print-log';

const DEFAULT_TEMPLATE_CODE = 'inpatient_daily_charge_list';
const DATASET_CODE = 'inpatient_daily_charge_list';
const TARGET_TYPE = 'TEMPLATE';

export interface InpatientDailyChargePrintParams {
  encounterId: string;
  billDate?: string;
  dateFrom?: string;
  dateTo?: string;
  templateCode?: string;
  datasetCode?: string;
  jobName?: string;
}

function buildBizRef(params: InpatientDailyChargePrintParams): Record<string, unknown> {
  const bizRef: Record<string, unknown> = {
    encounterId: params.encounterId,
  };
  if (params.billDate?.trim()) {
    bizRef.billDate = params.billDate.trim();
  }
  if (params.dateFrom?.trim()) {
    bizRef.dateFrom = params.dateFrom.trim();
  }
  if (params.dateTo?.trim()) {
    bizRef.dateTo = params.dateTo.trim();
  }
  return bizRef;
}

export function useInpatientDailyChargePrint() {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);

  async function printInpatientDailyChargeList(
    params: InpatientDailyChargePrintParams,
  ): Promise<void> {
    const encounterId = params.encounterId?.trim();
    if (!encounterId) {
      ElMessage.warning('缺少就诊信息，无法打印日费用清单');
      return;
    }

    const templateCode =
      params.templateCode?.trim() ||
      getCachedPrintTemplateCode(PRINT_BINDING.INPATIENT_DAILY_CHARGE_PRINT, DEFAULT_TEMPLATE_CODE);
    const datasetCode = params.datasetCode?.trim() || DATASET_CODE;
    const clientJobId = crypto.randomUUID();
    const bizRef = buildBizRef(params);
    const previewParams: Record<string, unknown> = { encounterId, templateCode };
    if (params.billDate?.trim()) {
      previewParams.billDate = params.billDate.trim();
    }
    if (params.dateFrom?.trim()) {
      previewParams.dateFrom = params.dateFrom.trim();
    }
    if (params.dateTo?.trim()) {
      previewParams.dateTo = params.dateTo.trim();
    }

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
        await writeReportPrintLog({
          eventType: 'PREVIEW',
          targetType: TARGET_TYPE,
          targetCode: templateCode,
          datasetCode,
          bizRef,
          copies: 0,
          status: 'FAILED',
          clientJobId,
          contentHash: result.contentHash,
          renderDurationMs,
          errorCode: 'PREVIEW_EMPTY_BODY',
          errorMessage: '住院日费用清单 PDF 为空',
          parentEventId: previewEventId,
        });
        throw new Error('住院日费用清单 PDF 生成失败');
      }

      await runPrintJob({
        pdfBlob: blob,
        clientJobId,
        jobName: params.jobName ?? '住院日费用清单',
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

  async function printInpatientDailyChargeLists(
    items: InpatientDailyChargePrintParams[],
  ): Promise<void> {
    if (!items.length) {
      ElMessage.warning('没有可打印的患者');
      return;
    }
    for (let i = 0; i < items.length; i += 1) {
      const item = items[i]!;
      const suffix = items.length > 1 ? ` (${i + 1}/${items.length})` : '';
      await printInpatientDailyChargeList({
        ...item,
        jobName: `${item.jobName ?? '住院日费用清单'}${suffix}`,
      });
    }
  }

  return {
    isPrinting,
    previewLoading,
    printInpatientDailyChargeList,
    printInpatientDailyChargeLists,
  };
}
