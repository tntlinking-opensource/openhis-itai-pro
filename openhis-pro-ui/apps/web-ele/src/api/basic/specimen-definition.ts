import type { ApiOptionItem, ApiResponse, PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '../request';

/** 样本定义发布状态 code（对齐后端 PublicationStatus） */
export const SPECIMEN_STATUS_ENUM = {
  DRAFT: 'DRAFT',
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
  DISCARDED: 'DISCARDED',
} as const;

export interface SpecimenDefinitionItem {
  id?: string;
  busNo?: string;
  name?: string;
  pyStr?: string;
  wbStr?: string;
  typeEnum?: string;
  typeEnumText?: string;
  statusEnum?: string;
  statusEnumText?: string;
  updatedAt?: string;
}

export interface PageSpecimenDefinitionParams extends PageParams {
  keyword?: string;
  statusEnum?: string;
  typeEnum?: string;
}

export interface SpecimenDefinitionInitResponse {
  typeEnumOptions?: ApiOptionItem[];
  statusEnumOptions?: ApiOptionItem[];
}

export interface CreateSpecimenDefinitionParams {
  name: string;
  typeEnum: string;
  pyStr?: string;
  wbStr?: string;
}

export interface UpdateSpecimenDefinitionParams extends CreateSpecimenDefinitionParams {
  id: string;
}

/** 加载样本定义维护页下拉选项 */
export async function initSpecimenDefinitionApi() {
  return publicRequestClient.post<SpecimenDefinitionInitResponse>('/specimenDefinition/init');
}

/** 分页查询样本定义列表 */
export async function pageSpecimenDefinitionApi(params: PageSpecimenDefinitionParams) {
  return publicRequestClient.post<ApiResponse<PageApiResponse<SpecimenDefinitionItem>>>(
    '/specimenDefinition/page',
    params,
  );
}

/** 按主键查询样本定义详情 */
export async function getSpecimenDefinitionApi(id: string) {
  return publicRequestClient.post<SpecimenDefinitionItem>(
    '/specimenDefinition/getSpecimenDefinition',
    {
      id,
    },
  );
}

/** 新建样本定义 */
export async function createSpecimenDefinitionApi(params: CreateSpecimenDefinitionParams) {
  return publicRequestClient.post<SpecimenDefinitionItem>(
    '/specimenDefinition/createSpecimenDefinition',
    params,
  );
}

/** 维护样本定义 */
export async function updateSpecimenDefinitionApi(params: UpdateSpecimenDefinitionParams) {
  return publicRequestClient.post<SpecimenDefinitionItem>(
    '/specimenDefinition/updateSpecimenDefinition',
    params,
  );
}

/** 逻辑删除样本定义 */
export async function deleteSpecimenDefinitionApi(id: string) {
  return publicRequestClient.post<unknown>('/specimenDefinition/deleteSpecimenDefinition', { id });
}

/** 启用样本定义 */
export async function enableSpecimenDefinitionApi(id: string) {
  return publicRequestClient.post<SpecimenDefinitionItem>(
    '/specimenDefinition/enableSpecimenDefinition',
    { value: id },
  );
}

/** 停用样本定义 */
export async function disableSpecimenDefinitionApi(id: string) {
  return publicRequestClient.post<SpecimenDefinitionItem>(
    '/specimenDefinition/disableSpecimenDefinition',
    { value: id },
  );
}
