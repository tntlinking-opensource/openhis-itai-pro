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
  /** 工作科室（护士站 / 医技科室，由角色决定候选） */
  WORK_ORG: 'WORK_ORG',
  NURSE_WARD_LOCATION: 'NURSE_WARD_LOCATION',
} as const;

export type StaffScopeCode = (typeof STAFF_SCOPE)[keyof typeof STAFF_SCOPE];

export const STAFF_SCOPE_OPTIONS: { label: string; value: StaffScopeCode }[] = [
  { label: '出诊科室', value: STAFF_SCOPE.DOCTOR_VISIT_ORG },
  { label: '管理库房', value: STAFF_SCOPE.WAREHOUSE_LOCATION },
  { label: '管理药房', value: STAFF_SCOPE.PHARMACY_LOCATION },
  { label: '工作科室', value: STAFF_SCOPE.WORK_ORG },
  { label: '管理病区', value: STAFF_SCOPE.NURSE_WARD_LOCATION },
];

/**
 * 工作科室机构分类 sortOrder。
 * 责任科室树 `classEnum` 出的是该数字（{@code OrganizationClassification}），不是中文名。
 */
export const WORK_DEPT_CLASS_SORT = {
  /** 护士站 */
  NS: '6',
  /** 医技科室 */
  TECH: '10',
} as const;

/**
 * 按已选平台角色 code 汇总工作科室允许的机构分类。
 * 只有护士 → 护士站；只有技师 → 医技科室；双角色 → 两类并集。
 */
export function workDeptClassSortsForRoleCodes(roleCodes: string[]): string[] {
  const sorts = new Set<string>();
  for (const raw of roleCodes) {
    const code = raw.trim().toUpperCase();
    if (code === 'ROLE_NURSE') {
      sorts.add(WORK_DEPT_CLASS_SORT.NS);
    }
    if (code === 'ROLE_MEDICAL_TECH') {
      sorts.add(WORK_DEPT_CLASS_SORT.TECH);
    }
  }
  return [...sorts];
}

/** 内置业务角色默认「角色管理能力」；财务无人员维护块 */
export const BUILTIN_ROLE_STAFF_SCOPES: Record<string, StaffScopeCode[]> = {
  ROLE_DOCTOR: [STAFF_SCOPE.DOCTOR_VISIT_ORG],
  ROLE_NURSE: [STAFF_SCOPE.NURSE_WARD_LOCATION, STAFF_SCOPE.WORK_ORG],
  ROLE_MEDICAL_TECH: [STAFF_SCOPE.WORK_ORG],
  ROLE_PHARMACIST: [STAFF_SCOPE.PHARMACY_LOCATION],
  ROLE_WAREHOUSE: [STAFF_SCOPE.WAREHOUSE_LOCATION, STAFF_SCOPE.PHARMACY_LOCATION],
  ROLE_ACCOUNTANT: [],
  /** 款员无人员维护块（与财务相同，只挂科室） */
  ROLE_CASHIER: [],
  /** 病案管理员无人员维护块（与财务审批类似） */
  ROLE_MRMS_ADMIN: [],
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

export interface StaffRoleScopesDto {
  staffScopes?: StaffScopeCode[];
}

/** 当前操作者可维护的岗位人员信息块 */
export async function getEditableScopesForCurrentUserApi() {
  return requestClient.post<StaffEditableScopesDto>('/position/getEditableScopesForCurrentUser');
}

/** 按所选角色汇总人员维护块 StaffScope（含自定义角色声明） */
export async function resolveStaffScopesByRoleIdsApi(roleIds: string[]) {
  const res = await requestClient.post<StaffRoleScopesDto | { data?: StaffRoleScopesDto }>(
    '/position/resolveStaffScopesByRoleIds',
    {
      roleIds: roleIds.map((id) => preserveApiIdForRequest(id)).filter((id): id is string => !!id),
    },
  );
  // requestClient 为 responseReturn:'body'，须从 Result.data 取业务体
  const payload = (res as { data?: StaffRoleScopesDto }).data ?? (res as StaffRoleScopesDto);
  const scopes = payload?.staffScopes;
  return Array.isArray(scopes) ? scopes : [];
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
