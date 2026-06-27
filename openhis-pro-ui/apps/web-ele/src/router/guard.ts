import type { RouteLocationPathRaw, Router } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences/es/preferences-manager';
import { useAccessStore } from '@vben/stores';
import { startProgress, stopProgress } from '@vben/utils';

import { DEFAULT_HOME_PATH } from '#/constants/app';
import { coreRouteNames } from '#/router/routes';

import {
  AccessInitTimeoutError,
  isNotFoundRouteName,
  ROUTER_ACCESS_INIT_TIMEOUT_MS,
  withAccessInitTimeout,
} from './access-init-guard';

const LOGIN_SCOPE_CHECK_TIMEOUT_MS = 3000;

function isLoginScopeNotReadyError(error: unknown): boolean {
  return error instanceof Error && error.name === 'LoginScopeNotReadyError';
}

/** 菜单初始化超时或后端未就绪时，清理陈旧 token 避免每次进登录页重复拉菜单 */
function clearStaleAccessSession(accessStore: ReturnType<typeof useAccessStore>) {
  accessStore.setAccessToken(null);
  accessStore.setIsAccessChecked(false);
}

/**
 * 从 resolve 结果中只取 path/query/hash，避免展开 RouteLocationResolved 带入 matched 等内部字段。
 */
function pickPathQueryHash(resolved: ReturnType<Router['resolve']>): RouteLocationPathRaw {
  return {
    path: resolved.path,
    query: resolved.query,
    hash: resolved.hash,
  };
}

/**
 * 通用守卫配置
 * @param router
 */
function setupCommonGuard(router: Router) {
  // 记录已经加载的页面
  const loadedPaths = new Set<string>();

  router.beforeEach((to) => {
    to.meta.loaded = loadedPaths.has(to.path);

    // 页面加载进度条
    if (!to.meta.loaded && preferences.transition.progress) {
      startProgress();
    }
    return true;
  });

  router.afterEach((to) => {
    // 记录页面是否加载,如果已经加载，后续的页面切换动画等效果不在重复执行

    loadedPaths.add(to.path);

    // 关闭页面加载进度条
    if (preferences.transition.progress) {
      stopProgress();
    }
  });
}

/**
 * 权限访问守卫配置
 * @param router
 */
function setupAccessGuard(router: Router) {
  router.beforeEach(async (to, from) => {
    const accessStore = useAccessStore();

    if (to.meta.ignoreAccess) {
      return true;
    }

    // 无 token 快速路径：不加载 auth store / 菜单，直接进登录页
    if (!accessStore.accessToken) {
      if (coreRouteNames.includes(to.name as string)) {
        return true;
      }
      if (to.fullPath !== LOGIN_PATH) {
        return {
          path: LOGIN_PATH,
          query:
            to.fullPath === DEFAULT_HOME_PATH ? {} : { redirect: encodeURIComponent(to.fullPath) },
          replace: true,
        };
      }
      return true;
    }

    const { useAuthStore } = await import('#/store/auth');
    const authStore = useAuthStore();

    if (coreRouteNames.includes(to.name as string)) {
      if (to.path === LOGIN_PATH && accessStore.accessToken) {
        try {
          const { requireLoginScopeReady } = await import('#/utils/auth/require-login-scope-ready');
          const { resolvePostLoginPath } = await import('#/constants/app');
          await withAccessInitTimeout(requireLoginScopeReady(), LOGIN_SCOPE_CHECK_TIMEOUT_MS);
          if (!accessStore.isAccessChecked) {
            return true;
          }
          const rawTarget = resolvePostLoginPath(to.query?.redirect as string | undefined);
          return { ...pickPathQueryHash(router.resolve(rawTarget)), replace: true };
        } catch (error) {
          if (!isLoginScopeNotReadyError(error) && !(error instanceof AccessInitTimeoutError)) {
            console.error('[router-guard] login scope check failed', error);
          }
          if (error instanceof AccessInitTimeoutError) {
            clearStaleAccessSession(accessStore);
          }
          authStore.setSelectedOrganization('');
          accessStore.setIsAccessChecked(false);
          return true;
        }
      }
      return true;
    }

    if (accessStore.isAccessChecked) {
      return true;
    }

    try {
      const { ensureAccessRoutes } = await import('./access-init');
      await withAccessInitTimeout(
        ensureAccessRoutes(router),
        ROUTER_ACCESS_INIT_TIMEOUT_MS,
        async () => {
          const { resetAccessInit } = await import('./access-init');
          resetAccessInit();
        },
      );
    } catch (error) {
      const { isAuthFailureError, isTransientRequestError } = await import(
        '#/utils/http/transient-request-error'
      );
      if (isLoginScopeNotReadyError(error)) {
        authStore.setSelectedOrganization('');
        accessStore.setIsAccessChecked(false);
        return { path: LOGIN_PATH, replace: true };
      }
      if (
        error instanceof AccessInitTimeoutError ||
        isTransientRequestError(error) ||
        !isAuthFailureError(error)
      ) {
        console.warn('[router-guard] access-init deferred, redirect to login for retry', error);
        clearStaleAccessSession(accessStore);
        return { path: LOGIN_PATH, replace: true };
      }
      console.error('[router-guard] access-init auth failed, redirect to login', error);
      accessStore.setAccessToken(null);
      return { path: LOGIN_PATH, replace: true };
    }

    // 动态路由注册完成后重新导航：须用 fullPath 重新匹配（首次可能先命中 404 兜底）
    const redirectEncoded = from.query.redirect as string | undefined;
    const redirectTarget = redirectEncoded ? decodeURIComponent(redirectEncoded) : to.fullPath;
    const resolved = router.resolve(redirectTarget);

    if (isNotFoundRouteName(resolved.name)) {
      console.error('[router-guard] route still not found after init', redirectTarget);
    }

    return {
      ...pickPathQueryHash(resolved),
      replace: true,
    };
  });
}

/**
 * 项目守卫配置
 * @param router
 */
function createRouterGuard(router: Router) {
  /** 通用 */
  setupCommonGuard(router);
  /** 权限访问 */
  setupAccessGuard(router);
}

export { createRouterGuard };
