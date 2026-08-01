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
/** 递增后丢弃进行中的菜单初始化结果，避免代理切换时旧请求写回上一身份菜单 */
let accessInitGeneration = 0;

/** 退出登录、代理切换或会话失效时调用，避免悬挂中的菜单初始化阻塞后续导航 */
function resetAccessInit() {
  accessInitPromise = null;
  accessInitGeneration += 1;
}

/**
 * 移除上一身份注册到 router 的动态路由，防止代理后残留 admin 路由/菜单。
 */
function removeRegisteredAccessRoutes(router: Router) {
  const accessStore = useAccessStore();
  const previousRoutes = accessStore.accessRoutes ?? [];
  for (const route of previousRoutes) {
    if (route.name && router.hasRoute(route.name)) {
      router.removeRoute(route.name);
    }
  }
  accessStore.setAccessMenus([]);
  accessStore.setAccessRoutes([]);
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
    const generation = accessInitGeneration;
    const initTask = (async () => {
      const authStore = useAuthStore();
      await requireLoginScopeReady();
      if (generation !== accessInitGeneration) {
        return;
      }
      await authStore.refreshApplicationConfiguration();
      if (generation !== accessInitGeneration) {
        return;
      }
      const menuRequestStartAt = Date.now();
      const menus = await getMenuTreeByLoginUserApi();
      if (generation !== accessInitGeneration) {
        return;
      }
      let menuData = Array.isArray(menus) ? menus : [];
      menuData = convertMenuData(menuData);
      if (import.meta.env.DEV) {
        console.debug('[access-init] menu loaded', {
          count: menuData.length,
          elapsedMs: Date.now() - menuRequestStartAt,
        });
      }

      const accessRoutes = await getAccessRoutes();
      const { accessibleMenus, accessibleRoutes } = await generateAccess({
        menuData,
        roles: [],
        router,
        routes: accessRoutes,
      });
      if (generation !== accessInitGeneration) {
        return;
      }
      if (import.meta.env.DEV) {
        console.debug('[access-init] routes initialized', {
          menus: accessibleMenus.length,
          routes: accessibleRoutes.length,
        });
      }

      accessStore.setAccessMenus(accessibleMenus);
      accessStore.setAccessRoutes(accessibleRoutes);
      accessStore.setIsAccessChecked(true);
      void import('#/utils/print/runtime/hiprint-client').then(({ schedulePrintClientWarmup }) => {
        schedulePrintClientWarmup();
      });
      void refreshEinvoiceCompensationMenuBadge();
      // 触发从 localStorage 恢复租户列表（供布局等读取）
      authStore.getTenantList();
    })().finally(() => {
      if (accessInitPromise === initTask) {
        accessInitPromise = null;
      }
    });
    accessInitPromise = initTask;
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
 * 菜单变更或身份切换后重新拉取并注册路由（代理登录、退出代理、菜单管理变更）。
 */
async function resetAndRefreshAccessRoutes(router: Router) {
  const accessStore = useAccessStore();
  resetAccessInit();
  removeRegisteredAccessRoutes(router);
  accessStore.setIsAccessChecked(false);
  await ensureAccessRoutes(router);
}

export { ensureAccessRoutes, prefetchAccessRoutes, resetAccessInit, resetAndRefreshAccessRoutes };
