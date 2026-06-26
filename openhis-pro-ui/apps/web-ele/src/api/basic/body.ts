import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';

import { publicRequestClient } from '../request';

export interface BodyItem {
  id?: string;
  busNo?: string;
  name?: string;
  parentId?: string;
  parentName?: string;
  statusEnum?: string;
  statusEnumText?: string;
  pyStr?: string;
  wbStr?: string;
  children?: BodyItem[];
}

export interface CreateBodyParams {
  name: string;
  parentId?: string;
}

export interface UpdateBodyParams {
  id: string;
  name: string;
  parentId?: string;
}

export interface BodyTreeOption {
  label: string;
  value: string;
  sort?: number;
  busNo?: string;
  children?: BodyTreeOption[];
}

/** 查询身体部位平面列表 */
export async function getBodyListApi(): Promise<BodyItem[]> {
  const response = await publicRequestClient.post<BodyItem[]>('/body/getBodyList');
  return unwrapApiList<BodyItem>(response);
}

/** 查询身体部位树（上级部位下拉） */
export async function getBodyTreeApi(): Promise<BodyTreeOption[]> {
  const response = await publicRequestClient.post<BodyTreeOption[]>('/body/getBodyStructureTree');
  return unwrapApiList<BodyTreeOption>(response);
}

/** 按主键查询身体部位详情 */
export async function getBodyApi(id: string) {
  return publicRequestClient.post<BodyItem>('/body/getBody', { id });
}

/** 新建身体部位 */
export async function createBodyApi(params: CreateBodyParams) {
  const payload: Record<string, string> = {
    name: params.name.trim(),
  };
  const parentId = preserveApiIdForRequest(params.parentId);
  if (parentId) {
    payload.parentId = parentId;
  }
  return publicRequestClient.post<BodyItem>('/body/createBody', payload);
}

/** 更新身体部位 */
export async function updateBodyApi(params: UpdateBodyParams) {
  const payload: Record<string, string> = {
    id: String(params.id),
    name: params.name.trim(),
  };
  const parentId = preserveApiIdForRequest(params.parentId);
  if (parentId) {
    payload.parentId = parentId;
  }
  return publicRequestClient.post<BodyItem>('/body/updateBody', payload);
}

/** 删除身体部位 */
export async function deleteBodyApi(id: number | string) {
  return publicRequestClient.post('/body/deleteBody', { id });
}

/** 启用身体部位 */
export async function activeBodyApi(id: number | string) {
  return publicRequestClient.post<BodyItem>('/body/activeBody', { id });
}

/** 停用身体部位 */
export async function inactiveBodyApi(id: number | string) {
  return publicRequestClient.post<BodyItem>('/body/inactiveBodyStructure', { id });
}
