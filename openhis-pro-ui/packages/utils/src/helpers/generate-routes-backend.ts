import type { RouteRecordRaw } from 'vue-router';

import type {
  ComponentRecordType,
  GenerateMenuAndRoutesOptions,
  RouteRecordStringComponent,
} from '@vben-core/typings';

/**
 * 动态生成路由 - 后端方式
 */
async function generateRoutesByBackend(
  options: GenerateMenuAndRoutesOptions,
): Promise<RouteRecordRaw[]> {
  const { fetchMenuListAsync, layoutMap = {}, pageMap = {} } = options;

  try {
    const menuRoutes = await fetchMenuListAsync?.();
    if (!menuRoutes) {
      return [];
    }

    const normalizePageMap: ComponentRecordType = {};

    for (const [key, value] of Object.entries(pageMap)) {
      normalizePageMap[normalizeViewPath(key)] = value;
    }

    const routes = convertRoutes(menuRoutes, layoutMap, normalizePageMap);

    return routes;
  } catch (error) {
    console.error(error);
    throw error;
  }
}

function convertRoutes(
  routes: RouteRecordStringComponent[],
  layoutMap: ComponentRecordType,
  pageMap: ComponentRecordType,
  parentNames: string[] = [],
): RouteRecordRaw[] {
  return routes.map((node) => {
    const route = node as unknown as RouteRecordRaw;
    const { component, name, children } = node;

    // 如果路由没有 name，尝试从 path 生成一个，或者使用默认名称
    // 确保 name 是字符串类型
    let routeName: string = typeof name === 'string' ? name : String(name || '');
    if (!routeName || routeName === '') {
      const pathParts = (route.path || '').split('/').filter(Boolean);
      routeName =
        pathParts.length > 0
          ? pathParts.map((part: string) => part.charAt(0).toUpperCase() + part.slice(1)).join('')
          : 'UnnamedRoute';
    }

    // 检查是否与任何祖先路由名称冲突
    // 需要循环检查，直到找到一个不冲突的名称
    let finalRouteName = routeName;
    let conflictCount = 0;
    while (parentNames.includes(finalRouteName)) {
      conflictCount++;
      // 如果子路由名称与祖先路由相同，使用路径作为后缀来区分
      const pathSuffix =
        route.path?.split('/').pop() || route.path?.replaceAll('/', '_') || 'child';
      // 如果已经重命名过一次，添加数字后缀
      finalRouteName =
        conflictCount > 1
          ? `${routeName}_${pathSuffix}_${conflictCount}`
          : `${routeName}_${pathSuffix}`;
    }

    // 设置路由名称
    routeName = finalRouteName;
    (route as any).name = routeName;

    // 构建当前路由的祖先名称列表（包含当前路由名称）
    const currentParentNames: string[] = [...parentNames, routeName];

    // layout转换
    if (component && layoutMap[component]) {
      route.component = layoutMap[component];
      // 页面组件转换
    } else if (component) {
      const normalizePath = normalizeViewPath(component);
      const pageKey = normalizePath.endsWith('.vue') ? normalizePath : `${normalizePath}.vue`;
      if (pageMap[pageKey]) {
        route.component = pageMap[pageKey];
      } else {
        // 尝试查找 not-found 组件，如果不存在则使用 undefined
        const notFoundKey = '/_core/fallback/not-found.vue';
        if (pageMap[notFoundKey]) {
          route.component = pageMap[notFoundKey];
        } else {
          console.error(
            `Route component is invalid and not-found page is also missing: ${pageKey}`,
            route,
          );
          // 如果连 not-found 都找不到，设置为 undefined，让路由系统处理
          route.component = undefined;
        }
      }
    }

    // 递归处理子路由，传递当前路由的祖先名称列表
    if (children && Array.isArray(children) && children.length > 0) {
      route.children = convertRoutes(children, layoutMap, pageMap, currentParentNames);
    }

    return route;
  });
}

function normalizeViewPath(path: string): string {
  // 去除相对路径前缀
  const normalizedPath = path.replace(/^(\.\/|\.\.\/)+/, '');

  // 确保路径以 '/' 开头
  const viewPath = normalizedPath.startsWith('/') ? normalizedPath : `/${normalizedPath}`;

  // 这里耦合了vben-admin的目录结构
  return viewPath.replace(/^\/views/, '');
}
export { generateRoutesByBackend };
