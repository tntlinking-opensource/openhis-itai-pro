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

/**
 * 包装类拆分属性（整包不拆 / 包装单次取整）：
 * 总量默认走包装带，总量单位下拉不展示拆零单位。
 */
export function isPackageBoundOrderCalcRule(ruleCode: string | undefined): boolean {
  const code = String(ruleCode ?? '').trim();
  return (
    code === OrderCalcRuleCode.PACKAGE_NO_SPLIT || code === OrderCalcRuleCode.PACKAGE_SINGLE_CEIL
  );
}
