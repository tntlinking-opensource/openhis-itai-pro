import type { AuthApi } from '#/api';

import {
  getAvailableHospitalsApi,
  getAvailableOrganizationUnitsApi,
  selectHospitalApi,
  selectTenantApi,
} from '#/api';
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

/** 选租户后：拉医院列表；仅 1 家时自动 selectHospital */
export async function resolveHospitalsAfterTenant(tenantId: number | string) {
  await selectTenantApi({ tenantId });
  const hospitals = transformLoginHospitalData(
    unwrapApiList<AuthApi.LoginOrganizationOption>(await getAvailableHospitalsApi()),
  );
  if (hospitals.length === 1) {
    await selectHospitalApi({ hospitalId: hospitals[0]!.id });
  }
  return hospitals;
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

/** 已选租户但无可登录科室时的提示文案（非平台管理员） */
export const LOGIN_NO_DEPARTMENT_MESSAGE =
  '已选择租户，但未获取到可登录科室。请维护医务人员信息或联系管理员配置岗位管辖后再试';

/** 平台管理员初始化：当前租户尚无医院/科室，将跳过选科进入系统 */
export const LOGIN_PLATFORM_ADMIN_BOOTSTRAP_MESSAGE =
  '当前租户尚未配置医院或科室，正在以平台管理员身份进入系统进行初始化…';

/** 已选医院但该医院下无可登录科室 */
export const LOGIN_NO_DEPARTMENT_FOR_HOSPITAL_MESSAGE =
  '当前医院下没有可登录的科室，请选择其他医院或联系管理员配置岗位管辖';

export const LOGIN_DEPARTMENT_LOAD_FAILED_MESSAGE = '获取可登录科室失败，请稍后重试';

export type LoginDepartmentStepOutcome =
  | { kind: 'auto'; department: LoginScopeOrganization }
  | { kind: 'empty' }
  | { kind: 'failed'; message?: string }
  | { kind: 'pick'; departments: LoginScopeOrganization[] };

function resolveLoginDepartmentErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  const message = (error as { message?: string })?.message;
  return message || LOGIN_DEPARTMENT_LOAD_FAILED_MESSAGE;
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
