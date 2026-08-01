/**
 * 住院患者入出明细报表 — inpatientInOutDetailReport
 */
import type { ApiOptionItem, PageParams } from '#/types/api-response';
import type { BinaryExportResult } from '#/utils/export/download-binary-export';

import { publicRequestClient } from '#/api/request';

const BASE = '/inpatientInOutDetailReport';

/** 入出状态：全部。 */
export const MOVEMENT_STATUS_ALL = 'ALL' as const;
/** 入出状态：入院。 */
export const MOVEMENT_STATUS_ADMISSION = 'ADMISSION' as const;
/** 入出状态：出院。 */
export const MOVEMENT_STATUS_DISCHARGE = 'DISCHARGE' as const;

export type MovementStatusCode =
  | typeof MOVEMENT_STATUS_ADMISSION
  | typeof MOVEMENT_STATUS_ALL
  | typeof MOVEMENT_STATUS_DISCHARGE;

export interface LoadInpatientInOutDetailReportPageInitResponse {
  defaultDateFrom?: string;
  defaultDateTo?: string;
  defaultPageSize?: number;
  defaultMovementStatus?: MovementStatusCode | string;
  movementStatusOptions?: ApiOptionItem[];
  organizationOptions?: ApiOptionItem[];
  wardOptions?: ApiOptionItem[];
}

export interface ListInpatientInOutDetailWardOptionsRequest {
  organizationId?: string;
}

export interface ListInpatientInOutDetailWardOptionsResponse {
  wardOptions?: ApiOptionItem[];
}

export interface InpatientInOutDetailQueryFilter {
  dateFrom: string;
  dateTo: string;
  movementStatus: MovementStatusCode | string;
  organizationId?: string;
  wardLocationId?: string;
}

export interface InpatientInOutDetailRowDto {
  encounterId?: string;
  busNo?: string;
  patientId?: string;
  patientName?: string;
  genderText?: string;
  ageText?: string;
  admissionOrganizationName?: string;
  admissionWardName?: string;
  wardName?: string;
  houseName?: string;
  bedName?: string;
  startTime?: string;
  endTime?: string;
  transferTargetText?: string;
  settledFeeAmount?: number | string | null;
  transferred?: boolean;
}

export interface InpatientInOutDetailFooterDto {
  admissionPersonTimes?: number;
  dischargePersonTimes?: number;
}

export interface PageInpatientInOutDetailReportRequest
  extends InpatientInOutDetailQueryFilter,
    PageParams {}

export interface PageInpatientInOutDetailReportResponse {
  content?: InpatientInOutDetailRowDto[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
  showTransferColumn?: boolean;
  footer?: InpatientInOutDetailFooterDto;
}

export type ExportInpatientInOutDetailReportRequest = InpatientInOutDetailQueryFilter;

export type ExportInpatientInOutDetailReportResponse = BinaryExportResult & {
  showTransferColumn?: boolean;
};

/** 加载页初始化（默认日期窗、状态下拉、入院科室）。 */
export async function loadInpatientInOutDetailReportPageInitApi() {
  return publicRequestClient.post<LoadInpatientInOutDetailReportPageInitResponse>(
    `${BASE}/loadInpatientInOutDetailReportPageInit`,
    {},
  );
}

/** 按入院科室加载病区选项。 */
export async function listInpatientInOutDetailWardOptionsApi(
  body: ListInpatientInOutDetailWardOptionsRequest,
) {
  return publicRequestClient.post<ListInpatientInOutDetailWardOptionsResponse>(
    `${BASE}/listInpatientInOutDetailWardOptions`,
    body,
  );
}

/** 分页查询入出明细。 */
export async function pageInpatientInOutDetailReportApi(
  body: PageInpatientInOutDetailReportRequest,
) {
  return publicRequestClient.post<PageInpatientInOutDetailReportResponse>(
    `${BASE}/pageInpatientInOutDetailReport`,
    body,
  );
}

/** 导出入出明细 Excel。 */
export async function exportInpatientInOutDetailReportApi(
  body: ExportInpatientInOutDetailReportRequest,
) {
  return publicRequestClient.post<ExportInpatientInOutDetailReportResponse>(
    `${BASE}/exportInpatientInOutDetailReport`,
    body,
  );
}
