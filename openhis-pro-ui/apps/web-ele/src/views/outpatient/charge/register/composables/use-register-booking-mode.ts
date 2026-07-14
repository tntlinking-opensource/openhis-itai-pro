import { ref } from 'vue';

import { createSharedComposable } from '@vueuse/core';

import type { BookingEntityId } from '#/api/booking/booking-id';
import { getRegistrationModeApi } from '#/api/booking/booking-policy';

/** 挂号台探测 booking 模块是否可用（与号源面板显隐共用）。 */
export const useRegisterBookingMode = createSharedComposable(() => {
  const bookingEnabled = ref(false);
  const modeLoading = ref(true);

  /**
   * 探测 booking 模块；organizationId 可选（无科室时仍探测模块级 bookingEnabled）。
   */
  async function refreshBookingMode(organizationId?: BookingEntityId) {
    modeLoading.value = true;
    try {
      const res = await getRegistrationModeApi(organizationId ? { organizationId } : {});
      bookingEnabled.value = res?.bookingEnabled !== false;
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
