/**
 * 定时任务管理 — 对接 POST /api/app/scheduledJob/*
 * 数据持久化在 whale_qrtz_* 表，主键为 jobName + jobGroup。
 */
import type { PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 任务自定义参数 */
export interface ScheduledJobParameter {
  key: string;
  value?: string;
}

/** 任务列表/详情 */
export interface ScheduledJobInfo {
  jobName: string;
  jobGroup: string;
  displayName?: string;
  description?: string;
  remark?: string;
  jobBeanName: string;
  cronExpression?: string;
  cronDescription?: string;
  triggerType?: string;
  status?: string;
  statusLabel?: string;
  misfirePolicy?: string;
  misfirePolicyLabel?: string;
  concurrentPolicy?: string;
  concurrentPolicyLabel?: string;
  tenantId?: string;
  tenantName?: string;
  parameters?: ScheduledJobParameter[];
  nextFireTime?: string;
  previousFireTime?: string;
}

export interface ScheduledJobListParams extends PageParams {
  keyword?: string;
  jobGroup?: string;
  status?: string;
  tenantId?: string;
}

export interface ScheduledJobTenantOption {
  id: string;
  name: string;
  code: string;
}

export interface ScheduledJobExecutionLog {
  id: string;
  jobName: string;
  jobGroup: string;
  jobBeanName: string;
  displayName?: string;
  tenantId?: string;
  tenantName?: string;
  executionType?: string;
  executionTypeLabel?: string;
  success?: boolean;
  successLabel?: string;
  errorMessage?: string;
  durationMs?: number;
  startedAt?: string;
  finishedAt?: string;
}

export interface ScheduledJobExecutionLogListParams extends PageParams {
  keyword?: string;
  jobName?: string;
  jobGroup?: string;
  tenantId?: number | string;
  success?: boolean;
  finishedAtFrom?: string;
  finishedAtTo?: string;
}

export interface ScheduledJobKeyParams {
  jobName: string;
  jobGroup: string;
}

export interface CreateScheduledJobParams {
  jobName: string;
  jobGroup?: string;
  displayName?: string;
  description?: string;
  remark?: string;
  jobBeanName: string;
  cronExpression?: string;
  cronPresetType?: string;
  intervalMinutes?: number;
  minute?: number;
  hour?: number;
  dayOfWeek?: number;
  dayOfMonth?: number;
  misfirePolicy?: string;
  concurrentPolicy?: string;
  status?: string;
  tenantId?: string;
  parameters?: ScheduledJobParameter[];
}

export interface UpdateScheduledJobParams {
  jobName: string;
  jobGroup: string;
  newJobName?: string;
  newJobGroup?: string;
  displayName?: string;
  description?: string;
  remark?: string;
  jobBeanName: string;
  cronExpression?: string;
  cronPresetType?: string;
  intervalMinutes?: number;
  minute?: number;
  hour?: number;
  dayOfWeek?: number;
  dayOfMonth?: number;
  misfirePolicy?: string;
  concurrentPolicy?: string;
  status?: string;
  tenantId?: string;
  parameters?: ScheduledJobParameter[];
}

export interface ChangeScheduledJobStatusParams extends ScheduledJobKeyParams {
  status: 'NORMAL' | 'PAUSE';
}

export interface ValidateCronParams {
  cronExpression: string;
}

export interface ValidateCronResult {
  valid: boolean;
  errorMessage?: string;
  description?: string;
  nextFireTime?: string;
  nextFireTimes?: string[];
}

export interface BuildCronParams {
  cronPresetType: string;
  intervalMinutes?: number;
  minute?: number;
  hour?: number;
  dayOfWeek?: number;
  dayOfMonth?: number;
  customExpression?: string;
}

export interface BuildCronResult {
  cronExpression: string;
  description?: string;
}

export interface JobHandlerOption {
  beanName: string;
  jobName?: string;
  description?: string;
}

export interface ScheduledJobOption {
  code: string;
  label: string;
}

export interface ScheduledJobOptionGroup {
  groupCode: string;
  groupLabel: string;
  options: ScheduledJobOption[];
}

/** 分页查询定时任务 */
export async function getScheduledJobListApi(params: ScheduledJobListParams) {
  return publicRequestClient.post<PageApiResponse<ScheduledJobInfo>>(
    '/scheduledJob/getList',
    params,
  );
}

/** 按 JobKey 查询详情 */
export async function getScheduledJobByKeyApi(params: ScheduledJobKeyParams) {
  return publicRequestClient.post<ScheduledJobInfo>('/scheduledJob/getByKey', params);
}

/** 新建定时任务 */
export async function createScheduledJobApi(params: CreateScheduledJobParams) {
  return publicRequestClient.post<ScheduledJobInfo>('/scheduledJob/create', params);
}

/** 更新定时任务 */
export async function updateScheduledJobApi(params: UpdateScheduledJobParams) {
  return publicRequestClient.post<ScheduledJobInfo>('/scheduledJob/update', params);
}

/** 删除定时任务 */
export async function deleteScheduledJobApi(params: ScheduledJobKeyParams) {
  return publicRequestClient.post<void>('/scheduledJob/delete', params);
}

/** 修改运行状态 */
export async function changeScheduledJobStatusApi(params: ChangeScheduledJobStatusParams) {
  return publicRequestClient.post<ScheduledJobInfo>('/scheduledJob/changeStatus', params);
}

/** 立即执行一次 */
export async function runScheduledJobNowApi(params: ScheduledJobKeyParams) {
  return publicRequestClient.post<void>('/scheduledJob/runNow', params);
}

/** 校验 Cron 并预览触发时间 */
export async function validateCronApi(params: ValidateCronParams) {
  return publicRequestClient.post<ValidateCronResult>('/scheduledJob/validateCron', params);
}

/** 可视化预设生成 Cron */
export async function buildCronApi(params: BuildCronParams) {
  return publicRequestClient.post<BuildCronResult>('/scheduledJob/buildCron', params);
}

/** 可选 IJob 处理器列表 */
export async function listJobHandlersApi() {
  return publicRequestClient.post<JobHandlerOption[]>('/scheduledJob/listHandlers', {});
}

/** 页面下拉选项（状态/策略/预设） */
export async function getScheduledJobOptionsApi() {
  return publicRequestClient.post<ScheduledJobOptionGroup[]>('/scheduledJob/getOptions', {});
}

/** 租户下拉（任务绑定与筛选） */
export async function listScheduledJobTenantOptionsApi() {
  return publicRequestClient.post<ScheduledJobTenantOption[]>(
    '/scheduledJob/listTenantOptions',
    {},
  );
}

/** 分页查询执行历史 */
export async function getScheduledJobExecutionLogListApi(
  params: ScheduledJobExecutionLogListParams,
) {
  return publicRequestClient.post<PageApiResponse<ScheduledJobExecutionLog>>(
    '/scheduledJobExecutionLog/getList',
    params,
  );
}

/** 写入可选租户 ID（Long 须 string） */
export function buildTenantIdForRequest(raw: null | number | string | undefined) {
  if (raw === undefined || raw === null || raw === '') {
    return undefined;
  }
  return preserveApiIdForRequest(raw) as string;
}
