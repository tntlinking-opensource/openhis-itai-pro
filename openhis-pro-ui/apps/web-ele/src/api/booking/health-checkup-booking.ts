/**
 * 体检预约 — 对接 {@code POST /api/app/healthCheckupBooking/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingPagedResult } from './booking-types';
import type { BookableSlotDto } from './booking-register';

export interface GroupOrgDto {
  id?: number;
  orgCode?: string;
  orgName?: string;
  contactName?: string;
  contactPhone?: string;
  settlementMode?: string;
}

export interface GroupBatchDto {
  id?: number;
  groupOrgId?: number;
  groupOrgName?: string;
  batchName?: string;
  bookingDate?: string;
  slotStartTime?: string;
  capacity?: number;
  bookedCount?: number;
  packageName?: string;
}

export interface GroupRosterDto {
  id?: number;
  batchId?: number;
  name?: string;
  idCardNoMasked?: string;
  studentNo?: string;
  phoneMasked?: string;
  bookingStatusView?: string;
  appointmentBusNo?: string;
}

export interface HealthCheckPackageDto {
  packageId?: number;
  packageCode?: string;
  packageName?: string;
  genderLimit?: string;
  estimatedAmount?: number;
}

export async function pageGroupOrgsApi(params: {
  keyword?: string;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<BookingPagedResult<GroupOrgDto>>(
    '/healthCheckupBooking/pageGroupOrgs',
    params,
  );
}

export async function saveGroupOrgApi(params: Partial<GroupOrgDto> & { orgName: string }) {
  return publicRequestClient.post<{ id?: number }>('/healthCheckupBooking/saveGroupOrg', params);
}

export async function deleteGroupOrgApi(params: { id: number }) {
  return publicRequestClient.post<void>('/healthCheckupBooking/deleteGroupOrg', params);
}

export async function pageGroupBatchesApi(params: {
  groupOrgId?: number;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<BookingPagedResult<GroupBatchDto>>(
    '/healthCheckupBooking/pageGroupBatches',
    params,
  );
}

export async function saveGroupBatchApi(params: Partial<GroupBatchDto> & { batchName: string }) {
  return publicRequestClient.post<{ id?: number }>('/healthCheckupBooking/saveGroupBatch', params);
}

export async function deleteGroupBatchApi(params: { id: number }) {
  return publicRequestClient.post<void>('/healthCheckupBooking/deleteGroupBatch', params);
}

export async function pageGroupRostersApi(params: {
  batchId: number;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<BookingPagedResult<GroupRosterDto>>(
    '/healthCheckupBooking/pageGroupRosters',
    params,
  );
}

export async function importGroupRosterApi(params: { batchId: number; fileToken?: string }) {
  return publicRequestClient.post<{ importedCount?: number }>(
    '/healthCheckupBooking/importGroupRoster',
    params,
  );
}

export async function batchBookGroupRosterApi(params: { batchId: number; rosterIds?: number[] }) {
  return publicRequestClient.post<{ bookedCount?: number }>(
    '/healthCheckupBooking/batchBookGroupRoster',
    params,
  );
}

export async function checkInHealthCheckupApi(params: {
  organizationId: number;
  appointmentBusNo?: string;
  appointmentId?: number;
}) {
  return publicRequestClient.post<{ displayOrder?: number }>(
    '/healthCheckupBooking/checkInHealthCheckup',
    params,
  );
}

export async function listIndividualPackagesApi(params: { organizationId: number }) {
  return publicRequestClient.post<{ packages?: HealthCheckPackageDto[] }>(
    '/healthCheckupBooking/listIndividualPackages',
    params,
  );
}

export async function listIndividualBookableSlotsApi(params: {
  organizationId: number;
  packageId: number;
  date: string;
}) {
  return publicRequestClient.post<{ slots?: BookableSlotDto[] }>(
    '/healthCheckupBooking/listIndividualBookableSlots',
    params,
  );
}

export async function createIndividualBookingApi(params: {
  organizationId: number;
  patientId: number;
  packageId: number;
  slotId: number;
  originChannel?: string;
}) {
  return publicRequestClient.post<{ appointmentId?: number; busNo?: string }>(
    '/healthCheckupBooking/createIndividualBooking',
    params,
  );
}
