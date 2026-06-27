import type { RoleInfo } from '#/api/user/role';

import { requestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 岗位范围（AssignmentScope，与后端 RoleAssignmentScopeConstants 一致） */
export const ASSIGNMENT_SCOPE = {
  ORG: 'ORG',
  WARD: 'WARD',
  PHARMACY: 'PHARMACY',
  WAREHOUSE: 'WAREHOUSE',
  GLOBAL: 'GLOBAL',
} as const;

export type AssignmentScopeCode = (typeof ASSIGNMENT_SCOPE)[keyof typeof ASSIGNMENT_SCOPE];

export const ASSIGNMENT_SCOPE_OPTIONS: { label: string; value: AssignmentScopeCode }[] = [
  { label: '科室', value: ASSIGNMENT_SCOPE.ORG },
  { label: '病区', value: ASSIGNMENT_SCOPE.WARD },
  { label: '药房', value: ASSIGNMENT_SCOPE.PHARMACY },
  { label: '库房', value: ASSIGNMENT_SCOPE.WAREHOUSE },
  { label: '不限', value: ASSIGNMENT_SCOPE.GLOBAL },
];

/** 人员维护可编辑块（角色管理能力，与 RoleStaffScopeConstants 一致） */
export const STAFF_SCOPE = {
  DOCTOR_VISIT_ORG: 'DOCTOR_VISIT_ORG',
  WAREHOUSE_LOCATION: 'WAREHOUSE_LOCATION',
  PHARMACY_LOCATION: 'PHARMACY_LOCATION',
  NURSE_ORG: 'NURSE_ORG',
  NURSE_WARD_LOCATION: 'NURSE_WARD_LOCATION',
} as const;

export type StaffScopeCode = (typeof STAFF_SCOPE)[keyof typeof STAFF_SCOPE];

export const STAFF_SCOPE_OPTIONS: { label: string; value: StaffScopeCode }[] = [
  { label: '出诊科室', value: STAFF_SCOPE.DOCTOR_VISIT_ORG },
  { label: '管理库房', value: STAFF_SCOPE.WAREHOUSE_LOCATION },
  { label: '管理药房', value: STAFF_SCOPE.PHARMACY_LOCATION },
  { label: '管理病区', value: STAFF_SCOPE.NURSE_WARD_LOCATION },
];

/** 内置业务角色默认「角色管理能力」；财务无人员维护块 */
export const BUILTIN_ROLE_STAFF_SCOPES: Record<string, StaffScopeCode[]> = {
  ROLE_DOCTOR: [STAFF_SCOPE.DOCTOR_VISIT_ORG],
  ROLE_NURSE: [STAFF_SCOPE.NURSE_WARD_LOCATION],
  ROLE_PHARMACIST: [STAFF_SCOPE.PHARMACY_LOCATION],
  ROLE_WAREHOUSE: [STAFF_SCOPE.WAREHOUSE_LOCATION, STAFF_SCOPE.PHARMACY_LOCATION],
  ROLE_ACCOUNTANT: [],
};

export interface StaffOrgLocationItem {
  orgId?: string;
  locationId?: string;
}

export interface StaffEditableScopesDto {
  editableScopes?: StaffScopeCode[];
}

export interface StaffDetailDto {
  userId?: string;
  userName?: string;
  name?: string;
  email?: string;
  phoneNumber?: string;
  isActive?: boolean;
  systemRoles?: RoleInfo[];
  practitionerId?: string;
  practitionerName?: string;
  busNo?: string;
  genderEnum?: string;
  statusEnum?: string;
  ybNo?: string;
  drProfttlCode?: number | string;
  pharPracCertNo?: string;
  primaryOrgId?: string;
  doctorVisitOrgList?: StaffOrgLocationItem[];
  warehouseLocationList?: StaffOrgLocationItem[];
  pharmacyLocationList?: StaffOrgLocationItem[];
  nurseOrgList?: StaffOrgLocationItem[];
  nurseWardLocationList?: StaffOrgLocationItem[];
  doctorServiceIds?: string[];
}

export interface SaveStaffParams {
  userId?: string;
  userName?: string;
  password?: string;
  name?: string;
  email?: string;
  phoneNumber?: string;
  isActive?: boolean;
  roleIds?: string[];
  practitionerId?: string;
  genderEnum?: string;
  busNo?: string;
  ybNo?: string;
  drProfttlCode?: number | string;
  pharPracCertNo?: string;
  extraProperties?: string;
  doctorVisitOrgList?: StaffOrgLocationItem[];
  warehouseLocationList?: StaffOrgLocationItem[];
  pharmacyLocationList?: StaffOrgLocationItem[];
  nurseOrgList?: StaffOrgLocationItem[];
  nurseWardLocationList?: StaffOrgLocationItem[];
  doctorServiceIds?: string[];
  /** 为 true 时更新 adm_practitioner.org_id（含置空） */
  syncPrimaryOrgId?: boolean;
  /** 默认登录科室（adm_practitioner.org_id）；不填则登录时使用当前医院 */
  primaryOrgId?: string;
}

/** 当前操作者可维护的岗位人员信息块 */
export async function getEditableScopesForCurrentUserApi() {
  return requestClient.post<StaffEditableScopesDto>('/position/getEditableScopesForCurrentUser');
}

/** 加载岗位人员维护详情 */
export async function getStaffDetailApi(userId: string) {
  const res = await requestClient.post<{ data?: StaffDetailDto } & StaffDetailDto>(
    '/position/getStaffDetail',
    { userId: preserveApiIdForRequest(userId) },
  );
  return (res as { data?: StaffDetailDto }).data ?? (res as StaffDetailDto);
}

/** 一体保存用户 + 医务人员 + 六类岗位分配 */
export async function saveStaffApi(params: SaveStaffParams) {
  return requestClient.post<StaffDetailDto>('/position/saveStaff', params);
}
