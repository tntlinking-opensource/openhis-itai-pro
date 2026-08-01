/**
 * 门诊挂号明细报表 — POST /api/app/outpatientRegistrationDetailReport/{methodName}
 * AS-RPT-007 IF-RPT-ORG-01～04
 */
import type { ApiOptionItem, PageParams } from '#/types/api-response';
import type { BinaryExportResult } from '#/utils/export/download-binary-export';

import { publicRequestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/outpatientRegistrationDetailReport';

/** 汇总视角：按科室。 */
export const SUMMARY_VIEW_DEPARTMENT = 'DEPARTMENT' as const;
/** 汇总视角：按医生。 */
export const SUMMARY_VIEW_DOCTOR = 'DOCTOR' as const;
export type SummaryViewCode = typeof SUMMARY_VIEW_DEPARTMENT | typeof SUMMARY_VIEW_DOCTOR;

/** 导出/展示 Tab：汇总。 */
export const PRESENTATION_MODE_SUMMARY = 'SUMMARY' as const;
/** 导出/展示 Tab：明细。 */
export const PRESENTATION_MODE_DETAIL = 'DETAIL' as const;
export type PresentationModeCode =
  | typeof PRESENTATION_MODE_DETAIL
  | typeof PRESENTATION_MODE_SUMMARY;

export interface LoadOutpatientRegistrationDetailReportPageInitResponse {
  defaultDateFrom?: string;
  defaultDateTo?: string;
  defaultPageSize?: number;
  defaultSummaryView?: SummaryViewCode | string;
  organizationOptions?: ApiOptionItem[];
  practitionerOptions?: ApiOptionItem[];
  serviceTypeOptions?: ApiOptionItem[];
  summaryViewOptions?: ApiOptionItem[];
}

export interface OutpatientRegistrationDetailQueryFilter {
  dateFrom: string;
  dateTo: string;
  organizationId?: string;
  practitionerId?: string;
  keyword?: string;
  summaryView?: SummaryViewCode | string;
}

export interface OutpatientRegistrationDetailSummaryRowDto {
  dimensionKey?: string;
  summaryDimension?: string;
  organizationId?: string;
  organizationName?: string;
  practitionerId?: string;
  practitionerName?: string;
  serviceTypeId?: string;
  serviceTypeName?: string;
  netRegistrationCount?: number;
}

export interface SummarizeOutpatientRegistrationDetailReportResponse {
  rows?: OutpatientRegistrationDetailSummaryRowDto[];
  totalNetRegistrationCount?: number;
}

export interface OutpatientRegistrationDetailRowDto {
  encounterId?: string;
  patientId?: string;
  patientName?: string;
  busNo?: string;
  organizationId?: string;
  organizationName?: string;
  registrationDoctorId?: string;
  registrationDoctorName?: string;
  serviceTypeId?: string;
  serviceTypeName?: string;
  statusEnum?: string;
  statusEnum_enumText?: string;
  registrationTime?: string;
  feeNatureContractNo?: string;
  feeNatureDisplay?: string;
  feeNatureCategoryCode?: string;
  insuranceTypeDisplay?: string;
  registrationFeeAmount?: number | string;
}

export interface PageOutpatientRegistrationDetailReportDetailRequest
  extends OutpatientRegistrationDetailQueryFilter,
    PageParams {}

export interface PageOutpatientRegistrationDetailReportDetailResponse {
  content?: OutpatientRegistrationDetailRowDto[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}

export interface ExportOutpatientRegistrationDetailReportRequest
  extends OutpatientRegistrationDetailQueryFilter {
  presentationMode: PresentationModeCode | string;
}

export type ExportOutpatientRegistrationDetailReportResponse = BinaryExportResult;

/** IF-01：页初始化。 */
export async function loadOutpatientRegistrationDetailReportPageInitApi() {
  return publicRequestClient.post<LoadOutpatientRegistrationDetailReportPageInitResponse>(
    `${BASE}/loadOutpatientRegistrationDetailReportPageInit`,
    {},
  );
}

/** IF-02：净汇总。 */
export async function summarizeOutpatientRegistrationDetailReportApi(
  body: OutpatientRegistrationDetailQueryFilter,
) {
  return publicRequestClient.post<SummarizeOutpatientRegistrationDetailReportResponse>(
    `${BASE}/summarizeOutpatientRegistrationDetailReport`,
    body,
  );
}

/** IF-03：明细分页。 */
export async function pageOutpatientRegistrationDetailReportDetailApi(
  body: PageOutpatientRegistrationDetailReportDetailRequest,
) {
  const res = await publicRequestClient.post<PageOutpatientRegistrationDetailReportDetailResponse>(
    `${BASE}/pageOutpatientRegistrationDetailReportDetail`,
    body,
  );
  const { content, totalCount } =
    parsePagedRowsFromApiData<OutpatientRegistrationDetailRowDto>(res);
  return {
    ...res,
    content: content.length > 0 ? content : (res.content ?? []),
    totalCount: totalCount || res.totalCount || 0,
  };
}

/** IF-04：导出当前 Tab。 */
export async function exportOutpatientRegistrationDetailReportApi(
  body: ExportOutpatientRegistrationDetailReportRequest,
) {
  return publicRequestClient.post<ExportOutpatientRegistrationDetailReportResponse>(
    `${BASE}/exportOutpatientRegistrationDetailReport`,
    body,
  );
}
