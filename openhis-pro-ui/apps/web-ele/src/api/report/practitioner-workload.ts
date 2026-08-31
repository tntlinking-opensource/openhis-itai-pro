/**
 * 科室/医生工作量统计 — POST /api/app/practitionerWorkload/{methodName}
 */
import { reportPost } from '#/constants/report-request-timeout';
import type { PageParams } from '#/types/api-response';
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';

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
  requestingOrgId?: string;
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
  /** 开立科室（ChargeItem.requesting_org_id）。 */
  requestingOrgId?: string;
  /** 遗留字段；与 requestingOrgId 同值提交。 */
  organizationId?: string;
  practitionerId?: string;
}

export interface PractitionerWorkloadFinTypeRowDto {
  finTypeCode?: string;
  finTypeDisplay?: string;
  saleAmount?: number | string;
  purchaseAmount?: number | string;
}

export interface PractitionerWorkloadRegistrationRowDto {
  serviceTypeId?: string;
  serviceTypeName?: string;
  netChargeCount?: number;
}

export interface PractitionerWorkloadSummaryTotalsDto {
  saleAmountTotal?: number | string;
  purchaseAmountTotal?: number | string;
  registrationNetCountTotal?: number;
  /** 接诊人数（distinct encounter）。 */
  encounterCountTotal?: number;
  /** 处方数量（仅药品；按药品请求 prescription_no 去重，同方多味计 1）。 */
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
  requestingOrgId?: string;
  requestingOrgName?: string;
  catalogTypeDisplay?: string;
  productName?: string;
  productCode?: string;
  specification?: string;
  practitionerId?: string;
  practitionerName?: string;
  enteredDate?: string;
  saleAmount?: number | string;
  purchaseAmount?: number | string;
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

export interface ExportPractitionerWorkloadDetailRequest extends PractitionerWorkloadQueryFilter {
  /** 明细表可见列 → 后端 Excel 列裁剪 */
  exportColumns?: ExcelExportColumnPayload[];
}

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
  return reportPost<LoadPractitionerWorkloadPageInitResponse>(
    `${BASE}/loadPractitionerWorkloadPageInit`,
    body ?? {},
  );
}

/** 工作量汇总查询。 */
export async function queryPractitionerWorkloadSummaryApi(body: PractitionerWorkloadQueryFilter) {
  return reportPost<QueryPractitionerWorkloadSummaryResponse>(
    `${BASE}/queryPractitionerWorkloadSummary`,
    body,
  );
}

export interface ListPractitionerWorkloadDoctorCardsRequest
  extends PractitionerWorkloadQueryFilter {
  /** 医生主键列表（须原样 string，禁止 Number）。 */
  practitionerIds: string[];
}

export interface PractitionerWorkloadDoctorCardDto {
  practitionerId?: string;
  /** 医生姓名（后端补齐；可含科室下拉外的开单/接诊医生）。 */
  practitionerName?: string;
  summaryTotals?: PractitionerWorkloadSummaryTotalsDto;
}

export interface ListPractitionerWorkloadDoctorCardsResponse {
  cards?: PractitionerWorkloadDoctorCardDto[];
}

/** 医生卡片墙批量查询（一次扫库按人分桶；卡片仅 summaryTotals）。 */
export async function listPractitionerWorkloadDoctorCardsApi(
  body: ListPractitionerWorkloadDoctorCardsRequest,
) {
  return reportPost<ListPractitionerWorkloadDoctorCardsResponse>(
    `${BASE}/listPractitionerWorkloadDoctorCards`,
    body,
  );
}

/** 工作量明细分页。 */
export async function pagePractitionerWorkloadDetailApi(
  body: PagePractitionerWorkloadDetailRequest,
) {
  const res = await reportPost<PagePractitionerWorkloadDetailResponse>(
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
  return reportPost<ExportPractitionerWorkloadDetailResponse>(
    `${BASE}/exportPractitionerWorkloadDetail`,
    body,
  );
}
