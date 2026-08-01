import type { RouteRecordRaw } from 'vue-router';

import { computed, type ComputedRef } from 'vue';
import { useRouter } from 'vue-router';

import { useAccessStore } from '@vben/stores';

import { useAuthStore } from '#/store';

import { resolveWorkbenchMenuIcon } from './workbench-menu-icons';
import { WORKBENCH_EXCLUDED_ROUTE_NAMES, WORKBENCH_GROUPS } from './workbench-groups';
import {
  WORKBENCH_STAFF_ALLOWLIST,
  WORKBENCH_STAFF_ROOT_GROUP_ID,
  WORKBENCH_STAFF_ROOT_GROUP_TITLE,
} from './workbench-staff-allowlist';
import type {
  WorkbenchGroupConfig,
  WorkbenchGroupView,
  WorkbenchShortcutItem,
} from './workbench-types';

interface RouteShortcutMeta {
  icon?: string;
  path: string;
  title: string;
}

interface StaffGroupBucket {
  items: WorkbenchShortcutItem[];
  order: number;
  title: string;
}

const CUSTOM_SHORTCUT_ACCENT = '#0066e6';
const CUSTOM_SHORTCUT_ICON_BG = '#eff6ff';
const CUSTOM_SHORTCUT_ICON_BG_NEXT = '#dbeafe';

const STAFF_GROUP_STYLE: Pick<
  WorkbenchGroupConfig,
  'accentColor' | 'accentGradient' | 'icon' | 'iconBgColors' | 'iconColors' | 'subtitle'
> = {
  accentColor: '#0066e6',
  accentGradient: 'linear-gradient(135deg, #3b82f6 0%, #0066e6 100%)',
  icon: 'lucide:layout-grid',
  iconBgColors: ['#eff6ff', '#e8f0fe', '#dbeafe', '#d6e4ff'],
  iconColors: ['#0066e6', '#1d5bb8', '#1e4a9e', '#1e3a7a'],
  subtitle: '',
};

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

/** 判断路由是否可作为可点击页面入口 */
function isNavigableShortcutRoute(route: RouteRecordRaw): boolean {
  if (route.meta?.hideInMenu) {
    return false;
  }
  if (typeof route.name !== 'string' || WORKBENCH_EXCLUDED_ROUTE_NAMES.has(route.name)) {
    return false;
  }
  // 纯布局目录（有子节点且无页面组件）不可单独跳转
  if (!route.component && route.children?.length) {
    return false;
  }
  return true;
}

/** 按调色板下标组装一张快捷卡片 */
function buildStyledShortcutItem(
  groupId: string,
  routeName: string,
  routeMeta: RouteShortcutMeta,
  index: number,
): WorkbenchShortcutItem {
  const paletteIndex = index % STAFF_GROUP_STYLE.iconBgColors.length;
  const nextPaletteIndex = (paletteIndex + 1) % STAFF_GROUP_STYLE.iconBgColors.length;
  const iconBg = STAFF_GROUP_STYLE.iconBgColors[paletteIndex]!;
  const iconBgNext = STAFF_GROUP_STYLE.iconBgColors[nextPaletteIndex]!;
  const iconFg = STAFF_GROUP_STYLE.iconColors[paletteIndex] ?? STAFF_GROUP_STYLE.accentColor;

  return {
    groupId,
    routeName,
    title: routeMeta.title,
    path: routeMeta.path,
    icon: resolveWorkbenchMenuIcon(routeName) || routeMeta.icon,
    accentColor: STAFF_GROUP_STYLE.accentColor,
    iconBgColor: iconBg,
    iconColor: iconFg,
    iconGradient: `linear-gradient(135deg, ${iconBg} 0%, ${iconBgNext} 100%)`,
    cardTint: iconBg,
  };
}

/** 向分组桶追加入口（同 routeName 去重） */
function pushStaffShortcut(
  groups: Map<string, StaffGroupBucket>,
  groupId: string,
  groupTitle: string,
  groupOrder: number,
  routeName: string,
  routeMeta: RouteShortcutMeta,
) {
  let bucket = groups.get(groupId);
  if (!bucket) {
    bucket = { title: groupTitle, order: groupOrder, items: [] };
    groups.set(groupId, bucket);
  }
  // 同一菜单不重复展示
  if (bucket.items.some((item) => item.routeName === routeName)) {
    return;
  }
  bucket.items.push(buildStyledShortcutItem(groupId, routeName, routeMeta, bucket.items.length));
}

/**
 * 非管理员：仅白名单菜单 +「仅父级有权限」的目录页，按侧栏父级分组。
 * 顶级无父级菜单（医生站/护士站等）归入「工作站」。
 */
function buildStaffGroupedShortcuts(
  menuTree: RouteRecordRaw[],
  resolvePath: (routeName: string) => string,
): WorkbenchGroupView[] {
  const groups = new Map<string, StaffGroupBucket>();

  const visit = (
    routes: RouteRecordRaw[],
    parent: null | { id: string; order: number; title: string },
  ) => {
    routes.forEach((route, index) => {
      if (typeof route.name !== 'string') {
        return;
      }

      const routeName = route.name;
      const title = String(route.meta?.title ?? routeName);
      const children = route.children ?? [];
      const order = Number(route.meta?.orderNo ?? index);

      if (children.length > 0) {
        // 子菜单以当前节点为父级分组
        visit(children, { id: routeName, title, order });

        // 父级本身也在白名单且可跳转时一并展示
        if (WORKBENCH_STAFF_ALLOWLIST.has(routeName) && isNavigableShortcutRoute(route)) {
          const target = parent ?? {
            id: WORKBENCH_STAFF_ROOT_GROUP_ID,
            title: WORKBENCH_STAFF_ROOT_GROUP_TITLE,
            order: -1,
          };
          pushStaffShortcut(groups, target.id, target.title, target.order, routeName, {
            title,
            path: resolvePath(routeName),
            icon: typeof route.meta?.icon === 'string' ? route.meta.icon : undefined,
          });
        }
        return;
      }

      const isAllowlisted = WORKBENCH_STAFF_ALLOWLIST.has(routeName);
      const isParentOnlyDirectory = route.meta?.menuType === 'directory';

      // 白名单叶子，或仅有父级目录权限时展示该父级
      if (!isAllowlisted && !isParentOnlyDirectory) {
        return;
      }
      if (!isNavigableShortcutRoute(route)) {
        return;
      }

      const target = parent ?? {
        id: WORKBENCH_STAFF_ROOT_GROUP_ID,
        title: WORKBENCH_STAFF_ROOT_GROUP_TITLE,
        order: -1,
      };

      pushStaffShortcut(groups, target.id, target.title, target.order, routeName, {
        title,
        path: resolvePath(routeName),
        icon: typeof route.meta?.icon === 'string' ? route.meta.icon : undefined,
      });
    });
  };

  visit(menuTree, null);

  return [...groups.entries()]
    .map(([id, bucket]) => ({
      config: {
        ...STAFF_GROUP_STYLE,
        id,
        title: bucket.title,
        menuCodes: bucket.items.map((item) => item.routeName),
      } satisfies WorkbenchGroupConfig,
      items: bucket.items,
      order: bucket.order,
    }))
    .sort((left, right) => {
      if (left.order !== right.order) {
        return left.order - right.order;
      }
      return left.config.title.localeCompare(right.config.title, 'zh-CN');
    })
    .map(({ config, items }) => ({ config, items }));
}

/** 平台管理员：沿用固定角色分组配置 */
function buildAdminGroupedShortcuts(
  routeMap: Map<string, RouteShortcutMeta>,
): WorkbenchGroupView[] {
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
}

/** 按分组配置与用户权限解析工作台快捷入口 */
export function useWorkbenchShortcuts(searchKeyword: ComputedRef<string>) {
  const accessStore = useAccessStore();
  const authStore = useAuthStore();
  const router = useRouter();

  /** 是否平台管理员（角色 admin / ROLE_ADMIN 或 admin 账号） */
  const isPlatformAdmin = computed(() => authStore.platformAdmin || authStore.getPlatformAdmin());

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
    if (isPlatformAdmin.value) {
      return buildAdminGroupedShortcuts(accessibleRouteMap.value);
    }

    const menuTree =
      accessStore.accessRoutes.length > 0
        ? accessStore.accessRoutes
        : (router.options.routes as RouteRecordRaw[]);

    return buildStaffGroupedShortcuts(
      menuTree,
      (routeName) => router.resolve({ name: routeName }).path,
    );
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
    isPlatformAdmin,
    shortcutCandidates,
    totalShortcutCount,
  };
}
