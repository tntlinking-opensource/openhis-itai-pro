/** 住院记账页签 code（逻辑判断用） */
export type InpatientBillingTab = 'auto-roll' | 'order-billing' | 'pricing-confirmation';

export interface InpatientBillingTabItem {
  label: string;
  name: InpatientBillingTab;
}

/** 产品默认页签（院点可 override 本文件） */
export const INPATIENT_BILLING_TABS: InpatientBillingTabItem[] = [
  { label: '医嘱计费', name: 'order-billing' },
  { label: '非医嘱计费', name: 'pricing-confirmation' },
  { label: '自动计费', name: 'auto-roll' },
];

/** 产品默认激活页签 */
export const INPATIENT_BILLING_DEFAULT_TAB: InpatientBillingTab = 'pricing-confirmation';
