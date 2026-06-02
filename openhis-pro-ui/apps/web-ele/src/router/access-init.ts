import type { Router } from 'vue-router';

import { useAccessStore } from '@vben/stores';

import { getMenuTreeApi } from '#/api';
import { accessRoutes } from '#/router/routes';
import { useAuthStore } from '#/store';
import { convertMenuData } from '#/utils/convert-menu-data';

import { generateAccess } from './access';

/** 首次导航命中兜底 404 时的路由名 */
const NOT_FOUND_ROUTE_NAMES = new Set(['FallbackNotFound', 'NotFound']);

let accessInitPromise: null | Promise<void> = null;

/** 退出登录或会话失效时调用，避免悬挂中的菜单初始化阻塞后续导航 */
function resetAccessInit() {
  accessInitPromise = null;
}

/**
 * 拉取菜单并注册动态路由（串行化，避免并发重复初始化）。
 */
async function ensureAccessRoutes(router: Router) {
  const accessStore = useAccessStore();
  if (accessStore.isAccessChecked) {
    return;
  }

  if (!accessInitPromise) {
    accessInitPromise = (async () => {
      const authStore = useAuthStore();
      const menuRequestStartAt = Date.now();
      const menus = await getMenuTreeApi();
      let menuData = Array.isArray(menus) ? menus : [];
      menuData = convertMenuData(menuData);
      console.warn('[access-init] menu loaded', {
        count: menuData.length,
        elapsedMs: Date.now() - menuRequestStartAt,
      });

      const { accessibleMenus, accessibleRoutes } = await generateAccess({
        menuData,
        roles: [],
        router,
        routes: accessRoutes,
      });
      console.warn('[access-init] routes initialized', {
        menus: accessibleMenus.length,
        routes: accessibleRoutes.length,
      });

      accessStore.setAccessMenus(accessibleMenus);
      accessStore.setAccessRoutes(accessibleRoutes);
      accessStore.setIsAccessChecked(true);
      authStore.getTenantList();
    })().finally(() => {
      accessInitPromise = null;
    });
  }

  await accessInitPromise;
}

/**
 * 应用启动时预注册动态路由，避免首次点击需接口的菜单时才阻塞导航。
 */
async function prefetchAccessRoutes(router: Router) {
  const accessStore = useAccessStore();
  const authStore = useAuthStore();

  if (!accessStore.accessToken) {
    return;
  }

  const tenantList = authStore.getTenantList();
  const selectedTenant = authStore.getSelectedTenant();
  const selectedOrganization = authStore.getSelectedOrganization();
  if (tenantList.length > 0 && (!selectedTenant || !selectedOrganization)) {
    return;
  }

  try {
    await ensureAccessRoutes(router);
  } catch (error) {
    console.error('[access-init] prefetch failed', error);
  }
}

/**
 * 菜单变更后重新拉取并注册路由（如系统管理里新增/编辑菜单）。
 */
async function resetAndRefreshAccessRoutes(router: Router) {
  const accessStore = useAccessStore();
  accessStore.setIsAccessChecked(false);
  await ensureAccessRoutes(router);
}

function isNotFoundRouteName(name: unknown): boolean {
  return typeof name === 'string' && NOT_FOUND_ROUTE_NAMES.has(name);
}

export {
  ensureAccessRoutes,
  isNotFoundRouteName,
  prefetchAccessRoutes,
  resetAccessInit,
  resetAndRefreshAccessRoutes,
};
