import { computed, ref } from 'vue';

import {
  readWorkbenchCustomShortcuts,
  WORKBENCH_CUSTOM_EMPTY_SLOT_COUNT,
  WORKBENCH_CUSTOM_SHORTCUT_MAX,
  writeWorkbenchCustomShortcuts,
} from './workbench-custom-shortcut-storage';
import type { WorkbenchShortcutItem } from './workbench-types';
import { buildWorkbenchShortcutItem } from './use-workbench-shortcuts';

/** 管理首页自定义快捷入口的读取、展示与本地持久化 */
export function useWorkbenchCustomShortcuts(
  shortcutCandidates: Readonly<{ value: WorkbenchShortcutItem[] }>,
) {
  const savedRouteNames = ref<string[]>(
    readWorkbenchCustomShortcuts().map((item) => item.routeName),
  );

  /** 从 localStorage 同步已保存的路由 name 列表 */
  function reloadFromStorage(): void {
    savedRouteNames.value = readWorkbenchCustomShortcuts().map((item) => item.routeName);
  }

  /** 候选路由索引，便于按 routeName 解析展示项 */
  const candidateMap = computed(() => {
    const map = new Map<string, WorkbenchShortcutItem>();
    for (const item of shortcutCandidates.value) {
      map.set(item.routeName, item);
    }
    return map;
  });

  /** 过滤无效权限后可用于展示的自定义快捷项 */
  const customItems = computed<WorkbenchShortcutItem[]>(() => {
    const map = candidateMap.value;
    const items: WorkbenchShortcutItem[] = [];

    for (const routeName of savedRouteNames.value) {
      const candidate = map.get(routeName);
      if (candidate) {
        items.push(candidate);
      }
    }

    return items;
  });

  /** 无自定义项时展示的空占位格数量 */
  const emptySlotCount = computed(() => {
    if (customItems.value.length === 0) {
      return WORKBENCH_CUSTOM_EMPTY_SLOT_COUNT;
    }
    if (customItems.value.length < WORKBENCH_CUSTOM_SHORTCUT_MAX) {
      return 1;
    }
    return 0;
  });

  /** 是否还能继续添加快捷入口 */
  const canAddMore = computed(() => customItems.value.length < WORKBENCH_CUSTOM_SHORTCUT_MAX);

  /** 弹窗内可选、且尚未加入自定义区的候选菜单 */
  const addableCandidates = computed(() => {
    const selected = new Set(savedRouteNames.value);
    return shortcutCandidates.value.filter((item) => !selected.has(item.routeName));
  });

  /** 将指定菜单加入自定义快捷入口 */
  function addCustomShortcut(routeName: string): boolean {
    const normalized = routeName.trim();
    if (!normalized || !canAddMore.value) {
      return false;
    }
    if (savedRouteNames.value.includes(normalized)) {
      return false;
    }
    if (!candidateMap.value.has(normalized)) {
      return false;
    }

    savedRouteNames.value = [...savedRouteNames.value, normalized];
    writeWorkbenchCustomShortcuts(savedRouteNames.value.map((name) => ({ routeName: name })));
    return true;
  }

  /** 从自定义快捷入口移除指定菜单 */
  function removeCustomShortcut(routeName: string): void {
    const normalized = routeName.trim();
    if (!normalized) {
      return;
    }

    savedRouteNames.value = savedRouteNames.value.filter((name) => name !== normalized);
    writeWorkbenchCustomShortcuts(savedRouteNames.value.map((name) => ({ routeName: name })));
  }

  return {
    addableCandidates,
    addCustomShortcut,
    canAddMore,
    customItems,
    emptySlotCount,
    reloadFromStorage,
    removeCustomShortcut,
  };
}
