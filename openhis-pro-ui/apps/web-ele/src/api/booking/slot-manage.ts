/**
 * 号源日历 — 对接 {@code POST /api/app/slotManage/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingPagedResult } from './booking-types';

export interface PageSlotsByDateRequest {
  organizationId: number;
  date: string;
  practitionerId?: number;
  scheduleId?: number;
  statusCode?: string;
  pageNo: number;
  pageSize: number;
}

export interface SlotListItemDto {
  slotId?: number;
  scheduleId?: number;
  organizationId?: number;
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
  slotId?: number;
  scheduleId?: number;
  organizationId?: number;
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
  slotIds: number[];
  reason?: string;
}

export interface UnblockSlotsRequest {
  slotIds: number[];
}

export interface SetOverbookedRequest {
  slotId: number;
  overbookedFlag: boolean;
}

export async function pageSlotsByDateApi(params: PageSlotsByDateRequest) {
  return publicRequestClient.post<BookingPagedResult<SlotListItemDto>>(
    '/slotManage/pageSlotsByDate',
    params,
  );
}

export async function loadSlotDetailApi(params: { slotId: number }) {
  return publicRequestClient.post<SlotDetailDto>('/slotManage/loadSlotDetail', params);
}

export async function blockSlotsApi(params: BlockSlotsRequest) {
  return publicRequestClient.post<void>('/slotManage/blockSlots', params);
}

export async function unblockSlotsApi(params: UnblockSlotsRequest) {
  return publicRequestClient.post<void>('/slotManage/unblockSlots', params);
}

export async function setOverbookedApi(params: SetOverbookedRequest) {
  return publicRequestClient.post<void>('/slotManage/setOverbooked', params);
}
