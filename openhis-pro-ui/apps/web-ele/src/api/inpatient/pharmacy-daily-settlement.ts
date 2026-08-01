/**
 * 住院药房日结 — POST /api/app/inpatientPharmacyDailySettlement/<method>
 */
import type { ApiOptionItem } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

import type {
  PharmacyDailySettlementCategoryRowDto,
  PharmacyDailySettlementDispenseItemDto,
  PharmacyDailySettlementHistoryItemDto,
  PharmacyDailySettlementProductRowDto,
} from '#/api/outpatient/pharmacy-daily-settlement';

export type {
  PharmacyDailySettlementCategoryRowDto,
  PharmacyDailySettlementDispenseItemDto,
  PharmacyDailySettlementHistoryItemDto,
  PharmacyDailySettlementProductRowDto,
} from '#/api/outpatient/pharmacy-daily-settlement';

const BASE = '/inpatientPharmacyDailySettlement';

export interface LoadInpatientPharmacyDailySettlementPageInitResponse {
  defaultDateFrom?: string;
  defaultDateTo?: string;
  pharmacyOptions?: ApiOptionItem[];
  /** 药师岗发药人下拉 */
  practitionerOptions?: ApiOptionItem[];
}

export interface PreviewInpatientPharmacyDailySettlementRequest {
  dateFrom: string;
  dateTo: string;
  locationId: string;
  /** 发药人软筛；空或不传表示不按人过滤 */
  practitionerIds?: string[];
}

export interface PreviewInpatientPharmacyDailySettlementResponse {
  pendingCount?: number;
  unsettledCount?: number;
  categoryRows?: PharmacyDailySettlementCategoryRowDto[];
  productRows?: PharmacyDailySettlementProductRowDto[];
  items?: PharmacyDailySettlementDispenseItemDto[];
  totalIncome?: string | number;
  gapHardBlockEarliestAt?: string;
  gapHardBlockMessage?: string;
  gapSoftWarnCount?: number;
  practitionerOptions?: ApiOptionItem[];
}

/** 归一化住院药房预览：待交笔数兼容 unsettledCount。 */
export function normalizePreviewInpatientPharmacyDailySettlementResponse(
  raw: PreviewInpatientPharmacyDailySettlementResponse,
): PreviewInpatientPharmacyDailySettlementResponse {
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

export interface SubmitInpatientPharmacyDailySettlementRequest {
  dateFrom: string;
  dateTo: string;
  locationId: string;
  /** 勾选发药行主键（与后端 selectedDispenseIds 对齐） */
  selectedDispenseIds: string[];
}

export interface SubmitInpatientPharmacyDailySettlementResponse {
  settlementId?: string;
  busNo?: string;
  paymentCount?: number;
  statusEnum?: string;
}

export interface WithdrawInpatientPharmacyDailySettlementRequest {
  settlementId: string;
}

export interface PageInpatientPharmacyDailySettlementHistoryRequest {
  submittedFrom: string;
  submittedTo: string;
  pageIndex?: number;
  pageSize?: number;
}

export interface PageInpatientPharmacyDailySettlementHistoryResponse {
  items?: PharmacyDailySettlementHistoryItemDto[];
  total?: number;
  pageIndex?: number;
  pageSize?: number;
}

/** 加载住院药房日结页初始化数据。 */
export async function loadInpatientPharmacyDailySettlementPageInitApi() {
  return publicRequestClient.post<LoadInpatientPharmacyDailySettlementPageInitResponse>(
    `${BASE}/loadInpatientPharmacyDailySettlementPageInit`,
    {},
  );
}

/** 预览住院药房日结汇总与待交明细。 */
export async function previewInpatientPharmacyDailySettlementApi(
  body: PreviewInpatientPharmacyDailySettlementRequest,
) {
  return publicRequestClient.post<PreviewInpatientPharmacyDailySettlementResponse>(
    `${BASE}/previewInpatientPharmacyDailySettlement`,
    body,
  );
}

/** 提交住院药房日结。 */
export async function submitInpatientPharmacyDailySettlementApi(
  body: SubmitInpatientPharmacyDailySettlementRequest,
) {
  return publicRequestClient.post<SubmitInpatientPharmacyDailySettlementResponse>(
    `${BASE}/submitInpatientPharmacyDailySettlement`,
    body,
  );
}

/** 撤回待审住院药房日结。 */
export async function withdrawInpatientPharmacyDailySettlementApi(
  body: WithdrawInpatientPharmacyDailySettlementRequest,
) {
  return publicRequestClient.post<{ settlementId?: string; statusEnum?: string }>(
    `${BASE}/withdrawInpatientPharmacyDailySettlement`,
    body,
  );
}

/** 住院药房交账历史分页。 */
export async function pageInpatientPharmacyDailySettlementHistoryApi(
  body: PageInpatientPharmacyDailySettlementHistoryRequest,
) {
  return publicRequestClient.post<PageInpatientPharmacyDailySettlementHistoryResponse>(
    `${BASE}/pageInpatientPharmacyDailySettlementHistory`,
    body,
  );
}
