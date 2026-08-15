/** 费用性质分类码（与后端 {@code ChargeTypeCategory.code} 一致；兼认 ContractCategory 字符串码） */
export const CHARGE_TYPE_CATEGORY = {
  SELF_PAY: '1',
  PUBLIC_PAY: '2',
  PROVINCIAL_INSURANCE: '3',
  MUNICIPAL_INSURANCE: '4',
  WORKER_INSURANCE: 'WORKER_INSURANCE',
} as const;

export type ChargeTypeCategoryCode =
  (typeof CHARGE_TYPE_CATEGORY)[keyof typeof CHARGE_TYPE_CATEGORY];

/** 医保类费用性质（省 / 市；含历史 ContractCategory 字符串码） */
export const INSURANCE_CONTRACT_CATEGORIES = new Set<string>([
  CHARGE_TYPE_CATEGORY.MUNICIPAL_INSURANCE,
  CHARGE_TYPE_CATEGORY.PROVINCIAL_INSURANCE,
  'PROVINCIAL_INSURANCE',
  'MUNICIPAL_INSURANCE',
]);

/** 公费类费用性质 */
export const PUBLIC_CHARGE_CONTRACT_CATEGORIES = new Set<string>([
  CHARGE_TYPE_CATEGORY.PUBLIC_PAY,
  'PUBLIC_CHARGE',
]);

const SELF_PAY_CONTRACT_CATEGORIES = new Set<string>([
  CHARGE_TYPE_CATEGORY.SELF_PAY,
  'SELF_CHARGE',
  'SELF_PAY',
]);

const WORK_INJURY_CONTRACT_CATEGORIES = new Set<string>([
  CHARGE_TYPE_CATEGORY.WORKER_INSURANCE,
  '5',
]);

export function isInsuranceContractCategory(category?: string): boolean {
  return category ? INSURANCE_CONTRACT_CATEGORIES.has(category) : false;
}

export function isPublicChargeContractCategory(category?: string): boolean {
  return category ? PUBLIC_CHARGE_CONTRACT_CATEGORIES.has(category) : false;
}

export function isWorkInjuryContractCategory(category?: string): boolean {
  return category ? WORK_INJURY_CONTRACT_CATEGORIES.has(category) : false;
}

/** 自费费用性质（与 {@link CHARGE_TYPE_CATEGORY.SELF_PAY} 一致） */
export function isSelfPayContractCategory(category?: string): boolean {
  return category ? SELF_PAY_CONTRACT_CATEGORIES.has(category) : false;
}

/** 是否需走医保读卡/待遇链路（省/市医保、工伤；不含公费） */
export function requiresInsuranceBenefitFlow(category?: string): boolean {
  return isInsuranceContractCategory(category) || isWorkInjuryContractCategory(category);
}

/** 是否非自费费用性质（含公费、医保、工伤） */
export function isNonSelfPayContractCategory(category?: string): boolean {
  if (!category) {
    return false;
  }
  return !isSelfPayContractCategory(category);
}

/** 读卡后引导 1101 时，医保费用性质优先顺序 */
export const INSURANCE_CONTRACT_CATEGORY_PRIORITY = [
  CHARGE_TYPE_CATEGORY.PROVINCIAL_INSURANCE,
  CHARGE_TYPE_CATEGORY.MUNICIPAL_INSURANCE,
] as const;

/**
 * 发卡地/参保地前缀 → 推荐医保费用性质类别。
 * 2201→市；其余有效参保地（省内异地/省本级/省外，长度≥4）→省；无效码不推荐。
 */
export function inferInsuranceCategoryFromInsuplc(insuplcAdmdvs?: string): null | string {
  const insu = String(insuplcAdmdvs ?? '').replaceAll(/\D/g, '');
  if (insu.length < 4) {
    return null;
  }
  if (insu.startsWith('2201')) {
    return CHARGE_TYPE_CATEGORY.MUNICIPAL_INSURANCE;
  }
  return CHARGE_TYPE_CATEGORY.PROVINCIAL_INSURANCE;
}

export function normalizeInsuplcAdmdvs(admdvs?: string): string {
  return String(admdvs ?? '').replaceAll(/\D/g, '');
}

/** 吉林院点常用参保地医保区划 → 展示地名（未知码保留原值） */
const INSUPLC_ADMDVS_LABEL: Array<{ label: string; prefix: string }> = [
  { prefix: '229900', label: '吉林省省本级' },
  { prefix: '220199', label: '长春市市本级' },
  { prefix: '220100', label: '长春市' },
  { prefix: '2299', label: '吉林省' },
  { prefix: '2201', label: '长春市' },
  { prefix: '22', label: '吉林省' },
];

/** 参保地展示：优先后端 1901 字典名，否则吉林临时映射，最后原码 */
export function formatInsuplcAdmdvsDisplay(
  admdvs?: string | null,
  dictLabel?: string | null,
): string {
  const label = String(dictLabel ?? '').trim();
  if (label) {
    const digits = normalizeInsuplcAdmdvs(admdvs ?? '');
    if (digits && digits !== label) {
      return `${label}（${digits}）`;
    }
    return label;
  }
  return formatInsuplcAdmdvsDisplayLegacy(admdvs);
}

/** @deprecated 字典未同步时的吉林院点回退映射 */
function formatInsuplcAdmdvsDisplayLegacy(admdvs?: string | null): string {
  const raw = String(admdvs ?? '').trim();
  if (!raw) {
    return '—';
  }
  const digits = normalizeInsuplcAdmdvs(raw);
  if (!digits) {
    return raw;
  }
  for (const item of INSUPLC_ADMDVS_LABEL) {
    if (digits.startsWith(item.prefix)) {
      return digits === item.prefix ? item.label : `${item.label}（${digits}）`;
    }
  }
  return digits;
}
