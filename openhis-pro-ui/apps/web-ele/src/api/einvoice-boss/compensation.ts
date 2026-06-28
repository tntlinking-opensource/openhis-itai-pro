/**
 * 博思电子票据 — 待补偿 API（POST /api/app/einvoiceBossQuery|einvoiceBossIssue/*）
 */
import { publicRequestClient } from '#/api/request';

const QUERY_BASE = '/einvoiceBossQuery';
const ISSUE_BASE = '/einvoiceBossIssue';

export type CompensationExceptionType = 'LOST' | 'AMOUNT' | 'FAILED' | 'STALE';

export interface BossPendingCompensationStatsDto {
  pendingCount?: number;
  lostCount?: number;
  amountCount?: number;
  failedCount?: number;
  staleCount?: number;
}

export interface BossCompensationRowDto {
  invoiceId?: string;
  documentId?: string;
  paymentId?: string;
  patientName?: string;
  encounterBusNo?: string;
  issueKind?: string;
  issueKindLabel?: string;
  paymentAmt?: number;
  busNo?: string;
  exceptionType?: CompensationExceptionType;
  exceptionTypeLabel?: string;
  waitingMinutes?: number;
  taskStatus?: string;
  lastErrorCode?: string;
  lastErrorMsg?: string;
  canRequeue?: boolean;
  canIssueNow?: boolean;
  canRecoverByBusNo?: boolean;
  canAdjustAmount?: boolean;
}

export interface ListPendingCompensationRequest {
  dateFrom?: string;
  dateTo?: string;
  keyword?: string;
  exceptionType?: CompensationExceptionType | '';
  issueKind?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface ListPendingCompensationResponse {
  stats?: BossPendingCompensationStatsDto;
  rows?: BossCompensationRowDto[];
  total?: number;
}

export interface BossInvoiceIssueResult {
  billNo?: string;
  success?: boolean;
  message?: string;
}

function toInvoiceIdBody(invoiceId: string) {
  return { invoiceId };
}

export function fetchPendingCompensationStatsApi() {
  return publicRequestClient.post<BossPendingCompensationStatsDto>(
    `${QUERY_BASE}/getPendingCompensationStats`,
    {},
  );
}

export function listPendingCompensationApi(request: ListPendingCompensationRequest) {
  return publicRequestClient.post<ListPendingCompensationResponse>(
    `${QUERY_BASE}/listPendingCompensation`,
    request,
  );
}

export function compensateRequeueApi(invoiceId: string) {
  return publicRequestClient.post<void>(
    `${ISSUE_BASE}/compensateRequeue`,
    toInvoiceIdBody(invoiceId),
  );
}

export function issueNowApi(invoiceId: string) {
  return publicRequestClient.post<BossInvoiceIssueResult>(
    `${ISSUE_BASE}/issueNow`,
    toInvoiceIdBody(invoiceId),
  );
}

export function recoverByBusNoApi(invoiceId: string) {
  return publicRequestClient.post<BossInvoiceIssueResult | null>(
    `${ISSUE_BASE}/recoverByBusNo`,
    toInvoiceIdBody(invoiceId),
  );
}

export interface BatchCompensateItemDto {
  invoiceId?: string;
  success?: boolean;
  message?: string;
  billNo?: string;
}

export interface BatchCompensateResultDto {
  successCount?: number;
  failCount?: number;
  items?: BatchCompensateItemDto[];
}

export function batchCompensateRequeueApi(invoiceIds: string[]) {
  return publicRequestClient.post<BatchCompensateResultDto>(
    `/einvoiceBossBatchIssue/batchCompensateRequeue`,
    {
      invoiceIds,
    },
  );
}

export function batchIssueNowApi(invoiceIds: string[]) {
  return publicRequestClient.post<BatchCompensateResultDto>(
    `/einvoiceBossBatchIssue/batchIssueNow`,
    {
      invoiceIds,
    },
  );
}
