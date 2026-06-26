import type { Router } from 'vue-router';

import { useAccessStore } from '@vben/stores';

import { getMenuTreeByLoginUserApi } from '#/api/core/system/menu';
import { bootstrapImpersonationMeta } from '#/composables/use-impersonation';
import { getAccessRoutes } from '#/router/routes';
import { useAuthStore } from '#/store';
import { convertMenuData } from '#/utils/auth/convert-menu-data';
import { refreshEinvoiceCompensationMenuBadge } from '#/composables/use-einvoice-compensation-badge';
import { requireLoginScopeReady } from '#/utils/auth/require-login-scope-ready';

import { generateAccess } from './access';
import {
  BOOTSTRAP_PREFETCH_TIMEOUT_MS,
  AccessInitTimeoutError,
  withAccessInitTimeout,
} from './access-init-guard';

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
      await requireLoginScopeReady();
      const menuRequestStartAt = Date.now();
      const menus = await getMenuTreeByLoginUserApi();
      let menuData = Array.isArray(menus) ? menus : [];
      menuData = convertMenuData(menuData);
      console.warn('[access-init] menu loaded', {
        count: menuData.length,
        elapsedMs: Date.now() - menuRequestStartAt,
      });

      const accessRoutes = await getAccessRoutes();
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
      void import('#/utils/print/runtime/hiprint-client').then(({ warmupHiprintClient }) => {
        warmupHiprintClient();
      });
      void refreshEinvoiceCompensationMenuBadge();
      // 触发从 localStorage 恢复租户列表（供布局等读取）
      authStore.getTenantList();
    })().finally(() => {
      accessInitPromise = null;
    });
  }

  await accessInitPromise;
}

/**
 * 应用启动时预注册动态路由，避免首次点击需接口的菜单时才阻塞导航。
 * 仅用于 mount 之后的后台任务，带短超时。
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
  const bootstrapReady = authStore.isOrganizationUnitSkipped();
  if (tenantList.length > 0 && (!selectedTenant || (!selectedOrganization && !bootstrapReady))) {
    return;
  }

  try {
    await withAccessInitTimeout(
      (async () => {
        await bootstrapImpersonationMeta();
        await ensureAccessRoutes(router);
      })(),
      BOOTSTRAP_PREFETCH_TIMEOUT_MS,
      resetAccessInit,
    );
  } catch (error) {
    if (error instanceof AccessInitTimeoutError) {
      console.warn('[access-init] prefetch timeout, skip until navigation retry');
      return;
    }
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

export { ensureAccessRoutes, prefetchAccessRoutes, resetAccessInit, resetAndRefreshAccessRoutes };
