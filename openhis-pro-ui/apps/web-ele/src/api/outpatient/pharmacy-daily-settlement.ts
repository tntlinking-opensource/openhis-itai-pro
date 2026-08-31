/**
 * 门诊药房日结 — POST /api/app/outpatientPharmacyDailySettlement/<method>
 */
import type { ApiOptionItem } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { dailySettlementLongRequestConfig } from '#/constants/daily-settlement-request-timeout';

const BASE = '/outpatientPharmacyDailySettlement';

export interface LoadOutpatientPharmacyDailySettlementPageInitResponse {
  defaultDateFrom?: string;
  defaultDateTo?: string;
  pharmacyOptions?: ApiOptionItem[];
  /** 药师岗发药人下拉 */
  practitionerOptions?: ApiOptionItem[];
}

export interface PreviewOutpatientPharmacyDailySettlementRequest {
  dateFrom: string;
  dateTo: string;
  locationId: string;
  /** 发药人软筛；空或不传表示不按人过滤 */
  practitionerIds?: string[];
}

export interface PharmacyDailySettlementCategoryRowDto {
  categoryCode?: string;
  categoryLabel?: string;
  /** 大类主区不展示；接口可能仍回传 */
  netQuantity?: string | number;
  purchaseAmount?: string | number;
  saleAmount?: string | number;
}

export interface PharmacyDailySettlementProductRowDto {
  medicationId?: string;
  medicationName?: string;
  busNo?: string;
  specification?: string;
  manufacturer?: string;
  netQuantity?: string | number;
  /** 进价单价（同药分桶键） */
  purchaseUnitPrice?: string | number;
  /** 售价单价（同药分桶键） */
  saleUnitPrice?: string | number;
  purchaseAmount?: string | number;
  saleAmount?: string | number;
}

export interface PharmacyDailySettlementDispenseItemDto {
  dispenseId?: string;
  busNo?: string;
  statusEnum?: string;
  statusEnumText?: string;
  dispenseEnum?: string;
  dispenseEnumText?: string;
  medicationId?: string;
  medicationBusNo?: string;
  medicationName?: string;
  specification?: string;
  manufacturer?: string;
  unitCode?: string;
  unitText?: string;
  locationId?: string;
  practitionerId?: string;
  dispenseTime?: string;
  dispenseQuantity?: string | number;
  purchaseUnitPrice?: string | number;
  purchaseAmount?: string | number;
  saleUnitPrice?: string | number;
  saleAmount?: string | number;
}

export interface PreviewOutpatientPharmacyDailySettlementResponse {
  /** 待交笔数（一发一退各计 1；净额为 0 仍计） */
  pendingCount?: number;
  /** @deprecated 兼容旧字段，等同 pendingCount */
  unsettledCount?: number;
  categoryRows?: PharmacyDailySettlementCategoryRowDto[];
  productRows?: PharmacyDailySettlementProductRowDto[];
  items?: PharmacyDailySettlementDispenseItemDto[];
  /** 售价合计：各大类售价总额之和（与头表 total_income / 交账审批金额同口径） */
  totalIncome?: string | number;
  gapHardBlockEarliestAt?: string;
  gapHardBlockMessage?: string;
  gapSoftWarnCount?: number;
  practitionerOptions?: ApiOptionItem[];
}

/** 归一化预览：待交笔数兼容 unsettledCount，并以 items.length 兜底。 */
export function normalizePreviewOutpatientPharmacyDailySettlementResponse(
  raw: PreviewOutpatientPharmacyDailySettlementResponse,
): PreviewOutpatientPharmacyDailySettlementResponse {
  const pendingFromField = raw.pendingCount ?? raw.unsettledCount;
  const pendingFromItems = raw.items?.length;
  let pendingCount = pendingFromField;
  if (pendingCount == null && pendingFromItems != null) {
    pendingCount = pendingFromItems;
  }
  return {
    ...raw,
    pendingCount: pendingCount ?? 0,
  };
}

export interface SubmitOutpatientPharmacyDailySettlementRequest {
  dateFrom: string;
  dateTo: string;
  locationId: string;
  /** 勾选发药行主键（与后端 selectedDispenseIds 对齐） */
  selectedDispenseIds: string[];
}

export interface SubmitOutpatientPharmacyDailySettlementResponse {
  settlementId?: string;
  busNo?: string;
  paymentCount?: number;
  statusEnum?: string;
}

export interface WithdrawOutpatientPharmacyDailySettlementRequest {
  settlementId: string;
}

export interface PharmacyDailySettlementHistoryItemDto {
  settlementId?: string;
  busNo?: string;
  submittedAt?: string;
  dateFrom?: string;
  dateTo?: string;
  practitionerIds?: string;
  submitterId?: string;
  submitterName?: string;
  approverId?: string;
  approverName?: string;
  approvedAt?: string;
  orgId?: string;
  orgName?: string;
  locationId?: string;
  locationName?: string;
  paymentCount?: number;
  totalIncome?: string | number;
  statusEnum?: string;
  statusEnumText?: string;
}

export interface PageOutpatientPharmacyDailySettlementHistoryRequest {
  submittedFrom: string;
  submittedTo: string;
  pageIndex?: number;
  pageSize?: number;
}

export interface PageOutpatientPharmacyDailySettlementHistoryResponse {
  items?: PharmacyDailySettlementHistoryItemDto[];
  total?: number;
  pageIndex?: number;
  pageSize?: number;
}

/** 加载门诊药房日结页初始化数据。 */
export async function loadOutpatientPharmacyDailySettlementPageInitApi() {
  return publicRequestClient.post<LoadOutpatientPharmacyDailySettlementPageInitResponse>(
    `${BASE}/loadOutpatientPharmacyDailySettlementPageInit`,
    {},
  );
}

/** 预览门诊药房日结汇总与待交明细。 */
export async function previewOutpatientPharmacyDailySettlementApi(
  body: PreviewOutpatientPharmacyDailySettlementRequest,
) {
  return publicRequestClient.post<PreviewOutpatientPharmacyDailySettlementResponse>(
    `${BASE}/previewOutpatientPharmacyDailySettlement`,
    body,
    dailySettlementLongRequestConfig(),
  );
}

/** 提交门诊药房日结。 */
export async function submitOutpatientPharmacyDailySettlementApi(
  body: SubmitOutpatientPharmacyDailySettlementRequest,
) {
  return publicRequestClient.post<SubmitOutpatientPharmacyDailySettlementResponse>(
    `${BASE}/submitOutpatientPharmacyDailySettlement`,
    body,
    dailySettlementLongRequestConfig(),
  );
}

/** 撤回待审门诊药房日结。 */
export async function withdrawOutpatientPharmacyDailySettlementApi(
  body: WithdrawOutpatientPharmacyDailySettlementRequest,
) {
  return publicRequestClient.post<{ settlementId?: string; statusEnum?: string }>(
    `${BASE}/withdrawOutpatientPharmacyDailySettlement`,
    body,
  );
}

/** 门诊药房交账历史分页。 */
export async function pageOutpatientPharmacyDailySettlementHistoryApi(
  body: PageOutpatientPharmacyDailySettlementHistoryRequest,
) {
  return publicRequestClient.post<PageOutpatientPharmacyDailySettlementHistoryResponse>(
    `${BASE}/pageOutpatientPharmacyDailySettlementHistory`,
    body,
  );
}
