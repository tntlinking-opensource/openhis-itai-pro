/**
 * 教职工档案 — POST /api/app/campusStaff/<method>
 */
import type { CampusPagedResult, IdLike } from './types';

import type {
  ArchiveChangeLogDto,
  ArchiveTransferDto,
  PublicExpenseProfileDto,
} from './campus-student';

import { publicRequestClient } from '#/api/request';

export interface CampusStaffDto {
  id?: IdLike;
  patientId?: IdLike;
  name?: string;
  pyStr?: string;
  wbStr?: string;
  genderEnum?: string;
  idCard?: string;
  phone?: string;
  employeeNo?: string;
  medicalCode?: string;
  departmentId?: IdLike;
  campusSiteId?: IdLike;
  staffTypeEnum?: string;
  validFrom?: string;
  validTo?: string;
  jobStatusEnum?: string;
  activeFlag?: boolean;
}

export interface StaffDetailDto {
  staff?: CampusStaffDto;
  peProfile?: PublicExpenseProfileDto;
}

export function pageStaffApi(params: {
  keyword?: string;
  departmentId?: IdLike | null;
  campusSiteId?: IdLike | null;
  jobStatus?: string | null;
  activeFlag?: boolean | null;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<CampusPagedResult<CampusStaffDto>>(
    '/campusStaff/pageStaff',
    params,
  );
}

export function createStaffApi(params: {
  name: string;
  employeeNo: string;
  departmentId: IdLike;
  campusSiteId: IdLike;
  patientId?: IdLike | null;
  pyStr?: string;
  wbStr?: string;
  genderEnum?: string;
  idCard?: string;
  phone?: string;
  medicalCode?: string;
  staffTypeEnum?: string;
  validFrom?: string;
  validTo?: string;
  jobStatusEnum?: string;
  activeFlag?: boolean;
  peCardNo?: string;
  peMedicalCode?: string;
  peGfStatusEnum?: string;
  peCategoryEnum?: string;
  peEligibleFlag?: boolean;
  peRemark?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusStaff/createStaff', params);
}

export function updateStaffApi(params: {
  id: IdLike;
  name: string;
  employeeNo: string;
  departmentId: IdLike;
  campusSiteId: IdLike;
  pyStr?: string;
  wbStr?: string;
  genderEnum?: string;
  idCard?: string;
  phone?: string;
  medicalCode?: string;
  staffTypeEnum?: string;
  validFrom?: string;
  validTo?: string;
  jobStatusEnum?: string;
  activeFlag?: boolean;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusStaff/updateStaff', params);
}

export function getStaffDetailApi(params: { id: IdLike }) {
  return publicRequestClient.post<StaffDetailDto>('/campusStaff/getStaffDetail', params);
}

export function disableStaffApi(params: { id: IdLike }) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusStaff/disableStaff', params);
}

export function transferStaffApi(params: {
  staffId: IdLike;
  toDepartmentId: IdLike;
  toCampusSiteId: IdLike;
  effectiveDate?: string;
  reason?: string;
  externalRef?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusStaff/transferStaff', params);
}

export function pageStaffTransfersApi(params: {
  staffId: IdLike;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<CampusPagedResult<ArchiveTransferDto>>(
    '/campusStaff/pageStaffTransfers',
    params,
  );
}

export function pageStaffChangesApi(params: { staffId: IdLike; pageNo: number; pageSize: number }) {
  return publicRequestClient.post<CampusPagedResult<ArchiveChangeLogDto>>(
    '/campusStaff/pageStaffChanges',
    params,
  );
}

export function saveStaffPeProfileApi(params: {
  staffId: IdLike;
  cardNo?: string;
  medicalCode?: string;
  gfStatusEnum?: string;
  categoryEnum?: string;
  eligibleFlag?: boolean;
  remark?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusStaff/saveStaffPeProfile', params);
}

export function bindStaffPatientApi(params: { staffId: IdLike; patientId: IdLike }) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusStaff/bindStaffPatient', params);
}
