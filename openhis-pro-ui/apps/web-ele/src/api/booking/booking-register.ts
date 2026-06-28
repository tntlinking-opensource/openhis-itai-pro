/**
 * 门诊预约 — 对接 {@code POST /api/app/bookingRegister/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingEntityId } from './booking-id';
import type { BookingPagedResult } from './booking-types';

export interface PageBookingsRequest {
  organizationId: number;
  patientId?: number;
  dateFrom?: string;
  dateTo?: string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface BookingListItemDto {
  appointmentId?: number;
  busNo?: string;
  patientId?: number;
  patientName?: string;
  organizationId?: number;
  slotId?: number;
  startInstant?: string;
  endInstant?: string;
  destinationLocationId?: number;
  statusView?: string;
}

export interface ListBookableSlotsRequest {
  organizationId: BookingEntityId;
  date: string;
  practitionerId?: BookingEntityId;
  healthcareServiceId?: BookingEntityId;
  scheduleId?: BookingEntityId;
}

export interface BookableSlotDto {
  slotId?: number;
  scheduleId?: number;
  startTime?: string;
  endTime?: string;
  capacity?: number;
  bookedCount?: number;
  remainingCount?: number;
  fullFlag?: boolean;
  virtualSlotKey?: string;
  fromTemplate?: boolean;
  waitlistEligible?: boolean;
  waitlistCount?: number;
}

export interface ListBookableSlotsResponse {
  slots?: BookableSlotDto[];
}

export interface CreateOutpatientBookingRequest {
  patientId: BookingEntityId;
  slotId?: BookingEntityId;
  virtualSlotKey?: string;
  organizationId: BookingEntityId;
  destinationLocationId?: BookingEntityId;
  originChannel?: string;
}

export interface CreateOutpatientBookingResponse {
  appointmentId?: number;
  busNo?: string;
}

export interface CancelBookingRequest {
  appointmentId: number;
  reason?: string;
}

export interface CancelBookingResponse {
  waitlistPromoted?: boolean;
  promotedAppointmentId?: number;
  promotedBusNo?: string;
}

export interface JoinWaitlistRequest {
  organizationId: BookingEntityId;
  patientId: BookingEntityId;
  slotId: BookingEntityId;
}

export interface JoinWaitlistResponse {
  waitlistEntryId?: number;
  slotId?: number;
  queuePosition?: number;
  waitingCount?: number;
}

export interface CancelWaitlistRequest {
  waitlistEntryId: number;
  reason?: string;
}

export interface RescheduleBookingRequest {
  appointmentId: number;
  newSlotId: number;
  reason?: string;
}

export interface RescheduleBookingResponse {
  appointmentId?: number;
  busNo?: string;
}

export interface BookingDetailDto {
  appointmentId?: number;
  busNo?: string;
  patientId?: number;
  patientName?: string;
  slotId?: number;
  startInstant?: string;
  endInstant?: string;
  statusView?: string;
}

export interface BookingPatientItem {
  patientId?: number;
  patientBusNo?: string;
  name?: string;
  genderCode?: string;
  birthDate?: string;
  idCardNoMasked?: string;
  phoneMasked?: string;
}

export async function pageBookingsApi(params: PageBookingsRequest) {
  return publicRequestClient.post<BookingPagedResult<BookingListItemDto>>(
    '/bookingRegister/pageBookings',
    params,
  );
}

export async function listBookableSlotsApi(params: ListBookableSlotsRequest) {
  return publicRequestClient.post<ListBookableSlotsResponse>(
    '/bookingRegister/listBookableSlots',
    params,
  );
}

export async function createOutpatientBookingApi(params: CreateOutpatientBookingRequest) {
  return publicRequestClient.post<CreateOutpatientBookingResponse>(
    '/bookingRegister/createOutpatientBooking',
    params,
  );
}

export async function cancelBookingApi(params: CancelBookingRequest) {
  return publicRequestClient.post<CancelBookingResponse>('/bookingRegister/cancelBooking', params);
}

export async function joinWaitlistApi(params: JoinWaitlistRequest) {
  return publicRequestClient.post<JoinWaitlistResponse>('/bookingRegister/joinWaitlist', params);
}

export async function cancelWaitlistApi(params: CancelWaitlistRequest) {
  return publicRequestClient.post<void>('/bookingRegister/cancelWaitlist', params);
}

export async function rescheduleBookingApi(params: RescheduleBookingRequest) {
  return publicRequestClient.post<RescheduleBookingResponse>(
    '/bookingRegister/rescheduleBooking',
    params,
  );
}

export async function loadBookingDetailApi(params: { appointmentId: number }) {
  return publicRequestClient.post<BookingDetailDto>('/bookingRegister/loadBookingDetail', params);
}

export async function pagePatientsForSelectApi(params: {
  keyword?: string;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<BookingPagedResult<BookingPatientItem>>(
    '/bookingRegister/pagePatientsForSelect',
    params,
  );
}
