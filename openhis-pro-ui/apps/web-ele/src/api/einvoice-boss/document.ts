/**
 * 博思电子票据 — 单据/金额调整 API
 */
import { publicRequestClient } from '#/api/request';

const DOCUMENT_BASE = '/einvoiceBossDocument';
const LIST_BASE = '/einvoiceBossList';
const COMPENSATION_BASE = '/einvoiceBossCompensation';

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
  writeOffStatus?: string;
  writeOffStatusLabel?: string;
  writeOffBillNo?: string;
  writeOffAt?: string;
  writeOffMessage?: string;
  writeOffBatchCode?: string;
  task?: BossTaskSummaryDto;
  lines?: BossLineDto[];
  adjustLogs?: BossAdjustLogDto[];
  /** 非阻断警告码（如支付额与开票总额不一致）。 */
  warningCode?: string;
  /** 警告说明。 */
  warningMessage?: string;
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
  writeOffStatus?: string;
  writeOffStatusLabel?: string;
  writeOffBillNo?: string;
  writeOffAt?: string;
  writeOffMessage?: string;
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
  /** 关键词过滤命中上限，结果可能不完整。 */
  keywordTruncated?: boolean;
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
  return publicRequestClient.post<AccountStatusResponse>(`${LIST_BASE}/getAccountStatus`, {
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
  return publicRequestClient.post<PageDocumentsResponse>(`${LIST_BASE}/pageDocuments`, request);
}

export function issueNowApi(invoiceId: string) {
  return publicRequestClient.post<{ billNo?: string; success?: boolean; message?: string }>(
    `${COMPENSATION_BASE}/issueNow`,
    { invoiceId },
  );
}
