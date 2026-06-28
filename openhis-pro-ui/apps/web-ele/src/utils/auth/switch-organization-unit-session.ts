import { useAccessStore } from '@vben/stores';

import { loginByOrgApi } from '#/api/core/auth';
import { useAuthStore } from '#/store';
import {
  requireLoginScopeReady,
  resetLoginScopeSync,
} from '#/utils/auth/require-login-scope-ready';

/**
 * 切换科室：先写 localStorage，再写服务端会话，并串行恢复租户/医院上下文。
 * 与登录页选科、左上角切换科室共用，避免只改前端展示而后端仍按旧科室收窄。
 */
export async function switchOrganizationUnitSession(organizationUnitId: string): Promise<void> {
  const trimmed = organizationUnitId.trim();
  if (!trimmed) {
    throw new Error('科室不能为空');
  }

  const authStore = useAuthStore();
  const accessStore = useAccessStore();

  // 先持久化目标科室，供 requireLoginScopeReady 在 selectTenant 后回写同一会话科室
  authStore.setSelectedOrganization(trimmed);
  resetLoginScopeSync();

  await loginByOrgApi({ organizationUnitId: trimmed });

  accessStore.setIsAccessChecked(false);
  await requireLoginScopeReady();
}
