import { acceptHMRUpdate, defineStore } from 'pinia';

export interface LoginUser {
  accessFailedCount: number;
  avatar: string;
  createdAt: string;
  email: string;
  extraProperties: null | Record<string, any>;
  id: number;
  isActive: boolean;
  isLockedOut: boolean;
  lockoutEnd: null | string;
  name: null | string;
  phoneNumber: null | string;
  realName: string;
  surname: null | string;
  tenantId: null | number;
  updatedAt: string;
  userName: string;
  username: string;
  /** adm_practitioner.id（登录接口扩展，可选） */
  practitionerId?: null | number | string;
  /** adm_organization.id（登录接口扩展，可选） */
  organizationId?: null | number | string;
  /** 角色（登录 configuration / 刷新配置时可能附带） */
  roles?: Array<Record<string, any> | string>;
}

interface AccessState {
  /**
   * 用户信息
   */
  userInfo: LoginUser | null;
  /**
   * 用户角色
   */
  userRoles: string[];
}

/** 从角色原始值提取可展示的 role 字符串 */
function extractRoleCodes(roles: LoginUser['roles']): string[] {
  if (!Array.isArray(roles) || roles.length === 0) {
    return [];
  }
  // 兼容 string / { code } / { value } / { roleKey } / { roleName } / { name }
  return roles
    .map((role) => {
      if (typeof role === 'string') {
        return role.trim();
      }
      if (!role || typeof role !== 'object') {
        return '';
      }
      const code = role.code ?? role.value ?? role.roleKey ?? role.roleName ?? role.name ?? '';
      return String(code).trim();
    })
    .filter((code) => code !== '');
}

/**
 * 将后端 login / currentUser 字段对齐到布局与个人中心使用的 username、realName。
 * 后端常见：userName、name；前端展示约定：username、realName。
 */
export function normalizeLoginUserInfo(
  userInfo: LoginUser | (Partial<LoginUser> & Record<string, unknown>),
): LoginUser {
  const userName = String(userInfo.userName ?? userInfo.username ?? '').trim();
  const username = String(userInfo.username ?? userInfo.userName ?? '').trim() || userName;
  const name = userInfo.name == null ? '' : String(userInfo.name).trim();
  const realName =
    String(userInfo.realName ?? '').trim() ||
    name ||
    String((userInfo as Record<string, unknown>).nickName ?? '').trim() ||
    username ||
    userName;

  return {
    ...(userInfo as LoginUser),
    userName: userName || username,
    username: username || userName,
    realName,
    name: userInfo.name ?? (name || null),
    avatar: userInfo.avatar ?? '',
    email: userInfo.email ?? '',
  };
}

/**
 * @zh_CN 用户信息相关
 */
export const useUserStore = defineStore('core-user', {
  actions: {
    setUserInfo(userInfo: LoginUser | null) {
      if (!userInfo) {
        this.userInfo = null;
        this.userRoles = [];
        return;
      }
      // 统一补齐 username / realName，供右上角与个人中心回显
      this.userInfo = normalizeLoginUserInfo(userInfo);
      const roles = extractRoleCodes(userInfo.roles);
      if (roles.length > 0) {
        this.setUserRoles(roles);
      }
    },
    setUserRoles(roles: LoginUser['roles'] | string[]) {
      // 登录 configuration.roles 可能是 string[]（name）或对象数组，统一提取
      this.userRoles = extractRoleCodes(roles as LoginUser['roles']);
    },
  },
  persist: {
    pick: ['userInfo', 'userRoles'],
    afterHydrate: (ctx) => {
      // 历史缓存可能只有 userName/name，恢复后再归一化一次
      if (ctx.store.userInfo) {
        ctx.store.userInfo = normalizeLoginUserInfo(ctx.store.userInfo);
      }
    },
  },
  state: (): AccessState => ({
    userInfo: null,
    userRoles: [],
  }),
});

// 解决热更新问题
const hot = import.meta.hot;
if (hot) {
  hot.accept(acceptHMRUpdate(useUserStore, hot));
}
