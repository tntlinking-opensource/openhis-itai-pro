/**
 * 博思电子票据 — 手动冲红 API（POST /api/app/einvoiceBossWriteOff/*）
 */
import type { BossDocumentDetailDto, BossDocumentListItemDto } from '#/api/einvoice-boss/document';

import { publicRequestClient } from '#/api/request';

const WRITEOFF_BASE = '/einvoiceBossWriteOff';

export type WriteOffScope = 'CANDIDATE' | 'HISTORY';

export interface PageWriteOffDocumentsRequest {
  dateFrom?: string;
  dateTo?: string;
  keyword?: string;
  billNo?: string;
  issueKind?: string;
  scope?: WriteOffScope | '';
  pageNo?: number;
  pageSize?: number;
}

export interface PageWriteOffDocumentsResponse {
  rows?: BossDocumentListItemDto[];
  total?: number;
  keywordTruncated?: boolean;
}

export interface ManualWriteOffRequest {
  documentId?: string;
  invoiceId?: string;
  reason?: string;
}

export interface ManualWriteOffResponse {
  success?: boolean;
  message?: string;
  documentId?: string;
  invoiceId?: string;
  busNo?: string;
  writeOffBillNo?: string;
  writeOffBatchCode?: string;
}

export function pageWriteOffDocumentsApi(request: PageWriteOffDocumentsRequest) {
  return publicRequestClient.post<PageWriteOffDocumentsResponse>(
    `${WRITEOFF_BASE}/pageWriteOffDocuments`,
    request,
  );
}

export function getWriteOffDetailApi(documentId: string) {
  return publicRequestClient.post<BossDocumentDetailDto>(`${WRITEOFF_BASE}/getWriteOffDetail`, {
    documentId,
  });
}

export function manualWriteOffApi(request: ManualWriteOffRequest) {
  return publicRequestClient.post<ManualWriteOffResponse>(
    `${WRITEOFF_BASE}/manualWriteOff`,
    request,
  );
}
