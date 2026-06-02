import { publicRequestClient } from '../request';

export interface CreateBodyParams {
  name: string;
  parentId?: string;
}

export interface UpdateBodyParams {
  id: string;
  name: string;
  parentId?: string;
}

export const getBodyListApi = async () => {
  const response = await publicRequestClient.post('/body/getBodyList');
  return response;
};

export const getBodyTreeApi = async () => {
  const response = await publicRequestClient.post('/body/getBodyTree');
  return response;
};

/**
 * 创建身体部位
 */
export const createBodyApi = async (params: CreateBodyParams) => {
  return publicRequestClient.post('/body/createBody', params);
};

/**
 * 更新身体部位
 */
export const updateBodyApi = async (params: UpdateBodyParams) => {
  return publicRequestClient.post('/body/updateBody', params);
};

export interface DeleteBodyParams {
  value: string;
}

/**
 * 删除身体部位
 */
export const deleteBodyApi = async (params: DeleteBodyParams) => {
  return publicRequestClient.post('/body/deleteBody', params);
};
