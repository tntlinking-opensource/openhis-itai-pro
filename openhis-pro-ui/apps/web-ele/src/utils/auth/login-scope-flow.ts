import type { AuthApi } from '#/api/core/auth';

import {
  bootstrapLoginScopeApi,
  getAvailableOrganizationUnitsApi,
  selectHospitalApi,
  selectTenantApi,
} from '#/api/core/auth';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';

export interface LoginScopeHospital {
  code?: string;
  description?: string;
  id: number | string;
  logo?: string;
  name: string;
}

export interface LoginScopeOrganization {
  code?: string;
  description?: string;
  id: number | string;
  logo?: string;
  name: string;
}

export function transformLoginHospitalData(
  apiData: AuthApi.LoginOrganizationOption[],
): LoginScopeHospital[] {
  return apiData.map((item) => ({
    id: item.id,
    name: item.displayName || item.name || '',
    code: item.code || '',
    description: undefined,
    logo: undefined,
  }));
}

export function transformLoginOrganizationData(
  apiData: AuthApi.OrganizationInfo[],
): LoginScopeOrganization[] {
  return apiData.map((item) => ({
    id: item.id,
    name: item.displayName || '',
    code: item.code || '',
    description: item.remark || undefined,
    logo: undefined,
  }));
}

/** 已选租户但无可登录科室：将以医院级进入（科室非必选） */
export const LOGIN_NO_DEPARTMENT_MESSAGE = '当前无可登录科室，将以医院级进入系统（科室非必选）';

/** 平台管理员初始化：当前租户尚无医院/科室，将跳过选科进入系统 */
export const LOGIN_PLATFORM_ADMIN_BOOTSTRAP_MESSAGE =
  '当前租户尚未配置医院或科室，正在进入系统进行初始化…';

/** 已选医院但该医院下无可登录科室：医院级进入 */
export const LOGIN_NO_DEPARTMENT_FOR_HOSPITAL_MESSAGE =
  '当前医院下无可登录科室，将以医院级进入系统（科室非必选）';

export const LOGIN_DEPARTMENT_LOAD_FAILED_MESSAGE = '获取可登录科室失败，请稍后重试';

/** 无可登录科室时进入系统的提示（通用，非仅 admin） */
export const LOGIN_HOSPITAL_LEVEL_ENTER_MESSAGE = '无可登录科室，正在以医院级进入系统…';

export type LoginDepartmentStepOutcome =
  | { department: LoginScopeOrganization; kind: 'auto' }
  | { departments: LoginScopeOrganization[]; kind: 'pick' }
  | { kind: 'empty' }
  | { kind: 'failed'; message?: string };

/** 选租户后一次拉齐的登录范围结果 */
export interface LoginScopeAfterTenant {
  departments: LoginScopeOrganization[];
  hospitals: LoginScopeHospital[];
  selectedHospitalId?: number | string;
}

function resolveLoginDepartmentErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  const message = (error as { message?: string })?.message;
  return message || LOGIN_DEPARTMENT_LOAD_FAILED_MESSAGE;
}

/** 由科室列表解析自动进入 / 多选 / 无科室 */
export function resolveDepartmentOutcomeFromList(
  departments: LoginScopeOrganization[],
): LoginDepartmentStepOutcome {
  if (departments.length === 1) {
    return { kind: 'auto', department: departments[0]! };
  }
  if (departments.length === 0) {
    return { kind: 'empty' };
  }
  return { kind: 'pick', departments };
}

/**
 * 选租户后一次拉齐医院 +（单院时）科室，避免串行 3 次 HTTP。
 */
export async function resolveLoginScopeAfterTenant(
  tenantId: number | string,
): Promise<LoginScopeAfterTenant> {
  await selectTenantApi({ tenantId });
  const res = await bootstrapLoginScopeApi();
  // requestClient 常见返回 { data: T } 或直接 T
  const scope = ((res as { data?: AuthApi.LoginScopeBootstrap })?.data ??
    res) as AuthApi.LoginScopeBootstrap;
  const hospitals = transformLoginHospitalData(
    unwrapApiList<AuthApi.LoginOrganizationOption>(scope?.hospitals),
  );
  const departments = transformLoginOrganizationData(
    unwrapApiList<AuthApi.OrganizationInfo>(scope?.departments),
  ).filter((item) => !!item.id);
  return {
    hospitals,
    departments,
    selectedHospitalId: scope?.selectedHospitalId ?? undefined,
  };
}

/** 兼容旧调用：仅返回医院；单院时服务端已写入会话 */
export async function resolveHospitalsAfterTenant(tenantId: number | string) {
  const scope = await resolveLoginScopeAfterTenant(tenantId);
  return scope.hospitals;
}

/** 拉可登录科室（须先 selectTenant；多医院时须先 selectHospital）；仅 1 家时返回 autoSelect */
export async function loadLoginDepartments() {
  const departments = transformLoginOrganizationData(
    unwrapApiList<AuthApi.OrganizationInfo>(await getAvailableOrganizationUnitsApi()),
  ).filter((item) => !!item.id);
  return {
    departments,
    autoSelect: departments.length === 1 ? departments[0]! : null,
  };
}

/** 登录选科室步骤：解析自动进入 / 多选 / 无科室 / 接口失败 */
export async function resolveLoginDepartmentStep(): Promise<LoginDepartmentStepOutcome> {
  try {
    const { autoSelect, departments } = await loadLoginDepartments();
    if (autoSelect) {
      return { kind: 'auto', department: autoSelect };
    }
    if (departments.length === 0) {
      return { kind: 'empty' };
    }
    return { kind: 'pick', departments };
  } catch (error) {
    return { kind: 'failed', message: resolveLoginDepartmentErrorMessage(error) };
  }
}

/** 用户手选医院后写入会话并拉科室 */
export async function resolveDepartmentsAfterHospital(hospitalId: number | string) {
  await selectHospitalApi({ hospitalId });
  return resolveLoginDepartmentStep();
}
