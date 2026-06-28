/**
 * 博思电子票据 — 单据/金额调整 API
 */
import { publicRequestClient } from '#/api/request';

const DOCUMENT_BASE = '/einvoiceBossDocument';
const QUERY_BASE = '/einvoiceBossQuery';
const ISSUE_BASE = '/einvoiceBossIssue';

export interface BossDocumentBriefDto {
  documentId?: string;
  invoiceId?: string;
}

export interface BossLineDto {
  lineId?: string;
  lineNo?: number;
  chargeCode?: string;
  chargeName?: string;
  stdAmount?: number;
  amount?: number;
  adjusted?: boolean;
}

export interface BossTaskSummaryDto {
  taskId?: string;
  status?: string;
  attemptCount?: number;
  lastErrorCode?: string;
  lastErrorMsg?: string;
  nextRetryAt?: string;
}

export interface BossAdjustLogDto {
  id?: string;
  beforeTotal?: number;
  afterTotal?: number;
  reason?: string;
  createdAt?: string;
}

export interface BossDocumentDetailDto {
  documentId?: string;
  invoiceId?: string;
  paymentId?: string;
  issueKind?: string;
  issueKindLabel?: string;
  busNo?: string;
  totalAmt?: number;
  paymentAmt?: number;
  amountStatus?: string;
  patientName?: string;
  genderDisplay?: string;
  age?: string;
  encounterBusNo?: string;
  invoiceStatus?: string;
  bossBillNo?: string;
  bossBatchCode?: string;
  pictureUrl?: string;
  pictureNetUrl?: string;
  billQrCode?: string;
  issuedAt?: string;
  task?: BossTaskSummaryDto;
  lines?: BossLineDto[];
  adjustLogs?: BossAdjustLogDto[];
}

export interface BossAmountValidateResultDto {
  valid?: boolean;
  lineSum?: number;
  totalAmt?: number;
  diff?: number;
}

export interface SaveDocumentAdjustmentRequest {
  documentId: string;
  totalAmt?: number;
  reason: string;
  lines: Array<{
    lineId?: string;
    chargeCode?: string;
    chargeName?: string;
    stdAmount?: number;
    amount?: number;
    unit?: string;
    quantity?: number;
    newLine?: boolean;
  }>;
}

export interface BossDocumentListItemDto {
  documentId?: string;
  invoiceId?: string;
  paymentId?: string;
  patientName?: string;
  issueKind?: string;
  issueKindLabel?: string;
  paymentAmt?: number;
  bossBillNo?: string;
  invoiceStatus?: string;
  amountStatus?: string;
  busNo?: string;
  issuedAt?: string;
  createdAt?: string;
}

export interface PageDocumentsRequest {
  dateFrom?: string;
  dateTo?: string;
  keyword?: string;
  billNo?: string;
  invoiceStatus?: string;
  issueKind?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface PageDocumentsResponse {
  rows?: BossDocumentListItemDto[];
  total?: number;
}

export function getDocumentByIdApi(documentId: string) {
  return publicRequestClient.post<BossDocumentDetailDto>(`${DOCUMENT_BASE}/getByDocumentId`, {
    documentId,
  });
}

export interface AccountStatusResponse {
  billBatchCode?: string;
  billNo?: string;
  accountStatus?: string;
  accountStatusLabel?: string;
  accountDate?: string;
  bossSuccess?: boolean;
  bossResultCode?: string;
  bossMessage?: string;
}

export function getAccountStatusApi(documentId: string) {
  return publicRequestClient.post<AccountStatusResponse>(`${QUERY_BASE}/getAccountStatus`, {
    documentId,
  });
}

export function getDocumentBriefByPaymentIdApi(paymentId: string) {
  return publicRequestClient.post<BossDocumentBriefDto>(`${DOCUMENT_BASE}/getBriefByPaymentId`, {
    paymentId,
  });
}

export function validateDocumentAmountApi(documentId: string) {
  return publicRequestClient.post<BossAmountValidateResultDto>(`${DOCUMENT_BASE}/validateAmount`, {
    documentId,
  });
}

export function saveDocumentAdjustmentApi(request: SaveDocumentAdjustmentRequest) {
  return publicRequestClient.post<BossDocumentDetailDto>(
    `${DOCUMENT_BASE}/saveAdjustment`,
    request,
  );
}

export function pageDocumentsApi(request: PageDocumentsRequest) {
  return publicRequestClient.post<PageDocumentsResponse>(`${QUERY_BASE}/pageDocuments`, request);
}

export function issueNowApi(invoiceId: string) {
  return publicRequestClient.post<{ billNo?: string; success?: boolean; message?: string }>(
    `${ISSUE_BASE}/issueNow`,
    { invoiceId },
  );
}
