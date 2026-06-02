import { getCodeListApi, getConceptListApi } from '#/api/basic/code';

export interface OutWayCodeOption {
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

/** 加载出院方式字典（ter_code_system.name = out_way_code） */
export async function loadOutWayCodeOptions(): Promise<OutWayCodeOption[]> {
  const response = await getCodeListApi({ pageNo: 1, pageSize: 200, name: 'out_way_code' });
  const systems = unwrapCodeSystemRows(response);
  const system = systems.find((item) => item.name === 'out_way_code');
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
      return { label, value };
    })
    .filter((item) => item.value);
}
