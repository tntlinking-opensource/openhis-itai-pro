/**
 * 住院药房汇总发药（AS-IP-PHA-002，IF-IPS-01～04）
 */

import type { PageApiResponse, PageParams } from '#/types/api-response';

/** IF-IPS-01 分页查询待核发汇总单头（继承 AbstractHealthPageRequest 分页/关键字/时段） */
export interface PagePendingMedicationSummarySlipsRequest extends PageParams {
  /** 汇总单号关键字 */
  keyword?: null | string;
  /** 申请时间下界 YYYY-MM-DD HH:mm:ss */
  startDate?: null | string;
  /** 申请时间上界 YYYY-MM-DD HH:mm:ss */
  endDate?: null | string;
  /** 发放药房；0 或空表示当前用户可见全部可操作药房 */
  purposeLocationId?: null | string;
  /** 供应审批态：待发药 APPROVAL、已核发 AGREE；缺省 APPROVAL */
  supplyStatusEnumIn?: null | string[];
}

/** 汇总单头（待核发） */
export interface InpatientPharmacySummarySlipHeaderDto {
  busNo?: string;
  applyTime?: string;
  dispenseTime?: string;
  purposeLocationId?: string;
  purposeLocationName?: string;
  applicantId?: string;
  applicantName?: string;
  receiverId?: string;
  receiverName?: string;
  supplyStatusEnum?: string;
  supplyStatusEnumText?: string;
}

/** IF-IPS-01 分页响应：行数据仅 {@code content}，与 PagedResult 对齐 */
export type PagePendingMedicationSummarySlipsResponse =
  PageApiResponse<InpatientPharmacySummarySlipHeaderDto>;

/** IF-IPS-02 查询汇总单详情 */
export interface GetMedicationSummaryDispenseDetailRequest {
  summaryNo: string;
}

/** 合并 Supply 行（拣货视图） */
export interface InpatientPharmacySummarySlipLineDto {
  supplyRequestId?: string;
  itemId?: string;
  medicationName?: string;
  specificationText?: string;
  itemQuantity?: number | string;
  unitCode?: string;
  unitCodeText?: string;
  /** 满包整数 + 拆零余量展示（如 1盒1片） */
  displayQuantityText?: string;
  manufacturerName?: string;
  purposeLocationName?: string;
  unitPrice?: number | string;
  totalPrice?: number | string;
}

/** 底层发放行（含患者/批号） */
export interface InpatientPharmacySummaryDispenseItemDto {
  dispenseId: string;
  statusEnum?: string;
  statusEnumText?: string;
  medicationId?: string;
  medicationName?: string;
  specificationText?: string;
  locationId?: string;
  locationName?: string;
  lotNumber?: string;
  quantity?: number | string;
  unitCode?: string;
  unitCodeText?: string;
  encounterId?: string;
  patientId?: string;
  patientName?: string;
  bedDisplay?: string;
  traceNo?: string;
}

export interface GetMedicationSummaryDispenseDetailResponse {
  summaryNo?: string;
  header?: InpatientPharmacySummarySlipHeaderDto;
  mergedLines?: InpatientPharmacySummarySlipLineDto[];
  dispenseItems?: InpatientPharmacySummaryDispenseItemDto[];
}

/** IF-IPS-03 批量汇总核发 */
export interface SummaryDispenseTraceLineSubmitDto {
  dispenseId: string;
  traceNo?: null | string;
}

export interface DispenseSummaryMedicationsRequest {
  summaryNos: string[];
  receiverId: string;
  preparerPractitionerId: string;
  traceLines?: SummaryDispenseTraceLineSubmitDto[];
  /** @deprecated CHS 启用即上传，请使用 contractBusNo */
  medicareUploadEnabled?: boolean;
  contractBusNo?: string;
}

export interface DispenseSummaryMedicationsResponse {
  summaryNos?: string[];
  message?: null | string;
}

/** IF-IPS-04 批量汇总驳回 */
export interface RejectSummaryDispenseRequest {
  summaryNos: string[];
  rejectReasonCode?: null | string;
}

export interface RejectSummaryDispenseResponse {
  summaryNos?: string[];
  message?: null | string;
}
