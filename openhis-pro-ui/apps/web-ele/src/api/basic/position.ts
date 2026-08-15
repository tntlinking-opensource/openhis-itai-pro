import type { UserListParams } from '#/api/user/user';
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 岗位工作台左侧用户行（与 PositionUserWorkbenchRowDto 一致） */
export interface PositionUserWorkbenchRow {
  userId?: string;
  userName?: string;
  name?: string | null;
  /** 系统角色展示名，顿号分隔 */
  roleNames?: string;
  isActive?: boolean;
  practitionerId?: string;
  practitionerName?: string;
  busNo?: string;
  practitionerStatusEnum?: string;
}

export interface PagePositionUserWorkbenchResponse {
  content?: PositionUserWorkbenchRow[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}

/** 医务人员工作台详情（右侧详情等场景；与后端 DTO 对齐时可扩展） */
export interface PractitionerWorkbenchDetail {
  id?: number | string;
  name?: string;
  busNo?: string;
  genderEnum?: string;
  phone?: string;
  orgId?: number | string;
  currentOrgName?: string;
  statusEnum?: string;
  userName?: string;
  profileIncomplete?: boolean;
}

/** 岗位工作台列表查询参数（仅启用用户，不传 isActive） */
export type PositionUserWorkbenchListParams = Pick<
  UserListParams,
  'pageNo' | 'pageSize' | 'keyword'
>;

/**
 * 岗位工作台：分页查询用户 + 系统角色 + 医务人员主档（仅已启用用户）
 * POST /api/app/position/pageUsersForWorkbench
 */
export async function pageUsersForWorkbenchApi(
  params: PositionUserWorkbenchListParams,
): Promise<ApiResponse<PagePositionUserWorkbenchResponse>> {
  return requestClient.post<ApiResponse<PagePositionUserWorkbenchResponse>>(
    '/position/pageUsersForWorkbench',
    {
      pageNo: params.pageNo,
      pageSize: params.pageSize,
      keyword: params.keyword,
      isActive: true,
    },
  );
}

/** 内置业务角色 code（仅逻辑判断，不在界面展示） */
export const PLATFORM_ROLE_CODE = {
  DOCTOR: 'ROLE_DOCTOR',
  NURSE: 'ROLE_NURSE',
  PHARMACIST: 'ROLE_PHARMACIST',
  WAREHOUSE: 'ROLE_WAREHOUSE',
  ACCOUNTANT: 'ROLE_ACCOUNTANT',
  FINANCE_APPROVER: 'ROLE_FINANCE_APPROVER',
  /** 病案管理员：门诊/住院「归档病例 / 解档病例」 */
  MRMS_ADMIN: 'ROLE_MRMS_ADMIN',
} as const;

/** 岗位分配可选内置角色（服务端解析 id，勿写死种子 id） */
export interface RoleOptionDto {
  id: string;
  displayName: string;
  builtIn?: boolean;
  /** 服务端 code，仅逻辑判断，不在界面展示 */
  code?: string;
  assignmentScopes?: string[];
}

/** 加载岗位分配可选内置角色 */
export async function loadBusinessBuiltInRoleOptions(): Promise<RoleOptionDto[]> {
  const res = await requestClient.post<RoleOptionDto[]>('/position/listAssignmentRoleOptions', {});
  const raw = res as RoleOptionDto[] | { data?: RoleOptionDto[] };
  const list = Array.isArray(raw) ? raw : raw.data;
  if (!Array.isArray(list)) {
    return [];
  }
  return list.map((item) => ({
    ...item,
    id: String(item.id),
  }));
}

/** legacy roleCode（1/2/3/4/6）→ 展示名（读侧兼容） */
export function legacyRoleCodeToLabel(code?: null | number | string): string {
  if (code === undefined || code === null || code === '') {
    return '';
  }
  const legacyMap: Record<number, string> = {
    1: '医师',
    2: '护士',
    3: '药师',
    4: '库管',
    6: '行政',
  };
  const n = Number(code);
  if (Number.isNaN(n)) {
    return String(code);
  }
  return legacyMap[n] ?? String(code);
}

/** 岗位分配行（PractitionerAssignmentRowDto） */
export interface PractitionerAssignmentRow {
  assignmentId?: string;
  practitionerId?: string;
  /** whale_roles.id */
  roleId?: string;
  /** legacy 1/2/3/4/6，非 whale_roles.id */
  roleCode?: null | number;
  roleName?: string;
  orgId?: string;
  orgName?: string;
  locationId?: string;
  locationName?: string;
  name?: string;
  activeFlag?: boolean;
  specialtyCode?: string;
  typeCode?: number | null;
  /** 岗位可接诊号源类型主键 */
  serviceIds?: string[];
  /** 号源类型名称（顿号分隔） */
  serviceNames?: string;
}

export async function listPractitionerAssignmentsApi(practitionerId: string) {
  return requestClient.post<ApiResponse<PractitionerAssignmentRow[]>>(
    '/position/listPractitionerAssignments',
    {
      practitionerId: preserveApiIdForRequest(practitionerId),
    },
  );
}

export interface AssignPractitionerRoleItemPayload {
  practitionerId: string;
  orgId?: string | null;
  locationId?: string | null;
  /** 推荐：whale_roles.id */
  roleId?: string;
  /** 兼容 legacy 1/2/3/4/6（与 roleId 二选一，后端优先 roleId） */
  roleCode?: number;
  name?: string;
  specialtyCode?: string;
  typeCode?: number | null;
  /** 可接诊号源类型（HealthcareService）主键列表 */
  serviceIds?: string[];
}

export async function assignPractitionerRoleApi(
  payload: AssignPractitionerRoleItemPayload | AssignPractitionerRoleItemPayload[],
) {
  const items = Array.isArray(payload) ? payload : [payload];
  return requestClient.post<ApiResponse<string>>('/position/assignPractitionerRole', {
    item: items[0],
    items,
  });
}

export interface EditPractitionerRoleItemPayload {
  assignmentId: string;
  name?: string;
  orgId?: string | null;
  /** 普通岗位为单个 ID；库房/药房多选时为逗号分隔的多个 ID */
  locationId?: string | null;
  specialtyCode?: string;
  typeCode?: number | null;
  /** 可接诊号源类型（HealthcareService）主键列表；传空数组表示清空 */
  serviceIds?: string[];
}

export async function editPractitionerRoleApi(payload: EditPractitionerRoleItemPayload) {
  return requestClient.post<ApiResponse<null>>('/position/editPractitionerRole', {
    item: payload,
    items: [payload],
  });
}

export async function retirePractitionerRoleApi(assignmentId: string, reason?: string) {
  const item = { assignmentId, reason: reason ?? '' };
  return requestClient.post<ApiResponse<null>>('/position/retirePractitionerRole', {
    item,
    items: [item],
  });
}

export async function activatePractitionerRoleApi(assignmentId: string) {
  const item = { assignmentId };
  return requestClient.post<ApiResponse<null>>('/position/activatePractitionerRole', {
    item,
    items: [item],
  });
}

export async function deactivatePractitionerRoleApi(assignmentId: string, reason?: string) {
  const item = { assignmentId, reason: reason ?? '停用' };
  return requestClient.post<ApiResponse<null>>('/position/deactivatePractitionerRole', {
    item,
    items: [item],
  });
}

/** 与后端 OptionDto 一致 */
export interface LocationOptionDto {
  sort?: number;
  value: string;
  label: string;
}

/**
 * 按科室加载启用中的物理位置下拉（名称 · 形态）
 * POST /api/app/position/listLocationOptionsByOrganization
 */
export async function listLocationOptionsByOrganizationApi(organizationId: string) {
  return requestClient.post<ApiResponse<LocationOptionDto[]>>(
    '/position/listLocationOptionsByOrganization',
    { organizationId },
  );
}

/**
 * 单条位置选项（用于当前分配引用非启用位置时下拉补全）
 * POST /api/app/position/getLocationOptionById
 */
export async function getLocationOptionByIdApi(locationId: string) {
  return requestClient.post<ApiResponse<LocationOptionDto | null>>(
    '/position/getLocationOptionById',
    {
      locationId,
    },
  );
}
