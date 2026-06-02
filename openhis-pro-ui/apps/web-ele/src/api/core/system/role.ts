import type { PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

export interface RoleListParams extends PageParams {
  keyword?: string;
}

export interface RoleInfo {
  id: number;
  name: string;
  displayName?: string;
  description?: string;
  isDefault?: boolean;
  isPublic?: boolean;
  createdAt?: string;
  updatedAt?: string;
  [key: string]: any;
}

export async function getRoleListApi(params: RoleListParams) {
  return publicRequestClient.post<PageApiResponse<RoleInfo>>('/role/getList', params);
}
