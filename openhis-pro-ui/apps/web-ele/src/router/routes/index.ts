import type { RouteRecordRaw } from 'vue-router';

import { coreRoutes, fallbackNotFoundRoute } from './core';

const dynamicRouteFileLoaders = import.meta.glob('./modules/**/*.ts');

const staticRoutes: RouteRecordRaw[] = [];
const externalRoutes: RouteRecordRaw[] = [];

let accessRoutesCache: RouteRecordRaw[] | undefined;
let accessRoutesPromise: Promise<RouteRecordRaw[]> | undefined;

/** 懒加载动态路由模块，避免启动时 eager 拉取全部 modules */
async function getAccessRoutes(): Promise<RouteRecordRaw[]> {
  if (accessRoutesCache) {
    return accessRoutesCache;
  }
  if (!accessRoutesPromise) {
    accessRoutesPromise = (async () => {
      const { mergeRouteModules } = await import('@zxplat/utils');
      const modules = await Promise.all(
        Object.values(dynamicRouteFileLoaders).map((load) => load()),
      );
      const dynamicRoutes = mergeRouteModules(
        Object.fromEntries(modules.map((mod, index) => [`module_${index}`, mod])),
      );
      accessRoutesCache = [...dynamicRoutes, ...staticRoutes];
      return accessRoutesCache;
    })();
  }
  return accessRoutesPromise;
}

/** 路由列表，由基本路由、外部路由和404兜底路由组成
 *  无需走权限验证（会一直显示在菜单中） */
const routes: RouteRecordRaw[] = [...coreRoutes, ...externalRoutes, fallbackNotFoundRoute];

/** 基本路由列表，这些路由不需要进入权限拦截 */
function getCoreRouteNames(): string[] {
  const names: string[] = [];
  const walk = (items: RouteRecordRaw[]) => {
    for (const route of items) {
      if (route.name) {
        names.push(route.name as string);
      }
      if (route.children?.length) {
        walk(route.children);
      }
    }
  };
  walk(coreRoutes);
  return names;
}

const coreRouteNames = getCoreRouteNames();

export { coreRouteNames, getAccessRoutes, routes };
