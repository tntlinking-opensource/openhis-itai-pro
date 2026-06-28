/**
 * T3 日历例外 — 对接 {@code POST /api/app/calendarException/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingEntityId } from './booking-id';
import type { BookingPagedResult } from './booking-types';

export type CalendarExceptionType = 'CLOSED' | 'REDUCED' | 'OVERRIDE' | 'EXTRA';

export interface CalendarExceptionDto {
  id?: number;
  organizationId?: number;
  exceptionDate?: string;
  rangeEnd?: string;
  exceptionType?: CalendarExceptionType;
  overrideScheduleTemplateId?: number;
  reason?: string;
  autoApplyOnGenerate?: boolean;
}

export interface CalendarExceptionMonthDayDto {
  date?: string;
  inMonth?: boolean;
  exceptionTypes?: CalendarExceptionType[];
}

export interface PageCalendarExceptionsRequest {
  organizationId?: number;
  exceptionType?: CalendarExceptionType;
  rangeStart?: string;
  rangeEnd?: string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface SaveCalendarExceptionRequest {
  id?: number;
  organizationId?: number;
  exceptionDate: string;
  rangeEnd?: string;
  exceptionType: CalendarExceptionType;
  overrideScheduleTemplateId?: number;
  reason?: string;
  autoApplyOnGenerate?: boolean;
}

export async function pageCalendarExceptionsApi(params: PageCalendarExceptionsRequest) {
  return publicRequestClient.post<BookingPagedResult<CalendarExceptionDto>>(
    '/calendarException/pageCalendarExceptions',
    params,
  );
}

export async function loadCalendarExceptionApi(params: { id: BookingEntityId }) {
  return publicRequestClient.post<CalendarExceptionDto>(
    '/calendarException/loadCalendarException',
    params,
  );
}

export async function saveCalendarExceptionApi(params: SaveCalendarExceptionRequest) {
  return publicRequestClient.post<{ id?: number }>(
    '/calendarException/saveCalendarException',
    params,
  );
}

export async function deleteCalendarExceptionApi(params: { id: BookingEntityId }) {
  return publicRequestClient.post<void>('/calendarException/deleteCalendarException', params);
}

export async function listCalendarExceptionsByMonthApi(params: {
  organizationId?: number;
  month: string;
}) {
  return publicRequestClient.post<{ days?: CalendarExceptionMonthDayDto[] }>(
    '/calendarException/listCalendarExceptionsByMonth',
    params,
  );
}

export const calendarExceptionTypeOptions: { value: CalendarExceptionType; label: string }[] = [
  { value: 'CLOSED', label: '停诊 (CLOSED)' },
  { value: 'REDUCED', label: '减号 (REDUCED)' },
  { value: 'OVERRIDE', label: '替换模板 (OVERRIDE)' },
  { value: 'EXTRA', label: '加班 (EXTRA)' },
];

export function calendarExceptionTypeLabel(type?: string) {
  return calendarExceptionTypeOptions.find((item) => item.value === type)?.label ?? type ?? '-';
}
