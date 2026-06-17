/**
 * 床位预约 — 对接 {@code POST /api/app/bedBooking/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingOptionDto, BookingPagedResult } from './booking-types';

export interface WardTreeNodeDto extends BookingOptionDto {
  children?: WardTreeNodeDto[];
}

export interface LoadWardTreeRequest {
  organizationId: number;
}

export interface LoadWardTreeResponse {
  wards?: WardTreeNodeDto[];
}

export interface PageBedMatrixRequest {
  organizationId: number;
  wardLocationId: number;
  rangeStart: string;
  rangeEnd: string;
}

export interface BedMatrixCellDto {
  bedLocationId?: number;
  bedName?: string;
  date?: string;
  operationalCode?: string;
  displayStatus?: string;
  appointmentId?: number;
  patientName?: string;
}

export interface BedMatrixRowDto {
  bedLocationId?: number;
  bedName?: string;
  cells?: BedMatrixCellDto[];
}

export interface PageBedMatrixResponse {
  dates?: string[];
  rows?: BedMatrixRowDto[];
}

export interface ReserveBedRequest {
  organizationId: number;
  bedLocationId: number;
  patientId: number;
  intendedAdmissionDate: string;
  commentText?: string;
}

export interface ReserveBedResponse {
  appointmentId?: number;
  busNo?: string;
}

export interface CancelBedReservationRequest {
  appointmentId: number;
  reason?: string;
}

export interface BedReservationListItemDto {
  appointmentId?: number;
  busNo?: string;
  patientId?: number;
  patientName?: string;
  bedLocationId?: number;
  bedName?: string;
  intendedAdmissionDate?: string;
  statusView?: string;
}

export async function loadWardTreeApi(params: LoadWardTreeRequest) {
  return publicRequestClient.post<LoadWardTreeResponse>('/bedBooking/loadWardTree', params);
}

export async function pageBedMatrixApi(params: PageBedMatrixRequest) {
  return publicRequestClient.post<PageBedMatrixResponse>('/bedBooking/pageBedMatrix', params);
}

export async function reserveBedApi(params: ReserveBedRequest) {
  return publicRequestClient.post<ReserveBedResponse>('/bedBooking/reserveBed', params);
}

export async function cancelBedReservationApi(params: CancelBedReservationRequest) {
  return publicRequestClient.post<void>('/bedBooking/cancelBedReservation', params);
}

export async function pageBedReservationsApi(params: {
  organizationId: number;
  wardLocationId?: number;
  dateFrom?: string;
  dateTo?: string;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<BookingPagedResult<BedReservationListItemDto>>(
    '/bedBooking/pageBedReservations',
    params,
  );
}
