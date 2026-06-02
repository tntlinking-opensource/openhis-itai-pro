import type { ApiOptionItem } from '#/types/api-response';

import { publicRequestClient } from '../request';

export interface ExecuteLocationItem {
  id?: string;
  activityDefinitionId?: string;
  activityCategoryCode?: string;
  executionOrgId?: string;
  executionLocationId?: string;
  startTime?: string;
  endTime?: string;
  priority?: number;
  displayOrder?: number;
  remark?: string;
}

export interface ExecuteLocationInitResponse {
  executionOrgOptions?: ApiOptionItem[];
  executionLocationOptions?: ApiOptionItem[];
  activityCategoryOptions?: ApiOptionItem[];
}

export interface PageExecuteLocationParams {
  executionOrgId: string;
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

export interface PageActivityDefinitionParams {
  executionOrgId: string;
  activityCategoryCode: string;
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
}

export interface PageActivityDefinitionResponse {
  content: ActivityDefinitionOption[];
  totalCount: number;
}

export interface CreateExecuteLocationParams {
  activityDefinitionId?: string;
  activityCategoryCode: string;
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
  executionOrgId: string;
  activityCategoryCode: string;
  executionLocationId?: string;
  activityDefinitionIds: string[];
  startTime: string;
  endTime: string;
  priority?: number;
  displayOrder?: number;
  remark?: string;
}

export async function initExecuteLocationApi() {
  return publicRequestClient.post<ExecuteLocationInitResponse>('/executeLocation/init');
}

export async function pageExecuteLocationApi(params: PageExecuteLocationParams) {
  return publicRequestClient.post<PageExecuteLocationResponse>(
    '/executeLocation/listByExecutionOrgId',
    params,
  );
}

export async function createExecuteLocationApi(params: CreateExecuteLocationParams) {
  return publicRequestClient.post<string>('/executeLocation/create', params);
}

export async function updateExecuteLocationApi(params: UpdateExecuteLocationParams) {
  return publicRequestClient.post<unknown>('/executeLocation/update', params);
}

export async function deleteExecuteLocationApi(id: string) {
  return publicRequestClient.post<unknown>('/executeLocation/deleteById', { value: id });
}

export async function batchCreateExecuteLocationApi(params: BatchCreateExecuteLocationParams) {
  return publicRequestClient.post<unknown>('/executeLocation/batchCreate', params);
}

export async function pageActivityDefinitionForExecuteLocationApi(
  params: PageActivityDefinitionParams,
) {
  return publicRequestClient.post<PageActivityDefinitionResponse>(
    '/executeLocation/pageActivityDefinitionsForExecution',
    params,
  );
}
