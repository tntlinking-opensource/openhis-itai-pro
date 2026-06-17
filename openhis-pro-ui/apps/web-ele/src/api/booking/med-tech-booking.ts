/**
 * 医技预约 — 对接 {@code POST /api/app/medTechBooking/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingPagedResult } from './booking-types';
import type { BookableSlotDto } from './booking-register';

export interface SearchEncounterRequest {
  organizationId: number;
  keyword?: string;
  encounterBusNo?: string;
  patientId?: number;
}

export interface EncounterContextDto {
  encounterId?: number;
  encounterBusNo?: string;
  patientId?: number;
  patientName?: string;
  organizationId?: number;
  organizationName?: string;
}

export interface ServiceRequestItemDto {
  serviceRequestId?: number;
  serviceRequestBusNo?: string;
  activityName?: string;
  statusView?: string;
  bookedFlag?: boolean;
  appointmentId?: number;
}

export interface ListBookableServiceRequestsRequest {
  organizationId: number;
  encounterId: number;
}

export interface ListBookableServiceRequestsResponse {
  items?: ServiceRequestItemDto[];
}

export interface ListDiagnosticBookableSlotsRequest {
  organizationId: number;
  serviceRequestId: number;
  date: string;
}

export interface ListDiagnosticBookableSlotsResponse {
  slots?: BookableSlotDto[];
}

export interface CreateDiagnosticBookingRequest {
  organizationId: number;
  patientId: number;
  serviceRequestId: number;
  slotId: number;
  originChannel?: string;
}

export interface CreateDiagnosticBookingResponse {
  appointmentId?: number;
  busNo?: string;
}

export interface CancelDiagnosticBookingRequest {
  appointmentId: number;
  reason?: string;
}

export async function searchEncounterApi(params: SearchEncounterRequest) {
  return publicRequestClient.post<EncounterContextDto>('/medTechBooking/searchEncounter', params);
}

export async function listBookableServiceRequestsApi(params: ListBookableServiceRequestsRequest) {
  return publicRequestClient.post<ListBookableServiceRequestsResponse>(
    '/medTechBooking/listBookableServiceRequests',
    params,
  );
}

export async function listDiagnosticBookableSlotsApi(params: ListDiagnosticBookableSlotsRequest) {
  return publicRequestClient.post<ListDiagnosticBookableSlotsResponse>(
    '/medTechBooking/listDiagnosticBookableSlots',
    params,
  );
}

export async function createDiagnosticBookingApi(params: CreateDiagnosticBookingRequest) {
  return publicRequestClient.post<CreateDiagnosticBookingResponse>(
    '/medTechBooking/createDiagnosticBooking',
    params,
  );
}

export async function cancelDiagnosticBookingApi(params: CancelDiagnosticBookingRequest) {
  return publicRequestClient.post<void>('/medTechBooking/cancelDiagnosticBooking', params);
}

export async function pageDiagnosticBookingsApi(params: {
  organizationId: number;
  encounterId?: number;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<BookingPagedResult<ServiceRequestItemDto>>(
    '/medTechBooking/pageDiagnosticBookings',
    params,
  );
}
