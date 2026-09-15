/**
 * 门诊/住院医保费用明细报表 — AS-RPT-012 / AS-RPT-013
 * POST /api/app/{outpatient|inpatient}InsuranceFeeDetailReport/{methodName}
 */
import { reportPost } from '#/constants/report-request-timeout';
import type { ApiOptionItem, PageParams } from '#/types/api-response';
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';
import type { BinaryExportResult } from '#/utils/export/download-binary-export';

import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

/** 报表场景：门诊或住院。 */
export type InsuranceFeeDetailReportScope = 'inpatient' | 'outpatient';

/** 职工/居民：全部。 */
export const INSUTYPE_GROUP_ALL = 'ALL' as const;
/** 职工/居民：职工。 */
export const INSUTYPE_GROUP_EMPLOYEE = 'EMPLOYEE' as const;
/** 职工/居民：居民。 */
export const INSUTYPE_GROUP_RESIDENT = 'RESIDENT' as const;

/** 省医保合同分类码。 */
export const CONTRACT_CATEGORY_PROVINCIAL = 'PROVINCIAL_INSURANCE' as const;
/** 市医保合同分类码。 */
export const CONTRACT_CATEGORY_MUNICIPAL = 'MUNICIPAL_INSURANCE' as const;

/**
 * 解析 AppService BASE。
 */
function resolveReportBase(scope: InsuranceFeeDetailReportScope): string {
  if (scope === 'inpatient') {
    return '/inpatientInsuranceFeeDetailReport';
  }
  return '/outpatientInsuranceFeeDetailReport';
}

export interface LoadInsuranceFeeDetailReportPageInitResponse {
  defaultDateFrom?: string;
  defaultDateTo?: string;
  defaultPageSize?: number;
  organizationOptions?: ApiOptionItem[];
  contractCategoryOptions?: ApiOptionItem[];
  workInjuryEnabled?: boolean;
  insutypeGroupOptions?: ApiOptionItem[];
}

export interface InsuranceFeeDetailQueryFilter {
  dateFrom: string;
  dateTo: string;
  contractCategoryCodes?: string[];
  workInjuryOnly?: boolean;
  insutypeGroup?: string;
  organizationId?: string;
  setlIdKeyword?: string;
  mdtrtIdKeyword?: string;
  busNoKeyword?: string;
  patientNameKeyword?: string;
}

export interface InsuranceFeeDetailRowDto {
  settlementRecordId?: string;
  encounterId?: string;
  patientName?: string;
  /** 就诊科室名称（门诊挂号科 / 住院入院科）。 */
  organizationName?: string;
  busNo?: string;
  mdtrtId?: string;
  setlId?: string;
  setlTime?: string;
  medfeeSumamt?: number | string;
  fundPaySumamt?: number | string;
  acctPay?: number | string;
  psnCashPay?: number | string;
}

export interface InsuranceFeeDetailFooterDto {
  lineCount?: number;
  medfeeSumamtTotal?: number | string;
  fundPaySumamtTotal?: number | string;
  acctPayTotal?: number | string;
  psnCashPayTotal?: number | string;
}

export interface PageInsuranceFeeDetailReportRequest
  extends InsuranceFeeDetailQueryFilter,
    PageParams {}

export interface PageInsuranceFeeDetailReportResponse {
  content?: InsuranceFeeDetailRowDto[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
  footer?: InsuranceFeeDetailFooterDto;
}

export interface ExportInsuranceFeeDetailReportRequest extends InsuranceFeeDetailQueryFilter {
  exportColumns?: ExcelExportColumnPayload[];
}

export type ExportInsuranceFeeDetailReportResponse = BinaryExportResult;

/**
 * IF-01：页初始化。
 */
export async function loadInsuranceFeeDetailReportPageInitApi(
  scope: InsuranceFeeDetailReportScope,
) {
  const method =
    scope === 'inpatient'
      ? 'loadInpatientInsuranceFeeDetailReportPageInit'
      : 'loadOutpatientInsuranceFeeDetailReportPageInit';
  return reportPost<LoadInsuranceFeeDetailReportPageInitResponse>(
    `${resolveReportBase(scope)}/${method}`,
    {},
  );
}

/**
 * IF-02：明细分页（含页脚合计）。
 */
export async function pageInsuranceFeeDetailReportApi(
  scope: InsuranceFeeDetailReportScope,
  params: PageInsuranceFeeDetailReportRequest,
) {
  const method =
    scope === 'inpatient'
      ? 'pageInpatientInsuranceFeeDetailReport'
      : 'pageOutpatientInsuranceFeeDetailReport';
  const data = await reportPost<PageInsuranceFeeDetailReportResponse>(
    `${resolveReportBase(scope)}/${method}`,
    params,
  );
  const parsed = parsePagedRowsFromApiData<InsuranceFeeDetailRowDto>(data);
  return {
    ...data,
    content: parsed.content,
    totalCount: parsed.totalCount,
  } as PageInsuranceFeeDetailReportResponse;
}

/**
 * IF-03：导出。
 */
export async function exportInsuranceFeeDetailReportApi(
  scope: InsuranceFeeDetailReportScope,
  params: ExportInsuranceFeeDetailReportRequest,
) {
  const method =
    scope === 'inpatient'
      ? 'exportInpatientInsuranceFeeDetailReport'
      : 'exportOutpatientInsuranceFeeDetailReport';
  return reportPost<ExportInsuranceFeeDetailReportResponse>(
    `${resolveReportBase(scope)}/${method}`,
    params,
    { responseType: 'json' },
  );
}
