import { storeToRefs } from 'pinia';

import { useAuthStore } from '#/store/auth';

/** 动账类审批按钮是否可见：仅消费后端 applicationConfiguration.currentUser.canFinanceApprove */
export function useFinanceApprovalAccess() {
  const authStore = useAuthStore();
  const { canFinanceApprove } = storeToRefs(authStore);

  return {
    canFinanceApprove,
  };
}
