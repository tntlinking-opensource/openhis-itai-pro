/**
 * 该文件可自行根据业务逻辑进行调整
 */
import type { RequestClientOptions } from '@vben/request';

import type { ApiResponse } from '#/types/api-response';

import { useAppConfig } from '@vben/hooks';
import { preferences } from '@vben/preferences';
import {
  authenticateResponseInterceptor,
  errorMessageResponseInterceptor,
  RequestClient,
} from '@vben/request';
import { useAccessStore } from '@vben/stores';

import { ElMessage } from 'element-plus';

import { useAuthStore } from '#/store';

import type { AuthApi } from '#/api/core/auth';

import { isTenantScopeLostError, restoreLoginScopeOnce } from '#/utils/auth/restore-login-scope';
import { formatInsuranceRegisterError } from '#/utils/http/format-insurance-register-error';
import { formatStaleCrossRoleError } from '#/utils/http/stale-cross-role-error';
import { showPersistentUserError } from '#/utils/http/show-persistent-user-message';

import { refreshTokenApi } from './core';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);

function createRequestClient(baseURL: string, options?: RequestClientOptions) {
  const client = new RequestClient({
    ...options,
    baseURL,
  });

  /**
   * 重新认证逻辑
   */
  async function doReAuthenticate() {
    console.warn('Access token or refresh token is invalid or expired. ');
    const accessStore = useAccessStore();
    const authStore = useAuthStore();
    accessStore.setAccessToken(null);
    if (preferences.app.loginExpiredMode === 'modal' && accessStore.isAccessChecked) {
      accessStore.setLoginExpired(true);
    } else {
      await authStore.logout();
    }
  }

  /** 刷新 token：携带 refreshToken，同步更新 access / refresh */
  async function doRefreshToken() {
    const accessStore = useAccessStore();
    const currentRefreshToken = accessStore.refreshToken;
    if (!currentRefreshToken) {
      throw new Error('Refresh token is missing');
    }
    const resp = await refreshTokenApi(String(currentRefreshToken));
    const payload = (resp as ApiResponse<AuthApi.LoginResult>).data ?? resp;
    const loginResult = payload as AuthApi.LoginResult;
    const accessToken = loginResult.accessToken;
    if (!accessToken) {
      throw new Error('Refresh token response missing accessToken');
    }
    accessStore.setAccessToken(accessToken);
    if (loginResult.refreshToken) {
      accessStore.setRefreshToken(loginResult.refreshToken);
    }
    try {
      const { requireLoginScopeReady } = await import('#/utils/auth/require-login-scope-ready');
      await requireLoginScopeReady();
    } catch {
      // 本地未缓存租户/科室时由业务请求拦截器再恢复
    }
    return accessToken;
  }

  function formatToken(token: null | string) {
    return token ? `Bearer ${token}` : null;
  }

  // 请求头处理
  client.addRequestInterceptor({
    fulfilled: async (config) => {
      const accessStore = useAccessStore();

      config.headers.Authorization = formatToken(accessStore.accessToken);
      config.headers['Accept-Language'] = preferences.app.locale;

      // 租户由服务端会话（selectTenant）注入，不再由前端请求头控制

      return config;
    },
  });

  // 处理返回的响应数据格式
  client.addResponseInterceptor({
    fulfilled: (response) => {
      const { config, data: responseData, status } = response;

      if (config.responseReturn === 'raw') {
        return response;
      }

      // HTTP 2xx/4xx/5xx 均尝试解析 Result.message（后端校验失败多为 HTTP 400）
      if (status >= 200 && status < 600) {
        const code = responseData?.code;
        const businessSuccess =
          code === 200 || (code === undefined && responseData?.success !== false && status < 400);
        if (businessSuccess) {
          return config.responseReturn === 'body' ? responseData : responseData?.data;
        }
        const errorMessage = responseData?.message || responseData?.error || '请求失败';
        throw Object.assign(new Error(errorMessage), {
          response,
          code,
          errorCode: responseData?.errorCode,
          message: errorMessage,
        });
      }

      throw Object.assign(new Error('请求失败'), { response });
    },
  });

  // 后端重启后会话租户丢失：自动恢复 selectTenant/医院/科室 并重试一次
  client.addResponseInterceptor({
    rejected: async (error) => {
      const config = error?.config as
        | (typeof error.config & { __isScopeRetryRequest?: boolean })
        | undefined;
      if (!config || config.__isScopeRetryRequest || !isTenantScopeLostError(error)) {
        throw error;
      }
      await restoreLoginScopeOnce();
      config.__isScopeRetryRequest = true;
      return client.request(config.url, { ...config });
    },
  });

  // 业务「没有权限」也走 HTTP 401，勿当成 token 失效触发登出
  client.addResponseInterceptor({
    rejected: async (error) => {
      const response = error?.response as
        | { data?: { message?: string }; status?: number }
        | undefined;
      const message = String(response?.data?.message ?? error?.message ?? '');
      if (response?.status === 401 && message.includes('没有权限')) {
        response.status = 403;
      }
      throw error;
    },
  });

  // token过期的处理
  client.addResponseInterceptor(
    authenticateResponseInterceptor({
      client,
      doReAuthenticate,
      doRefreshToken,
      enableRefreshToken: preferences.app.enableRefreshToken,
      formatToken,
    }),
  );

  // 通用的错误处理,如果没有进入上面的错误处理逻辑，就会进入这里
  client.addResponseInterceptor(
    errorMessageResponseInterceptor((msg: string, error) => {
      // 这里可以根据业务进行定制,你可以拿到 error 内的信息进行定制化处理，根据不同的 code 做不同的提示，而不是直接使用 message.error 提示 msg
      // 当前mock接口返回的错误字段是 error 或者 message
      const responseData = error?.response?.data ?? error?.data ?? {};
      const errorMessage = responseData?.message ?? responseData?.error ?? error?.message ?? '';
      const errorCode = responseData?.errorCode ?? error?.errorCode;
      const displayMessage = errorMessage || msg;

      if (errorCode === 'MODULE_NOT_AVAILABLE') {
        ElMessage.warning(displayMessage);
        return;
      }

      const registerInsuranceError = formatInsuranceRegisterError(
        typeof errorCode === 'string' ? errorCode : undefined,
        displayMessage,
      );
      if (registerInsuranceError) {
        void showPersistentUserError(registerInsuranceError);
        return;
      }

      const staleCrossRoleError = formatStaleCrossRoleError(
        typeof errorCode === 'string' ? errorCode : undefined,
        displayMessage,
      );
      if (staleCrossRoleError) {
        ElMessage.warning(staleCrossRoleError);
        return;
      }

      // 如果没有错误信息，则会根据状态码进行提示
      ElMessage.error(displayMessage);
    }),
  );

  return client;
}

export const loginRequestClient = createRequestClient(apiURL, {
  responseReturn: 'body',
});

export const requestClient = createRequestClient(apiURL, {
  responseReturn: 'body',
});

export const publicRequestClient = createRequestClient(apiURL, {
  responseReturn: 'data',
});

export const baseRequestClient = new RequestClient({ baseURL: apiURL });
