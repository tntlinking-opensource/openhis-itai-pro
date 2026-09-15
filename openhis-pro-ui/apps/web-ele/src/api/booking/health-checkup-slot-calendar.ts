/**
 * 体检号源日历 — 对接 {@code POST /api/app/healthCheckupSlotCalendar/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingEntityId } from './booking-id';

export interface HcSlotTemplateDto {
  id?: number;
  organizationId?: number;
  slotMinutes?: number;
  amStart?: string;
  amEnd?: string;
  pmStart?: string;
  pmEnd?: string;
  capacityByType?: Record<string, number>;
  restWeekdays?: number[];
  campusCodes?: string[];
}

export interface HcDaySummaryDto {
  planDate?: string;
  restFlag?: boolean;
  totalCapacity?: number;
  totalBooked?: number;
  remaining?: number;
  /** OPEN / REST / FULL / EMPTY */
  status?: string;
}

export interface HcDayPlanDto {
  id?: number;
  organizationId?: number;
  planDate?: string;
  restFlag?: boolean;
  remark?: string;
}

export interface HcDayTypeDto {
  id?: number;
  contractTypeCode?: string;
}

export interface HcDaySlotDto {
  id?: number;
  dayPlanId?: number;
  planDate?: string;
  startTime?: string;
  endTime?: string;
  contractTypeCode?: string;
  campusCode?: string;
  capacity?: number;
  bookedCount?: number;
  remaining?: number;
  openFlag?: boolean;
}

export interface LoadHcMonthOverviewResponse {
  days?: HcDaySummaryDto[];
}

export interface LoadHcDayDetailResponse {
  plan?: HcDayPlanDto;
  dayTypes?: HcDayTypeDto[];
  slots?: HcDaySlotDto[];
}

export interface SaveHcSlotTemplateResponse {
  id?: number;
}

export interface GenerateHcFutureDaysResponse {
  generatedDayCount?: number;
}

/** 加载机构号源模板（记住上次配置）。 */
export async function getSlotTemplateApi(params: { organizationId: BookingEntityId }) {
  return publicRequestClient.post<HcSlotTemplateDto>(
    '/healthCheckupSlotCalendar/getSlotTemplate',
    params,
  );
}

/** 保存号源时段模板。 */
export async function saveSlotTemplateApi(params: {
  organizationId: BookingEntityId;
  slotMinutes?: number;
  amStart?: string;
  amEnd?: string;
  pmStart?: string;
  pmEnd?: string;
  capacityByType?: Record<string, number>;
  restWeekdays?: number[];
  campusCodes?: string[];
}) {
  return publicRequestClient.post<SaveHcSlotTemplateResponse>(
    '/healthCheckupSlotCalendar/saveSlotTemplate',
    params,
  );
}

/** 按模板一键生成未来 N 天日计划。 */
export async function generateFutureDaysApi(params: {
  organizationId: BookingEntityId;
  days: number;
  fromDate?: string;
  campusCodes?: string[];
}) {
  return publicRequestClient.post<GenerateHcFutureDaysResponse>(
    '/healthCheckupSlotCalendar/generateFutureDays',
    params,
  );
}

/** 加载月历总览（余量 / 休息 / 约满）。 */
export async function loadMonthOverviewApi(params: {
  organizationId: BookingEntityId;
  yearMonth?: string;
  fromDate?: string;
  toDate?: string;
}) {
  return publicRequestClient.post<LoadHcMonthOverviewResponse>(
    '/healthCheckupSlotCalendar/loadMonthOverview',
    params,
  );
}

/** 加载指定日明细（开放类型 + 时段配额）。 */
export async function loadDayDetailApi(params: {
  organizationId: BookingEntityId;
  planDate: string;
}) {
  return publicRequestClient.post<LoadHcDayDetailResponse>(
    '/healthCheckupSlotCalendar/loadDayDetail',
    params,
  );
}

/** 更新某档容量（新容量 ≥ 已约）。 */
export async function updateSlotCapacityApi(params: {
  daySlotId: BookingEntityId;
  newCapacity: number;
}) {
  return publicRequestClient.post<void>('/healthCheckupSlotCalendar/updateSlotCapacity', params);
}

/** 设置整日休息或恢复开放。 */
export async function setDayRestApi(params: {
  organizationId: BookingEntityId;
  planDate: string;
  restFlag: boolean;
}) {
  return publicRequestClient.post<void>('/healthCheckupSlotCalendar/setDayRest', params);
}

/** 设置某档开放或关闭。 */
export async function setSlotOpenApi(params: { daySlotId: BookingEntityId; openFlag: boolean }) {
  return publicRequestClient.post<void>('/healthCheckupSlotCalendar/setSlotOpen', params);
}

/** 全量替换某日可开展的合同类型（含 WALK_IN）。 */
export async function setDayContractTypesApi(params: {
  organizationId: BookingEntityId;
  planDate: string;
  contractTypeCodes: string[];
}) {
  return publicRequestClient.post<void>('/healthCheckupSlotCalendar/setDayContractTypes', params);
}

/** 手动新增日时段配额。 */
export async function addDaySlotApi(params: {
  organizationId: BookingEntityId;
  planDate: string;
  startTime: string;
  endTime: string;
  contractTypeCode: string;
  campusCode?: string;
  capacity?: number;
  openFlag?: boolean;
}) {
  return publicRequestClient.post<void>('/healthCheckupSlotCalendar/addDaySlot', params);
}

/** 按时间窗与间隔批量加档（已存在跳过；可多校区、各校区独立容量）。 */
export async function batchAddDaySlotsApi(params: {
  organizationId: BookingEntityId;
  planDate: string;
  windowStart: string;
  windowEnd: string;
  slotMinutes: number;
  contractTypeCodes: string[];
  campusCodes?: string[];
  capacityByCampus?: Record<string, number>;
  campusCode?: string;
  capacity?: number;
  openFlag?: boolean;
}) {
  return publicRequestClient.post<{ createdCount?: number; skippedCount?: number }>(
    '/healthCheckupSlotCalendar/batchAddDaySlots',
    params,
  );
}

/** 删除日时段配额（已约 > 0 时服务端拒绝）。 */
export async function deleteDaySlotApi(params: { daySlotId: BookingEntityId }) {
  return publicRequestClient.post<void>('/healthCheckupSlotCalendar/deleteDaySlot', params);
}

/** 复制源日计划到多个目标日。 */
export async function copyDayPlanApi(params: {
  organizationId: BookingEntityId;
  sourceDate: string;
  targetDates: string[];
}) {
  return publicRequestClient.post<void>('/healthCheckupSlotCalendar/copyDayPlan', params);
}
