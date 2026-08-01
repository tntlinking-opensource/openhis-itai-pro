export * from './infectious-dict';
export * from './infectious-push-monitor';
export * from './csv-import';

import { useAuthStore } from '#/store';

/** 解析 CRB 请求租户 id（Q13 单租户默认 1）。 */
export function resolveCrbTenantId(): string {
  const tenantId = useAuthStore().getSelectedTenant();
  return tenantId || '1';
}
