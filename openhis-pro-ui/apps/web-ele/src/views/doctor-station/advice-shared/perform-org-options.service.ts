import type { ActivityExecutionLocationRuleRowDto } from '#/api/doctor-station/advice.types';

import { initExecuteLocationApi } from '#/api/basic/execute-location';
import type { ApiOptionItem } from '#/types/api-response';

export type PerformOrgOption = { label: string; value: string };

type OrgTreeOption = ApiOptionItem & { children?: OrgTreeOption[] };

let allDepartmentOptionsCache: PerformOrgOption[] | null = null;
let allDepartmentOptionsLoading: Promise<PerformOrgOption[]> | null = null;

/** 递归扁平化机构树为下拉项，按 value 去重 */
function flattenOrgTree(nodes: OrgTreeOption[]): PerformOrgOption[] {
  const seen = new Set<string>();
  const result: PerformOrgOption[] = [];

  const walk = (list: OrgTreeOption[]) => {
    for (const node of list) {
      const value = String(node.value ?? '').trim();
      const label = String(node.label ?? '').trim();
      if (value && !seen.has(value)) {
        seen.add(value);
        result.push({ value, label: label || value });
      }
      if (node.children?.length) {
        walk(node.children);
      }
    }
  };

  walk(nodes);
  return result;
}

/** IF-DOR-19 规则行去重转执行科室下拉 */
export function mapActivityExecutionRulesToPerformOrgOptions(
  rules: ActivityExecutionLocationRuleRowDto[] | undefined,
): PerformOrgOption[] {
  const seen = new Set<string>();
  const opts: PerformOrgOption[] = [];
  for (const rule of rules ?? []) {
    const value = String(rule.executionOrgId ?? '').trim();
    if (!value || seen.has(value)) {
      continue;
    }
    seen.add(value);
    const label = String(rule.executionOrgName ?? '').trim();
    opts.push({ value, label: label || value });
  }
  return opts;
}

/** 配置无规则时回落全院科室（与基础数据「执行科室」init 同源） */
export async function loadAllDepartmentPerformOrgOptions(): Promise<PerformOrgOption[]> {
  if (allDepartmentOptionsCache) {
    return allDepartmentOptionsCache;
  }
  if (allDepartmentOptionsLoading) {
    return allDepartmentOptionsLoading;
  }

  allDepartmentOptionsLoading = (async () => {
    try {
      const initData = await initExecuteLocationApi();
      const tree = (initData.executionOrgOptions ?? []) as OrgTreeOption[];
      allDepartmentOptionsCache = flattenOrgTree(tree);
      return allDepartmentOptionsCache;
    } catch {
      allDepartmentOptionsCache = [];
      return [];
    } finally {
      allDepartmentOptionsLoading = null;
    }
  })();

  return allDepartmentOptionsLoading;
}

/** 保证当前已选执行科室仍出现在下拉中（历史回显） */
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
