/**
 * 住院用药明细（住院患者药品使用）—
 * POST /api/app/inpatientPharmacyPatientMedicationUsage/{methodName}
 */
import { reportPost } from '#/constants/report-request-timeout';
import type { PageParams } from '#/types/api-response';

import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/inpatientPharmacyPatientMedicationUsage';

/** 菜单路由 path（与 whale_menus.path 对齐）。 */
export const INPATIENT_MEDICATION_DETAIL_ROUTE_PATH = '/report/inpatient-medication-detail';

export const PRESENTATION_MODE_DETAIL = 'DETAIL' as const;
export const PRESENTATION_MODE_SUMMARY = 'SUMMARY' as const;
export type InpatientMedicationPresentationMode =
  | typeof PRESENTATION_MODE_DETAIL
  | typeof PRESENTATION_MODE_SUMMARY;

export interface OptionDto {
  value?: string;
  label?: string;
}

export interface InpatientMedicationPresentationOptionDto {
  code?: string;
  display?: string;
}

export interface InpatientMedicationStatusOptionDto {
  code?: string;
  display?: string;
}

export interface LoadInpatientMedicationDetailPageInitResponse {
  dateFrom?: string;
  dateTo?: string;
  presentationOptions?: InpatientMedicationPresentationOptionDto[];
  usageStatusOptions?: InpatientMedicationStatusOptionDto[];
}

export interface PageInpatientMedicationPatientsRequest extends PageParams {
  keyword?: string;
}

export interface InpatientMedicationPatientRowDto {
  encounterId?: string;
  patientId?: string;
  patientName?: string;
  inpatientNo?: string;
  wardName?: string;
  bedName?: string;
  statusEnum?: string;
}

export interface PageInpatientMedicationPatientsResponse {
  total?: number;
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
  records?: InpatientMedicationPatientRowDto[];
  content?: InpatientMedicationPatientRowDto[];
}

export interface QueryInpatientMedicationDetailRequest {
  encounterId: string;
  patientId: string;
  dateFrom: string;
  dateTo: string;
  presentationMode: InpatientMedicationPresentationMode;
}

export interface InpatientMedicationDetailLineDto {
  dispenseId?: string;
  medicationId?: string;
  itemName?: string;
  itemSpec?: string;
  quantityValue?: number | string;
  quantityUnitCode?: string;
  quantityUnitDisplay?: string;
  unitPrice?: number | string;
  totalPrice?: number | string;
  /** 计划发药时间（过滤/排序轴） */
  plannedDispenseTime?: string;
  /** 客户可见执行时间（Procedure.recorded_time） */
  recordedTime?: string;
  procedureId?: string;
  statusEnum?: string;
  usageStatusCode?: string;
  usageStatusDisplay?: string;
  dispenseEnum?: string;
  summaryNo?: string;
  lotNumber?: string;
}

export interface InpatientMedicationSummaryLineDto {
  medicationId?: string;
  itemName?: string;
  itemSpec?: string;
  unitPrice?: number | string;
  lineTotalAmount?: number | string;
  packageQuantity?: number | string;
  splitUnitQuantity?: number | string;
  packageUnitCode?: string;
  packageUnitDisplay?: string;
  splitUnitCode?: string;
  splitUnitDisplay?: string;
}

export interface InpatientMedicationTotalsDto {
  grandTotal?: number | string;
  lineCount?: number;
}

export interface QueryInpatientMedicationDetailResponse {
  presentationMode?: InpatientMedicationPresentationMode | string;
  detailLines?: InpatientMedicationDetailLineDto[];
  summaryLines?: InpatientMedicationSummaryLineDto[];
  totals?: InpatientMedicationTotalsDto;
}

/** 页初始化：默认日期与明细/汇总、发药状态值集。 */
export async function loadInpatientMedicationDetailPageInitApi() {
  return reportPost<LoadInpatientMedicationDetailPageInitResponse>(
    `${BASE}/loadInpatientPatientMedicationUsagePageInit`,
    {},
  );
}

/** 关键字分页检索住院患者候选。 */
export async function pageInpatientMedicationPatientsApi(
  body: PageInpatientMedicationPatientsRequest,
) {
  const res = await reportPost<PageInpatientMedicationPatientsResponse>(
    `${BASE}/pageInpatientPatientMedicationUsagePatients`,
    body,
  );
  const { content, totalCount } = parsePagedRowsFromApiData<InpatientMedicationPatientRowDto>(res);
  return {
    ...res,
    records: content.length > 0 ? content : (res.records ?? []),
    total: totalCount || Number(res.total ?? res.totalCount ?? 0),
  };
}

/** 按就诊 + 计划发药日区间查询明细或汇总。 */
export async function queryInpatientMedicationDetailApi(
  body: QueryInpatientMedicationDetailRequest,
) {
  return reportPost<QueryInpatientMedicationDetailResponse>(
    `${BASE}/queryInpatientPatientMedicationUsage`,
    body,
  );
}
