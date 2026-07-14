import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 周期类型 code（DM-CUS-008 §4.1） */
export type FrequencyCycleTypeCode = 'as-needed' | 'daily' | 'fixed-count' | 'interval' | 'weekly';

/** 跨日选项 code（DM-CUS-008 §4.4） */
export type FrequencyAllowCrossDayCode = 'by-shift' | 'cross-day' | 'same-day';

export interface FrequencyItem {
  id?: number | string;
  rateCode?: string;
  name?: string;
  cycleType?: FrequencyCycleTypeCode | string;
  dayCount?: number;
  dayInterval?: number;
  dayTimes?: string;
  weekCycleFlag?: number;
  totalExecutionCount?: number;
  executionPeriod?: number;
  executionPeriodUnit?: string;
  /** 启用标志：1 启用，0 停用（列表展示；启停请走 enable/disable 接口） */
  activeFlag?: number;
  sortNo?: number;
  applicableScenes?: string;
  allowCrossDay?: FrequencyAllowCrossDayCode | string;
}

export interface PageFrequencyParams {
  where?: string[];
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
  name?: string;
  rateCode?: string;
}

export interface PageFrequencyResponse {
  content?: FrequencyItem[];
  totalCount?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface FrequencyInitResponse {
  statusOptions?: Array<{ label: string; value: string }>;
  cycleTypeOptions?: Array<{ label: string; value: string }>;
  sceneOptions?: Array<{ label: string; value: string }>;
  allowCrossDayOptions?: Array<{ label: string; value: string }>;
  periodUnitOptions?: Array<{ label: string; value: string }>;
}

/** 与后端 {@code ComputeFrequencyExecutionTimePointsRequest} 对齐（LocalDateTime，无时区） */
export interface ComputeFrequencyExecutionTimePointsRequest {
  rateCode: string;
  /** 本地时间串，形如 2026-07-05T00:00:00 */
  startTime: string;
  endTime: string;
}

/** 与后端 {@code ComputeFrequencyExecutionTimePointsResponse} 对齐 */
export interface ComputeFrequencyExecutionTimePointsResponse {
  executionCount?: number;
  executionAtList?: string[];
}

export interface CreateFrequencyParams {
  rateCode?: string;
  name: string;
  cycleType?: string;
  dayCount?: number;
  dayInterval?: number;
  dayTimes?: string;
  weekCycleFlag?: number;
  totalExecutionCount?: number;
  executionPeriod?: number;
  executionPeriodUnit?: string;
  sortNo?: number;
  applicableScenes?: string;
  allowCrossDay?: string;
}

export interface UpdateFrequencyParams extends CreateFrequencyParams {
  id: string;
}

export async function initFrequencyApi() {
  return requestClient.post<FrequencyInitResponse>('/frequency/init');
}

export async function pageFrequencyApi(
  params: PageFrequencyParams,
): Promise<ApiResponse<PageFrequencyResponse>> {
  return requestClient.post<ApiResponse<PageFrequencyResponse>>('/frequency/page', params);
}

/** 计算频次在给定时间窗内的计划执行次数（DM-CUS-008 computeExecutionTimePoints）。 */
export async function computeFrequencyExecutionTimePointsApi(
  params: ComputeFrequencyExecutionTimePointsRequest,
): Promise<ComputeFrequencyExecutionTimePointsResponse> {
  return requestClient.post<ComputeFrequencyExecutionTimePointsResponse>(
    '/frequency/computeExecutionTimePoints',
    params,
  );
}

export async function createFrequencyApi(
  params: CreateFrequencyParams,
): Promise<ApiResponse<FrequencyItem>> {
  return requestClient.post<ApiResponse<FrequencyItem>>('/frequency/create', params);
}

export async function updateFrequencyApi(
  params: UpdateFrequencyParams,
): Promise<ApiResponse<FrequencyItem>> {
  return requestClient.post<ApiResponse<FrequencyItem>>('/frequency/update', params);
}

export async function deleteFrequencyApi(id: number | string): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/frequency/delete', preserveApiIdForRequest(id));
}

export async function enableFrequencyApi(id: number | string): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/frequency/enable', preserveApiIdForRequest(id));
}

export async function disableFrequencyApi(id: number | string): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/frequency/disable', preserveApiIdForRequest(id));
}
