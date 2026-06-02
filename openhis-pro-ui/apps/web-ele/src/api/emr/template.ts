/**
 * 模板 API（whale-emr TemplateAppService）
 */

import type { PageApiResponse } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

import type { DocumentTypeDto, QueryDocumentTypeParams } from './document-type';

/** 模板列表项 */
export interface TemplateDto {
  id?: number | string;
  name?: string;
  code?: string;
  documentTypeCode?: string;
  documentTypeName?: string;
  scopeLevel?: string;
  scopeLevelName?: string;
  organizationId?: number | string;
  organizationName?: string;
  ownerPractitionerId?: number | string;
  encounterClassCode?: string;
  encounterClassName?: string;
  isEnabled?: boolean;
  enabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

/** 模板正文入库：后端 content 为 JSON 字符串 */
export function serializeEmrTemplateContent(content: unknown): string {
  if (content === undefined || content === null) return '{}';
  if (typeof content === 'string') return content;
  return JSON.stringify(content);
}

export interface TemplateContentDto extends TemplateDto {
  content?: Record<string, unknown> | string | null;
}

export interface QueryTemplateParams {
  pageNo: number;
  pageSize: number;
  sortBy?: string;
  sortDirection?: string;
  keyword?: string;
  documentTypeCode?: string;
  scopeLevel?: string;
  encounterClassCode?: string;
  organizationId?: string;
  enabled?: boolean;
}

export interface ListTemplatesForStationParams {
  /** OUTPATIENT | INPATIENT */
  stationKind: string;
  documentTypeCode?: string;
  enabled?: boolean;
}

export interface GetTemplateContentParams {
  templateId: string;
}

export interface CreateTemplateParams {
  name: string;
  code: string;
  documentTypeCode?: string;
  scopeLevel: string;
  encounterClassCode: string;
  organizationId?: string;
  ownerPractitionerId?: string;
  /** 医生站存为模版：后端强制个人模板 */
  fromDoctorStation?: boolean;
  content?: unknown;
  enabled?: boolean;
}

export interface UpdateTemplateParams {
  id: string;
  name?: string;
  code?: string;
  documentTypeCode?: string;
  scopeLevel?: string;
  encounterClassCode?: string;
  organizationId?: string;
  ownerPractitionerId?: string;
  content?: unknown;
  enabled?: boolean;
}

export interface DeleteTemplateParams {
  id: string;
}

export async function getTemplateListApi(params: QueryTemplateParams) {
  const body = {
    ...params,
    organizationId: params.organizationId ? Number(params.organizationId) : undefined,
  };
  return publicRequestClient.post<PageApiResponse<TemplateDto>>('/template/getList', body);
}

/** 医生站：按场景与当前用户可见范围加载模板（无 content） */
export async function listTemplatesForStationApi(params: ListTemplatesForStationParams) {
  return publicRequestClient.post<TemplateDto[]>('/template/listForStation', params);
}

export async function getTemplateDocumentTypesApi(params?: QueryDocumentTypeParams) {
  return publicRequestClient.post<DocumentTypeDto[]>('/template/getDocumentTypes', params ?? {});
}

export async function getTemplateContentApi(params: GetTemplateContentParams) {
  return publicRequestClient.post<TemplateContentDto>('/template/getContent', {
    templateId: preserveApiIdForRequest(params.templateId),
  });
}

function buildScopePayload(params: {
  scopeLevel: string;
  encounterClassCode: string;
  organizationId?: string;
  ownerPractitionerId?: string;
}) {
  const scopeLevel = params.scopeLevel.trim();
  const encounterClassCode = params.encounterClassCode.trim();
  const organizationId = preserveApiIdForRequest(params.organizationId);
  const ownerPractitionerId = preserveApiIdForRequest(params.ownerPractitionerId);
  return {
    scopeLevel,
    encounterClassCode,
    organizationId: organizationId ? Number(organizationId) : undefined,
    ownerPractitionerId: ownerPractitionerId ? Number(ownerPractitionerId) : undefined,
  };
}

export async function createTemplateApi(params: CreateTemplateParams) {
  return publicRequestClient.post<TemplateContentDto>('/template/create', {
    name: params.name,
    code: params.code,
    documentTypeCode: params.documentTypeCode,
    content: serializeEmrTemplateContent(params.content),
    enabled: params.enabled,
    fromDoctorStation: params.fromDoctorStation,
    ...buildScopePayload(params),
  });
}

export async function updateTemplateApi(params: UpdateTemplateParams) {
  return publicRequestClient.post<TemplateContentDto>('/template/update', {
    id: preserveApiIdForRequest(params.id),
    name: params.name,
    code: params.code,
    documentTypeCode: params.documentTypeCode,
    content: params.content !== undefined ? serializeEmrTemplateContent(params.content) : undefined,
    enabled: params.enabled,
    ...(params.scopeLevel && params.encounterClassCode
      ? buildScopePayload({
          scopeLevel: params.scopeLevel,
          encounterClassCode: params.encounterClassCode,
          organizationId: params.organizationId,
          ownerPractitionerId: params.ownerPractitionerId,
        })
      : {}),
  });
}

export async function deleteTemplateApi(params: DeleteTemplateParams) {
  return publicRequestClient.post<void>('/template/delete', {
    id: preserveApiIdForRequest(params.id),
  });
}
