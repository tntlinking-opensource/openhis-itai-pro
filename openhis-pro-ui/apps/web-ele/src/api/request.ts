/**
 * 该文件可自行根据业务逻辑进行调整
 */
import type { RequestClientOptions } from '@vben/request';

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

  /**
   * 刷新token逻辑
   */
  async function doRefreshToken() {
    const accessStore = useAccessStore();
    const resp = await refreshTokenApi();
    const newToken = resp.data;
    accessStore.setAccessToken(newToken);
    return newToken;
  }

  function formatToken(token: null | string) {
    return token ? `Bearer ${token}` : null;
  }

  // 请求头处理
  client.addRequestInterceptor({
    fulfilled: async (config) => {
      const accessStore = useAccessStore();
      const authStore = useAuthStore();

      config.headers.Authorization = formatToken(accessStore.accessToken);
      config.headers['Accept-Language'] = preferences.app.locale;

      // 添加租户ID到请求头
      const tenantId = authStore.getSelectedTenant();
      if (tenantId) {
        config.headers['X-Tenant-ID'] = String(tenantId);
      }

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

      // 检查 HTTP 状态码
      if (status >= 200 && status < 400) {
        // 检查业务状态码
        const code = responseData?.code;
        if (code === 200) {
          // code 为 200 时返回数据
          return config.responseReturn === 'body' ? responseData : responseData?.data;
        } else {
          // code 不为 200 时抛出错误，让错误拦截器处理
          const errorMessage = responseData?.message || responseData?.error || '请求失败';
          throw Object.assign(new Error(errorMessage), {
            response,
            code,
            message: errorMessage,
          });
        }
      }

      throw Object.assign({}, response, { response });
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
      const responseData = error?.response?.data ?? {};
      const errorMessage = responseData?.error ?? responseData?.message ?? '';
      // 如果没有错误信息，则会根据状态码进行提示
      ElMessage.error(errorMessage || msg);
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
