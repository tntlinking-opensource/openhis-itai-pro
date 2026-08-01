/**
 * 团检批量收费 — {@code POST /api/app/physicalExamGroupCharge/<method>}
 */
import { publicRequestClient } from '#/api/request';

import type { IdLike } from './catalog-config';
import type { GroupRosterRowDto } from './group-check';

export interface PageGroupChargeRosterRequest {
  batchId: IdLike;
  collegeId?: IdLike;
  className?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageGroupChargeRosterResponse {
  content?: GroupRosterRowDto[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}

export interface PreSettleBatchRequest {
  rosterIds: IdLike[];
}

export interface PreSettleBatchResponse {
  estimatedCount?: number;
  estimatedAmount?: number;
}

export interface ConfirmGroupBatchSettlementRequest {
  rosterIds: IdLike[];
  orgId?: IdLike;
}

export interface GroupChargeFailureRowDto {
  rosterId?: IdLike;
  message?: string;
}

export interface ConfirmGroupBatchSettlementResponse {
  successCount?: number;
  failures?: GroupChargeFailureRowDto[];
}

export interface RefreshRosterChargeStatusRequest {
  rosterId: IdLike;
}

export interface RosterChargeStatusResponse {
  rosterId?: IdLike;
  payStatusCode?: string;
  encounterId?: IdLike;
  paid?: boolean;
}

export interface GroupChargePageInitResponse {
  paymentChannelOptions?: string[];
}

export function loadPageInitApi() {
  return publicRequestClient.post<GroupChargePageInitResponse>(
    '/physicalExamGroupCharge/loadPageInit',
    {},
  );
}

export function pageUnpaidRosterApi(params: PageGroupChargeRosterRequest) {
  return publicRequestClient.post<PageGroupChargeRosterResponse>(
    '/physicalExamGroupCharge/pageUnpaidRoster',
    params,
  );
}

export function pagePaidRosterApi(params: PageGroupChargeRosterRequest) {
  return publicRequestClient.post<PageGroupChargeRosterResponse>(
    '/physicalExamGroupCharge/pagePaidRoster',
    params,
  );
}

export function preSettleBatchApi(params: PreSettleBatchRequest) {
  return publicRequestClient.post<PreSettleBatchResponse>(
    '/physicalExamGroupCharge/preSettleBatch',
    params,
  );
}

export function confirmBatchSettlementApi(params: ConfirmGroupBatchSettlementRequest) {
  return publicRequestClient.post<ConfirmGroupBatchSettlementResponse>(
    '/physicalExamGroupCharge/confirmBatchSettlement',
    params,
  );
}

export function refreshRosterChargeStatusApi(params: RefreshRosterChargeStatusRequest) {
  return publicRequestClient.post<RosterChargeStatusResponse>(
    '/physicalExamGroupCharge/refreshRosterChargeStatus',
    params,
  );
}
