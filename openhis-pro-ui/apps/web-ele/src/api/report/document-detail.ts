/**
 * 单据详情报表 — POST /api/app/inventoryDocumentDetail/{methodName}
 */
import { reportPost } from '#/constants/report-request-timeout';
import type { PageParams } from '#/types/api-response';
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';

import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/inventoryDocumentDetail';

export const DOCUMENT_DETAIL_DOMAIN_WAREHOUSE = 'WAREHOUSE' as const;
export const DOCUMENT_DETAIL_DOMAIN_PHARMACY = 'PHARMACY' as const;
export const DOCUMENT_DETAIL_DOMAIN_DEPARTMENT = 'DEPARTMENT' as const;
export type DocumentDetailDomainScope =
  | typeof DOCUMENT_DETAIL_DOMAIN_DEPARTMENT
  | typeof DOCUMENT_DETAIL_DOMAIN_PHARMACY
  | typeof DOCUMENT_DETAIL_DOMAIN_WAREHOUSE;

/** 项目分类：药品定义表。 */
export const DOCUMENT_DETAIL_ITEM_TABLE_MEDICATION = 'med_medication_definition' as const;
/** 项目分类：耗材定义表。 */
export const DOCUMENT_DETAIL_ITEM_TABLE_DEVICE = 'adm_device_definition' as const;
export type DocumentDetailItemTable =
  | typeof DOCUMENT_DETAIL_ITEM_TABLE_DEVICE
  | typeof DOCUMENT_DETAIL_ITEM_TABLE_MEDICATION;

export interface OptionDto {
  value?: string;
  label?: string;
}

export interface LoadDocumentDetailFormInitRequest {
  domainScope: DocumentDetailDomainScope;
}

export interface LoadDocumentDetailFormInitResponse {
  typeEnumOptions?: OptionDto[];
  defaultTypeEnum?: string;
  sourceLocationOptions?: OptionDto[];
  purposeLocationOptions?: OptionDto[];
  /** 供应商下拉；空=全部不过滤 */
  supplierOptions?: OptionDto[];
  defaultApprovalTimeStart?: string;
  defaultApprovalTimeEnd?: string;
}

export interface DocumentDetailQueryFilter {
  domainScope: DocumentDetailDomainScope;
  pageNo?: number;
  pageSize?: number;
  typeEnum?: string;
  sourceLocationId?: string;
  purposeLocationId?: string;
  /** 供应商主键；空=该维不过滤 */
  supplierId?: string;
  approvalTimeStart?: string;
  approvalTimeEnd?: string;
  /** 项目分类：med_medication_definition / adm_device_definition；空=全部 */
  itemTable?: DocumentDetailItemTable | string;
  productKeyword?: string;
  lotNumberKeyword?: string;
  busNo?: string;
}

export interface DocumentDetailRowDto {
  lineId?: string;
  busNo?: string;
  originalBusNo?: string;
  typeEnum?: string;
  typeEnumText?: string;
  sourceLocationName?: string;
  purposeLocationName?: string;
  /** 领用科室（行政 Organization；领用/退领有值） */
  consumingOrganizationName?: string;
  supplierName?: string;
  productName?: string;
  productBusNo?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  traceNo?: string;
  /** 操作单位编码（持久化录入单位，非 SPLIT 码） */
  unitCode?: string;
  unitCodeDictText?: string;
  /** 操作单位展示数量（后端已由 SPLIT 换算；与单价同口径） */
  itemQuantity?: number | string;
  /** 包装单位数量（拆零量 ÷ 包装倍数） */
  packageQuantity?: number | string;
  /** 包装单位展示名 */
  packageUnitName?: string;
  /** 最小（拆零）单位数量 */
  minUnitQuantity?: number | string;
  /** 最小单位展示名 */
  minUnitName?: string;
  price?: number | string;
  totalPrice?: number | string;
  applicantName?: string;
  approverName?: string;
  practitionerName?: string;
  applyTime?: string;
  approvalTime?: string;
  occurrenceTime?: string;
  bookQuantity?: number | string;
  actualQuantity?: number | string;
  gainLossQuantity?: number | string;
  actualAmount?: number | string;
  gainLossAmount?: number | string;
  remake?: string;
}

export interface PageDocumentDetailsRequest
  extends Omit<DocumentDetailQueryFilter, 'pageNo' | 'pageSize'>,
    PageParams {}

export interface PageDocumentDetailsResponse {
  total?: number;
  pageNo?: number;
  pageSize?: number;
  records?: DocumentDetailRowDto[];
}

export interface SummarizeDocumentDetailsResponse {
  totalPriceSum?: number | string;
  itemQuantitySum?: number | string;
  rowCount?: number;
}

export type ExportDocumentDetailsRequest = Omit<
  DocumentDetailQueryFilter,
  'pageNo' | 'pageSize'
> & {
  /** 页面可见列 → 后端 Excel 列裁剪 */
  exportColumns?: ExcelExportColumnPayload[];
};

export interface ExportDocumentDetailsResponse {
  fileName?: string;
  contentType?: string;
  fileBytes?: string;
}

/** 页面初始化：单据类型与库位下拉、默认审批时间窗。 */
export async function loadDocumentDetailFormInitApi(body: LoadDocumentDetailFormInitRequest) {
  return reportPost<LoadDocumentDetailFormInitResponse>(`${BASE}/loadDocumentDetailFormInit`, body);
}

/** 单据详情明细分页。 */
export async function pageDocumentDetailsApi(body: PageDocumentDetailsRequest) {
  const res = await reportPost<PageDocumentDetailsResponse>(`${BASE}/pageDocumentDetails`, body);
  const { content, totalCount } = parsePagedRowsFromApiData<DocumentDetailRowDto>(res);
  return {
    ...res,
    records: content.length > 0 ? content : (res.records ?? []),
    total: totalCount || res.total || 0,
  };
}

/** 单据详情合计。 */
export async function summarizeDocumentDetailsApi(body: ExportDocumentDetailsRequest) {
  return reportPost<SummarizeDocumentDetailsResponse>(`${BASE}/summarizeDocumentDetails`, body);
}

/** 导出单据详情 Excel。 */
export async function exportDocumentDetailsApi(body: ExportDocumentDetailsRequest) {
  return reportPost<ExportDocumentDetailsResponse>(`${BASE}/exportDocumentDetails`, body);
}
