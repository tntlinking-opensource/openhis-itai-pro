import type { RouteLocationPathRaw, Router } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';
import { startProgress, stopProgress } from '@vben/utils';

import { DEFAULT_HOME_PATH, resolvePostLoginPath } from '#/constants/app';
import { coreRouteNames } from '#/router/routes';
import { useAuthStore } from '#/store';

import { isAuthFailureError, isTransientRequestError } from '#/utils/transient-request-error';

import { ensureAccessRoutes, isNotFoundRouteName } from './access-init';
import { LoginScopeNotReadyError, requireLoginScopeReady } from '#/utils/require-login-scope-ready';

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
    const authStore = useAuthStore();

    // 优先检查：明确声明忽略权限访问权限，则可以访问（适用于任何路由）
    if (to.meta.ignoreAccess) {
      return true;
    }

    // 基本路由，这些路由不需要进入权限拦截
    if (coreRouteNames.includes(to.name as string)) {
      if (to.path === LOGIN_PATH && accessStore.accessToken) {
        try {
          await requireLoginScopeReady();
          const rawTarget = resolvePostLoginPath(to.query?.redirect as string | undefined);
          return { ...pickPathQueryHash(router.resolve(rawTarget)), replace: true };
        } catch (error) {
          if (!(error instanceof LoginScopeNotReadyError)) {
            console.error('[router-guard] login scope check failed', error);
          }
          authStore.setSelectedOrganization('');
          accessStore.setIsAccessChecked(false);
          return true;
        }
      }
      return true;
    }

    // accessToken 检查
    if (!accessStore.accessToken) {
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

    // 动态路由已注册即放行；登录上下文在 ensureAccessRoutes 首次初始化时已校验
    if (accessStore.isAccessChecked) {
      return true;
    }

    try {
      await ensureAccessRoutes(router);
    } catch (error) {
      if (error instanceof LoginScopeNotReadyError) {
        authStore.setSelectedOrganization('');
        accessStore.setIsAccessChecked(false);
        return { path: LOGIN_PATH, replace: true };
      }
      if (isTransientRequestError(error) || !isAuthFailureError(error)) {
        console.warn('[router-guard] access-init failed, keep session for retry', error);
        accessStore.setIsAccessChecked(false);
        return true;
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
