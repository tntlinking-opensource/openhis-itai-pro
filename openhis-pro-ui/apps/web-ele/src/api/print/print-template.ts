/**
 * 画布打印模板 — 对接 {@code POST /api/app/printTemplate/<method>}
 */
import type { PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

const BASE = '/printTemplate';

export interface PrtTemplateListItem {
  id?: string;
  busNo?: string;
  scenarioCode?: string;
  name?: string;
  /** draft / published / archived */
  status?: string;
  /** 当前生效发布版本 */
  version?: number;
  schemaVersion?: number;
  hasDraft?: boolean;
  draftId?: string;
  publishedId?: string;
  /** 租户自建模板可删，出厂平台模板为 false */
  deletable?: boolean;
}

export interface PrtTemplateDto {
  id?: string;
  busNo?: string;
  scenarioCode?: string;
  name?: string;
  status?: string;
  version?: number;
  schemaVersion?: number;
  PrintJson?: Record<string, unknown>;
  /** 兼容网关/序列化把 PrintJson 转成 camelCase */
  printJson?: Record<string, unknown>;
  samplePrintData?: Record<string, unknown>;
}

export interface QueryPrtTemplatePageParams extends PageParams {
  keyword?: string;
  status?: string;
}

export interface PagedPrtTemplateResult {
  content?: PrtTemplateListItem[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}

export interface CreateBlankDraftParams {
  name?: string;
  scenarioCode?: string;
}

export interface SaveDraftParams {
  id?: string;
  busNo?: string;
  scenarioCode?: string;
  name?: string;
  PrintJson: Record<string, unknown>;
  samplePrintData?: Record<string, unknown>;
}

export async function queryPrintTemplatePageApi(params: QueryPrtTemplatePageParams) {
  return publicRequestClient.post<PagedPrtTemplateResult>(`${BASE}/queryPage`, params);
}

export async function createBlankDraftApi(params?: CreateBlankDraftParams) {
  return publicRequestClient.post<PrtTemplateDto>(`${BASE}/createBlankDraft`, params ?? {});
}

export async function getByBusNoApi(busNo: string) {
  return publicRequestClient.post<PrtTemplateDto>(`${BASE}/getByBusNo`, { busNo });
}

/** 进入设计器前确保存在草稿（无 draft 时从 published fork） */
export async function ensureDesignDraftApi(busNo: string) {
  return publicRequestClient.post<PrtTemplateDto>(`${BASE}/ensureDesignDraft`, { busNo });
}

/** 保存草稿前确保可写：已有 draft 直接返回，否则从 published fork */
export async function ensureWritableDraftApi(busNo: string) {
  const dto = await getByBusNoApi(busNo);
  if (dto.status === 'published') {
    return ensureDesignDraftApi(busNo);
  }
  return dto;
}

export async function saveDraftApi(params: SaveDraftParams) {
  return publicRequestClient.post<PrtTemplateDto>(`${BASE}/saveDraft`, params);
}

export async function publishTemplateApi(busNo: string) {
  return publicRequestClient.post<PrtTemplateDto>(`${BASE}/publish`, { busNo });
}

export async function forkDraftApi(busNo: string) {
  return publicRequestClient.post<PrtTemplateDto>(`${BASE}/forkDraft`, { busNo });
}

export async function deleteTemplateApi(busNo: string) {
  return publicRequestClient.post(`${BASE}/deleteTemplate`, { busNo });
}
