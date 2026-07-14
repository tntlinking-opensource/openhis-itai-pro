/** 开立医嘱表格列宽：default 与现网 1920 布局一致；compact 仅 <1680 视口。 */
export const ADVICE_TABLE_COL_WIDTH = {
  default: {
    status: 80,
    type: 108,
    contentMin: 350,
    startTime: 170,
    amount: 100,
    diagnosisMin: 140,
    doctor: 100,
    skinTest: 80,
  },
  compact: {
    status: 72,
    type: 96,
    contentMin: 280,
    startTime: 156,
    amount: 88,
    diagnosisMin: 108,
    doctor: 88,
    skinTest: 72,
  },
} as const;

export type AdviceTableColWidthPreset = (typeof ADVICE_TABLE_COL_WIDTH)['default'];
