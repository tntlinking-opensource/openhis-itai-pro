/**
 * 外系统报告查看 — 对接 {@code POST /api/app/externalDocument/<method>}
 */
import { publicRequestClient } from '#/api/request';

const BASE = '/externalDocument';

export interface ExternalDocumentSourceDto {
  documentCode?: string;
  title?: string;
  scheme?: string;
}

export interface ExternalDocumentDto {
  documentCode?: string;
  title?: string;
  available?: boolean;
  contentType?: string;
  documentUrl?: string;
  message?: string;
  paramsEcho?: Record<string, unknown>;
}

export interface ExternalDocumentRequest {
  documentCode: string;
  params?: Record<string, unknown>;
}

export function listExternalDocumentSourcesApi(): Promise<ExternalDocumentSourceDto[]> {
  return publicRequestClient.post<ExternalDocumentSourceDto[]>(`${BASE}/listSources`, {});
}

export function resolveExternalDocumentApi(
  request: ExternalDocumentRequest,
): Promise<ExternalDocumentDto> {
  return publicRequestClient.post<ExternalDocumentDto>(`${BASE}/resolve`, request);
}
