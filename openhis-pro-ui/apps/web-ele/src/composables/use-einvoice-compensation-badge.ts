import type { MenuRecordRaw } from '@vben/types';

import { useAccessStore } from '@vben/stores';

import { isEinvoiceUiEnabled } from '#/utils/project-optional-modules';

const BADGE_MENU_CODES = new Set(['EinvoiceBossHub', 'EinvoiceCompensation', 'ChargeStation']);

function patchMenuBadge(menus: MenuRecordRaw[], count: number): MenuRecordRaw[] {
  return menus.map((menu) => {
    const cloned: MenuRecordRaw = {
      ...menu,
      meta: menu.meta ? { ...menu.meta } : {},
      children: menu.children ? patchMenuBadge(menu.children, count) : undefined,
    };
    if (BADGE_MENU_CODES.has(String(menu.name)) && count > 0) {
      cloned.badge = String(count);
      cloned.badgeVariants = 'destructive';
    } else if (BADGE_MENU_CODES.has(String(menu.name))) {
      cloned.badge = undefined;
    }
    return cloned;
  });
}

/**
 * 刷新电子发票待补偿菜单角标。
 */
export async function refreshEinvoiceCompensationMenuBadge(count?: number) {
  if (!isEinvoiceUiEnabled()) {
    return;
  }
  const accessStore = useAccessStore();
  const menus = accessStore.accessMenus;
  if (!menus || menus.length === 0) {
    return;
  }
  let pending = count;
  if (pending === undefined) {
    try {
      const { fetchPendingCompensationStatsApi } = await import('#/api/einvoice-boss/compensation');
      const stats = await fetchPendingCompensationStatsApi();
      pending = Number(stats?.pendingCount ?? 0);
    } catch {
      pending = 0;
    }
  }
  accessStore.setAccessMenus(patchMenuBadge(menus, pending ?? 0));
}
