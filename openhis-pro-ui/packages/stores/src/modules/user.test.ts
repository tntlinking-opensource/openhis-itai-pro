import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';

import { normalizeLoginUserInfo, useUserStore } from './user';

describe('normalizeLoginUserInfo', () => {
  it('maps backend userName/name to username/realName', () => {
    const normalized = normalizeLoginUserInfo({
      userName: 'admin',
      name: '管理员',
      email: 'a@b.c',
    } as any);

    expect(normalized.username).toBe('admin');
    expect(normalized.userName).toBe('admin');
    expect(normalized.realName).toBe('管理员');
  });
});

describe('useUserStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('returns correct userInfo with display aliases', () => {
    const store = useUserStore();
    store.setUserInfo({ userName: 'jane', name: 'Jane Doe', roles: ['user'] } as any);
    expect(store.userInfo?.username).toBe('jane');
    expect(store.userInfo?.realName).toBe('Jane Doe');
    expect(store.userRoles).toEqual(['user']);
  });

  // 测试重置用户信息时的行为
  it('clears userInfo and userRoles when setting null userInfo', () => {
    const store = useUserStore();
    store.setUserInfo({
      userName: 'u1',
      name: 'U1',
      roles: [{ roleName: 'User', value: 'user' }],
    } as any);
    expect(store.userInfo).not.toBeNull();
    expect(store.userRoles.length).toBeGreaterThan(0);

    store.setUserInfo(null as any);
    expect(store.userInfo).toBeNull();
    expect(store.userRoles).toEqual([]);
  });

  // 测试在没有用户角色时返回空数组
  it('returns an empty array for userRoles if not set', () => {
    const store = useUserStore();
    expect(store.userRoles).toEqual([]);
  });
});
