/**
 * 打印排版设计 — 对接 {@code POST /api/app/reportDesign/<method>}
 */
import type { PreviewLayoutMetaDto } from '#/utils/report/report-preview';

import { publicRequestClient } from '#/api/request';

const BASE = '/reportDesign';

export interface ReportTemplateListItemDto {
  id?: number | string;
  code?: string;
  name?: string;
  pipeline?: string;
  layoutMode?: string;
  pageSize?: string;
  orientation?: string;
  datasetCode?: string;
  status?: string;
  version?: number;
  source?: 'platform' | 'tenant';
  tenantId?: number | string;
  updatedAt?: string;
  bizSeq?: string;
  priority?: string;
}

export interface ListTemplatesRequest {
  keyword?: string;
  status?: string;
  pipeline?: string;
  priority?: string;
}

export interface PrintElementDefDto {
  elementCode?: string;
  label?: string;
  renderType?: string;
  bindPath?: string;
  layoutMode?: string;
  required?: boolean;
  defaultLabel?: string;
  defaultStyle?: Record<string, unknown>;
  kvItems?: Array<Record<string, unknown>>;
  columns?: Array<Record<string, unknown>>;
  defaultBox?: Record<string, unknown>;
}

export interface ElementPackDto {
  packCode?: string;
  pipeline?: string;
  elements?: PrintElementDefDto[];
  sampleData?: Record<string, unknown>;
}

export interface ValidateLayoutRequest {
  packCode?: string;
  layoutJson?: string;
  forPublish?: boolean;
}

export interface ValidateLayoutResultDto {
  valid?: boolean;
  errors?: string[];
}

export interface PreviewRequest {
  templateCode?: string;
  /** 元素 pack，设计器 inline 预览应与模板 datasetCode 一致 */
  packCode?: string;
  layoutJson?: string;
  useSampleData?: boolean;
  params?: Record<string, unknown>;
}

export interface PreviewResultDto {
  contentType?: string;
  fileName?: string;
  bodyBase64?: string;
  contentHash?: string;
  previewMeta?: PreviewLayoutMetaDto | null;
}

/** 模板列表（主路径，对齐 ReportTemplateAppService.getList） */
export async function listReportTemplatesApi(params?: ListTemplatesRequest) {
  return publicRequestClient.post<ReportTemplateListItemDto[]>(
    '/reportTemplate/getList',
    params ?? {},
  );
}

/** 兼容旧路径 */
export async function listReportTemplatesLegacyApi(params?: ListTemplatesRequest) {
  return publicRequestClient.post<ReportTemplateListItemDto[]>(
    `${BASE}/listTemplates`,
    params ?? {},
  );
}

export async function reportDesignPreviewApi(params: PreviewRequest) {
  return publicRequestClient.post<PreviewResultDto>(`${BASE}/preview`, params);
}

export async function getElementPackApi(params: { packCode?: string; layoutMode?: string }) {
  return publicRequestClient.post<ElementPackDto>(`${BASE}/getElementPack`, params);
}

export async function validateLayoutApi(params: ValidateLayoutRequest) {
  return publicRequestClient.post<ValidateLayoutResultDto>(`${BASE}/validateLayout`, params);
}

export function previewBase64ToBlob(result: PreviewResultDto): Blob | null {
  const b64 = result.bodyBase64?.trim();
  if (!b64) {
    return null;
  }
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return new Blob([bytes], { type: result.contentType ?? 'application/pdf' });
}
