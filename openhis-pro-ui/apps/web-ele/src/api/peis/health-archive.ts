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
  tagCode?: string;
}

export interface SaveArchiveRequest {
  allergySummary?: string;
  familyHistory?: string;
  habitSummary?: string;
  orgId?: IdLike;
  pastHistory?: string;
  patientId: IdLike;
  remark?: string;
  tagCode?: string;
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

export interface SaveArchiveApiOptions {
  /**
   * 软失败：HTTP 拦截器不自动 toast，由调用方处理提示。
   * 用于「患者主档已保存、体检档案附属写入失败」等场景，避免误报整单失败。
   */
  softFailToast?: boolean;
}

/**
 * 保存体检健康档案。
 *
 * @param data 档案字段；patientId 须为 string，禁止 Number 雪花主键
 * @param options softFailToast 时由调用方自行 toast
 */
export function saveArchiveApi(data: SaveArchiveRequest, options?: SaveArchiveApiOptions) {
  const headers =
    options?.softFailToast === true
      ? {
          'X-Zxplat-Soft-Fail': '1',
        }
      : undefined;
  return publicRequestClient.post<{ archiveId?: IdLike; patientId?: IdLike }>(
    '/peisHealthArchive/saveArchive',
    data,
    { headers },
  );
}

export function checkArchiveCompletenessApi(data: CheckArchiveCompletenessRequest) {
  return publicRequestClient.post<CheckArchiveCompletenessResponse>(
    '/peisHealthArchive/checkArchiveCompleteness',
    data,
  );
}
