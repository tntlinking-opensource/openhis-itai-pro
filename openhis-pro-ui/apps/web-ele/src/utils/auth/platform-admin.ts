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

/** 无可登录科室时是否允许跳过选科（平台管理员或 admin 账号） */
export function canSkipLoginDepartmentSelection(options?: {
  roles?: null | string[];
  username?: null | string;
}): boolean {
  return isPlatformAdminRoles(options?.roles) || isAdminUsername(options?.username);
}
