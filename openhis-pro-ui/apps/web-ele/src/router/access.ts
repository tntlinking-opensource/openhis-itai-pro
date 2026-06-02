import type { ComponentRecordType, GenerateMenuAndRoutesOptions } from '@vben/types';

import { generateAccessible } from '@vben/access';
import { preferences } from '@vben/preferences';

import { BasicLayout, IFrameView } from '#/layouts';
import { accessRoutes } from '#/router/routes';

const forbiddenComponent = () => import('#/views/_core/fallback/forbidden.vue');

async function generateAccess(
  options: GenerateMenuAndRoutesOptions & {
    menuData?: any[];
  },
) {
  const pageMap: ComponentRecordType = {
    ...import.meta.glob('../views/**/*.vue'),
    ...import.meta.glob('../chs/**/*.vue'),
  };

  const layoutMap: ComponentRecordType = {
    BasicLayout,
    IFrameView,
    // ParentView 用于显示子菜单但不渲染自己的页面
    ParentView: BasicLayout,
  };

  const { menuData, ...restOptions } = options;

  const staticRoutes = accessRoutes;

  if (
    (preferences.app.accessMode === 'backend' || preferences.app.accessMode === 'mixed') &&
    staticRoutes.length > 0
  ) {
    const { router } = restOptions;
    const root = router.getRoutes().find((item) => item.path === '/');
    const rootName = root?.name;

    staticRoutes.forEach((route) => {
      const routeName = route.name;
      const routeExists = routeName ? router.hasRoute(routeName) : false;

      if (rootName && !route.meta?.noBasicLayout) {
        if (routeExists) {
          return;
        }
        // 如果包含子路由，移除 component 避免多层 BasicLayout
        const childRoute =
          route.children && route.children.length > 0
            ? (() => {
                const routeCopy = { ...route };
                delete routeCopy.component;
                return routeCopy;
              })()
            : route;
        router.addRoute(rootName, childRoute);
      } else {
        if (routeExists) {
          return;
        }
        router.addRoute(route);
      }
    });
  }

  const result = await generateAccessible(preferences.app.accessMode, {
    ...restOptions,
    fetchMenuListAsync: async () => {
      if (menuData && Array.isArray(menuData) && menuData.length > 0) {
        return menuData;
      }
      return [];
    },
    // 可以指定没有权限跳转403页面
    forbiddenComponent,
    // 如果 route.meta.menuVisibleWithForbidden = true
    layoutMap,
    pageMap,
  });
  return result;
}

export { generateAccess };
