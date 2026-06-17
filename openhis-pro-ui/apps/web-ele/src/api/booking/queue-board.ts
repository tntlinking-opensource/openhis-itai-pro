/**
 * 候诊叫号 — 对接 {@code POST /api/app/queueBoard/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingPagedResult } from './booking-types';

export interface PageQueueRequest {
  organizationId: number;
  destinationLocationId: number;
  pageNo: number;
  pageSize: number;
}

export interface QueueListItemDto {
  appointmentId?: number;
  busNo?: string;
  patientId?: number;
  patientName?: string;
  encounterId?: number;
  displayOrder?: number;
  triageLevel?: string;
  appointmentKind?: string;
  startInstant?: string;
  statusView?: string;
  substituteDisplay?: string;
}

export interface CheckInRequest {
  organizationId: number;
  destinationLocationId?: number;
  appointmentId?: number;
  appointmentBusNo?: string;
  encounterId?: number;
  patientId?: number;
  originChannel?: string;
}

export interface CheckInResponse {
  appointmentId?: number;
  busNo?: string;
  displayOrder?: number;
  appointmentKind?: string;
}

export interface CallToSeeRequest {
  appointmentId: number;
  practitionerId: number;
}

export interface CallToSeeResponse {
  appointmentId?: number;
  busNo?: string;
  patientId?: number;
  patientName?: string;
  displayOrder?: number;
  calledAt?: string;
  practitionerId?: number;
}

export interface NoShowRequest {
  appointmentId: number;
  reasonCode?: string;
}

export async function pageQueueApi(params: PageQueueRequest) {
  return publicRequestClient.post<BookingPagedResult<QueueListItemDto>>(
    '/queueBoard/pageQueue',
    params,
  );
}

export async function checkInApi(params: CheckInRequest) {
  return publicRequestClient.post<CheckInResponse>('/queueBoard/checkIn', params);
}

export async function callToSeeApi(params: CallToSeeRequest) {
  return publicRequestClient.post<CallToSeeResponse>('/queueBoard/callToSee', params);
}

export async function noShowApi(params: NoShowRequest) {
  return publicRequestClient.post<void>('/queueBoard/noShow', params);
}

export interface PriorityTreatmentRequest {
  appointmentId: number;
  reasonCode?: string;
}

export interface ChangeOverRequest {
  organizationId: number;
  destinationLocationId: number;
  appointmentId: number;
  targetDisplayOrder: number;
}

export interface GetQueueDisplaySnapshotRequest {
  organizationId: number;
  destinationLocationId: number;
}

export interface QueueDisplaySnapshotDto {
  locationName?: string;
  organizationName?: string;
  currentCall?: CallToSeeResponse | null;
  waitingList?: QueueListItemDto[];
  completedList?: QueueListItemDto[];
  polledAt?: string;
}

export interface PageCompletedQueueRequest {
  organizationId: number;
  destinationLocationId: number;
  pageNo: number;
  pageSize: number;
}

export async function priorityTreatmentApi(params: PriorityTreatmentRequest) {
  return publicRequestClient.post<void>('/queueBoard/priorityTreatment', params);
}

export async function changeOverApi(params: ChangeOverRequest) {
  return publicRequestClient.post<void>('/queueBoard/changeOver', params);
}

export async function getQueueDisplaySnapshotApi(params: GetQueueDisplaySnapshotRequest) {
  return publicRequestClient.post<QueueDisplaySnapshotDto>(
    '/queueBoard/getQueueDisplaySnapshot',
    params,
  );
}

export async function pageCompletedQueueApi(params: PageCompletedQueueRequest) {
  return publicRequestClient.post<BookingPagedResult<QueueListItemDto>>(
    '/queueBoard/pageCompletedQueue',
    params,
  );
}
