/** 门诊收费成功后打印 templateCode — 院点绑定 {@link PRINT_BINDING.OUTPATIENT_CHARGE_PRINT} */

import {
  getCachedPrintTemplateCode,
  hydratePrintBindingCache,
  PRINT_BINDING,
  savePrintBindings,
} from '#/utils/report/print-binding-store';

export const OUTPATIENT_CHARGE_PRINT_RECEIPT = 'charge_receipt';
export const OUTPATIENT_CHARGE_PRINT_INVOICE_OVERLAY = 'outpatient_invoice_overlay';

export const OUTPATIENT_CHARGE_PRINT_OPTIONS = [
  { label: '热敏凭条', value: OUTPATIENT_CHARGE_PRINT_RECEIPT },
  { label: '票据联 (overlay)', value: OUTPATIENT_CHARGE_PRINT_INVOICE_OVERLAY },
] as const;

export function getOutpatientChargePrintTemplateCode(): string {
  return getCachedPrintTemplateCode(
    PRINT_BINDING.OUTPATIENT_CHARGE_PRINT,
    OUTPATIENT_CHARGE_PRINT_RECEIPT,
  );
}

export async function ensureOutpatientChargePrintBindingLoaded(): Promise<void> {
  await hydratePrintBindingCache();
}

export async function setOutpatientChargePrintTemplateCode(code: string): Promise<void> {
  const normalized = code.trim();
  const allowed = OUTPATIENT_CHARGE_PRINT_OPTIONS.some((o) => o.value === normalized);
  if (!allowed) {
    return;
  }
  await savePrintBindings({
    [PRINT_BINDING.OUTPATIENT_CHARGE_PRINT]: normalized,
  });
}

export function isOutpatientInvoiceOverlayTemplate(code: string): boolean {
  return code.trim() === OUTPATIENT_CHARGE_PRINT_INVOICE_OVERLAY;
}
