/**
 * 就诊元数据值解析 API（whale-emr EncounterMetadataAppService）
 */

import { publicRequestClient } from '#/api/request';

export interface ResolveEncounterMetadataParams {
  patientId: string;
  encounterId?: string;
  documentId?: string;
  fieldKeys?: string[];
  metadataOverrides?: Record<string, string>;
}

export interface ResolveDocumentMetadataParams {
  documentId: string;
  fieldKeys?: string[];
  metadataOverrides?: Record<string, string>;
}

export interface MetadataValuesDto {
  values?: Record<string, string>;
  maskedFields?: string[];
}

export async function resolveEncounterMetadataApi(params: ResolveEncounterMetadataParams) {
  return publicRequestClient.post<MetadataValuesDto>('/encounterMetadata/resolveValues', params);
}

export async function resolveDocumentMetadataApi(params: ResolveDocumentMetadataParams) {
  return publicRequestClient.post<MetadataValuesDto>(
    '/encounterMetadata/resolveForDocument',
    params,
  );
}
