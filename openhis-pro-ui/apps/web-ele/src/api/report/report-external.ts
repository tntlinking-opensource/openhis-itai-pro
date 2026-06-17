/**
 * D 类外系统报告查看 — 对接 {@code POST /api/app/reportExternalView/<method>}
 */
import { publicRequestClient } from '#/api/request';

const BASE = '/reportExternalView';

export interface ReportExternalSourceDto {
  documentCode?: string;
  title?: string;
  scheme?: string;
}

export interface ReportExternalDocumentDto {
  documentCode?: string;
  title?: string;
  available?: boolean;
  contentType?: string;
  documentUrl?: string;
  message?: string;
  paramsEcho?: Record<string, unknown>;
}

export interface ReportExternalDocumentRequest {
  documentCode: string;
  params?: Record<string, unknown>;
}

export function listReportExternalSourcesApi(): Promise<ReportExternalSourceDto[]> {
  return publicRequestClient.post<ReportExternalSourceDto[]>(`${BASE}/listSources`, {});
}

export function resolveReportExternalDocumentApi(
  request: ReportExternalDocumentRequest,
): Promise<ReportExternalDocumentDto> {
  return publicRequestClient.post<ReportExternalDocumentDto>(`${BASE}/resolve`, request);
}
