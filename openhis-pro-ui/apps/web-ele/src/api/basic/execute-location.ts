import type { ApiOptionItem } from '#/types/api-response';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { publicRequestClient } from '../request';

/** 执行科室规则行（开立科室维度配置） */
export interface ExecuteLocationItem {
  id?: string;
  /** 开立科室 */
  organizationId?: string;
  organizationName?: string;
  activityDefinitionId?: string;
  activityDefinitionName?: string;
  activityCategoryCode?: string;
  /** 业务类型码（术语 activity_type_code）；可空 */
  activityTypeCode?: string;
  /** 业务类型展示名 */
  activityTypeCode_dictText?: string;
  /** 执行科室（行内选择） */
  executionOrgId?: string;
  executionOrgName?: string;
  executionLocationId?: string;
  startTime?: string;
  endTime?: string;
  priority?: number;
  displayOrder?: number;
  remark?: string;
}

/** 配置页 init：开立科室树 + 执行科室树 + 诊疗大类 */
export interface ExecuteLocationInitResponse {
  organizationOptions?: ApiOptionItem[];
  executionOrgOptions?: ApiOptionItem[];
  executionLocationOptions?: ApiOptionItem[];
  activityCategoryOptions?: ApiOptionItem[];
  /** 业务类型（术语 activity_type_code） */
  activityTypeOptions?: ApiOptionItem[];
}

/** 按开立科室分页查规则 */
export interface PageExecuteLocationParams {
  organizationId: string;
  pageNo: number;
  pageSize: number;
  activityCategoryCode?: string;
}

export interface PageExecuteLocationResponse {
  content: ExecuteLocationItem[];
  totalCount: number;
}

export interface ActivityDefinitionOption {
  value: string;
  label: string;
}

/** 挑选诊疗项目分页（按开立科室上下文） */
export interface PageActivityDefinitionParams {
  organizationId: string;
  activityCategoryCode: string;
  /** 业务类型，可选；有值时按 AD.type_enum 过滤 */
  activityTypeCode?: string;
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
}

export interface PageActivityDefinitionResponse {
  content: ActivityDefinitionOption[];
  totalCount: number;
}

export interface CreateExecuteLocationParams {
  organizationId: string;
  activityDefinitionId?: string;
  activityCategoryCode: string;
  activityTypeCode?: string;
  executionOrgId: string;
  executionLocationId?: string;
  startTime: string;
  endTime: string;
  priority?: number;
  displayOrder?: number;
  remark?: string;
}

export interface UpdateExecuteLocationParams extends CreateExecuteLocationParams {
  id: string;
}

export interface BatchCreateExecuteLocationParams {
  organizationId: string;
  executionOrgId: string;
  activityCategoryCode: string;
  activityTypeCode?: string;
  executionLocationId?: string;
  activityDefinitionIds: string[];
  startTime: string;
  endTime: string;
  priority?: number;
  displayOrder?: number;
  remark?: string;
}

/** 配置页初始化 */
export async function initExecuteLocationApi() {
  return publicRequestClient.post<ExecuteLocationInitResponse>('/executeLocation/init');
}

/** 按开立科室列出规则 */
export async function pageExecuteLocationApi(params: PageExecuteLocationParams) {
  return publicRequestClient.post<PageExecuteLocationResponse>(
    '/executeLocation/listByOrganizationId',
    params,
  );
}

/** 创建单条规则 */
export async function createExecuteLocationApi(params: CreateExecuteLocationParams) {
  return publicRequestClient.post<string>('/executeLocation/create', params);
}

/** 更新单条规则 */
export async function updateExecuteLocationApi(params: UpdateExecuteLocationParams) {
  return publicRequestClient.post<unknown>('/executeLocation/update', params);
}

/** 按主键删除规则 */
export async function deleteExecuteLocationApi(id: number | string) {
  return publicRequestClient.post<unknown>(
    '/executeLocation/deleteById',
    preserveApiIdForRequest(id),
  );
}

/** 批量创建规则 */
export async function batchCreateExecuteLocationApi(params: BatchCreateExecuteLocationParams) {
  return publicRequestClient.post<unknown>('/executeLocation/batchCreate', params);
}

/** 分页挑选待绑定诊疗项目 */
export async function pageActivityDefinitionForExecuteLocationApi(
  params: PageActivityDefinitionParams,
) {
  return publicRequestClient.post<PageActivityDefinitionResponse>(
    '/executeLocation/pageActivityDefinitionsForExecution',
    params,
  );
}

/** 按诊疗大类拉取业务类型（该大类目录实际用过的 type_enum，排除与大类同名） */
export async function listActivityTypeOptionsByCategoryApi(activityCategoryCode: string) {
  return publicRequestClient.post<ApiOptionItem[]>(
    '/executeLocation/listActivityTypeOptionsByCategory',
    { activityCategoryCode },
  );
}
