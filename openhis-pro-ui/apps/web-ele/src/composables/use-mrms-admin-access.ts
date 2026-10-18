import { computed } from 'vue';

import { storeToRefs } from 'pinia';
import { useUserStore } from '@zxplat/stores';

import { useAuthStore } from '#/store/auth';

/**
 * 是否病案管理员（可显示归档病例按钮）。
 *
 * <p>优先 {@code authStore.canMrmsAdmin}；兜底识别 roles 中的 mrms_admin / ROLE_MRMS_ADMIN；
 * 平台 admin 账号同步兜底（避免新角色尚未写入 zxplat_user_roles 时按钮消失）。</p>
 */
export function useMrmsAdminAccess() {
  const authStore = useAuthStore();
  const userStore = useUserStore();
  const { canMrmsAdmin: canMrmsAdminFromConfig, platformAdmin } = storeToRefs(authStore);

  /** 合并配置标志、本地 roles、平台管理员兜底 */
  const canMrmsAdmin = computed(() => {
    if (canMrmsAdminFromConfig.value) {
      return true;
    }
    // 平台管理员：种子角色后可能尚未同步到 zxplat_user_roles，仍应可见归档入口
    if (platformAdmin.value) {
      return true;
    }
    const roles = userStore.userRoles ?? [];
    for (const role of roles) {
      const normalized = String(role ?? '')
        .trim()
        .toUpperCase();
      if (
        normalized === 'ROLE_MRMS_ADMIN' ||
        normalized === 'MRMS_ADMIN' ||
        normalized === '病案管理员'
      ) {
        return true;
      }
    }
    return false;
  });

  return {
    canMrmsAdmin,
  };
}
