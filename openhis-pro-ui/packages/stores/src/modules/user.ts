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

/**
 * @zh_CN 用户信息相关
 */
export const useUserStore = defineStore('core-user', {
  actions: {
    setUserInfo(userInfo: LoginUser | null) {
      // 设置用户信息
      this.userInfo = userInfo;
      // // // 设置角色信息
      // const roles = userInfo?.roles ?? [];
      // this.setUserRoles(roles);
    },
    setUserRoles(roles: string[]) {
      this.userRoles = roles;
    },
  },
  persist: {
    pick: ['userInfo', 'userRoles'],
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
