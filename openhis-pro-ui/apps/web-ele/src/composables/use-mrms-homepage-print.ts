import { ref } from 'vue';

import { printPdfFromBlob } from '@vben/utils';

import { ElMessage } from 'element-plus';

import { generateHomepagePdfApi } from '#/api/mrms';
import { usePrintJob } from '#/composables/use-print-job';
import { pdfBase64ToBlob, revokePdfObjectUrl } from '#/emrEditor/utils/emr-pdf-blob';

export function useMrmsHomepagePrint() {
  const { isPrinting, runPrintJob } = usePrintJob();
  const previewLoading = ref(false);

  async function requestHomepagePdf(caseRecordId: string) {
    const id = caseRecordId?.trim();
    if (!id) {
      throw new Error('缺少病案主键');
    }
    previewLoading.value = true;
    try {
      const result = await generateHomepagePdfApi(id);
      if (!result?.pdfBase64) {
        throw new Error('未生成 PDF');
      }
      return result;
    } finally {
      previewLoading.value = false;
    }
  }

  async function previewHomepagePdf(caseRecordId: string): Promise<void> {
    let url: string | undefined;
    try {
      const result = await requestHomepagePdf(caseRecordId);
      url = URL.createObjectURL(pdfBase64ToBlob(result));
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '病案首页 PDF 生成失败');
      throw error;
    } finally {
      if (url) {
        revokePdfObjectUrl(url);
      }
    }
  }

  async function printHomepagePdf(caseRecordId: string, patientName?: string): Promise<void> {
    const id = caseRecordId?.trim();
    if (!id) {
      ElMessage.warning('缺少病案主键，无法打印');
      return;
    }

    const result = await requestHomepagePdf(id);
    const blob = pdfBase64ToBlob(result.pdfBase64);
    const clientJobId = crypto.randomUUID();
    const jobName = patientName?.trim() ? `病案首页-${patientName.trim()}` : '病案首页';

    await runPrintJob({
      pdfBlob: blob,
      clientJobId,
      jobName,
      onFallbackBrowser: printPdfFromBlob,
      printLog: {
        targetType: 'MRMS',
        targetCode: id,
        contentHash: result.contentHash,
        contentType: 'application/pdf',
        clientJobId,
      },
    });
  }

  return {
    isPrinting,
    previewLoading,
    previewHomepagePdf,
    printHomepagePdf,
  };
}
