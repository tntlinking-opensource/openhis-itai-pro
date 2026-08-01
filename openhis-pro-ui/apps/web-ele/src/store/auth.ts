import type { Recordable } from '@vben/types';

import type { AuthApi } from '#/api/core/auth';

import { ref } from 'vue';

import { LOGIN_PATH } from '@vben/constants';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { defineStore } from 'pinia';

import { stopProgress } from '@vben/utils';

import { getTenantsApi, loginApi, logoutApi } from '#/api/core/auth';
import { getUserInfoApi } from '#/api/core/user';
import {
  beginForceLogoutSession,
  endForceLogoutSession,
  registerEmrUnsavedGuard,
} from '#/emrEditor/use-emr-unsaved-guard';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import { canSkipLoginDepartmentSelection } from '#/utils/auth/platform-admin';
import {
  clearPersistedSessionStorage,
  clearTabbarPersistedState,
  hardRedirectToLogin,
} from '#/utils/auth/session-logout';

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
  /** 登录所选医院展示名（打印抬头等）；与 selectedHospitalId 同步落盘 */
  const selectedHospitalName = ref<string>('');
  const selectedOrganizationId = ref<string>('');
  const tenantList = ref<any[]>([]);
  const userInfo = ref<AuthApi.LoginUser | null>(null);
  /** 当前用户是否为平台管理员（登录 configuration 写入） */
  const platformAdmin = ref(false);
  /** 当前用户是否具备财务审批登录角色（applicationConfiguration.canFinanceApprove） */
  const canFinanceApprove = ref(false);
  /** 无可登录科室时是否已跳过选科（医院级会话） */
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
        void import('#/utils/auth/require-login-scope-ready').then(({ resetLoginScopeSync }) =>
          resetLoginScopeSync(),
        );
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
        syncCanFinanceApproveFromConfiguration(configuration);

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

  function setSelectedHospital(hospitalId: string, hospitalName?: string) {
    selectedHospitalId.value = hospitalId;
    if (hospitalId) {
      localStorage.setItem('selectedHospitalId', String(hospitalId));
      if (hospitalName !== undefined) {
        const name = String(hospitalName ?? '').trim();
        selectedHospitalName.value = name;
        if (name) {
          localStorage.setItem('selectedHospitalName', name);
        } else {
          localStorage.removeItem('selectedHospitalName');
        }
      }
    } else {
      localStorage.removeItem('selectedHospitalId');
      selectedHospitalName.value = '';
      localStorage.removeItem('selectedHospitalName');
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

  /** 登录所选医院名称（打印/抬头用） */
  function getSelectedHospitalName(): string {
    if (selectedHospitalName.value.trim()) {
      return selectedHospitalName.value.trim();
    }
    const stored = localStorage.getItem('selectedHospitalName');
    if (stored) {
      const name = stored.trim();
      selectedHospitalName.value = name;
      return name;
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

  function setCanFinanceApprove(value: boolean) {
    canFinanceApprove.value = value;
    // 清理历史 localStorage 键，避免刷新后误读上一用户的审批权限
    localStorage.removeItem('canFinanceApprove');
  }

  function getCanFinanceApprove(): boolean {
    return canFinanceApprove.value;
  }

  /** 从 applicationConfiguration 同步财务审批能力（登录 / 路由初始化时调用） */
  function syncCanFinanceApproveFromConfiguration(
    configuration: AuthApi.LoginConfiguration | null | undefined,
  ) {
    setCanFinanceApprove(configuration?.currentUser?.canFinanceApprove === true);
  }

  /**
   * 科室/租户会话就绪后拉取 applicationConfiguration，刷新权限码与 canFinanceApprove。
   * 登录初次的 configuration 往往在选科之前，须以选科后会话为准；代理切换后亦依赖此刷新。
   */
  async function refreshApplicationConfiguration(): Promise<void> {
    try {
      const { getApplicationConfigurationApi } = await import('#/api/core/auth');
      const configuration = await getApplicationConfigurationApi();
      if (configuration?.auth?.grantedPolicies) {
        const permissions = configuration.auth.grantedPolicies;
        accessStore.setAccessCodes(Object.keys(permissions).filter((key) => permissions[key]));
      }
      setPlatformAdmin(
        canSkipLoginDepartmentSelection({
          roles: configuration?.currentUser?.roles,
          username: configuration?.currentUser?.userName,
        }),
      );
      syncCanFinanceApproveFromConfiguration(configuration);
      const { syncImpersonationMetaFromConfiguration } = await import(
        '#/composables/use-impersonation'
      );
      syncImpersonationMetaFromConfiguration(configuration);
    } catch (error) {
      console.warn('[auth] refreshApplicationConfiguration failed', error);
    }
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

    logoutTask = Promise.resolve()
      .then(() => {
        const redirectPath =
          redirect && window.location.pathname !== LOGIN_PATH
            ? `${window.location.pathname}${window.location.search}`
            : undefined;

        try {
          beginForceLogoutSession();
          registerEmrUnsavedGuard(null);
          void import('#/router/access-init').then(({ resetAccessInit }) => resetAccessInit());
          void import('#/utils/auth/require-login-scope-ready').then(({ resetLoginScopeSync }) =>
            resetLoginScopeSync(),
          );

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
          setCanFinanceApprove(false);
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
      })
      .finally(() => {
        logoutTask = null;
        endForceLogoutSession();
      });

    return logoutTask;
  }

  async function fetchUserInfo() {
    try {
      // 优先走 applicationConfiguration（后端无 /user/info）
      const { getApplicationConfigurationApi } = await import('#/api/core/auth');
      const configuration = await getApplicationConfigurationApi();
      const currentUser = configuration?.currentUser;
      if (currentUser) {
        const prev = userStore.userInfo;
        const userInfo = {
          ...(prev ?? {}),
          id: currentUser.id ?? prev?.id,
          userId: String(currentUser.id ?? prev?.id ?? ''),
          userName: currentUser.userName || prev?.userName || '',
          username: currentUser.userName || prev?.username || '',
          name: currentUser.name ?? prev?.name ?? null,
          realName: currentUser.name || prev?.realName || currentUser.userName || '',
          email: currentUser.email || prev?.email || '',
          phoneNumber: currentUser.phoneNumber ?? prev?.phoneNumber ?? null,
          avatar: prev?.avatar || '',
          roles: currentUser.roles || [],
          practitionerId: currentUser.practitionerId ?? prev?.practitionerId,
          organizationId: currentUser.organizationId ?? prev?.organizationId,
        };
        userStore.setUserInfo(userInfo as any);
        if (currentUser.roles?.length) {
          userStore.setUserRoles(currentUser.roles);
        }
        syncCanFinanceApproveFromConfiguration(configuration);
        return userInfo as any;
      }
      return null;
    } catch (error) {
      console.error('Failed to fetch user info:', error);
      // 兼容旧实现：若配置接口失败再尝试 /user/info
      try {
        const response = await getUserInfoApi();
        const userData = (response as any).data || response;
        if (userData) {
          const userInfo: AuthApi.LoginUser = {
            ...userData,
            userId: String(userData.userId || userData.id || ''),
            username: userData.userName || userData.username || '',
            realName: userData.nickName || userData.realName || userData.name || '',
            avatar: userData.avatar || '',
            roles: userData.roles?.map((role: any) => role.roleKey || role.roleName || role) || [],
          } as any;
          userStore.setUserInfo(userInfo as any);
          return userInfo;
        }
      } catch (fallbackError) {
        console.error('Failed to fetch user info fallback:', fallbackError);
      }
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
    getSelectedHospitalName,
    getPlatformAdmin,
    getCanFinanceApprove,
    getSelectedOrganization,
    getSelectedTenant,
    getTenantList,
    isOrganizationUnitSkipped,
    canFinanceApprove,
    loginLoading,
    logout,
    platformAdmin,
    organizationUnitSkipped,
    selectedHospitalId,
    selectedHospitalName,
    selectedOrganizationId,
    selectedTenantId,
    setPlatformAdmin,
    setCanFinanceApprove,
    syncCanFinanceApproveFromConfiguration,
    refreshApplicationConfiguration,
    setOrganizationUnitSkipped,
    setSelectedHospital,
    setSelectedOrganization,
    setSelectedTenant,
    setTenantList,
    tenantList,
  };
});
