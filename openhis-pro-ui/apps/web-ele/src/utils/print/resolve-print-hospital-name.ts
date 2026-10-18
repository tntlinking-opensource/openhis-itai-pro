import { preferences } from '@zxplat/preferences';
import { useUserStore } from '@zxplat/stores';

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

/** 红旗医院组织机构代码（国标码；登录上下文常无此字段，院点写死） */
const HONGQI_ORG_CODE = '12220104MB1287293C';

/** 是否红旗/朝阳中医院点（中医病案首页 + 组织机构代码写死） */
export function isHongqiHospitalSite(): boolean {
  const hospitalName = resolvePrintHospitalName();
  if (hospitalName.includes('红旗')) {
    return true;
  }
  const projectId = String(import.meta.env.VITE_ZXPLAT_PROJECT ?? '')
    .trim()
    .replace(/^project-/i, '');
  return projectId === 'H22010402403';
}

/** 从登录用户扩展字段解析组织机构代码（无可用来源时返回空串） */
export function resolvePrintOrgCode(): string {
  // 红旗医院：写死国标组织机构代码
  if (isHongqiHospitalSite()) {
    return HONGQI_ORG_CODE;
  }

  const userStore = useUserStore();
  const userInfo = userStore.userInfo as null | Record<string, unknown> | undefined;
  const ext = (userInfo?.extraProperties ?? {}) as Record<string, unknown>;
  return firstNonEmpty([
    userInfo?.orgCode,
    userInfo?.organizationCode,
    userInfo?.creditCode,
    userInfo?.uscc,
    ext.orgCode,
    ext.OrgCode,
    ext.organizationCode,
    ext.OrganizationCode,
    ext.creditCode,
    ext.CreditCode,
    ext.uscc,
    ext.USCC,
  ]);
}
