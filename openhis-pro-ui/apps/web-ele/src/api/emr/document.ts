/**
 * 文书 API（whale-emr DocumentAppService）
 */

import type { PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 文书正文：后端 content 为 JSON 字符串 */
export function serializeEmrDocumentContent(content: unknown): string {
  if (content === undefined || content === null) return '{}';
  if (typeof content === 'string') return content;
  return JSON.stringify(content);
}

export interface DocumentContentDto {
  id?: number | string;
  patientId?: string;
  encounterId?: string;
  documentTypeCode?: string;
  /** 文书类型显示名（页眉 `{documentTitle}` 回退用） */
  documentTypeName?: string;
  title?: string;
  status?: string;
  content?: Record<string, unknown> | string | null;
  readOnly?: boolean;
  metadataValues?: Record<string, string>;
}

export interface GetDocumentContentParams {
  /** 雪花 ID 须用字符串传参 */
  documentId?: string;
  patientId?: string;
  encounterId?: string;
  documentTypeCode?: string;
}

export interface CreateDocumentParams {
  patientId: string;
  encounterId?: string;
  documentTypeCode: string;
  /** 雪花 ID 须用字符串传参 */
  templateId?: string;
}

export interface SaveDocumentParams {
  /** 雪花 ID 须用字符串传参 */
  documentId: string;
  content: unknown;
  saveVersion?: boolean;
}

export interface SaveDocumentResult {
  documentId?: number | string;
  newVersionNumber?: number;
}

/** 文书列表项（摘要） */
export interface DocumentSummaryDto {
  id?: number | string;
  patientId?: string;
  encounterId?: string;
  documentTypeCode?: string;
  documentTypeName?: string;
  title?: string;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: number | string;
  updatedBy?: number | string;
  /** 创建时使用的模板 ID（挂到模板树下） */
  sourceTemplateId?: number | string;
  /** 就诊科室名称 */
  deptName?: string;
  /** 保存人显示名 */
  savedByName?: string;
  /** 主诉摘要（列表树展示；无则空） */
  chiefComplaint?: string;
}

export interface QueryDocumentParams extends PageParams {
  patientId: string;
  encounterId?: string;
  /** 显式多就诊过滤；通常由服务端 includeLinkedEncounters 展开 */
  encounterIds?: string[];
  /** 为 true 时按门诊/住院 amb_encounter_id 关联展开就诊并集 */
  includeLinkedEncounters?: boolean;
  documentTypeCode?: string;
  status?: string;
  keyword?: string;
}

export async function getDocumentContentApi(params: GetDocumentContentParams) {
  return publicRequestClient.post<DocumentContentDto>('/document/getContent', params);
}

export async function createDocumentApi(params: CreateDocumentParams) {
  return publicRequestClient.post<DocumentContentDto>('/document/create', params);
}

export async function saveDocumentApi(params: SaveDocumentParams) {
  return publicRequestClient.post<SaveDocumentResult>('/document/save', {
    documentId: params.documentId,
    content: serializeEmrDocumentContent(params.content),
    saveVersion: params.saveVersion,
  });
}

export async function getDocumentListApi(params: QueryDocumentParams) {
  return publicRequestClient.post<PageApiResponse<DocumentSummaryDto>>('/document/getList', params);
}

export interface DeleteDocumentParams {
  /** 雪花 ID，必须以字符串提交 */
  documentId: string;
}

/** 软删除文书 → POST /document/delete（体为 { value }） */
export async function deleteDocumentApi(params: DeleteDocumentParams) {
  return publicRequestClient.post<void>('/document/delete', {
    value: preserveApiIdForRequest(params.documentId),
  });
}

/** 与后端 PrintConfig.MarginConfig 对齐（单位 mm） */
export interface EmrPrintMarginsMm {
  bottomMm?: number;
  leftMm?: number;
  rightMm?: number;
  topMm?: number;
  /** 屏上 ProseMirror padding 实测：PDF 正文区顶边距（mm），不再叠加 header 高度 */
  contentTopMm?: number;
  /** 屏上 ProseMirror padding 实测：PDF 正文区底边距（mm），不再叠加 footer 高度 */
  contentBottomMm?: number;
}

export interface EmrPrintHeaderPayload {
  enabled?: boolean;
  heightMm?: number;
  template?: string;
  firstPageDifferent?: boolean;
}

export interface EmrPrintFooterPayload {
  enabled?: boolean;
  heightMm?: number;
  /** 支持 {page} / {patientName} 等占位；长文案由服务端换行绘制 */
  template?: string;
  showPageNumber?: boolean;
}

/** 与后端 PrintConfig.PaperConfig 对齐（宽高为竖向毫米，横向由 orientation 翻转） */
export interface EmrPrintPaperPayload {
  heightMm?: number;
  orientation?: 'landscape' | 'portrait';
  widthMm?: number;
}

export interface EmrPrintConfigPayload {
  margins?: EmrPrintMarginsMm;
  header?: EmrPrintHeaderPayload;
  footer?: EmrPrintFooterPayload;
  paper?: EmrPrintPaperPayload;
}

export interface GenerateDocumentPdfParams {
  /** 雪花 ID 须用字符串；可与 content 同时传，正文优先用 content */
  documentId?: string;
  /** 当前编辑器正文（未保存也可预览/打印） */
  content?: unknown;
  /** true=预览；false=正式打印 */
  previewMode?: boolean;
  patientId?: string;
  encounterId?: string;
  documentTitle?: string;
  documentTypeName?: string;
  metadata?: Record<string, string>;
  printConfig?: EmrPrintConfigPayload;
}

export interface GenerateDocumentPdfResult {
  pdfBase64?: string;
  pageCount?: number;
  contentHash?: string;
  previewTruncated?: boolean;
  url?: string;
}

/**
 * 创建文书时 title 为「模板名-yyyy-MM-dd HH:mm」；打印页眉只出文书名。
 * 顺带去掉模板变体后缀「（标准）」/「标准」，与文书类型名（如「入院记录」）对齐。
 *
 * @param title 库内文书标题（可能带创建时刻）
 */
export function stripEmrPrintDocumentTitle(title: string | undefined): string | undefined {
  if (title == null) return title;
  const trimmed = title.trim();
  let stripped = trimmed.replace(/-\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/, '').trim();
  stripped = stripped
    .replace(/[（(]\s*标准\s*[）)]/g, '')
    .replace(/标准$/u, '')
    .trim();
  return stripped || trimmed;
}

/** 生成文书 PDF（OpenPDF）→ POST /document/generatePdf */
export async function generateDocumentPdfApi(params: GenerateDocumentPdfParams) {
  return publicRequestClient.post<GenerateDocumentPdfResult>('/document/generatePdf', {
    documentId: params.documentId,
    content:
      params.content === undefined || params.content === null
        ? undefined
        : serializeEmrDocumentContent(params.content),
    previewMode: params.previewMode ?? true,
    patientId: params.patientId,
    encounterId: params.encounterId,
    documentTitle: stripEmrPrintDocumentTitle(params.documentTitle),
    documentTypeName: params.documentTypeName,
    metadata: params.metadata,
    printConfig: params.printConfig,
  });
}
