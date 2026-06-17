import { computed } from 'vue';

import { useAuthStore } from '#/store/auth';

/** 当前登录科室主键；未选科室时返回 undefined */
export function useBookingOrganization() {
  const authStore = useAuthStore();
  const organizationId = computed(() => {
    const raw = authStore.getSelectedOrganization();
    const id = Number(raw);
    return Number.isFinite(id) && id > 0 ? id : undefined;
  });
  return { organizationId };
}
