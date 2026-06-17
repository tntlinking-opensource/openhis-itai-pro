import { LOGIN_PATH } from '@vben/constants';
import { useTabbarStore } from '@vben/stores';

const AUTH_LOCAL_KEYS = [
  'userInfo',
  'tenantList',
  'selectedTenantId',
  'selectedHospitalId',
  'selectedOrganizationId',
  'platformAdmin',
  'organizationUnitSkipped',
] as const;

/** 与 main.ts initStores 的 namespace 规则一致 */
export function getAppStoreNamespace() {
  const env = import.meta.env.PROD ? 'prod' : 'dev';
  const appVersion = import.meta.env.VITE_APP_VERSION;
  return `${import.meta.env.VITE_APP_NAMESPACE}-${appVersion}-${env}`;
}

/** 清除 Pinia 持久化与业务侧写入的登录相关 localStorage / sessionStorage */
export function clearPersistedSessionStorage() {
  const namespace = getAppStoreNamespace();
  const prefix = `${namespace}-`;
  const secureMetaKey = `${namespace}-secure-meta`;

  for (const storage of [localStorage, sessionStorage] as const) {
    const keysToRemove: string[] = [];
    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i);
      if (!key) continue;
      const isAuthLocalKey =
        storage === localStorage && (AUTH_LOCAL_KEYS as readonly string[]).includes(key);
      if (key.startsWith(prefix) || key === secureMetaKey || isAuthLocalKey) {
        keysToRemove.push(key);
      }
    }
    keysToRemove.forEach((key) => storage.removeItem(key));
  }
}

/** 清除页签缓存（core-tabbar 持久化在 sessionStorage，登出/换用户须清） */
export function clearTabbarPersistedState() {
  try {
    const tabbarStore = useTabbarStore();
    tabbarStore.$patch({
      tabs: [],
      cachedTabs: new Set<string>(),
      excludeCachedTabs: new Set<string>(),
      renderRouteView: true,
      updateTime: Date.now(),
    });
  } catch {
    // Pinia 尚未初始化时忽略
  }

  const namespace = getAppStoreNamespace();
  sessionStorage.removeItem(`${namespace}-core-tabbar`);
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

function isAlreadyOnLoginPage(targetHref: string) {
  const { hash, href, pathname } = window.location;
  if (href === targetHref) {
    return true;
  }
  const loginHash = `#${LOGIN_PATH}`;
  return hash === loginHash || hash.startsWith(`${loginHash}?`) || pathname === LOGIN_PATH;
}

export function hardRedirectToLogin(redirectPath?: string) {
  const target = buildLoginPageHref(redirectPath);
  if (isAlreadyOnLoginPage(target)) {
    // hash 路由下若已在登录页，replace 同 URL 不会卸载应用，须强制刷新
    const reloadHref = target + (target.includes('?') ? '&' : '?') + `_logout=${Date.now()}`;
    window.location.replace(reloadHref);
    return;
  }
  window.location.replace(target);
}
