/**
 * 博思电子票据 — 财务日结核对 API
 */
import { publicRequestClient } from '#/api/request';

const RECONCILE_BASE = '/einvoiceBossReconcile';

export type ReconcileDateMode = 'BUS_DATE' | 'IVC_DATE';

export interface CheckDailyTotalRequest {
  checkDate?: string;
  dateMode?: ReconcileDateMode;
  issueKind?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface CheckDailyTotalResponse {
  checkDate?: string;
  dateMode?: ReconcileDateMode;
  issueKind?: string;
  localIssuedCount?: number;
  localIssuedAmount?: number;
  bossTotalCount?: number;
  bossTotalAmount?: number;
  bossSuccess?: boolean;
  bossResultCode?: string;
  bossMessage?: string;
  matched?: boolean;
  countDiff?: number;
}

export function checkDailyTotalApi(request: CheckDailyTotalRequest) {
  return publicRequestClient.post<CheckDailyTotalResponse>(
    `${RECONCILE_BASE}/checkDailyTotal`,
    request,
  );
}
