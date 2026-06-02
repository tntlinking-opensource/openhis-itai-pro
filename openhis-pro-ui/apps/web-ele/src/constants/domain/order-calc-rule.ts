/**
 * 门诊发药/计价取整规则编码，与后端 {@code OrderCalcRuleEnum#getCode()} 一致（持久化为数字字符串 "1"~"4"）。
 * 药品目录表单「门诊拆分属性」「住院临时医嘱拆分属性」下拉与此对齐。
 */
export const OrderCalcRuleCode = {
  SINGLE_SPLIT_CEIL: '1',
  PACKAGE_NO_SPLIT: '2',
  TOTAL_SPLIT_CEIL: '3',
  PACKAGE_SINGLE_CEIL: '4',
} as const;

export type OrderCalcRuleCodeValue = (typeof OrderCalcRuleCode)[keyof typeof OrderCalcRuleCode];
