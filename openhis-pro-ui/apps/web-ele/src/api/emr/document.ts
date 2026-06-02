/**
 * 文书 API（whale-emr DocumentAppService）
 */

import type { PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

/** 文书正文：后端 content 为 JSON 字符串 */
export function serializeEmrDocumentContent(content: unknown): string {
  if (content === undefined || content === null) return '{}';
  if (typeof content === 'string') return content;
  return JSON.stringify(content);
}

export interface DocumentContentDto {
  id?: number | string;
  patientId?: string;
  encounterId?: string;
  documentTypeCode?: string;
  title?: string;
  status?: string;
  content?: Record<string, unknown> | string | null;
  readOnly?: boolean;
  metadataValues?: Record<string, string>;
}

export interface GetDocumentContentParams {
  /** 雪花 ID 须用字符串传参 */
  documentId?: string;
  patientId?: string;
  encounterId?: string;
  documentTypeCode?: string;
}

export interface CreateDocumentParams {
  patientId: string;
  encounterId?: string;
  documentTypeCode: string;
  /** 雪花 ID 须用字符串传参 */
  templateId?: string;
}

export interface SaveDocumentParams {
  /** 雪花 ID 须用字符串传参 */
  documentId: string;
  content: unknown;
  saveVersion?: boolean;
}

export interface SaveDocumentResult {
  documentId?: number | string;
  newVersionNumber?: number;
}

/** 文书列表项（摘要） */
export interface DocumentSummaryDto {
  id?: number | string;
  patientId?: string;
  encounterId?: string;
  documentTypeCode?: string;
  documentTypeName?: string;
  title?: string;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: number | string;
  updatedBy?: number | string;
}

export interface QueryDocumentParams extends PageParams {
  patientId: string;
  encounterId?: string;
  documentTypeCode?: string;
  status?: string;
  keyword?: string;
}

export async function getDocumentContentApi(params: GetDocumentContentParams) {
  return publicRequestClient.post<DocumentContentDto>('/document/getContent', params);
}

export async function createDocumentApi(params: CreateDocumentParams) {
  return publicRequestClient.post<DocumentContentDto>('/document/create', params);
}

export async function saveDocumentApi(params: SaveDocumentParams) {
  return publicRequestClient.post<SaveDocumentResult>('/document/save', {
    documentId: params.documentId,
    content: serializeEmrDocumentContent(params.content),
    saveVersion: params.saveVersion,
  });
}

export async function getDocumentListApi(params: QueryDocumentParams) {
  return publicRequestClient.post<PageApiResponse<DocumentSummaryDto>>('/document/getList', params);
}
