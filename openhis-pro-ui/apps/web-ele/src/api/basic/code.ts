import type { GetInfoParams, PageParams } from '#/types/api-response';

import { publicRequestClient } from '../request';

export interface CodeListParams extends PageParams {
  sortDirection?: string;
  name?: string;
  sortBy?: string;
  keyword?: string;
  status?: string;
}

export interface CodeListResponse {
  id: string;
  name: string;
  description: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  title: string;
  version: string;
}

export interface CreateCodeSystemParams {
  name: string;
  description: string;
  title: string;
  version: string;
}

export interface ConceptParams {
  display: string;
  definition: string;
  codeSystemId: string;
  code: string;
  sortOrder?: number;
  isActive?: boolean;
}

export interface EnumDictItem {
  value: string;
  label: string;
  children?: EnumDictItem[];
}

export interface EnumDictParams {
  enumName: string;
}
export const getCodeListApi = async (params: CodeListParams) => {
  return publicRequestClient.post<CodeListResponse[]>('/code/getCodeSystemList', params);
};

/**
 * 创建代码系统
 */
export const createCodeSystemApi = async (params: CreateCodeSystemParams) => {
  return publicRequestClient.post('/code/createCodeSystem', params);
};
/**
 * 更新代码系统
 */
export const updateCodeSystemApi = async (
  params: Omit<CreateCodeSystemParams, 'name'> & { id: string },
) => {
  return publicRequestClient.post('/code/updateCodeSystem', params);
};

export const getConceptListApi = async (params: GetInfoParams) => {
  // 后端入参为 Long（请求体直接传 id），不要传 { id: ... } 这种对象
  const id = (params as any)?.id ?? (params as any)?.value ?? params;
  // 兼容后端严格 Long 反序列化：尽量传 JSON number；超出 JS 安全整数则传 string
  const idStr = String(id);
  const idNum = Number(idStr);
  const payload =
    Number.isFinite(idNum) && Number.isSafeInteger(idNum) && String(idNum) === idStr
      ? idNum
      : idStr;
  return publicRequestClient.post('/code/getConceptList', payload);
};

export const updateConceptApi = async (params: ConceptParams & { id: string }) => {
  return publicRequestClient.post('/code/updateConcept', params);
};

export const createConceptApi = async (params: ConceptParams) => {
  return publicRequestClient.post('/code/addConcept', params);
};

export const deleteConceptApi = async (params: GetInfoParams) => {
  const id = (params as any)?.id ?? (params as any)?.value ?? params;
  const idStr = String(id);
  const idNum = Number(idStr);
  const payload =
    Number.isFinite(idNum) && Number.isSafeInteger(idNum) && String(idNum) === idStr
      ? idNum
      : idStr;
  return publicRequestClient.post('/code/deleteConcept', payload);
};

export const deleteCodeSystemApi = async (params: GetInfoParams) => {
  const id = (params as any)?.id ?? (params as any)?.value ?? params;
  const idStr = String(id);
  const idNum = Number(idStr);
  const payload =
    Number.isFinite(idNum) && Number.isSafeInteger(idNum) && String(idNum) === idStr
      ? idNum
      : idStr;
  return publicRequestClient.post('/code/deleteCodeSystem', payload);
};

export const getEnumDictListApi = async () => {
  return publicRequestClient.post<EnumDictItem[]>('/code/getEnumDictList');
};

export const getEnumConceptListApi = async (params: EnumDictParams) => {
  return publicRequestClient.post('/code/getEnumConceptList', params);
};

/** 清空术语概念字典缓存（SQL 灌数后无需重启即可让业务下拉回源） */
export const refreshConceptCacheApi = async () => {
  return publicRequestClient.post('/code/refreshConceptCache');
};
