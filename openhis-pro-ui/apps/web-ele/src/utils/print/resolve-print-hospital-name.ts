import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';

import { useAuthStore } from '#/store';

function firstNonEmpty(values: unknown[]): string {
  for (const item of values) {
    const text = String(item ?? '').trim();
    if (text) {
      return text;
    }
  }
  return '';
}

/** 从登录用户 / 登录选院 / 租户上下文解析机构抬头 */
export function resolvePrintHospitalName(): string {
  const userStore = useUserStore();
  const userInfo = userStore.userInfo as null | Record<string, unknown> | undefined;
  const ext = (userInfo?.extraProperties ?? {}) as Record<string, unknown>;

  const fromUser = firstNonEmpty([
    userInfo?.hospitalName,
    userInfo?.tenantName,
    userInfo?.orgName,
    userInfo?.organizationName,
    ext.hospitalName,
    ext.HospitalName,
    ext.tenantName,
    ext.TenantName,
    ext.orgName,
    ext.OrgName,
    ext.organizationName,
    ext.OrganizationName,
  ]);
  if (fromUser) {
    return fromUser;
  }

  try {
    const authStore = useAuthStore();
    const fromHospital = firstNonEmpty([authStore.getSelectedHospitalName()]);
    if (fromHospital) {
      return fromHospital;
    }

    const tenantId = authStore.getSelectedTenant();
    const tenants = authStore.getTenantList() as Array<Record<string, unknown>>;
    const tenant = tenants.find((item) => String(item?.id ?? '') === String(tenantId ?? ''));
    const fromTenant = firstNonEmpty([tenant?.name, tenant?.displayName, tenant?.tenantName]);
    if (fromTenant) {
      return fromTenant;
    }
  } catch {
    // auth store 不可用时忽略，继续兜底
  }

  return firstNonEmpty([preferences.app.name]);
}
