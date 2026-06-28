import type { RouteRecordRaw } from 'vue-router';

import { computed, type ComputedRef } from 'vue';
import { useRouter } from 'vue-router';

import { useAccessStore } from '@vben/stores';

import { resolveWorkbenchMenuIcon } from './workbench-menu-icons';
import { WORKBENCH_EXCLUDED_ROUTE_NAMES, WORKBENCH_GROUPS } from './workbench-groups';
import type { WorkbenchGroupView, WorkbenchShortcutItem } from './workbench-types';

interface RouteShortcutMeta {
  icon?: string;
  path: string;
  title: string;
}

const CUSTOM_SHORTCUT_ACCENT = '#0066e6';
const CUSTOM_SHORTCUT_ICON_BG = '#eff6ff';
const CUSTOM_SHORTCUT_ICON_BG_NEXT = '#dbeafe';

/** 将可访问路由元数据转为工作台卡片展示项 */
export function buildWorkbenchShortcutItem(
  routeName: string,
  routeMeta: RouteShortcutMeta,
  groupId: WorkbenchShortcutItem['groupId'] = 'custom',
): WorkbenchShortcutItem {
  return {
    groupId,
    routeName,
    title: routeMeta.title,
    path: routeMeta.path,
    icon: resolveWorkbenchMenuIcon(routeName) || routeMeta.icon,
    accentColor: CUSTOM_SHORTCUT_ACCENT,
    iconBgColor: CUSTOM_SHORTCUT_ICON_BG,
    iconColor: CUSTOM_SHORTCUT_ACCENT,
    iconGradient: `linear-gradient(135deg, ${CUSTOM_SHORTCUT_ICON_BG} 0%, ${CUSTOM_SHORTCUT_ICON_BG_NEXT} 100%)`,
    cardTint: CUSTOM_SHORTCUT_ICON_BG,
  };
}

/** 收集可访问路由 name 集合（含子路由） */
function collectAccessibleRouteNames(routes: RouteRecordRaw[]) {
  const names = new Set<string>();

  const walk = (items: RouteRecordRaw[]) => {
    for (const route of items) {
      if (typeof route.name === 'string') {
        names.add(route.name);
      }
      if (route.children?.length) {
        walk(route.children);
      }
    }
  };

  walk(routes);
  return names;
}

/** 基于 router 解析结果构建快捷入口索引，保证 path 与运行时一致 */
function buildAccessibleRouteMap(
  accessibleNames: Set<string>,
  resolvePath: (routeName: string) => string,
  getRouteMeta: (routeName: string) => RouteRecordRaw | undefined,
) {
  const routeMap = new Map<string, RouteShortcutMeta>();

  for (const routeName of accessibleNames) {
    if (WORKBENCH_EXCLUDED_ROUTE_NAMES.has(routeName)) {
      continue;
    }

    const route = getRouteMeta(routeName);
    if (!route || route.meta?.hideInMenu) {
      continue;
    }
    if (!route.component && route.children?.length) {
      continue;
    }

    routeMap.set(routeName, {
      title: String(route.meta?.title ?? routeName),
      path: resolvePath(routeName),
      icon: typeof route.meta?.icon === 'string' ? route.meta.icon : undefined,
    });
  }

  return routeMap;
}

/** 按分组配置与用户权限解析工作台快捷入口 */
export function useWorkbenchShortcuts(searchKeyword: ComputedRef<string>) {
  const accessStore = useAccessStore();
  const router = useRouter();

  const accessibleRouteMap = computed(() => {
    const accessibleNames =
      accessStore.accessRoutes.length > 0
        ? collectAccessibleRouteNames(accessStore.accessRoutes)
        : new Set(
            router
              .getRoutes()
              .map((route) => route.name)
              .filter((name): name is string => typeof name === 'string'),
          );

    const routeByName = new Map(
      router
        .getRoutes()
        .flatMap((route) => (route.name ? [[String(route.name), route] as const] : [])),
    );

    return buildAccessibleRouteMap(
      accessibleNames,
      (routeName) => router.resolve({ name: routeName }).path,
      (routeName) => routeByName.get(routeName),
    );
  });

  const groupedShortcuts = computed<WorkbenchGroupView[]>(() => {
    const routeMap = accessibleRouteMap.value;

    return WORKBENCH_GROUPS.map((config) => {
      const items: WorkbenchShortcutItem[] = [];

      config.menuCodes.forEach((menuCode, index) => {
        const routeMeta = routeMap.get(menuCode);
        if (!routeMeta) {
          return;
        }
        const paletteIndex = index % config.iconBgColors.length;
        const nextPaletteIndex = (paletteIndex + 1) % config.iconBgColors.length;
        const iconBg = config.iconBgColors[paletteIndex]!;
        const iconBgNext = config.iconBgColors[nextPaletteIndex]!;
        const iconFg = config.iconColors[paletteIndex] ?? config.accentColor;

        items.push({
          groupId: config.id,
          routeName: menuCode,
          title: routeMeta.title,
          path: routeMeta.path,
          icon: resolveWorkbenchMenuIcon(menuCode),
          accentColor: config.accentColor,
          iconBgColor: iconBg,
          iconColor: iconFg,
          iconGradient: `linear-gradient(135deg, ${iconBg} 0%, ${iconBgNext} 100%)`,
          cardTint: iconBg,
        });
      });

      return { config, items };
    }).filter((group) => group.items.length > 0);
  });

  const filteredGroups = computed<WorkbenchGroupView[]>(() => {
    const keyword = searchKeyword.value.trim().toLowerCase();
    if (!keyword) {
      return groupedShortcuts.value;
    }

    return groupedShortcuts.value
      .map((group) => ({
        ...group,
        items: group.items.filter((item) => item.title.toLowerCase().includes(keyword)),
      }))
      .filter((group) => group.items.length > 0);
  });

  const totalShortcutCount = computed(() =>
    groupedShortcuts.value.reduce((sum, group) => sum + group.items.length, 0),
  );

  /** 当前用户可访问的全部快捷入口候选（供自定义区选择） */
  const shortcutCandidates = computed<WorkbenchShortcutItem[]>(() => {
    const routeMap = accessibleRouteMap.value;
    const items: WorkbenchShortcutItem[] = [];

    for (const [routeName, routeMeta] of routeMap.entries()) {
      items.push(buildWorkbenchShortcutItem(routeName, routeMeta));
    }

    return items.sort((left, right) => left.title.localeCompare(right.title, 'zh-CN'));
  });

  return {
    accessibleRouteMap,
    filteredGroups,
    groupedShortcuts,
    shortcutCandidates,
    totalShortcutCount,
  };
}
