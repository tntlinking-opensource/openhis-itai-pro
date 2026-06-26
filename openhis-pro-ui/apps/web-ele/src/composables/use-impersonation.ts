import type { AuthApi } from '#/api/core/auth';

import { computed, ref } from 'vue';

import { useAccessStore, useUserStore } from '@vben/stores';

import {
  getApplicationConfigurationApi,
  impersonateApi,
  stopImpersonatingApi,
} from '#/api/core/auth';
import { canSkipLoginDepartmentSelection } from '#/utils/auth/platform-admin';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { useAuthStore } from '#/store/auth';

const IMPERSONATOR_BACKUP_KEY = 'whale:impersonator:backup';
const IMPERSONATION_META_KEY = 'whale:impersonation:meta';

interface ImpersonatorBackup {
  accessToken: string;
  refreshToken: string;
  accessCodes: string[];
  selectedTenantId: string;
  selectedHospitalId: string;
  selectedOrganizationId: string;
  platformAdmin: boolean;
  organizationUnitSkipped: boolean;
  userInfo: AuthApi.LoginUser | null;
}

export interface ImpersonationMeta {
  impersonatorUserName: string;
  impersonatedUserName: string;
  startedAt: string;
}

const impersonationMeta = ref<ImpersonationMeta | null>(loadImpersonationMeta());

function loadImpersonationMeta(): ImpersonationMeta | null {
  const raw = sessionStorage.getItem(IMPERSONATION_META_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as ImpersonationMeta;
  } catch {
    return null;
  }
}

function saveImpersonationMeta(meta: ImpersonationMeta | null) {
  impersonationMeta.value = meta;
  if (meta) {
    sessionStorage.setItem(IMPERSONATION_META_KEY, JSON.stringify(meta));
  } else {
    sessionStorage.removeItem(IMPERSONATION_META_KEY);
  }
}

function applyConfiguration(
  configuration: AuthApi.LoginConfiguration | null | undefined,
  user: AuthApi.LoginUser | AuthApi.ImpersonateUser | null | undefined,
) {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const authStore = useAuthStore();

  if (configuration?.auth?.grantedPolicies) {
    const permissions = configuration.auth.grantedPolicies;
    accessStore.setAccessCodes(Object.keys(permissions).filter((key) => permissions[key]));
  }

  if (user) {
    userStore.setUserInfo(user as any);
  }

  authStore.setPlatformAdmin(
    canSkipLoginDepartmentSelection({
      roles: configuration?.currentUser?.roles,
      username: configuration?.currentUser?.userName ?? user?.userName,
    }),
  );
}

function backupAdminSession() {
  const accessStore = useAccessStore();
  const authStore = useAuthStore();
  const userStore = useUserStore();
  const backup: ImpersonatorBackup = {
    accessToken: String(accessStore.accessToken ?? ''),
    refreshToken: String(accessStore.refreshToken ?? ''),
    accessCodes: [...accessStore.accessCodes],
    selectedTenantId: authStore.getSelectedTenant(),
    selectedHospitalId: authStore.getSelectedHospital(),
    selectedOrganizationId: authStore.getSelectedOrganization(),
    platformAdmin: authStore.getPlatformAdmin(),
    organizationUnitSkipped: authStore.isOrganizationUnitSkipped(),
    userInfo: (userStore.userInfo as AuthApi.LoginUser | null) ?? null,
  };
  sessionStorage.setItem(IMPERSONATOR_BACKUP_KEY, JSON.stringify(backup));
}

function restoreAdminSession(): ImpersonatorBackup | null {
  const raw = sessionStorage.getItem(IMPERSONATOR_BACKUP_KEY);
  if (!raw) {
    return null;
  }
  let backup: ImpersonatorBackup;
  try {
    backup = JSON.parse(raw) as ImpersonatorBackup;
  } catch {
    return null;
  }

  const accessStore = useAccessStore();
  const authStore = useAuthStore();
  const userStore = useUserStore();

  accessStore.setAccessToken(backup.accessToken);
  accessStore.setRefreshToken(backup.refreshToken);
  accessStore.setAccessCodes(backup.accessCodes);
  authStore.setSelectedTenant(backup.selectedTenantId);
  authStore.setSelectedHospital(backup.selectedHospitalId);
  authStore.setSelectedOrganization(backup.selectedOrganizationId);
  authStore.setPlatformAdmin(backup.platformAdmin);
  authStore.setOrganizationUnitSkipped(backup.organizationUnitSkipped);
  if (backup.userInfo) {
    userStore.setUserInfo(backup.userInfo as any);
  }

  sessionStorage.removeItem(IMPERSONATOR_BACKUP_KEY);
  return backup;
}

export function useImpersonation() {
  const accessStore = useAccessStore();
  const authStore = useAuthStore();

  const isImpersonating = computed(() => impersonationMeta.value != null);

  async function impersonateUser(
    userId: number | string,
    options?: { reason?: string; tenantId?: null | number | string },
  ) {
    backupAdminSession();

    try {
      const tenantId = options?.tenantId ?? authStore.getSelectedTenant() ?? undefined;
      const res = await impersonateApi({
        userId: preserveApiIdForRequest(userId) ?? userId,
        tenantId: tenantId ? (preserveApiIdForRequest(tenantId) ?? tenantId) : undefined,
        reason: options?.reason,
      });
      const data = res.data;

      accessStore.setAccessToken(data.accessToken);
      accessStore.setRefreshToken(data.refreshToken);
      accessStore.setIsAccessChecked(false);

      const effectiveTenantId =
        data.configuration?.currentTenant?.id ??
        data.configuration?.currentUser?.tenantId ??
        tenantId;
      if (
        effectiveTenantId !== null &&
        effectiveTenantId !== undefined &&
        String(effectiveTenantId).trim() !== ''
      ) {
        authStore.setSelectedTenant(String(effectiveTenantId));
      } else {
        authStore.setSelectedTenant('');
      }
      authStore.setSelectedHospital('');
      authStore.setSelectedOrganization('');
      authStore.setOrganizationUnitSkipped(false);
      void import('#/utils/auth/require-login-scope-ready').then(({ resetLoginScopeSync }) =>
        resetLoginScopeSync(),
      );

      applyConfiguration(data.configuration, data.user);

      saveImpersonationMeta({
        impersonatorUserName: data.impersonator?.userName ?? '未知',
        impersonatedUserName: data.user?.userName ?? '未知',
        startedAt: new Date().toISOString(),
      });

      return data;
    } catch (error) {
      restoreAdminSession();
      saveImpersonationMeta(null);
      throw error;
    }
  }

  async function stopImpersonating() {
    const refreshToken = accessStore.refreshToken;
    if (refreshToken) {
      try {
        await stopImpersonatingApi(String(refreshToken));
      } catch {
        // 本地恢复优先，服务端吊销失败不阻断
      }
    }

    const backup = restoreAdminSession();
    saveImpersonationMeta(null);

    if (backup) {
      try {
        const configRes = await getApplicationConfigurationApi();
        applyConfiguration(configRes.data, backup.userInfo);
      } catch {
        // 已恢复备份权限码
      }
      accessStore.setIsAccessChecked(false);
    }

    return backup != null;
  }

  return {
    impersonationMeta,
    isImpersonating,
    impersonateUser,
    stopImpersonating,
    syncImpersonationMetaFromConfiguration,
  };
}

function syncImpersonationMetaFromConfiguration(configuration?: AuthApi.LoginConfiguration | null) {
  const currentUser = configuration?.currentUser;
  if (currentUser?.impersonatorUserId != null) {
    saveImpersonationMeta({
      impersonatorUserName: currentUser.impersonatorUserName ?? '未知',
      impersonatedUserName: currentUser.userName ?? '未知',
      startedAt: impersonationMeta.value?.startedAt ?? new Date().toISOString(),
    });
  } else if (!sessionStorage.getItem(IMPERSONATOR_BACKUP_KEY)) {
    saveImpersonationMeta(null);
  }
}

/**
 * 应用启动时同步代理态横幅：sessionStorage 元数据丢失但备份 Token 仍在时使用。
 */
export async function bootstrapImpersonationMeta() {
  if (!sessionStorage.getItem(IMPERSONATOR_BACKUP_KEY)) {
    return;
  }
  if (impersonationMeta.value) {
    return;
  }
  try {
    const configRes = await getApplicationConfigurationApi();
    syncImpersonationMetaFromConfiguration(configRes.data);
  } catch {
    // 配置拉取失败时保留无横幅状态，不影响主流程
  }
}
