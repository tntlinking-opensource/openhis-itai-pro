/** 住院结算页工具栏/底栏动作 code */
export const INPATIENT_SETTLE_TOOLBAR_ACTION = {
  REFRESH_FEES: 'REFRESH_FEES',
  VERIFY_INSURANCE: 'VERIFY_INSURANCE',
} as const;

export type InpatientSettleToolbarActionCode =
  (typeof INPATIENT_SETTLE_TOOLBAR_ACTION)[keyof typeof INPATIENT_SETTLE_TOOLBAR_ACTION];

export const INPATIENT_SETTLE_TOOLBAR_ACTION_LABEL: Record<
  InpatientSettleToolbarActionCode,
  string
> = {
  [INPATIENT_SETTLE_TOOLBAR_ACTION.REFRESH_FEES]: '刷新费用',
  [INPATIENT_SETTLE_TOOLBAR_ACTION.VERIFY_INSURANCE]: '医保校验',
};

export const INPATIENT_SETTLE_FOOTER_ACTION = {
  CONFIRM_SETTLE: 'CONFIRM_SETTLE',
} as const;

export type InpatientSettleFooterActionCode =
  (typeof INPATIENT_SETTLE_FOOTER_ACTION)[keyof typeof INPATIENT_SETTLE_FOOTER_ACTION];

export const INPATIENT_SETTLE_FOOTER_ACTION_LABEL: Record<InpatientSettleFooterActionCode, string> =
  {
    [INPATIENT_SETTLE_FOOTER_ACTION.CONFIRM_SETTLE]: '确认结算',
  };
