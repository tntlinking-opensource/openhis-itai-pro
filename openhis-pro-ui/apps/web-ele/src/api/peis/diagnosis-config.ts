/**
 * 诊断与表达式（P0：诊断库/常见结果）— {@code POST /api/app/peisDiagnosisConfig/<method>}
 */
import { publicRequestClient } from '#/api/request';

import type { PeisPagedResult } from './catalog-config';

export interface PeisDiagnosisTemplateDto {
  definitionId?: string | number;
  code?: string;
  display?: string;
  categoryCode?: string;
  statusEnum?: string;
}

export interface PeisCommonResultDto {
  id?: string | number;
  observationDefinitionId?: string | number;
  deptOrgId?: string | number;
  display?: string;
  valueString?: string;
  interpretationCode?: string;
  sortNum?: number;
  statusEnum?: string;
}

export interface SearchDiagnosisTemplateRequest {
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface SaveDiagnosisTemplateRequest {
  /** 雪花主键以 string 提交，避免 JS Number 精度丢失 */
  definitionId?: string | number;
  code: string;
  display: string;
  categoryCode?: string;
  statusEnum: string;
}

export interface SearchCommonResultRequest {
  keyword?: string;
  pageNo: number;
  pageSize: number;
  observationDefinitionId?: number;
  deptOrgId?: number;
  statusEnum?: string;
}

export interface SaveCommonResultRequest {
  id?: number;
  observationDefinitionId?: number;
  deptOrgId?: number;
  display: string;
  valueString?: string;
  interpretationCode?: string;
  sortNum?: number;
  statusEnum?: string;
}

export function searchDiagnosisTemplateApi(params: SearchDiagnosisTemplateRequest) {
  return publicRequestClient.post<PeisPagedResult<PeisDiagnosisTemplateDto>>(
    '/peisDiagnosisConfig/searchDiagnosisTemplate',
    params,
  );
}

export function saveDiagnosisTemplateApi(params: SaveDiagnosisTemplateRequest) {
  return publicRequestClient.post<PeisDiagnosisTemplateDto>(
    '/peisDiagnosisConfig/saveDiagnosisTemplate',
    params,
  );
}

export function searchCommonResultApi(params: SearchCommonResultRequest) {
  return publicRequestClient.post<PeisPagedResult<PeisCommonResultDto>>(
    '/peisDiagnosisConfig/searchCommonResult',
    params,
  );
}

export function saveCommonResultApi(params: SaveCommonResultRequest) {
  return publicRequestClient.post<PeisCommonResultDto>(
    '/peisDiagnosisConfig/saveCommonResult',
    params,
  );
}
