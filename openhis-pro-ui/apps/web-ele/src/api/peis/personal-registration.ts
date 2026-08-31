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
  /** personal / group / freshman / annual / staff / other；空则默认 personal */
  examKind?: string;
  /** 团体体检单位 */
  companyId?: IdLike;
  /** 团体体检分组（批次） */
  groupBatchId?: IdLike;
  /** 本次体检推荐人 */
  referrerName?: string;
  /** 本次体检是否提供早餐 */
  breakfastFlag?: boolean | null;
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

export interface PeisGuidePrintSlipDto {
  hospitalName?: string;
  patientName?: string;
  genderEnumText?: string;
  ageText?: string;
  busNo?: string;
  packageName?: string;
  registeredAtText?: string;
  encounterId?: IdLike;
  patientId?: IdLike;
  scenarioCode?: string;
  guideLines?: Array<{
    seqText?: string;
    deptName?: string;
    itemName?: string;
    locationText?: string;
  }>;
}

export function loadGuidePrintSlipApi(params: { encounterId: IdLike }) {
  return publicRequestClient.post<PeisGuidePrintSlipDto>(
    '/peisPersonalRegistration/loadGuidePrintSlip',
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

export async function listActivePersonalRegistrationsApi(params: {
  patientId: IdLike;
}): Promise<{ rows?: PersonalRegistrationHistoryRow[] }> {
  return publicRequestClient.post<{ rows?: PersonalRegistrationHistoryRow[] }>(
    '/peisPersonalRegistration/listActivePersonalRegistrations',
    params,
  );
}
