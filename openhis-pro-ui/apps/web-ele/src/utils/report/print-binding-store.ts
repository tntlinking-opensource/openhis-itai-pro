import {
  getPrintBindingConfigApi,
  resolvePrintBindingApi,
  savePrintBindingConfigApi,
} from '#/api/report/report-print-binding';

/** 业务绑定点编码，与后端 PrintBindingCatalog 一致 */
export const PRINT_BINDING = {
  OUTPATIENT_CHARGE_PRINT: 'outpatient_charge_print',
  OUTPATIENT_PRESCRIPTION_PRINT: 'outpatient_prescription_print',
  OUTPATIENT_CHARGE_LIST_PRINT: 'outpatient_charge_list_print',
  INPATIENT_DAILY_CHARGE_PRINT: 'inpatient_daily_charge_print',
} as const;

const cache = new Map<string, string>();
let hydrated = false;
let hydratePromise: Promise<void> | null = null;

function putCache(bindings: Record<string, string>) {
  for (const [code, templateCode] of Object.entries(bindings)) {
    if (code && templateCode) {
      cache.set(code.trim(), templateCode.trim());
    }
  }
  hydrated = true;
}

export function getCachedPrintTemplateCode(bindingCode: string, fallback: string): string {
  const hit = cache.get(bindingCode);
  if (hit) {
    return hit;
  }
  return fallback;
}

export function invalidatePrintBindingCache(): void {
  cache.clear();
  hydrated = false;
  hydratePromise = null;
}

export async function hydratePrintBindingCache(force = false): Promise<void> {
  if (hydrated && !force) {
    return;
  }
  if (hydratePromise && !force) {
    await hydratePromise;
    return;
  }
  hydratePromise = (async () => {
    const config = await getPrintBindingConfigApi();
    cache.clear();
    for (const item of config.items ?? []) {
      if (item.bindingCode && item.templateCode) {
        cache.set(item.bindingCode, item.templateCode);
      }
    }
    hydrated = true;
  })();
  try {
    await hydratePromise;
  } finally {
    hydratePromise = null;
  }
}

export async function resolvePrintTemplateCode(
  bindingCode: string,
  fallback: string,
): Promise<string> {
  try {
    const result = await resolvePrintBindingApi(bindingCode);
    const code = result.templateCode?.trim();
    if (code) {
      cache.set(bindingCode, code);
      hydrated = true;
      return code;
    }
  } catch {
    /* 回退默认 */
  }
  return getCachedPrintTemplateCode(bindingCode, fallback);
}

export async function savePrintBindings(bindings: Record<string, string>): Promise<void> {
  const config = await savePrintBindingConfigApi(bindings);
  cache.clear();
  for (const item of config.items ?? []) {
    if (item.bindingCode && item.templateCode) {
      cache.set(item.bindingCode, item.templateCode);
    }
  }
  hydrated = true;
}
