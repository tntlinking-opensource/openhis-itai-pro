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
  /** 是否仅查询发放表终态 COMPLETED 的汇总单（已发药 Tab）；false/空=待发核对 SUMMARIZED */
  summaryDispenseCompletedOnly?: boolean | null;
}

/** 汇总单头（待核发） */
export interface InpatientPharmacySummarySlipHeaderDto {
  busNo?: string;
  applyTime?: string;
  dispenseTime?: string;
  purposeLocationId?: string;
  purposeLocationName?: string;
  /** 申领科室（点汇总/发送的人的登录科室） */
  requestingDepartmentName?: string;
  applicantId?: string;
  applicantName?: string;
  /** 申领科室（病区所属科室；详情/打印回填） */
  applicantDepartmentName?: string;
  receiverId?: string;
  receiverName?: string;
  supplyStatusEnum?: string;
  supplyStatusEnumText?: string;
  /** 是否已发药（发放表 COMPLETED 终态） */
  summaryDispenseCompleted?: boolean;
  /** 汇总门控展示：待核对 / 已发药 */
  summaryDispenseStatusText?: string;
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
  /** 追溯码（汇总核发落 SupplyDelivery；待发由前端暂存） */
  traceNo?: string;
  /** 包装单位码（待扫换算） */
  packageUnitCode?: string;
  /** 包装相对拆零倍数 */
  packageUnitRatio?: number | string;
  /** 拆零单位码 */
  splitUnitCode?: string;
  /** 追溯码主档贴码单位 */
  traceUnitCode?: string;
  /** 追溯码主档贴码单位展示文案（UNIT_CODE） */
  traceUnitCodeText?: string;
  /** 追溯码主档贴码比例快照 */
  traceUnitRatio?: number | string;
  /** 主表该品批是否已有追溯码（有码批） */
  lotHasTraceMaster?: boolean;
  /** 代表批号（待扫换算/匹配） */
  representativeLotNumber?: string;
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
  /** 是否已发药（发放表终态） */
  summaryDispenseCompleted?: boolean;
}

/** IF-IPS-03 批量汇总核发 */
export interface SummaryDispenseTraceLineSubmitDto {
  /** 供应请求行主键（非 dispenseId） */
  supplyRequestId: string;
  traceNo?: null | string;
}

export interface DispenseSummaryMedicationsRequest {
  summaryNos: string[];
  receiverId: string;
  preparerPractitionerId: string;
  traceLines?: SummaryDispenseTraceLineSubmitDto[];
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
