/**
 * 健康档案 — {@code POST /peisHealthArchive/<method>}
 */
import { publicRequestClient } from '#/api/request';

import type { IdLike } from './catalog-config';

export interface ArchiveMissingItemDto {
  code?: string;
  level?: string;
  message?: string;
}

export interface ArchiveCompletenessDto {
  complete?: boolean;
  level?: string;
  missingItems?: ArchiveMissingItemDto[];
  summary?: string;
}

export interface LoadArchiveRequest {
  includeCompleteness?: boolean;
  patientId?: IdLike;
  studentNo?: string;
}

export interface LoadArchiveResponse {
  allergySummary?: string;
  archiveId?: IdLike;
  completeness?: ArchiveCompletenessDto;
  familyHistory?: string;
  habitSummary?: string;
  pastHistory?: string;
  patientId?: IdLike;
  remark?: string;
  studentNo?: string;
}

export interface SaveArchiveRequest {
  allergySummary?: string;
  familyHistory?: string;
  habitSummary?: string;
  orgId?: IdLike;
  pastHistory?: string;
  patientId: IdLike;
  remark?: string;
}

export interface CheckArchiveCompletenessRequest {
  patientId?: IdLike;
  studentNo?: string;
}

export interface CheckArchiveCompletenessResponse {
  completeness?: ArchiveCompletenessDto;
  patientId?: IdLike;
  schoolStatusEnum?: string;
  studentNo?: string;
  subjectType?: string;
}

export function loadArchiveApi(data: LoadArchiveRequest) {
  return publicRequestClient.post<LoadArchiveResponse>('/peisHealthArchive/loadArchive', data);
}

export function saveArchiveApi(data: SaveArchiveRequest) {
  return publicRequestClient.post<{ archiveId?: IdLike; patientId?: IdLike }>(
    '/peisHealthArchive/saveArchive',
    data,
  );
}

export function checkArchiveCompletenessApi(data: CheckArchiveCompletenessRequest) {
  return publicRequestClient.post<CheckArchiveCompletenessResponse>(
    '/peisHealthArchive/checkArchiveCompleteness',
    data,
  );
}
