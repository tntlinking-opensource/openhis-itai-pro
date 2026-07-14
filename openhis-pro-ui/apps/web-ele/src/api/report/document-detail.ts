/**
 * 单据详情报表 — POST /api/app/inventoryDocumentDetail/{methodName}
 */
import type { PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/inventoryDocumentDetail';

export const DOCUMENT_DETAIL_DOMAIN_WAREHOUSE = 'WAREHOUSE' as const;
export const DOCUMENT_DETAIL_DOMAIN_PHARMACY = 'PHARMACY' as const;
export const DOCUMENT_DETAIL_DOMAIN_DEPARTMENT = 'DEPARTMENT' as const;
export type DocumentDetailDomainScope =
  | typeof DOCUMENT_DETAIL_DOMAIN_DEPARTMENT
  | typeof DOCUMENT_DETAIL_DOMAIN_PHARMACY
  | typeof DOCUMENT_DETAIL_DOMAIN_WAREHOUSE;

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
  approvalTimeStart?: string;
  approvalTimeEnd?: string;
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
  /** 操作单位展示数量（后端 {@link DocumentDetailQuantityConversionSupport} 已由 SPLIT 换算） */
  itemQuantity?: number | string;
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

export type ExportDocumentDetailsRequest = Omit<DocumentDetailQueryFilter, 'pageNo' | 'pageSize'>;

export interface ExportDocumentDetailsResponse {
  fileName?: string;
  contentType?: string;
  fileBytes?: string;
}

/** 页面初始化：单据类型与库位下拉、默认审批时间窗。 */
export async function loadDocumentDetailFormInitApi(body: LoadDocumentDetailFormInitRequest) {
  return publicRequestClient.post<LoadDocumentDetailFormInitResponse>(
    `${BASE}/loadDocumentDetailFormInit`,
    body,
  );
}

/** 单据详情明细分页。 */
export async function pageDocumentDetailsApi(body: PageDocumentDetailsRequest) {
  const res = await publicRequestClient.post<PageDocumentDetailsResponse>(
    `${BASE}/pageDocumentDetails`,
    body,
  );
  const { content, totalCount } = parsePagedRowsFromApiData<DocumentDetailRowDto>(res);
  return {
    ...res,
    records: content.length > 0 ? content : (res.records ?? []),
    total: totalCount || res.total || 0,
  };
}

/** 单据详情合计。 */
export async function summarizeDocumentDetailsApi(body: ExportDocumentDetailsRequest) {
  return publicRequestClient.post<SummarizeDocumentDetailsResponse>(
    `${BASE}/summarizeDocumentDetails`,
    body,
  );
}

/** 导出单据详情 Excel。 */
export async function exportDocumentDetailsApi(body: ExportDocumentDetailsRequest) {
  return publicRequestClient.post<ExportDocumentDetailsResponse>(
    `${BASE}/exportDocumentDetails`,
    body,
  );
}
