/**
 * 排班模板 T1/T2 — 对接 {@code POST /api/app/scheduleTemplate/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingEntityId } from './booking-id';
import type { BookingOptionDto, BookingPagedResult } from './booking-types';

export interface SlotProvisionTemplateItemDto {
  startTime: string;
  endTime: string;
  capacity?: number;
}

export interface SlotProvisionTemplateDto {
  id?: BookingEntityId;
  organizationId?: BookingEntityId;
  templateCode?: string;
  templateName?: string;
  slotTemplates?: SlotProvisionTemplateItemDto[];
  enabled?: boolean;
}

export interface ScheduleTemplateDto {
  id?: BookingEntityId;
  organizationId?: BookingEntityId;
  templateCode?: string;
  templateName?: string;
  scheduleMode?: string;
  practitionerId?: BookingEntityId;
  locationId?: BookingEntityId;
  healthcareServiceId?: BookingEntityId;
  slotProvisionTemplateId?: BookingEntityId;
  defaultReleasePolicyId?: BookingEntityId;
  effectiveStart?: string;
  effectiveEnd?: string;
  priority?: number;
  sourcePractitionerRoleId?: BookingEntityId;
  enabled?: boolean;
}

export interface WeekScheduleTemplateItemDto {
  id?: number;
  weekday?: number;
  dayScheduleTemplateId?: BookingEntityId;
  dayScheduleTemplateName?: string;
  sortNo?: number;
  enabled?: boolean;
}

export interface WeekScheduleTemplateDto {
  id?: BookingEntityId;
  organizationId?: BookingEntityId;
  templateCode?: string;
  templateName?: string;
  fallbackBookable?: boolean;
  isDefault?: boolean;
  enabled?: boolean;
  effectiveStart?: string;
  effectiveEnd?: string;
  items?: WeekScheduleTemplateItemDto[];
}

export interface PageTemplatesRequest {
  organizationId?: BookingEntityId;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface SaveSlotProvisionTemplateRequest {
  id?: BookingEntityId;
  organizationId?: BookingEntityId;
  templateCode?: string;
  templateName: string;
  slotTemplates: SlotProvisionTemplateItemDto[];
  enabled: boolean;
}

export interface SaveScheduleTemplateRequest {
  id?: BookingEntityId;
  organizationId: BookingEntityId;
  templateCode?: string;
  templateName: string;
  scheduleMode: string;
  practitionerId?: BookingEntityId;
  locationId?: BookingEntityId;
  healthcareServiceId: BookingEntityId;
  slotProvisionTemplateId?: BookingEntityId;
  defaultReleasePolicyId?: BookingEntityId;
  effectiveStart?: string;
  effectiveEnd?: string;
  priority?: number;
  enabled: boolean;
}

export async function pageSlotProvisionTemplatesApi(params: PageTemplatesRequest) {
  return publicRequestClient.post<BookingPagedResult<SlotProvisionTemplateDto>>(
    '/scheduleTemplate/pageSlotProvisionTemplates',
    params,
  );
}

export async function loadSlotProvisionTemplateApi(params: { id: number }) {
  return publicRequestClient.post<SlotProvisionTemplateDto>(
    '/scheduleTemplate/loadSlotProvisionTemplate',
    params,
  );
}

export async function saveSlotProvisionTemplateApi(params: SaveSlotProvisionTemplateRequest) {
  return publicRequestClient.post<{ id?: BookingEntityId }>(
    '/scheduleTemplate/saveSlotProvisionTemplate',
    params,
  );
}

export async function deleteSlotProvisionTemplateApi(params: { id: number }) {
  return publicRequestClient.post<void>('/scheduleTemplate/deleteSlotProvisionTemplate', params);
}

export async function listSlotProvisionTemplateOptionsApi(params: {
  organizationId?: BookingEntityId;
}) {
  return publicRequestClient.post<{ options?: BookingOptionDto[] }>(
    '/scheduleTemplate/listSlotProvisionTemplateOptions',
    params,
  );
}

export async function pageScheduleTemplatesApi(params: PageTemplatesRequest) {
  return publicRequestClient.post<BookingPagedResult<ScheduleTemplateDto>>(
    '/scheduleTemplate/pageScheduleTemplates',
    params,
  );
}

export async function loadScheduleTemplateApi(params: { id: number }) {
  return publicRequestClient.post<ScheduleTemplateDto>(
    '/scheduleTemplate/loadScheduleTemplate',
    params,
  );
}

export async function saveScheduleTemplateApi(params: SaveScheduleTemplateRequest) {
  return publicRequestClient.post<{ id?: BookingEntityId }>(
    '/scheduleTemplate/saveScheduleTemplate',
    params,
  );
}

export async function deleteScheduleTemplateApi(params: { id: number }) {
  return publicRequestClient.post<void>('/scheduleTemplate/deleteScheduleTemplate', params);
}

export interface ImportFromPractitionerRoleRequest {
  organizationId: BookingEntityId;
  practitionerId: BookingEntityId;
  templateCode?: string;
  templateName?: string;
}

export interface ImportFromPractitionerRoleResponse {
  scheduleTemplateId?: number;
  templateCode?: string;
}

export async function importFromPractitionerRoleApi(params: ImportFromPractitionerRoleRequest) {
  return publicRequestClient.post<ImportFromPractitionerRoleResponse>(
    '/scheduleTemplate/importFromPractitionerRole',
    params,
  );
}

export interface SaveWeekScheduleTemplateRequest {
  id?: number;
  organizationId: BookingEntityId;
  templateCode?: string;
  templateName: string;
  fallbackBookable?: boolean;
  isDefault?: boolean;
  enabled: boolean;
  effectiveStart?: string;
  effectiveEnd?: string;
  items?: Array<{
    weekday: number;
    dayScheduleTemplateId: BookingEntityId;
    sortNo?: number;
    enabled?: boolean;
  }>;
}

export async function pageWeekScheduleTemplatesApi(params: PageTemplatesRequest) {
  return publicRequestClient.post<BookingPagedResult<WeekScheduleTemplateDto>>(
    '/scheduleTemplate/pageWeekScheduleTemplates',
    params,
  );
}

export async function loadWeekScheduleTemplateApi(params: { id: number }) {
  return publicRequestClient.post<WeekScheduleTemplateDto>(
    '/scheduleTemplate/loadWeekScheduleTemplate',
    params,
  );
}

export async function saveWeekScheduleTemplateApi(params: SaveWeekScheduleTemplateRequest) {
  return publicRequestClient.post<{ id?: BookingEntityId }>(
    '/scheduleTemplate/saveWeekScheduleTemplate',
    params,
  );
}

export async function deleteWeekScheduleTemplateApi(params: { id: number }) {
  return publicRequestClient.post<void>('/scheduleTemplate/deleteWeekScheduleTemplate', params);
}
