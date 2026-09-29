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
  /** 重点药类型下拉（基药/监控/集采/抗生素）；品规销售排名筛选 */
  keyDrugTypeOptions?: ApiOptionItem[];
  /** 药品性质下拉（麻醉/精一/精二）；品规销售排名筛选 */
  medicinePropertyOptions?: ApiOptionItem[];
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
  /** 基药标识；品规销售排名按重点药类型筛选 */
  basicFlag?: boolean;
  /** 国家重点监控药品标识 */
  nationalMonitorFlag?: boolean;
  /** 集采药品标识 */
  centralPurchaseFlag?: boolean;
  /** 抗生素标识 */
  antibioticFlag?: boolean;
  /** 药品性质码（medicine_properties）；品规销售排名按麻/精筛选 */
  pharmacologyCategoryCode?: string;
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

/** 归一化预览：待交笔数优先 pendingCount，缺省回落 items.length。 */
export function normalizePreviewOutpatientPharmacyDailySettlementResponse(
  raw: PreviewOutpatientPharmacyDailySettlementResponse,
): PreviewOutpatientPharmacyDailySettlementResponse {
  const pendingFromField = raw.pendingCount;
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
