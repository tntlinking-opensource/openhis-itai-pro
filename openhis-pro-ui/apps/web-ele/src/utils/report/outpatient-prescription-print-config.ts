/** 门诊处方打印 templateCode — 院点绑定 {@link PRINT_BINDING.OUTPATIENT_PRESCRIPTION_PRINT} */

import {
  getCachedPrintTemplateCode,
  hydratePrintBindingCache,
  PRINT_BINDING,
  savePrintBindings,
} from '#/utils/report/print-binding-store';

export const OUTPATIENT_PRESCRIPTION_TEMPLATE_FLOW = 'outpatient_prescription_flow';
export const OUTPATIENT_PRESCRIPTION_TEMPLATE_OVERLAY = 'outpatient_prescription_overlay';
export const OUTPATIENT_PRESCRIPTION_TCM_TEMPLATE_FLOW = 'outpatient_prescription_tcm_flow';
export const OUTPATIENT_PRESCRIPTION_TCM_TEMPLATE_OVERLAY = 'outpatient_prescription_tcm_overlay';
export const OUTPATIENT_PRESCRIPTION_NARCOTIC_TEMPLATE_FLOW =
  'outpatient_prescription_narcotic_flow';
export const OUTPATIENT_PRESCRIPTION_NARCOTIC_TEMPLATE_OVERLAY =
  'outpatient_prescription_narcotic_overlay';
export const DISPENSE_SLIP_TEMPLATE = 'dispense_slip';

const VALID_TEMPLATE_CODES = new Set([
  OUTPATIENT_PRESCRIPTION_TEMPLATE_FLOW,
  OUTPATIENT_PRESCRIPTION_TEMPLATE_OVERLAY,
  OUTPATIENT_PRESCRIPTION_TCM_TEMPLATE_FLOW,
  OUTPATIENT_PRESCRIPTION_TCM_TEMPLATE_OVERLAY,
  OUTPATIENT_PRESCRIPTION_NARCOTIC_TEMPLATE_FLOW,
  OUTPATIENT_PRESCRIPTION_NARCOTIC_TEMPLATE_OVERLAY,
  DISPENSE_SLIP_TEMPLATE,
]);

export const OUTPATIENT_PRESCRIPTION_TEMPLATE_OPTIONS = [
  { label: '白纸 (flow)', value: OUTPATIENT_PRESCRIPTION_TEMPLATE_FLOW },
  { label: '套打 (overlay)', value: OUTPATIENT_PRESCRIPTION_TEMPLATE_OVERLAY },
  { label: '发药单', value: DISPENSE_SLIP_TEMPLATE },
] as const;

type PrescriptionLayoutPreference = 'dispense_slip' | 'flow' | 'overlay';

function normalizeStoredTemplateCode(stored: string): PrescriptionLayoutPreference {
  const code = stored.trim();
  if (code === DISPENSE_SLIP_TEMPLATE) {
    return 'dispense_slip';
  }
  if (
    code === OUTPATIENT_PRESCRIPTION_TEMPLATE_OVERLAY ||
    code === OUTPATIENT_PRESCRIPTION_TCM_TEMPLATE_OVERLAY ||
    code === OUTPATIENT_PRESCRIPTION_NARCOTIC_TEMPLATE_OVERLAY
  ) {
    return 'overlay';
  }
  return 'flow';
}

export function getOutpatientPrescriptionTemplateCode(): string {
  return resolveOutpatientPrescriptionTemplateCode();
}

export async function ensureOutpatientPrescriptionPrintBindingLoaded(): Promise<void> {
  await hydratePrintBindingCache();
}

export function resolveOutpatientPrescriptionTemplateCode(options?: {
  narcoticOnly?: boolean;
  tcmOnly?: boolean;
  templateCode?: string;
}): string {
  const explicit = options?.templateCode?.trim();
  if (explicit) {
    return explicit;
  }

  const stored = getCachedPrintTemplateCode(
    PRINT_BINDING.OUTPATIENT_PRESCRIPTION_PRINT,
    OUTPATIENT_PRESCRIPTION_TEMPLATE_FLOW,
  );
  const preference = normalizeStoredTemplateCode(stored);

  if (preference === 'dispense_slip') {
    return DISPENSE_SLIP_TEMPLATE;
  }

  const narcotic = options?.narcoticOnly === true;
  const tcm = options?.tcmOnly === true;
  if (preference === 'overlay') {
    if (narcotic) {
      return OUTPATIENT_PRESCRIPTION_NARCOTIC_TEMPLATE_OVERLAY;
    }
    if (tcm) {
      return OUTPATIENT_PRESCRIPTION_TCM_TEMPLATE_OVERLAY;
    }
    return OUTPATIENT_PRESCRIPTION_TEMPLATE_OVERLAY;
  }
  if (narcotic) {
    return OUTPATIENT_PRESCRIPTION_NARCOTIC_TEMPLATE_FLOW;
  }
  if (tcm) {
    return OUTPATIENT_PRESCRIPTION_TCM_TEMPLATE_FLOW;
  }
  return OUTPATIENT_PRESCRIPTION_TEMPLATE_FLOW;
}

export async function setOutpatientPrescriptionTemplateCode(code: string): Promise<void> {
  const normalized = code.trim();
  if (!VALID_TEMPLATE_CODES.has(normalized)) {
    return;
  }
  await savePrintBindings({
    [PRINT_BINDING.OUTPATIENT_PRESCRIPTION_PRINT]: normalized,
  });
}
