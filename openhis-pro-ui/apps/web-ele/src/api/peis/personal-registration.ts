/**
 * 个检登记 — {@code POST /api/app/peisPersonalRegistration/<method>}
 */
import { publicRequestClient } from '#/api/request';

import type { IdLike } from './catalog-config';

export interface CustomerRowDto {
  patientId?: IdLike;
  name?: string;
  idCardNo?: string;
  genderCode?: string;
  phone?: string;
}

export interface SearchCustomerRequest {
  idCardNo?: string;
  nameKeyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface SearchCustomerResponse {
  rows?: CustomerRowDto[];
  total?: number;
}

export interface CreateRegistrationRequest {
  patientId: IdLike;
  packageActivityId: IdLike;
  orgId: IdLike;
  bookingAppointmentId?: IdLike;
  /** freshman / annual / staff / other；空则后端推断 */
  examKind?: string;
}

export interface CreateRegistrationResponse {
  encounterId?: IdLike;
  busNo?: string;
  serviceRequestCount?: number;
}

export interface ServiceRequestLineDto {
  serviceRequestId?: IdLike;
  activityId?: IdLike;
  display?: string;
  execStatusCode?: string;
}

export interface CheckOrderDetailResponse {
  encounterId?: IdLike;
  busNo?: string;
  patientId?: IdLike;
  packageActivityId?: IdLike;
  serviceRequestLines?: ServiceRequestLineDto[];
}

export interface AdjustServiceRequestsRequest {
  encounterId: IdLike;
  addActivityIds?: IdLike[];
  removeServiceRequestIds?: IdLike[];
}

export interface ChargeStatusResponse {
  paid?: boolean;
  unknown?: boolean;
}

export interface ChargeLaunchContextResponse {
  encounterId?: IdLike;
  patientId?: IdLike;
  pathHint?: string;
}

export interface PagePersonalRegistrationsRequest {
  dateFrom: string;
  dateTo: string;
  keyword?: string;
  patientId?: IdLike;
  pageNo: number;
  pageSize: number;
}

export interface PersonalRegistrationHistoryRow {
  encounterId?: IdLike;
  busNo?: string;
  patientId?: IdLike;
  patientName?: string;
  genderCode?: string;
  phone?: string;
  packageActivityId?: IdLike;
  packageName?: string;
  registeredAt?: string;
  statusEnum?: string;
  paid?: boolean;
  chargeUnknown?: boolean;
}

export interface PagePersonalRegistrationsResponse {
  content?: PersonalRegistrationHistoryRow[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}

export function searchCustomerApi(params: SearchCustomerRequest) {
  return publicRequestClient.post<SearchCustomerResponse>(
    '/peisPersonalRegistration/searchCustomer',
    params,
  );
}

export function createRegistrationApi(params: CreateRegistrationRequest) {
  return publicRequestClient.post<CreateRegistrationResponse>(
    '/peisPersonalRegistration/createRegistration',
    params,
  );
}

export function getCheckOrderDetailApi(params: { encounterId: IdLike }) {
  return publicRequestClient.post<CheckOrderDetailResponse>(
    '/peisPersonalRegistration/getCheckOrderDetail',
    params,
  );
}

export function adjustServiceRequestsApi(params: AdjustServiceRequestsRequest) {
  return publicRequestClient.post<CheckOrderDetailResponse>(
    '/peisPersonalRegistration/adjustServiceRequests',
    params,
  );
}

export function getChargeStatusApi(params: { encounterId: IdLike }) {
  return publicRequestClient.post<ChargeStatusResponse>(
    '/peisPersonalRegistration/getChargeStatus',
    params,
  );
}

export function getChargeLaunchContextApi(params: { encounterId: IdLike }) {
  return publicRequestClient.post<ChargeLaunchContextResponse>(
    '/peisPersonalRegistration/getChargeLaunchContext',
    params,
  );
}

export function pagePersonalRegistrationsApi(params: PagePersonalRegistrationsRequest) {
  return publicRequestClient.post<PagePersonalRegistrationsResponse>(
    '/peisPersonalRegistration/pagePersonalRegistrations',
    params,
  );
}
