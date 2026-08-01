/**
 * 元数据字段定义 API（whale-emr MetadataFieldAppService）
 */

import type { PageApiResponse } from '#/types/api-response';

import type { EmrMetadataField } from '#/emrEditor/types';

import { publicRequestClient } from '#/api/request';

export interface MetadataFieldDto {
  id?: number | string;
  fieldKey?: string;
  label?: string;
  dataType?: string;
  editableDefault?: boolean;
  valueSource?: string;
  sourcePath?: string;
  sourceConfig?: Record<string, unknown> | null;
  defaultValue?: string;
  maskRule?: string;
  formatPattern?: string;
  sortOrder?: number;
  isEnabled?: boolean;
  remark?: string;
}

export interface QueryMetadataFieldParams {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  valueSource?: string;
  isEnabled?: boolean;
}

export interface QueryEnabledMetadataFieldParams {
  keyword?: string;
}

export interface GetMetadataFieldParams {
  id: string;
}

export interface CreateMetadataFieldParams {
  fieldKey: string;
  label: string;
  dataType?: string;
  editableDefault?: boolean;
  valueSource: string;
  sourcePath?: string;
  sourceConfig?: Record<string, unknown>;
  defaultValue?: string;
  maskRule?: string;
  formatPattern?: string;
  sortOrder?: number;
  isEnabled?: boolean;
  remark?: string;
}

export interface UpdateMetadataFieldParams {
  id: string;
  label?: string;
  dataType?: string;
  editableDefault?: boolean;
  valueSource?: string;
  sourcePath?: string;
  sourceConfig?: Record<string, unknown>;
  defaultValue?: string;
  maskRule?: string;
  formatPattern?: string;
  sortOrder?: number;
  isEnabled?: boolean;
  remark?: string;
}

export interface DeleteMetadataFieldParams {
  id: string;
}

export interface CheckMetadataFieldReferenceParams {
  fieldKey: string;
}

export interface MetadataFieldReferenceDto {
  referenced?: boolean;
  templateNames?: string[];
}

export function mapMetadataFieldDtoToEmr(dto: MetadataFieldDto): EmrMetadataField {
  const fieldKey = String(dto.fieldKey ?? '').trim();
  const valueSource = String(dto.valueSource ?? '').trim();
  return {
    fieldKey,
    label: String(dto.label ?? fieldKey).trim() || fieldKey,
    editable: dto.editableDefault !== false,
    ...(valueSource ? { valueSource } : {}),
  };
}

export async function getMetadataFieldListApi(params: QueryMetadataFieldParams) {
  return publicRequestClient.post<PageApiResponse<MetadataFieldDto>>(
    '/metadataField/getList',
    params,
  );
}

export async function getEnabledMetadataFieldListApi(params?: QueryEnabledMetadataFieldParams) {
  return publicRequestClient.post<MetadataFieldDto[]>(
    '/metadataField/getEnabledList',
    params ?? {},
  );
}

export async function getMetadataFieldByIdApi(params: GetMetadataFieldParams) {
  return publicRequestClient.post<MetadataFieldDto>('/metadataField/getById', params);
}

export async function createMetadataFieldApi(params: CreateMetadataFieldParams) {
  return publicRequestClient.post<MetadataFieldDto>('/metadataField/create', params);
}

export async function updateMetadataFieldApi(params: UpdateMetadataFieldParams) {
  return publicRequestClient.post<MetadataFieldDto>('/metadataField/update', params);
}

export async function deleteMetadataFieldApi(params: DeleteMetadataFieldParams) {
  return publicRequestClient.post<void>('/metadataField/delete', params);
}

export async function checkMetadataFieldReferenceApi(params: CheckMetadataFieldReferenceParams) {
  return publicRequestClient.post<MetadataFieldReferenceDto>(
    '/metadataField/checkReference',
    params,
  );
}
