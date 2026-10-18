import { getCodeListApi, getConceptListApi } from '#/api/basic/code';
import { TerminologyCodeSystemName } from '#/constants/domain/terminology-code-system';

export interface ActivityTypeCodeOption {
  label: string;
  value: string;
}

interface CodeSystemRow {
  id?: string;
  name?: string;
}

interface ConceptRow {
  code?: string;
  display?: string;
  isActive?: boolean;
  name?: string;
  sortOrder?: number | string;
}

/** 从分页或数组响应中取出代码系统行列表 */
function unwrapCodeSystemRows(response: unknown): CodeSystemRow[] {
  if (Array.isArray(response)) {
    return response;
  }
  if (response && typeof response === 'object') {
    const obj = response as { content?: CodeSystemRow[]; data?: { content?: CodeSystemRow[] } };
    return obj.content ?? obj.data?.content ?? [];
  }
  return [];
}

/** 从概念列表响应中取出行 */
function unwrapConceptRows(response: unknown): ConceptRow[] {
  if (Array.isArray(response)) {
    return response;
  }
  if (response && typeof response === 'object') {
    const obj = response as { data?: ConceptRow[] };
    return obj.data ?? [];
  }
  return [];
}

/**
 * 加载诊疗业务类型字典（ter_code_system.name = activity_type_code）。
 *
 * @returns 启用态选项；按 sortOrder 升序
 */
export async function loadActivityTypeCodeOptions(): Promise<ActivityTypeCodeOption[]> {
  const response = await getCodeListApi({
    pageNo: 1,
    pageSize: 200,
    name: TerminologyCodeSystemName.ACTIVITY_TYPE_CODE,
  });
  const systems = unwrapCodeSystemRows(response);
  const system = systems.find((item) => item.name === TerminologyCodeSystemName.ACTIVITY_TYPE_CODE);
  if (!system?.id) {
    return [];
  }
  const conceptResponse = await getConceptListApi({ id: system.id });
  const concepts = unwrapConceptRows(conceptResponse);
  return concepts
    .filter((item) => item.isActive !== false)
    .map((item) => {
      const value = String(item.code ?? '').trim();
      const label = (item.display ?? item.name ?? value).trim() || value;
      const sortRaw = Number(item.sortOrder ?? 0);
      return {
        label,
        value,
        sort: Number.isFinite(sortRaw) ? sortRaw : 0,
      };
    })
    .filter((item) => item.value)
    .sort((a, b) => a.sort - b.sort || a.value.localeCompare(b.value))
    .map((item) => ({ label: item.label, value: item.value }));
}
