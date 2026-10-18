/**
 * 护士工作量统计 — POST /api/app/nurseWorkloadReport/{methodName}
 */
import { reportPost } from '#/constants/report-request-timeout';
import type { PageParams } from '#/types/api-response';
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';

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
  /** 就诊范围；切换门诊/住院时用于刷新默认登录科室下拉。 */
  encounterScope?: EncounterScope;
  /** 默认登录科室；已选时护士下拉仅返回该科人员。 */
  organizationId?: string;
}

export interface LoadNurseWorkloadFormInitResponse {
  defaultEncounterScope?: EncounterScope;
  defaultDateFrom?: string;
  defaultDateTo?: string;
  encounterScopeOptions?: OptionDto[];
  nurseOptions?: OptionDto[];
  /** 默认登录科室下拉（对应 Practitioner.org_id）。 */
  organizationOptions?: OptionDto[];
  /** 是否已配置有效折扣内容。 */
  discountFeatureEnabled?: boolean;
}

export interface NurseWorkloadQueryFilter {
  encounterScope: EncounterScope;
  dateFrom: string;
  dateTo: string;
  /**
   * 默认登录科室（Practitioner.org_id）。
   * 已选时统计该科下全部护士工作量。
   */
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
  totalDiscountAmount?: number | string;
  totalActualReceivedAmount?: number | string;
  totalPurchaseAmount?: number | string | null;
  totalExecutionCount?: number;
}

export interface NurseExecutionDetailRowDto {
  saleAmount?: number | string;
  discountAmount?: number | string;
  actualReceivedAmount?: number | string;
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
  totalDiscountAmount?: number | string;
  totalActualReceivedAmount?: number | string;
  totalPurchaseAmount?: number | string | null;
  totalQuantity?: number | string;
}

export interface NurseConsumableDetailRowDto {
  saleAmount?: number | string;
  discountAmount?: number | string;
  actualReceivedAmount?: number | string;
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
  /** 当前 Tab 明细可见列 → 后端 Excel 列裁剪 */
  exportColumns?: ExcelExportColumnPayload[];
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
  executionDiscountAmountTotal?: number | string;
  executionActualReceivedAmountTotal?: number | string;
  executionPurchaseAmountTotal?: number | string | null;
  executionSummaryRows?: NurseExecutionSummaryRowDto[];
  consumableTotalQuantity?: number | string;
  consumableSaleAmountTotal?: number | string;
  consumableDiscountAmountTotal?: number | string;
  consumableActualReceivedAmountTotal?: number | string;
  consumablePurchaseAmountTotal?: number | string | null;
  consumableSummaryRows?: NurseConsumableSummaryRowDto[];
  consumableItemChips?: NurseConsumableItemChipDto[];
}

export interface ListNurseWorkloadNurseCardsResponse {
  cards?: NurseWorkloadNurseCardDto[];
  /** 诊疗执行总次数（与汇总 IF 同口径）。 */
  totalExecutionCount?: number;
  /** 诊疗执行售价合计。 */
  totalExecutionSaleAmount?: number | string;
  totalExecutionDiscountAmount?: number | string;
  totalExecutionActualReceivedAmount?: number | string;
  /** 耗材总数量。 */
  totalConsumableQuantity?: number | string;
  /** 耗材售价合计。 */
  totalConsumableSaleAmount?: number | string;
  totalConsumableDiscountAmount?: number | string;
  totalConsumableActualReceivedAmount?: number | string;
  /** 耗材进价合计；全无进价时为 null。 */
  totalConsumablePurchaseAmount?: number | string | null;
}

/** 页面初始化：就诊范围、默认登录科室、护士下拉与默认日期。 */
export async function loadNurseWorkloadFormInitApi(body?: LoadNurseWorkloadFormInitRequest) {
  return reportPost<LoadNurseWorkloadFormInitResponse>(
    `${BASE}/loadNurseWorkloadFormInit`,
    body ?? {},
  );
}

/** 护士卡片墙批量查询（一次扫库按人分桶；轻量 enrich）。 */
export async function listNurseWorkloadNurseCardsApi(body: NurseWorkloadQueryFilter) {
  return reportPost<ListNurseWorkloadNurseCardsResponse>(
    `${BASE}/listNurseWorkloadNurseCards`,
    body,
  );
}

/** 诊疗执行汇总。 */
export async function summarizeNurseExecutionWorkloadApi(body: NurseWorkloadQueryFilter) {
  return reportPost<SummarizeNurseExecutionWorkloadResponse>(
    `${BASE}/summarizeNurseExecutionWorkload`,
    body,
  );
}

/** 诊疗执行明细分页。 */
export async function pageNurseExecutionWorkloadDetailApi(
  body: PageNurseExecutionWorkloadDetailRequest,
) {
  const res = await reportPost<PageNurseExecutionWorkloadDetailResponse>(
    `${BASE}/pageNurseExecutionWorkloadDetail`,
    body,
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
  return reportPost<SummarizeNurseConsumableWorkloadResponse>(
    `${BASE}/summarizeNurseConsumableWorkload`,
    body,
  );
}

/** 耗材使用明细分页。 */
export async function pageNurseConsumableWorkloadDetailApi(
  body: PageNurseConsumableWorkloadDetailRequest,
) {
  const res = await reportPost<PageNurseConsumableWorkloadDetailResponse>(
    `${BASE}/pageNurseConsumableWorkloadDetail`,
    body,
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
  return reportPost<ExportNurseWorkloadDetailResponse>(`${BASE}/exportNurseWorkloadDetail`, body);
}
