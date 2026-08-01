/**
 * 博思电子票据 — 待补偿 API（POST /api/app/einvoiceBossCompensation/*）
 */
import { publicRequestClient } from '#/api/request';

const COMPENSATION_BASE = '/einvoiceBossCompensation';

export type CompensationExceptionType = 'LOST' | 'AMOUNT' | 'FAILED' | 'STALE';

export interface BossPendingCompensationStatsDto {
  pendingCount?: number;
  lostCount?: number;
  amountCount?: number;
  failedCount?: number;
  staleCount?: number;
  /** 扫描命中上限，统计/列表可能不完整。 */
  scanTruncated?: boolean;
}

export interface BossCompensationRowDto {
  invoiceId?: string;
  documentId?: string;
  paymentId?: string;
  patientName?: string;
  encounterBusNo?: string;
  issueKind?: string;
  issueKindLabel?: string;
  /** 开票金额（票面） */
  totalAmt?: number;
  /** 患者现金支付金额 */
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
    `${COMPENSATION_BASE}/getPendingCompensationStats`,
    {},
  );
}

export function listPendingCompensationApi(request: ListPendingCompensationRequest) {
  return publicRequestClient.post<ListPendingCompensationResponse>(
    `${COMPENSATION_BASE}/listPendingCompensation`,
    request,
  );
}

export function compensateRequeueApi(invoiceId: string) {
  return publicRequestClient.post<void>(
    `${COMPENSATION_BASE}/compensateRequeue`,
    toInvoiceIdBody(invoiceId),
  );
}

export function issueNowApi(invoiceId: string) {
  return publicRequestClient.post<BossInvoiceIssueResult>(
    `${COMPENSATION_BASE}/issueNow`,
    toInvoiceIdBody(invoiceId),
  );
}

export function recoverByBusNoApi(invoiceId: string) {
  return publicRequestClient.post<BossInvoiceIssueResult | null>(
    `${COMPENSATION_BASE}/recoverByBusNo`,
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
    `${COMPENSATION_BASE}/batchCompensateRequeue`,
    {
      invoiceIds,
    },
  );
}

export function batchIssueNowApi(invoiceIds: string[]) {
  return publicRequestClient.post<BatchCompensateResultDto>(`${COMPENSATION_BASE}/batchIssueNow`, {
    invoiceIds,
  });
}
