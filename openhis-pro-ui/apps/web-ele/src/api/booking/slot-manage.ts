/**
 * 号源日历 — 对接 {@code POST /api/app/slotManage/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingPagedResult } from './booking-types';
import type { BookingEntityId } from './booking-id';

export interface PageSlotsByDateRequest {
  organizationId: BookingEntityId;
  date: string;
  practitionerId?: BookingEntityId;
  scheduleId?: BookingEntityId;
  statusCode?: string;
  pageNo: number;
  pageSize: number;
}

export interface SlotListItemDto {
  slotId?: BookingEntityId;
  scheduleId?: BookingEntityId;
  organizationId?: BookingEntityId;
  practitionerId?: BookingEntityId;
  healthcareServiceId?: BookingEntityId;
  practitionerName?: string;
  healthcareServiceName?: string;
  organizationName?: string;
  startTime?: string;
  endTime?: string;
  statusCode?: string;
  capacity?: number;
  bookedCount?: number;
  remainingCount?: number;
  displayStatus?: string;
  overbookedFlag?: boolean;
}

export interface SlotDetailDto {
  slotId?: BookingEntityId;
  scheduleId?: BookingEntityId;
  organizationId?: BookingEntityId;
  practitionerId?: BookingEntityId;
  healthcareServiceId?: BookingEntityId;
  practitionerName?: string;
  healthcareServiceName?: string;
  organizationName?: string;
  startTime?: string;
  endTime?: string;
  statusCode?: string;
  capacity?: number;
  bookedCount?: number;
  remainingCount?: number;
  displayStatus?: string;
  overbookedFlag?: boolean;
  commentText?: string;
}

export interface BlockSlotsRequest {
  slotIds: BookingEntityId[];
  reason?: string;
}

export interface UnblockSlotsRequest {
  slotIds: BookingEntityId[];
}

export interface DeleteSlotsRequest {
  slotIds: BookingEntityId[];
}

export interface SetOverbookedRequest {
  slotId: BookingEntityId;
  overbookedFlag: boolean;
}

export async function pageSlotsByDateApi(params: PageSlotsByDateRequest) {
  return publicRequestClient.post<BookingPagedResult<SlotListItemDto>>(
    '/slotManage/pageSlotsByDate',
    params,
  );
}

export async function loadSlotDetailApi(params: { slotId: BookingEntityId }) {
  return publicRequestClient.post<SlotDetailDto>('/slotManage/loadSlotDetail', params);
}

export async function blockSlotsApi(params: BlockSlotsRequest) {
  return publicRequestClient.post<void>('/slotManage/blockSlots', params);
}

export async function unblockSlotsApi(params: UnblockSlotsRequest) {
  return publicRequestClient.post<void>('/slotManage/unblockSlots', params);
}

export async function deleteSlotsApi(params: DeleteSlotsRequest) {
  return publicRequestClient.post<void>('/slotManage/deleteSlots', params);
}

export async function setOverbookedApi(params: SetOverbookedRequest) {
  return publicRequestClient.post<void>('/slotManage/setOverbooked', params);
}
