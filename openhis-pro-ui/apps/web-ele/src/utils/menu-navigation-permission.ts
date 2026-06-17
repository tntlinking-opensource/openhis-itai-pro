import type { Menu } from '#/api/core/system/menu.types';

/** 与后端 MenuNavigationPermissionNames.toPermissionSegment 一致 */
function toPermissionSegment(code: string): string {
  const trimmed = code.trim();
  if (!trimmed) {
    return '';
  }
  let segment = '';
  for (const part of trimmed.split(/[_\-.]+/)) {
    if (!part) {
      continue;
    }
    segment += part.charAt(0).toUpperCase() + part.slice(1).toLowerCase();
  }
  return segment;
}

/** 由菜单 code 生成 Navigation 权限名，例如 MedicineReturnIp → Navigation.Medicinereturnip */
export function menuPermissionNameFromCode(code: string): string | undefined {
  const segment = toPermissionSegment(code);
  if (!segment) {
    return undefined;
  }
  return `Navigation.${segment}`;
}

/**
 * 解析菜单可授权权限名：优先 permissionName，否则按 code 推导（与 MenuViewPermissionProvisioner 一致）。
 */
export function resolveGrantableMenuPermissionName(menu: Menu): string | undefined {
  const explicit = menu.permissionName?.trim();
  if (explicit) {
    return explicit;
  }
  const code = menu.code?.trim();
  if (!code) {
    return undefined;
  }
  return menuPermissionNameFromCode(code);
}
