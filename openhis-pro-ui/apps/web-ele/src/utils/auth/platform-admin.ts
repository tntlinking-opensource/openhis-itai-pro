/** 判断登录配置中的角色是否属于平台管理员（admin / ROLE_ADMIN） */
export function isPlatformAdminRoles(roles?: null | string[]): boolean {
  if (!roles?.length) {
    return false;
  }
  return roles.some(
    (role) => role === 'admin' || role === 'ROLE_ADMIN' || role.toLowerCase() === 'admin',
  );
}

/** 平台内置 admin 账号（清空数据后用于初始化医院/科室） */
export function isAdminUsername(username?: null | string): boolean {
  return username?.trim().toLowerCase() === 'admin';
}

/** 下拉展示名是否平台内置 admin（业务选型下拉剔除；日结等历史经办场景勿用） */
export function isPlatformAdminOptionLabel(label?: null | string): boolean {
  return String(label ?? '').trim().toLowerCase() === 'admin';
}

/**
 * 从人员下拉选项中剔除平台 admin。
 * 日结/费用明细等「按实际经办人筛选」勿调用。
 */
export function excludePlatformAdminOptions<T extends { label?: null | string }>(
  options?: null | T[],
): T[] {
  if (!options?.length) {
    return [];
  }
  return options.filter((item) => !isPlatformAdminOptionLabel(item.label));
}

/**
 * 是否平台管理员身份（角色或 admin 账号）。
 * 注意：无可登录科室时的医院级登录对任意用户开放，不经此函数；
 * 本函数仅用于 platformAdmin 标识及「有可登录科室但未选」时的特殊放行。
 */
export function canSkipLoginDepartmentSelection(options?: {
  roles?: null | string[];
  username?: null | string;
}): boolean {
  return isPlatformAdminRoles(options?.roles) || isAdminUsername(options?.username);
}
