/**
 * 科室/医生工作量统计 — POST /api/app/practitionerWorkload/{methodName}
 */
import type { PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/practitionerWorkload';

export const ENCOUNTER_SCOPE_OUTPATIENT = 'OUTPATIENT' as const;
export const ENCOUNTER_SCOPE_INPATIENT = 'INPATIENT' as const;
export type EncounterScope = typeof ENCOUNTER_SCOPE_INPATIENT | typeof ENCOUNTER_SCOPE_OUTPATIENT;

export const WORKLOAD_TAB_SUMMARY = 'SUMMARY' as const;
export const WORKLOAD_TAB_DETAIL = 'DETAIL' as const;

export interface OptionDto {
  value?: string;
  label?: string;
}

export interface LoadPractitionerWorkloadPageInitRequest {
  encounterScope?: EncounterScope;
  organizationId?: string;
}

export interface LoadPractitionerWorkloadPageInitResponse {
  encounterScopeOptions?: OptionDto[];
  defaultEncounterScope?: EncounterScope;
  organizationOptions?: OptionDto[];
  practitionerOptions?: OptionDto[];
  defaultDateFrom?: string;
  defaultDateTo?: string;
  defaultActiveTab?: string;
}

export interface PractitionerWorkloadQueryFilter {
  encounterScope: EncounterScope;
  dateFrom: string;
  dateTo: string;
  organizationId?: string;
  practitionerId?: string;
}

export interface PractitionerWorkloadFinTypeRowDto {
  finTypeCode?: string;
  finTypeDisplay?: string;
  receivableAmount?: number | string;
  actualReceivedAmount?: number | string;
  costAmount?: number | string;
  netIncomeAmount?: number | string;
}

export interface PractitionerWorkloadRegistrationRowDto {
  serviceTypeId?: string;
  serviceTypeName?: string;
  netChargeCount?: number;
}

export interface PractitionerWorkloadSummaryTotalsDto {
  receivableAmountTotal?: number | string;
  actualReceivedAmountTotal?: number | string;
  costAmountTotal?: number | string;
  netIncomeAmountTotal?: number | string;
  registrationNetCountTotal?: number;
  /** 接诊人数（distinct encounter）。 */
  encounterCountTotal?: number;
  /** 处方数量（distinct 药品医嘱）。 */
  prescriptionCountTotal?: number;
  /** 诊疗项目总数量（distinct 诊疗 + 耗材医嘱）。 */
  activityItemCountTotal?: number;
}

export interface QueryPractitionerWorkloadSummaryResponse {
  encounterScope?: EncounterScope;
  organizationId?: string;
  organizationName?: string;
  finTypeRows?: PractitionerWorkloadFinTypeRowDto[];
  registrationRows?: PractitionerWorkloadRegistrationRowDto[];
  summaryTotals?: PractitionerWorkloadSummaryTotalsDto;
}

export interface PractitionerWorkloadDetailLineDto {
  chargeItemId?: string;
  paymentId?: string;
  requestingOrgId?: string;
  requestingOrgName?: string;
  catalogTypeDisplay?: string;
  productName?: string;
  productCode?: string;
  specification?: string;
  practitionerId?: string;
  practitionerName?: string;
  enteredDate?: string;
  receivableAmount?: number | string;
  actualReceivedAmount?: number | string;
  costAmount?: number | string;
}

export interface PagePractitionerWorkloadDetailRequest
  extends PageParams,
    PractitionerWorkloadQueryFilter {}

export interface PagePractitionerWorkloadDetailResponse {
  content?: PractitionerWorkloadDetailLineDto[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
  summaryTotals?: PractitionerWorkloadSummaryTotalsDto;
}

export type ExportPractitionerWorkloadDetailRequest = PractitionerWorkloadQueryFilter;

export interface ExportPractitionerWorkloadDetailResponse {
  fileName?: string;
  contentType?: string;
  body?: string;
  rowCount?: number;
  truncated?: boolean;
}

/** 页面初始化：就诊范围、科室/医生下拉与默认日期。 */
export async function loadPractitionerWorkloadPageInitApi(
  body?: LoadPractitionerWorkloadPageInitRequest,
) {
  return publicRequestClient.post<LoadPractitionerWorkloadPageInitResponse>(
    `${BASE}/loadPractitionerWorkloadPageInit`,
    body ?? {},
  );
}

/** 工作量汇总查询。 */
export async function queryPractitionerWorkloadSummaryApi(body: PractitionerWorkloadQueryFilter) {
  return publicRequestClient.post<QueryPractitionerWorkloadSummaryResponse>(
    `${BASE}/queryPractitionerWorkloadSummary`,
    body,
  );
}

/** 工作量明细分页。 */
export async function pagePractitionerWorkloadDetailApi(
  body: PagePractitionerWorkloadDetailRequest,
) {
  const res = await publicRequestClient.post<PagePractitionerWorkloadDetailResponse>(
    `${BASE}/pagePractitionerWorkloadDetail`,
    body,
  );
  const { content, totalCount } = parsePagedRowsFromApiData<PractitionerWorkloadDetailLineDto>(res);
  return {
    ...res,
    content,
    totalCount,
  };
}

/** 导出工作量明细 Excel。 */
export async function exportPractitionerWorkloadDetailApi(
  body: ExportPractitionerWorkloadDetailRequest,
) {
  return publicRequestClient.post<ExportPractitionerWorkloadDetailResponse>(
    `${BASE}/exportPractitionerWorkloadDetail`,
    body,
  );
}
