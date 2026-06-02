/**
 * 文书类型 API（whale-emr DocumentTypeAppService）
 */

import { publicRequestClient } from '#/api/request';

/** 文书类型 */
export interface DocumentTypeDto {
  id?: number | string;
  code?: string;
  name?: string;
  defaultTemplateId?: number | string;
  defaultTemplateName?: string;
  isEnabled?: boolean;
  sortOrder?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface QueryDocumentTypeParams {
  enabled?: boolean;
  keyword?: string;
}

export interface GetDocumentTypeParams {
  /** 雪花 ID 须用字符串传参，避免 JS Number 精度丢失 */
  id: string;
}

export interface CreateDocumentTypeParams {
  code: string;
  name: string;
  defaultTemplateId?: string;
  isEnabled?: boolean;
  sortOrder?: number;
}

export interface UpdateDocumentTypeParams {
  /** 雪花 ID 须用字符串传参 */
  id: string;
  name?: string;
  defaultTemplateId?: string;
  clearDefaultTemplate?: boolean;
  isEnabled?: boolean;
  sortOrder?: number;
}

export interface DeleteDocumentTypeParams {
  /** 雪花 ID 须用字符串传参 */
  id: string;
}

export async function getDocumentTypeListApi(params?: QueryDocumentTypeParams) {
  return publicRequestClient.post<DocumentTypeDto[]>('/documentType/getList', params ?? {});
}

export async function getDocumentTypeApi(params: GetDocumentTypeParams) {
  return publicRequestClient.post<DocumentTypeDto>('/documentType/get', params);
}

export async function createDocumentTypeApi(params: CreateDocumentTypeParams) {
  return publicRequestClient.post<DocumentTypeDto>('/documentType/create', params);
}

export async function updateDocumentTypeApi(params: UpdateDocumentTypeParams) {
  return publicRequestClient.post<DocumentTypeDto>('/documentType/update', params);
}

export async function deleteDocumentTypeApi(params: DeleteDocumentTypeParams) {
  return publicRequestClient.post<void>('/documentType/delete', params);
}
