import type { Recordable } from '@vben/types';

import type { AuthApi } from '#/api';

import { ref } from 'vue';

import { LOGIN_PATH } from '@vben/constants';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { defineStore } from 'pinia';

import { stopProgress } from '@vben/utils';

import { getTenantsApi, getUserInfoApi, loginApi, logoutApi } from '#/api';
import {
  beginForceLogoutSession,
  registerEmrUnsavedGuard,
} from '#/emrEditor/use-emr-unsaved-guard';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import { canSkipLoginDepartmentSelection } from '#/utils/platform-admin';
import {
  clearPersistedSessionStorage,
  clearTabbarPersistedState,
  hardRedirectToLogin,
} from '#/utils/session-logout';

const LOGOUT_API_TIMEOUT_MS = 3000;

let logoutTask: Promise<void> | null = null;

function notifyServerLogout(refreshToken?: null | string) {
  void Promise.race([
    logoutApi(refreshToken ? String(refreshToken) : undefined),
    new Promise<never>((_, reject) => {
      setTimeout(() => reject(new Error('logout api timeout')), LOGOUT_API_TIMEOUT_MS);
    }),
  ]).catch(() => {
    // 后端不可达或超时时忽略，本地会话已清理
  });
}

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();

  const loginLoading = ref(false);
  const selectedTenantId = ref<string>('');
  const selectedHospitalId = ref<string>('');
  const selectedOrganizationId = ref<string>('');
  const tenantList = ref<any[]>([]);
  const userInfo = ref<AuthApi.LoginUser | null>(null);
  /** 当前用户是否为平台管理员（登录 configuration 写入） */
  const platformAdmin = ref(false);
  /** 平台管理员在无科室时是否已跳过选科 */
  const organizationUnitSkipped = ref(false);

  const setTenantList = (tenants: any[]) => {
    tenantList.value = tenants;
    if (tenants.length > 0) {
      localStorage.setItem('tenantList', JSON.stringify(tenants));
    } else {
      localStorage.removeItem('tenantList');
    }
  };

  const setUserInfo = (user: AuthApi.LoginUser | null) => {
    userInfo.value = user;
    if (userInfo.value) {
      localStorage.setItem('userInfo', JSON.stringify(userInfo));
    } else {
      localStorage.removeItem('userInfo');
    }
  };

  /**
   * 异步处理登录操作
   * Asynchronously handle the login process
   * @param params 登录表单数据
   * @param onSuccess 登录成功回调，接收租户列表作为参数
   */
  async function authLogin(
    params: Recordable<any>,
    onSuccess?: (result: {
      configuration: AuthApi.LoginConfiguration | null;
      tenants: any[];
      userInfo: AuthApi.LoginUser | null;
    }) => Promise<void> | void,
  ) {
    let tenants: any[] = [];
    try {
      loginLoading.value = true;
      const res = await loginApi(params);
      const responseData = res.data;
      const { accessToken, refreshToken, user, configuration } = responseData;

      if (accessToken) {
        accessStore.setAccessToken(accessToken);
        accessStore.setRefreshToken(refreshToken);
        clearTabbarPersistedState();
        setSelectedTenant('');
        setSelectedOrganization('');
        setSelectedHospital('');
        setOrganizationUnitSkipped(false);
        accessStore.setIsAccessChecked(false);
        const tenantsResult = await getTenantsApi();
        tenants = unwrapApiList(tenantsResult);

        if (tenants.length > 0) {
          setTenantList(tenants);
        }

        setUserInfo(user);
        setPlatformAdmin(
          canSkipLoginDepartmentSelection({
            roles: configuration?.currentUser?.roles,
            username: user?.userName ?? configuration?.currentUser?.userName,
          }),
        );

        if (accessStore.loginExpired) {
          accessStore.setLoginExpired(false);
        } else {
          if (tenants.length > 0 && onSuccess) {
            await onSuccess({ tenants, userInfo: user, configuration });
          } else if (onSuccess) {
            await onSuccess({ tenants, userInfo: user, configuration });
          }
        }
      }
    } finally {
      loginLoading.value = false;
    }
  }

  function setSelectedTenant(tenantId: string) {
    selectedTenantId.value = tenantId;
    if (tenantId) {
      localStorage.setItem('selectedTenantId', String(tenantId));
    } else {
      localStorage.removeItem('selectedTenantId');
    }
  }

  function getSelectedTenant(): string {
    if (selectedTenantId.value) {
      return selectedTenantId.value;
    }
    const stored = localStorage.getItem('selectedTenantId');
    if (stored) {
      selectedTenantId.value = stored;
      return selectedTenantId.value;
    }
    return '';
  }

  function getTenantList(): any[] {
    if (tenantList.value.length > 0) {
      return tenantList.value;
    }
    const stored = localStorage.getItem('tenantList');
    if (stored) {
      try {
        tenantList.value = JSON.parse(stored);
        return tenantList.value;
      } catch {
        return [];
      }
    }
    return [];
  }

  function setSelectedHospital(hospitalId: string) {
    selectedHospitalId.value = hospitalId;
    if (hospitalId) {
      localStorage.setItem('selectedHospitalId', String(hospitalId));
    } else {
      localStorage.removeItem('selectedHospitalId');
    }
  }

  function getSelectedHospital(): string {
    if (selectedHospitalId.value) {
      return selectedHospitalId.value;
    }
    const stored = localStorage.getItem('selectedHospitalId');
    if (stored) {
      selectedHospitalId.value = stored;
      return selectedHospitalId.value;
    }
    return '';
  }

  function setSelectedOrganization(organizationId: string) {
    selectedOrganizationId.value = organizationId;
    if (organizationId) {
      localStorage.setItem('selectedOrganizationId', String(organizationId));
      setOrganizationUnitSkipped(false);
    } else {
      localStorage.removeItem('selectedOrganizationId');
    }
  }

  function setPlatformAdmin(value: boolean) {
    platformAdmin.value = value;
    if (value) {
      localStorage.setItem('platformAdmin', '1');
    } else {
      localStorage.removeItem('platformAdmin');
    }
  }

  function getPlatformAdmin(): boolean {
    if (platformAdmin.value) {
      return true;
    }
    return localStorage.getItem('platformAdmin') === '1';
  }

  function setOrganizationUnitSkipped(skipped: boolean) {
    organizationUnitSkipped.value = skipped;
    if (skipped) {
      localStorage.setItem('organizationUnitSkipped', '1');
    } else {
      localStorage.removeItem('organizationUnitSkipped');
    }
  }

  function isOrganizationUnitSkipped(): boolean {
    if (organizationUnitSkipped.value) {
      return true;
    }
    return localStorage.getItem('organizationUnitSkipped') === '1';
  }

  function getSelectedOrganization(): string {
    if (selectedOrganizationId.value) {
      return selectedOrganizationId.value;
    }
    const stored = localStorage.getItem('selectedOrganizationId');
    if (stored) {
      selectedOrganizationId.value = stored;
      return selectedOrganizationId.value;
    }
    return '';
  }

  function logout(redirect: boolean = true) {
    if (logoutTask) {
      return logoutTask;
    }

    logoutTask = Promise.resolve().then(() => {
      const redirectPath =
        redirect && window.location.pathname !== LOGIN_PATH
          ? `${window.location.pathname}${window.location.search}`
          : undefined;

      try {
        beginForceLogoutSession();
        registerEmrUnsavedGuard(null);

        const refreshToken = accessStore.refreshToken;

        accessStore.setAccessToken(null);
        accessStore.setRefreshToken(null);
        accessStore.setAccessCodes([]);
        accessStore.setIsAccessChecked(false);
        accessStore.setLoginExpired(false);
        accessStore.unlockScreen();

        setSelectedTenant('');
        setSelectedHospital('');
        setSelectedOrganization('');
        setPlatformAdmin(false);
        setOrganizationUnitSkipped(false);
        setTenantList([]);
        setUserInfo(null);

        clearTabbarPersistedState();
        accessStore.setAccessMenus([]);
        accessStore.setAccessRoutes([]);
        resetAllStores();
        clearPersistedSessionStorage();

        accessStore.setAccessToken(null);
        accessStore.setRefreshToken(null);
        accessStore.setIsAccessChecked(false);
        accessStore.setLoginExpired(false);

        stopProgress();
        notifyServerLogout(refreshToken);
      } catch (error) {
        console.error('[auth] logout cleanup failed', error);
      } finally {
        hardRedirectToLogin(redirectPath);
      }
    });

    return logoutTask;
  }

  async function fetchUserInfo() {
    try {
      const response = await getUserInfoApi();
      const userData = (response as any).data || response;
      if (userData) {
        const userInfo: AuthApi.LoginUser = {
          userId: String(userData.userId || userData.id || ''),
          username: userData.userName || userData.username || '',
          realName: userData.nickName || userData.realName || '',
          avatar: userData.avatar || '',
          roles: userData.roles?.map((role: any) => role.roleKey || role.roleName || role) || [],
          ...userData,
        };
        userStore.setUserInfo(userInfo);
        return userInfo;
      }
      return null;
    } catch (error) {
      console.error('Failed to fetch user info:', error);
      return null;
    }
  }

  function $reset() {
    loginLoading.value = false;
  }

  return {
    $reset,
    authLogin,
    fetchUserInfo,
    getSelectedHospital,
    getPlatformAdmin,
    getSelectedOrganization,
    getSelectedTenant,
    getTenantList,
    isOrganizationUnitSkipped,
    loginLoading,
    logout,
    platformAdmin,
    organizationUnitSkipped,
    selectedHospitalId,
    selectedOrganizationId,
    selectedTenantId,
    setPlatformAdmin,
    setOrganizationUnitSkipped,
    setSelectedHospital,
    setSelectedOrganization,
    setSelectedTenant,
    setTenantList,
    tenantList,
  };
});
