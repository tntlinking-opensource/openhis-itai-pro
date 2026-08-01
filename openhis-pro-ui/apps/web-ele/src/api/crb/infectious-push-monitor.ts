/**
 * 传染病推送监控 — POST /api/app/infectiousPushMonitor/{method}
 */
import type { ApiResponse, PageApiResponse, PageParams } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const BASE = '/infectiousPushMonitor';

export interface PagePushTasksParams extends PageParams {
  status?: string;
}

export interface PushTaskSummaryDto {
  taskId?: string;
  resource?: string;
  businessKey?: string;
  status?: string;
  slaTier?: string;
  dueAt?: string;
  attemptCount?: number;
  lastError?: string;
}

export interface PushLogSummaryDto {
  logId?: string;
  success?: boolean;
  errorCode?: string;
  errorName?: string;
  durationMs?: number;
  createdAt?: string;
}

export interface FrontSoftwareConfigViewDto {
  configured?: boolean;
  baseUrlMasked?: string;
  licenseMasked?: string;
  orgCode?: string;
  mockEnabled?: boolean;
}

/** 分页查询推送任务。 */
export async function pagePushTasksApi(tenantId: string | number, request: PagePushTasksParams) {
  const res = await requestClient.post<ApiResponse<PageApiResponse<PushTaskSummaryDto>>>(
    `${BASE}/pageTasks`,
    {
      tenantId: preserveApiIdForRequest(tenantId) ?? tenantId,
      request,
    },
  );
  return res.data;
}

/** 按任务 id 查询推送日志。 */
export async function listPushLogsByTaskApi(tenantId: string | number, taskId: string) {
  const res = await requestClient.post<ApiResponse<PushLogSummaryDto[]>>(`${BASE}/listLogsByTask`, {
    tenantId: preserveApiIdForRequest(tenantId) ?? tenantId,
    taskId: preserveApiIdForRequest(taskId),
  });
  return res.data;
}

/** 人工重试推送任务。 */
export async function retryPushTaskApi(tenantId: string | number, taskId: string) {
  const res = await requestClient.post<ApiResponse<PushTaskSummaryDto>>(`${BASE}/retryTask`, {
    tenantId: preserveApiIdForRequest(tenantId) ?? tenantId,
    taskId: preserveApiIdForRequest(taskId),
  });
  return res.data;
}

/** 读取前置软件配置只读视图。 */
export async function getFrontSoftwareConfigViewApi() {
  const res = await requestClient.post<ApiResponse<FrontSoftwareConfigViewDto>>(
    `${BASE}/getFrontSoftwareConfigView`,
    {},
  );
  return res.data;
}

/** 查询待处理任务（存量接口）。 */
export async function listPendingPushTasksApi(
  tenantId: string | number,
  pageNo: number,
  pageSize: number,
) {
  const res = await requestClient.post<ApiResponse<PushTaskSummaryDto[]>>(
    `${BASE}/listPendingTasks`,
    {
      tenantId: preserveApiIdForRequest(tenantId) ?? tenantId,
      pageNo,
      pageSize,
    },
  );
  return res.data;
}
