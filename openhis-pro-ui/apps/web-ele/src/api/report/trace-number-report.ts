/**
 * 追溯码查询报表 — POST /api/app/traceNumberReport/{methodName}
 */
import type { PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/traceNumberReport';

/** 菜单路由 path（与 whale_menus.path 对齐）。 */
export const TRACE_NUMBER_REPORT_ROUTE_PATH = '/report/trace-number';

export const TRACE_QUERY_SCOPE_HOSPITAL = 'HOSPITAL' as const;
export const TRACE_QUERY_SCOPE_LOCATION = 'LOCATION' as const;
export type TraceNumberReportQueryScope =
  | typeof TRACE_QUERY_SCOPE_HOSPITAL
  | typeof TRACE_QUERY_SCOPE_LOCATION;

export interface OptionDto {
  value?: string;
  label?: string;
}

export interface LoadTraceNumberReportFormInitResponse {
  queryScopeOptions?: OptionDto[];
  inHospitalStatusOptions?: OptionDto[];
  locationStockStatusOptions?: OptionDto[];
  locationOptions?: OptionDto[];
}

export interface TraceNumberReportFilter {
  queryScope: TraceNumberReportQueryScope;
  locationId?: string;
  inHospitalStatusEnum?: string;
  traceNoKeyword?: string;
  medicationKeyword?: string;
  lotNumberKeyword?: string;
  flowTimeStart?: string;
  flowTimeEnd?: string;
  traceNo?: string;
  openDetail?: boolean;
}

export interface PageTraceNumberReportRequest extends TraceNumberReportFilter, PageParams {}

export interface TraceNumberReportRowDto {
  traceNo?: string;
  medicationId?: string;
  medicationCode?: string;
  medicationName?: string;
  /** 规格（药品 totalVolume enrich）。 */
  specificationText?: string;
  lotNumber?: string;
  inHospitalStatusEnum?: string;
  inHospitalStatusLabel?: string;
  remainingSplitQuantity?: number | string;
  /** 追溯单位展示名（后端已将 code 转成术语 label）。 */
  traceUnitCode?: string;
  /** 当前所在库房名称（多个顿号拼接）。 */
  currentLocationNames?: string;
  locationNetSplitQuantity?: number | string;
  lastFlowAt?: string;
}

export interface PageTraceNumberReportResponse {
  total?: number;
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
  records?: TraceNumberReportRowDto[];
  content?: TraceNumberReportRowDto[];
  prefillTraceNo?: string;
  openDetail?: boolean;
}

export type ExportTraceNumberReportRequest = Omit<
  PageTraceNumberReportRequest,
  'pageNo' | 'pageSize'
>;

export interface ExportTraceNumberReportResponse {
  fileName?: string;
  contentType?: string;
  body?: string;
  exportedRowCount?: number;
  truncated?: boolean;
}

export interface GetTraceNumberOverviewRequest {
  traceNo: string;
}

export interface GetTraceNumberOverviewResponse {
  traceNo?: string;
  medicationId?: string;
  medicationCode?: string;
  medicationName?: string;
  specificationText?: string;
  lotNumber?: string;
  traceUnitCode?: string;
  remainingSplitQuantity?: number | string;
  inHospitalStatusEnum?: string;
  inHospitalStatusLabel?: string;
}

export interface ListTraceRecordsRequest {
  traceNo: string;
}

export interface TraceNumberRecordRowDto {
  id?: string;
  flowCategoryEnum?: string;
  flowCategoryLabel?: string;
  flowDirectionEnum?: string;
  flowDirectionLabel?: string;
  locationId?: string;
  locationName?: string;
  quantity?: number | string;
  unitCode?: string;
  unitCodeLabel?: string;
  operationType?: string;
  operationTypeLabel?: string;
  busNo?: string;
  createdAt?: string;
  patientName?: string;
}

export interface ListTraceRecordsResponse {
  traceNo?: string;
  records?: TraceNumberRecordRowDto[];
}

/** 页面初始化：查询范围、在院态、本库态与库位下拉。 */
export async function loadTraceNumberReportFormInitApi() {
  return publicRequestClient.post<LoadTraceNumberReportFormInitResponse>(
    `${BASE}/loadTraceNumberReportFormInit`,
    {},
  );
}

/** 追溯码列表分页查询。 */
export async function pageTraceNumberReportApi(body: PageTraceNumberReportRequest) {
  const res = await publicRequestClient.post<PageTraceNumberReportResponse>(
    `${BASE}/pageTraceNumberReport`,
    body,
  );
  const { content, totalCount } = parsePagedRowsFromApiData<TraceNumberReportRowDto>(res);
  return {
    ...res,
    records: content.length > 0 ? content : (res.records ?? []),
    total: totalCount || Number(res.total ?? res.totalCount ?? 0),
  };
}

/** 单码概览（详情 Drawer · 概览 Tab）。 */
export async function getTraceNumberOverviewApi(body: GetTraceNumberOverviewRequest) {
  return publicRequestClient.post<GetTraceNumberOverviewResponse>(
    `${BASE}/getTraceNumberOverview`,
    body,
  );
}

/** 单码流转明细（详情 Drawer · 明细 Tab）。 */
export async function listTraceRecordsApi(body: ListTraceRecordsRequest) {
  return publicRequestClient.post<ListTraceRecordsResponse>(`${BASE}/listTraceRecords`, body);
}

/** 导出与列表同筛选的 Excel。 */
export async function exportTraceNumberReportApi(body: ExportTraceNumberReportRequest) {
  return publicRequestClient.post<ExportTraceNumberReportResponse>(
    `${BASE}/exportTraceNumberReport`,
    body,
  );
}
