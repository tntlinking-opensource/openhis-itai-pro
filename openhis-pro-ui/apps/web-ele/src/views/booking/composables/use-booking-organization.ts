import { computed } from 'vue';

import type { BookingEntityId } from '#/api/booking/booking-id';
import { toBookingId } from '#/api/booking/booking-id';
import { useAuthStore } from '#/store/auth';
import { resolveLoginOrganizationIdForRequest } from '#/utils/format/preserve-api-id';

export type UseBookingOrganizationOptions = {
  /**
   * 医院级会话（登录只选了机构、跳过选科）时，回退到登录医院 id。
   * 体检号源等院级业务应开启；门诊排班等科室业务保持默认关闭。
   */
  fallbackToHospital?: boolean;
};

/**
 * 当前 booking 业务机构主键（string，避免 Long 精度丢失）。
 * 默认：登录科室 → 用户所属科室；可选再回退登录医院。
 */
export function useBookingOrganization(options?: UseBookingOrganizationOptions) {
  const authStore = useAuthStore();
  const organizationId = computed((): BookingEntityId | undefined => {
    const fromLoginScope = toBookingId(
      resolveLoginOrganizationIdForRequest(
        authStore.userInfo as null | Record<string, unknown> | undefined,
        authStore.getSelectedOrganization(),
      ),
    );
    if (fromLoginScope) {
      return fromLoginScope;
    }
    if (options?.fallbackToHospital) {
      return toBookingId(authStore.getSelectedHospital());
    }
    return undefined;
  });
  return { organizationId };
}
