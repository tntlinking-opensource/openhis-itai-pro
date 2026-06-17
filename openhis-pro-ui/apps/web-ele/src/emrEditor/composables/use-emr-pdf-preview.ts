import type { JSONContent } from '@tiptap/core';

import type { PrintConfigDto } from '#/api/emr/print-config.types';

import { onBeforeUnmount, ref } from 'vue';

import { usePdfPrint } from '@vben/hooks';

import { ElMessage } from 'element-plus';

import { generatePdfApi, mergePdfApi } from '#/api/emr/pdf';
import { serializeEmrDocumentContent } from '#/api/emr/document';
import { usePrintJob } from '#/composables/use-print-job';

import { computeEmrContentHash } from '../utils/emr-content-hash';
import { pdfBase64ToBlob, pdfBase64ToObjectUrl, revokePdfObjectUrl } from '../utils/emr-pdf-blob';
import {
  buildPdfMetadataFromPatient,
  buildPrintConfigFromEditor,
  type EditorPageMargins,
} from '../utils/emr-print-config';

export interface UseEmrPdfPreviewContext {
  getContentJson: () => JSONContent | null | undefined;
  getDocumentId: () => string;
  getPatientId: () => string;
  getEncounterId: () => string;
  getDocumentTitle: () => string;
  getDocumentTypeName: () => string;
  getPageMargins: () => EditorPageMargins | undefined;
  getMetadata: () => Record<string, string>;
  getPatientInfo?: () =>
    | {
        age?: string;
        gender?: string;
        medicalRecordNo?: string;
        name?: string;
      }
    | undefined;
  isDirty?: () => boolean;
}

export function useEmrPdfPreview(ctx: UseEmrPdfPreviewContext) {
  const loading = ref(false);
  const errorMessage = ref('');
  const previewStale = ref(false);
  const pageCount = ref<number | undefined>();
  const previewTruncated = ref(false);
  const renderedAtLabel = ref('');
  const pdfBlobUrl = ref('');
  const lastSyncedHash = ref('');

  let lastPdfUrl = '';

  const { printFromBlob, downloadBlob, isPrinting } = usePdfPrint();
  const { runPrintJob, isPrinting: isSilentPrinting } = usePrintJob();

  function setPdfUrl(url: string) {
    revokePdfObjectUrl(lastPdfUrl);
    lastPdfUrl = url;
    pdfBlobUrl.value = url;
  }

  function markPreviewStale() {
    if (lastSyncedHash.value) {
      previewStale.value = true;
    }
  }

  function buildGenerateParams(previewMode: boolean) {
    const contentObj = ctx.getContentJson();
    const content = serializeEmrDocumentContent(contentObj);
    const printConfig = buildPrintConfigFromEditor(ctx.getPageMargins());
    const metadata = {
      ...buildPdfMetadataFromPatient(ctx.getPatientInfo?.(), ctx.getMetadata()),
      ...ctx.getMetadata(),
    };
    return {
      content,
      printConfig,
      metadata,
      previewMode,
      previewMaxPages: previewMode ? 20 : undefined,
      documentId: ctx.getDocumentId() || undefined,
      patientId: ctx.getPatientId() || undefined,
      encounterId: ctx.getEncounterId() || undefined,
      documentTitle: ctx.getDocumentTitle() || undefined,
      documentTypeName: ctx.getDocumentTypeName() || undefined,
      contentHashPromise: computeEmrContentHash(content, JSON.stringify(printConfig)),
    };
  }

  function buildMergeContext() {
    const printConfig = buildPrintConfigFromEditor(ctx.getPageMargins());
    const metadata = {
      ...buildPdfMetadataFromPatient(ctx.getPatientInfo?.(), ctx.getMetadata()),
      ...ctx.getMetadata(),
    };
    return { printConfig, metadata };
  }

  function applyMergePdfResult(
    res: Awaited<ReturnType<typeof mergePdfApi>>,
    previewMode: boolean,
    contentHash: string,
  ) {
    if (!res.pdfBase64) {
      throw new Error('服务端未返回 PDF 数据');
    }
    setPdfUrl(pdfBase64ToObjectUrl(res.pdfBase64));
    pageCount.value = res.pageCount;
    previewTruncated.value = Boolean(res.previewTruncated);
    lastSyncedHash.value = contentHash;
    previewStale.value = false;
    if (res.renderedAt) {
      const d = new Date(res.renderedAt);
      renderedAtLabel.value = Number.isNaN(d.getTime())
        ? String(res.renderedAt)
        : d.toLocaleString();
    } else {
      renderedAtLabel.value = new Date().toLocaleString();
    }
    if (previewTruncated.value && previewMode) {
      ElMessage.warning('仅预览前 20 页，正式导出可查看全文');
    }
  }

  async function requestMergedPdf(documentIds: string[], previewMode: boolean) {
    const ids = documentIds.map((id) => id.trim()).filter(Boolean);
    if (!ids.length) {
      ElMessage.warning('没有可合并的病程记录');
      return null;
    }
    loading.value = true;
    errorMessage.value = '';
    try {
      const { printConfig, metadata } = buildMergeContext();
      const contentHash = await computeEmrContentHash(ids.join(','), JSON.stringify(printConfig));
      const res = await mergePdfApi({
        documentIds: ids,
        previewMode,
        printConfig,
        metadata,
        patientId: ctx.getPatientId() || undefined,
        encounterId: ctx.getEncounterId() || undefined,
      });
      applyMergePdfResult(res, previewMode, contentHash);
      return res;
    } catch (e) {
      errorMessage.value = (e as Error)?.message || '合并 PDF 生成失败';
      throw e;
    } finally {
      loading.value = false;
    }
  }

  async function refreshMergedPreview(documentIds: string[]) {
    return requestMergedPdf(documentIds, true);
  }

  async function exportMergedPdf(documentIds: string[], fileName?: string) {
    const res = await requestMergedPdf(documentIds, false);
    if (!res?.pdfBase64) return;
    const name = fileName || `病程记录合并-${Date.now()}.pdf`;
    downloadBlob(pdfBase64ToBlob(res.pdfBase64), name);
    ElMessage.success('PDF 已下载');
  }

  async function printMergedFormal(documentIds: string[]) {
    return printMergedFormalSilent(documentIds);
  }

  async function requestPdf(previewMode: boolean) {
    loading.value = true;
    errorMessage.value = '';
    try {
      const params = buildGenerateParams(previewMode);
      const contentHash = await params.contentHashPromise;
      const res = await generatePdfApi({
        content: params.content,
        printConfig: params.printConfig,
        metadata: params.metadata,
        previewMode,
        previewMaxPages: params.previewMaxPages,
        documentId: params.documentId,
        patientId: params.patientId,
        encounterId: params.encounterId,
        documentTitle: params.documentTitle,
        documentTypeName: params.documentTypeName,
        contentHash,
      });

      if (!res.pdfBase64) {
        throw new Error('服务端未返回 PDF 数据');
      }

      setPdfUrl(pdfBase64ToObjectUrl(res.pdfBase64));
      pageCount.value = res.pageCount;
      previewTruncated.value = Boolean(res.previewTruncated);
      lastSyncedHash.value = contentHash;
      previewStale.value = false;

      if (res.renderedAt) {
        const d = new Date(res.renderedAt);
        renderedAtLabel.value = Number.isNaN(d.getTime())
          ? String(res.renderedAt)
          : d.toLocaleString();
      } else {
        renderedAtLabel.value = new Date().toLocaleString();
      }

      if (previewTruncated.value && previewMode) {
        ElMessage.warning('仅预览前 20 页，正式导出可查看全文');
      }
      return res;
    } catch (e) {
      errorMessage.value = (e as Error)?.message || 'PDF 生成失败';
      throw e;
    } finally {
      loading.value = false;
    }
  }

  async function refreshPreview(options?: { force?: boolean }) {
    if (!options?.force && ctx.isDirty?.()) {
      previewStale.value = true;
    }
    return requestPdf(true);
  }

  function openPreviewInNewTab() {
    if (!pdfBlobUrl.value) {
      ElMessage.warning('请先生成预览');
      return;
    }
    window.open(pdfBlobUrl.value, '_blank', 'noopener,noreferrer');
  }

  async function exportPdf(fileName?: string) {
    const res = await requestPdf(false);
    if (!res?.pdfBase64) return;
    const name =
      fileName || `${(ctx.getDocumentTitle() || '病历').trim() || '病历'}-${Date.now()}.pdf`;
    downloadBlob(pdfBase64ToBlob(res.pdfBase64), name);
    ElMessage.success('PDF 已下载');
  }

  async function printFormal() {
    return printFormalSilent();
  }

  async function printFormalSilent() {
    const res = await requestPdf(false);
    if (!res?.pdfBase64) return;
    const blob = pdfBase64ToBlob(res.pdfBase64);
    const documentId = ctx.getDocumentId() || 'emr';
    await runPrintJob({
      pdfBlob: blob,
      jobName: ctx.getDocumentTitle() || '病历',
      onFallbackBrowser: (fallbackBlob) =>
        printFromBlob(fallbackBlob, {
          title: ctx.getDocumentTitle() || '病历打印',
          autoPrint: true,
        }),
      printLog: {
        targetType: 'EMR',
        targetCode: documentId,
        contentHash: lastSyncedHash.value,
        contentType: 'application/pdf',
      },
    });
  }

  async function printMergedFormalSilent(documentIds: string[]) {
    const res = await requestMergedPdf(documentIds, false);
    if (!res?.pdfBase64) return;
    const blob = pdfBase64ToBlob(res.pdfBase64);
    const encounterId = ctx.getEncounterId() || 'merge';
    await runPrintJob({
      pdfBlob: blob,
      jobName: '病程记录合并打印',
      onFallbackBrowser: (fallbackBlob) =>
        printFromBlob(fallbackBlob, {
          title: '病程记录合并打印',
          autoPrint: true,
        }),
      printLog: {
        targetType: 'EMR',
        targetCode: `merge-${encounterId}`,
        contentHash: lastSyncedHash.value,
        contentType: 'application/pdf',
      },
    });
  }

  onBeforeUnmount(() => {
    revokePdfObjectUrl(lastPdfUrl);
  });

  return {
    loading,
    errorMessage,
    previewStale,
    pageCount,
    previewTruncated,
    renderedAtLabel,
    pdfBlobUrl,
    isPrinting,
    isSilentPrinting,
    markPreviewStale,
    refreshPreview,
    openPreviewInNewTab,
    exportPdf,
    printFormal,
    printFormalSilent,
    refreshMergedPreview,
    exportMergedPdf,
    printMergedFormal,
    printMergedFormalSilent,
  };
}
