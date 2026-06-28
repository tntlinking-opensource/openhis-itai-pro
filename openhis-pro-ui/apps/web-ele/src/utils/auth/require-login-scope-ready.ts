import type { AuthApi } from '#/api/core/auth';

import { useUserStore } from '@vben/stores';

import {
  getAvailableHospitalsApi,
  getAvailableOrganizationUnitsApi,
  loginByOrgApi,
  selectHospitalApi,
  selectTenantApi,
  skipOrganizationUnitSelectionApi,
} from '#/api/core/auth';
import { useAuthStore } from '#/store';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import {
  LOGIN_NO_DEPARTMENT_FOR_HOSPITAL_MESSAGE,
  LOGIN_NO_DEPARTMENT_MESSAGE,
} from '#/utils/auth/login-scope-flow';
import { canSkipLoginDepartmentSelection } from '#/utils/auth/platform-admin';

/** 登录上下文未就绪（须回登录页完成 租户→医院→科室） */
export class LoginScopeNotReadyError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'LoginScopeNotReadyError';
  }
}

/** 本会话内已完成租户/医院/科室同步，避免路由守卫等重复打满链路 */
let loginScopeSynced = false;
let syncingPromise: null | Promise<void> = null;

/** 租户/医院/科室变更或强制恢复前调用 */
export function resetLoginScopeSync(): void {
  loginScopeSynced = false;
}

/** 平台管理员在无科室时确认跳过选科并写入服务端会话 */
async function confirmPlatformAdminBootstrapSkip(): Promise<void> {
  const authStore = useAuthStore();
  await skipOrganizationUnitSelectionApi();
  authStore.setOrganizationUnitSkipped(true);
  authStore.setSelectedOrganization('');
}

/**
 * 以服务端 getAvailableOrganizationUnits 为准，校验并完成租户/医院/科室会话。
 * 平台管理员在无可登录科室时可跳过选科以进入系统做初始化。
 */
function shouldSkipLoginDepartmentSelection(): boolean {
  const authStore = useAuthStore();
  if (authStore.getPlatformAdmin()) {
    return true;
  }
  const userStore = useUserStore();
  const userInfo = userStore.userInfo as null | {
    roles?: string[];
    userName?: string;
    username?: string;
  };
  return canSkipLoginDepartmentSelection({
    roles: userInfo?.roles,
    username: userInfo?.userName ?? userInfo?.username,
  });
}

async function syncLoginScopeReady(): Promise<void> {
  const authStore = useAuthStore();
  const tenantId = authStore.getSelectedTenant();
  if (!tenantId) {
    throw new LoginScopeNotReadyError('请先选择租户');
  }

  await selectTenantApi({ tenantId });

  const hospitals = unwrapApiList<AuthApi.LoginOrganizationOption>(
    await getAvailableHospitalsApi(),
  );
  let hospitalId = authStore.getSelectedHospital();

  if (hospitals.length > 1) {
    if (!hospitalId) {
      throw new LoginScopeNotReadyError('请先选择医院');
    }
    const allowed = hospitals.some((item) => String(item.id) === String(hospitalId));
    if (!allowed) {
      authStore.setSelectedHospital('');
      throw new LoginScopeNotReadyError('请先选择医院');
    }
    await selectHospitalApi({ hospitalId });
  } else if (hospitals.length === 1) {
    hospitalId = String(hospitals[0]!.id);
    authStore.setSelectedHospital(hospitalId);
    await selectHospitalApi({ hospitalId: hospitals[0]!.id });
  }

  const departments = unwrapApiList<AuthApi.OrganizationInfo>(
    await getAvailableOrganizationUnitsApi(),
  ).filter((item) => item.id !== null && item.id !== undefined && String(item.id).trim() !== '');

  const hasSelectedHospital = hospitals.length > 0 && !!hospitalId;
  if (departments.length === 0) {
    if (shouldSkipLoginDepartmentSelection()) {
      await confirmPlatformAdminBootstrapSkip();
      return;
    }
    authStore.setSelectedOrganization('');
    throw new LoginScopeNotReadyError(
      hasSelectedHospital ? LOGIN_NO_DEPARTMENT_FOR_HOSPITAL_MESSAGE : LOGIN_NO_DEPARTMENT_MESSAGE,
    );
  }

  authStore.setOrganizationUnitSkipped(false);

  const allowedOrgIds = new Set(departments.map((item) => String(item.id)));
  let targetOrgId: string;

  if (departments.length === 1) {
    // 单科室始终以服务端列表为准，避免 localStorage 残留上一用户的科室
    targetOrgId = String(departments[0]!.id);
  } else {
    const storedOrgId = authStore.getSelectedOrganization();
    if (!storedOrgId || !allowedOrgIds.has(String(storedOrgId))) {
      authStore.setSelectedOrganization('');
      throw new LoginScopeNotReadyError('请先选择科室');
    }
    targetOrgId = storedOrgId;
  }

  await loginByOrgApi({ organizationUnitId: targetOrgId });
  authStore.setSelectedOrganization(targetOrgId);
}

export async function requireLoginScopeReady(): Promise<void> {
  if (loginScopeSynced) {
    return;
  }
  if (syncingPromise) {
    return syncingPromise;
  }

  syncingPromise = syncLoginScopeReady()
    .then(() => {
      loginScopeSynced = true;
    })
    .finally(() => {
      syncingPromise = null;
    });

  return syncingPromise;
}
