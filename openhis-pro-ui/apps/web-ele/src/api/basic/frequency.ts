import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

export interface FrequencyItem {
  /** 主键，后端为 Long，前端可能为 number 或 string */
  id?: number | string;
  rateCode?: string;
  name?: string;
  dayInterval?: number;
  dayTimes?: string;
  weekCycleFlag?: number;
  weekInterval?: number;
  totalExecutionCount?: number;
  executionPeriod?: number;
  executionPeriodUnit?: string;
  thirdCode?: string;
  memo?: string;
  activeFlag?: number;
  activeFlag_dictText?: string;
  /** TODO: 排序号，用于列表与下拉排序 */
  sortNo?: number;
  /** TODO: 周期类型：1-按天 2-按周 3-按月 4-按小时间隔 5-按需(PRN) */
  cycleType?: number;
  /** TODO: 适用场景，暂用逗号分隔字符串，后续可改为多选枚举 */
  applicableScenes?: string;
  /** TODO: 允许跨日标志：0-不允许，1-允许 */
  allowCrossDay?: number;
}

export interface PageFrequencyParams {
  where?: string[];
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
  name?: string;
  rateCode?: string;
}

/** 分页响应，兼容 totalCount（当前后端）与 totalElements */
export interface PageFrequencyResponse {
  content?: FrequencyItem[];
  totalCount?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface FrequencyInitResponse {
  statusOptions?: Array<{ label: string; value: string }>;
  /** TODO: 周期类型下拉选项，后端暂按固定枚举生成 */
  cycleTypeOptions?: Array<{ label: string; value: string }>;
  /** TODO: 适用场景下拉选项，后端暂用配置枚举，前端多选提交逗号分隔字符串 */
  sceneOptions?: Array<{ label: string; value: string }>;
  /** TODO: 允许跨日选项：0-不允许，1-允许 */
  allowCrossDayOptions?: Array<{ label: string; value: string }>;
}

export interface CreateFrequencyParams {
  /** 频次编码（需求 2.2 必填且唯一，复制时必填新编码；后端支持后可传） */
  rateCode?: string;
  name: string;
  dayInterval?: number;
  dayTimes?: string;
  weekCycleFlag?: number;
  weekInterval?: number;
  totalExecutionCount?: number;
  executionPeriod?: number;
  executionPeriodUnit?: string;
  thirdCode?: string;
  memo?: string;
  activeFlag?: number;
  sortNo?: number;
  cycleType?: number;
  applicableScenes?: string;
  allowCrossDay?: number;
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

export async function deleteFrequencyApi(id: string): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/frequency/delete', id);
}

export async function enableFrequencyApi(id: string): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/frequency/enable', { value: id });
}

export async function disableFrequencyApi(id: string): Promise<ApiResponse<null>> {
  return requestClient.post<ApiResponse<null>>('/frequency/disable', { value: id });
}
