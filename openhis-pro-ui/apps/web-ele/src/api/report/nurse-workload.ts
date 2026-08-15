/**
 * 护士工作量统计 — POST /api/app/nurseWorkloadReport/{methodName}
 */
import type { PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { reportLongRequestConfig } from '#/constants/report-request-timeout';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/nurseWorkloadReport';

export const ENCOUNTER_SCOPE_OUTPATIENT = 'OUTPATIENT' as const;
export const ENCOUNTER_SCOPE_INPATIENT = 'INPATIENT' as const;
export type EncounterScope = typeof ENCOUNTER_SCOPE_INPATIENT | typeof ENCOUNTER_SCOPE_OUTPATIENT;

export const NURSE_MAIN_TAB_EXECUTION = 'EXECUTION' as const;
export const NURSE_MAIN_TAB_CONSUMABLE = 'CONSUMABLE' as const;
export type NurseMainTabCode = typeof NURSE_MAIN_TAB_CONSUMABLE | typeof NURSE_MAIN_TAB_EXECUTION;

export interface OptionDto {
  value?: string;
  label?: string;
}

export interface LoadNurseWorkloadFormInitRequest {
  /** 就诊范围；切换门诊/住院时用于刷新开立科室下拉。 */
  encounterScope?: EncounterScope;
}

export interface LoadNurseWorkloadFormInitResponse {
  defaultEncounterScope?: EncounterScope;
  defaultDateFrom?: string;
  defaultDateTo?: string;
  encounterScopeOptions?: OptionDto[];
  nurseOptions?: OptionDto[];
  organizationOptions?: OptionDto[];
}

export interface NurseWorkloadQueryFilter {
  encounterScope: EncounterScope;
  dateFrom: string;
  dateTo: string;
  /** 开立科室（ChargeItem.requesting_org_id）。 */
  requestingOrgId?: string;
  /** 遗留字段；与 requestingOrgId 同值提交。 */
  organizationId?: string;
  practitionerId?: string;
}

export interface NurseExecutionSummaryRowDto {
  categoryEnum?: string;
  categoryDisplay?: string;
  executionCount?: number;
}

export interface SummarizeNurseExecutionWorkloadResponse {
  rows?: NurseExecutionSummaryRowDto[];
  totalSaleAmount?: number | string;
  totalPurchaseAmount?: number | string | null;
  totalExecutionCount?: number;
}

export interface NurseExecutionDetailRowDto {
  saleAmount?: number | string;
  purchaseAmount?: number | string | null;
  procedureId?: string;
  serviceRequestId?: string;
  itemName?: string;
  categoryEnum?: string;
  categoryDisplay?: string;
  generateSourceEnum?: string;
  generateSourceDisplay?: string;
  practitionerId?: string;
  practitionerName?: string;
  recordedTime?: string;
  encounterId?: string;
  encounterBusNo?: string;
  patientId?: string;
  patientName?: string;
}

export interface PageNurseExecutionWorkloadDetailRequest
  extends NurseWorkloadQueryFilter,
    PageParams {}

export interface PageNurseExecutionWorkloadDetailResponse {
  total?: number;
  pageNo?: number;
  pageSize?: number;
  rows?: NurseExecutionDetailRowDto[];
}

export interface NurseConsumableSummaryRowDto {
  hvcmFlag?: boolean | null;
  hvcmDisplay?: string;
  quantityTotal?: number | string;
}

export interface SummarizeNurseConsumableWorkloadResponse {
  rows?: NurseConsumableSummaryRowDto[];
  totalSaleAmount?: number | string;
  totalPurchaseAmount?: number | string | null;
  totalQuantity?: number | string;
}

export interface NurseConsumableDetailRowDto {
  saleAmount?: number | string;
  purchaseAmount?: number | string | null;
  dispenseId?: string;
  deviceDefId?: string;
  deviceName?: string;
  specText?: string;
  hvcmFlag?: boolean | null;
  hvcmDisplay?: string;
  generateSourceEnum?: string;
  generateSourceDisplay?: string;
  practitionerId?: string;
  practitionerName?: string;
  businessTime?: string;
  quantity?: number | string;
  unitCode?: string;
  unitCodeLabel?: string;
  encounterId?: string;
  encounterBusNo?: string;
  patientId?: string;
  patientName?: string;
}

export interface PageNurseConsumableWorkloadDetailRequest
  extends NurseWorkloadQueryFilter,
    PageParams {}

export interface PageNurseConsumableWorkloadDetailResponse {
  total?: number;
  pageNo?: number;
  pageSize?: number;
  rows?: NurseConsumableDetailRowDto[];
}

export interface ExportNurseWorkloadDetailRequest extends NurseWorkloadQueryFilter {
  mainTabCode: NurseMainTabCode;
}

export interface ExportNurseWorkloadDetailResponse {
  fileName?: string;
  contentType?: string;
  fileBytes?: string;
  truncated?: boolean;
  exportedRowCount?: number;
  mainTabCode?: NurseMainTabCode;
}

export interface NurseConsumableItemChipDto {
  key?: string;
  label?: string;
  quantity?: number | string;
}

export interface NurseWorkloadNurseCardDto {
  practitionerId?: string;
  executionTotalCount?: number;
  executionSaleAmountTotal?: number | string;
  executionPurchaseAmountTotal?: number | string | null;
  executionSummaryRows?: NurseExecutionSummaryRowDto[];
  consumableTotalQuantity?: number | string;
  consumableSaleAmountTotal?: number | string;
  consumablePurchaseAmountTotal?: number | string | null;
  consumableSummaryRows?: NurseConsumableSummaryRowDto[];
  consumableItemChips?: NurseConsumableItemChipDto[];
}

export interface ListNurseWorkloadNurseCardsResponse {
  cards?: NurseWorkloadNurseCardDto[];
}

/** 页面初始化：就诊范围、开立科室、护士下拉与默认日期。 */
export async function loadNurseWorkloadFormInitApi(body?: LoadNurseWorkloadFormInitRequest) {
  return publicRequestClient.post<LoadNurseWorkloadFormInitResponse>(
    `${BASE}/loadNurseWorkloadFormInit`,
    body ?? {},
  );
}

/** 护士卡片墙批量查询（一次扫库按人分桶；轻量 enrich）。 */
export async function listNurseWorkloadNurseCardsApi(body: NurseWorkloadQueryFilter) {
  return publicRequestClient.post<ListNurseWorkloadNurseCardsResponse>(
    `${BASE}/listNurseWorkloadNurseCards`,
    body,
    reportLongRequestConfig(),
  );
}

/** 诊疗执行汇总。 */
export async function summarizeNurseExecutionWorkloadApi(body: NurseWorkloadQueryFilter) {
  return publicRequestClient.post<SummarizeNurseExecutionWorkloadResponse>(
    `${BASE}/summarizeNurseExecutionWorkload`,
    body,
    reportLongRequestConfig(),
  );
}

/** 诊疗执行明细分页。 */
export async function pageNurseExecutionWorkloadDetailApi(
  body: PageNurseExecutionWorkloadDetailRequest,
) {
  const res = await publicRequestClient.post<PageNurseExecutionWorkloadDetailResponse>(
    `${BASE}/pageNurseExecutionWorkloadDetail`,
    body,
    reportLongRequestConfig(),
  );
  const { content, totalCount } = parsePagedRowsFromApiData<NurseExecutionDetailRowDto>(res);
  return {
    ...res,
    rows: content.length > 0 ? content : (res.rows ?? []),
    total: totalCount || res.total || 0,
  };
}

/** 耗材使用汇总。 */
export async function summarizeNurseConsumableWorkloadApi(body: NurseWorkloadQueryFilter) {
  return publicRequestClient.post<SummarizeNurseConsumableWorkloadResponse>(
    `${BASE}/summarizeNurseConsumableWorkload`,
    body,
    reportLongRequestConfig(),
  );
}

/** 耗材使用明细分页。 */
export async function pageNurseConsumableWorkloadDetailApi(
  body: PageNurseConsumableWorkloadDetailRequest,
) {
  const res = await publicRequestClient.post<PageNurseConsumableWorkloadDetailResponse>(
    `${BASE}/pageNurseConsumableWorkloadDetail`,
    body,
    reportLongRequestConfig(),
  );
  const { content, totalCount } = parsePagedRowsFromApiData<NurseConsumableDetailRowDto>(res);
  return {
    ...res,
    rows: content.length > 0 ? content : (res.rows ?? []),
    total: totalCount || res.total || 0,
  };
}

/** 导出当前主 Tab 明细 Excel。 */
export async function exportNurseWorkloadDetailApi(body: ExportNurseWorkloadDetailRequest) {
  return publicRequestClient.post<ExportNurseWorkloadDetailResponse>(
    `${BASE}/exportNurseWorkloadDetail`,
    body,
    reportLongRequestConfig(),
  );
}
