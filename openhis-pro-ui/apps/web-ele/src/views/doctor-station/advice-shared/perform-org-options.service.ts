/**
 * 医生站执行科室下拉：规则行去重、多项目并集、历史回显。
 * 无配置时由后端 listActivityExecutionLocations 回落开立科室，本模块不再拉全院科室。
 */
import type { ActivityExecutionLocationRuleRowDto } from '#/api/doctor-station/advice.types';

export type PerformOrgOption = { label: string; value: string };

/**
 * IF-DOR-19 规则行去重转执行科室下拉。
 *
 * @param rules 后端返回的规则行；可含无配置时的开立科室回落行
 */
export function mapActivityExecutionRulesToPerformOrgOptions(
  rules: ActivityExecutionLocationRuleRowDto[] | undefined,
): PerformOrgOption[] {
  const seen = new Set<string>();
  const opts: PerformOrgOption[] = [];
  for (const rule of rules ?? []) {
    const value = String(rule.executionOrgId ?? '').trim();
    // 空执行科室或重复 id 跳过
    if (!value || seen.has(value)) {
      continue;
    }
    seen.add(value);
    const label = String(rule.executionOrgName ?? '').trim();
    opts.push({ value, label: label || value });
  }
  return opts;
}

/**
 * 合并多组执行科室候选。无配置时接口已带回开立科室，这里只去重，不再回落全院。
 *
 * @param ruleGroups 各诊疗项目各自的规则行列表
 */
export function unionPerformOrgOptions(
  ruleGroups: Array<ActivityExecutionLocationRuleRowDto[] | undefined>,
): PerformOrgOption[] {
  const seen = new Map<string, PerformOrgOption>();
  for (const rules of ruleGroups) {
    for (const option of mapActivityExecutionRulesToPerformOrgOptions(rules)) {
      if (!seen.has(option.value)) {
        seen.set(option.value, option);
      }
    }
  }
  return [...seen.values()];
}

/**
 * 保证当前已选执行科室仍出现在下拉中（历史回显）。
 *
 * @param options 当前候选
 * @param performOrgId 已选执行科室 id
 * @param performOrgName 已选执行科室名
 */
export function mergeCurrentPerformOrgIntoOptions(
  options: PerformOrgOption[],
  performOrgId?: string,
  performOrgName?: string,
): PerformOrgOption[] {
  const list = options.map((option) => ({ ...option }));
  const id = String(performOrgId ?? '').trim();
  const name = String(performOrgName ?? '').trim();
  if (id && !list.some((option) => option.value === id)) {
    list.unshift({ value: id, label: name || id });
  }
  return list;
}
