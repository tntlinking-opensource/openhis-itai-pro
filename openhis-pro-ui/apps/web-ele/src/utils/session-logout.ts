import { LOGIN_PATH } from '@vben/constants';

const AUTH_LOCAL_KEYS = [
  'userInfo',
  'tenantList',
  'selectedTenantId',
  'selectedOrganizationId',
] as const;

/** 与 main.ts initStores 的 namespace 规则一致 */
export function getAppStoreNamespace() {
  const env = import.meta.env.PROD ? 'prod' : 'dev';
  const appVersion = import.meta.env.VITE_APP_VERSION;
  return `${import.meta.env.VITE_APP_NAMESPACE}-${appVersion}-${env}`;
}

/** 清除 Pinia 持久化与业务侧写入的登录相关 localStorage */
export function clearPersistedSessionStorage() {
  const namespace = getAppStoreNamespace();
  const prefix = `${namespace}-`;
  const keysToRemove: string[] = [];

  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (!key) continue;
    if (key.startsWith(prefix) || (AUTH_LOCAL_KEYS as readonly string[]).includes(key)) {
      keysToRemove.push(key);
    }
  }

  keysToRemove.forEach((key) => localStorage.removeItem(key));
}

/** 不依赖 vue-router 模块，避免 logout 时动态 import router 触发循环依赖挂起 */
export function buildLoginPageHref(redirectPath?: string) {
  const base = import.meta.env.VITE_BASE || '/';
  const origin = window.location.origin;
  const normalizedBase = base.endsWith('/') ? base : `${base}/`;
  const query =
    redirectPath && redirectPath !== LOGIN_PATH
      ? `?redirect=${encodeURIComponent(redirectPath)}`
      : '';

  if (import.meta.env.VITE_ROUTER_HISTORY === 'hash') {
    return `${origin}${normalizedBase}#${LOGIN_PATH}${query}`;
  }

  const pathPrefix = normalizedBase === '/' ? '' : normalizedBase.replace(/\/$/, '');
  return `${origin}${pathPrefix}${LOGIN_PATH}${query}`;
}

export function hardRedirectToLogin(redirectPath?: string) {
  window.location.replace(buildLoginPageHref(redirectPath));
}
