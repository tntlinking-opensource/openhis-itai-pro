import { computed } from 'vue';

import { useAuthStore } from '#/store/auth';

/** 是否可审批/驳行动账类单据（用户管理分配的财务审批角色） */
export function useFinanceApprovalAccess() {
  const authStore = useAuthStore();

  const canFinanceApprove = computed(() => authStore.getCanFinanceApprove());

  return {
    canFinanceApprove,
  };
}
