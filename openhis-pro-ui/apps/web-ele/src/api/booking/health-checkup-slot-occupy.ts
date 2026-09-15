/**
 * 体检号源占号 — 对接 {@code POST /api/app/healthCheckupSlotOccupy/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingEntityId } from './booking-id';

export interface HcBookableDayDto {
  planDate?: string;
  remaining?: number;
}

export interface HcBookableSlotDto {
  daySlotId?: number;
  startTime?: string;
  endTime?: string;
  campusCode?: string;
  remaining?: number;
}

export interface ListHcBookableDaysResponse {
  days?: HcBookableDayDto[];
}

export interface ListHcBookableSlotsResponse {
  slots?: HcBookableSlotDto[];
}

export interface CreateHcSlotBookingResponse {
  appointmentId?: number;
  busNo?: string;
}

export interface CancelHcSlotBookingResponse {
  appointmentId?: number;
}

export interface RescheduleHcSlotBookingResponse {
  appointmentId?: number;
  busNo?: string;
}

/** 列出可约日期（按合同类型 / 校区过滤）。 */
export async function listBookableDaysApi(params: {
  organizationId: BookingEntityId;
  contractTypeCode: string;
  campusCode?: string;
  fromDate: string;
  toDate: string;
}) {
  return publicRequestClient.post<ListHcBookableDaysResponse>(
    '/healthCheckupSlotOccupy/listBookableDays',
    params,
  );
}

/** 列出指定日可约时段。 */
export async function listBookableSlotsApi(params: {
  organizationId: BookingEntityId;
  planDate: string;
  contractTypeCode: string;
  campusCode?: string;
}) {
  return publicRequestClient.post<ListHcBookableSlotsResponse>(
    '/healthCheckupSlotOccupy/listBookableSlots',
    params,
  );
}

/** 创建体检号源预约（窗口 / 小程序共用）。 */
export async function createHcSlotBookingApi(params: {
  organizationId: BookingEntityId;
  patientId: BookingEntityId;
  packageId: BookingEntityId;
  daySlotId: BookingEntityId;
  contractId?: BookingEntityId;
  contractTypeCode?: string;
  originChannel?: string;
}) {
  return publicRequestClient.post<CreateHcSlotBookingResponse>(
    '/healthCheckupSlotOccupy/createBooking',
    params,
  );
}

/** 取消体检号源预约。 */
export async function cancelHcSlotBookingApi(params: {
  appointmentId: BookingEntityId;
  reason?: string;
  cancellationReasonCode?: string;
}) {
  return publicRequestClient.post<CancelHcSlotBookingResponse>(
    '/healthCheckupSlotOccupy/cancelBooking',
    params,
  );
}

/** 改约到新时段配额。 */
export async function rescheduleHcSlotBookingApi(params: {
  appointmentId: BookingEntityId;
  newDaySlotId: BookingEntityId;
}) {
  return publicRequestClient.post<RescheduleHcSlotBookingResponse>(
    '/healthCheckupSlotOccupy/rescheduleBooking',
    params,
  );
}
