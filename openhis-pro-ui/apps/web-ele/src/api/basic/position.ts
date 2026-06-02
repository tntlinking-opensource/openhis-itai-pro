import type { UserListParams } from '#/api/user/user';
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

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

/** adm_pr_assign 业务角色（与 DM-ADM-006 / 后端 Integer 编码一致） */
export const PRACTITIONER_BUSINESS_ROLE_OPTIONS = [
  { label: '医师', value: 1 },
  { label: '护士', value: 2 },
  { label: '药师', value: 3 },
  { label: '库管', value: 4 },
  { label: '技师', value: 5 },
  { label: '行政', value: 6 },
] as const;

/** 岗位分配行（PractitionerAssignmentRowDto） */
export interface PractitionerAssignmentRow {
  assignmentId?: string;
  practitionerId?: string;
  roleCode?: number;
  roleName?: string;
  orgId?: string;
  orgName?: string;
  locationId?: string;
  locationName?: string;
  name?: string;
  activeFlag?: boolean;
  specialtyCode?: string;
  typeCode?: number | null;
}

export async function listPractitionerAssignmentsApi(practitionerId: string) {
  return requestClient.post<ApiResponse<PractitionerAssignmentRow[]>>(
    '/position/listPractitionerAssignments',
    {
      practitionerId: typeof practitionerId === 'string' ? practitionerId : practitionerId,
    },
  );
}

export interface AssignPractitionerRoleItemPayload {
  practitionerId: string;
  orgId?: number | null;
  locationId?: number | null;
  roleCode: number;
  name?: string;
  specialtyCode?: string;
  typeCode?: number | null;
}

export async function assignPractitionerRoleApi(payload: AssignPractitionerRoleItemPayload) {
  return requestClient.post<ApiResponse<string>>('/position/assignPractitionerRole', {
    item: payload,
  });
}

export interface EditPractitionerRoleItemPayload {
  assignmentId: number;
  name?: string;
  orgId?: number | null;
  locationId?: number | null;
  specialtyCode?: string;
  typeCode?: number | null;
}

export async function editPractitionerRoleApi(payload: EditPractitionerRoleItemPayload) {
  return requestClient.post<ApiResponse<null>>('/position/editPractitionerRole', {
    item: payload,
  });
}

export async function retirePractitionerRoleApi(assignmentId: number, reason?: string) {
  return requestClient.post<ApiResponse<null>>('/position/retirePractitionerRole', {
    item: { assignmentId, reason: reason ?? '' },
  });
}

export async function activatePractitionerRoleApi(assignmentId: number) {
  return requestClient.post<ApiResponse<null>>('/position/activatePractitionerRole', {
    item: { assignmentId },
  });
}

export async function deactivatePractitionerRoleApi(assignmentId: number, reason?: string) {
  return requestClient.post<ApiResponse<null>>('/position/deactivatePractitionerRole', {
    item: { assignmentId, reason: reason ?? '停用' },
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
export async function listLocationOptionsByOrganizationApi(organizationId: string | number) {
  const id = typeof organizationId === 'string' ? Number(organizationId) : organizationId;
  return requestClient.post<ApiResponse<LocationOptionDto[]>>(
    '/position/listLocationOptionsByOrganization',
    { organizationId: id },
  );
}

/**
 * 单条位置选项（用于当前分配引用非启用位置时下拉补全）
 * POST /api/app/position/getLocationOptionById
 */
export async function getLocationOptionByIdApi(locationId: string | number) {
  const id = typeof locationId === 'string' ? Number(locationId) : locationId;
  return requestClient.post<ApiResponse<LocationOptionDto | null>>(
    '/position/getLocationOptionById',
    {
      locationId: id,
    },
  );
}
