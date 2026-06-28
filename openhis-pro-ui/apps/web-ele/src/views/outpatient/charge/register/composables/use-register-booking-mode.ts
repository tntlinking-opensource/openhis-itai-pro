import { ref } from 'vue';

import { createSharedComposable } from '@vueuse/core';

import { toBookingId } from '#/api/booking/booking-id';
import { getRegistrationModeApi } from '#/api/booking/booking-policy';
import { useAuthStore } from '#/store/auth';

/** 挂号台探测 booking 模块是否可用（与预约加载条共用）。 */
export const useRegisterBookingMode = createSharedComposable(() => {
  const bookingEnabled = ref(false);
  const modeLoading = ref(true);

  async function refreshBookingMode() {
    modeLoading.value = true;
    try {
      const authStore = useAuthStore();
      const organizationId = toBookingId(authStore.getSelectedOrganization());
      if (!organizationId) {
        bookingEnabled.value = false;
        return;
      }
      const res = await getRegistrationModeApi({ organizationId });
      bookingEnabled.value = !!res?.bookingEnabled;
    } catch {
      bookingEnabled.value = false;
    } finally {
      modeLoading.value = false;
    }
  }

  void refreshBookingMode();

  return {
    bookingEnabled,
    modeLoading,
    refreshBookingMode,
  };
});
