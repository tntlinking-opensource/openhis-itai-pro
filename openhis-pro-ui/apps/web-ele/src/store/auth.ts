import type { Recordable } from '@vben/types';

import type { AuthApi } from '#/api';

import { ref } from 'vue';

import { LOGIN_PATH } from '@vben/constants';
import { useAccessStore, useUserStore } from '@vben/stores';

import { defineStore } from 'pinia';

import { stopProgress } from '@vben/utils';

import { getTenantsApi, getUserInfoApi, loginApi, logoutApi } from '#/api';
import { caLoginApi } from '#/api/core/ca-auth';
import type { CaAuthApi } from '#/api/core/ca-auth';
import {
  beginForceLogoutSession,
  registerEmrUnsavedGuard,
} from '#/emrEditor/use-emr-unsaved-guard';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import { clearPersistedSessionStorage, hardRedirectToLogin } from '#/utils/session-logout';

const LOGOUT_API_TIMEOUT_MS = 3000;

let logoutTask: Promise<void> | null = null;

function notifyServerLogout() {
  void Promise.race([
    logoutApi(),
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
  const selectedOrganizationId = ref<string>('');
  const tenantList = ref<any[]>([]);
  const userInfo = ref<AuthApi.LoginUser | null>(null);

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
        const tenantsResult = await getTenantsApi();
        tenants = unwrapApiList(tenantsResult);

        if (tenants.length > 0) {
          setTenantList(tenants);
        }

        setUserInfo(user);

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

  function setSelectedOrganization(organizationId: string) {
    selectedOrganizationId.value = organizationId;
    if (organizationId) {
      localStorage.setItem('selectedOrganizationId', String(organizationId));
    } else {
      localStorage.removeItem('selectedOrganizationId');
    }
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

        accessStore.setAccessToken(null);
        accessStore.setRefreshToken(null);
        accessStore.setAccessCodes([]);
        accessStore.setIsAccessChecked(false);
        accessStore.setLoginExpired(false);
        accessStore.unlockScreen();

        setSelectedTenant('');
        setSelectedOrganization('');
        setTenantList([]);
        setUserInfo(null);

        clearPersistedSessionStorage();

        accessStore.setAccessToken(null);
        accessStore.setRefreshToken(null);
        accessStore.setIsAccessChecked(false);
        accessStore.setLoginExpired(false);

        stopProgress();
        notifyServerLogout();
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

  /**
   * CA证书登录
   * @param params CA登录参数（challengeId、nonce、signature、certificate、algorithm）
   * @param onSuccess 登录成功回调
   */
  async function caLogin(
    params: CaAuthApi.CaLoginParams,
    onSuccess?: (result: { tenants: any[]; userInfo: any }) => Promise<void> | void,
  ) {
    let tenants: any[] = [];
    try {
      loginLoading.value = true;
      const res = await caLoginApi(params);
      const responseData = res.data;
      const { accessToken, refreshToken } = responseData;

      if (accessToken) {
        accessStore.setAccessToken(accessToken);
        accessStore.setRefreshToken(refreshToken);

        try {
          const tenantsResult = await getTenantsApi();
          tenants = unwrapApiList(tenantsResult);
          if (tenants.length > 0) {
            setTenantList(tenants);
          }
        } catch {
          // CA登录可能还未选择租户，忽略租户获取失败
        }

        if (accessStore.loginExpired) {
          accessStore.setLoginExpired(false);
        } else if (onSuccess) {
          await onSuccess({ tenants, userInfo: responseData });
        }
      }
    } finally {
      loginLoading.value = false;
    }
  }

  function $reset() {
    loginLoading.value = false;
  }

  return {
    $reset,
    authLogin,
    caLogin,
    fetchUserInfo,
    getSelectedOrganization,
    getSelectedTenant,
    getTenantList,
    loginLoading,
    logout,
    selectedOrganizationId,
    selectedTenantId,
    setSelectedOrganization,
    setSelectedTenant,
    setTenantList,
    tenantList,
  };
});
