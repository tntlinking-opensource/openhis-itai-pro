/**
 * 学生档案 — POST /api/app/campusStudent/<method>
 */
import type { CampusPagedResult, IdLike } from './types';

import { publicRequestClient } from '#/api/request';

export interface CampusStudentDto {
  id?: IdLike;
  patientId?: IdLike;
  name?: string;
  pyStr?: string;
  wbStr?: string;
  genderEnum?: string;
  age?: number;
  birthday?: string;
  studentNo?: string;
  idCard?: string;
  phone?: string;
  collegeId?: IdLike;
  collegeName?: string;
  campusCode?: string;
  major?: string;
  majorCode?: string;
  eduLevelEnum?: string;
  enrollTime?: string;
  leaveTime?: string;
  grade?: string;
  className?: string;
  studyYears?: number;
  schoolYear?: number;
  studyTypeEnum?: string;
  schoolStatusEnum?: string;
  physicalExamResultEnum?: string;
  nativePlace?: string;
  counselorName?: string;
  counselorPhone?: string;
  address?: string;
  doorplate?: string;
  activeFlag?: boolean;
}

export interface PublicExpenseProfileDto {
  id?: IdLike;
  subjectTypeEnum?: string;
  subjectId?: IdLike;
  patientId?: IdLike;
  cardNo?: string;
  medicalCode?: string;
  gfStatusEnum?: string;
  categoryEnum?: string;
  validFrom?: string;
  validTo?: string;
  eligibleFlag?: boolean;
  remark?: string;
}

export interface StudentDetailDto {
  student?: CampusStudentDto;
  peProfile?: PublicExpenseProfileDto;
}

export interface ArchiveChangeLogDto {
  id?: IdLike;
  subjectTypeEnum?: string;
  subjectId?: IdLike;
  changeTypeEnum?: string;
  fieldName?: string;
  oldValue?: string;
  newValue?: string;
  operatorId?: IdLike;
  operatorName?: string;
  changeTime?: string;
  changeSourceEnum?: string;
  campusCode?: string;
  batchId?: string;
  remark?: string;
}

export interface ArchiveTransferDto {
  id?: IdLike;
  subjectTypeEnum?: string;
  subjectId?: IdLike;
  patientId?: IdLike;
  transferTypeEnum?: string;
  effectiveDate?: string;
  fromCampusCode?: string;
  toCampusCode?: string;
  fromCollegeId?: IdLike;
  toCollegeId?: IdLike;
  fromDepartmentId?: IdLike;
  toDepartmentId?: IdLike;
  reason?: string;
  externalRef?: string;
  transferSourceEnum?: string;
  operatorName?: string;
  operateTime?: string;
  changeLogBatchId?: string;
}

export function pageStudentsApi(params: {
  keyword?: string;
  collegeId?: IdLike | null;
  campusCode?: string | null;
  schoolStatus?: string | null;
  activeFlag?: boolean | null;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<CampusPagedResult<CampusStudentDto>>(
    '/campusStudent/pageStudents',
    params,
  );
}

export function createStudentApi(params: {
  name: string;
  studentNo: string;
  collegeId: IdLike;
  campusCode: string;
  patientId?: IdLike | null;
  pyStr?: string;
  wbStr?: string;
  genderEnum?: string;
  age?: number;
  birthday?: string;
  idCard?: string;
  phone?: string;
  major?: string;
  majorCode?: string;
  eduLevelEnum?: string;
  enrollTime?: string;
  leaveTime?: string;
  grade?: string;
  className?: string;
  studyYears?: number;
  schoolYear?: number;
  studyTypeEnum?: string;
  schoolStatusEnum?: string;
  physicalExamResultEnum?: string;
  nativePlace?: string;
  counselorName?: string;
  counselorPhone?: string;
  address?: string;
  doorplate?: string;
  activeFlag?: boolean;
  peCardNo?: string;
  peMedicalCode?: string;
  peGfStatusEnum?: string;
  peCategoryEnum?: string;
  peEligibleFlag?: boolean;
  peRemark?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusStudent/createStudent', params);
}

export function updateStudentApi(params: {
  id: IdLike;
  name: string;
  studentNo: string;
  collegeId: IdLike;
  campusCode: string;
  pyStr?: string;
  wbStr?: string;
  genderEnum?: string;
  age?: number;
  birthday?: string;
  idCard?: string;
  phone?: string;
  major?: string;
  majorCode?: string;
  eduLevelEnum?: string;
  enrollTime?: string;
  leaveTime?: string;
  grade?: string;
  className?: string;
  studyYears?: number;
  schoolYear?: number;
  studyTypeEnum?: string;
  schoolStatusEnum?: string;
  physicalExamResultEnum?: string;
  nativePlace?: string;
  counselorName?: string;
  counselorPhone?: string;
  address?: string;
  doorplate?: string;
  activeFlag?: boolean;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusStudent/updateStudent', params);
}

export function getStudentDetailApi(params: { id: IdLike }) {
  return publicRequestClient.post<StudentDetailDto>('/campusStudent/getStudentDetail', params);
}

export function disableStudentApi(params: { id: IdLike }) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusStudent/disableStudent', params);
}

export function transferStudentApi(params: {
  studentId: IdLike;
  toCollegeId: IdLike;
  toCampusCode: string;
  effectiveDate?: string;
  reason?: string;
  externalRef?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusStudent/transferStudent', params);
}

export function pageStudentTransfersApi(params: {
  studentId: IdLike;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<CampusPagedResult<ArchiveTransferDto>>(
    '/campusStudent/pageStudentTransfers',
    params,
  );
}

export function pageStudentChangesApi(params: {
  studentId: IdLike;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<CampusPagedResult<ArchiveChangeLogDto>>(
    '/campusStudent/pageStudentChanges',
    params,
  );
}

export function saveStudentPeProfileApi(params: {
  studentId: IdLike;
  cardNo?: string;
  medicalCode?: string;
  gfStatusEnum?: string;
  categoryEnum?: string;
  eligibleFlag?: boolean;
  remark?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusStudent/saveStudentPeProfile', params);
}

export function bindStudentPatientApi(params: { studentId: IdLike; patientId: IdLike }) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusStudent/bindStudentPatient', params);
}
