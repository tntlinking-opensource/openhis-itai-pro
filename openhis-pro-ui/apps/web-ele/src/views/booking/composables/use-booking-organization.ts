import { computed } from 'vue';

import type { BookingEntityId } from '#/api/booking/booking-id';
import { toBookingId } from '#/api/booking/booking-id';
import { useAuthStore } from '#/store/auth';

/** 当前登录科室主键（string，避免 Long 精度丢失）；未选科室时返回 undefined */
export function useBookingOrganization() {
  const authStore = useAuthStore();
  const organizationId = computed((): BookingEntityId | undefined =>
    toBookingId(authStore.getSelectedOrganization()),
  );
  return { organizationId };
}
