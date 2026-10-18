import { useAccessStore } from '@zxplat/stores';

import { loginByOrgApi } from '#/api/core/auth';
import { useAuthStore } from '#/store';
import {
  requireLoginScopeReady,
  resetLoginScopeSync,
} from '#/utils/auth/require-login-scope-ready';

/**
 * 切换科室：先写服务端会话，成功后再持久化 localStorage，并串行恢复租户/医院上下文。
 * 与登录页选科、左上角切换科室共用；禁止「展示已是新科、会话仍是旧科」。
 */
export async function switchOrganizationUnitSession(organizationUnitId: string): Promise<void> {
  const trimmed = organizationUnitId.trim();
  if (!trimmed) {
    throw new Error('科室不能为空');
  }

  const authStore = useAuthStore();
  const accessStore = useAccessStore();
  const previousOrgId = authStore.getSelectedOrganization();

  resetLoginScopeSync();
  try {
    // 必须先打中服务端会话，再改本地展示；失败则回滚本地，避免顶栏与会话漂移
    await loginByOrgApi({ organizationUnitId: trimmed });
    authStore.setSelectedOrganization(trimmed);
    accessStore.setIsAccessChecked(false);
    await requireLoginScopeReady();
  } catch (error) {
    if (previousOrgId) {
      authStore.setSelectedOrganization(previousOrgId);
    } else {
      authStore.setSelectedOrganization('');
    }
    resetLoginScopeSync();
    throw error;
  }
}
