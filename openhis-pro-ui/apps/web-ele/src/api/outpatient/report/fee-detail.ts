/**
 * 门诊费用明细 — POST /api/app/outpatientFeeDetail/<method>
 */
import { reportPost } from '#/constants/report-request-timeout';
import type { ApiOptionItem, PageParams } from '#/types/api-response';
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';
import type { BinaryExportResult } from '#/utils/export/download-binary-export';

const BASE = '/outpatientFeeDetail';

export interface LoadOutpatientFeeDetailPageInitResponse {
  defaultDateFrom?: string;
  defaultDateTo?: string;
  defaultPageSize?: number;
  tradeDirectionOptions?: ApiOptionItem[];
  feeCategoryOptions?: ApiOptionItem[];
  medicationSubCategoryOptions?: ApiOptionItem[];
  activitySubCategoryOptions?: ApiOptionItem[];
  deviceSubCategoryOptions?: ApiOptionItem[];
  registrationDepartmentOptions?: ApiOptionItem[];
  cashierOptions?: ApiOptionItem[];
}

export interface PageOutpatientFeeDetailParams extends PageParams {
  dateFrom: string;
  dateTo: string;
  registrationDepartmentId?: string;
  feeCategoryCodes?: string[];
  subCategoryCodes?: string[];
  patientKeyword?: string;
  productNameKeyword?: string;
  productCodeKeyword?: string;
  cashierId?: string;
  tradeDirection?: string;
}

export interface OutpatientFeeDetailAmountSummaryDto {
  baseAmountTotal?: number | string;
  discountAmountTotal?: number | string;
  receivableAmountTotal?: number | string;
  discountedAmountTotal?: number | string;
  lineAmountTotal?: number | string;
  actualReceivedAmountTotal?: number | string;
  insuranceAmountTotal?: number | string;
  selfPayAmountTotal?: number | string;
}

export interface OutpatientFeeDetailLineDto {
  chargeItemId?: string;
  paymentId?: string;
  feeCategoryCode?: string;
  feeCategoryDisplay?: string;
  subCategoryCode?: string;
  subCategoryDisplay?: string;
  productName?: string;
  productCode?: string;
  specification?: string;
  quantityValue?: number | string;
  quantityUnitDisplay?: string;
  orderingDoctorId?: string;
  orderingDoctorName?: string;
  performerId?: string;
  performerName?: string;
  cashierId?: string;
  cashierName?: string;
  billDate?: string;
  tradeDirection?: string;
  insuranceLevelDisplay?: string;
  baseAmount?: number | string;
  discountAmount?: number | string;
  receivableAmount?: number | string;
  discountedAmount?: number | string;
  actualReceivedAmount?: number | string;
  insuranceAmount?: number | string;
  selfPayAmount?: number | string;
  discountRateDisplay?: string;
}

export interface OutpatientFeeDetailEncounterBlockDto {
  encounterId?: string;
  patientName?: string;
  busNo?: string;
  registrationDepartmentName?: string;
  encounterDate?: string;
  lines?: OutpatientFeeDetailLineDto[];
  subtotal?: OutpatientFeeDetailAmountSummaryDto;
}

export interface PageOutpatientFeeDetailResponse {
  content?: OutpatientFeeDetailEncounterBlockDto[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
  summary?: OutpatientFeeDetailAmountSummaryDto;
}

export type ExportOutpatientFeeDetailParams = Omit<
  PageOutpatientFeeDetailParams,
  'pageNo' | 'pageSize'
> & {
  /** 当前可见列 → 后端 Excel 列裁剪 */
  exportColumns?: ExcelExportColumnPayload[];
};

export interface ExportOutpatientFeeDetailResponse extends BinaryExportResult {
  rowCount?: number;
}

/** 加载门诊费用明细页筛选项与默认日期。 */
export async function loadOutpatientFeeDetailPageInitApi() {
  return reportPost<LoadOutpatientFeeDetailPageInitResponse>(
    `${BASE}/loadOutpatientFeeDetailPageInit`,
    {},
  );
}

/** 按筛选条件分页查询就诊块与全量合计。 */
export async function pageOutpatientFeeDetailApi(body: PageOutpatientFeeDetailParams) {
  return reportPost<PageOutpatientFeeDetailResponse>(`${BASE}/pageOutpatientFeeDetail`, body);
}

/** 按当前筛选条件导出门诊费用明细 Excel。 */
export async function exportOutpatientFeeDetailApi(body: ExportOutpatientFeeDetailParams) {
  return reportPost<ExportOutpatientFeeDetailResponse>(`${BASE}/exportOutpatientFeeDetail`, body);
}
