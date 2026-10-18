/**
 * 开立医嘱表格列宽：default 用于 ≥1680；compact 仅 <1680。
 * 类型/金额/皮试等列收窄，诊断改为固定宽，多出来的宽度让给医嘱内容，避免出横向滚动条。
 */
export const ADVICE_TABLE_COL_WIDTH = {
  default: {
    status: 88,
    chargeStatus: 88,
    feeNature: 88,
    type: 88,
    contentMin: 350,
    startTime: 170,
    amount: 88,
    diagnosis: 168,
    doctor: 88,
    skinTest: 64,
  },
  compact: {
    status: 80,
    chargeStatus: 80,
    feeNature: 80,
    type: 80,
    contentMin: 280,
    startTime: 156,
    amount: 80,
    diagnosis: 132,
    doctor: 80,
    skinTest: 56,
  },
} as const;

export type AdviceTableColWidthPreset = (typeof ADVICE_TABLE_COL_WIDTH)['default'];
