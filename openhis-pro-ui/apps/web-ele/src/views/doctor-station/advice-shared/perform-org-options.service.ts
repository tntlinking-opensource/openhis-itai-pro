import type { ActivityExecutionLocationRuleRowDto } from '#/api/doctor-station/advice.types';

import { initExecuteLocationApi } from '#/api/basic/execute-location';
import type { ApiOptionItem } from '#/types/api-response';

export type PerformOrgOption = { label: string; value: string };

type OrgTreeOption = ApiOptionItem & { children?: OrgTreeOption[] };

let allDepartmentOptionsCache: PerformOrgOption[] | null = null;
let allDepartmentOptionsLoading: Promise<PerformOrgOption[]> | null = null;

/** 开立科室树顶层 id（医院根），执行科室回落下拉须排除 */
function collectHospitalRootIds(nodes: OrgTreeOption[]): Set<string> {
  const ids = new Set<string>();
  for (const node of nodes) {
    const value = String(node.value ?? '').trim();
    if (value) {
      ids.add(value);
    }
  }
  return ids;
}

/** 执行科室候选：扁平化时仅保留叶子节点，排除医院等非科室父节点 */
function flattenOrgTree(nodes: OrgTreeOption[], excludeIds?: Set<string>): PerformOrgOption[] {
  const seen = new Set<string>();
  const result: PerformOrgOption[] = [];

  const walk = (list: OrgTreeOption[]) => {
    for (const node of list) {
      const children = node.children ?? [];
      // 有子节点的是医院/上级，不当作可选科室
      if (children.length > 0) {
        walk(children);
        continue;
      }
      const value = String(node.value ?? '').trim();
      const label = String(node.label ?? '').trim();
      if (!value || seen.has(value) || excludeIds?.has(value)) {
        continue;
      }
      seen.add(value);
      result.push({ value, label: label || value });
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
      const hospitalRootIds = collectHospitalRootIds(
        (initData.organizationOptions ?? []) as OrgTreeOption[],
      );
      const tree = (initData.executionOrgOptions ?? []) as OrgTreeOption[];
      // 后端已过滤科室；前端再排除开立树医院根，避免脏数据把院名塞进下拉
      allDepartmentOptionsCache = flattenOrgTree(tree, hospitalRootIds);
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
