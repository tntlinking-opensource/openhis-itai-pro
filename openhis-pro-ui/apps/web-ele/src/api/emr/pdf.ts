/**
 * 文书 PDF 生成 API（DocumentAppService.generatePdf / mergePdf）
 */

import type { PrintConfigDto } from '#/api/emr/print-config.types';

import { publicRequestClient } from '#/api/request';

export interface GeneratePdfParams {
  /** 雪花 ID 建议用字符串；也可仅传 content */
  documentId?: string;
  content?: string;
  previewMode?: boolean;
  previewMaxPages?: number;
  patientId?: string;
  encounterId?: string;
  documentTitle?: string;
  documentTypeName?: string;
  pageRange?: string;
  regionNames?: string[];
  includeTrackChanges?: boolean;
  watermark?: string;
  headerTemplate?: string;
  footerTemplate?: string;
  printConfig?: PrintConfigDto;
  contentHash?: string;
  metadata?: Record<string, string>;
}

export interface PdfGenerateResultDto {
  url?: string;
  token?: string;
  pdfBase64?: string;
  pageCount?: number;
  contentHash?: string;
  renderedAt?: string;
  previewTruncated?: boolean;
}

export interface MergePdfParams {
  documentIds: string[];
  previewMode?: boolean;
  printConfig?: PrintConfigDto;
  patientId?: string;
  encounterId?: string;
  metadata?: Record<string, string>;
}

export async function generatePdfApi(params: GeneratePdfParams) {
  return publicRequestClient.post<PdfGenerateResultDto>('/document/generatePdf', params);
}

export async function mergePdfApi(params: MergePdfParams) {
  return publicRequestClient.post<PdfGenerateResultDto>('/document/mergePdf', params);
}
